/*
 * 派生自 AndroidLiquidGlass 的 catalog 组件 「Alpha 遮罩渐进模糊」demo
 * （https://github.com/Kyant0/AndroidLiquidGlass），Copyright 2025 Kyant，
 * 以 Apache License 2.0 授权。本文件已按本项目需要修改（包名、主题取色、
 * 玻璃底色与布局适配等）。完整许可见 third_party/AndroidLiquidGlass-LICENSE.txt。
 */
package com.gzmtuview.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect

/**
 * 渐进式磨砂（官方 AndroidLiquidGlass demo「Alpha 遮罩渐进模糊」同款机制）：
 * 先 [blur] 折射 [backdrop]，再用 AGSL 运行时着色器把模糊结果**混向一层纯色 [tint]**，
 * 并按纵向位置做 alpha 渐隐——被折射内容滚过时「越靠近强边越看不清」，
 * 而非整块均匀模糊。作为某个容器（如页头）的背景修饰，容器内的文字/按钮照常绘制在上方。
 *
 * 关键：内容「看不清」主要靠 [tintIntensity] 那层近乎不透明的着色覆盖，而不是靠超大模糊半径。
 * 单纯堆半径有两个害处——① 大半径下节点左右边缘外没有可采样内容，只能靠边缘复制，
 * 于是「中间糊、两边还看得清」；② 高斯结果仍保留原始对比度，粗体大字只是变成灰印子。
 * 纯色覆盖在横向上天然均匀，边缘瑕疵被压到 (1 − tintIntensity) 的权重里，同时把内容彻底盖掉。
 *
 * 安全约束：本修饰是一个 `drawPlainBackdrop` 玻璃节点，其 [backdrop] 捕获节点必须是
 * 本节点的**兄弟**（不能位于该 layerBackdrop 子树内），否则自引用渲染树崩溃
 * （HyperOS 3 / RenderThread SIGSEGV 红线，同 GlassTopBar 顶栏按钮范式）。
 *
 * @param blurRadius 折射模糊半径（有 tint 覆盖兜底，取中低值即可，别追大）
 * @param tint 磨砂层覆盖的纯色，应与页面底色一致，否则页头会显出色块
 * @param tintIntensity 强边处向 [tint] 混合的比例，越大越不透明、越盖不住内容
 * @param opaqueTop  true = 顶部最强、向下渐隐（页头）；false = 底部最强、向上渐隐
 * @param holdFraction 自强边起保持满强度（s=1）的高度比例，其余部分才渐隐。
 *   页头内的按钮行必须落在满强度区内，否则按钮处内容仍清晰可读（像没磨砂）。
 */
fun Modifier.progressiveBlur(
    backdrop: Backdrop,
    tint: Color,
    blurRadius: Dp = 28.dp,
    tintIntensity: Float = 0.82f,
    opaqueTop: Boolean = true,
    holdFraction: Float = 0f,
): Modifier = drawPlainBackdrop(
    backdrop = backdrop,
    shape = { RectangleShape },
    effects = {
        blur(blurRadius.toPx())
        // content = 上面 blur 链接后的结果；纵向 smoothstep 遮罩 + 纯色覆盖
        runtimeShaderEffect(
            "gmuview_progressive_blur",
            """
    uniform shader content;

    uniform float2 size;
    uniform float opaqueTop;
    uniform float hold;
    uniform float tintIntensity;
    layout(color) uniform half4 tint;

    half4 main(float2 coord) {
        float t = clamp(coord.y / max(size.y, 1.0), 0.0, 1.0);
        t = mix(1.0 - t, t, opaqueTop);
        // 先走满强度平台，剩余区间再做 smoothstep 渐隐
        t = clamp((t - hold) / max(1.0 - hold, 0.001), 0.0, 1.0);
        float s = 1.0 - t * t * (3.0 - 2.0 * t);
        // 预乘色：模糊内容与纯色覆盖共用同一个强度 s，直接线性混合
        return mix(content.eval(coord), tint, tintIntensity) * s;
    }""",
            "content",
        ) {
            setFloatUniform("size", size.width, size.height)
            setFloatUniform("opaqueTop", if (opaqueTop) 1f else 0f)
            setFloatUniform("hold", holdFraction.coerceIn(0f, 0.95f))
            setFloatUniform("tintIntensity", tintIntensity.coerceIn(0f, 1f))
            setColorUniform("tint", tint)
        }
    },
)
