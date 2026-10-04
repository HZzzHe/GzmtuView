package com.gzmtuview.ui.glass

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 交互触感（默认关）。
 *
 * 只走系统振动效果（不自定义马达曲线、不申请额外权限），
 * [enabled] 由启动时的偏好读入 + 设置页开关写穿，触发点无需再查偏好。
 */
object Haptics {

    /** 触感总开关；关闭时所有 fire 直接返回。 */
    var enabled: Boolean = false

    private var vibrator: Vibrator? = null

    /** App 启动时挂载一次（用 applicationContext，无泄漏风险）。 */
    fun attach(context: Context) {
        vibrator = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 玻璃按钮按动、底部标签页切换、周次步进器切周。 */
    fun tap() = fire(VibrationEffect.EFFECT_CLICK, 24L)

    /** 开关翻转、周次滑块跨一档。 */
    fun tick() = fire(VibrationEffect.EFFECT_TICK, 14L)

    private fun fire(effect: Int, fallbackMs: Long) {
        if (!enabled) return
        val v = vibrator ?: return
        try {
            if (!v.hasVibrator()) return
            val fx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(effect)
            } else {
                VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            v.vibrate(fx)
        } catch (_: Exception) {
            // 振动失败不影响交互
        }
    }
}
