package com.gzmtuview.data.model

/** 学年学期（对应正方课表接口的 xnm / xqm 参数）。 */
data class Semester(val xnm: String, val xqm: String) {

    /** 学期编码 → 名称：3=第1学期，12=第2学期，16=第3学期（短学期）。 */
    val xqmc: String
        get() = XQM_NAMES[xqm] ?: "学期$xqm"

    val yearRange: String
        get() {
            val start = xnm.toIntOrNull() ?: 0
            return "$xnm-${start + 1}"
        }

    val label: String
        get() = "$yearRange $xqmc"

    /** 学期时间序号（越大越新）：第 1 学期=0，第 2 学期=1，短学期=2。 */
    val rank: Int
        get() {
            val y = xnm.toIntOrNull() ?: 0
            val q = when (xqm) {
                "12" -> 1
                "16" -> 2
                else -> 0
            }
            return y * 10 + q
        }

    companion object {
        val XQM_NAMES = mapOf("3" to "第1学期", "12" to "第2学期", "16" to "第3学期")
    }
}
