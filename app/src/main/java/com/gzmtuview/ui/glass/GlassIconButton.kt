/*
 * 派生自 AndroidLiquidGlass 的 catalog 组件 LiquidButton（按压交互同源）
 * （https://github.com/Kyant0/AndroidLiquidGlass），Copyright 2025 Kyant，
 * 以 Apache License 2.0 授权。本文件已按本项目需要修改（包名、主题取色、
 * 玻璃底色与布局适配等）。完整许可见 third_party/AndroidLiquidGlass-LICENSE.txt。
 */
package com.gzmtuview.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * 应用全局 backdrop（AppScreen 玻璃模式下提供真实内容层）。
 * 供页面顶栏 actions、浮层等玻璃组件折射内容使用。
 */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

/**
 * 圆形液态玻璃图标按钮（照官方 LiquidButton 的按压交互：果冻形变+缩放+高光）。
 * 折射 [LocalAppBackdrop] 背后内容；无涟漪，保持玻璃质感。
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    diameterDp: Int = 36,
    backdrop: Backdrop? = null,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    Box(
        modifier
            .drawBackdrop(
                // 页内顶栏按钮经 LocalTopBarBackdrop 折射顶栏局部内容；
                // 其余（浮层上的按钮）回落全局捕获层
                backdrop = backdrop ?: LocalTopBarBackdrop.current ?: LocalAppBackdrop.current,
                shape = { androidx.compose.foundation.shape.CircleShape },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                    lens(10f.dp.toPx(), 14f.dp.toPx())
                },
                layerBlock = {
                    val progress = interactiveHighlight.pressProgress
                    val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)
                    scaleX = scale
                    scaleY = scale
                    val offset = interactiveHighlight.offset
                    val maxOffset = size.minDimension
                    val initialDerivative = 0.05f
                    translationX = maxOffset * kotlin.math.tanh(initialDerivative * offset.x / maxOffset)
                    translationY = maxOffset * kotlin.math.tanh(initialDerivative * offset.y / maxOffset)
                },
                onDrawSurface = {
                    drawRect(surfaceColor)
                },
            )
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
            .size(diameterDp.dp)
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(18.dp), tint = tint)
    }
}
