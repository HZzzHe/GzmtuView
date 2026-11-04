package com.gzmtuview.data.net

import com.gzmtuview.data.model.Course
import com.gzmtuview.data.model.ExamRecord
import com.gzmtuview.data.model.GradeRecord
import com.gzmtuview.data.model.ScheduleResult
import com.gzmtuview.data.model.Semester
import com.gzmtuview.data.model.WeekDates
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** 教务系统接口错误。 */
class ZFException(
    message: String,
    val authFailed: Boolean = false,
    val sessionExpired: Boolean = false,
) : Exception(message)

/** 判断是否为网络层错误（离线/超时），业务错误不算。 */
fun isNetworkError(error: Throwable): Boolean {
    if (error is ZFException) return false
    return error is IOException
}

/** 凭据读取接口（会话过期自动重登用）。 */
interface CredentialStore {
    suspend fun loadCredentials(): Credentials?
}

data class Credentials(val username: String, val password: String?)

/** 有效学期缓存接口。 */
interface SemesterCache {
    /** 读取有效学期缓存；无缓存或已过期返回 null。 */
    suspend fun loadValidSemesters(): List<Semester>?

    /** 保存有效学期缓存。 */
    suspend fun saveValidSemesters(semesters: List<Semester>)
}

/**
 * 正方教务系统 9.0（jwglxt）客户端。
 * 登录协议（在真实部署上实测）：csrftoken → RSA 公钥 →
 * PKCS#1 v1.5 加密密码 → POST 提交 → 课表索引页确认会话。
 * 会话由 CookieJar 自动维护（JSESSIONID）。
 *
 * 教务系统地址见 [ZFClient.DEFAULT_BASE_URL]，开源版是占位值，必须改成你自己学校的地址。
 */
class ZFClient(
    private val preferHttps: Boolean = true,
    private val store: CredentialStore? = null,
    private val semesterCache: SemesterCache? = null,
    private val baseUrlOverride: String? = null,
    private val cookieJar: MemCookieJar = MemCookieJar(),
) {
    companion object {
        /**
         * ★ 开源版是占位值，必须改成你自己学校的正方教务系统地址再构建：
         * 只需「协议 + 域名」，形如 `https://jwgl.xxx.edu.cn`，
         * 应用内的路径前缀由 [PATH_PREFIX] 统一拼接，不要把它写进这里。
         * 仍是该占位值时 [request] 直接报错，不会真的去连 example 域名。
         */
        const val DEFAULT_BASE_URL = "https://your-academic-affairs-server.example.edu.cn"

        /** 占位地址的特征后缀（example 保留域名，真实学校不会出现）。 */
        private const val PLACEHOLDER_SUFFIX = ".example.edu.cn"

        /** 教务系统地址是否仍是未配置的占位值。 */
        fun isPlaceholderUrl(url: String): Boolean = url.endsWith(PLACEHOLDER_SUFFIX)

        /** 正方 9.0 的上下文路径前缀。 */
        const val PATH_PREFIX = "/jwglxt"

        /** 学年窗口：只保留「当前年往前推 6 年」到「当前年」的学年。 */
        const val YEAR_WINDOW = 6

        /** 标准学期编码（第 1/2 学期；第 3 学期为短学期，多数学校没有）。 */
        val DEFAULT_XQMS = listOf("3", "12")

        /** 从手机版时间表页面 HTML 解析周次-日期（纯函数）。
         *  页面结构：`<span id='zs' ...>第1周&nbsp; 2026-08-31/2026-09-06</span>` */
        fun parseWeekDatesFromHtml(html: String, expectedWeeks: Int = 20): WeekDates? {
            val m = Regex(
                """第\s*(\d+)\s*周[^0-9]*(\d{4})-(\d{2})-(\d{2})/(\d{4})-(\d{2})-(\d{2})"""
            ).find(html) ?: return null
            val weekNum = m.groupValues[1].toInt()
            val start = LocalDate.of(
                m.groupValues[2].toInt(),
                m.groupValues[3].toInt(),
                m.groupValues[4].toInt(),
            )
            val week1Monday = start.minusDays((weekNum - 1) * 7L)

            val xnm = Regex("""id="xnm_hide"\s+value="(\d+)"""")
                .find(html)?.groupValues?.get(1) ?: ""
            val xqm = Regex("""id="xqm_hide"\s+value="(\d+)"""")
                .find(html)?.groupValues?.get(1) ?: ""

            return WeekDates(week1Monday, expectedWeeks, xnm, xqm)
        }
    }

    /** 教务系统地址（当前协议；连接失败自动降级到相反协议后更新）。 */
    private var baseUrl: String = normalizeScheme(baseUrlOverride ?: DEFAULT_BASE_URL, preferHttps)

    /** 已降级到相反协议（本次会话内不再重复降级；重启后按开关重新尝试）。 */
    private var schemeDowngraded = false

    val jar: MemCookieJar get() = cookieJar

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        // 强制 HTTP/1.1：Flutter 版(Dart HttpClient)只有 HTTP/1.1；
        // 部分正方教务服务器的 HTTP/2 实现对登录表单处理存在兼容问题，
        // OkHttp 默认会协商 h2，导致 POST 登录被忽略（返回登录页）。
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .build()

    /** 按「优先使用 https」偏好规范化协议前缀。 */
    private fun normalizeScheme(url: String, preferHttps: Boolean): String {
        val s = url.trim()
        val i = s.indexOf("://")
        val rest = if (i < 0) s else s.substring(i + 3)
        return (if (preferHttps) "https" else "http") + "://" + rest
    }

    private fun switchScheme() {
        val rest = baseUrl.removePrefix("https://").removePrefix("http://")
        val newScheme = if (baseUrl.startsWith("https://")) "http" else "https"
        baseUrl = "$newScheme://$rest"
    }

    private fun isConnError(e: IOException): Boolean =
        e is ConnectException || e is SocketTimeoutException || e is UnknownHostException

    /** 发请求（带协议降级重试）。form 非空时 POST，否则 GET。 */
    private suspend fun request(
        path: String,
        query: Map<String, String> = emptyMap(),
        form: Map<String, String>? = null,
    ): String = withContext(Dispatchers.IO) {
        // 开源版地址是占位值：任何一次请求都先给出可行动提示，
        // 别让人等 5s 连接超时看一句「网络错误」猜原因。
        if (isPlaceholderUrl(baseUrl)) {
            throw ZFException(
                "教务系统地址尚未配置：请把 ZFClient.DEFAULT_BASE_URL 改成你学校的地址后重新构建"
            )
        }
        var result: String? = null
        while (true) {
            try {
                result = doRequest(path, query, form)
                break
            } catch (e: IOException) {
                if (!schemeDowngraded && isConnError(e)) {
                    schemeDowngraded = true
                    switchScheme()
                    continue // 降级后重试一次
                }
                throw e
            }
        }
        result!!
    }

    private fun doRequest(path: String, query: Map<String, String>, form: Map<String, String>?): String {
        val urlSb = StringBuilder(baseUrl).append(path)
        if (query.isNotEmpty()) {
            urlSb.append(if (path.contains('?')) '&' else '?')
            urlSb.append(query.entries.joinToString("&") {
                "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
            })
        }
        val builder = Request.Builder()
            .url(urlSb.toString())
            // 模拟浏览器 UA（OkHttp 默认 okhttp/x 易被服务器拒绝/差异化处理）。
            // 机型串只为「像个手机浏览器」，换掉不影响判定；若你的教务 WAF 挑剔再调这里。
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36",
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        // 与 Flutter 版(Dio，不带 X-Requested-With)保持一致的请求头：
        // 正方对带 XMLHttpRequest 头的 login_slogin.html POST 会按 AJAX 返回 JSON
        // （含“验证码”等提示文本），导致 contains("验证码") 误判为要求验证码。
        if (form != null) {
            val fb = FormBody.Builder()
            for ((k, v) in form) fb.add(k, v)
            builder.post(fb.build())
        } else {
            builder.get()
        }
        client.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            return body
        }
    }

    /** 重登互斥：并发请求（如刷新时 fetchSchedule+fetchWeekDates 同时失效）
     *  会同时触发 autoRelogin，两个 login 互踩 cookieJar（clear 掉对方刚建的
     *  会话、消费对方拿到的 csrftoken）→ 误报"账号或密码错误"。 */
    private val reloginMutex = kotlinx.coroutines.sync.Mutex()

    /** 会话过期时自动用保存凭据重新登录并重试一次。 */
    private suspend fun <T> autoRelogin(action: suspend () -> T): T {
        try {
            return action()
        } catch (e: ZFException) {
            if (!e.sessionExpired || store == null) throw e
            val creds = store.loadCredentials() ?: throw e
            val password = creds.password ?: throw e
            reloginMutex.withLock {
                // 拿到锁先重试：并发场景下其他请求可能已完成重登（会话已恢复）
                try {
                    return action()
                } catch (e2: ZFException) {
                    // 非会话类错误（如真密码错/验证码）直接上抛，不做重登
                    if (!e2.sessionExpired) throw e2
                }
                login(creds.username, password)
            }
            return action()
        }
    }

    /** 登录教务系统。成功后会话 cookie 保存在 CookieJar 中。 */
    suspend fun login(username: String, password: String) {
        // 0. 清空旧会话：跨天/半失效的旧 Cookie（JSESSIONID/route）会让服务器把
        //    登录页请求重定向到系统首页，页面里没有 csrftoken → “csrftoken 缺失”误报。
        //    登录本就应从全新会话开始（对齐浏览器无痕登录行为）。
        cookieJar.clear()
        // 1. 获取登录页与 csrftoken
        val page = request("$PATH_PREFIX/xtgl/login_slogin.html")
        if (page.contains("zfdun") || page.contains("zfcaptcha")) {
            throw ZFException("教务系统当前启用了滑块验证码，暂不支持自动登录")
        }
        val csrftoken = extractAttr(page, "csrftoken", "value")
            ?: throw ZFException("无法解析登录页（csrftoken 缺失），教务系统可能已改版")

        // 2. 获取 RSA 公钥
        val keyResp = request(
            "$PATH_PREFIX/xtgl/login_getPublicKey.html",
            query = mapOf("time" to System.currentTimeMillis().toString()),
        )
        val keyJson = try {
            JSONObject(keyResp)
        } catch (e: Exception) {
            throw ZFException("获取登录公钥失败")
        }
        val modulus = keyJson.optString("modulus", "").ifEmpty { null }
        val exponent = keyJson.optString("exponent", "").ifEmpty { null }
        if (modulus == null || exponent == null) throw ZFException("获取登录公钥失败")

        // 3. 加密密码并组装表单（连同页面隐藏字段一并提交，与浏览器行为一致）
        val enPassword = RsaEncryptor.encrypt(password, modulus, exponent)
        val form = extractHiddenFields(page).toMutableMap()
        form["csrftoken"] = csrftoken
        form["yhm"] = username
        form["mm"] = enPassword

        // 4. 提交登录
        val body = request("$PATH_PREFIX/xtgl/login_slogin.html", form = form)
        if (body.contains("用户名或密码") || body.contains("密码错误") || body.contains("账号不存在")) {
            throw ZFException("账号或密码错误，请前往教务系统确认后再试", authFailed = true)
        }
        if (body.contains("验证码")) {
            throw ZFException("教务系统要求验证码，暂不支持自动登录")
        }

        // 5. 用课表索引页确认会话是否真正建立（失败时会被重定向回登录页）
        val checkHtml = request(
            "$PATH_PREFIX/kbcx/xskbcx_cxXskbcxIndex.html",
            query = mapOf("gnmkdm" to "N2151"),
        )
        if (checkHtml.contains("login_slogin")) {
            throw ZFException("登录失败：未能建立会话，请检查账号密码后重试")
        }
    }

    /** 获取可选学年学期列表：仅返回**有课表数据**的学期（探测 + 缓存）。 */
    suspend fun fetchSemesters(): List<Semester> = autoRelogin {
        // ★ 缓存优先：3 天内命中直接返回（零请求）
        if (semesterCache != null) {
            val cached = semesterCache.loadValidSemesters()
            if (!cached.isNullOrEmpty()) {
                return@autoRelogin sortSemesters(cached)
            }
        }

        val candidates = fetchSemesterCandidates()
        if (candidates.isEmpty()) {
            throw ZFException("未能解析学期列表，教务系统可能已改版")
        }

        // 探测：保留有课程数据的学期
        var valid = probeSemesters(candidates)
        if (valid.isEmpty()) {
            // 探测全部失败（网络波动等）：降级返回规则候选，避免空白
            valid = candidates
        } else {
            // 窗口延伸：窗口内最早学年有数据 → 向前探测到入学年
            val extended = probeEarlierYears(valid)
            if (extended != null) valid = extended + valid
        }

        if (semesterCache != null) {
            try {
                semesterCache.saveValidSemesters(valid)
            } catch (_: Exception) {
                // 缓存失败不阻塞
            }
        }
        valid
    }

    /** 解析索引页并生成规则过滤后的候选学期列表。 */
    private suspend fun fetchSemesterCandidates(): List<Semester> {
        val html = request(
            "$PATH_PREFIX/kbcx/xskbcx_cxXskbcxIndex.html",
            query = mapOf("gnmkdm" to "N2151"),
        )
        if (html.contains("login_slogin")) {
            throw ZFException("登录已过期，请重新登录", sessionExpired = true)
        }
        val xnmValues = parseSelect(html, "xnm").map { it.value }
        val xqmValues = parseSelect(html, "xqm").map { it.value }
        return buildSemesterCandidates(xnmValues, xqmValues, LocalDate.now().year)
    }

    /** 获取全部学年（原始 xnm 值列表，已过滤占位符），用于会话轻量检查。 */
    suspend fun fetchYearList(): List<String> = autoRelogin {
        val html = request(
            "$PATH_PREFIX/kbcx/xskbcx_cxXskbcxIndex.html",
            query = mapOf("gnmkdm" to "N2151"),
        )
        if (html.contains("login_slogin")) {
            throw ZFException("登录已过期，请重新登录", sessionExpired = true)
        }
        parseSelect(html, "xnm").map { it.value }.filter { (it.toIntOrNull() ?: 0) > 0 }
    }

    /** 生成候选学期列表（纯函数）。 */
    fun buildSemesterCandidates(
        xnmValues: List<String>,
        xqmValues: List<String>,
        currentYear: Int,
    ): List<Semester> {
        val years = sortedSetOf<Int>()
        for (v in xnmValues) {
            val y = v.toIntOrNull()
            if (y != null && y >= currentYear - YEAR_WINDOW && y <= currentYear) years.add(y)
        }
        val xqms = DEFAULT_XQMS.toMutableSet()
        for (v in xqmValues) {
            if (v.toIntOrNull() != null) xqms.add(v)
        }
        val list = mutableListOf<Semester>()
        for (y in years) for (q in xqms) list.add(Semester(xnm = y.toString(), xqm = q))
        return sortSemesters(list)
    }

    /** 按时间倒序衔接排序（最新学期在前）。 */
    fun sortSemesters(list: List<Semester>): List<Semester> =
        list.sortedByDescending { it.rank }

    /** 并发探测学期有效性（分批 4 个），返回有课程数据的学期。 */
    private suspend fun probeSemesters(candidates: List<Semester>): List<Semester> {
        val valid = mutableListOf<Semester>()
        val batchSize = 4
        var i = 0
        while (i < candidates.size) {
            val batch = candidates.subList(i, minOf(i + batchSize, candidates.size))
            // 顺序探测（保持简单可靠）；Flutter 版为并发，结果一致
            for (s in batch) {
                val count = probeScheduleCount(s)
                if (count != null && count > 0) valid.add(s)
            }
            i += batchSize
        }
        return valid
    }

    /** 窗口内最早学年有数据时，向前探测入学年边界（连续 2 个空学年停止）。 */
    private suspend fun probeEarlierYears(valid: List<Semester>): List<Semester>? {
        var minYear = 9999
        for (s in valid) {
            val y = s.xnm.toIntOrNull()
            if (y != null && y < minYear) minYear = y
        }
        if (minYear > LocalDate.now().year - YEAR_WINDOW) return null

        val extended = mutableListOf<Semester>()
        var emptyRun = 0
        var year = minYear - 1
        while (year >= 2000 && emptyRun < 2) {
            val s1 = Semester(xnm = year.toString(), xqm = "3")
            val s2 = Semester(xnm = year.toString(), xqm = "12")
            val c1 = probeScheduleCount(s1)
            val c2 = probeScheduleCount(s2)
            if ((c1 ?: 0) > 0 || (c2 ?: 0) > 0) {
                if ((c1 ?: 0) > 0) extended.add(s1)
                if ((c2 ?: 0) > 0) extended.add(s2)
                emptyRun = 0
            } else {
                emptyRun++
            }
            year--
        }
        return if (extended.isEmpty()) null else extended.reversed()
    }

    /** 探测学期有效性：返回该学期课表课程数；无效学期返回 null。 */
    suspend fun probeScheduleCount(semester: Semester): Int? = try {
        fetchSchedule(semester).courses.size
    } catch (_: Exception) {
        null
    }

    /** 获取学期周次-日期信息。解析失败或未登录返回 null（不影响课表加载）。 */
    suspend fun fetchWeekDates(expectedWeeks: Int = 20): WeekDates? = autoRelogin {
        try {
            val html = request("$PATH_PREFIX/kbcx/xskbcxMobile_cxTimeTableIndex.html")
            if (html.contains("login_slogin")) {
                throw ZFException("登录已过期，请重新登录", sessionExpired = true)
            }
            parseWeekDatesFromHtml(html, expectedWeeks)
        } catch (e: ZFException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** 查询指定学期的考试安排（PC 端「考试信息查询」，学生本人数据）。 */
    suspend fun fetchExams(semester: Semester): List<ExamRecord> = autoRelogin {
        // 手机版 ksmdglMobile 模块已被教务下线（入口页与列表接口都返回「系统运行异常」），
        // 改用 PC 端同一张表的数据接口：返回分页对象 {"items":[...]}，一次给出全部记录。
        val body = request(
            "$PATH_PREFIX/kwgl/kscx_cxXsksxxIndex.html",
            query = mapOf("doType" to "query"),
            form = mapOf("xnm" to semester.xnm, "xqm" to semester.xqm),
        )
        if (body.contains("login_slogin")) {
            throw ZFException("登录已过期，请重新登录", sessionExpired = true)
        }
        val root = try {
            JSONObject(body)
        } catch (_: Exception) {
            throw ZFException("考试数据格式异常")
        }
        val items = root.optJSONArray("items") ?: JSONArray()
        val result = mutableListOf<ExamRecord>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            result.add(ExamRecord.fromJson(item))
        }
        result
    }

    /** 查询指定学期的成绩（手机版接口）。 */
    suspend fun fetchGrades(semester: Semester): List<GradeRecord> = autoRelogin {
        val body = request(
            "$PATH_PREFIX/cjcx/cjcxMobile_cxXsgrcj.html",
            query = mapOf("doType" to "app"),
            form = mapOf("xnm" to semester.xnm, "xqm" to semester.xqm, "pkey" to ""),
        )
        parseListResponse(body) { JSONObject(it.toString()) }.map { GradeRecord.fromJson(it) }
    }

    /** 查询指定学期的个人课表。 */
    suspend fun fetchSchedule(semester: Semester): ScheduleResult = autoRelogin {
        val body = request(
            "$PATH_PREFIX/kbcx/xskbcx_cxXsKb.html",
            query = mapOf("gnmkdm" to "N2151"),
            form = mapOf("xnm" to semester.xnm, "xqm" to semester.xqm, "kzlx" to "ck", "xsdm" to ""),
        )
        if (body.trimStart().startsWith("<")) {
            if (body.contains("login_slogin")) {
                throw ZFException("登录已过期，请重新登录", sessionExpired = true)
            }
            throw ZFException("教务系统返回了异常页面")
        }
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            throw ZFException("课表数据解析失败")
        }
        val rawList = json.opt("kbList") ?: throw ZFException("课表数据为空或格式异常")
        val courses = mutableListOf<Course>()
        if (rawList is JSONArray) {
            for (i in 0 until rawList.length()) {
                courses.add(Course.fromJson(rawList.getJSONObject(i)))
            }
        }
        val xsxx = json.optJSONObject("xsxx")
        val studentName = xsxx?.optString("XM", "") ?: ""
        val semesterLabel = xsxx?.optString("XQMMC", "")?.takeIf { it.isNotEmpty() } ?: semester.label

        ScheduleResult(courses = courses, studentName = studentName, semesterLabel = semesterLabel)
    }

    /** 退出登录并清除本地会话。 */
    suspend fun logout() {
        try {
            request("$PATH_PREFIX/xtgl/login_logoutAccount.html")
        } catch (_: Exception) {
            // 忽略退出接口失败，仍清理本地会话
        }
        cookieJar.clear()
    }

    // ---- 解析辅助 ----

    /** 解析手机版列表接口返回（JSON 数组；登录过期/异常页面抛错）。 */
    private fun parseListResponse(body: String, fromJson: (JSONObject) -> JSONObject): List<JSONObject> {
        if (body.trimStart().startsWith("<")) {
            if (body.contains("login_slogin")) {
                throw ZFException("登录已过期，请重新登录", sessionExpired = true)
            }
            throw ZFException("教务系统返回了异常页面")
        }
        val decoded = try {
            JSONArray(body)
        } catch (_: Exception) {
            throw ZFException("数据格式异常")
        }
        val result = mutableListOf<JSONObject>()
        for (i in 0 until decoded.length()) {
            val item = decoded.optJSONObject(i) ?: continue
            result.add(item)
        }
        return result
    }

    private data class SelectOption(val value: String, val label: String, val selected: Boolean)

    /** 提取指定 id 元素的指定属性值，如 csrftoken 的 value。 */
    private fun extractAttr(html: String, id: String, attr: String): String? {
        val tagRe = Regex("""<input\b[^>]*\bid="$id"[^>]*>""", RegexOption.IGNORE_CASE)
        for (m in tagRe.findAll(html)) {
            val v = Regex("""\b$attr="([^"]*)"""", RegexOption.IGNORE_CASE)
                .find(m.value)?.groupValues?.get(1)
            if (v != null) return v
        }
        return null
    }

    /** 收集页面所有隐藏表单字段（登录时原样提交，与浏览器一致）。 */
    private fun extractHiddenFields(html: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        val inputRe = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
        for (m in inputRe.findAll(html)) {
            val tag = m.value
            val type = Regex("""\btype="([^"]*)"""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1) ?: ""
            if (type.lowercase() != "hidden") continue
            val name = Regex("""\bname="([^"]*)"""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1)
            if (name.isNullOrEmpty()) continue
            val value = Regex("""\bvalue="([^"]*)"""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1) ?: ""
            fields[name] = value
        }
        return fields
    }

    private fun parseSelect(html: String, id: String): List<SelectOption> {
        val result = mutableListOf<SelectOption>()
        val selectRe = Regex(
            """<select\b[^>]*\bid="$id"[^>]*>(.*?)</select>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val m = selectRe.find(html) ?: return result
        val optionRe = Regex(
            """<option\b([^>]*)>([^<]*)</option>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        for (om in optionRe.findAll(m.groupValues[1])) {
            val attrs = om.groupValues[1]
            val value = Regex("""\bvalue="([^"]*)"""")
                .find(attrs)?.groupValues?.get(1) ?: continue
            val label = om.groupValues[2].trim().ifEmpty { value }
            val selected = attrs.contains("selected")
            result.add(SelectOption(value, label, selected))
        }
        return result
    }

}

/** 当前系统时区日期（供 UI 层使用）。 */
fun todayLocal(): LocalDate = LocalDate.now(ZoneId.systemDefault())
