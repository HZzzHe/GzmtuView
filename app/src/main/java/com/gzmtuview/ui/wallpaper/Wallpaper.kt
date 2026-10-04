package com.gzmtuview.ui.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.gzmtuview.GmuApplication
import com.gzmtuview.data.pref.SettingsStore
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 自定义壁纸（Beta）运行时通道。
 *
 * 与 [com.gzmtuview.ui.glass.Haptics] 同构：启动读一次偏好进状态、设置页写穿，
 * 绘制侧只读状态不查盘——所以壁纸改动立即生效，不需要重启。
 *
 * 图片必须落地成 `filesDir` 下的固定文件：系统相册选择器（Photo Picker）**不给持久化
 * URI 授权**，选完离开进程就失效，只有复制成本地文件才能下次启动继续用。
 */
object Wallpaper {

    /** 落地长边上限：再高对「全屏 + 压暗遮罩」的观感无收益，却把解码位图按比例推大。 */
    private const val MAX_EDGE = 1920
    private const val JPEG_QUALITY = 85
    private const val FILE_NAME = "wallpaper.jpg"
    private const val SAVE_DEBOUNCE_MS = 250L

    /** 取色采样边长（逐像素求均值只在 16×16 上做）。 */
    private const val SAMPLE_EDGE = 16

    /** 条带行数：末 4 行 ≈ 壁纸下部 25%，覆盖底栏 + 步进器所在区域。 */
    private const val BAND_ROWS = 4

    /** 亮背板阈值：亮度高于它走「浅底深墨」，否则「深底亮墨」。 */
    private const val LIGHT_BACKDROP_LUM = 0.5f

    /** 浅底用的深墨 / 深底用的亮墨（[adaptiveInk] 与底部 chrome 共用同一组）。 */
    private val InkOnLight = Color(0xFF14181D)
    private val InkOnDark = Color(0xFFF4F6F8)

    val enabled = mutableStateOf(false)

    /** true = 全局背板（所有页面与玻璃件统一折射壁纸）；false = 仅课表页。 */
    val global = mutableStateOf(true)

    /** 黑色压暗遮罩的不透明度 0..[SettingsStore.WALLPAPER_DIM_MAX]（明暗两色下都只压暗）。 */
    val dim = mutableStateOf(SettingsStore.WALLPAPER_DIM_DEFAULT)

    /** 壁纸高斯模糊半径（dp），0 = 不糊。 */
    val blur = mutableStateOf(SettingsStore.WALLPAPER_BLUR_DEFAULT)

    val bitmap = mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)

    /** 壁纸平均色（解码/导入时算一次）：驱动直接画在背板上的文字自适应取色。 */
    val avgColor = mutableStateOf(Color.Unspecified)

    /**
     * 壁纸**屏幕下缘条带**均色（与 [avgColor] 同一次采样算出）：驱动底栏/步进器的深浅样式。
     * 整图均色会把顶部天空、亮部一起平均掉，判不出底栏背后到底是亮是暗。
     */
    val bottomColor = mutableStateOf(Color.Unspecified)

    /** 壁纸是否真的在画：开关开 + 位图解码成功（缺一个都不能让页面把底色让出来）。 */
    val shown: Boolean get() = enabled.value && bitmap.value != null

    /** 是否作为全局背板——true 时各页面根底与顶栏底色让位给壁纸。 */
    val shownGlobally: Boolean get() = shown && global.value

    fun file(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    /**
     * 整屏铺底（页面根 / 顶栏底色）在全局壁纸期间让位给背板壁纸，其余情况原样返回。
     * 只用于「整屏铺底」：磨砂 tint、分组卡片底色必须继续用实色，否则盖不住滚动内容。
     */
    fun pageBase(base: Color): Color = if (shownGlobally) Color.Transparent else base

    /**
     * 仅深色模式让位给壁纸：浅色保持不透明 [base]，深色才透明露出背板。
     * 设置/工具族页面用——浅色下若让位，压暗后的深色壁纸会从近白卡片/磨砂间透出，
     * 既与浅色观感冲突又破坏文字对比；深色下让位则与暗色主题一致。
     */
    @Composable
    fun pageBaseDarkYield(base: Color): Color =
        if (isSystemInDarkTheme()) pageBase(base) else base

    /**
     * 壁纸有效背板色：均色被黑色压暗遮罩（[dim]）压暗后的等效色，即
     * `lerp(均色, 黑, dim)`（与捕获层「壁纸×(1-dim) + 黑×dim」一致）。
     * 无壁纸时返回 [fallback]。用于磨砂 tint 等需要「与壁纸融合」的实色场景。
     */
    @Composable
    fun effectiveBackdropColor(fallback: Color): Color {
        val a = avgColor.value
        if (!shown || a == Color.Unspecified) return fallback
        return lerp(a, Color.Black, dim.value)
    }

    /**
     * 位图取色：缩到 16×16 再逐像素求均值（O(256)，解码/导入时各算一次），
     * 一次同时产出整图均色 [avgColor] 与下缘条带均色 [bottomColor]（末 [BAND_ROWS] 行）。
     * 不直接用原图逐像素——大图解码后逐点 getPixel 会卡 IO 线程。
     */
    private fun sampleAverages(bmp: Bitmap) {
        val small = runCatching {
            Bitmap.createScaledBitmap(bmp, SAMPLE_EDGE, SAMPLE_EDGE, true)
        }.getOrNull()
        if (small == null) {
            avgColor.value = Color.Unspecified
            bottomColor.value = Color.Unspecified
            return
        }
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        var br = 0L; var bg = 0L; var bb = 0L; var bn = 0
        val bandStart = small.height - BAND_ROWS
        for (y in 0 until small.height) {
            val inBand = y >= bandStart
            for (x in 0 until small.width) {
                val p = small.getPixel(x, y)
                val pr = (p shr 16) and 0xFF
                val pg = (p shr 8) and 0xFF
                val pb = p and 0xFF
                r += pr; g += pg; b += pb; n++
                if (inBand) {
                    br += pr; bg += pg; bb += pb; bn++
                }
            }
        }
        if (small !== bmp) small.recycle()
        avgColor.value = if (n == 0) {
            Color.Unspecified
        } else {
            Color(r.toFloat() / n / 255f, g.toFloat() / n / 255f, b.toFloat() / n / 255f)
        }
        bottomColor.value = if (bn == 0) {
            Color.Unspecified
        } else {
            Color(br.toFloat() / bn / 255f, bg.toFloat() / bn / 255f, bb.toFloat() / bn / 255f)
        }
    }

    /**
     * 有效背板相对亮度 0..1：壁纸均色被黑色压暗遮罩（[dim]）压暗后的亮度。
     * 捕获层叠放为「壁纸像素 ×(1-dim) + 黑 ×dim」，故亮度按 (1-dim) 线性衰减。
     * 无壁纸（未显示或均色缺失）返回 -1，调用方据此回落到主题色。
     */
    fun backdropLuminance(): Float {
        val a = avgColor.value
        if (!shown || a == Color.Unspecified) return -1f
        return a.luminance() * (1f - dim.value)
    }

    /**
     * 屏幕下缘条带的有效亮度（算法同 [backdropLuminance]，只是换成 [bottomColor]）。
     * 无壁纸/条带缺失返回 -1。
     */
    fun bottomLuminance(): Float {
        val a = bottomColor.value
        if (!shown || a == Color.Unspecified) return -1f
        return a.luminance() * (1f - dim.value)
    }

    /**
     * 直接画在页面背板（壁纸）上的前景自适应取色：背板亮 → 深墨、背板暗 → 亮墨。
     * 无壁纸时原样返回 [fallback]（主题色），不改变既有色板。
     * 仅用于「文字/图标直接落在让位给壁纸的页面底」处——卡片、课程块等有自己的不透明底，
     * 不该走这里。[variant] 为次要文字：在自适应墨色上再降一档不透明度。
     */
    @Composable
    fun adaptiveInk(fallback: Color, variant: Boolean = false): Color {
        val lum = backdropLuminance()
        if (lum < 0f) return fallback
        val base = if (lum > LIGHT_BACKDROP_LUM) InkOnLight else InkOnDark
        return if (variant) base.copy(alpha = 0.78f) else base
    }

    /**
     * 屏幕下缘的浮层 chrome（玻璃标签栏、周次步进器）该走深色还是浅色样式。
     *
     * 这两处玻璃折射的是【壁纸下部】，跟整图均色常常相反（天空亮、地面暗），
     * 所以判定只能用 [bottomLuminance]；否则浅色主题在暗地面壁纸上会画出
     * 「近白底 + 深墨」，控件边缘和图标一起糊掉——用户反馈的「辨识度差」。
     * [backdropIsWallpaper] 必须如实传：底栏背后画的是页面实底时（未开全局背板且不在
     * 课表页）壁纸不参与，一切照系统主题。
     */
    @Composable
    fun prefersDarkChrome(backdropIsWallpaper: Boolean = shown): Boolean {
        val lum = if (backdropIsWallpaper) bottomLuminance() else -1f
        return if (lum < 0f) isSystemInDarkTheme() else lum <= LIGHT_BACKDROP_LUM
    }

    /**
     * 底栏 chrome 的前景墨色：壁纸决定时给不透明实色（亮墨/深墨），
     * 否则原样返回 [fallback]（主题色）。与 [prefersDarkChrome] 同源，两者不会打架。
     */
    @Composable
    fun chromeInk(fallback: Color, backdropIsWallpaper: Boolean = shown): Color {
        if (bottomLuminance() < 0f || !backdropIsWallpaper) return fallback
        return if (prefersDarkChrome(true)) InkOnDark else InkOnLight
    }

    /**
     * 底栏 chrome 的容器底色（调用方自己 `.copy(alpha=…)` 后当磨砂 wash）：
     * 与 [chromeInk] 反向配对——浅底配深墨、深底配亮墨。
     * 壁纸不决定时返回 [fallback]（主题 surface），保持无壁纸时的既有观感。
     */
    @Composable
    fun chromeContainer(fallback: Color, backdropIsWallpaper: Boolean = shown): Color {
        if (bottomLuminance() < 0f || !backdropIsWallpaper) return fallback
        return if (prefersDarkChrome(true)) Color(0xFF121212) else Color(0xFFFAFAFA)
    }

    /** 启动挂载（Application.onCreate）：偏好同步读，位图解码丢给 IO 协程。 */
    fun attach(ctx: Context) {
        val store = GmuApplication.deps(ctx).store
        enabled.value = store.loadWallpaperEnabled()
        global.value = store.loadWallpaperGlobal()
        dim.value = store.loadWallpaperDim()
        blur.value = store.loadWallpaperBlur()
        if (!enabled.value) return
        fireAndForgetDecode(ctx)
    }

    private fun fireAndForgetDecode(ctx: Context) {
        // 与 MainActivity 冷启动预热同一手法：没有应用级 scope，解码失败只是这次不显示壁纸
        CoroutineScope(Dispatchers.IO).launch { decodeInPlace(ctx) }
    }

    private suspend fun decodeInPlace(ctx: Context) = withContext(Dispatchers.IO) {
        val f = file(ctx)
        if (!f.exists()) {
            // 文件被清理（如「清除数据」外的手动删档）：开关跟着落回，避免设置页显示开着却无图
            enabled.value = false
            bitmap.value = null
            avgColor.value = Color.Unspecified
            bottomColor.value = Color.Unspecified
            return@withContext
        }
        val bmp = runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
        if (bmp == null) {
            enabled.value = false
            bitmap.value = null
            avgColor.value = Color.Unspecified
            bottomColor.value = Color.Unspecified
        } else {
            sampleAverages(bmp)
            bitmap.value = bmp.asImageBitmap()
        }
    }

    /**
     * 把选中的图片复制进 filesDir（降采样 + 重编码），成功后立即刷新显示。
     * 返回 null 表示成功，否则是可直接 Toast 的原因。
     */
    suspend fun import(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return@withContext "所选图片无法解析"

        var sample = 1
        while (longEdge / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull() ?: return@withContext "所选图片解码失败"

        val sized = if (max(decoded.width, decoded.height) > MAX_EDGE) {
            val k = MAX_EDGE.toFloat() / max(decoded.width, decoded.height)
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * k).roundToInt().coerceAtLeast(1),
                (decoded.height * k).roundToInt().coerceAtLeast(1),
                true,
            ).also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }

        val tmp = File(ctx.filesDir, "$FILE_NAME.tmp")
        val compressed = runCatching {
            tmp.outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        }.getOrDefault(false)
        if (!compressed) {
            tmp.delete()
            return@withContext "壁纸保存失败"
        }
        file(ctx).delete()
        if (!tmp.renameTo(file(ctx))) {
            tmp.delete()
            return@withContext "壁纸保存失败"
        }
        sampleAverages(sized)
        bitmap.value = sized.asImageBitmap()
        null
    }

    /** 开关/强度/范围写穿：设置页调用，UI 侧立即重组。 */
    fun setEnabled(ctx: Context, value: Boolean) {
        GmuApplication.deps(ctx).store.saveWallpaperEnabled(value)
        enabled.value = value
        if (value && bitmap.value == null) fireAndForgetDecode(ctx)
    }

    fun setGlobal(ctx: Context, value: Boolean) {
        GmuApplication.deps(ctx).store.saveWallpaperGlobal(value)
        global.value = value
    }

    private val saveScope = CoroutineScope(Dispatchers.IO)
    private var dimSaveJob: Job? = null
    private var blurSaveJob: Job? = null

    fun setDim(ctx: Context, value: Float) {
        val v = value.coerceIn(0f, SettingsStore.WALLPAPER_DIM_MAX)
        dim.value = v
        val store = GmuApplication.deps(ctx).store
        dimSaveJob = debouncedSave(dimSaveJob) { store.saveWallpaperDim(v) }
    }

    fun setBlur(ctx: Context, value: Float) {
        val v = value.coerceIn(0f, SettingsStore.WALLPAPER_BLUR_MAX)
        blur.value = v
        val store = GmuApplication.deps(ctx).store
        blurSaveJob = debouncedSave(blurSaveJob) { store.saveWallpaperBlur(v) }
    }

    /**
     * 滑杆每帧回调：状态先改让背板立即跟手，写盘防抖 250ms
     * ——SettingsStore 是 runBlocking 同步写，逐帧落盘会拖垮拖动。
     */
    private fun debouncedSave(previous: Job?, persist: () -> Unit): Job {
        previous?.cancel()
        return saveScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persist()
        }
    }

    /** 移除壁纸文件并关掉开关（设置页「移除壁纸」）。 */
    suspend fun clear(ctx: Context) = withContext(Dispatchers.IO) {
        dimSaveJob?.cancel()
        blurSaveJob?.cancel()
        runCatching { file(ctx).delete() }
        bitmap.value = null
        avgColor.value = Color.Unspecified
        bottomColor.value = Color.Unspecified
        setEnabled(ctx, false)
    }
}
