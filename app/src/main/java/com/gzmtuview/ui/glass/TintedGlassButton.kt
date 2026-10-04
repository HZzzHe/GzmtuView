/*
 * 派生自 AndroidLiquidGlass 的 catalog 组件 LiquidButton（着色配方与按压形变同源）
 * （https://github.com/Kyant0/AndroidLiquidGlass），Copyright 2025 Kyant，
 * 以 Apache License 2.0 授权。本文件已按本项目需要修改（包名、主题取色、
 * 玻璃底色与布局适配等）。完整许可见 third_party/AndroidLiquidGlass-LICENSE.txt。
 */
package com.gzmtuview.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedCornerStyle
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 着色液态玻璃按钮（官方 LiquidButton 一比一 + 纯色画布 backdrop）。
 * 页面 body 内（捕获层内）安全：折射内容为纯色画布（官方 ToggleContent 第二例）。
 *
 * - selected=true 且给定 [tint]：Hue 混合着色 + 75% 覆盖（官方配方）
 * - 未选中：素玻璃（surface 35% 覆盖）
 * - 按压果冻形变/拖拽缩放与官方 LiquidButton 相同
 */
@Composable
fun TintedGlassButton(
    label: String,
    selected: Boolean,
    tint: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    /** 玻璃折射底色。留空（Unspecified）时在组件体内取 surface，确保主题切换即时更新；
     *  传值则由调用方固定。 */
    backdropColor: Color = Color.Unspecified,
    /** 变化时触发一次「按压-回弹」动画（如滑动切页联动按钮，高光从按钮中心泛起）。 */
    pulseKey: Any? = null,
    /** 投影：默认紧凑小控件阴影（官方 demo 小部件用 radius 4dp）。
     *  库默认 Shadow.Default（radius 24dp）溢出约 48dp，会被对话框裁剪视口切断。 */
    shadow: Shadow = Shadow(radius = 4f.dp, offset = DpOffset(0f.dp, 1f.dp)),
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope,
            // 零偏移（程序化 pulse）时高光落在按钮中心；手势偏移不会恰好为精确零
            position = { size, offset ->
                if (offset == androidx.compose.ui.geometry.Offset.Zero) {
                    androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                } else {
                    offset
                }
            },
        )
    }
    // 玻璃底色在体内取 surface（而非调用方默认参数）：本组件因读 isSystemInDarkTheme
    // 会随主题重组，体内取值保证深色→浅色切换后折射底色即时更新，不残留旧主题色
    val effectiveBackdropColor =
        if (backdropColor.isSpecified) backdropColor else MaterialTheme.colorScheme.surface
    val canvasBackdrop = rememberCanvasBackdrop { drawRect(effectiveBackdropColor) }
    // 滑动切页联动：仅「selected 发生 false→true 转变且非本按钮点击触发」才播脉冲。
    // 注意不能用「pulseKey 变化 && selected」判断——常亮选中态的按钮（如考试页
    // 「回顾中」）每次页切换都会满足，导致多按钮同时形变、大小间距失衡。
    // 点击自己切页时 clickable 先置 suppressPulse 跳过（只保留手势按压反馈）。
    var lastSelected by remember { mutableStateOf(selected) }
    var suppressPulse by remember { mutableStateOf(false) }
    LaunchedEffect(pulseKey, selected) {
        if (selected != lastSelected) {
            val becameSelected = selected && !lastSelected
            lastSelected = selected
            if (becameSelected && !suppressPulse) {
                interactiveHighlight.pressPulse()
            }
            suppressPulse = false
        }
    }
    // DrawScope 内不能读 MaterialTheme：composable 上下文先取值
    val unselectedSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
    val activeTint = if (selected) tint else null
    val labelColor =
        when {
            activeTint != null -> Color.White
            isSystemInDarkTheme() -> Color(0xFFE6E6E6)
            else -> Color(0xFF3A3A3C)
        }

    Row(
        modifier
            .drawBackdrop(
                backdrop = canvasBackdrop,
                // Circular 圆角 → Outline.Rounded（graphicsLayer clip 抗锯齿）；
                // 默认 Continuous 走 Outline.Generic（clipPath 不 AA → 边角锯齿）
                shape = { Capsule(style = RoundedCornerStyle.Circular) },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                shadow = { shadow },
                layerBlock = {
                    val width = size.width
                    val height = size.height
                    val progress = interactiveHighlight.pressProgress
                    val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)
                    val maxOffset = size.minDimension
                    val initialDerivative = 0.05f
                    val offset = interactiveHighlight.offset
                    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)
                    val maxDragScale = 4f.dp.toPx() / size.height
                    val offsetAngle = atan2(offset.y, offset.x)
                    scaleX = scale +
                        maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                        (width / height).fastCoerceAtMost(1f)
                    scaleY = scale +
                        maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                        (height / width).fastCoerceAtMost(1f)
                },
                onDrawSurface = {
                    if (activeTint != null && activeTint.isSpecified) {
                        drawRect(activeTint, blendMode = BlendMode.Hue)
                        drawRect(activeTint.copy(alpha = 0.75f))
                    } else {
                        drawRect(unselectedSurface)
                    }
                },
            )
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
            ) {
                // 本次页切换由点击本按钮发起：跳过 pulse 联动（只保留手势按压反馈）
                suppressPulse = true
                onClick()
            }
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
            .height(38.dp)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 5.dp).height(16.dp),
                tint = labelColor,
            )
        }
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = labelColor,
            maxLines = 1,
        )
    }
}
