package com.gzmtuview.ui

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gzmtuview.GmuApplication
import com.gzmtuview.ui.exams.ExamPageCache
import com.gzmtuview.CrashGuard
import com.gzmtuview.ui.glass.GlassDialog
import com.gzmtuview.ui.glass.LocalAppBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.gzmtuview.ui.schedule.SchedulePageCache
import com.gzmtuview.ui.sync.DailySync
import com.gzmtuview.data.net.isNetworkError
import com.gzmtuview.ui.login.LoginScreen
import com.gzmtuview.ui.schedule.ScheduleScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 应用根：启动恢复会话 → 登录页或课表页。
 *
 * 当天首次打开不再拿全屏加载页拦人：本机有课表缓存就直接进课表看旧数据，
 * 「课表 + 周次日期 + 考试 + 成绩」的一次合并同步由 [DailySync] 在应用级作用域里跑，
 * 进度落在课表页底栏槽的那颗芯片上（见 ScheduleScreen 的 SyncChip）。
 * 只有「本机一点数据都没有」（新装/清空数据）才需要联网确认会话，此时才显示启动加载页。
 */
@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val app = GmuApplication.deps(ctx)
    var restoring by remember { mutableStateOf(true) }
    var loggedIn by remember { mutableStateOf(false) }
    // 恢复态文案：只有「新装/清空数据」才会在这一段联网校验会话，那句话术对应的才是真实等待；
    // 有本机缓存时进门不请求网络，转圈只是磁盘读的时间。此段绝不能只剩一个光转圈。
    var restoringHint by remember { mutableStateOf("正在启动…") }
    // 会话失效被芯片送回登录页时的说明（主动退出登录不带这句）
    var reloginHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val cache = app.cache
        val store = app.store
        val freshToday = cache.hasFreshCacheToday()
        restoringHint = if (freshToday) "正在加载本地课表…" else "今日首次打开，正在获取最新数据…"
        loggedIn = runCatching {
            val creds = store.loadCredentials()
            val password = creds?.password
            when {
                creds == null || password == null -> false
                // 有本地数据先放进去看，同步在芯片里跑；断网/会话失效都不拦进门
                freshToday || cache.hasAnyCache() -> true
                else -> trySession(app, creds.username, password)
            }
        }.getOrDefault(false)
        restoring = false
    }

    // 上次崩溃日志展示（远程无 adb：用户截图即可反馈）
    var lastCrash by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            DailySync.start(app)
            val f = CrashGuard.lastCrashFile(app)
            if (f.exists()) {
                lastCrash = runCatching { f.readText() }.getOrNull()
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        when {
            restoring -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    restoringHint,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            !loggedIn -> LoginScreen(
                hint = reloginHint,
                onLoggedIn = {
                    reloginHint = null
                    loggedIn = true
                },
            )
            else -> MainTabs(
                onLogout = {
                    CoroutineScope(Dispatchers.Main).launch {
                        app.store.clearCredentials()
                    }
                    // 在跑的同步作业属于旧会话，必须停；再清页面级缓存，
                    // 下个会话不残留上次课表/考试数据
                    DailySync.cancel()
                    SchedulePageCache.reset()
                    ExamPageCache.reset()
                    loggedIn = false
                },
                onRelogin = {
                    // 会话失效：不动凭据与缓存，只把用户送回登录页（芯片点了就重新同步）
                    DailySync.cancel()
                    SchedulePageCache.errorState.value = null
                    reloginHint = "教务系统会话已失效，请重新登录"
                    loggedIn = false
                },
            )
        }

        // 崩溃日志对话框必须【最后】组合才绘制在内容之上；且它位于 AppScreen 之外，
        // 必须就地提供 PageHost 与背板——否则 LocalPageHost 默认值抛错 → 每次启动死循环
        lastCrash?.let { stack ->
            val crashHost = remember { PageHost() }
            val surfaceColor = MaterialTheme.colorScheme.surface
            val crashBackdrop = rememberCanvasBackdrop {
                drawRect(surfaceColor)
            }
            CompositionLocalProvider(
                LocalPageHost provides crashHost,
                LocalAppBackdrop provides crashBackdrop,
            ) {
                GlassDialog(
                    visible = true,
                    onDismissRequest = {
                        CrashGuard.clear(app)
                        lastCrash = null
                    },
                    title = "检测到上次异常退出",
                    text = {
                        Text(
                            stack.take(1800),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.75f),
                        )
                    },
                    confirmText = "复制日志",
                    onConfirm = {
                        runCatching {
                            val cm = app.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", lastCrash ?: ""))
                        }
                        CrashGuard.clear(app)
                        lastCrash = null
                    },
                )
            }
        }
    }
}

/**
 * 本机一点缓存都没有时（新装/清空数据）才走这里：必须联网确认会话可用才能进门。
 * 先用一次轻量请求探活（教务年度列表），拿到的是"改版/密码错"这类业务错误就补登一次；
 * 纯网络错误直接判失败——没有缓存可展示，进主界面也只是对着空课表转圈。
 */
private suspend fun trySession(app: GmuApplication, username: String, password: String): Boolean =
    try {
        app.client.fetchYearList()
        true
    } catch (e: Exception) {
        if (isNetworkError(e)) {
            false
        } else {
            runCatching { app.client.login(username, password) }.isSuccess
        }
    }
