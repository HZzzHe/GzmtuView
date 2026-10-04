package com.gzmtuview.data.model

import org.json.JSONObject

/**
 * 课程模型（对应正方课表接口 kbList 中的一项）。
 * 字段映射：kcmc->name, xm->teacher, cdmc->location, xqmc->campus,
 * jxbmc->className, xqj->dayOfWeek, jc->sections, zcd->weeks,
 * xf->credit, kcxszc->type。
 */
data class Course(
    val name: String,
    val teacher: String,
    val location: String,
    val campus: String,
    val className: String,
    val dayOfWeek: Int,        // 星期几 1-7
    val sections: List<Int>,   // 节次（解析后，如 [1,2]）
    val weeks: List<Int>,      // 周次（解析后，1-based；空表示解析失败，视为每周都有）
    val weeksText: String,     // 原始周次文本，如 "1-16周(单)"
    val credit: Double,
    val type: String,          // 课程性质，如 "必修"
    val rawTime: String,       // 原始节次文本，如 "1-2节"
) {
    /** 该课程是否在指定周次上课。weeks 为空时视为每周都有。 */
    fun inWeek(week: Int): Boolean = weeks.isEmpty() || weeks.contains(week)

    /** 是否为单双周课程（只有部分周上课）。 */
    val isAlternate: Boolean
        get() = weeksText.contains("单") || weeksText.contains("双")

    companion object {
        fun fromJson(json: JSONObject): Course {
            val rawWeeks = json.optString("zcd", "")
            val weekInfo = parseWeeks(rawWeeks)
            return Course(
                name = json.optString("kcmc", "未知课程"),
                teacher = json.optString("xm", ""),
                location = json.optString("cdmc", ""),
                campus = json.optString("xqmc", ""),
                className = json.optString("jxbmc", ""),
                dayOfWeek = json.optString("xqj", "").toIntOrNull() ?: 1,
                sections = parseSections(json.optString("jc", "")),
                weeks = weekInfo.weeks,
                weeksText = rawWeeks,
                credit = json.optString("xf", "").toDoubleOrNull() ?: 0.0,
                type = json.optString("kcxszc", ""),
                rawTime = json.optString("jc", ""),
            )
        }
    }

    /** 序列化为正方课表接口同构 JSON（供本地缓存往返使用）。 */
    fun toJson(): JSONObject = JSONObject().apply {
        put("kcmc", name)
        put("xm", teacher)
        put("cdmc", location)
        put("xqmc", campus)
        put("jxbmc", className)
        put("xqj", dayOfWeek.toString())
        put("jc", rawTime)
        put("zcd", weeksText)
        put("xf", credit.toString())
        put("kcxszc", type)
    }
}

/** 周次解析结果。 */
data class WeekInfo(val weeks: List<Int>, val isAlternate: Boolean)

/**
 * 解析节次文本："1-2节" -> [1, 2]；"5-6,9-10节" -> [5, 6, 9, 10]。
 */
fun parseSections(raw: String): List<Int> {
    val result = sortedSetOf<Int>()
    val rangeRe = Regex("""(\d+)\s*[-—~至]\s*(\d+)""")
    for (seg in raw.split(",")) {
        val m = rangeRe.find(seg)
        if (m != null) {
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            for (i in a..b) result.add(i)
        } else {
            val s = Regex("""\d+""").find(seg)
            if (s != null) result.add(s.value.toInt())
        }
    }
    return result.toList()
}

/**
 * 解析周次文本并处理单双周。
 * - "1-16周" -> 每周
 * - "1-8,10-16周(单)" -> 单周（1,3,5,7,11,13,15）
 * - "1-16周(双)" -> 双周
 * - "1-16周(单双)" -> 每周
 */
fun parseWeeks(raw: String): WeekInfo {
    val hasDan = raw.contains("单")
    val hasShuang = raw.contains("双")
    val isSingle = hasDan && !hasShuang
    val isDouble = hasShuang && !hasDan
    val alternate = isSingle || isDouble

    val weeks = sortedSetOf<Int>()
    val rangeRe = Regex("""(\d+)\s*[-—~]\s*(\d+)""")
    fun accept(v: Int) {
        if (!alternate || (if (isSingle) v % 2 == 1 else v % 2 == 0)) weeks.add(v)
    }
    for (seg in raw.split(",")) {
        val m = rangeRe.find(seg)
        if (m != null) {
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            for (i in a..b) accept(i)
        } else {
            val s = Regex("""\d+""").find(seg)
            if (s != null) accept(s.value.toInt())
        }
    }
    return WeekInfo(weeks = weeks.toList(), isAlternate = alternate)
}
