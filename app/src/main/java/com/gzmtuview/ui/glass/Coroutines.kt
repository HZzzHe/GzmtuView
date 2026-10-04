/*
 * 派生自 AndroidLiquidGlass 的 catalog 组件/工具
 * （https://github.com/Kyant0/AndroidLiquidGlass），Copyright 2025 Kyant，
 * 以 Apache License 2.0 授权。本文件已按本项目需要修改（包名、主题取色、
 * 触感与布局适配等）。完整许可见 third_party/AndroidLiquidGlass-LICENSE.txt。
 */
package com.gzmtuview.ui.glass

// 对齐 demo 的 expect/actual（KMP）；本工程为纯 Android 模块，直接实现。
suspend fun awaitFrame() {
    kotlinx.coroutines.android.awaitFrame()
}
