package com.gzmtuview.ui.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gzmtuview.data.color.courseColorsForIndex
import com.gzmtuview.data.color.kCoursePalette
import com.gzmtuview.data.model.Course
import com.gzmtuview.data.model.HolidayAdjustCalendar
import com.gzmtuview.data.model.MakeupDay
import com.gzmtuview.data.model.SectionTime
import com.gzmtuview.data.model.kDefaultSectionTimes
import com.gzmtuview.data.model.skipKey
import com.gzmtuview.data.model.WeekDates
import com.gzmtuview.ui.wallpaper.Wallpaper
import java.time.LocalDate
import kotlin.math.abs

/** 表头星期名，下标 0 = 周一，与 Course.dayOfWeek - 1 对齐 */
private val kWeekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 放假「休」绿 / 补班「班」红，与「不上了」「要上课」按钮同色系 */
private val holidayOffColor = Color(0xFF34B87A)
private val holidayOnColor = Color(0xFFFC3C57)

/** 调休启用后，本周某列的日历身份 */
private data class DayPlan(
    val date: LocalDate,
    val holidayName: String?,
    val makeup: MakeupDay?,
)

/** 网格中的一张课程卡：显示在 [column]（1..7）列；[moved] = 这节课是调休补班借来的 */
private data class PlacedCourse(
    val course: Course,
    val column: Int,
    val moved: Boolean,
)

/** 课程表周视图网格（Material 3，自适应铺满布局）。 */
@Composable
fun ScheduleGrid(
    courses: List<Course>,
    currentWeek: Int? = null,
    currentWeekDates: List<LocalDate>? = null,
    showTeacherName: Boolean = true,
    showGridLines: Boolean = false,
    courseColorIndex: Map<String, Int>? = null,
    sectionCount: Int = 11,
    allowHorizontalScroll: Boolean = false,
    sectionTimes: List<SectionTime> = kDefaultSectionTimes,
    /** 底部预留高度（玻璃底栏浮层高度）：行高计算时扣除，让网格底边落在底栏上方 */
    bottomPadding: Dp = 0.dp,
    /** 「不上了」标记 key 集（Course.skipKey(date)）：命中当天那节课的卡半透明显示 */
    skippedKeys: Set<String> = emptySet(),
    /** 节假日调休（beta）：叠加官方放假日置灰与补班日借用课表，需 [currentWeekDates] 齐全才生效 */
    holidayAdjust: Boolean = false,
    /** 学期周次锚点：补班日借的课要按「被调走那天」所在周次筛选，可能不是当前页的周次 */
    weekDates: WeekDates? = null,
    /** 课程卡片点击：回传课程与其所在列的日期（"全部"页无日期为 null）；详情对话框由调用方在主窗口 overlay 层渲染 */
    onCourseDetail: ((Course, java.time.LocalDate?) -> Unit)? = null,
) {
    val headerHeightDp = 40.dp
    val timeColumnWidth = 48.dp
    val minDayColumnWidth = 44.dp
    val minRowHeight = 44.dp
    val maxDayColumnWidth = 1000.dp
    val maxRowHeight = 1000.dp
    val compactThreshold = 72.dp
    val scheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val todayColor = Color(0xFFFF375F)
    // 网格的星期名/节次号/日期/时间直接落在让位给壁纸的页面底上：按背板亮度自适应墨色
    // （无壁纸时 adaptiveInk 原样返回主题色，行为不变）。课程卡有自己的不透明底，不参与。
    val labelInk = Wallpaper.adaptiveInk(scheme.onSurfaceVariant)
    val mutedInk = Wallpaper.adaptiveInk(scheme.outline, variant = true)
    val visible = if (currentWeek == null) courses
    else courses.filter { it.inWeek(currentWeek) }
    val now = LocalDate.now()
    val todayIndex = currentWeekDates?.indexOfFirst { it == now } ?: -1

    // 调休：空表 = 未启用（「全部」页没有日期，自然走原始周一至周五逻辑）
    val dayPlans: List<DayPlan> =
        if (holidayAdjust && currentWeekDates != null && currentWeekDates.size == 7) {
            currentWeekDates.map { d ->
                DayPlan(
                    date = d,
                    holidayName = HolidayAdjustCalendar.holidayName(d),
                    makeup = HolidayAdjustCalendar.makeupOf(d),
                )
            }
        } else emptyList()

    // 补班日列下标（0..6）：该列原本是休息日，整列换成被调走那天的课表
    val makeupColumns: Set<Int> = dayPlans.indices.filter { dayPlans[it].makeup != null }.toSet()

    val placedCourses = buildList<PlacedCourse> {
        for (c in visible) {
            if (c.dayOfWeek in 1..7 && c.sections.isNotEmpty() && (c.dayOfWeek - 1) !in makeupColumns) {
                add(PlacedCourse(c, c.dayOfWeek, false))
            }
        }
        dayPlans.forEachIndexed { i, plan ->
            val src = plan.makeup?.replaysDate ?: return@forEachIndexed
            val w = src.dayOfWeek.value
            // 补的是「那一天」的课表：那一天可能在别的周次（如 9/20 补 10/6 = 第 6 周），
            // 必须按源日期的周次筛选，否则会把本周的同星期课搬过来
            val srcWeek = weekDates?.weekOf(src)
            val source =
                if (srcWeek == null || srcWeek == currentWeek) visible
                else courses.filter { it.inWeek(srcWeek) }
            for (c in source) {
                if (c.dayOfWeek == w && c.sections.isNotEmpty()) add(PlacedCourse(c, i + 1, true))
            }
        }
    }

    BoxWithConstraints {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        // 自适应列宽：铺满可用宽度，限制 [min, max]；allowHorizontalScroll=false 时下限 0
        val minDayW = if (allowHorizontalScroll) minDayColumnWidth else 0.dp
        val dayW: Float = ((availableWidth.value - timeColumnWidth.value) / 7f)
            .coerceIn(minDayW.value, maxDayColumnWidth.value)
        // 自适应行高
        val rowH: Float = ((availableHeight.value - headerHeightDp.value - bottomPadding.value) / sectionCount)
            .coerceIn(minRowHeight.value, maxRowHeight.value)
        val totalWidth = timeColumnWidth.value + dayW * 7
        val totalHeight = rowH * sectionCount
        val needsHorizontal = (totalWidth - availableWidth.value) > 0.5f &&
            allowHorizontalScroll

        val vScroll = rememberScrollState()
        val hScroll = rememberScrollState()
        var content = Modifier
            .width(totalWidth.dp)
            .height(totalHeight.dp)

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(vScroll)
                .then(if (needsHorizontal) Modifier.horizontalScroll(hScroll) else Modifier)
        ) {
            // 星期表头
            Row(Modifier.height(headerHeightDp)) {
                Spacer(Modifier.width(timeColumnWidth))
                for (d in 0 until 7) {
                    Box(Modifier.width(dayW.dp).height(headerHeightDp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                kWeekdayLabels[d],
                                fontSize = 13.sp,
                                // 锁定紧凑行高：MiSans 默认行高×fontScale 会超出 40dp 表头致日期侵线
                                lineHeight = 16.sp,
                                // 当天加粗到 ExtraBold 增强辨识（其余天保持 Bold）
                                fontWeight = if (d == todayIndex) FontWeight.ExtraBold else FontWeight.Bold,
                                color = if (d == todayIndex) todayColor else labelInk,
                            )
                            if (currentWeekDates != null && currentWeekDates.size == 7) {
                                val dt = currentWeekDates[d]
                                val plan = dayPlans.getOrNull(d)
                                val marker = when {
                                    plan?.holidayName != null -> "休"
                                    plan?.makeup != null -> "班"
                                    else -> null
                                }
                                Text(
                                    buildAnnotatedString {
                                        append("${dt.monthValue}/${dt.dayOfMonth}")
                                        if (marker != null) {
                                            append(" ")
                                            withStyle(
                                                SpanStyle(
                                                    color = if (marker == "休") holidayOffColor else holidayOnColor,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            ) { append(marker) }
                                        }
                                    },
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (d == todayIndex) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (d == todayIndex) todayColor else mutedInk,
                                )
                            }
                        }
                    }
                }
            }
            // 网格与课程
            Box(Modifier.width(totalWidth.dp).height(totalHeight.dp)) {
                // 网格线（注意：DrawScope 坐标单位是物理像素，
                // dp 值必须乘 density，否则网格被压缩到左上角与卡片错位）
                Canvas(Modifier.size(totalWidth.dp, totalHeight.dp)) {
                    val lineColor = when {
                        !showGridLines -> Color.Transparent
                        // 壁纸透到整屏时，实色分界线会变成压在壁纸上的一层网格脏点：降到 40%
                        Wallpaper.shown -> scheme.outlineVariant.copy(alpha = 0.4f)
                        else -> scheme.outlineVariant
                    }
                    val d = density
                    val stroke = 1.dp.toPx()
                    for (i in 0..7) {
                        val x = (timeColumnWidth.value + i * dayW) * d
                        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
                    }
                    for (i in 0..sectionCount) {
                        val y = i * rowH * d
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
                    }
                }
                // 节次号与时间（跨行三行显示）
                for (i in 0 until sectionCount) {
                    Box(
                        Modifier.offset(0.dp, (i * rowH).dp)
                            .width(timeColumnWidth)
                            .height(rowH.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // 第 11 节：隐藏节号但保留占位（行高由外层 Box 的 offset+height 固定）
                            if (i + 1 != 11) {
                                Text(
                                    "${i + 1}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = labelInk,
                                )
                            }
                            if (i < sectionTimes.size && sectionTimes[i].section <= 10) {
                                // 仅前 10 节显示起止时间；第 11 节节号与时间都不显示
                                Text(sectionTimes[i].start, fontSize = 8.5.sp, color = mutedInk, lineHeight = 10.sp)
                                Text(sectionTimes[i].end, fontSize = 8.5.sp, color = mutedInk, lineHeight = 10.sp)
                            }
                        }
                    }
                }
                // 课程卡片
                for (p in placedCourses) {
                    val c = p.course
                    val start = c.sections.first().coerceIn(1, sectionCount)
                    val span = c.sections.size.coerceIn(1, sectionCount - start + 1)
                    val index = courseColorIndex?.get(c.name)
                        ?: (abs(c.name.hashCode()) % kCoursePalette.size)
                    val colors = courseColorsForIndex(index, isDark)
                    val compact = dayW < compactThreshold.value
                    // 该卡对应的那一天的日期（"全部"页 currentWeekDates 为 null → 无日期）
                    val cardDate = currentWeekDates?.getOrNull(p.column - 1)
                    // 放假日的自然课要置灰；借到补班列的那份照常显示
                    val onHoliday = !p.moved &&
                        dayPlans.getOrNull(p.column - 1)?.holidayName != null
                    val dimmed = cardDate != null &&
                        (onHoliday || c.skipKey(cardDate) in skippedKeys)
                    CourseCard(
                        c = c,
                        dayIndex = p.column,
                        dayWidth = dayW,
                        rowHeight = rowH,
                        compact = compact,
                        span = span,
                        background = colors.first,
                        barColor = colors.second,
                        textColor = colors.third,
                        showTeacherName = showTeacherName,
                        moved = p.moved,
                        dimmed = dimmed,
                        onDetail = { onCourseDetail?.invoke(c, cardDate) },
                    )
                }
            }
        }
    }
}

/** 课程卡片：按卡片宽/高分级显示文字与顶部胶囊条。 */
@Composable
private fun CourseCard(
    c: Course,
    dayIndex: Int,
    dayWidth: Float,
    rowHeight: Float,
    compact: Boolean,
    span: Int,
    background: Color,
    barColor: Color,
    textColor: Color,
    showTeacherName: Boolean,
    moved: Boolean = false,
    dimmed: Boolean = false,
    onDetail: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val start = c.sections.first().coerceIn(1, 11)
    // 「不上了」半透明只能降各色 alpha，不能用 Modifier.alpha：
    // 捕获层(layerBackdrop)子树内的离屏 alpha 图层会被 HyperOS 3 RenderThread 丢弃，整卡不绘制
    val dim = if (dimmed) 0.4f else 1f
    val bgColor = background.copy(alpha = background.alpha * dim)
    val barColorDim = barColor.copy(alpha = barColor.alpha * dim)
    val textColorDim = textColor.copy(alpha = textColor.alpha * dim)
    val subColor = textColorDim.copy(alpha = textColorDim.alpha * 0.72f)
    val nameFont = if (compact) 10.5.sp else 12.sp
    val subFont = if (compact) 9.sp else 10.sp
    val nameLine = if (compact) 12.6f else 14.4f
    // 胶囊宽 = 卡宽 45%（限制 32~48dp）
    val cardW = dayWidth - 3f
    val barW = (cardW * 0.45f).coerceIn(32f, 48f)

    Box(
        Modifier
            .offset(
                x = (48f + (dayIndex - 1) * dayWidth + 1.5f).dp,
                y = ((start - 1) * rowHeight + 1.5f).dp,
            )
            .width(cardW.dp)
            .height((span * rowHeight - 3f).dp)
            .clip(RoundedCornerShape(if (compact) 10.dp else 15.dp))
            .background(bgColor)
            .clickable { onDetail() }
    ) {
        // 根据卡片高度分级（行高 × 节数）
        val hDp = (span * rowHeight - 3f).dp.value
        val roomy = hDp >= 80f
        val mid = hDp >= 52f
        val showTeacher = showTeacherName && c.teacher.isNotEmpty() && mid
        val showWeek = !compact && c.isAlternate && roomy && !moved
        val nameMaxLines = if (compact) (if (span == 1) 2 else 3) else (if (span == 1) 3 else 4)

        // 文字组：撑满整卡后居中（等同 Flutter Positioned.fill+Column center），
        // 避免文字贴顶与顶部胶囊重叠
        Box(Modifier.fillMaxSize().padding(horizontal = if (compact) 2.dp else 4.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    c.name,
                    textAlign = TextAlign.Center,
                    maxLines = nameMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = nameFont,
                    fontWeight = FontWeight.SemiBold,
                    color = textColorDim,
                    lineHeight = nameLine.sp,
                )
                if (showTeacher) {
                    Text(
                        c.teacher,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = subFont,
                        lineHeight = (if (compact) 10.8 else 12.0).sp,
                        color = subColor,
                    )
                }
                if (c.location.isNotEmpty()) {
                    Text(
                        c.location,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = subFont,
                        lineHeight = (if (compact) 10.8 else 12.0).sp,
                        color = subColor,
                    )
                }
                if (showWeek) {
                    Text(
                        c.weeksText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 9.sp,
                        lineHeight = 10.8.sp,
                        color = subColor.copy(alpha = subColor.alpha * 0.85f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        // 调休标记：钉在卡底一行，颜色跟随课程名，具体补哪天见详情
        if (moved && mid) {
            Text(
                "调休",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontSize = 8.5.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Medium,
                color = textColorDim,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
            )
        }
        // 胶囊标记条（顶部居中，纯装饰）
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (if (roomy) 6f else if (mid) 2f else 1f).dp)
                .width(barW.dp)
                .height((if (roomy) 3f else 2f).dp)
                .clip(RoundedCornerShape(999.dp))
                .background(barColorDim)
        )
    }
}

