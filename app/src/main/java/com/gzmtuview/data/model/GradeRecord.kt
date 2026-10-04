package com.gzmtuview.data.model

import org.json.JSONObject

/** 成绩记录（手机版成绩接口返回项）。 */
data class GradeRecord(
    val kch: String,     // 课程号
    val kcmc: String,    // 课程名称
    val cj: String,      // 成绩（"85"、"优秀"、"无" 等）
    val xf: Double,      // 学分
    val jd: String,      // 绩点（"3.5"、"无"）
    val ksxz: String,    // 考试性质
    val kcxzmc: String,  // 课程性质
    val cjbz: String,    // 成绩备注
    val jsxm: String,    // 教师姓名
) {
    /** 绩点数值（无法解析返回 null）。 */
    val jdValue: Double?
        get() = jd.toDoubleOrNull()

    companion object {
        fun fromJson(json: JSONObject): GradeRecord = GradeRecord(
            kch = json.optString("kch"),
            kcmc = json.optString("kcmc"),
            cj = json.optString("cj"),
            xf = json.optString("xf").toDoubleOrNull() ?: 0.0,
            jd = json.optString("jd"),
            ksxz = json.optString("ksxz"),
            kcxzmc = json.optString("kcxzmc"),
            cjbz = json.optString("cjbz"),
            jsxm = json.optString("jsxm"),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("kch", kch); put("kcmc", kcmc); put("cj", cj)
        put("xf", xf.toString()); put("jd", jd)
        put("ksxz", ksxz); put("kcxzmc", kcxzmc)
        put("cjbz", cjbz); put("jsxm", jsxm)
    }
}

/** 成绩统计。 */
data class GradeStats(val totalCredits: Double, val avgGpa: Double)

/** 计算成绩统计：总学分 + 学分加权平均绩点。 */
fun computeGradeStats(records: List<GradeRecord>): GradeStats {
    var credits = 0.0
    var weighted = 0.0
    for (r in records) {
        val jd = r.jdValue
        if (jd != null && r.xf > 0) {
            credits += r.xf
            weighted += jd * r.xf
        }
    }
    return GradeStats(
        totalCredits = credits,
        avgGpa = if (credits > 0) weighted / credits else 0.0,
    )
}
