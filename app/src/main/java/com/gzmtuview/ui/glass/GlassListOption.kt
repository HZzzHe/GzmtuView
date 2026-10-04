package com.gzmtuview.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle

/**
 * 对话框内的玻璃列表选项（单选行）：整行铺满、左侧文字、选中时行尾勾。
 *
 * 与 [TintedGlassButton] 同一套玻璃配方（纯色画布 backdrop + Hue 着色），
 * 因此可以安全地放在对话框面板子树内（不采样祖先捕获层，无渲染树自引用风险）。
 * 用于替换「一堆胶囊按钮挤在一起」的 FlowRow 选择器。
 */
@Composable
fun GlassListOption(
    label: String,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // DrawScope 内不能读 MaterialTheme：composable 上下文先取值
    val base = scheme.surface
    val unselectedSurface = base.copy(alpha = 0.35f)
    val pressedOverlay = scheme.onSurface.copy(alpha = 0.06f)
    val labelColor = if (selected) Color.White else scheme.onSurface
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = rememberCanvasBackdrop { drawRect(base) },
                // Circular → Outline.Rounded 走 graphicsLayer 裁剪，圆角不发锯齿
                shape = { RoundedRectangle(14.dp, RoundedCornerStyle.Circular) },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                // 紧凑阴影：库默认 radius 24dp 的溢出会被对话框视口切断成硬边
                shadow = { Shadow(radius = 4f.dp, offset = DpOffset(0f.dp, 1f.dp)) },
                onDrawSurface = {
                    if (selected) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    } else {
                        drawRect(unselectedSurface)
                        if (pressed) drawRect(pressedOverlay)
                    }
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .height(48.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = Color.White)
        }
    }
}

/**
 * 下拉式单选：默认只有一行「当前选中项 + 上下箭头」，点箭头才展开菜单列表。
 *
 * 展开后走外层 [GlassDialog] text 槽自带的 heightIn + 垂直滚动，学期很多时自动滚，
 * 不会把对话框撑破。
 */
@Composable
fun GlassChoiceDropdown(
    options: List<String>,
    selectedIndex: Int,
    tint: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val base = scheme.surface
    val fieldSurface = base.copy(alpha = 0.35f)
    val fieldLabel = options.getOrNull(selectedIndex)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = rememberCanvasBackdrop { drawRect(base) },
                    shape = { RoundedRectangle(14.dp, RoundedCornerStyle.Circular) },
                    effects = {
                        vibrancy()
                        blur(2f.dp.toPx())
                        lens(12f.dp.toPx(), 24f.dp.toPx())
                    },
                    shadow = { Shadow(radius = 4f.dp, offset = DpOffset(0f.dp, 1f.dp)) },
                    onDrawSurface = { drawRect(fieldSurface) },
                )
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = { expanded = !expanded },
                )
                .height(48.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                fieldLabel ?: "未选择",
                fontSize = 14.sp,
                color = if (fieldLabel == null) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                if (expanded) "收起列表" else "展开列表",
                Modifier.size(22.dp),
                tint = scheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEachIndexed { i, label ->
                    GlassListOption(
                        label = label,
                        selected = i == selectedIndex,
                        tint = tint,
                        onClick = {
                            onSelect(i)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
