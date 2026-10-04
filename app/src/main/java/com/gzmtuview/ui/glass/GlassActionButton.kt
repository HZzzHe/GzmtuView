package com.gzmtuview.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
 * 主操作液态玻璃按钮（整宽 CTA）：与 [TintedGlassButton] 同一套玻璃配方
 * （纯色画布 backdrop + vibrancy/blur/lens + 按压果冻形变），
 * 但更高、字号更大，并支持 loading 与禁用态。
 *
 * 页面 body 内（捕获层内）安全：折射内容为纯色画布，不自引用捕获层。
 */
@Composable
fun GlassActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    /** 着色（Hue + 覆盖），留空时取 colorScheme.primary */
    tint: Color = Color.Unspecified,
    height: Dp = 52.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val activeTint = if (tint.isSpecified) tint else scheme.primary
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope,
            // 零偏移时高光落在按钮中心
            position = { size, offset ->
                if (offset == Offset.Zero) Offset(size.width / 2f, size.height / 2f) else offset
            },
        )
    }
    // 折射底色在组件体内取值（不放调用方默认参数）：主题切换时即时更新，不残留旧主题色
    val backdropColor = if (isSystemInDarkTheme()) scheme.surface else Color(0xFFEFF0F4)
    val canvasBackdrop = rememberCanvasBackdrop { drawRect(backdropColor) }
    val labelColor = if (enabled) Color.White else scheme.onSurface.copy(alpha = 0.38f)

    Row(
        modifier
            .drawBackdrop(
                backdrop = canvasBackdrop,
                // Circular → Outline.Rounded（graphicsLayer 抗锯齿裁剪）
                shape = { Capsule(style = RoundedCornerStyle.Circular) },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                shadow = {
                    if (enabled) Shadow(radius = 6f.dp, offset = DpOffset(0f.dp, 2f.dp)) else null
                },
                layerBlock = {
                    val w = size.width
                    val h = size.height
                    val progress = if (enabled) interactiveHighlight.pressProgress else 0f
                    val scale = lerp(1f, 1f + 4f.dp.toPx() / h, progress)
                    val maxOffset = size.minDimension
                    val initialDerivative = 0.05f
                    val offset = interactiveHighlight.offset
                    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)
                    val maxDragScale = 4f.dp.toPx() / h
                    val offsetAngle = atan2(offset.y, offset.x)
                    scaleX = scale +
                        maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                        (w / h).fastCoerceAtMost(1f)
                    scaleY = scale +
                        maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                        (h / w).fastCoerceAtMost(1f)
                },
                onDrawSurface = {
                    drawRect(activeTint, blendMode = BlendMode.Hue)
                    drawRect(activeTint.copy(alpha = if (enabled) 0.75f else 0.18f))
                },
            )
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                enabled = enabled && !loading,
            ) { onClick() }
            .then(if (enabled) interactiveHighlight.modifier else Modifier)
            .then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
            .height(height)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(19.dp),
                strokeWidth = 2.2.dp,
                color = labelColor,
            )
            Spacer(Modifier.width(6.dp))
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(19.dp), tint = labelColor)
        }
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.W600, color = labelColor)
    }
}
