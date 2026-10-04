package com.gzmtuview.ui.tools.electric

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gzmtuview.data.model.DailyPoint
import com.gzmtuview.data.model.EstimateResult
import com.gzmtuview.data.model.estimate
import com.gzmtuview.data.model.fmtDate
import com.gzmtuview.data.model.kMaxDays
import com.gzmtuview.data.model.kMaxFutureDays
import com.gzmtuview.data.model.kModels
import com.gzmtuview.data.model.kStyleFactor
import com.gzmtuview.data.model.niceInterval
import com.gzmtuview.data.model.pickModelKey
import com.gzmtuview.data.model.shortDate
import com.gzmtuview.data.model.validateRange
import com.gzmtuview.data.net.WeatherService
import com.gzmtuview.ui.ScreenScaffold
import com.gzmtuview.ui.glass.FrostScrollPage
import com.gzmtuview.ui.glass.GlassActionButton
import com.gzmtuview.ui.glass.GlassDialog
import com.gzmtuview.ui.glass.GlassIconButton
import com.gzmtuview.ui.glass.LiquidGlassSwitch
import com.gzmtuview.ui.glass.TintedGlassButton
import com.gzmtuview.ui.settings.GroupDivider
import com.gzmtuview.ui.settings.SectionLabel
import com.gzmtuview.ui.settings.SettingsGroup
import com.gzmtuview.ui.settings.SettingsRow
import com.gzmtuview.ui.settings.settingsCardBase
import com.gzmtuview.ui.settings.settingsPageBase
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle as WdTextStyle
import java.util.Locale

private val Blue = Color(0xFF3A8DCC)
private val BlueBar = Color(0x8C3A8DCC)
private val Orange = Color(0xFFFB923C)
private val OrangeDark = Color(0xFFFF8F00)
private val Pink = Color(0xFFE86A9E)

/** 生活习惯按钮着色（能耗梯度：省电=青绿 / 标准=琥珀 / 高耗=红橙）。 */
private val kHabitTints = mapOf(
    "省电型" to Color(0xFF34B87A),
    "标准型" to Color(0xFFE09540),
    "高耗型" to Color(0xFFE05A4E),
)

/** 快捷区间（点一下即定好起止，省去翻日历）。 */
private val kPresets = listOf("近 7 天" to 7L, "近 30 天" to 30L, "近 90 天" to 90L)

private fun LocalDate.cn() = "${monthValue}月${dayOfMonth}日"

/**
 * 电费估算工具：按宿舍模型 + 真实温度估算区间用电与电费。对齐 Flutter ElectricFeePage。
 * 排版沿用设置页「灰底 + 白卡 + 灰色小节标题」语言；区间选择用液态玻璃对话框。
 */
@Composable
fun ElectricFeeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val weather = remember { WeatherService(ctx.filesDir) }

    val today = LocalDate.now()
    var rangeStart by remember { mutableStateOf(today.minusDays(29)) }
    var rangeEnd by remember { mutableStateOf(today) }
    var isMale by remember { mutableStateOf(true) }
    var isTopFloor by remember { mutableStateOf(false) }
    var style by remember { mutableStateOf("标准型") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<EstimateResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showRangePicker by remember { mutableStateOf(false) }

    // 预加载 bundled 历史数据 + 本地缓存（一次性）
    LaunchedEffect(Unit) {
        runCatching {
            val bundled = ctx.assets.open("weather_pack.json")
                .bufferedReader().use { it.readText() }
            weather.loadBundled(bundled)
        }
        weather.loadLocalCache()
    }

    fun runEstimate() {
        scope.launch {
            loading = true
            error = null
            result = null
            try {
                val fetched = weather.ensureRange(rangeStart, rangeEnd)
                val temps = mutableMapOf<String, Double>()
                var d = rangeStart
                while (!d.isAfter(rangeEnd)) {
                    weather[fmtDate(d)]?.let { temps[fmtDate(d)] = it }
                    d = d.plusDays(1)
                }
                val model = kModels[pickModelKey(isMale, isTopFloor)]!!
                val r = estimate(
                    temps = temps,
                    start = rangeStart,
                    end = rangeEnd,
                    model = model,
                    styleFactor = kStyleFactor[style] ?: 1.0,
                )
                result = r
                val totalDays = rangeEnd.toEpochDay() - rangeStart.toEpochDay() + 1
                error = if (temps.size < totalDays) {
                    "部分日期无温度数据（联网获取 $fetched 天），结果按已有数据估算"
                } else null
            } catch (e: Exception) {
                error = "估算失败：$e"
            } finally {
                loading = false
            }
        }
    }

    val model = kModels[pickModelKey(isMale, isTopFloor)]!!
    val pageBase = settingsPageBase()

    ScreenScaffold(
        hideBottomBar = true,
        // 区间选择对话框必须挂在主窗口浮层槽（Popup 子窗口内玻璃采样坐标会错位）
        overlay = {
            RangePickerDialog(
                visible = showRangePicker,
                initialStart = rangeStart,
                initialEnd = rangeEnd,
                onDismiss = { showRangePicker = false },
                onConfirm = { s, e ->
                    rangeStart = s
                    rangeEnd = e
                    showRangePicker = false
                },
            )
        },
    ) {
        FrostScrollPage(
            title = "电费估算",
            pageBase = pageBase,
            leading = {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onClick = onBack)
            },
        ) {
            // ---- 估算区间 ----
            SectionLabel("估算区间")
            SettingsGroup {
                SettingsRow(
                    title = "${rangeStart.cn()} ~ ${rangeEnd.cn()}",
                    subtitle = "${rangeEnd.toEpochDay() - rangeStart.toEpochDay() + 1} 天 · " +
                        "跨度 ≤ $kMaxDays 天，未来 ≤ $kMaxFutureDays 天",
                    leading = {
                        Icon(
                            Icons.Outlined.DateRange, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { showRangePicker = true },
                )
            }
            Spacer(Modifier.height(20.dp))

            // ---- 宿舍模型 ----
            SectionLabel("宿舍模型")
            SettingsGroup {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    FieldCaption("宿舍类别")
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TintedGlassButton(
                            label = "男生宿舍",
                            selected = isMale,
                            tint = Blue,
                            modifier = Modifier.weight(1f),
                            onClick = { isMale = true },
                        )
                        TintedGlassButton(
                            label = "女生宿舍",
                            selected = !isMale,
                            tint = Pink,
                            modifier = Modifier.weight(1f),
                            onClick = { isMale = false },
                        )
                    }
                }
                GroupDivider()
                SettingsRow(
                    title = "顶楼宿舍",
                    subtitle = "顶层西晒，空调负荷更高",
                    showChevron = false,
                    leading = {
                        Icon(
                            Icons.Outlined.ArrowUpward, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailing = {
                        LiquidGlassSwitch(
                            checked = isTopFloor,
                            onCheckedChange = { isTopFloor = it },
                            // 行背景即卡片基色
                            backdropColor = settingsCardBase(),
                        )
                    },
                )
                GroupDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    FieldCaption("生活习惯")
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        kStyleFactor.keys.forEach { k ->
                            TintedGlassButton(
                                label = k,
                                selected = style == k,
                                // 能耗梯度：省电=绿 / 标准=琥珀 / 高耗=红橙
                                tint = kHabitTints[k] ?: Orange,
                                modifier = Modifier.weight(1f),
                                onClick = { style = k },
                            )
                        }
                    }
                }
                if (model.estimated) {
                    GroupDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Outlined.WarningAmber, null, Modifier.size(18.dp),
                            tint = OrangeDark,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "男生非顶楼为外推模型（无实测数据），精度较低，结果仅供参考",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = OrangeDark,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            // ---- 主操作 ----
            GlassActionButton(
                label = if (loading) "估算中…" else "开始估算",
                icon = Icons.Outlined.Calculate,
                loading = loading,
                enabled = !loading,
                onClick = { runEstimate() },
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = OrangeDark)
            }

            // ---- 结果 ----
            result?.let {
                Spacer(Modifier.height(26.dp))
                SectionLabel("估算结果")
                EstimateResultView(it, model.name, ctx)
            }

            // ---- 脚注 ----
            Spacer(Modifier.height(26.dp))
            HorizontalDivider(
                Modifier.padding(horizontal = 28.dp),
                thickness = 0.6.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(Modifier.height(14.dp))
            val scheme = MaterialTheme.colorScheme
            Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                NoteText("温度数据由 Open-Meteo 提供（CC-BY 4.0）", scheme)
                NoteText(
                    "Open-Meteo 对 IP 存在访问限额，建议尽量在非校园网环境下估算，并避免频繁估算。",
                    scheme,
                )
                NoteText(
                    "估算结果仅供参考，以实际使用为准；\n" +
                        "建议空调使用较多时再进行估算（本工具主要估算空调用电，天冷时节精度较低）。",
                    scheme,
                )
                NoteText(
                    "该电费估算仅支持计算新空调",
                    scheme,
                    color = Color(0xFFE65100),
                    bold = true,
                )
                NoteText("当前计算模型版本：v26.26.37", scheme)
            }
        }
    }
}

@Composable
private fun FieldCaption(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---- 区间选择：液态玻璃对话框 + 自绘月历 ----

/**
 * 估算区间选择对话框：顶部「起 / 止」摘要胶囊 + 快捷区间，下方自绘月历。
 *
 * 不用 Material3 DateRangePicker：它的区间带几何写死在组件内部（铺满整行且含左右内衬），
 * 色带会从圆角日格外侧戳出；给日历限宽又会把周末一列的选中格挤成方块。自绘后色带端点
 * 停在日格圆心、只在该端是区间端点时取圆角，日格宽度按容器均分（不再挤压）。
 * 另外整块月历是普通 Row（非懒加载列表），在对话框正文槽的 verticalScroll 下也不会撞无限高约束。
 */
@Composable
private fun RangePickerDialog(
    visible: Boolean,
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val minDate = today.minusDays(365)
    val maxDate = today.plusDays(kMaxFutureDays.toLong())
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf<LocalDate?>(initialEnd) }
    var month by remember { mutableStateOf(initialStart.withDayOfMonth(1)) }

    // 对话框常驻浮层槽（关闭仍有退场动画），state 不随关闭销毁 → 每次打开重置
    LaunchedEffect(visible) {
        if (visible) {
            start = initialStart
            end = initialEnd
            month = initialStart.withDayOfMonth(1)
        }
    }

    fun applyRange(s: LocalDate, e: LocalDate) {
        start = s
        end = e
        month = s.withDayOfMonth(1)
    }

    // 已选好开始、点在开始之后 → 补全结束；其余（含点在当前开始之前）→ 重开一段只留开始
    fun pick(d: LocalDate) {
        if (end != null || d < start) {
            start = d
            end = null
        } else {
            end = d
        }
    }

    GlassDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = "估算区间",
        textMaxHeight = 520.dp,
        text = {
            Column(Modifier.fillMaxWidth()) {
                RangeSummary(start, end)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kPresets.forEach { (label, days) ->
                        TintedGlassButton(
                            label = label,
                            selected = false,
                            tint = null,
                            modifier = Modifier.weight(1f),
                            onClick = { applyRange(today.minusDays(days - 1), today) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                GlassRangeCalendar(
                    month = month,
                    start = start,
                    end = end,
                    minDate = minDate,
                    maxDate = maxDate,
                    onPick = { pick(it) },
                    onMonthChange = { month = it.withDayOfMonth(1) },
                )
            }
        },
        confirmText = "确定",
        onConfirm = {
            val e = end ?: start
            val err = validateRange(start, e)
            if (err != null) {
                Toast.makeText(ctx, err, Toast.LENGTH_SHORT).show()
            } else {
                onConfirm(start, e)
            }
        },
        dismissText = "取消",
        onDismiss = onDismiss,
    )
}

/** 星期表头（周一起始）。 */
private val kWeekCaps = listOf("一", "二", "三", "四", "五", "六", "日")

/** 月历：月份导航 + 星期表头 + 若干周行。 */
@Composable
private fun GlassRangeCalendar(
    month: LocalDate,
    start: LocalDate?,
    end: LocalDate?,
    minDate: LocalDate,
    maxDate: LocalDate,
    onPick: (LocalDate) -> Unit,
    onMonthChange: (LocalDate) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val firstOfMonth = month.withDayOfMonth(1)
    // 网格首格 = 该月 1 号所在周的周一（Mon=1 → 前移 0 天，Sun=7 → 前移 6 天）
    val leading = (firstOfMonth.dayOfWeek.value + 6) % 7
    val gridFirst = firstOfMonth.minusDays(leading.toLong())
    val rows = (leading + month.lengthOfMonth() + 6) / 7

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            MonthArrow(Icons.Outlined.ChevronLeft, "上一月", firstOfMonth > minDate.withDayOfMonth(1)) {
                onMonthChange(month.minusMonths(1))
            }
            Text(
                "${month.year}年${month.monthValue}月",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                fontWeight = FontWeight.W600,
                color = scheme.onSurface,
            )
            MonthArrow(Icons.Outlined.ChevronRight, "下一月", firstOfMonth < maxDate.withDayOfMonth(1)) {
                onMonthChange(month.plusMonths(1))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().height(26.dp)) {
            kWeekCaps.forEach { w ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(w, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
            }
        }
        repeat(rows) { r ->
            WeekRow(
                weekFirst = gridFirst.plusDays((r * 7).toLong()),
                month = month,
                start = start,
                end = end,
                minDate = minDate,
                maxDate = maxDate,
                onPick = onPick,
            )
        }
    }
}

/** 单周：整行一条区间色带（drawBehind）+ 七个日格。 */
@Composable
private fun WeekRow(
    weekFirst: LocalDate,
    month: LocalDate,
    start: LocalDate?,
    end: LocalDate?,
    minDate: LocalDate,
    maxDate: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val s = start
    val e = end
    // 月外的格子留空位，保证列对齐
    val days = (0..6).map { weekFirst.plusDays(it.toLong()) }
        .map { if (it.monthValue == month.monthValue && it.year == month.year) it else null }
    val firstVisible = days.indexOfFirst { it != null }
    val lastVisible = days.indexOfLast { it != null }
    val startCol = if (s != null) days.indexOf(s) else -1
    val endCol = if (e != null) days.indexOf(e) else -1
    val startHere = startCol >= 0
    val endHere = endCol >= 0
    val leftCol = if (startHere) startCol else firstVisible
    val rightCol = if (endHere) endCol else lastVisible
    // 本行是否被区间覆盖（单日区间由选中圆本身表达，不画带）
    val covered = s != null && e != null && s != e &&
        days.any { it != null && it >= s && it <= e }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellW = maxWidth / 7
        val circle = minOf(cellW - 4.dp, 40.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(minOf(cellW, 44.dp))
                .drawBehind {
                    if (covered && leftCol >= 0 && rightCol >= leftCol) {
                        val cw = size.width / 7f
                        val d = circle.toPx()
                        val inset = (cw - d) / 2f
                        val top = (size.height - d) / 2f
                        val r = d / 2f
                        val x0 = leftCol * cw + inset
                        val x1 = (rightCol + 1) * cw - inset
                        val band = scheme.primary.copy(alpha = 0.16f)
                        // 端点是区间端点的那一侧收半圆帽，另一侧方角续接到下一格
                        val capL = if (startHere) r else 0f
                        val capR = if (endHere) r else 0f
                        drawRect(band, Offset(x0 + capL, top), Size(x1 - x0 - capL - capR, d))
                        if (startHere) drawCircle(band, r, Offset(x0 + r, top + r))
                        if (endHere) drawCircle(band, r, Offset(x1 - r, top + r))
                    }
                },
        ) {
            days.forEach { d ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (d != null) {
                        DayCell(d, circle, s, e, today, minDate, maxDate, onPick)
                    }
                }
            }
        }
    }
}

/** 日格：选中为实心圆 + 白字，今天为描边圆，区间外置灰不可点。 */
@Composable
private fun DayCell(
    date: LocalDate,
    cell: Dp,
    start: LocalDate?,
    end: LocalDate?,
    today: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val selected = date == start || date == end
    val enabled = date in minDate..maxDate
    val isToday = date == today
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(cell)
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(interactionSource = interaction, indication = null) { onPick(date) } else Modifier)
            .background(
                when {
                    selected -> scheme.primary
                    pressed -> scheme.onSurface.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isToday && !selected) {
            Box(Modifier.matchParentSize().border(1.5.dp, scheme.primary, CircleShape))
        }
        Text(
            date.dayOfMonth.toString(),
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.W600 else FontWeight.W400,
            color = when {
                !enabled -> scheme.onSurface.copy(alpha = 0.25f)
                selected -> Color.White
                isToday -> scheme.primary
                else -> scheme.onSurface
            },
        )
    }
}

/** 月份翻页按钮（无玻璃折射，避免在对话框内采样错位）。 */
@Composable
private fun MonthArrow(icon: ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> Color.Transparent
                    pressed -> scheme.onSurface.copy(alpha = 0.08f)
                    else -> scheme.onSurface.copy(alpha = 0.05f)
                }
            )
            .then(if (enabled) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            desc,
            Modifier.size(22.dp),
            tint = scheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.25f),
        )
    }
}

/** 起止摘要：两个圆角胶囊 + 中间天数，随日历点选实时联动。 */
@Composable
private fun RangeSummary(start: LocalDate?, end: LocalDate?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        DateChip("开始", start)
        Text(
            if (start != null && end != null) "${end.toEpochDay() - start.toEpochDay() + 1} 天" else "—",
            fontSize = 12.sp,
            color = scheme.onSurfaceVariant,
        )
        DateChip("结束", end)
    }
}

@Composable
private fun DateChip(role: String, date: LocalDate?) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.onSurface.copy(alpha = 0.07f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(role, fontSize = 10.sp, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(1.dp))
        Text(
            date?.let {
                "${it.cn()} ${it.dayOfWeek.getDisplayName(WdTextStyle.SHORT, Locale.CHINA)}"
            } ?: "未选择",
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
            color = if (date == null) scheme.onSurfaceVariant else scheme.onSurface,
            maxLines = 1,
        )
    }
}

// ---- 说明文字 ----

@Composable
private fun NoteText(text: String, scheme: androidx.compose.material3.ColorScheme,
                     color: Color = scheme.outline, bold: Boolean = false) {
    Text(
        text,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        fontSize = 10.sp,
        color = color,
        fontWeight = if (bold) FontWeight.W600 else null,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    )
}

// ---- 结果视图（统计 + 双轴图表） ----

@Composable
private fun EstimateResultView(
    r: EstimateResult,
    modelName: String,
    ctx: android.content.Context,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(settingsCardBase())
            .padding(16.dp),
    ) {
        Text(modelName, fontWeight = FontWeight.W600, color = scheme.primary)
        Spacer(Modifier.height(12.dp))
        DailyChart(r.dailyPoints) { p ->
            Toast.makeText(
                ctx,
                "${p.date}\n用电 ${"%.1f".format(p.kwh)} 度 · 气温 ${"%.1f".format(p.temp)}°C",
                Toast.LENGTH_SHORT,
            ).show()
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 0.6.dp,
            color = scheme.outlineVariant)
        ResultRow("估算天数", "${r.days} 天")
        ResultRow("平均气温", "${"%.1f".format(r.avgTemp)} °C")
        ResultRow("预计总用电", "${"%.1f".format(r.totalKwh)} 度")
        ResultRow("空调增量", "${"%.1f".format(r.acKwh)} 度")
        ResultRow("预计电费", "${"%.2f".format(r.fee)} 元", emphasize = r.fee > 0)
        ResultRow("日均用电", "${"%.1f".format(r.dailyAvg)} 度/天")
        ResultRow("空调满负荷等效", "${"%.1f".format(r.hoursPerDay)} 小时/天")
    }
}

@Composable
private fun ResultRow(label: String, value: String, emphasize: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = if (emphasize) FontWeight.W700 else FontWeight.W500,
            color = if (emphasize) OrangeDark else scheme.onSurface,
        )
    }
}

/** 双轴叠加图：每日用电柱状（左轴，蓝）+ 日平均气温折线（右轴，橙）。点击柱形看当日明细。 */
@Composable
private fun DailyChart(points: List<DailyPoint>, onTap: (DailyPoint) -> Unit) {
    if (points.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val maxKwh = points.maxOf { it.kwh }
    val minTemp = points.minOf { it.temp }
    val maxTemp = points.maxOf { it.temp }
    val n = points.size
    val tempPad = (maxTemp - minTemp) * 0.15 + 1
    val yTempMin = minTemp - tempPad
    val yTempMax = maxTemp + tempPad
    val kwhInterval = if (maxKwh > 8) kotlin.math.ceil(maxKwh / 4) else 2.0
    val labelStep = if (n <= 8) 1 else kotlin.math.ceil(n / 6.0).toInt()

    Column(Modifier.fillMaxWidth()) {
        Row {
            LegendDot(Blue, "用电(度)", scheme)
            Spacer(Modifier.width(14.dp))
            LegendDot(Orange, "气温(°C)", scheme)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(190.dp).pointerInput(points) {
            detectTapGestures { offset ->
                // 简化命中：图表绘图区 x 方向映射最近柱
                val padL = 40f
                val padR = 40f
                val totalW = size.width
                if (offset.x in padL..(totalW - padR)) {
                    val plotW = totalW - padL - padR
                    val idx = ((offset.x - padL) / plotW * n).toInt().coerceIn(0, n - 1)
                    onTap(points[idx])
                }
            }
        }) {
            ChartCanvas(points, maxKwh, yTempMin, yTempMax, kwhInterval, labelStep, scheme)
        }
        Spacer(Modifier.height(4.dp))
        Text("注：数值为按模型与当日气温估算；点按柱形可看当日明细",
            fontSize = 10.sp, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendDot(color: Color, label: String, scheme: androidx.compose.material3.ColorScheme) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) {
            drawCircle(color)
        }
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun ChartCanvas(
    points: List<DailyPoint>,
    maxKwh: Double,
    yTempMin: Double,
    yTempMax: Double,
    kwhInterval: Double,
    labelStep: Int,
    scheme: androidx.compose.material3.ColorScheme,
) {
    val tm = rememberTextMeasurer()
    val axisGray = scheme.onSurfaceVariant
    val gridColor = axisGray.copy(alpha = 0.18f)
    Canvas(Modifier.fillMaxSize()) {
        val padL = 38f * density
        val padR = 38f * density
        val padT = 10f * density
        val padB = 22f * density
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB
        val n = points.size
        val maxY = kotlin.math.ceil(maxKwh * 1.2)

        fun kwhY(v: Double): Float = padT + (1f - (v / maxY).toFloat()) * plotH
        fun tempY(v: Double): Float =
            padT + (1f - ((v - yTempMin) / (yTempMax - yTempMin)).toFloat()) * plotH

        val axisText = TextStyle(fontSize = 8.sp, color = axisGray)

        // 用电横网格线 + 左轴刻度
        var gi = 0.0
        while (gi <= maxY) {
            val y = kwhY(gi)
            drawLine(
                gridColor,
                Offset(padL, y),
                Offset(size.width - padR, y),
                strokeWidth = 1f,
            )
            val label = tm.measure(gi.toInt().toString(), axisText)
            drawText(label, topLeft = Offset(padL - label.size.width - 4f * density, y - label.size.height / 2))
            gi += kwhInterval
        }
        // 左轴名
        val lAxis = tm.measure("用电(度)", TextStyle(fontSize = 8.sp, color = Blue))
        drawText(lAxis, topLeft = Offset(padL / 2 - lAxis.size.width / 2, padT))
        // 右轴名 + 刻度
        val tempInterval = niceInterval(yTempMin, yTempMax, 4)
        var tv = kotlin.math.ceil(yTempMin / tempInterval) * tempInterval
        while (tv <= yTempMax) {
            val y = tempY(tv)
            val label = tm.measure(tv.toInt().toString(), axisText)
            drawText(label, topLeft = Offset(size.width - padR + 4f * density, y - label.size.height / 2))
            tv += tempInterval
        }
        val rAxis = tm.measure("气温(°C)", TextStyle(fontSize = 8.sp, color = Orange))
        drawText(rAxis, topLeft = Offset(size.width - padR / 2 - rAxis.size.width / 2, padT))

        val slot = plotW / n
        val barW = (slot * 0.4f).coerceAtMost(8f * density)
        // 柱状（半透明蓝）
        points.forEachIndexed { i, p ->
            val x = padL + slot * i + (slot - barW) / 2
            val y = kwhY(p.kwh)
            drawRoundRect(
                BlueBar,
                topLeft = Offset(x, y),
                size = Size(barW, padT + plotH - y),
                cornerRadius = CornerRadius(2f * density),
            )
        }
        // 温度折线（橙）
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = padL + slot * i + slot / 2
            val y = tempY(p.temp)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // 面积填充
        val area = Path().apply {
            val first = points.first()
            moveTo(padL + slot / 2, padT + plotH)
            lineTo(padL + slot / 2, tempY(first.temp))
            points.forEachIndexed { i, p ->
                if (i > 0) lineTo(padL + slot * i + slot / 2, tempY(p.temp))
            }
            lineTo(padL + slot * (n - 1) + slot / 2, padT + plotH)
            close()
        }
        drawPath(area, Orange.copy(alpha = 0.08f))
        drawPath(path, Orange, style = Stroke(width = 2f * density))
        // 底部日期（抽稀）
        points.forEachIndexed { i, p ->
            if (i % labelStep != 0) return@forEachIndexed
            val label = tm.measure(shortDate(p.date), TextStyle(fontSize = 8.sp, color = axisGray))
            val cx = padL + slot * i + slot / 2
            drawText(label, topLeft = Offset(cx - label.size.width / 2, padT + plotH + 4f * density))
        }
    }
}
