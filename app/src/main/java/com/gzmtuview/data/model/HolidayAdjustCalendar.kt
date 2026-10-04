package com.gzmtuview.data.model

import java.time.LocalDate

/** 调休补班日：该周末照常上课，补的是 [replaysDate] 那一天的课表。 */
data class MakeupDay(
    val holidayName: String,
    val replaysDate: LocalDate,
)

/**
 * 法定节假日 / 调休离线表（国务院放假安排公告），供课表「节假日调休」功能使用，全程不联网。
 *
 * 表里只存公告明文：放假区间 + 哪几个周六/周日补班。补班日补哪天，由 [MakeupDay] 在初始化时
 * 按公告结构推算，规则（与高校实际通知一致，如 2026 国庆 9/20→10/6、10/10→10/7）：
 * 法定假日 13 天（2025-01-01 起新规：元旦1、春节4〔除夕~初三〕、清明1、劳动2、端午1、中秋1、国庆3），
 * 公告把它与相邻双休拼成连续假期，借走的工作日用紧邻的双休偿还 —— 因此
 * **把该段假期里被占用的工作日按时间排序，取末尾「补班日个数」个，与补班日按时间排序逐一配对**。
 * 被占工作日多于补班日时，差额就是净放假（那几天的课本就不用补）。
 *
 * 扩表方式：每年 11~12 月国务院公布次年后，在 init 里补 `putOff` 区间 + 往 `rawMakeups`
 * 加「补班日 → 假日名」即可；学校自己临时停课（公告里没有对应补班日）用 `putSchoolOff`，
 * 只标停课、不参与配对。若日后补班安排公布且要偿还这几天，得把 `putSchoolOff` 换成 `putOff`
 * 合并进国庆 Block —— 那会重排该假已有的全部配对，改完必须复核 9/20、10/10 两天。
 * 未收录的日期一律回退普通周一至周五逻辑，不会误改课表。
 */
object HolidayAdjustCalendar {

    /** 放假日 → 假日名 */
    private val offDays: Map<LocalDate, String>

    /** 补班日 → 补哪天（由 [pair] 推算） */
    private val makeupDays: Map<LocalDate, MakeupDay>

    /** 公告原文：补班日 → 它偿还的是哪个假 */
    private val rawMakeups: List<Pair<LocalDate, String>>

    /** 一段连续假期 */
    private data class Block(val name: String, val start: LocalDate, val end: LocalDate)

    init {
        val off = mutableMapOf<LocalDate, String>()
        val blocks = mutableListOf<Block>()
        fun putOff(y1: Int, m1: Int, d1: Int, y2: Int, m2: Int, d2: Int, name: String) {
            val start = LocalDate.of(y1, m1, d1)
            val end = LocalDate.of(y2, m2, d2)
            blocks.add(Block(name, start, end))
            var day = start
            while (!day.isAfter(end)) {
                off[day] = name
                day = day.plusDays(1)
            }
        }
        // 只入放假日表、不建 Block：pair() 按 Block 区间反推「被占工作日 ↔ 补班日」，
        // 为学校临时停课新建 Block 会把已核对的 2026 国庆 9/20→10/6、10/10→10/7 重新配对。
        fun putSchoolOff(y1: Int, m1: Int, d1: Int, y2: Int, m2: Int, d2: Int, name: String) {
            var day = LocalDate.of(y1, m1, d1)
            val end = LocalDate.of(y2, m2, d2)
            while (!day.isAfter(end)) {
                off[day] = name
                day = day.plusDays(1)
            }
        }
        // ---- 2025 ----
        putOff(2025, 1, 1, 2025, 1, 1, "元旦")
        putOff(2025, 1, 28, 2025, 2, 4, "春节")
        putOff(2025, 4, 4, 2025, 4, 6, "清明节")
        putOff(2025, 5, 1, 2025, 5, 5, "劳动节")
        putOff(2025, 5, 31, 2025, 6, 2, "端午节")
        putOff(2025, 10, 1, 2025, 10, 8, "国庆节")
        // ---- 2026 ----
        putOff(2026, 1, 1, 2026, 1, 3, "元旦")
        putOff(2026, 2, 15, 2026, 2, 23, "春节")
        putOff(2026, 4, 4, 2026, 4, 6, "清明节")
        putOff(2026, 5, 1, 2026, 5, 5, "劳动节")
        putOff(2026, 6, 19, 2026, 6, 21, "端午节")
        putOff(2026, 9, 25, 2026, 9, 27, "中秋节")
        putOff(2026, 10, 1, 2026, 10, 7, "国庆节")
        // 学校通知：国庆前 9/28（一）~9/30（三）停课，补课安排另行公布（暂不配平班日）
        putSchoolOff(2026, 9, 28, 2026, 9, 30, "国庆节")
        // ---- 2027：次年公告尚未发布，先只记法定日本身（无补班） ----
        putOff(2027, 1, 1, 2027, 1, 1, "元旦")
        offDays = off

        rawMakeups = listOf(
            LocalDate.of(2025, 1, 26) to "春节",
            LocalDate.of(2025, 2, 8) to "春节",
            LocalDate.of(2025, 4, 27) to "劳动节",
            LocalDate.of(2025, 9, 28) to "国庆节",
            LocalDate.of(2025, 10, 11) to "国庆节",
            LocalDate.of(2026, 1, 4) to "元旦",
            LocalDate.of(2026, 2, 14) to "春节",
            LocalDate.of(2026, 2, 28) to "春节",
            LocalDate.of(2026, 5, 9) to "劳动节",
            LocalDate.of(2026, 9, 20) to "国庆节",
            LocalDate.of(2026, 10, 10) to "国庆节",
        )
        makeupDays = pair(off, blocks, rawMakeups)
    }

    /** 该日是放假日则返回假日名，否则 null。 */
    fun holidayName(date: LocalDate): String? = offDays[date]

    /** 该日是调休补班日则返回其安排（补哪天），否则 null。 */
    fun makeupOf(date: LocalDate): MakeupDay? = makeupDays[date]

    /**
     * 假期块末尾被占工作日 ↔ 补班日，逐一配对。
     * 补班日先归属到同名假期中离它最近的那一段，避免跨年份串档。
     */
    private fun pair(
        off: Map<LocalDate, String>,
        blocks: List<Block>,
        raw: List<Pair<LocalDate, String>>,
    ): Map<LocalDate, MakeupDay> {
        val owner = raw.associate { (date, name) ->
            date to blocks.filter { it.name == name }
                .minByOrNull { kotlin.math.abs(it.start.toEpochDay() - date.toEpochDay()) }
        }
        val result = mutableMapOf<LocalDate, MakeupDay>()
        for (block in blocks) {
            val days = raw.filter { owner[it.first] == block }.map { it.first }.sorted()
            if (days.isEmpty()) continue
            // 周六日本就休息，只有工作日被占才算「被调走的课」
            val eaten = ArrayList<LocalDate>(days.size)
            var d = block.start
            while (!d.isAfter(block.end)) {
                if (d.dayOfWeek.value in 1..5 && off.containsKey(d)) eaten.add(d)
                d = d.plusDays(1)
            }
            val from = (eaten.size - days.size).coerceAtLeast(0)
            days.forEachIndexed { k, makeup ->
                val src = eaten.getOrNull(from + k) ?: return@forEachIndexed
                result[makeup] = MakeupDay(block.name, src)
            }
        }
        return result
    }
}
