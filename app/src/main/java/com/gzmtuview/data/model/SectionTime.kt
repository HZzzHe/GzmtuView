package com.gzmtuview.data.model

/** 节次时间（上课起止时间）。 */
data class SectionTime(val section: Int, val start: String, val end: String)

/** 广州航海学院默认 11 节上课时间表（每节 45 分钟）。
 *  第 11 节有真实时间（详情/小部件可用），课表网格时间列不显示（见 ScheduleGrid）。 */
val kDefaultSectionTimes: List<SectionTime> = listOf(
    SectionTime(1, "08:10", "08:55"),
    SectionTime(2, "09:05", "09:50"),
    SectionTime(3, "10:10", "10:55"),
    SectionTime(4, "11:05", "11:50"),
    SectionTime(5, "14:00", "14:45"),
    SectionTime(6, "14:55", "15:40"),
    SectionTime(7, "16:00", "16:45"),
    SectionTime(8, "16:55", "17:40"),
    SectionTime(9, "18:40", "19:25"),
    SectionTime(10, "19:35", "20:20"),
    SectionTime(11, "20:30", "21:15"),
)
