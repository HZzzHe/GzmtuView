package com.gzmtuview.ui.feature

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gzmtuview.GmuApplication
import com.gzmtuview.ui.ScreenScaffold
import com.gzmtuview.ui.schedule.SchedulePageCache
import com.gzmtuview.widget.WidgetPusher
import com.gzmtuview.ui.glass.FrostScrollPage
import com.gzmtuview.ui.glass.GlassDialog
import com.gzmtuview.ui.glass.GlassIconButton
import com.gzmtuview.ui.glass.Haptics
import com.gzmtuview.ui.glass.LiquidGlassSwitch
import com.gzmtuview.ui.settings.GroupDivider
import com.gzmtuview.ui.settings.SectionLabel
import com.gzmtuview.ui.settings.SettingsGroup
import com.gzmtuview.ui.settings.SettingsRow
import com.gzmtuview.ui.settings.settingsCardBase
import com.gzmtuview.ui.wallpaper.Wallpaper
import com.gzmtuview.ui.settings.settingsPageBaseResolved

/**
 * 「功能」二级页：体验分区（课表显示学生姓名 / 显示考试倒计时 等开关）+ Beta 分区。
 * 简洁分组列表，与设置主页同一设计语言。
 */
@Composable
fun FeatureDisplayScreen(
    app: GmuApplication,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    var showGridLines by remember { mutableStateOf(false) }
    var showCourseCapsule by remember { mutableStateOf(true) }
    var showStudentName by remember { mutableStateOf(false) }
    var showTeacherName by remember { mutableStateOf(true) }
    var showExamCountdown by remember { mutableStateOf(true) }
    var showWeekStepper by remember { mutableStateOf(true) }
    var preferHttps by remember { mutableStateOf(true) }
    var autoCheckUpdate by remember { mutableStateOf(true) }
    var holidayAdjust by remember { mutableStateOf(false) }
    var hapticFeedback by remember { mutableStateOf(false) }

    // 重启确认（preferHttps 需要重建界面生效）
    var restartPromptFor by remember { mutableStateOf<String?>(null) }
    var confirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showGridLines = app.store.loadShowGridLines()
        showCourseCapsule = app.store.loadShowCourseCapsule()
        showStudentName = app.store.loadShowStudentName()
        showTeacherName = app.store.loadShowTeacherName()
        showExamCountdown = app.store.loadShowExamCountdown()
        showWeekStepper = app.store.loadShowWeekStepper()
        preferHttps = app.store.loadPreferHttps()
        autoCheckUpdate = app.store.loadAutoCheckUpdate()
        holidayAdjust = app.store.loadHolidayAdjust()
        hapticFeedback = app.store.loadHapticFeedback()
        loaded = true
    }

    // 二级页：隐藏玻璃底栏 + 玻璃对话框挂主窗口 overlay 槽
    ScreenScaffold(
        hideBottomBar = true,
        overlay = {
            GlassDialog(
                visible = confirmDialog,
                onDismissRequest = { confirmDialog = false },
                title = "重启生效",
                textString = "该设置（${restartPromptFor ?: ""}）需要重启软件后才会生效。",
                confirmText = "立即重启",
                onConfirm = {
                    confirmDialog = false
                    val act = ctx as? Activity
                    if (act != null) {
                        act.recreate()
                    } else {
                        Toast.makeText(ctx, "已保存，重启后生效", Toast.LENGTH_SHORT).show()
                    }
                },
                dismissText = "稍后",
                onDismiss = { confirmDialog = false },
            )
        },
    ) {
        val pageBase = settingsPageBaseResolved()
        // 开关折射所在分组卡片纯色（近白/更亮一档），避免捕获层自采样
        val switchBackdrop = settingsCardBase()
        FrostScrollPage(
            title = "功能",
            pageBase = pageBase,
            leading = {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onClick = onBack)
            },
        ) {
            SectionLabel("体验")
            SettingsGroup {
                ToggleRow(
                    title = "课表显示课程定位辅助线",
                    subtitle = "关闭后不显示课程之间的分界线",
                    value = showGridLines,
                    loaded = loaded,
                    backdropColor = switchBackdrop,
                    onChanged = {
                        showGridLines = it
                        app.store.saveShowGridLines(it)
                    },
                )
                GroupDivider()
                ToggleRow(
                    title = "课表显示课程胶囊",
                    subtitle = "关闭后课程卡片顶部不显示小胶囊标记条（文字位置与排版不变）",
                    value = showCourseCapsule,
                    loaded = loaded,
                    backdropColor = switchBackdrop,
                    onChanged = {
                        showCourseCapsule = it
                        app.store.saveShowCourseCapsule(it)
                    },
                )
                GroupDivider()
                ToggleRow(
                    title = "课表显示学生姓名",
                    subtitle = "关闭后课表页仅显示周次，不显示姓名",
                    value = showStudentName,
                    loaded = loaded,
                    backdropColor = switchBackdrop,
                    onChanged = {
                        showStudentName = it
                        app.store.saveShowStudentName(it)
                    },
                )
                GroupDivider()
                ToggleRow(
                    title = "课表显示任课老师姓名",
                    subtitle = "关闭后课表仅显示课程名称和上课地点",
                    value = showTeacherName,
                    loaded = loaded,
                    backdropColor = switchBackdrop,
                    onChanged = {
                        showTeacherName = it
                        app.store.saveShowTeacherName(it)
                    },
                )
                GroupDivider()
                ToggleRow(
                    title = "显示考试倒计时",
                    subtitle = "关闭后考试卡片不显示剩余时间",
                    value = showExamCountdown,
                    loaded = loaded,
                    backdropColor = switchBackdrop,
                    onChanged = {
                        showExamCountdown = it
                        app.store.saveShowExamCountdown(it)
                    },
                )
                GroupDivider()
                ToggleRow(
                    title = "显示周次步进器",
                    subtitle = "关闭后课表右下角不显示切周步进器（仍可用页眉周次选择切周）",
                    value = showWeekStepper,
                    loaded = loaded,
                    backdropColor = switchBackdrop,
                    onChanged = {
                        showWeekStepper = it
                        app.store.saveShowWeekStepper(it)
                    },
                )
                GroupDivider()
                ToggleRow(
                    title = "交互触感",
                    subtitle = "玻璃按钮与周次步进器按动、开关翻转、底部标签页切换、周次滑块跨档时给出轻微振动反馈（立即生效）",
                    value = hapticFeedback,
                    loaded = loaded,
                    backdropColor = switchBackdrop,
                    onChanged = {
                        hapticFeedback = it
                        app.store.saveHapticFeedback(it)
                        // 写穿到全局触感通道：无需重启，下一次交互即生效
                        Haptics.enabled = it
                    },
                )
                GroupDivider()
                ToggleRow(
                    title = "优先使用 https 通道",
                    subtitle = "关闭后优先使用 http 通道，连接失败时自动改用 https（需重启生效）",
                    value = preferHttps,
                    loaded = loaded,
                    backdropColor = switchBackdrop,
                    onChanged = {
                        preferHttps = it
                        app.store.savePreferHttps(it)
                        restartPromptFor = "优先使用 https 通道"
                        confirmDialog = true
                    },
                )
                GroupDivider()
                ToggleRow(
                    title = "自动检查更新",
                    subtitle = "每次启动时查询 GitHub Releases；关闭后不再自动检查，" +
                        "仍可在「关于 → 检查更新」手动检查",
                    value = autoCheckUpdate,
                    loaded = loaded,
                    backdropColor = switchBackdrop,
                    onChanged = {
                        autoCheckUpdate = it
                        app.store.saveAutoCheckUpdate(it)
                    },
                )
            }

            Spacer(Modifier.height(28.dp))
            SectionLabel("Beta")
            SettingsGroup {
                ToggleRow(
                    title = "节假日调休（Beta）",
                    subtitle = "按官方放假与调休安排重排课表：放假日课程置灰，补班日显示被调走那天的课（需重启生效）",
                    value = holidayAdjust,
                    loaded = loaded,
                    backdropColor = switchBackdrop,
                    onChanged = {
                        holidayAdjust = it
                        app.store.saveHolidayAdjust(it)
                        // 小部件按推送快照自行重排：立刻用页面缓存重推一次，
                        // 不必等课表页重建（用户此刻很可能停在设置页）
                        WidgetPusher.push(
                            ctx,
                            SchedulePageCache.coursesState.value,
                            SchedulePageCache.weekDatesState.value,
                            SchedulePageCache.courseColorIndexState.value,
                            app.store.loadSkippedCourses(),
                            it,
                        )
                        restartPromptFor = "节假日调休（Beta）"
                        confirmDialog = true
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    value: Boolean,
    loaded: Boolean,
    backdropColor: androidx.compose.ui.graphics.Color,
    onChanged: (Boolean) -> Unit,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        showChevron = false,
        trailing = {
            if (loaded) {
                LiquidGlassSwitch(
                    checked = value,
                    onCheckedChange = onChanged,
                    backdropColor = backdropColor,
                )
            }
        },
    )
}
