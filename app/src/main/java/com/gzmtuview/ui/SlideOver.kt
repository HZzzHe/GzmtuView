package com.gzmtuview.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** 覆盖式二级页的容器形状：滑动方向前缘（左缘）上下两个大圆角。 */
val SlideOverShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)

/**
 * 覆盖式二级页外壳：左缘圆角 + 页面底色 + 恒定高层级（zIndex 2），
 * 保证 push 进入时覆盖旧页、pop 退出时在上层滑出。
 * 必须放在捕获层内的页面组合树中（非玻璃组件，安全）。
 *
 * 注意：不能加 shadow/elevation——HyperOS 3 (Android 16) 上 elevation 层与
 * 玻璃捕获层（layerBackdrop + RenderEffect）叠加会让 RenderThread 在
 * MiBackgroundBlurBlend::preUpdateInfo → promotedToLayer 处递归到栈溢出
 * （SIGSEGV，体测/电费/设置打开即闪退）；去 shadow 后三页恢复正常。
 */
@Composable
fun SlideOverShell(content: @Composable () -> Unit) {
    Box(
        Modifier
            .zIndex(2f)
            .fillMaxSize()
            .clip(SlideOverShape)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}

/**
 * 覆盖式导航 + 预测性返回：父页常驻底层，子页作为浮层覆盖其上。
 * - 进入：子页从右缘滑入覆盖父页（父页左移 1/4 视差），340ms FastOutSlowIn；
 * - 程序化退出（子页返回按钮 → [childKey] 置 null）：子页向右滑出，300ms；
 * - 系统返回手势：[PredictiveBackHandler] 实时擦洗 progress，子页跟手右移、
 *   父页视差归位；过阈值松手 → [onRequestClose]（再收尾滑出），未过阈值 → 回弹。
 *
 * 子页壳（[SlideOverShell]）由本容器统一包裹；父页在子页可见期间通过
 * [LocalHostSuppressed] 抑制 ScreenScaffold 注册，保证任一时刻只有子页一个
 * 权威注册者（子页关闭后本地值翻回 false，父页 SideEffect 重跑并重新注册）。
 *
 * 位移仅用 graphicsLayer.translationX（与 AnimatedContent 的 slide 同机制，位于
 * 捕获层子树内已验证安全）；绝不用 shadow/elevation（HyperOS 3 玻璃层会栈溢出）。
 *
 * @param childKey null=仅父页；非 null=展示对应子页（其值同时用于 [child] 渲染）。
 * @param onRequestClose 预测性返回手势过阈值松手时回调（调用方据此把 childKey 置 null）。
 */
@Composable
fun <T : Any> PredictiveSlideOver(
    childKey: T?,
    onRequestClose: () -> Unit,
    parent: @Composable () -> Unit,
    child: @Composable (T) -> Unit,
) {
    val open = childKey != null
    // progress：0 = 子页完全覆盖父页；1 = 子页完全滑出右缘。
    // 首帧即按 open 定位：进程重建/配置变更时子页处于打开态则直接就位（0f），不重放入场动画
    val progress = remember { Animatable(if (open) 0f else 1f) }
    // 记住最后一次非空 key：退出动画/手势期间子页内容需继续渲染
    var lastKey by remember { mutableStateOf(childKey) }
    if (childKey != null) lastKey = childKey
    // 子页是否仍在组合中（进入即 true，退出动画/手势彻底结束才 false）
    var childVisible by remember { mutableStateOf(open) }

    LaunchedEffect(open) {
        if (open) {
            childVisible = true
            progress.animateTo(0f, tween(340, easing = FastOutSlowInEasing))
        } else {
            if (progress.value < 0.99f) {
                progress.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            } else {
                progress.snapTo(1f)
            }
            childVisible = false
        }
    }

    PredictiveBackHandler(enabled = open) { flow ->
        try {
            flow.collect { event -> progress.snapTo(event.progress) }
            // 手势过阈值松手：交调用方置 childKey=null，上面的 LaunchedEffect 收尾滑出
            onRequestClose()
        } catch (e: CancellationException) {
            // 手势取消：onBack 协程此刻已被取消，直接调 suspend 的 animateTo 会被立即中断
            // （停在拖拽末值 → 子页左缘留缝隙露出父页）。用 NonCancellable 上下文跑完回弹，
            // 确保子页重新铺满父页。
            withContext(NonCancellable) {
                progress.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 父层：常驻；子页可见期间抑制其 host 注册并左移 1/4 视差
        CompositionLocalProvider(LocalHostSuppressed provides childVisible) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    translationX = -(1f - progress.value) * size.width / 4f
                },
            ) { parent() }
        }
        // 子页浮层：跟手/动画右移
        val key = lastKey
        if (childVisible && key != null) {
            Box(
                Modifier.fillMaxSize().zIndex(2f).graphicsLayer {
                    translationX = progress.value * size.width
                },
            ) {
                SlideOverShell { child(key) }
            }
        }
    }
}
