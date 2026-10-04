package com.gzmtuview.data.model

import java.time.LocalDate

/**
 * 电费估算（移植自《电费估算工具》，模型依据 2026 年 1-7 月实测回归）。
 * - 4 个宿舍模型（性别 × 是否顶楼），M_NTOP 为外推（精度较低，UI 需标注）
 * - 日用电 = max(base, slope×均温 + intercept) × 生活习惯系数
 * - 电费按超额计费：max(总用电 − 定额折算, 0) × 0.6258 元/度
 * - 日期范围 ≤ 90 天；未来 ≤ 16 天（Open-Meteo 预报上限）
 */

/** 宿舍用电模型（回归参数）。 */
data class AcModel(
    val name: String,
    val base: Double,
    val slope: Double,
    val intercept: Double,
    val quota: Double,
    val estimated: Boolean,
)

/** 4 个模型参数。 */
val kModels: Map<String, AcModel> = linkedMapOf(
    "E1" to AcModel("男生 · 顶楼 · 4人间", 2.98, 2.078, -38.64, 32.0, false),
    "E4_7F" to AcModel("女生 · 顶楼 · 5-6人间", 1.91, 1.413, -26.30, 40.0, false),
    "E4_16F" to AcModel("女生 · 非顶楼 · 5-6人间", 1.46, 1.002, -18.28, 40.0, false),
    "M_NTOP" to AcModel("男生 · 非顶楼（外推）", 2.28, 1.474, -26.75, 32.0, true),
)

/** 性别 × 是否顶楼 → 模型 key。 */
fun pickModelKey(isMale: Boolean, isTopFloor: Boolean): String = when {
    isMale && isTopFloor -> "E1"
    isMale -> "M_NTOP"
    isTopFloor -> "E4_7F"
    else -> "E4_16F"
}

/** 电价（元/度）。 */
const val kPrice = 0.6258

/** 空调额定制冷功率（kW）。 */
const val kRatedPower = 0.810

/** 平均每月天数。 */
const val kAvgMonthDays = 30.44

/** 最大估算跨度（天）。 */
const val kMaxDays = 90

/** 未来最大天数（Open-Meteo 预报上限）。 */
const val kMaxFutureDays = 16

/** 黄埔区坐标。 */
val kLat = 23.163
val kLon = 113.466

/** 生活习惯系数。 */
val kStyleFactor: Map<String, Double> = linkedMapOf(
    "省电型" to 0.65,
    "标准型" to 1.0,
    "高耗型" to 1.7,
)

/** 日期格式化为 YYYY-MM-DD（LocalDate.toString 即 ISO）。 */
fun fmtDate(d: LocalDate): String = d.toString()

/** 每日明细点（图表用）。 */
data class DailyPoint(
    val date: String,
    val temp: Double,
    val kwh: Double,
)

/** 估算结果。 */
data class EstimateResult(
    val avgTemp: Double,
    val totalKwh: Double,
    val acKwh: Double,
    val fee: Double,
    val dailyAvg: Double,
    val hoursPerDay: Double,
    val days: Int,
    val dailyPoints: List<DailyPoint>,
)

/**
 * 按回归模型估算 [start, end] 区间用电。
 * [temps]：日期(YYYY-MM-DD) → 均温；缺失日期的天跳过。
 */
fun estimate(
    temps: Map<String, Double>,
    start: LocalDate,
    end: LocalDate,
    model: AcModel,
    styleFactor: Double,
): EstimateResult {
    var total = 0.0
    var baseSum = 0.0
    var tSum = 0.0
    var n = 0
    val points = mutableListOf<DailyPoint>()
    var d = start
    while (!d.isAfter(end)) {
        val t = temps[fmtDate(d)]
        if (t != null) {
            val daily = maxOf(model.base, model.slope * t + model.intercept) * styleFactor
            total += daily
            baseSum += model.base * styleFactor
            tSum += t
            points.add(DailyPoint(fmtDate(d), t, daily))
            n++
        }
        d = d.plusDays(1)
    }
    if (n == 0) throw IllegalStateException("所选范围内无温度数据")

    val avgT = tSum / n
    val acKwh = if (total - baseSum < 0) 0.0 else total - baseSum
    val quota = model.quota / kAvgMonthDays * n // 定额按天折算
    val fee = if (total - quota < 0) 0.0 else (total - quota) * kPrice

    return EstimateResult(
        avgTemp = avgT,
        totalKwh = total,
        acKwh = acKwh,
        fee = fee,
        dailyAvg = total / n,
        hoursPerDay = acKwh / kRatedPower / n,
        days = n,
        dailyPoints = points,
    )
}

/** 校验日期范围；返回错误信息（null = 通过）。 */
fun validateRange(start: LocalDate, end: LocalDate, now: LocalDate = LocalDate.now()): String? {
    if (end.isBefore(start)) return "结束日期不能早于开始日期"
    val days = end.toEpochDay() - start.toEpochDay() + 1
    if (days > kMaxDays) return "日期跨度不能超过 $kMaxDays 天"
    if (end.isAfter(now.plusDays(kMaxFutureDays.toLong()))) {
        return "结束日期不能超过今天 + $kMaxFutureDays 天"
    }
    return null
}

/** 把 [min, max] 切成约 [target] 段的整数步长。 */
fun niceInterval(min: Double, max: Double, target: Int): Double {
    val raw = (max - min) / target
    return when {
        raw <= 1 -> 1.0
        raw <= 2 -> 2.0
        raw <= 5 -> 5.0
        raw <= 10 -> 10.0
        else -> kotlin.math.ceil(raw / 10) * 10
    }
}

/** '2026-08-14' → '8/14'（跨年时保留年份）。 */
fun shortDate(date: String, now: LocalDate = LocalDate.now()): String {
    val parts = date.split("-")
    if (parts.size != 3) return date
    val y = parts[0].toIntOrNull() ?: return date
    val m = parts[1].toIntOrNull() ?: return date
    val dd = parts[2].toIntOrNull() ?: return date
    return if (y == now.year) "$m/$dd" else "$y/$m/$dd"
}
