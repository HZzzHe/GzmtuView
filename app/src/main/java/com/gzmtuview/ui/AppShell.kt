package com.gzmtuview.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gzmtuview.ui.glass.Haptics
import com.gzmtuview.ui.wallpaper.Wallpaper
import com.gzmtuview.ui.glass.LiquidBottomTab
import com.gzmtuview.ui.glass.LiquidBottomTabs
import com.gzmtuview.ui.glass.LocalAppBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** 导航项定义（底栏共用；选中/未选中同一图标）。 */
data class NavDest(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/** 玻璃底栏跨页面记录的上一次选中索引（滑块切换动画的起点）。 */
val glassTabLastIndex = androidx.compose.runtime.mutableIntStateOf(0)

/** 底栏导航项：课表 / 考试 / 工具箱（设置仅从"更多"菜单进入，不再占底栏位）。 */
val NavDests: List<NavDest> = listOf(
    NavDest("课表", Icons.Filled.ViewTimeline, Icons.Filled.ViewTimeline),
    NavDest("考试", Icons.Filled.Assignment, Icons.Filled.Assignment),
    NavDest("工具箱", Icons.Outlined.Work, Icons.Outlined.Work),
)

/**
 * 页面 → 外壳的注册桥：页面通过 [ScreenScaffold] 把自己的顶栏/浮层/底栏显隐
 * 注册到这个单例上；玻璃外壳（AppScreen）在 MainTabs 层单实例持有，
 * 因此底栏/顶栏浮层不随页面切换重建——切页动画与滑块动效互不干扰。
 */
class PageHost {
    var overlay: (@Composable () -> Unit)? by mutableStateOf(null)
    /**
     * 底栏同层的浮层槽（如周次步进器）：与 [overlay] 一样绘制在捕获层【之外】，
     * 因此内部玻璃件可采样真实 backdrop 折射壁纸——而页面 body 内的玻璃只能折射纯色。
     */
    var bottomOverlay: (@Composable () -> Unit)? by mutableStateOf(null)
    var hideBottomBar: Boolean by mutableStateOf(false)
    /** 玻璃对话框显示期间临时隐藏底栏（压暗层不覆盖玻璃浮层，直接退场更干净） */
    var dialogActive: Boolean by mutableStateOf(false)
}

val LocalPageHost = staticCompositionLocalOf<PageHost> { error("PageHost not provided") }

/**
 * 为 true 时 [ScreenScaffold] 不向 [PageHost] 注册（overlay/hideBottomBar）。
 * 用于「父页常驻 + 子页浮层」的预测性返回容器：子页可见期间抑制父页注册，
 * 保证任一时刻只有子页这一个权威注册者；子页关闭后本地值翻回 false，
 * 父页 SideEffect 随之重跑并重新注册。
 */
val LocalHostSuppressed = compositionLocalOf<Boolean> { false }

/**
 * 页面级外壳：注册顶栏/浮层/底栏显隐到 [PageHost]，然后直接绘制页面内容。
 * 玻璃捕获层、底栏、顶栏浮层由上层 [AppScreen] 单实例提供。
 */
@Composable
fun ScreenScaffold(
    overlay: @Composable () -> Unit = {},
    bottomOverlay: @Composable () -> Unit = {},
    hideBottomBar: Boolean = false,
    content: @Composable () -> Unit,
) {
    val host = LocalPageHost.current
    val suppressed = LocalHostSuppressed.current
    SideEffect {
        if (!suppressed) {
            host.overlay = overlay
            host.bottomOverlay = bottomOverlay
            host.hideBottomBar = hideBottomBar
        }
    }
    content()
}

/**
 * 玻璃外壳（MainTabs 层单实例）：捕获层 + 顶栏浮层 + 浮层槽 + 玻璃底栏。
 * [body] 为页面切换动画与页面内容（内含各页 ScreenScaffold）。
 * 注意：drawBackdrop 的玻璃组件绝不能位于捕获层(layerBackdrop 标记的子树)内部，
 * 否则渲染树自引用 → RenderThread 栈溢出 SIGSEGV。
 */
@Composable
fun AppScreen(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    items: List<NavDest> = NavDests,
    body: @Composable () -> Unit,
) {
    val host = remember { PageHost() }
    val realBackdrop = rememberLayerBackdrop()
    // 二级页（hideBottomBar）也有玻璃对话框：必须恒用真实捕获层，
    // 空 backdrop 会让对话框玻璃全透明（退出登录对话框渲染异常的根因）
    val backdrop = realBackdrop

    CompositionLocalProvider(
        LocalPageHost provides host,
        LocalAppBackdrop provides backdrop,
    ) {
        Box(Modifier.fillMaxSize()) {
            // 此刻屏幕下缘画的是不是壁纸：底栏与周次步进器的深浅样式跟着它。
            // 未开全局背板且不在课表页时底栏背后是页面实底，必须回落到系统主题。
            val backdropIsWallpaper =
                Wallpaper.shown && (Wallpaper.global.value || selectedIndex == 0)
            val chromeInk =
                Wallpaper.chromeInk(MaterialTheme.colorScheme.onSurface, backdropIsWallpaper)
            // 捕获层：页面内容。白底必须作为捕获层的【子节点】绘制——挂在
            // layerBackdrop 同一节点之前的 background 不会被记录，
            // 底栏区域透明像素会被亮度映射判为"黑"致玻璃发灰
            Box(Modifier.fillMaxSize().layerBackdrop(realBackdrop)) {
                val scheme = MaterialTheme.colorScheme
                val wp = if (backdropIsWallpaper) Wallpaper.bitmap.value else null
                // 捕获层必须是【不透明底 → 壁纸图 → 色彩级压暗遮罩】三层正常绘制：
                // 遮罩不能用 Modifier.alpha/离屏层（HyperOS 3 会丢），也不能留透明像素
                Box(Modifier.fillMaxSize().background(scheme.surface))
                if (wp != null) {
                    val radius = Wallpaper.blur.value
                    Image(
                        bitmap = wp,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // 必须 Unbounded：默认的 Rectangle 会先按矩形裁剪再糊，四边会糊成
                        // 透明像素，捕获层一旦记下透明像素，底栏玻璃就发灰
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (radius > 0.5f) {
                                    Modifier.blur(radius.dp, BlurredEdgeTreatment.Unbounded)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    // 压暗遮罩恒为黑色：浅色下拿 surface 会变成「压白」，壁纸直接洗成白雾
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = Wallpaper.dim.value)))
                }
    // 顶栏已合并进页面 body（GlassTopBar 页内常驻），body 恒全屏：
                // 消除"独立顶部区域"，push/切页动画时顶栏跟随页面运动
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopStart,
                ) {
                    // 页面切换动画在此层内：底栏/顶栏浮层不受动画影响
                    body()
                }
            }
            // 底栏同层浮层槽（周次步进器等）：在捕获层之外，内部玻璃可折射真实壁纸。
            // 必须排在 overlay 槽【之前】——对话框/菜单的压暗层在 overlay 里，
            // 这样打开对话框时压暗层能盖住步进器（与页面 body 被压暗一致）。
            Box(Modifier.fillMaxSize()) { host.bottomOverlay?.invoke() }
            // 通用玻璃浮层槽（菜单/对话框等，主窗口坐标）
            Box(Modifier.fillMaxSize()) { host.overlay?.invoke() }
            // 玻璃底栏浮层（单实例，不随页面切换重建）。
            // 手机屏宽不足 600dp 时加宽到 62%、贴底 6dp；平板 45%/10dp。
            // glassTabLastIndex 记录上一次选中位；-1 = 无选中（设置页：滑块隐藏）
            androidx.compose.animation.AnimatedVisibility(
                // 对话框（dialogActive）期间底栏暂时退场：压暗层只盖住页面内容，
                // 玻璃浮层盖不住，直接退场比被压暗更干净
                visible = !host.hideBottomBar && !host.dialogActive,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)) +
                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(240)) { it / 3 },
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)) +
                    androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(240)) { it / 3 },
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                Box(Modifier.fillMaxSize()) {
                // 一比一对齐 Pronto 底栏（对照 img/ 四张双端截图）：
                // 绝对尺寸全设备统一、与屏宽解耦——Pronto 双端胶囊总宽实测相近
                // （手机 ≈268dp / 平板 ≈236dp），固定 dp 即可在任何屏幕比例下
                // 呈现一致设计，不再按 screenWidthDp 分档：
                // 每 tab 78dp（较 84 收窄使标签栏更紧凑）+ 8dp×0.81 内衬；
                // 高 0.81×64=52dp；icon 22dp（仅图标放大）；文字 11sp；边距 start 18 / bottom 16
                val hs = 0.81f
                val showLabel = true
                LiquidBottomTabs(
                    heightScale = hs,
                    darkChrome = Wallpaper.prefersDarkChrome(backdropIsWallpaper),
                    selectedTabIndex = { if (selectedIndex in items.indices) selectedIndex else -1 },
                    onTabSelected = { i ->
                        val c = i.coerceIn(items.indices)
                        glassTabLastIndex.intValue = c
                        onSelect(c)
                    },
                    animationInitialIndex = glassTabLastIndex.intValue.coerceIn(items.indices),
                    backdrop = backdrop,
                    tabsCount = items.size,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = 18.dp, bottom = 16.dp)
                        .width((items.size * 78 + 8f * hs).dp),
                ) {
                    items.forEachIndexed { i, n ->
                        LiquidBottomTab(onClick = {
                            // 仅真正换页才振动；点当前 tab 静默；
                            // 系统返回程控回课表不经过这里（天然不触发）
                            if (i != selectedIndex) Haptics.tap()
                            onSelect(i)
                        }) {
                            Icon(
                                n.icon,
                                null,
                                // 仅图标放大到 22dp（文字保持 11sp）
                                Modifier.size(22.dp),
                                tint = chromeInk,
                            )
                            if (showLabel) {
                                Text(
                                    n.label,
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    color = chromeInk,
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
