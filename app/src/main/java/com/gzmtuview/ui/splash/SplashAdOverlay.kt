package com.gzmtuview.ui.splash

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/** 开屏广告停留时长（毫秒）。 */
private const val TOTAL_MS = 5000L

/** 去重力后的瞬时加速度阈值（m/s²）：握持晃动约 3~8，明显摇动超过 12。 */
private const val SHAKE_THRESHOLD = 13f

/** 重力低通系数：越大越"迟钝"，只保留慢变的姿态分量。 */
private const val GRAVITY_ALPHA = 0.8f

/**
 * 彩蛋：开屏「摇一摇广告」。
 *
 * 纯文字画面停留 5 秒；提示用户摇一摇直达设置页、在设置页连点「GZMTU View」5 次关闭本广告。
 * 传感器只在这 5 秒窗口内注册，触发一次即注销（[AtomicBoolean] 防重入）。
 */
@Composable
fun SplashAdOverlay(
    onFinished: () -> Unit,
    onShake: () -> Unit,
) {
    val ctx = LocalContext.current
    var remainMs by remember { mutableLongStateOf(TOTAL_MS) }
    val finished = remember { AtomicBoolean(false) }
    val shakeCallback = rememberUpdatedState(onShake)
    val finishCallback = rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        val start = SystemClock.elapsedRealtime()
        while (true) {
            val left = (TOTAL_MS - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(0L)
            remainMs = left
            if (left == 0L) break
            delay(64)
        }
        if (finished.compareAndSet(false, true)) finishCallback.value()
    }

    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val main = Handler(Looper.getMainLooper())
        val gravity = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (finished.get()) return
                gravity[0] = GRAVITY_ALPHA * gravity[0] + (1 - GRAVITY_ALPHA) * event.values[0]
                gravity[1] = GRAVITY_ALPHA * gravity[1] + (1 - GRAVITY_ALPHA) * event.values[1]
                gravity[2] = GRAVITY_ALPHA * gravity[2] + (1 - GRAVITY_ALPHA) * event.values[2]
                val dx = event.values[0] - gravity[0]
                val dy = event.values[1] - gravity[1]
                val dz = event.values[2] - gravity[2]
                if (sqrt(dx * dx + dy * dy + dz * dz) > SHAKE_THRESHOLD) {
                    main.post {
                        if (finished.compareAndSet(false, true)) shakeCallback.value()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sm != null && sensor != null) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sm?.unregisterListener(listener) }
    }

    val seconds = ((remainMs + 999) / 1000).coerceAtLeast(1)
    val accent = Color(0xFF7CC0F0)
    val hint = Color.White.copy(alpha = 0.72f)

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B101B), Color(0xFF16253D)),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 26.dp, vertical = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "广告 · 彩蛋",
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = hint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(
                            0.8.dp,
                            Color.White.copy(alpha = 0.22f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "$seconds 秒后自动关闭",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.58f),
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                "GZMTU View",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.6.sp,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "课表 · 考试 · 成绩，一屏尽览",
                fontSize = 15.sp,
                letterSpacing = 0.4.sp,
                color = hint,
            )

            Spacer(Modifier.height(36.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(
                        0.8.dp,
                        Color.White.copy(alpha = 0.14f),
                        RoundedCornerShape(24.dp),
                    )
                    .padding(22.dp),
            ) {
                Text(
                    "摇一摇手机",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "立即跳转至设置页",
                    fontSize = 14.sp,
                    color = hint,
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.8.dp)
                        .background(Color.White.copy(alpha = 0.12f)),
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "想关闭这个开屏广告？",
                    fontSize = 14.sp,
                    color = hint,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "在设置页连续点击「GZMTU View」5 次",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 23.sp,
                    color = accent,
                )
            }

            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.14f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(remainMs / TOTAL_MS.toFloat())
                        .fillMaxHeight()
                        .background(accent),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "本广告为应用彩蛋，无真实推广内容",
                fontSize = 10.5.sp,
                color = Color.White.copy(alpha = 0.36f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
