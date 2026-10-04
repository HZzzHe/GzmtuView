package com.gzmtuview.ui.theme

import androidx.compose.ui.graphics.Color

// 主题种子色：航海蓝（与 Flutter 版 AppTheme.seedColor = #0061A4 保持一致）
val Seed = Color(0xFF0061A4)

// 浅色覆盖（与 Flutter 版一致）
val LightSurface = Color(0xFFFCFDFF)
val LightSurfaceContainer = Color(0xFFF4F6FA)
// 「灰底 + 白卡」页面的底色：比 surfaceContainer 再深一档。surfaceContainer(#F4F6FA)
// 与卡片 surface(#FCFDFF) 只差 3%，高亮屏/平板上分组圆角完全看不出轮廓。
val LightPageBase = Color(0xFFE9ECF2)
val LightOnSurface = Color(0xFF1A1C1E)
val LightOnSurfaceVariant = Color(0xFF42474E)
val LightOutlineVariant = Color(0xFFD8DDE5)
val LightOutline = Color(0xFF73777F) // 次级灰（时间轴/节次辅助文字）

// 深色覆盖（与 Flutter 版一致）
val DarkSurface = Color(0xFF10151B)
val DarkSurfaceContainer = Color(0xFF1C2128)
val DarkOnSurface = Color(0xFFE0E2E9)
val DarkOnSurfaceVariant = Color(0xFFC3C6CF)
val DarkOutlineVariant = Color(0xFF42474E)
val DarkOutline = Color(0xFF8D9199)
