package com.gzmtuview.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gzmtuview.GmuApplication
import com.gzmtuview.data.net.ProjectRepo
import com.gzmtuview.data.net.UpdateService
import com.gzmtuview.ui.PredictiveSlideOver
import com.gzmtuview.ui.ScreenScaffold
import com.gzmtuview.ui.feature.AccountScreen
import com.gzmtuview.ui.feature.FeatureDisplayScreen
import com.gzmtuview.ui.feature.PersonalizeScreen
import com.gzmtuview.ui.glass.GlassDialog
import com.gzmtuview.ui.glass.GlassIconButton
import com.gzmtuview.ui.glass.GlassTopBar
import com.gzmtuview.ui.theme.LightPageBase
import com.gzmtuview.ui.wallpaper.Wallpaper
import kotlinx.coroutines.launch

/** 版本号（与原生工程发布版本一致）。 */
const val kAppVersion = "v0.1.0"

/**
 * 页面基色（对照第三方设置页截图）：浅色为中性灰、深色为深灰，
 * 分组卡片在其上以近白/更亮一档浮起，形成"灰底 + 白卡"的简洁层次。
 * 浅色用 [LightPageBase] 而不是 `surfaceContainer`：后者与卡片色只差 3%，
 * 高亮屏/平板上分组圆角完全看不出轮廓（2026-09-09 真机截图）。
 */
@Composable
internal fun settingsPageBase(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface
    else LightPageBase

/** 分组卡片基色：与 [settingsPageBase] 相反的一档，构成浮起感。 */
@Composable
internal fun settingsCardBase(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainer
    else MaterialTheme.colorScheme.surface

/**
 * 设置族页面根底色（含全局壁纸策略）：仅深色模式让位给壁纸，浅色模式恒为不透明
 * [LightPageBase]。浅色下若让位成透明，压暗后的深色壁纸会从近白卡片间透出，
 * 既与浅色观感冲突、又让直接画在页面底上的小节标题/说明文字失去对比（用户反馈）。
 */
@Composable
internal fun settingsPageBaseResolved(): Color =
    Wallpaper.pageBaseDarkYield(settingsPageBase())

/**
 * 设置页：账号 / 功能 入口 + 关于信息。
 * 简洁分组列表（对照第三方设置页截图）：灰底 + 近白圆角分组 + 灰色小节标题 +
 * 纯文字行 + 发丝分隔线 + 浅灰 chevron；顶栏融入页面基色。
 */
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onNavigate: (Int) -> Unit = {},
) {
    val ctx = LocalContext.current
    val app = GmuApplication.deps(ctx)
    var sub by remember { mutableStateOf<String?>(null) }
    var showProjectHomeConfirm by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    // 未配置 ProjectRepo.SLUG 时为 null：此时「项目主页」一行不出现
    val homeUrl = ProjectRepo.homeUrl

    // 二级子页（账号/功能）：覆盖式父页常驻 + 子页浮层，支持跟手预测性返回。
    // 主页「返回」回课表主 tab 交由 MainTabs 外层预测性返回容器接管。
    PredictiveSlideOver(
        childKey = sub,
        onRequestClose = { sub = null },
        parent = {
            ScreenScaffold(
                hideBottomBar = true,
                // 玻璃对话框必须挂主窗口 overlay 槽（Popup 取色错位、捕获层内自采样崩溃）
                overlay = {
                    GlassDialog(
                        visible = showProjectHomeConfirm,
                        onDismissRequest = { showProjectHomeConfirm = false },
                        title = "项目主页",
                        textString = "是否打开浏览器访问 ${homeUrl.orEmpty().removePrefix("https://")} ？",
                        confirmText = "打开",
                        onConfirm = {
                            showProjectHomeConfirm = false
                            runCatching {
                                ctx.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(homeUrl.orEmpty()),
                                    ),
                                )
                            }.onFailure {
                                Toast.makeText(
                                    ctx,
                                    "无法打开浏览器，请手动访问 ${homeUrl.orEmpty().removePrefix("https://")}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        dismissText = "取消",
                        onDismiss = { showProjectHomeConfirm = false },
                    )
                    GlassDialog(
                        visible = showLicense,
                        onDismissRequest = { showLicense = false },
                        title = "开源许可",
                        textString = "本应用的液态玻璃效果基于 AndroidLiquidGlass" +
                            "（作者 Kyant，Apache License 2.0）。\n\n" +
                            "依赖：io.github.kyant0:backdrop:2.0.1、" +
                            "io.github.kyant0:shapes:1.2.1\n" +
                            "仓库：github.com/Kyant0/AndroidLiquidGlass\n\n" +
                            "ui/glass 下的 LiquidSlider、LiquidBottomTab(s)、" +
                            "LiquidGlassSwitch（上游 LiquidToggle）、" +
                            "DampedDragAnimation、DragGestureInspector、Coroutines、" +
                            "InteractiveHighlight 由该库 catalog 移植并按本项目需要修改，" +
                            "版权归 Kyant 所有。\n\n" +
                            "完整许可文本见仓库内 LICENSE、NOTICE 与 third_party/。\n\n" +
                            "其余依赖（Jetpack Compose、Material 3、OkHttp、DataStore 等）" +
                            "各自遵循其开源许可。",
                        confirmText = "知道了",
                        onConfirm = { showLicense = false },
                    )
                    GlassDialog(
                        visible = updateMsg != null,
                        onDismissRequest = { updateMsg = null },
                        title = "检查更新",
                        textString = updateMsg,
                        confirmText = if (updateUrl != null) "去下载" else "知道了",
                        onConfirm = {
                            val url = updateUrl
                            updateMsg = null
                            if (url != null) {
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }.onFailure {
                                    Toast.makeText(ctx, "无法打开浏览器，请手动访问 $url", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                },
            ) {
                val scheme = MaterialTheme.colorScheme
                val pageBase = settingsPageBaseResolved()
                // push 动画中主页随壳整体移动：顶栏改页内自绘并融入页面基色
                Column(Modifier.fillMaxSize().background(pageBase)) {
                    GlassTopBar(
                        title = "设置",
                        backgroundColor = pageBase,
                        leading = {
                            // 覆盖式进入的页面对应"返回"= 回课表主 tab（与系统返回键同语义）
                            GlassIconButton(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "返回",
                                onClick = { onNavigate(0) },
                            )
                        },
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
                    ) {
                        SectionLabel("通用")
                        SettingsGroup {
                            SettingsRow(
                                title = "账号",
                                subtitle = "管理账号信息",
                                onClick = { sub = "account" },
                            )
                            GroupDivider()
                            SettingsRow(
                                title = "功能",
                                subtitle = "管理 APP 的部分功能与 Beta 特性",
                                onClick = { sub = "feature" },
                            )
                            GroupDivider()
                            SettingsRow(
                                title = "个性化（Beta）",
                                subtitle = "自定义壁纸与玻璃背板",
                                onClick = { sub = "personalization" },
                            )
                        }

                        Spacer(Modifier.height(28.dp))
                        SectionLabel("关于")
                        SettingsGroup {
                            SettingsRow(
                                title = "GZMTU View",
                                subtitle = kAppVersion,
                                showChevron = false,
                            )
                            GroupDivider()
                            if (homeUrl != null) {
                                SettingsRow(
                                    title = "项目主页",
                                    subtitle = homeUrl.removePrefix("https://"),
                                    onClick = { showProjectHomeConfirm = true },
                                )
                                GroupDivider()
                            }
                            SettingsRow(
                                title = "开源许可",
                                subtitle = "液态玻璃等第三方组件致谢",
                                onClick = { showLicense = true },
                            )
                            GroupDivider()
                            SettingsRow(
                                title = "检查更新",
                                subtitle = if (checkingUpdate) "检查中…" else "当前 $kAppVersion",
                                onClick = {
                                    if (!checkingUpdate) {
                                        checkingUpdate = true
                                        scope.launch {
                                            val info = UpdateService.fetchLatest()
                                            checkingUpdate = false
                                            val current = runCatching {
                                                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
                                            }.getOrNull() ?: kAppVersion
                                            if (info == null || info.tagName.isEmpty()) {
                                                updateUrl = null
                                                updateMsg = "暂无法获取更新信息（网络不可用或尚未发布 Release）。"
                                            } else if (UpdateService.isNewer(info.tagName, current)) {
                                                updateUrl = info.htmlUrl
                                                updateMsg = "发现新版本 ${info.tagName}" +
                                                    (if (info.title.isNotEmpty()) "（${info.title}）" else "") +
                                                    "，当前为 $current。\n\n前往 Release 页下载最新安装包。"
                                            } else {
                                                updateUrl = null
                                                updateMsg = "当前已是最新版本（$current）。"
                                            }
                                        }
                                    }
                                },
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        Text(
                            "本应用为个人项目，非学校官方项目，仅限用于学习和研究目的。\n" +
                                "课表、考试、成绩数据缓存当天有效；当天内再次打开不会重复访问教务系统，" +
                                "如需获取最新变动，请在课表页或考试页点击刷新按钮手动更新。",
                            fontSize = 11.5.sp,
                            color = scheme.outline,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        },
        child = { key ->
            when (key) {
                "account" -> AccountScreen(
                    app = app,
                    onLogout = {
                        sub = null
                        onLogout()
                    },
                    onBack = { sub = null },
                )

                "feature" -> FeatureDisplayScreen(app = app, onBack = { sub = null })

                "personalization" -> PersonalizeScreen(app = app, onBack = { sub = null })
            }
        },
    )
}

/** 近白圆角分组容器：裁剪子项按压高光到圆角内（捕获层子树内仅用色彩级 alpha，不用投影）。 */
@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(settingsCardBase()),
        content = content,
    )
}

/** 组内发丝分隔线：左侧缩进对齐标题（行水平内边距）。 */
@Composable
internal fun GroupDivider() {
    HorizontalDivider(
        Modifier.padding(start = 16.dp, end = 16.dp),
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** 分组上方的小节标题（灰字，对齐行文字左缘）。 */
@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
    )
}

/**
 * 纯文字设置行：标题（+可选副标题）+ 浅灰 chevron 或自定义 trailing（如开关）。
 * 点按高光用色彩级 alpha（[MutableInteractionSource] 驱动），不使用涟漪/投影。
 * [titleColor] 覆盖标题色（如退出登录用 error）。
 */
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String? = null,
    titleColor: Color? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressBg by animateFloatAsState(if (pressed && onClick != null) 0.05f else 0f, tween(140))
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null) { onClick() }
                } else {
                    Modifier
                },
            )
            .background(scheme.onSurface.copy(alpha = pressBg))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = titleColor ?: scheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 12.5.sp,
                    color = scheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
        }
        when {
            trailing != null -> {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            showChevron -> {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.ChevronRight,
                    null,
                    Modifier.size(18.dp),
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

/** 复制文本到剪贴板。 */
fun Context.copyToClipboard(label: String, text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
