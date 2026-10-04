/*
 * 派生自 AndroidLiquidGlass 的 catalog 组件 DialogContent
 * （https://github.com/Kyant0/AndroidLiquidGlass），Copyright 2025 Kyant，
 * 以 Apache License 2.0 授权。本文件已按本项目需要修改（包名、主题取色、
 * 玻璃底色与布局适配等）。完整许可见 third_party/AndroidLiquidGlass-LICENSE.txt。
 */
package com.gzmtuview.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle

/**
 * 对话框内玻璃子组件（如 [LiquidSlider]）的折射源：对话框面板表面的纯色画布。
 * 由 [GlassDialog] 在自定义正文槽提供，避免子组件折射到主窗口内容
 * （如课表的网格线/课程定位辅助线被滑块折射进去）。
 * 不在对话框内时为 null，调用方应回落到 [LocalAppBackdrop]。
 */
val LocalDialogBackdrop: androidx.compose.runtime.ProvidableCompositionLocal<Backdrop?> =
    staticCompositionLocalOf { null }

/**
 * 液态玻璃对话框（官方 DialogContent demo 一比一配方）：
 * 背景调暗层（浅 29293A@0.23 / 深 121212@0.56）+ RoundedRectangle(48dp)
 * 玻璃面板（colorControls brightness 浅0.2/深0 + saturation 1.5 + blur 浅16/深8 +
 * lens(24,48,depthEffect) + Highlight.Plain + surface 浅 FAFAFA@0.6 / 深 121212@0.4）。
 * 标题 24sp Medium / 双胶囊按钮 48dp（确认=主题色白字、取消=玻璃@0.2）。
 * 面板宽度上限：竖屏 560dp、横屏（宽屏）440dp，收敛为居中面板（窄屏不受限）。
 *
 * 必须挂在主窗口坐标（AppScreen 的 overlay 槽 / ScreenScaffold(overlay=...)），
 * 绝不能放 Popup 或捕获层内。进出动画内置：spring(0.9/500) 弹入、tween(120) 收回。
 *
 * @param visible      显示意图（由调用方 state 驱动）
 * @param textString   简单正文（15sp，主色 68% 透明度）；复杂内容用 [text]
 * @param text         自定义正文内容
 * @param confirmText  确认按钮文案（胶囊主题色）；null 且 dismissText 也为 null 时显示单个"好的"
 * @param dismissText  取消按钮文案（玻璃胶囊）；null = 无取消按钮
 */
@Composable
fun GlassDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    textString: String? = null,
    text: (@Composable ColumnScope.() -> Unit)? = null,
    confirmText: String? = null,
    onConfirm: () -> Unit = {},
    dismissText: String? = null,
    onDismiss: () -> Unit = {},
    /** 第三按钮（如课程详情的「不上了/要上课」）：玻璃胶囊底，文字用 [neutralColor] */
    neutralText: String? = null,
    neutralColor: Color? = null,
    onNeutral: () -> Unit = {},
    /** 正文槽可视高度上限（超出即内部滚动）。日历等固定高度内容可放宽。 */
    textMaxHeight: Dp = 420.dp,
) {
    var shown by remember { mutableStateOf(false) }
    val pop = remember { Animatable(0f) }
    // 显示期间通知外壳临时隐藏玻璃底栏（压暗层盖不住浮层）
    val host = com.gzmtuview.ui.LocalPageHost.current
    // 兜底：调用处用 "state?.let { GlassDialog(visible = true) }" 模式时，
    // 关闭=组件直接离组，下方 LaunchedEffect 的复位不会执行——
    // 离组时强制复位，否则底栏永久消失（课程详情/导出对话框踩坑）
    androidx.compose.runtime.DisposableEffect(host) {
        onDispose { host.dialogActive = false }
    }
    LaunchedEffect(visible) {
        if (visible) {
            shown = true
            host.dialogActive = true
            pop.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 500f))
        } else if (shown) {
            pop.animateTo(0f, tween(durationMillis = 120))
            shown = false
            host.dialogActive = false
        }
    }
    if (!shown) return

    val isLightTheme = !isSystemInDarkTheme()
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.6f)
        else Color(0xFF121212).copy(0.4f)
    val dimColor =
        if (isLightTheme) Color(0xFF29293A).copy(0.23f)
        else Color(0xFF121212).copy(0.56f)
    val accentColor = MaterialTheme.colorScheme.primary

    // 对话框面板表面的纯色折射源：供面板内玻璃子组件（滑块等）折射，
    // 替代主窗口 backdrop，避免把课表网格线等背景内容折射进滑块
    val dialogSurfaceColor = if (isLightTheme) Color(0xFFFAFAFA) else Color(0xFF121212)
    val dialogSurface = rememberCanvasBackdrop { drawRect(dialogSurfaceColor) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // 整体淡入：调暗层柔和出现，不再与底栏退场跳变
                alpha = pop.value
            }
    ) {
        // 横屏（宽屏）收窄到 440dp、竖屏保持 560dp：避免横屏下对话框过宽
        val widthCap = if (maxWidth > maxHeight) 440.dp else 560.dp
        // 背景调暗层（点外部关闭）
        Box(
            Modifier
                .fillMaxSize()
                .background(dimColor)
                .clickable(interactionSource = null, indication = null) { onDismissRequest() }
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        // 从外到内打开：scale 1.08→1（收回反向 1→1.08），
                        // alpha 同步 0→1；比从内弹出的观感更轻柔
                        val v = pop.value
                        val scale = 1.08f - 0.08f * v
                        scaleX = scale
                        scaleY = scale
                        alpha = v
                    }
                    .drawBackdrop(
                        backdrop = LocalAppBackdrop.current,
                        shape = { RoundedRectangle(48f.dp) },
                        effects = {
                            colorControls(
                                brightness = if (isLightTheme) 0.2f else 0f,
                                saturation = 1.5f,
                            )
                            blur((if (isLightTheme) 16f else 8f).dp.toPx())
                            lens(24f.dp.toPx(), 48f.dp.toPx(), depthEffect = true)
                        },
                        highlight = { Highlight.Plain },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .widthIn(max = widthCap)
                    .fillMaxWidth()
            ) {
                Column {
                    Text(
                        title,
                        Modifier.padding(28.dp, 24.dp, 28.dp, 12.dp),
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                    )
                    if (textString != null) {
                        Text(
                            textString,
                            Modifier.padding(24.dp, 12.dp, 24.dp, 12.dp),
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = contentColor.copy(0.68f),
                        )
                    }
                    if (text != null) {
                        Column(
                            Modifier
                                .heightIn(max = textMaxHeight)
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp, 12.dp, 24.dp, 12.dp),
                        ) {
                            // 面板内玻璃子组件（滑块等）折射对话框表面纯色，
                            // 而非主窗口内容（课表网格线等）
                            CompositionLocalProvider(LocalDialogBackdrop provides dialogSurface) {
                                text()
                            }
                        }
                    }
                    Row(
                        Modifier
                            .padding(24.dp, 12.dp, 24.dp, 24.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (neutralText != null) 8.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val hasNeutral = neutralText != null
                        val hasDismiss = dismissText != null
                        val hasConfirm = confirmText != null
                        if (!hasNeutral && !hasDismiss && !hasConfirm) {
                            GlassDialogButton("好的", containerColor.copy(0.2f), contentColor, Modifier.weight(1f)) {
                                onDismissRequest()
                            }
                        }
                        if (hasNeutral) {
                            GlassDialogButton(
                                neutralText!!,
                                // 玻璃胶囊底在浅色面板上近白不可见：用中性色淡底保证胶囊可读
                                (neutralColor ?: contentColor).copy(alpha = 0.12f),
                                neutralColor ?: contentColor,
                                Modifier.weight(1f),
                            ) {
                                onNeutral()
                            }
                        }
                        if (hasDismiss) {
                            GlassDialogButton(dismissText!!, containerColor.copy(0.2f), contentColor, Modifier.weight(1f)) {
                                onDismiss()
                            }
                        }
                        if (hasConfirm) {
                            GlassDialogButton(confirmText!!, accentColor, Color.White, Modifier.weight(1f)) {
                                onConfirm()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 官方对话框胶囊按钮：48dp 高、Capsule、16sp 文案、横向 16dp 内边距。 */
@Composable
private fun GlassDialogButton(
    label: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(com.kyant.shapes.Capsule())
            .background(background)
            .clickable(interactionSource = null, indication = null) { onClick() }
            .height(48.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, color = contentColor, maxLines = 1)
    }
}
