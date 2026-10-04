package com.gzmtuview.widget

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.gzmtuview.data.model.Course
import com.gzmtuview.data.model.WeekDates
import com.gzmtuview.data.model.kDefaultSectionTimes
import org.json.JSONArray
import org.json.JSONObject

/**
 * 桌面小部件数据推送（对齐 Flutter widget_service.dart）。
 *
 * 推送"全部课程 + 开学日 + 节次时间 + 课程颜色索引"（v6 JSON，含节假日调休开关）到
 * SharedPreferences（与 Provider 约定相同的 key），随后广播触发即时刷新。
 * 失败静默：小部件数据过期不影响 App 使用。
 */
object WidgetPusher {

    /** 与 Provider 约定的 SharedPreferences 文件名/key。 */
    const val PREFERENCES_NAME = "HomeWidgetPreferences"
    const val KEY_TODAY_COURSES = "today_courses"
    const val ACTION_WIDGET_REFRESH = "com.gzmtuview.action.WIDGET_REFRESH"

    /** 节次起止时间（"HH:mm"）；未知节次返回空串。 */
    fun sectionTime(section: Int, end: Boolean): String {
        val target = kDefaultSectionTimes.find { it.section == section } ?: return ""
        return if (end) target.end else target.start
    }

    /** 课程竖线色索引（0~29）：优先课表页分配映射；否则按课程名哈希回退。 */
    fun barColorIndex(name: String, courseColorIndex: Map<String, Int>?): Int {
        val mapped = courseColorIndex?.get(name)
        if (mapped != null) return mapped
        // hashCode 可能为 Int.MIN_VALUE：屏蔽符号位后取模，保证落在 0..29
        return (name.hashCode() and Int.MAX_VALUE) % 30
    }

    /** 生成 v6 数据 JSON（全部课程 + 开学日 + 时间 + 竖线色索引 + 「不上了」标记 + 调休开关）。 */
    fun buildJson(
        courses: List<Course>,
        weekDates: WeekDates?,
        courseColorIndex: Map<String, Int>?,
        skippedKeys: Set<String> = emptySet(),
        holidayAdjust: Boolean = false,
    ): String {
        val arr = JSONArray()
        for (c in courses) {
            if (c.sections.isEmpty()) continue
            val weeks = JSONArray()
            for (w in c.weeks) weeks.put(w)
            val first = c.sections.first()
            val last = c.sections.last()
            arr.put(
                JSONObject()
                    .put("dayOfWeek", c.dayOfWeek)
                    .put("weeks", weeks)
                    .put("sections", "$first-${last}节")
                    .put("startSection", first)
                    .put("startTime", sectionTime(first, end = false))
                    .put("endTime", sectionTime(last, end = true))
                    .put("name", c.name)
                    .put("location", c.location)
                    .put("barIndex", barColorIndex(c.name, courseColorIndex)),
            )
        }
        val skipped = JSONArray()
        for (k in skippedKeys) skipped.put(k)
        return JSONObject()
            .put("v", 6)
            .put("week1Monday", weekDates?.week1Monday?.toString() ?: "")
            .put("holidayAdjust", holidayAdjust)
            .put("courses", arr)
            .put("skipped", skipped)
            .toString()
    }

    /** 写入小部件数据并触发即时刷新；失败静默。 */
    fun push(
        context: Context,
        courses: List<Course>,
        weekDates: WeekDates?,
        courseColorIndex: Map<String, Int>?,
        skippedKeys: Set<String> = emptySet(),
        holidayAdjust: Boolean = false,
    ) {
        try {
            if (courses.isEmpty()) return
            val prefs: SharedPreferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(
                    KEY_TODAY_COURSES,
                    buildJson(courses, weekDates, courseColorIndex, skippedKeys, holidayAdjust),
                )
                .apply()
            context.sendBroadcast(
                Intent(context, GmuViewWidgetProvider::class.java)
                    .setAction(ACTION_WIDGET_REFRESH),
            )
        } catch (_: Exception) {
            // 推送失败不影响主流程
        }
    }
}
