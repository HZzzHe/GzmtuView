package com.gzmtuview.ui.feature

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gzmtuview.GmuApplication
import com.gzmtuview.data.pref.SettingsStore
import com.gzmtuview.ui.ScreenScaffold
import com.gzmtuview.ui.glass.GlassDialog
import com.gzmtuview.ui.glass.GlassIconButton
import com.gzmtuview.ui.glass.GlassTopBar
import com.gzmtuview.ui.glass.Haptics
import com.gzmtuview.ui.glass.LiquidGlassSwitch
import com.gzmtuview.ui.glass.LiquidSlider
import com.gzmtuview.ui.settings.GroupDivider
import com.gzmtuview.ui.settings.SectionLabel
import com.gzmtuview.ui.settings.SettingsGroup
import com.gzmtuview.ui.settings.SettingsRow
import com.gzmtuview.ui.settings.settingsCardBase
import com.gzmtuview.ui.settings.settingsPageBaseResolved
import com.gzmtuview.ui.wallpaper.Wallpaper
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 「个性化（Beta）」二级页：自定义壁纸（开关 / 选图 / 压暗强度 / 作用范围 / 移除）。
 * 与「功能」页同一设计语言；改动全部经 [Wallpaper] 写穿状态，立即生效不需重启。
 */
@Composable
fun PersonalizeScreen(
    app: GmuApplication,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var global by remember { mutableStateOf(true) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enabled = app.store.loadWallpaperEnabled()
        global = app.store.loadWallpaperGlobal()
        loaded = true
    }

    val picture = Wallpaper.bitmap.value
    // 相册选择器（Photo Picker）：只给一次性读授权、不给持久化权限，
    // 所以选中后必须立刻把字节复制进 filesDir，否则下次启动就没图可用了
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val error = Wallpaper.import(ctx, uri)
                if (error != null) {
                    Toast.makeText(ctx, error, Toast.LENGTH_SHORT).show()
                } else if (!Wallpaper.enabled.value) {
                    // 从「开关」进来且首次选图成功：这一刻才真正打开壁纸
                    Wallpaper.setEnabled(ctx, true)
                    enabled = true
                }
            }
        }
    }
    fun openPicker() = picker.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
    )

    // 开关折射所在分组卡片纯色（近白/更亮一档），避免捕获层自采样；
    // 滑块同理会落在卡片上，折射卡片色而不是它身下的整屏捕获层。
    // rememberUpdatedState：canvas backdrop 的绘制 lambda 会被 remember，
    // 直接捕获卡片色会让它停在外表首次组合的那一档（切深浅色后不跟着变）
    val cardColor by rememberUpdatedState(settingsCardBase())
    val sliderBackdrop = rememberCanvasBackdrop { drawRect(cardColor) }

    ScreenScaffold(
        hideBottomBar = true,
        overlay = {
            GlassDialog(
                visible = showRemoveConfirm,
                onDismissRequest = { showRemoveConfirm = false },
                title = "移除壁纸",
                textString = "将删除本机保存的壁纸图片并关闭该功能，此操作不可撤销。",
                confirmText = "移除",
                onConfirm = {
                    showRemoveConfirm = false
                    scope.launch {
                        Wallpaper.clear(ctx)
                        enabled = false
                    }
                },
                dismissText = "取消",
                onDismiss = { showRemoveConfirm = false },
            )
        },
    ) {
        val pageBase = settingsPageBaseResolved()
        val scheme = MaterialTheme.colorScheme
        Column(Modifier.fillMaxSize().background(pageBase)) {
            GlassTopBar(
                title = "个性化（Beta）",
                backgroundColor = pageBase,
                leading = {
                    GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onClick = onBack)
                },
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            ) {
                SectionLabel("壁纸")
                SettingsGroup {
                    SettingsRow(
                        title = "自定义壁纸",
                        subtitle = if (picture == null) {
                            "开启前需要先从相册选一张图片"
                        } else {
                            "已保存到本机 · ${picture.width}×${picture.height}px"
                        },
                        showChevron = false,
                        trailing = {
                            if (loaded) {
                                LiquidGlassSwitch(
                                    checked = enabled,
                                    onCheckedChange = { next ->
                                        enabled = next
                                        if (next && picture == null) {
                                            // 无图可开：先去选图，选成功再由选择器回调翻开关
                                            openPicker()
                                        } else {
                                            Wallpaper.setEnabled(ctx, next)
                                        }
                                    },
                                    backdropColor = cardColor,
                                )
                            }
                        },
                    )
                    GroupDivider()
                    SettingsRow(
                        title = "选择壁纸",
                        subtitle = "从相册选一张，只存本机私有目录（不上传、不改相册）",
                        onClick = { openPicker() },
                    )
                    GroupDivider()
                    SliderRow(
                        title = "压暗强度",
                        subtitle = "越高越好读，越低越看得清壁纸",
                        value = { Wallpaper.dim.value },
                        valueRange = 0f..SettingsStore.WALLPAPER_DIM_MAX,
                        backdrop = sliderBackdrop,
                        onChanged = { Wallpaper.setDim(ctx, it) },
                    )
                    GroupDivider()
                    SliderRow(
                        title = "模糊程度",
                        subtitle = "虚化壁纸，减少杂色干扰",
                        value = { Wallpaper.blur.value },
                        valueRange = 0f..SettingsStore.WALLPAPER_BLUR_MAX,
                        backdrop = sliderBackdrop,
                        onChanged = { Wallpaper.setBlur(ctx, it) },
                    )
                    GroupDivider()
                    SettingsRow(
                        title = "在所有页面显示",
                        subtitle = if (global) "所有页面都以壁纸为背板" else "仅课表页透出壁纸",
                        showChevron = false,
                        trailing = {
                            if (loaded) {
                                LiquidGlassSwitch(
                                    checked = global,
                                    onCheckedChange = { next ->
                                        global = next
                                        Wallpaper.setGlobal(ctx, next)
                                    },
                                    backdropColor = cardColor,
                                )
                            }
                        },
                    )
                    if (picture != null) {
                        GroupDivider()
                        SettingsRow(
                            title = "移除壁纸",
                            titleColor = scheme.error,
                            showChevron = false,
                            onClick = { showRemoveConfirm = true },
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))
                SectionLabel("说明")
                // 提醒单独成段并用错误色：可读性是壁纸功能的头号副作用，不能混在长段落里
                Text(
                    "提醒：壁纸会从文字下方透出，画面复杂或偏亮时页面会变难读。" +
                        "若觉得看不清，优先调高「压暗强度」，必要时关闭壁纸。",
                    fontSize = 11.5.sp,
                    color = scheme.error,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "壁纸铺在整屏背板层，玻璃底栏与顶栏按钮会折射它，课程卡片和文字仍压在实色上。\n" +
                        "压暗是盖一层黑色遮罩（浅深色都只压暗，最高 85%）；模糊需 Android 12 及以上。" +
                        "桌面小部件由系统渲染，不跟随壁纸。",
                    fontSize = 11.5.sp,
                    color = scheme.outline,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/**
 * 滑杆行：标题 + 说明 + 官方 LiquidSlider（拖动即时预览，写盘在 [Wallpaper] 内防抖）。
 *
 * ★ `value` 必须是**读活状态的 lambda**：`LiquidSlider` 里的 `DampedDragAnimation` 与
 * `snapshotFlow { value() }` 都只认首次组合传入的那个 lambda（remember(animationScope)，
 * 换 key 不了）。传组合期快照 Float 的话，拖动一停就被旧值 `updateValue` 拉回原位，
 * 表现就是「只能点按、拖不动」。
 */
@Composable
private fun SliderRow(
    title: String,
    subtitle: String,
    value: () -> Float,
    valueRange: ClosedFloatingPointRange<Float>,
    backdrop: Backdrop,
    onChanged: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val span = valueRange.endInclusive - valueRange.start
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp)) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            fontSize = 12.5.sp,
            color = scheme.onSurfaceVariant,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(12.dp))
        LiquidSlider(
            value = value,
            onValueChange = { v ->
                // 每跨 5% 量程振一次，连续拖动不会每像素都响
                val steps = 20f / span
                if ((value() * steps).roundToInt() != (v * steps).roundToInt()) Haptics.tick()
                onChanged(v.coerceIn(valueRange))
            },
            valueRange = valueRange,
            visibilityThreshold = 0.01f,
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
