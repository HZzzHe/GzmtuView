package com.gzmtuview.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gzmtuview.ui.PredictiveSlideOver
import com.gzmtuview.ui.ScreenScaffold
import com.gzmtuview.ui.glass.GlassTopBar
import com.gzmtuview.ui.settings.GroupDivider
import com.gzmtuview.ui.settings.SectionLabel
import com.gzmtuview.ui.settings.SettingsGroup
import com.gzmtuview.ui.settings.SettingsRow
import com.gzmtuview.ui.settings.settingsPageBaseResolved
import com.gzmtuview.ui.tools.physical.PhysicalTestScreen
import com.gzmtuview.ui.tools.electric.ElectricFeeScreen
import com.gzmtuview.ui.wallpaper.Wallpaper

/**
 * 工具箱页：工具列表（体测成绩计算、电费估算）。
 * 对齐 Flutter ToolsPage。
 */
@Composable
fun ToolsScreen(
    onLogout: () -> Unit,
    onNavigate: (Int) -> Unit = {},
) {
    var sub by remember { mutableStateOf<String?>(null) }

    // 二级子页（体测/电费）：覆盖式父页常驻 + 子页浮层，支持跟手预测性返回
    PredictiveSlideOver(
        childKey = sub,
        onRequestClose = { sub = null },
        parent = {
            ScreenScaffold {
                val scheme = MaterialTheme.colorScheme
                val pageBase = settingsPageBaseResolved()
                Column(Modifier.fillMaxSize().background(pageBase)) {
                    // 页内常驻顶栏（随页面参与切页/push 动画），融入页面基色
                    GlassTopBar(title = "工具箱", backgroundColor = pageBase)
                    Column(
                        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 100.dp),
                    ) {
                        SectionLabel("工具")
                        SettingsGroup {
                            SettingsRow(
                                title = "体测成绩计算",
                                subtitle = "看看哪里还可以更强",
                                leading = {
                                    Icon(Icons.Filled.FitnessCenter, null, Modifier.size(22.dp),
                                        tint = scheme.onSurfaceVariant)
                                },
                                onClick = { sub = "physical" },
                            )
                            GroupDivider()
                            SettingsRow(
                                title = "电费估算",
                                subtitle = "我的电费怎么这么贵？！",
                                leading = {
                                    Icon(Icons.Filled.Bolt, null, Modifier.size(22.dp),
                                        tint = scheme.onSurfaceVariant)
                                },
                                onClick = { sub = "electric" },
                            )
                        }
                    }
                }
            }
        },
        child = { key ->
            // 二级页各自持有 ScreenScaffold（电费页需要 overlay 槽挂玻璃对话框），
            // 此处不再重复包裹，避免同一 PageHost 被父子双重注册
            when (key) {
                "physical" -> PhysicalTestScreen(onBack = { sub = null })
                "electric" -> ElectricFeeScreen(onBack = { sub = null })
            }
        },
    )
}
