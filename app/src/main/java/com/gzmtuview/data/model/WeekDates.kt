package com.gzmtuview.data.model

import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 学期周次-日期信息（由手机版时间表页面的第 1 周日期推导）。 */
data class WeekDates(
    val week1Monday: LocalDate,  // 第 1 周周一（开学日期）
    val weekCount: Int,          // 学期总周数（广州航海学院固定 20 周）
    val xnm: String,             // 页面所属学期（用于与用户所选学期比对）
    val xqm: String,
) {
    /** 第 [week] 周的周一。 */
    fun mondayOf(week: Int): LocalDate = week1Monday.plusDays((week - 1) * 7L)

    /** 第 [week] 周星期 [dayOfWeek]（1=周一..7=周日）的日期。 */
    fun dateOf(week: Int, dayOfWeek: Int): LocalDate =
        mondayOf(week).plusDays((dayOfWeek - 1).toLong())

    /** 日期对应的周数；不在学期内（开学前/结课后）返回第 1 周。 */
    fun weekOf(date: LocalDate): Int {
        val diff = ChronoUnit.DAYS.between(week1Monday, date)
        if (diff < 0) return 1
        val week = (diff / 7).toInt() + 1
        return if (week <= weekCount) week else 1
    }

    /** 是否属于指定学期。 */
    fun matches(semester: Semester): Boolean =
        xnm == semester.xnm && xqm == semester.xqm

    companion object {
        /** 序列化为 JSON（供缓存使用）。 */
        fun toJson(wd: WeekDates): JSONObject = JSONObject().apply {
            put("week1Monday", wd.week1Monday.toString())
            put("weekCount", wd.weekCount)
            put("xnm", wd.xnm)
            put("xqm", wd.xqm)
        }

        /** 从 JSON 还原；解析失败返回 null。 */
        fun fromJson(json: JSONObject?): WeekDates? {
            if (json == null) return null
            return try {
                WeekDates(
                    week1Monday = LocalDate.parse(json.getString("week1Monday")),
                    weekCount = json.getInt("weekCount"),
                    xnm = json.getString("xnm"),
                    xqm = json.getString("xqm"),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** 课表查询结果。 */
data class ScheduleResult(
    val courses: List<Course>,
    val studentName: String,
    val semesterLabel: String,
)
