package com.gzmtuview.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.gzmtuview.ui.exams.ExamsScreen
import com.gzmtuview.ui.schedule.ScheduleScreen
import com.gzmtuview.ui.settings.SettingsScreen
import com.gzmtuview.ui.tools.ToolsScreen

/**
 * 主界面：课表(0) / 考试(1) / 工具箱(2) 三个主 tab + 设置覆盖页（仅从课表「更多」菜单进入）。
 * 玻璃外壳（含底栏）在此层单实例——主 tab 切换只换 [AppScreen] body 里的 [AnimatedContent]，
 * 底栏/顶栏浮层恒定不重建；主 tab 间切页 = 新页 96%→100% 缩放 + 淡入（从内打开）。
 *
 * 设置页改为 [PredictiveSlideOver] 覆盖层（父 = 主 tab，子 = 设置）：进入右滑覆盖、
 * 系统返回手势跟手右滑出、父页视差归位——与设置内二级子页（账号/功能）、工具箱
 * 二级子页（体测/电费）共用同一套预测性返回机制。嵌套时更深的手势处理器优先，
 * 故「账号 → 设置 → 课表」逐级返回互不干扰。
 */
@Composable
fun MainTabs(onLogout: () -> Unit, onRelogin: () -> Unit = {}) {
    // tab：主 tab 0/1/2（不再含设置）；settingsOpen：设置覆盖页开关
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    // 导航目标：3 = 打开设置覆盖页；其余 = 切主 tab 并关闭设置
    fun navigate(dest: Int) {
        if (dest == 3) {
            settingsOpen = true
        } else {
            settingsOpen = false
            tab = dest
        }
    }
    // 主 tab（考试/工具箱）系统返回先回课表，再次返回才退出应用；
    // 设置覆盖页开启时由 PredictiveSlideOver 的预测性返回手势接管，故此处禁用
    androidx.activity.compose.BackHandler(enabled = !settingsOpen && tab != 0) { tab = 0 }
    AppScreen(
        selectedIndex = tab,
        onSelect = { navigate(it) },
    ) {
        PredictiveSlideOver<Unit>(
            childKey = if (settingsOpen) Unit else null,
            onRequestClose = { settingsOpen = false },
            parent = {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val enter = fadeIn(tween(190)) + scaleIn(
                            initialScale = 0.96f,
                            animationSpec = tween(190, easing = LinearOutSlowInEasing),
                        )
                        (enter togetherWith fadeOut(tween(90))).apply { targetContentZIndex = 1f }
                    },
                    label = "pageSwitch",
                ) { page ->
                    when (page) {
                        0 -> ScheduleScreen(
                            onLogout = onLogout,
                            onNavigate = { navigate(it) },
                            onRelogin = onRelogin,
                        )
                        1 -> ExamsScreen(onLogout = onLogout, onNavigate = { navigate(it) })
                        2 -> ToolsScreen(onLogout = onLogout, onNavigate = { navigate(it) })
                    }
                }
            },
            child = {
                SettingsScreen(onLogout = onLogout, onNavigate = { navigate(it) })
            },
        )
    }
}
