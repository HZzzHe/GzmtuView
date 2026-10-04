package com.gzmtuview.data.model

import java.time.LocalDate

/**
 * 「不上了」标记 key：课程名|星期|起始节|日期(yyyy-MM-dd)。
 * 仅作用于该日期当天那节课：课表网格该卡半透明、桌面小部件不显示该课当天安排，
 * 同课程其他周次不受影响。日期为 null（如"全部课程"页无法确定哪次课）时不生成标记。
 */
fun Course.skipKey(date: LocalDate?): String =
    "$name|$dayOfWeek|${sections.firstOrNull() ?: 0}|${date?.toString().orEmpty()}"
