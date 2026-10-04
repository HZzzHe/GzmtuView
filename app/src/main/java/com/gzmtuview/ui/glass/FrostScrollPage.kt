package com.gzmtuview.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.gzmtuview.ui.wallpaper.Wallpaper

/**
 * 带渐隐磨砂页头的整页滚动壳：正文铺满整屏并滚入页头下方，页头底下垫一层
 * [progressiveBlur]（与考试页同款），内容滚到标题行时已被纯色覆盖盖到读不出。
 *
 * 满强度平台正好止于页头下沿，页头外只留 [fadeHeight] 一段渐隐收尾；
 * 正文顶部预留「页头 + 整段渐隐带」，因此静止时首块完全清晰，
 * 只有真正滚进带里才开始溶解。
 *
 * 安全约束：磨砂节点的捕获层 [scrollBackdrop] 是正文 Column 自身，磨砂 Box 必须
 * 作为它的**兄弟**（排在页头之前）绘制，绝不能放进该子树内。
 */
@Composable
fun FrostScrollPage(
    title: String,
    pageBase: Color,
    modifier: Modifier = Modifier,
    fadeHeight: Dp = 28.dp,
    blurRadius: Dp = 40.dp,
    horizontalPadding: Dp = 16.dp,
    topSpacing: Dp = 4.dp,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBackdrop = rememberLayerBackdrop()
    var headerPx by remember { mutableIntStateOf(0) }
    val headerPad = with(LocalDensity.current) { headerPx.toDp() }
    val frostHeight = headerPad + fadeHeight
    val frostHold = 1f - fadeHeight / frostHeight
    // 根底仅深色模式让位给背板（浅色保持不透明，避免深色壁纸从浅底/磨砂间透出）；
    // 下方磨砂 tint 必须继续用实色 pageBase，否则盖不住滚进页头的正文
    Box(modifier.fillMaxSize().background(Wallpaper.pageBaseDarkYield(pageBase))) {
        Column(
            Modifier
                .fillMaxSize()
                .layerBackdrop(scrollBackdrop)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding),
        ) {
            // 静止时正文首块必须落在渐隐带下沿之外，否则分组标签会被洗成半透明
            Spacer(Modifier.height(frostHeight + topSpacing))
            content()
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(frostHeight)
                .progressiveBlur(
                    backdrop = scrollBackdrop,
                    tint = pageBase,
                    blurRadius = blurRadius,
                    tintIntensity = 0.9f,
                    holdFraction = frostHold,
                ),
        )
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerPx = it.height },
        ) {
            GlassTopBar(
                title = title,
                backgroundColor = Color.Transparent,
                leading = leading,
            )
        }
    }
}
