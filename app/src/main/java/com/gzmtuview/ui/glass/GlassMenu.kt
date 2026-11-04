package com.gzmtuview.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gzmtuview.data.model.Semester
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 菜单卡满宽（相册菜单 731px @3.75 = 195dp）；卡片动画的 offset 数学与此常量绑定 */
internal const val GlassMenuCardWidth = 195f

/** 菜单卡满宽时的圆角（75px = 20dp） */
internal const val GlassMenuCardRadius = 20f

/** 卡片常态面底色（0.68 时课表胶囊仍会透色，调到 0.82 让背景不清晰可见） */
internal const val GlassMenuCardFill = 0.82f

/** 菜单行高：相册 161px @3.75 ≈ 43dp */
internal val GlassMenuRowHeight = 43.dp

/** 卡片内容内衬（相册 28px = 7.5dp）；行内再缩进 12.5dp，文字距卡边 20dp */
internal val GlassMenuCardPadding = 7.5.dp

/**
 * 玻璃菜单行：与系统相册一致——无图标、16sp、43dp 行高，文字距卡边 20dp。
 * 按压有圆角底色 + 轻微缩放的即时反馈（玻璃面板上不用 ripple，观感更干净）。
 */
@Composable
internal fun GlassMenuRow(
    label: String,
    modifier: Modifier = Modifier,
    autoSize: TextAutoSize? = null,
    labelColor: Color = Color.Unspecified,
    horizontalPadding: Dp = 12.5.dp,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ink = if (labelColor == Color.Unspecified) scheme.onSurface else labelColor
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(140))
    val pressBg by animateFloatAsState(if (pressed) 0.10f else 0f, tween(140))
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = GlassMenuRowHeight)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.onSurface.copy(alpha = pressBg))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            fontSize = 16.sp,
            color = ink,
            maxLines = 1,
            autoSize = autoSize,
        )
        trailing?.invoke()
    }
}

/** 菜单行尾箭头：收起时向右（›），展开时向上（⌃）；二级栏标题与触发行共用。 */
@Composable
internal fun GlassMenuChevron(expanded: Boolean) {
    val rotation by animateFloatAsState(
        if (expanded) -90f else 0f,
        tween(200, easing = FastOutSlowInEasing),
        label = "glassMenuChevron",
    )
    Icon(
        Icons.Outlined.ChevronRight,
        null,
        Modifier
            .size(18.dp)
            .graphicsLayer { rotationZ = rotation },
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 菜单内发丝分割线：左右内缩与行文字同宽（默认 12.5dp，体测面板用 8dp）。 */
@Composable
internal fun GlassMenuDivider(horizontalPadding: Dp = 12.5.dp) {
    Box(
        Modifier
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

/** 学期列表行（最多同显 4 行 + 右侧滚动条）；当前学期用 [accent] 蓝字标记，不占行尾。 */
@Composable
internal fun SemesterPickerList(
    semesters: List<Semester>,
    selected: Semester?,
    accent: Color,
    onSelect: (Semester) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    // 单行实测高度：列表上限 = 4 行，字体缩放时行高变化也仍然只露 4 行
    var semRowH by remember { mutableStateOf(GlassMenuRowHeight) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = semRowH * 4),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll),
        ) {
            semesters.forEachIndexed { i, sem ->
                GlassMenuRow(
                    label = sem.label,
                    modifier = if (i == 0) {
                        Modifier.onSizeChanged {
                            semRowH = with(density) { it.height.toDp() }
                        }
                    } else {
                        Modifier
                    },
                    // 学期名含学年 + 学期，比相册菜单项长；autoSize 只在系统字体放大到
                    // 一行放不下时兜底（默认字号下保持 16sp）
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 11.sp,
                        maxFontSize = 16.sp,
                        stepSize = 0.5.sp,
                    ),
                    labelColor = if (sem == selected) accent else Color.Unspecified,
                ) { onSelect(sem) }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            val max = scroll.maxValue
            if (max <= 0) return@Canvas
            val viewport = size.height
            val thumbH = (viewport * viewport / (viewport + max))
                .coerceAtLeast(24.dp.toPx())
            val top = (viewport - thumbH) * scroll.value / max
            drawRoundRect(
                color = scheme.onSurface.copy(alpha = 0.18f),
                topLeft = Offset(size.width - 5.dp.toPx(), top),
                size = Size(3.dp.toPx(), thumbH),
                cornerRadius = CornerRadius(1.5.dp.toPx()),
            )
        }
    }
}

/**
 * 卡片以布局尺寸做「触发体尺寸 → 整卡」的动画，内容等比缩放并贴左上角。
 * 玻璃节点不能用外层 graphicsLayer 缩放/位移：drawBackdrop 按布局坐标采样背板，
 * 外层变换会让采样矩形错位，出现硬边白块 / 半透明白块分界线（2026-10-31 真机帧确认）。
 */
private fun Modifier.cardMorph(
    startSize: DpSize,
    grow: () -> Float,
    shrink: () -> Float = { 1f },
) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val fullW = placeable.width.toFloat()
    val fullH = placeable.height.toFloat()
    val startW = startSize.width.toPx()
    val startH = startSize.height.toPx()
    val g = grow()
    val s = shrink()
    val w = (startW + (fullW - startW) * g) * s
    val h = (startH + (fullH - startH) * g) * s
    layout(w.roundToInt(), h.roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            scaleX = w / fullW
            scaleY = h / fullH
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

/**
 * 二级栏的展开高度：内容按完整高度测量，报告 `minHeight + (fullHeight - minHeight) × progress`。
 * [minHeight] 是「只显示标题行的小卡」高度——相册里二级卡先完整出现为一张标题卡，
 * 再向下长出列表；从 0 高度长起会先露出一条线，视觉上像从行上沿裂开。
 * 内容由外层 clipToBounds 裁切。
 */
internal fun Modifier.revealHeight(
    progress: () -> Float,
    minHeight: Dp = 0.dp,
) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val min = minHeight.roundToPx().coerceIn(0, placeable.height)
    val p = progress().coerceIn(0f, 1f)
    val h = (min + (placeable.height - min) * p).roundToInt()
    layout(placeable.width, h) {
        placeable.place(0, 0)
    }
}

/**
 * 从触发按钮长出来的玻璃菜单卡（青课表 ⋮ 菜单与考试页学期菜单共用）。
 * - 打开：「⋮」/按钮图标原地淡出，卡片以触发体尺寸（默认 36dp 圆）弹出，中心先到终位、尺寸后到；
 * - 收回：尺寸和位移同帧启动，中心领先，卡片缩回触发体，图标/文字接管；
 * - 全程只有一张玻璃卡，玻璃节点不做任何外层变换（采样不错位）。
 *
 * [anchorCenter]：触发体中心相对整卡右上角的偏移（课表 ⋮ 为 `-24dp, +24dp`；
 * 考试页「选择学期」按钮离屏右更近，为 `-14dp, +24dp`）。
 * [anchorTop]：整卡右上角相对状态栏下沿再往下的距离（默认 0 = 顶栏按钮下方）；
 * 体测年级这类行内触发器用它把卡片压到「行内值」的位置。
 * [rightMargin]：整卡右缘距屏幕右侧的距离（默认 12dp；与设置卡对齐时传 16dp）。
 * [anchorSize]：触发体收缩态尺寸（默认 36dp 圆按钮）；行内「当前值」等文字触发器
 * 传文字实测尺寸，卡片就从这块文字框长出来 / 缩回去，而不是一个圆。
 * [anchorRadius]：收缩态圆角，默认短边一半（圆按钮 / 胶囊）。
 * [anchorContent]：浮层里接管的触发体替身（图标或文字）；打开时原地淡出、收回时淡入。
 * [pinAnchorContent]：替身固定钉在锚点（文字触发器用，收回时文字在原位浮现）；
 * 默认 false = 收回时跟随缩小卡片的中心（按钮图标与卡片一起回位）。
 * [iconTint]：触发图标颜色（默认 onSurface；行内浅色箭头用它无缝接管）。
 * [shrink]：二级栏浮起时整卡缩小比例，由页面自己驱动（默认不缩）。
 * [secondary]：与卡片同容器、叠在卡片之上的二级栏浮层（课表学期选择用）。
 * [blockContentClicks]：二级栏浮起期间拦截主菜单内容的点击（转给 [onDismiss]，
 * 页面据此只收起二级栏）；二级栏本身在其上层，不受影响。
 */
@Composable
internal fun AnchoredGlassMenu(
    open: Boolean,
    icon: ImageVector? = null,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
    anchorCenter: DpOffset = DpOffset((-24).dp, 24.dp),
    anchorTop: Dp = 0.dp,
    rightMargin: Dp = 12.dp,
    anchorSize: DpSize = DpSize(36.dp, 36.dp),
    anchorRadius: Dp? = null,
    anchorContent: (@Composable () -> Unit)? = null,
    pinAnchorContent: Boolean = false,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    shrink: () -> Float = { 1f },
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
    secondary: @Composable BoxScope.() -> Unit = {},
    blockContentClicks: Boolean = false,
) {
    var shown by remember { mutableStateOf(false) }
    // 相册的原生帧里，卡片右上角不是固定锚点：卡片中心先用较快曲线走到终位，
    // 尺寸后到（约 330ms），于是右上角先内收约 22dp、再随尺寸长满甩回终位。
    // 因此这里把「中心位置」和「尺寸」拆成两条曲线，offset 由两者合成。
    val move = remember { Animatable(0f) }
    val grow = remember { Animatable(0f) }
    // 内容撑满时的卡片高度（由 Column 实测），用于把中心路径换算成右上角偏移
    var fullH by remember { mutableStateOf(187.dp) }
    val fade = remember { Animatable(0f) }
    // 卡片上的图标：打开时从 1 淡出（按钮在圆里消失），收起时中段淡入（菜单渐变成按钮）。
    // 图标画在卡片坐标系里并反向缩放，屏幕上恒 18dp；卡片缩到 36dp 时正好等于真实按钮的图标。
    val iconInk = remember { Animatable(1f) }
    LaunchedEffect(open) {
        if (open) {
            move.snapTo(0f)
            grow.snapTo(0f)
            fade.snapTo(0f)
            iconInk.snapTo(1f)
            shown = true
            // 相册里图标是在原按钮位置原地渐隐，不跟着卡片走（跟着走会像按钮瞬移）
            launch { iconInk.animateTo(0f, tween(durationMillis = 90, easing = LinearEasing)) }
            // 先让圆在按钮上停一拍，再按相册的节奏展开：
            // 中心约 170ms 先到终位（快速减速），尺寸 ~330ms 才长满（长尾缓收）。
            launch {
                delay(30)
                grow.animateTo(1f, tween(durationMillis = 330, easing = LinearOutSlowInEasing))
            }
            launch {
                delay(30)
                move.animateTo(1f, spring(dampingRatio = 1f, stiffness = 1200f))
            }
            // 行文字跟着扩张一起浮现（相册也是卡片长满时文字已基本可见），不拖到落定之后
            delay(45)
            fade.animateTo(1f, tween(durationMillis = 100, easing = LinearEasing))
        } else if (shown) {
            // 收起对照系统相册逐帧（img/ 60fps 录屏）：先让文字在卡片原位淡出，
            // 随后整卡用短促的 160ms 收进按钮，而不是让一块空白的卡连续缩 280ms
            // （旧节奏里文字早已消失、白卡还在慢慢收，正是「生硬」的来源）。
            // 位移与尺寸同曲线同时结束，右上角不再先滑到终位再等尺寸。
            launch { fade.animateTo(0f, tween(durationMillis = 80, easing = LinearEasing)) }
            // 图标等卡片基本收进按钮再浮现（相册也是缩到按钮大小后才看到三点），
            // 太早出现会像一颗图标在缩小的菜单中央「坐电梯」
            launch { iconInk.animateTo(1f, tween(durationMillis = 70, delayMillis = 130, easing = LinearEasing)) }
            launch {
                delay(40)
                grow.animateTo(0f, tween(durationMillis = 160, easing = LinearOutSlowInEasing))
            }
            // 主协程必须等动画走完再撤层：先置 shown=false 会让整层重组离场、动画被取消
            delay(40)
            move.animateTo(0f, tween(durationMillis = 160, easing = LinearOutSlowInEasing))
            shown = false
            onClosed()
        }
    }
    // 浮层被宿主摘除（设置覆盖页会抑制父页浮层）时收起动画会中断，
    // 在这里无条件交还真实按钮，避免 closing 永远为 true。
    DisposableEffect(Unit) {
        onDispose { onClosed() }
    }
    // 关闭且不在动画中：整层浮层不参与布局（shown 在收起动画走完后才置 false）
    if (!shown && !open) return

    val density = LocalDensity.current
    val surfaceSolid = MaterialTheme.colorScheme.surface
    // 屏幕圆角：收缩态取触发体自身圆角（圆按钮 = 半径；文字框 = 胶囊短边一半），
    // 随尺寸增长先保持圆、再收拢到 20dp——相册的容器也是「圆 → 圆角方 → 菜单」
    // 逐段变形，而不是一开始就是方角。
    // lens 效果只接受 RoundedCornerShape / CornerBasedShape，不能自造 Shape（会直接抛异常）。
    val startRadius: Dp = anchorRadius ?: (minOf(anchorSize.width, anchorSize.height) / 2)
    val cardRadius: () -> Dp = {
        val g = grow.value
        val morph = (g / 0.6f).coerceIn(0f, 1f)
        startRadius + (GlassMenuCardRadius.dp - startRadius) * morph
    }

    Box(Modifier.fillMaxSize()) {
        if (shown) {
            // 点菜单外关闭（相册同样不压暗页面）
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = null, indication = null) { onDismiss() },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    // 终位右上角 = 状态栏下沿（+ anchorTop）、距屏右 rightMargin：
                    // 整颗按钮落在卡片下方
                    .padding(top = anchorTop, end = rightMargin),
                contentAlignment = Alignment.TopEnd,
            ) {
                Box(
                    Modifier
                        // 右上角偏移 = 中心位置 + 当前尺寸的一半。中心用 move 先到终位、
                        // 尺寸用 grow 后到，所以右上角先内收再甩出（相册同款轨迹），
                        // 不是钉在终位只做尺寸变化。仍是布局级 offset，玻璃节点无外层变换。
                        // 二级栏展开时整卡再按 shrink 缩小（锚定右上角：上沿/右缘不动，
                        // 下沿上移、左缘右移——相册的二级栏浮起方式）。
                        .offset {
                            val g = grow.value
                            val m = move.value
                            val fullW = GlassMenuCardWidth
                            val fullHdp = fullH.value
                            val startW = anchorSize.width.value
                            val startH = anchorSize.height.value
                            val baseW = startW + (fullW - startW) * g
                            val baseH = startH + (fullHdp - startH) * g
                            // 中心相对右上角的坐标：起点 = 触发按钮中心，终点 = 整卡中心
                            val centerX = anchorCenter.x.value + (-fullW / 2f - anchorCenter.x.value) * m
                            val centerY = anchorCenter.y.value + (fullHdp / 2f - anchorCenter.y.value) * m
                            // 右缘/上沿不动：缩小时下沿上移、左缘右移（h 已含 shrink）
                            IntOffset(
                                (centerX + baseW / 2f).dp.roundToPx(),
                                (centerY - baseH / 2f).dp.roundToPx(),
                            )
                        }
                        // 这一层不做任何缩放/位移：
                        // drawBackdrop 的背板采样按节点布局坐标算，外层 scale 会让采样矩形错位，
                        // 出现硬边的白块 / 半透明白块分界线（2026-10-31 真机帧确认）。
                        .drawBackdrop(
                            backdrop = LocalAppBackdrop.current,
                            shape = { RoundedCornerShape(cardRadius()) },
                            effects = {
                                // 不用亮度映射：菜单背后是复杂课表内容，会斑驳
                                verticalTint(
                                    tint = surfaceSolid,
                                    intensity = 0.45f,
                                    heightPx = 300 * density.density,
                                )
                                vibrancy()
                                // 相册菜单是「白雾 + 模糊」：大半径模糊把课表压成柔和色块，
                                // 只留极轻的透镜；靠模糊而不是靠高不透明度遮内容
                                blur(22f.dp.toPx())
                                lens(3f.dp.toPx(), 4f.dp.toPx())
                            },
                            // 阴影随卡片长出来 / 收回去。收尾不能为 0：真实 GlassIconButton
                            // 用库默认 Shadow（radius 24dp / offset 4dp），卡片缩到 36dp 时
                            // 必须已经是同一组参数，否则交回真实按钮的一瞬阴影会突然出现。
                            shadow = {
                                val g = grow.value
                                Shadow(
                                    radius = (24f + (16f - 24f) * g).dp,
                                    offset = DpOffset(0.dp, 4.dp),
                                    color = Color.Black.copy(alpha = 0.10f),
                                )
                            },
                            // 面底色恒定：卡片自己缩成圆，收尾与真实按钮同尺寸同形状
                            onDrawSurface = { drawRect(surfaceSolid.copy(alpha = GlassMenuCardFill)) },
                        )
                        // 布局尺寸真实动画（触发体尺寸 → 整卡），内容在内部等比缩放贴左上角。
                        // 玻璃节点不能被外层缩放，否则背板采样错位；相册也是「容器先圆后展开」。
                        .cardMorph(
                            startSize = anchorSize,
                            grow = { grow.value },
                            shrink = shrink,
                        )
                        .width(GlassMenuCardWidth.dp),
                ) {
                    Column(
                        Modifier
                            .onSizeChanged { fullH = with(density) { it.height.toDp() } }
                            .graphicsLayer { alpha = fade.value }
                            .then(contentModifier)
                            .padding(GlassMenuCardPadding),
                        content = content,
                    )
                    // 二级栏浮起时盖住主菜单内容（二级栏在其后绘制、仍可交互）：
                    // 此时点菜单标题/设置/CSV/卡内空隙都只走 onDismiss，由页面收起二级栏
                    if (blockContentClicks) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .clickable(interactionSource = null, indication = null) { onDismiss() },
                        )
                    }
                }
                // 二级栏浮层：与卡片同容器、盖在卡片之上；位置由页面自己定
                secondary()
                // 触发体替身：打开时固定在原按钮/文字位置原地渐隐（相册如此；跟着卡片走会像瞬移），
                // 收起时才落到卡面中心、跟着缩小的卡片回到触发体，尺寸与位置无缝交接。
                // 文字触发器用 pinAnchorContent 始终钉在文字原位，收回时文字在原处浮现。
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset {
                            val x: Float
                            val y: Float
                            val startW = anchorSize.width.value
                            val startH = anchorSize.height.value
                            if (open || pinAnchorContent) {
                                // 替身盒中心 = 触发体中心
                                x = anchorCenter.x.value + startW / 2f
                                y = anchorCenter.y.value - startH / 2f
                            } else {
                                val g = grow.value
                                val m = move.value
                                val fullW = GlassMenuCardWidth
                                val fullHdp = fullH.value
                                val w = startW + (fullW - startW) * g
                                val h = startH + (fullHdp - startH) * g
                                val offX = anchorCenter.x.value + (-fullW / 2f - anchorCenter.x.value) * m + w / 2f
                                val offY = anchorCenter.y.value + (fullHdp / 2f - anchorCenter.y.value) * m - h / 2f
                                x = offX - w / 2f + startW / 2f
                                y = offY + h / 2f - startH / 2f
                            }
                            IntOffset(x.dp.roundToPx(), y.dp.roundToPx())
                        }
                        .graphicsLayer { alpha = iconInk.value }
                        .size(anchorSize),
                    contentAlignment = Alignment.Center,
                ) {
                    if (anchorContent != null) {
                        anchorContent()
                    } else if (icon != null) {
                        Icon(
                            icon,
                            null,
                            Modifier.size(18.dp),
                            tint = iconTint,
                        )
                    }
                }
            }
        }
    }
}
