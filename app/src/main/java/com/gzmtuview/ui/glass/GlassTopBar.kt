package com.gzmtuview.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 页内常驻顶栏（合并进页面 body 的"独立顶栏区域"替代方案）：
 * - 随页面参与切页/push 动画（不再是全局浮层），顶栏依然固定在页面顶部、不随列表滚动；
 * - 通过 [LocalTopBarBackdrop] 给 leading/trailing 里的玻璃按钮提供**顶栏局部捕获层**
 *   （官方 LiquidToggle 轨道/滑块同款机制）：按钮作为 layerBackdrop 节点的兄弟
 *   浮层，折射顶栏自身背景，无捕获层自采样风险。
 *
 * 布局对齐 Flutter 版：标题行自然高度（非 64dp 居中），[subtitle] 紧贴标题
 * 下方（如课表的周次选择器），整体紧凑；leading（返回）槽在标题左前方、
 * trailing 按钮与标题行垂直对齐。
 */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    /** 顶栏底色（默认 surface；设置页等灰底页面传入页面基色以融为一体）。 */
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    /** 标题前景色：壁纸让位的页面传入自适应墨色（默认主题 onSurface）。 */
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    // 注意：必须排在 trailing 之前——Kotlin 尾随 lambda 恒匹配最后一个参数，
    // 各调用点的 trailing 均用尾随 lambda 语法传入
    /** 标题同行后缀（如课表"第 n 周"周次选择器）：紧跟标题右侧、垂直居中 */
    titleTrailing: (@Composable () -> Unit)? = null,
    /** 右侧按钮距屏幕右缘的边距（默认 8dp；课表「更多」用 18dp，与系统相册的按钮位置对齐） */
    trailingEndPadding: Dp = 8.dp,
    trailing: (@Composable () -> Unit)? = null,
) {
    val barBackdrop = rememberLayerBackdrop()
    // 有 leading（返回按钮）时标题给左侧导航槽让位：12dp 槽边距 + 36dp 按钮 + 8dp 间隙
    val titleStart = if (leading != null) 56.dp else 16.dp
    Box(
        modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        // 捕获内容列：状态栏内衬 + 标题行 + 可选第二行（玻璃按钮在此外面）
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .layerBackdrop(barBackdrop)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    // 与 leading/trailing 按钮行同构（top 2dp + 44dp 高）且垂直居中，
                    // 保证标题文字与玻璃按钮同心对齐
                    .padding(top = 2.dp)
                    .height(44.dp)
                    .padding(
                        start = titleStart,
                        // 56 = 8dp 按钮边距 + 36dp 按钮 + 12dp 间隙；按钮外移时同步让位
                        end = if (trailing != null) trailingEndPadding + 48.dp else 16.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    // fill=false：标题过长时省略号收缩，不挤压同行后缀
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleLarge,
                    // 压缩 MiSans 默认行高：标题底部到第二行的间隙对齐 Flutter 版
                    lineHeight = 30.sp,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (titleTrailing != null) {
                    Box(Modifier.padding(start = 12.dp)) { titleTrailing() }
                }
            }
            if (subtitle != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = titleStart, end = 56.dp, bottom = 4.dp)
                ) {
                    subtitle()
                }
            }
        }
        // 返回按钮浮层（兄弟节点，左上导航槽）
        if (leading != null) {
            CompositionLocalProvider(LocalTopBarBackdrop provides barBackdrop) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(top = 2.dp, start = 12.dp)
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leading()
                }
            }
        }
        // 玻璃按钮浮层（兄弟节点，与标题行垂直对齐）
        if (trailing != null) {
            CompositionLocalProvider(LocalTopBarBackdrop provides barBackdrop) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 2.dp, end = trailingEndPadding)
                        .height(44.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    trailing()
                }
            }
        }
    }
}

/** 页内玻璃按钮的局部折射源；无 GlassTopBar 时为 null（回落到全局捕获层）。 */
val LocalTopBarBackdrop = staticCompositionLocalOfNull()

private fun staticCompositionLocalOfNull(): androidx.compose.runtime.ProvidableCompositionLocal<Backdrop?> =
    androidx.compose.runtime.staticCompositionLocalOf { null }
