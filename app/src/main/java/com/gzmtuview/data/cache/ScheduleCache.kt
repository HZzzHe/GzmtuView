package com.gzmtuview.data.cache

import android.content.Context
import com.gzmtuview.data.model.Course
import com.gzmtuview.data.model.ExamRecord
import com.gzmtuview.data.model.GradeRecord
import com.gzmtuview.data.model.ScheduleResult
import com.gzmtuview.data.model.Semester
import com.gzmtuview.data.model.WeekDates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** 课表缓存条目。 */
data class CachedSchedule(
    val fetchedAt: LocalDate,
    val result: ScheduleResult,
    val weekDates: WeekDates? = null,
)

/** 通用列表缓存条目（考试 / 成绩）。 */
data class CachedItems<T>(
    val fetchedAt: LocalDate,
    val items: List<T>,
)

/** 带学期回退的加载结果。 */
data class FallbackResult<T>(
    val semester: Semester,
    val items: List<T>,
    /** 因无数据而回退的来源学期（null 表示未回退）。 */
    val fallbackFrom: Semester? = null,
    /** true 表示命中跨天缓存（调用方应先展示，再后台刷新）。 */
    val stale: Boolean = false,
)

/**
 * 课表本地缓存：按学期存储 JSON 文件。
 * 策略：当天缓存命中 → 直接使用不请求；跨天 → 先展示缓存后台静默更新。
 */
class ScheduleCache(private val context: Context) {

    private fun dir(): File =
        File(context.filesDir, "schedule_cache").apply { mkdirs() }

    private fun fileName(s: Semester) = "${s.xnm}_${s.xqm}.json"

    /**
     * 原子落盘：先写同名 `.tmp` 再 rename 覆盖。
     * 直接 writeText 会在进程被杀时留下半截 JSON，下次启动读不出缓存，
     * 课表页会掉进「全屏加载等同步」而不是缓存秒开（2026-11-04 真机确认）。
     * rename 在同一目录内是原子的；失败时保留旧文件，不让缓存变空。
     */
    private fun writeAtomically(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw java.io.IOException("rename failed: ${file.name}")
        }
    }

    /** 读取指定学期的缓存；无缓存或损坏返回 null。 */
    suspend fun load(semester: Semester): CachedSchedule? = withContext(Dispatchers.IO) {
        try {
            val file = File(dir(), fileName(semester))
            if (!file.exists()) return@withContext null
            val json = JSONObject(file.readText())
            val fetchedAt = LocalDate.parse(json.getString("fetchedAt"))
            val courses = mutableListOf<Course>()
            val arr = json.getJSONArray("courses")
            for (i in 0 until arr.length()) {
                courses.add(Course.fromJson(arr.getJSONObject(i)))
            }
            CachedSchedule(
                fetchedAt = fetchedAt,
                weekDates = WeekDates.fromJson(json.optJSONObject("weekDates")),
                result = ScheduleResult(
                    courses = courses,
                    studentName = json.optString("studentName", ""),
                    semesterLabel = json.optString("semesterLabel", ""),
                ),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** 写入指定学期的缓存（失败静默，不阻塞主流程）。 */
    suspend fun save(
        semester: Semester,
        result: ScheduleResult,
        weekDates: WeekDates? = null,
    ) = withContext(Dispatchers.IO) {
        try {
            val file = File(dir(), fileName(semester))
            val json = JSONObject().apply {
                put("fetchedAt", LocalDate.now().toString())
                put("studentName", result.studentName)
                put("semesterLabel", result.semesterLabel)
                put("courses", JSONArray().apply {
                    for (c in result.courses) put(c.toJson())
                })
            }
            if (weekDates != null) {
                json.put("weekDates", WeekDates.toJson(weekDates))
            } else {
                // 本次没拿到周次日期（接口抖动/学期不匹配）：保留文件里上一份好值。
                // 开学日是同学期内的常量，不能因为一次请求失败就从缓存里消失——
                // 小部件正是靠它算「今天第几周」，丢了就只剩「打开 App 同步课表」。
                runCatching {
                    JSONObject(file.readText()).optJSONObject("weekDates")
                        ?.let { json.put("weekDates", it) }
                }
            }
            writeAtomically(file, json.toString())
        } catch (_: Exception) {
            // 缓存写入失败不影响使用
        }
    }

    /** 清空全部缓存。 */
    suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            dir().deleteRecursively()
        } catch (_: Exception) {
        }
    }

    /** 是否存在任意缓存（AppRoot 的「免网络进门」判定用）。 */
    suspend fun hasAnyCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            dir().listFiles()?.any { it.name.endsWith(".json") } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** 当天是否有新鲜课表缓存（启动时免会话检查的判断依据）。 */
    suspend fun hasFreshCacheToday(): Boolean = withContext(Dispatchers.IO) {
        try {
            val sems = loadCachedSemesters()
            if (sems.isEmpty()) return@withContext false
            val c = load(sems.first()) ?: return@withContext false
            c.fetchedAt == LocalDate.now()
        } catch (_: Exception) {
            false
        }
    }

    /** 从缓存文件恢复已缓存的学期列表（时间倒序）。 */
    suspend fun loadCachedSemesters(): List<Semester> = withContext(Dispatchers.IO) {
        try {
            val sems = sortedSetOf<Semester>(compareByDescending { it.rank })
            val re = Regex("""^(?:exams_pc_|exams_|grades_)?(\d{4})_(\d{1,2})\.json$""")
            dir().listFiles()?.forEach { f ->
                val m = re.find(f.name)
                if (m != null) {
                    val sem = Semester(xnm = m.groupValues[1], xqm = m.groupValues[2])
                    if (sem.isStandard) sems.add(sem)
                }
            }
            sems.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---- 考试 / 成绩缓存（每学期独立文件） ----

    // 前缀带 _pc：手机版考试模块下线前的 exams_*.json（含当天空结果）一律不再命中，
    // 避免升级后当天仍被旧缓存挡住、看不到 PC 接口返回的考试。
    suspend fun loadExams(s: Semester): CachedItems<ExamRecord>? =
        loadList(s, "exams_pc") { ExamRecord.fromJson(it) }

    suspend fun saveExams(s: Semester, items: List<ExamRecord>) = saveList(s, "exams_pc", items) {
        it.toJson()
    }

    suspend fun loadGrades(s: Semester): CachedItems<GradeRecord>? =
        loadList(s, "grades") { GradeRecord.fromJson(it) }

    suspend fun saveGrades(s: Semester, items: List<GradeRecord>) = saveList(s, "grades", items) {
        it.toJson()
    }

    private suspend fun <T> loadList(
        s: Semester,
        kind: String,
        fromJson: (JSONObject) -> T,
    ): CachedItems<T>? = withContext(Dispatchers.IO) {
        try {
            val file = File(dir(), "${kind}_${s.xnm}_${s.xqm}.json")
            if (!file.exists()) return@withContext null
            val json = JSONObject(file.readText())
            val fetchedAt = LocalDate.parse(json.getString("fetchedAt"))
            val items = mutableListOf<T>()
            val arr = json.getJSONArray("items")
            for (i in 0 until arr.length()) {
                items.add(fromJson(arr.getJSONObject(i)))
            }
            CachedItems(fetchedAt, items)
        } catch (_: Exception) {
            null
        }
    }


    /**
     * 带学期回退的加载（考试/成绩共用）。策略对齐 Flutter fetchWithFallback。
     *
     * [probeOlderOnEmpty]：当前学期取到空结果时，是否继续联网逐学期探测更早学期。
     * 考试/成绩页传 false——更早学期只从磁盘缓存回退。否则本学期本来就没有考试时，
     * 每次重开软件都要把整条回退链重新请求一遍（用户反馈的「重复拉取」）。
     */
    suspend fun <T> fetchWithFallback(
        semesters: List<Semester>,
        initial: Semester,
        fetch: suspend (Semester) -> List<T>,
        loadCache: suspend (Semester) -> CachedItems<T>?,
        saveCache: suspend (Semester, List<T>) -> Unit,
        forceRefresh: Boolean = false,
        probeOlderOnEmpty: Boolean = true,
    ): FallbackResult<T> {
        var idx = semesters.indexOf(initial)
        if (idx < 0) idx = 0
        val today = LocalDate.now()

        // 当前学期非空缓存：当天直接用，跨天 stale（强制刷新时跳过）
        if (!forceRefresh) {
            val cached = loadCache(initial)
            if (cached != null && cached.items.isNotEmpty()) {
                if (cached.fetchedAt == today) {
                    return FallbackResult(initial, cached.items)
                }
                return FallbackResult(initial, cached.items, stale = true)
            }
            // 当天空缓存：直接从其他学期缓存回退展示
            if (cached != null && cached.fetchedAt == today) {
                for (i in idx + 1 until semesters.size) {
                    val c = loadCache(semesters[i])
                    if (c != null && c.items.isNotEmpty()) {
                        return FallbackResult(semesters[i], c.items, fallbackFrom = initial)
                    }
                }
                return FallbackResult(initial, emptyList())
            }
        }

        // 网络请求 + 逐级回退（当前学期空/无缓存时不拦截）
        var fallbackFrom: Semester? = null
        while (idx < semesters.size) {
            val sem = semesters[idx]
            // 回退途中命中其他学期当天非空缓存则直接用（强制刷新时跳过）
            if (!forceRefresh && fallbackFrom != null) {
                val c = loadCache(sem)
                if (c != null && c.items.isNotEmpty() && c.fetchedAt == today) {
                    return FallbackResult(sem, c.items, fallbackFrom = initial)
                }
            }
            try {
                val items = fetch(sem)
                saveCache(sem, items)
                if (items.isNotEmpty() || idx == semesters.size - 1) {
                    return FallbackResult(
                        semester = sem,
                        items = items,
                        fallbackFrom = if (fallbackFrom == null) null else initial,
                    )
                }
                if (!probeOlderOnEmpty) {
                    // 只联网问当前学期；空结果时从更早学期的磁盘缓存回退，不再逐级请求
                    for (i in idx + 1 until semesters.size) {
                        val c = loadCache(semesters[i])
                        if (c != null && c.items.isNotEmpty()) {
                            return FallbackResult(semesters[i], c.items, fallbackFrom = initial)
                        }
                    }
                    return FallbackResult(initial, emptyList())
                }
                fallbackFrom = fallbackFrom ?: sem
                idx++
            } catch (e: Exception) {
                if (fallbackFrom == null) {
                    // 网络失败：优先从其他学期缓存（非空）回退展示
                    for (i in idx + 1 until semesters.size) {
                        val c = loadCache(semesters[i])
                        if (c != null && c.items.isNotEmpty()) {
                            return FallbackResult(semesters[i], c.items, fallbackFrom = initial)
                        }
                    }
                    // 其他学期无可用缓存：若当前学期已有缓存则展示缓存
                    val cached = loadCache(initial)
                    if (cached != null) {
                        return FallbackResult(initial, cached.items)
                    }
                    throw e
                }
                // 回退途中失败：继续尝试下一个学期（BUG-003 对齐）
                idx++
            }
        }
        return FallbackResult(initial, emptyList(), fallbackFrom = initial)
    }
    private suspend fun <T> saveList(
        s: Semester,
        kind: String,
        items: List<T>,
        toJson: (T) -> JSONObject,
    ) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("fetchedAt", LocalDate.now().toString())
                put("items", JSONArray().apply { for (it in items) put(toJson(it)) })
            }
            writeAtomically(File(dir(), "${kind}_${s.xnm}_${s.xqm}.json"), json.toString())
        } catch (_: Exception) {
        }
    }
}

/** 是否为当天（本地日期相等）。 */
fun isToday(d: LocalDate): Boolean = d == LocalDate.now()

/** 课表是否有变化（课程指纹集合比较，与顺序无关）。 */
fun coursesChanged(before: List<Course>, after: List<Course>): Boolean {
    fun signature(c: Course) = "${c.name}|${c.teacher}|${c.location}|${c.campus}|" +
        "${c.dayOfWeek}|${c.sections.joinToString(",")}|${c.weeks.joinToString(",")}|${c.weeksText}"
    return setChanged(before.map { signature(it) }.toSet(), after.map { signature(it) }.toSet())
}

/** 考试列表是否有变化（指纹集合比较）。 */
fun examListChanged(before: List<ExamRecord>, after: List<ExamRecord>): Boolean {
    fun sig(e: ExamRecord) = "${e.kcmc}|${e.ksmc}|${e.kssj}|${e.cdmc}|${e.zwh}"
    return setChanged(before.map { sig(it) }.toSet(), after.map { sig(it) }.toSet())
}

/** 成绩列表是否有变化（指纹集合比较）。 */
fun gradeListChanged(before: List<GradeRecord>, after: List<GradeRecord>): Boolean {
    fun sig(g: GradeRecord) = "${g.kch}|${g.kcmc}|${g.cj}|${g.xf}|${g.jd}|${g.cjbz}"
    return setChanged(before.map { sig(it) }.toSet(), after.map { sig(it) }.toSet())
}

private fun setChanged(a: Set<String>, b: Set<String>): Boolean {
    if (a.size != b.size) return true
    for (k in a) if (k !in b) return true
    return false
}
