package com.gzmtuview.ui.feature

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
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
import com.gzmtuview.data.net.ZFClient
import com.gzmtuview.ui.ScreenScaffold
import com.gzmtuview.ui.wallpaper.Wallpaper
import com.gzmtuview.ui.glass.GlassDialog
import com.gzmtuview.ui.glass.GlassIconButton
import com.gzmtuview.ui.glass.GlassTopBar
import com.gzmtuview.ui.settings.GroupDivider
import com.gzmtuview.ui.settings.SettingsGroup
import com.gzmtuview.ui.settings.SettingsRow
import com.gzmtuview.ui.settings.settingsPageBaseResolved
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 账号二级页：账号信息、教务系统地址（可复制）、退出登录。
 * 简洁分组列表，与设置主页同一设计语言。
 */
@Composable
fun AccountScreen(
    app: GmuApplication,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var username by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(ZFClient.DEFAULT_BASE_URL) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        username = app.store.loadCredentials()?.username ?: ""
        baseUrl = app.store.loadBaseUrl() ?: ZFClient.DEFAULT_BASE_URL
    }

    // 二级页：隐藏玻璃底栏 + 玻璃对话框挂主窗口 overlay 槽
    ScreenScaffold(
        hideBottomBar = true,
        overlay = {
            GlassDialog(
                visible = showLogoutConfirm,
                onDismissRequest = { showLogoutConfirm = false },
                title = "退出登录",
                textString = "将清除本机保存的账号密码与登录状态，确定退出吗？",
                confirmText = "退出",
                onConfirm = {
                    showLogoutConfirm = false
                    // 先清本地凭据立即退出（不等服务器接口，避免假死）
                    CoroutineScope(Dispatchers.Main).launch {
                        app.store.clearCredentials()
                        onLogout()
                        // 后台通知服务器登出（失败静默）
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching { app.client.logout() }
                        }
                    }
                },
                dismissText = "取消",
                onDismiss = { showLogoutConfirm = false },
            )
        },
    ) {
        val scheme = MaterialTheme.colorScheme
        val pageBase = settingsPageBaseResolved()
        Column(Modifier.fillMaxSize().background(pageBase)) {
            GlassTopBar(
                title = "账号",
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
                SettingsGroup {
                    SettingsRow(
                        title = if (username.isEmpty()) "未登录" else username,
                        subtitle = "广州航海学院 · 正方教务账号",
                        showChevron = false,
                    )
                    GroupDivider()
                    SettingsRow(
                        title = "教务系统地址",
                        subtitle = baseUrl,
                        showChevron = false,
                        trailing = {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                null,
                                Modifier.size(18.dp),
                                tint = scheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            runCatching {
                                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                cm.setPrimaryClip(
                                    android.content.ClipData.newPlainText("教务系统地址", baseUrl),
                                )
                                Toast.makeText(ctx, "教务系统地址已复制", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }

                Spacer(Modifier.height(28.dp))
                SettingsGroup {
                    SettingsRow(
                        title = "退出登录",
                        titleColor = scheme.error,
                        showChevron = false,
                        onClick = { showLogoutConfirm = true },
                    )
                }
            }
        }
    }
}
