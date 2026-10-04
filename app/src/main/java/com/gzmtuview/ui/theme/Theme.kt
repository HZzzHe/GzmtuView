package com.gzmtuview.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** 与 Flutter 版 AppTheme 对齐的 Material 3 主题。
 *  说明：ColorScheme 由 seed 派生时，Container/Secondary 等派生色与 Flutter
 *  fromSeed 输出存在微小差异；P2 UI 阶段按对照结果细调。
 */
private val LightColors = lightColorScheme(
    primary = Seed,
    surface = LightSurface,
    onSurface = LightOnSurface,
    // background 同步覆盖：Scaffold 等默认取 background，
    // M3 由 seed 派生的默认 background 带暖红调，导致内容区与顶栏(surface)色不一致
    background = LightSurface,
    onBackground = LightOnSurface,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHighest = LightSurfaceContainer,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

private val DarkColors = darkColorScheme(
    primary = Seed,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHighest = DarkSurfaceContainer,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

@Composable
fun GmuViewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
    ) {
        // MaterialTheme 不设 LocalContentColor；未指定 color 的 Text 会落到平台默认黑色
        // （浅色下恰好正确、深色下课程名/考试时间等仍为黑）。全局兜底主题色，
        // 已显式取色或自带 contentColor 的组件不受影响。
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            content()
        }
    }
}
