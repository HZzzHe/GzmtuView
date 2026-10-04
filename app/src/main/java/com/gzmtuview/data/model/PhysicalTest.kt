package com.gzmtuview.data.model

import kotlin.math.roundToInt

/** 体测性别。 */
enum class TestGender { MALE, FEMALE }

/** 年级组。 */
enum class TestGrade { FRESHMAN_SOPHOMORE, JUNIOR_SENIOR }

/** 单项结果。 */
data class PhysicalItemResult(
    val name: String,
    val score: Int,
    val bonus: Int = 0,
    val note: String = "",
) {
    val total: Int get() = score + bonus
}

/** 体测总分结果。 */
data class PhysicalTestResult(
    val items: List<PhysicalItemResult>,
    val totalScore: Double,
    val filledCount: Int,
) {
    val failed: Boolean get() = filledCount >= 7 && totalScore < 50
}

/** 评分标准一行：score / 大一大二男 / 大三大四男 / 大一大二女 / 大三大四女。 */
private data class PRow(val score: Int, val m1: Double?, val m3: Double?, val f1: Double?, val f3: Double?) {
    fun value(g: TestGender, grade: TestGrade): Double? = when {
        g == TestGender.MALE -> if (grade == TestGrade.FRESHMAN_SOPHOMORE) m1 else m3
        else -> if (grade == TestGrade.FRESHMAN_SOPHOMORE) f1 else f3
    }
}

/**
 * 体测评分器（纯函数，对齐 Flutter PhysicalTestScorer）。
 * 权重：BMI 15%、肺活量 15%、50米 20%、坐位体前屈 10%、立定跳远 10%、
 * 引体向上(男)/仰卧起坐(女) 10%、耐力跑 20%。总分 <50 不及格。
 */
object PhysicalTestScorer {

    // ---------- BMI（不分年级；超重/低体重 80 分） ----------
    fun scoreBmi(g: TestGender, bmi: Double): Int {
        val normalMax = 23.9
        val normalMin = if (g == TestGender.MALE) 17.9 else 17.2
        if (bmi >= normalMin && bmi <= normalMax) return 100
        if (if (g == TestGender.MALE) bmi <= 17.8 else bmi <= 17.1) return 80 // 低体重
        if (bmi >= 24.0 && bmi <= 27.9) return 80 // 超重
        return 60 // 肥胖 >=28.0
    }

    // ---------- 肺活量 ----------
    private val _vitalCapacity: List<PRow> = listOf(
            PRow(100, 5040.0, 5140.0, 3400.0, 3450.0),
            PRow(95, 4920.0, 5020.0, 3350.0, 3400.0),
            PRow(90, 4800.0, 4900.0, 3300.0, 3350.0),
            PRow(85, 4550.0, 4650.0, 3150.0, 3200.0),
            PRow(80, 4300.0, 4400.0, 3000.0, 3050.0),
            PRow(78, 4180.0, 4280.0, 2900.0, 2950.0),
            PRow(76, 4060.0, 4160.0, 2800.0, 2850.0),
            PRow(74, 3940.0, 4040.0, 2700.0, 2750.0),
            PRow(72, 3820.0, 3920.0, 2600.0, 2650.0),
            PRow(70, 3700.0, 3800.0, 2500.0, 2550.0),
            PRow(68, 3580.0, 3680.0, 2400.0, 2450.0),
            PRow(66, 3460.0, 3560.0, 2300.0, 2350.0),
            PRow(64, 3340.0, 3440.0, 2200.0, 2250.0),
            PRow(62, 3220.0, 3320.0, 2100.0, 2150.0),
            PRow(60, 3100.0, 3200.0, 2000.0, 2050.0),
            PRow(50, 2940.0, 3030.0, 1960.0, 2010.0),
            PRow(40, 2780.0, 2860.0, 1920.0, 1970.0),
            PRow(30, 2620.0, 2690.0, 1880.0, 1930.0),
            PRow(20, 2460.0, 2520.0, 1840.0, 1890.0),
            PRow(10, 2300.0, 2350.0, 1800.0, 1850.0),
    )

    // ---------- 坐位体前屈 ----------
    private val _sitAndReach = listOf(
            PRow(100, 24.9, 25.1, 25.8, 26.3),
            PRow(95, 23.1, 23.3, 24.0, 24.4),
            PRow(90, 21.3, 21.5, 22.2, 22.4),
            PRow(85, 19.5, 19.9, 20.6, 21.0),
            PRow(80, 17.7, 18.2, 19.0, 19.5),
            PRow(78, 16.3, 16.8, 17.7, 18.2),
            PRow(76, 14.9, 15.4, 16.4, 16.9),
            PRow(74, 13.5, 14.0, 15.1, 15.6),
            PRow(72, 12.1, 12.6, 13.8, 14.3),
            PRow(70, 10.7, 11.2, 12.5, 13.0),
            PRow(68, 9.3, 9.8, 11.2, 11.7),
            PRow(66, 7.9, 8.4, 9.9, 10.4),
            PRow(64, 6.5, 7.0, 8.6, 9.1),
            PRow(62, 5.1, 5.6, 7.3, 7.8),
            PRow(60, 3.7, 4.2, 6.0, 6.5),
            PRow(50, 2.7, 3.2, 5.2, 5.7),
            PRow(40, 1.7, 2.2, 4.4, 4.9),
            PRow(30, 0.7, 1.2, 3.6, 4.1),
            PRow(20, -0.3, 0.2, 2.8, 3.3),
            PRow(10, -1.3, -0.8, 2.0, 2.5),
    )

    // ---------- 立定跳远 ----------
    private val _longJump = listOf(
            PRow(100, 273.0, 275.0, 207.0, 208.0),
            PRow(95, 268.0, 270.0, 201.0, 202.0),
            PRow(90, 263.0, 265.0, 195.0, 196.0),
            PRow(85, 256.0, 258.0, 188.0, 189.0),
            PRow(80, 248.0, 250.0, 181.0, 182.0),
            PRow(78, 244.0, 246.0, 178.0, 179.0),
            PRow(76, 240.0, 242.0, 175.0, 176.0),
            PRow(74, 236.0, 238.0, 172.0, 173.0),
            PRow(72, 232.0, 234.0, 169.0, 170.0),
            PRow(70, 228.0, 230.0, 166.0, 167.0),
            PRow(68, 224.0, 226.0, 163.0, 164.0),
            PRow(66, 220.0, 222.0, 160.0, 161.0),
            PRow(64, 216.0, 218.0, 157.0, 158.0),
            PRow(62, 212.0, 214.0, 154.0, 155.0),
            PRow(60, 208.0, 210.0, 151.0, 152.0),
            PRow(50, 203.0, 205.0, 146.0, 147.0),
            PRow(40, 198.0, 200.0, 141.0, 142.0),
            PRow(30, 193.0, 195.0, 136.0, 137.0),
            PRow(20, 188.0, 190.0, 131.0, 132.0),
            PRow(10, 183.0, 185.0, 126.0, 127.0),
    )

    // ---------- 引体向上/仰卧起坐 ----------
    private val _pullUpSitUp = listOf(
            PRow(100, 19.0, 20.0, 56.0, 57.0),
            PRow(95, 18.0, 19.0, 54.0, 55.0),
            PRow(90, 17.0, 18.0, 52.0, 53.0),
            PRow(85, 16.0, 17.0, 49.0, 50.0),
            PRow(80, 15.0, 16.0, 46.0, 47.0),
            PRow(78, null, null, 44.0, 45.0),
            PRow(76, 14.0, 15.0, 42.0, 43.0),
            PRow(74, null, null, 40.0, 41.0),
            PRow(72, 13.0, 14.0, 38.0, 39.0),
            PRow(70, null, null, 36.0, 37.0),
            PRow(68, 12.0, 13.0, 34.0, 35.0),
            PRow(66, null, null, 32.0, 33.0),
            PRow(64, 11.0, 12.0, 30.0, 31.0),
            PRow(62, null, null, 28.0, 29.0),
            PRow(60, 10.0, 11.0, 26.0, 27.0),
            PRow(50, 9.0, 10.0, 24.0, 25.0),
            PRow(40, 8.0, 9.0, 22.0, 23.0),
            PRow(30, 7.0, 8.0, 20.0, 21.0),
            PRow(20, 6.0, 7.0, 18.0, 19.0),
            PRow(10, 5.0, 6.0, 16.0, 17.0),
    )

    // ---------- 50米 ----------
    private val _fiftyMeter = listOf(
            PRow(100, 6.7, 6.6, 7.5, 7.4),
            PRow(95, 6.8, 6.7, 7.6, 7.5),
            PRow(90, 6.9, 6.8, 7.7, 7.6),
            PRow(85, 7.0, 6.9, 8.0, 7.9),
            PRow(80, 7.1, 7.0, 8.3, 8.2),
            PRow(78, 7.3, 7.2, 8.5, 8.4),
            PRow(76, 7.5, 7.4, 8.7, 8.6),
            PRow(74, 7.7, 7.6, 8.9, 8.8),
            PRow(72, 7.9, 7.8, 9.1, 9.0),
            PRow(70, 8.1, 8.0, 9.3, 9.2),
            PRow(68, 8.3, 8.2, 9.5, 9.4),
            PRow(66, 8.5, 8.4, 9.7, 9.6),
            PRow(64, 8.7, 8.6, 9.9, 9.8),
            PRow(62, 8.9, 8.8, 10.1, 10.0),
            PRow(60, 9.1, 9.0, 10.3, 10.2),
            PRow(50, 9.3, 9.2, 10.5, 10.4),
            PRow(40, 9.5, 9.4, 10.7, 10.6),
            PRow(30, 9.7, 9.6, 10.9, 10.8),
            PRow(20, 9.9, 9.8, 11.1, 11.0),
            PRow(10, 10.1, 10.0, 11.3, 11.2),
    )

    // ---------- 耐力跑 ----------
    private val _endurance = listOf(
            PRow(100, 197.0, 195.0, 198.0, 196.0),
            PRow(95, 202.0, 200.0, 204.0, 202.0),
            PRow(90, 207.0, 205.0, 210.0, 208.0),
            PRow(85, 214.0, 212.0, 217.0, 215.0),
            PRow(80, 222.0, 220.0, 224.0, 222.0),
            PRow(78, 227.0, 225.0, 229.0, 227.0),
            PRow(76, 232.0, 230.0, 234.0, 232.0),
            PRow(74, 237.0, 235.0, 239.0, 237.0),
            PRow(72, 242.0, 240.0, 244.0, 242.0),
            PRow(70, 247.0, 245.0, 249.0, 247.0),
            PRow(68, 252.0, 250.0, 254.0, 252.0),
            PRow(66, 257.0, 255.0, 259.0, 257.0),
            PRow(64, 262.0, 260.0, 264.0, 262.0),
            PRow(62, 267.0, 265.0, 269.0, 267.0),
            PRow(60, 272.0, 270.0, 274.0, 272.0),
            PRow(50, 292.0, 290.0, 284.0, 282.0),
            PRow(40, 312.0, 310.0, 294.0, 292.0),
            PRow(30, 332.0, 330.0, 304.0, 302.0),
            PRow(20, 352.0, 350.0, 314.0, 312.0),
            PRow(10, 372.0, 370.0, 324.0, 322.0),
    )

    /// 高优指标查表：成绩 >= 档位值 → 最高档得分；低于最低档 → 0。
    private fun scoreHigh(table: List<PRow>, g: TestGender, grade: TestGrade, value: Double): Int {
        for (r in table) {
            val v = r.value(g, grade) ?: continue
            if (value >= v) return r.score
        }
        return 0
    }

    /// 低优指标查表：成绩 <= 档位值 → 最高档得分；超过最低档 → 0。
    private fun scoreLow(table: List<PRow>, g: TestGender, grade: TestGrade, value: Double): Int {
        for (r in table) {
            val v = r.value(g, grade) ?: continue
            if (value <= v) return r.score
        }
        return 0
    }

    /// 满分标准（用于加分计算）。
    private fun fullMark(table: List<PRow>, g: TestGender, grade: TestGrade): Double {
        for (r in table) {
            if (r.score == 100) {
                val v = r.value(g, grade)
                if (v != null) return v
            }
        }
        return 0.0
    }

    /** 综合评分。未填项按 0 分计入。 */
    fun score(
        gender: TestGender,
        grade: TestGrade,
        bmi: Double? = null,
        vitalCapacity: Double? = null,
        fiftyMeter: Double? = null,
        sitAndReach: Double? = null,
        longJump: Double? = null,
        pullUpSitUp: Double? = null,
        enduranceSeconds: Double? = null,
    ): PhysicalTestResult {
        val weights = intArrayOf(15, 15, 20, 10, 10, 10, 20)
        val names = listOf(
            "体重指数（BMI）",
            "肺活量",
            "50米跑",
            "坐位体前屈",
            "立定跳远",
            if (gender == TestGender.MALE) "引体向上" else "一分钟仰卧起坐",
            if (gender == TestGender.MALE) "1000米跑" else "800米跑",
        )
        val values = listOf(bmi, vitalCapacity, fiftyMeter, sitAndReach, longJump, pullUpSitUp, enduranceSeconds)

        val items = mutableListOf<PhysicalItemResult>()
        var total = 0.0
        for (i in 0 until 7) {
            val v = values[i] ?: continue
            val item: PhysicalItemResult = when (i) {
                0 -> PhysicalItemResult(names[i], scoreBmi(gender, v), note = "BMI = $v")
                1 -> PhysicalItemResult(names[i], scoreHigh(_vitalCapacity, gender, grade, v))
                2 -> PhysicalItemResult(names[i], scoreLow(_fiftyMeter, gender, grade, v))
                3 -> PhysicalItemResult(names[i], scoreHigh(_sitAndReach, gender, grade, v))
                4 -> PhysicalItemResult(names[i], scoreHigh(_longJump, gender, grade, v))
                5 -> {
                    var s = scoreHigh(_pullUpSitUp, gender, grade, v)
                    var bonus = 0
                    val full = fullMark(_pullUpSitUp, gender, grade)
                    if (s == 100 && v > full) {
                        bonus = ((v - full).roundToInt()).coerceIn(0, 10)
                    }
                    PhysicalItemResult(
                        names[i], s, bonus,
                        if (bonus > 0) "超出满分 $bonus 次，+$bonus 分" else "",
                    )
                }
                else -> {
                    var s = scoreLow(_endurance, gender, grade, v)
                    var bonus = 0
                    val full = fullMark(_endurance, gender, grade)
                    if (s == 100 && v < full) {
                        val saved = (full - v).roundToInt()
                        val perSecond = if (gender == TestGender.MALE) 4 else 5
                        bonus = (saved / perSecond).coerceIn(0, 10)
                    }
                    PhysicalItemResult(
                        names[i], s, bonus,
                        if (bonus > 0) "快 ${bonus * (if (gender == TestGender.MALE) 4 else 5)} 秒，+$bonus 分" else "",
                    )
                }
            }
            items.add(item)
            total += item.total * weights[i] / 100.0
        }
        return PhysicalTestResult(items, total, items.size)
    }
}
