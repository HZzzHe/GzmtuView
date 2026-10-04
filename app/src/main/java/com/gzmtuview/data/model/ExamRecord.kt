package com.gzmtuview.data.model

import org.json.JSONObject
import java.time.LocalDateTime

/** 考试信息记录（手机版考试接口返回项）。 */
data class ExamRecord(
    val kcmc: String,   // 课程名称
    val ksmc: String,   // 考试名称
    val kssj: String,   // 考试时间，如 "2026-07-13(14:30-16:30)"
    val kkxy: String,   // 开课学院
    val xqmc: String,   // 校区
    val jxlmc: String,  // 楼号
    val cdlbmc: String, // 场地类别
    val cdmc: String,   // 场地名称
    val zwh: String,    // 座位号
) {
    companion object {
        fun fromJson(json: JSONObject): ExamRecord = ExamRecord(
            kcmc = json.optString("kcmc"),
            ksmc = json.optString("ksmc"),
            kssj = json.optString("kssj"),
            kkxy = json.optString("kkxy"),
            xqmc = json.optString("xqmc"),
            jxlmc = json.optString("jxlmc"),
            cdlbmc = json.optString("cdlbmc"),
            cdmc = json.optString("cdmc"),
            zwh = json.optString("zwh"),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("kcmc", kcmc); put("ksmc", ksmc); put("kssj", kssj)
        put("kkxy", kkxy); put("xqmc", xqmc); put("jxlmc", jxlmc)
        put("cdlbmc", cdlbmc); put("cdmc", cdmc); put("zwh", zwh)
    }
}

/** 解析考试开始时间（格式 "2026-07-13(14:30-16:30)"）；无法解析返回 null。 */
fun parseExamStart(kssj: String): LocalDateTime? {
    val m = Regex("""(\d{4})-(\d{2})-(\d{2})\((\d{2}):(\d{2})""").find(kssj) ?: return null
    return try {
        LocalDateTime.of(
            m.groupValues[1].toInt(),
            m.groupValues[2].toInt(),
            m.groupValues[3].toInt(),
            m.groupValues[4].toInt(),
            m.groupValues[5].toInt(),
        )
    } catch (e: Exception) {
        null
    }
}

/** 考试倒计时文案：剩余 X 天/小时/分钟；已过（负值）返回 null 表示不显示。 */
fun examCountdownText(start: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String? {
    val seconds = java.time.Duration.between(now, start).seconds
    if (seconds > 0) {
        val days = seconds / 86400
        val hours = seconds / 3600
        val minutes = seconds / 60
        return when {
            days > 0 -> "剩余 $days 天"
            hours > 0 -> "剩余 $hours 小时"
            else -> "剩余 $minutes 分钟"
        }
    }
    return null
}

/** 格式化考试日期，如 "2026-07-13"；解析失败原样返回。 */
fun formatExamDate(kssj: String): String {
    val m = Regex("""(\d{4})-(\d{2})-(\d{2})""").find(kssj) ?: return kssj
    return m.value
}
