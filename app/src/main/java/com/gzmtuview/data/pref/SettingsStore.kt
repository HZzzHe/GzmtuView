package com.gzmtuview.data.pref

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gzmtuview.data.model.Semester
import com.gzmtuview.data.net.CredentialStore
import com.gzmtuview.data.net.Credentials
import com.gzmtuview.data.net.SemesterCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 需「立即生效」的显示开关集合；默认值与 [SettingsStore] 各 readBool 默认一致。 */
data class DisplayPrefs(
    val showStudentName: Boolean = false,
    val showTeacherName: Boolean = true,
    val showGridLines: Boolean = false,
    val showCourseCapsule: Boolean = true,
    val showWeekStepper: Boolean = true,
    val showExamCountdown: Boolean = true,
)

/**
 * 本地设置存储：账号密码使用系统安全存储（EncryptedSharedPreferences / Keystore）。
 * 同时作为 [CredentialStore] 与 [SemesterCache] 的实现。
 */
class SettingsStore(private val context: Context) : CredentialStore, SemesterCache {

    // ---- DataStore 键 ----
    private object K {
        val validSemesters = stringPreferencesKey("valid_semesters")
        val baseUrl = stringPreferencesKey("zf_base_url")
        val courseColorMap = stringPreferencesKey("course_color_map")
        val showStudentName = booleanPreferencesKey("show_student_name")
        val showGridLines = booleanPreferencesKey("show_grid_lines")
        val showCourseCapsule = booleanPreferencesKey("show_course_capsule")
        val showTeacherName = booleanPreferencesKey("show_teacher_name")
        val showExamCountdown = booleanPreferencesKey("show_exam_countdown")
        val preferHttps = booleanPreferencesKey("prefer_https")
        val skippedCourses = stringPreferencesKey("skipped_courses")
        val holidayAdjust = booleanPreferencesKey("holiday_adjust")
        val hapticFeedback = booleanPreferencesKey("haptic_feedback")
        val autoCheckUpdate = booleanPreferencesKey("auto_check_update")
        val showWeekStepper = booleanPreferencesKey("show_week_stepper")
        val wallpaper = booleanPreferencesKey("wallpaper")
        val wallpaperDim = floatPreferencesKey("wallpaper_dim")
        val wallpaperBlur = floatPreferencesKey("wallpaper_blur")
        val wallpaperGlobal = booleanPreferencesKey("wallpaper_global")
        val lastSyncDay = stringPreferencesKey("last_sync_day")
        val splashAd = booleanPreferencesKey("splash_ad")
    }

    /** 有效学期缓存有效期（毫秒）。 */
    companion object {
        const val SEMESTERS_CACHE_TTL_MS = 3L * 24 * 3600 * 1000
        private const val COLOR_MAP_VER = 2
        private const val CRED_FILE = "gmuview_secure"

        /** 壁纸压暗遮罩（黑色）默认不透明度：0=不压。明暗两色下都只压暗，不压白。 */
        const val WALLPAPER_DIM_DEFAULT = 0.72f

        /** 压暗上限：留一成壁纸，否则 100% 等于没设背景。 */
        const val WALLPAPER_DIM_MAX = 0.85f

        /** 壁纸高斯模糊半径（dp）：默认不糊，上限够把大面积杂色压成背景。 */
        const val WALLPAPER_BLUR_DEFAULT = 0f
        const val WALLPAPER_BLUR_MAX = 60f
    }

    // ---- 加密凭据存储 ----
    private val securePrefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                CRED_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // 极少数设备 Keystore 不可用：回退普通存储（不阻塞登录）
            context.getSharedPreferences(CRED_FILE, Context.MODE_PRIVATE)
        }
    }

    private suspend fun prefs(): Preferences = context.settingsDataStore.data.first()
    private suspend fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }

    private fun readString(key: Preferences.Key<String>): String? =
        runBlocking { prefs()[key] }

    private fun readBool(key: Preferences.Key<Boolean>, default: Boolean): Boolean =
        runBlocking { prefs()[key] ?: default }

    private fun writeBool(key: Preferences.Key<Boolean>, value: Boolean) {
        runBlocking { update { it[key] = value } }
    }

    private fun readFloat(key: Preferences.Key<Float>, default: Float): Float =
        runBlocking { prefs()[key] ?: default }

    private fun writeFloat(key: Preferences.Key<Float>, value: Float) {
        runBlocking { update { it[key] = value } }
    }

    // ---- 凭据 ----

    override suspend fun loadCredentials(): Credentials? {
        val sp = securePrefs ?: return null
        val username = sp.getString("zf_username", "") ?: return null
        if (username.isEmpty()) return null
        val password = sp.getString("zf_password", "")
        return Credentials(username, password?.takeIf { it.isNotEmpty() })
    }

    suspend fun saveCredentials(username: String, password: String) {
        val sp = securePrefs ?: return
        sp.edit().putString("zf_username", username).putString("zf_password", password).apply()
    }

    suspend fun clearCredentials() {
        val sp = securePrefs ?: return
        sp.edit().remove("zf_username").remove("zf_password").apply()
    }

    // ---- 教务系统地址 ----

    fun loadBaseUrl(): String? = readString(K.baseUrl)

    fun saveBaseUrl(url: String) {
        runBlocking { update { it[K.baseUrl] = url } }
    }

    // ---- 有效学期缓存 ----

    override suspend fun loadValidSemesters(): List<Semester>? {
        val raw = readString(K.validSemesters) ?: return null
        return try {
            val json = JSONObject(raw)
            val fetchedAt = json.getLong("fetchedAt")
            if (System.currentTimeMillis() - fetchedAt > SEMESTERS_CACHE_TTL_MS) return null
            val items = json.getJSONArray("items")
            val list = mutableListOf<Semester>()
            for (i in 0 until items.length()) {
                val m = items.getJSONObject(i)
                list.add(Semester(xnm = m.getString("xnm"), xqm = m.getString("xqm")))
            }
            list
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun saveValidSemesters(semesters: List<Semester>) {
        val json = JSONObject().apply {
            put("fetchedAt", System.currentTimeMillis())
            put("items", org.json.JSONArray().apply {
                for (s in semesters) put(JSONObject().put("xnm", s.xnm).put("xqm", s.xqm))
            })
        }
        runBlocking { update { it[K.validSemesters] = json.toString() } }
    }

    // ---- 每日合并同步的「今天这一趟已跑完」标记 ----

    /**
     * 只记录「今天这一趟合并同步已经跑完」，不冒充任何数据的新鲜度：
     * 课表/考试/成绩的 `fetchedAt` 仍各自负责断点续传与变化比对。
     * 不能拿三份缓存的 `fetchedAt` 当门控——考试/成绩接口失败是设计内的静默降级，
     * 那两份缓存永远到不了「今天」，门控就永远不成立，表现为每次打开都重同步。
     */
    suspend fun loadLastSyncDay(): String? = prefs()[K.lastSyncDay]

    suspend fun saveLastSyncDay(day: String) {
        update { it[K.lastSyncDay] = day }
    }

    // ---- 课程颜色映射缓存 ----

    /** 读取课程颜色映射缓存；返回 (hash, map)；无缓存或版本不符返回 null。 */
    fun loadCourseColorMap(): Pair<String, Map<String, Int>>? {
        val raw = readString(K.courseColorMap) ?: return null
        return try {
            val json = JSONObject(raw)
            if (json.optInt("ver") != COLOR_MAP_VER) return null
            val map = mutableMapOf<String, Int>()
            val obj = json.getJSONObject("map")
            for (k in obj.keys()) map[k] = obj.getInt(k)
            json.getString("hash") to map
        } catch (_: Exception) {
            null
        }
    }

    fun saveCourseColorMap(hash: String, map: Map<String, Int>) {
        val json = JSONObject().apply {
            put("ver", COLOR_MAP_VER)
            put("hash", hash)
            put("map", org.json.JSONObject().apply {
                for ((k, v) in map) put(k, v)
            })
        }
        runBlocking { update { it[K.courseColorMap] = json.toString() } }
    }

    // ---- 「不上了」课程标记（仅当天那节课；课表半透明 + 小部件隐藏，换行分隔存储） ----

    /** 读取标记集；丢弃旧版课程级 key 与已过期日期的 key（语义改为按天，过期即无意义）。 */
    fun loadSkippedCourses(): Set<String> {
        val today = java.time.LocalDate.now()
        return readString(K.skippedCourses)?.split("\n")?.filter { key ->
            val date = key.substringAfterLast('|', "")
            key.contains('|') && date.isNotEmpty() &&
                runCatching { java.time.LocalDate.parse(date) >= today }.getOrDefault(false)
        }?.toSet() ?: emptySet()
    }

    fun saveSkippedCourses(keys: Set<String>) {
        runBlocking { update { it[K.skippedCourses] = keys.joinToString("\n") } }
    }

    // ---- 节假日调休（beta，默认关闭） ----

    fun loadHolidayAdjust(): Boolean = readBool(K.holidayAdjust, false)
    fun saveHolidayAdjust(value: Boolean) = writeBool(K.holidayAdjust, value)

    // ---- 交互触感（默认关闭） ----

    fun loadHapticFeedback(): Boolean = readBool(K.hapticFeedback, false)
    fun saveHapticFeedback(value: Boolean) = writeBool(K.hapticFeedback, value)

    // ---- 自定义壁纸（beta，默认关；实时生效靠 ui/wallpaper/Wallpaper 写穿状态） ----

    fun loadWallpaperEnabled(): Boolean = readBool(K.wallpaper, false)
    fun saveWallpaperEnabled(value: Boolean) = writeBool(K.wallpaper, value)

    /** 读取时夹到上限：旧版本存过 1.0，那样壁纸会被完全盖掉。 */
    fun loadWallpaperDim(): Float =
        readFloat(K.wallpaperDim, WALLPAPER_DIM_DEFAULT).coerceIn(0f, WALLPAPER_DIM_MAX)
    fun saveWallpaperDim(value: Float) = writeFloat(K.wallpaperDim, value)

    fun loadWallpaperBlur(): Float =
        readFloat(K.wallpaperBlur, WALLPAPER_BLUR_DEFAULT).coerceIn(0f, WALLPAPER_BLUR_MAX)
    fun saveWallpaperBlur(value: Float) = writeFloat(K.wallpaperBlur, value)

    /** true = 壁纸作全局背板（所有页与玻璃件统一折射）；false = 仅课表页显示。 */
    fun loadWallpaperGlobal(): Boolean = readBool(K.wallpaperGlobal, true)
    fun saveWallpaperGlobal(value: Boolean) = writeBool(K.wallpaperGlobal, value)

    // ---- 显示偏好（辅助线/学生姓名默认关，其余默认开） ----

    fun loadShowStudentName(): Boolean = readBool(K.showStudentName, false)
    fun saveShowStudentName(value: Boolean) = writeBool(K.showStudentName, value)

    fun loadShowTeacherName(): Boolean = readBool(K.showTeacherName, true)
    fun saveShowTeacherName(value: Boolean) = writeBool(K.showTeacherName, value)

    fun loadShowGridLines(): Boolean = readBool(K.showGridLines, false)
    fun saveShowGridLines(value: Boolean) = writeBool(K.showGridLines, value)

    /** 课程卡片顶部那条装饰胶囊。关掉只去胶囊，文字位置与卡片排版都不变。 */
    fun loadShowCourseCapsule(): Boolean = readBool(K.showCourseCapsule, true)
    fun saveShowCourseCapsule(value: Boolean) = writeBool(K.showCourseCapsule, value)

    fun loadShowWeekStepper(): Boolean = readBool(K.showWeekStepper, true)
    fun saveShowWeekStepper(value: Boolean) = writeBool(K.showWeekStepper, value)

    fun loadShowExamCountdown(): Boolean = readBool(K.showExamCountdown, true)
    fun saveShowExamCountdown(value: Boolean) = writeBool(K.showExamCountdown, value)

    /** 显示开关的响应式订阅：DataStore 变更即发射新值，页面无需重启即可跟随。 */
    fun displayPrefsFlow(): Flow<DisplayPrefs> = context.settingsDataStore.data.map { p ->
        DisplayPrefs(
            showStudentName = p[K.showStudentName] ?: false,
            showTeacherName = p[K.showTeacherName] ?: true,
            showGridLines = p[K.showGridLines] ?: false,
            showCourseCapsule = p[K.showCourseCapsule] ?: true,
            showWeekStepper = p[K.showWeekStepper] ?: true,
            showExamCountdown = p[K.showExamCountdown] ?: true,
        )
    }

    fun loadPreferHttps(): Boolean = readBool(K.preferHttps, true)
    fun savePreferHttps(value: Boolean) = writeBool(K.preferHttps, value)

    /** 启动时自动检查更新（默认开）；关闭后仍可在设置里手动检查。 */
    fun loadAutoCheckUpdate(): Boolean = readBool(K.autoCheckUpdate, true)
    fun saveAutoCheckUpdate(value: Boolean) = writeBool(K.autoCheckUpdate, value)

    // ---- 开屏摇一摇广告彩蛋（默认关；设置页「GZMTU View」连点 5 次切换） ----

    fun loadSplashAd(): Boolean = readBool(K.splashAd, false)
    fun saveSplashAd(value: Boolean) = writeBool(K.splashAd, value)
}
