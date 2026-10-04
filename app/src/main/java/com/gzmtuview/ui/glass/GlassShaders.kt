package com.gzmtuview.ui.glass

import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.runtimeShaderEffect

/**
 * 自定义 AGSL 效果（移植自 Pronto app 的开源实现，Apache-2.0）。
 *
 * 1. [luminanceMapped]：亮度映射玻璃——按背后内容的亮度经贝塞尔曲线映射，
 *    使玻璃自适应背景明暗（亮背景→透亮白玻璃、暗背景→沉稳深玻璃），
 *    同时可调饱和度与亮度。Pronto 底栏"通透自适应"质感的核心。
 * 2. [verticalTint]：顶部→底部渐进着色（顶部强、底部弱），用于玻璃面板的纵向渐变染色。
 */

private const val LUMINANCE_MAP_SHADER = """
uniform shader content;
uniform float p0, p1, p2, p3;
uniform float mapIntensity;
uniform float saturation;
uniform float brightness;

const vec3 LUMA_WEIGHTS = vec3(0.2126, 0.7152, 0.0722);

float bezierMap(float x) {
    float invX = 1.0 - x;
    float invX2 = invX * invX;
    float invX3 = invX2 * invX;
    float x2 = x * x;
    float x3 = x2 * x;
    return invX3 * p0 + 3.0 * invX2 * x * p1 + 3.0 * invX * x2 * p2 + x3 * p3;
}

vec4 main(vec2 fragCoord) {
    vec3 rgb = content.eval(fragCoord).rgb;
    float luma1 = dot(rgb, LUMA_WEIGHTS);
    float mappedLuma = bezierMap(luma1);
    vec3 colorMapped = mix(rgb, vec3(mappedLuma), mapIntensity);
    float luma2 = dot(colorMapped, LUMA_WEIGHTS);
    vec3 colorSaturated = mix(vec3(luma2), colorMapped, saturation);
    vec3 finalColor = colorSaturated + vec3(brightness);
    return vec4(clamp(finalColor, 0.0, 1.0), 1.0);
}
"""

private const val VERTICAL_TINT_SHADER = """
uniform shader content;
uniform float height;
uniform float4 tint;
uniform float tintIntensity;

half4 main(float2 coord) {
    float tintAlpha = smoothstep(height, height * 0.5, coord.y);
    return mix(content.eval(coord), tint * tintAlpha, tintIntensity * tintAlpha);
}
"""

/**
 * 亮度映射玻璃效果。
 * p0..p3：亮度→目标亮度的贝塞尔控制点（x=背后内容 luma，y=输出亮度）；
 * mapIntensity：映射混合强度；saturation：饱和度；brightness：加亮。
 */
fun BackdropEffectScope.luminanceMapped(
    p0: Float,
    p1: Float,
    p2: Float,
    p3: Float,
    mapIntensity: Float,
    saturation: Float,
    brightness: Float,
) {
    runtimeShaderEffect(
        key = "gmuv_luminance_map",
        shaderString = LUMINANCE_MAP_SHADER,
        uniformShaderName = "content",
    ) {
        setFloatUniform("p0", p0)
        setFloatUniform("p1", p1)
        setFloatUniform("p2", p2)
        setFloatUniform("p3", p3)
        setFloatUniform("mapIntensity", mapIntensity)
        setFloatUniform("saturation", saturation)
        setFloatUniform("brightness", brightness)
    }
}

/** 顶部→底部渐进着色（tint 在顶部以 [intensity] 强度显示，向底部渐隐）。 */
fun BackdropEffectScope.verticalTint(
    tint: Color,
    intensity: Float,
    heightPx: Float,
) {
    runtimeShaderEffect(
        key = "gmuv_vertical_tint",
        shaderString = VERTICAL_TINT_SHADER,
        uniformShaderName = "content",
    ) {
        setFloatUniform("height", heightPx)
        setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
        setFloatUniform("tintIntensity", intensity)
    }
}
