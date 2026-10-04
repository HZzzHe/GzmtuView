package com.gzmtuview.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import com.gzmtuview.MainActivity
import com.gzmtuview.R
import com.gzmtuview.data.model.HolidayAdjustCalendar
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

/**
 * GMU View 桌面小部件：今日课程（动态进度，最多 2 门）。
 *
 * 直接继承系统 AppWidgetProvider（不依赖 home_widget 插件类，避免 R8 误删父类
 * 导致 receiver 加载失败）；数据从 home_widget 写入的 SharedPreferences 读取。
 *
 * 数据 v6：全部课程 + 开学日 + 节次起止时间 + 竖线色索引 + 节假日调休开关。
 * - 按当天日期（星期 + 周次）过滤 → 当天课程
 * - 调休开启时按同一份离线表重排：放假日显示「××放假，今日无课」，
 *   补班日改上「被调走那天」的课表（日期行后缀「· 调休」），与课表页一致
 * - 按当前时间过滤已结束的课 → 动态展示"接下来要上的课"
 * - 配色跟随系统深浅色：浅色背景 #FCFDFF / 深色背景 #10151B（与课表页同款）
 * - 自动刷新：updatePeriodMillis + **AlarmManager 每小时定时广播**（updatePeriodMillis
 *   在部分 ROM 上不可靠，AlarmManager 定时是主保障，保证已结束课程及时消失）
 */
class GmuViewWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
  ) {
    val now = Calendar.getInstance()
    val prefs = context.getSharedPreferences(
        PREFERENCES_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_TODAY_COURSES, null)
    appWidgetIds.forEach { widgetId ->
      try {
        val views = buildViews(context, now, raw)
        appWidgetManager.updateAppWidget(widgetId, views)
      } catch (e: Exception) {
        // 兜底：异常时显示明确提示，避免小部件空白
        val views = fallbackViews(context, now)
        appWidgetManager.updateAppWidget(widgetId, views)
      }
    }
  }

  /// 小部件首次添加：注册每小时定时刷新（AlarmManager，不依赖 App 存活）。
  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    scheduleRefreshAlarm(context)
  }

  /// 最后一个小部件移除：取消定时刷新。
  override fun onDisabled(context: Context) {
    super.onDisabled(context)
    cancelRefreshAlarm(context)
  }

  /// 处理自定义定时刷新广播（显式 PendingIntent，无 manifest filter）与系统重启。
  /// updatePeriodMillis 在部分 ROM/Doze 下不可靠，此广播是自动刷新的主保障：
  /// 每次触发都按当前时间重算——已结束的课会消失、日期跨天会更新。
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      ACTION_WIDGET_REFRESH -> {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, GmuViewWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
          onUpdate(context, manager, ids)
        }
        return
      }
      Intent.ACTION_BOOT_COMPLETED -> {
        // 系统重启会清空 AlarmManager：若仍有小部件实例则重新注册定时刷新
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, GmuViewWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
          scheduleRefreshAlarm(context)
        }
        return
      }
    }
    super.onReceive(context, intent)
  }

  /// 每小时触发一次刷新广播（setInexactRepeating，无需权限；
  /// 相同 PendingIntent 重复注册会覆盖，幂等）。
  private fun scheduleRefreshAlarm(context: Context) {
    try {
      val alarm =
          context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      val pi = refreshPendingIntent(context)
      val firstAt = System.currentTimeMillis() + REFRESH_INTERVAL_MS
      alarm.setInexactRepeating(
          AlarmManager.RTC_WAKEUP, firstAt, REFRESH_INTERVAL_MS, pi)
    } catch (_: Exception) {
      // 定时失败不影响小部件（还有 updatePeriodMillis 兜底）
    }
  }

  private fun cancelRefreshAlarm(context: Context) {
    try {
      val alarm =
          context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      alarm.cancel(refreshPendingIntent(context))
    } catch (_: Exception) {
    }
  }

  private fun refreshPendingIntent(context: Context): PendingIntent =
      PendingIntent.getBroadcast(
          context,
          ALARM_REQUEST_CODE,
          Intent(context, GmuViewWidgetProvider::class.java)
              .setAction(ACTION_WIDGET_REFRESH),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

  private fun buildViews(context: Context, now: Calendar, raw: String?): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_today).apply {
      setOnClickPendingIntent(R.id.widget_root, launchAppIntent(context))
      val isNight = isNightMode(context)
      applyColors(this)

      if (raw == null || raw.isEmpty()) {
        showEmpty("打开 App 同步课表", now, this)
        return@apply
      }
      val data = JSONObject(raw)
      val week1Monday = data.optString("week1Monday", "")
      if (data.optInt("v", 0) < 3 || week1Monday.isEmpty()) {
        showEmpty("打开 App 同步课表", now, this)
        return@apply
      }

      setTextViewText(R.id.widget_title, "今日课程")
      setTextViewText(
          R.id.widget_date,
          SimpleDateFormat("M月d日 E", Locale.CHINA).format(now.time),
      )

      val week = weekOf(now, week1Monday)
      if (week < 0) {
        showEmpty("打开 App 同步课表", now, this)
        return@apply
      }
      if (week == 0) {
        showEmpty("假期中，暂无课程", now, this)
        return@apply
      }

      // 节假日调休（beta 开关随数据推送）：放假日无课；补班日整天上「被调走那天」的课表
      var effWeek = week
      var effCourseWeek = courseWeekOf(now)
      var dateSuffix = ""
      if (data.optBoolean("holidayAdjust", false)) {
        val today = toLocalDate(now)
        val rest = HolidayAdjustCalendar.holidayName(today)
        if (rest != null) {
          showEmpty("${rest}放假，今日无课", now, this)
          return@apply
        }
        val makeup = HolidayAdjustCalendar.makeupOf(today)
        val srcWeek = if (makeup == null) -1 else weekOf(makeup.replaysDate, week1Monday)
        if (makeup != null && srcWeek >= 1) {
          effWeek = srcWeek
          effCourseWeek = makeup.replaysDate.dayOfWeek.value
          dateSuffix = " · 调休"
        }
      }
      if (dateSuffix.isNotEmpty()) {
        setTextViewText(
            R.id.widget_date,
            SimpleDateFormat("M月d日 E", Locale.CHINA).format(now.time) + dateSuffix,
        )
      }

      val courses =
          upcomingCourses(data.optJSONArray("courses"), now, effWeek, effCourseWeek, skippedKeys(data))
      if (courses.length() == 0) {
        val hasAnyToday = hasAnyTodayCourse(
            data.optJSONArray("courses"), now, effWeek, effCourseWeek, skippedKeys(data))
        showEmpty(
            if (hasAnyToday) "今日课程已结束" else "今日无课，好好休息",
            now, this, dateSuffix)
        return@apply
      }

      setViewVisibility(R.id.widget_empty, View.GONE)
      for (i in 0 until MAX_COURSES) {
        if (i < courses.length()) {
          val c = courses.getJSONObject(i)
          setTextViewText(nameId(i + 1), c.optString("name", ""))
          setTextViewText(locationId(i + 1), c.optString("location", ""))
          val time = "${c.optString("startTime", "")} - ${c.optString("endTime", "")}"
          setTextViewText(timeId(i + 1), time.trim())
          // v5：竖线走资源 ID（drawable 内部引用 @color，宿主按系统主题自动解析），
          // 主题切换无需 App 重推即跟随；v3/v4 旧数据回退到 hex 直接上色。
          val barIndex = c.optInt("barIndex", -1)
          if (barIndex in 0 until BAR_COUNT) {
            setInt(barId(i + 1), "setBackgroundResource", barResId(barIndex))
          } else {
            val bar = c
                .optString(if (isNight) "barDark" else "barLight", "")
                .ifEmpty { c.optString("color", "#FFFFFF") }
            try {
              setInt(barId(i + 1), "setBackgroundColor", Color.parseColor(bar))
            } catch (_: Exception) {
              setInt(barId(i + 1), "setBackgroundColor", Color.parseColor("#FFFFFF"))
            }
          }
          setViewVisibility(rowId(i + 1), View.VISIBLE)
        } else {
          setViewVisibility(rowId(i + 1), View.GONE)
        }
      }
    }
    return views
  }

  private fun fallbackViews(context: Context, now: Calendar): RemoteViews =
      RemoteViews(context.packageName, R.layout.widget_today).apply {
        setOnClickPendingIntent(R.id.widget_root, launchAppIntent(context))
        applyColors(this)
        setTextViewText(R.id.widget_title, "今日课程")
        setTextViewText(
            R.id.widget_date,
            SimpleDateFormat("M月d日 E", Locale.CHINA).format(now.time))
        setTextViewText(R.id.widget_empty, "小部件异常，请重新打开 App 同步")
        setViewVisibility(R.id.widget_empty, View.VISIBLE)
        for (i in 1..MAX_COURSES) setViewVisibility(rowId(i), View.GONE)
      }

  /// 点击小部件 → 打开 App（不依赖 home_widget 的 LaunchIntent）。
  private fun launchAppIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
    return PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  }

  /// 系统当前是否深色模式（仅 v3/v4 旧数据回退选竖线色时使用）。
  private fun isNightMode(context: Context): Boolean {
    val mode =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return mode == Configuration.UI_MODE_NIGHT_YES
  }

  /// 课程竖线资源 ID（30 色，drawable 内部引用 @color/widget_bar_NN，
  /// 宿主按 values(-night) 限定符解析深浅色）。
  private fun barResId(index: Int): Int = when (index) {
    0 -> R.drawable.widget_bar_00
    1 -> R.drawable.widget_bar_01
    2 -> R.drawable.widget_bar_02
    3 -> R.drawable.widget_bar_03
    4 -> R.drawable.widget_bar_04
    5 -> R.drawable.widget_bar_05
    6 -> R.drawable.widget_bar_06
    7 -> R.drawable.widget_bar_07
    8 -> R.drawable.widget_bar_08
    9 -> R.drawable.widget_bar_09
    10 -> R.drawable.widget_bar_10
    11 -> R.drawable.widget_bar_11
    12 -> R.drawable.widget_bar_12
    13 -> R.drawable.widget_bar_13
    14 -> R.drawable.widget_bar_14
    15 -> R.drawable.widget_bar_15
    16 -> R.drawable.widget_bar_16
    17 -> R.drawable.widget_bar_17
    18 -> R.drawable.widget_bar_18
    19 -> R.drawable.widget_bar_19
    20 -> R.drawable.widget_bar_20
    21 -> R.drawable.widget_bar_21
    22 -> R.drawable.widget_bar_22
    23 -> R.drawable.widget_bar_23
    24 -> R.drawable.widget_bar_24
    25 -> R.drawable.widget_bar_25
    26 -> R.drawable.widget_bar_26
    27 -> R.drawable.widget_bar_27
    28 -> R.drawable.widget_bar_28
    else -> R.drawable.widget_bar_29
  }

  /// 应用配色：背景用资源限定符（宿主按系统主题自动解析）。
  /// 文字颜色由布局 XML 的 @color（values(-night) 限定符）在宿主 inflate 时
  /// 按系统主题解析——不再在 Provider 里 setTextColor（那是生成时的快照，
  /// 主题切换后不会更新，导致深/浅色下文字不跟随）。
  private fun applyColors(views: RemoteViews) {
    views.setInt(
        R.id.widget_root, "setBackgroundResource",
        R.drawable.widget_background)
  }

  private fun showEmpty(text: String, now: Calendar, views: RemoteViews, dateSuffix: String = "") {
    views.setTextViewText(R.id.widget_title, "今日课程")
    views.setTextViewText(
        R.id.widget_date,
        SimpleDateFormat("M月d日 E", Locale.CHINA).format(now.time) + dateSuffix)
    views.setTextViewText(R.id.widget_empty, text)
    views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
    for (i in 1..MAX_COURSES) views.setViewVisibility(rowId(i), View.GONE)
  }

  /// 「不上了」标记集合（App 课表标记的课程，小部件不显示其当天安排）。
  private fun skippedKeys(data: JSONObject): Set<String> {
    val arr = data.optJSONArray("skipped") ?: return emptySet()
    val out = HashSet<String>()
    for (i in 0 until arr.length()) out.add(arr.optString(i))
    return out
  }

  /// 课程是否被标记「不上了」（与 App 侧 skipKey 同格式：名|星期|起始节|yyyy-MM-dd；
  /// 小部件只展示当天，故用当天日期匹配——标记仅跳过那天的那节课）。
  private fun isSkipped(c: JSONObject, skipped: Set<String>, todayIso: String): Boolean =
      skipped.contains(
          "${c.optString("name")}|${c.optInt("dayOfWeek")}|${c.optInt("startSection")}|$todayIso")

  private fun upcomingCourses(
      raw: JSONArray?,
      now: Calendar,
      week: Int,
      courseWeek: Int,
      skipped: Set<String>,
  ): JSONArray {
    val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
    val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val list = ArrayList<JSONObject>()
    if (raw != null) {
      for (i in 0 until raw.length()) {
        val c = raw.getJSONObject(i)
        if (c.optInt("dayOfWeek", -1) != courseWeek) continue
        if (!weeksMatch(c.optJSONArray("weeks"), week)) continue
        if (isSkipped(c, skipped, todayIso)) continue
        val end = c.optString("endTime", "")
        if (end.isNotEmpty() && !isAfter(end, nowMinutes)) continue
        list.add(c)
      }
    }
    list.sortBy { it.optString("startTime", "") }
    val out = JSONArray()
    for (i in 0 until minOf(list.size, MAX_COURSES)) out.put(list[i])
    return out
  }

  private fun hasAnyTodayCourse(
      raw: JSONArray?,
      now: Calendar,
      week: Int,
      courseWeek: Int,
      skipped: Set<String>,
  ): Boolean {
    if (raw == null) return false
    val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
    for (i in 0 until raw.length()) {
      val c = raw.getJSONObject(i)
      if (c.optInt("dayOfWeek", -1) == courseWeek &&
          weeksMatch(c.optJSONArray("weeks"), week) &&
          !isSkipped(c, skipped, todayIso)) {
        return true
      }
    }
    return false
  }

  private fun weeksMatch(weeks: JSONArray?, week: Int): Boolean {
    if (weeks == null) return true
    for (j in 0 until weeks.length()) {
      if (weeks.getInt(j) == week) return true
    }
    return false
  }

  private fun isAfter(hhmm: String, nowMinutes: Int): Boolean {
    val parts = hhmm.split(":")
    if (parts.size != 2) return true
    val m = (parts[0].toIntOrNull() ?: return true) * 60 + (parts[1].toIntOrNull() ?: return true)
    return m > nowMinutes
  }

  private fun courseWeekOf(now: Calendar): Int {
    val dow = now.get(Calendar.DAY_OF_WEEK)
    return if (dow == Calendar.SUNDAY) 7 else dow - 1
  }

  private fun weekOf(now: Calendar, week1MondayIso: String): Int {
    if (week1MondayIso.isEmpty()) return -1
    return try {
      val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
      val monday = fmt.parse(week1MondayIso.take(10)) ?: return -1
      val mondayCal = Calendar.getInstance().apply {
        time = monday
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
      val days = ((now.timeInMillis - mondayCal.timeInMillis) / 86400000L).toInt()
      // 开学前（今天早于第 1 周周一）：必须直接判假期。
      // 不能走 days / 7 + 1 —— Kotlin/Java 整数除法向零截断，
      // days 为 -1~-6 时 days / 7 = 0，会被误判成第 1 周而显示未来课程。
      if (days < 0) return 0
      val week = days / 7 + 1
      if (week in 1..20) week else 0
    } catch (_: Exception) {
      -1
    }
  }

  /// 小部件时钟 → LocalDate（节假日/补班表按日期查）。
  private fun toLocalDate(cal: Calendar): LocalDate =
      LocalDate.of(
          cal.get(Calendar.YEAR),
          cal.get(Calendar.MONTH) + 1,
          cal.get(Calendar.DAY_OF_MONTH),
      )

  /// 指定日期落在第几周（与 Calendar 版同一套 1..20 规则）。
  /// 补班日借的是「被调走那天」的课表，那天主课可能整个学期只在那一周有。
  private fun weekOf(date: LocalDate, week1MondayIso: String): Int {
    if (week1MondayIso.isEmpty()) return -1
    val monday = try {
      LocalDate.parse(week1MondayIso.take(10))
    } catch (_: Exception) {
      return -1
    }
    val days = ChronoUnit.DAYS.between(monday, date).toInt()
    if (days < 0) return 0
    val week = days / 7 + 1
    return if (week in 1..20) week else 0
  }

  companion object {
    /// home_widget 的 SharedPreferences 文件名（与 Flutter 侧 saveWidgetData 一致）。
    const val PREFERENCES_NAME = "HomeWidgetPreferences"
    const val KEY_TODAY_COURSES = "today_courses"
    const val MAX_COURSES = 2

    /// 课程竖线色板数量（与课表页 30 色板一致）。
    const val BAR_COUNT = 30

    /// AlarmManager 定时刷新广播 action（显式 PendingIntent，无需 manifest filter）。
    const val ACTION_WIDGET_REFRESH = "com.gzmtuview.action.WIDGET_REFRESH"
    private const val ALARM_REQUEST_CODE = 0x5147

    /// 定时刷新间隔：1 小时（与 gmuview_widget.xml 的 updatePeriodMillis 一致）。
    const val REFRESH_INTERVAL_MS = 60 * 60 * 1000L

    private fun rowId(i: Int): Int = if (i == 1) R.id.widget_row_1 else R.id.widget_row_2
    private fun barId(i: Int): Int = if (i == 1) R.id.widget_bar_1 else R.id.widget_bar_2
    private fun nameId(i: Int): Int = if (i == 1) R.id.widget_name_1 else R.id.widget_name_2
    private fun locationId(i: Int): Int =
        if (i == 1) R.id.widget_location_1 else R.id.widget_location_2
    private fun timeId(i: Int): Int = if (i == 1) R.id.widget_time_1 else R.id.widget_time_2
  }
}
