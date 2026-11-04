package com.gzmtuview.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gzmtuview.GmuApplication
import com.gzmtuview.data.net.ReleaseInfo
import com.gzmtuview.data.net.UpdateService
import com.gzmtuview.ui.LocalPageHost
import com.gzmtuview.ui.PageHost
import com.gzmtuview.ui.glass.GlassDialog
import com.gzmtuview.ui.glass.LocalAppBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class UpdatePhase { AVAILABLE, DOWNLOADING, READY, FAILED, MESSAGE }

internal data class UpdateUiState(
    val visible: Boolean = false,
    val phase: UpdatePhase = UpdatePhase.MESSAGE,
    val info: ReleaseInfo? = null,
    val progress: Float = 0f,
    val message: String? = null,
    val apk: File? = null,
)

/**
 * 应用内更新的状态机：启动自动检查、手动检查、下载、安装都汇总到这里，
 * 由一个全局 [UpdateDialogHost] 统一展示。下载/安装沿用系统安装器，
 * 全程不打开浏览器（仅当 Release 没有 APK 附件时才回落到发布页）。
 */
object UpdateCenter {

    internal var state by mutableStateOf(UpdateUiState())
        private set

    /** 手动检查时设置页的行副标题跟着变成「检查中…」。 */
    var checking by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var autoChecked = false
    private var downloadJob: Job? = null

    /** 每次启动只自动查一次（进程内）；设置里关掉「自动检查更新」后静默跳过。 */
    fun autoCheck(ctx: Context) {
        if (autoChecked) return
        autoChecked = true
        scope.launch {
            val enabled = withContext(Dispatchers.IO) {
                runCatching { GmuApplication.deps(ctx).store.loadAutoCheckUpdate() }.getOrDefault(true)
            }
            if (enabled) runCheck(ctx, manual = false)
        }
    }

    fun check(ctx: Context, manual: Boolean) {
        // 下载中不打断当前流程（覆盖状态会让进度条凭空消失）
        if (downloadJob?.isActive == true) return
        runCheck(ctx, manual)
    }

    private fun runCheck(ctx: Context, manual: Boolean) {
        checking = true
        scope.launch {
            val info = UpdateService.fetchLatest()
            checking = false
            val current = installedVersion(ctx)
            if (info == null || info.tagName.isEmpty()) {
                if (manual) {
                    state = UpdateUiState(
                        visible = true,
                        phase = UpdatePhase.MESSAGE,
                        message = "暂无法获取更新信息（网络不可用或尚未发布 Release）。",
                    )
                }
                return@launch
            }
            if (!UpdateService.isNewer(info.tagName, current)) {
                if (manual) {
                    state = UpdateUiState(
                        visible = true,
                        phase = UpdatePhase.MESSAGE,
                        message = "当前已是最新版本（$current）。",
                    )
                }
                return@launch
            }
            state = UpdateUiState(
                visible = true,
                phase = UpdatePhase.AVAILABLE,
                info = info,
            )
        }
    }

    fun download(ctx: Context) {
        val info = state.info ?: return
        if (downloadJob?.isActive == true) return
        state = state.copy(phase = UpdatePhase.DOWNLOADING, progress = 0f, message = null)
        downloadJob = scope.launch {
            try {
                val file = UpdateService.downloadApk(ctx, info) { p ->
                    // 进度回调在 IO 线程，回到主线程写状态
                    scope.launch { state = state.copy(progress = p) }
                }
                val invalid = UpdateService.validateApk(ctx, file)
                if (invalid != null) {
                    state = state.copy(phase = UpdatePhase.FAILED, message = "安装包校验失败：$invalid")
                    return@launch
                }
                state = state.copy(phase = UpdatePhase.READY, apk = file, progress = 1f)
                // 已授权就直接拉起系统安装器；未授权则停在 READY，按钮先带用户去授权
                if (UpdateService.canInstall(ctx)) {
                    if (UpdateService.installApk(ctx, file)) {
                        dismiss()
                    } else {
                        state = state.copy(phase = UpdatePhase.FAILED, message = "无法调起系统安装程序。")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    phase = UpdatePhase.FAILED,
                    message = "下载失败：${e.message ?: "网络异常"}",
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        state = UpdateUiState()
    }

    fun install(ctx: Context) {
        val file = state.apk ?: return
        if (!UpdateService.canInstall(ctx)) {
            UpdateService.openInstallPermission(ctx)
            state = state.copy(
                message = "已打开系统设置：请允许本应用安装未知应用，返回后再次点击「安装」。",
            )
            return
        }
        if (UpdateService.installApk(ctx, file)) {
            dismiss()
        } else {
            state = state.copy(phase = UpdatePhase.FAILED, message = "无法调起系统安装程序。")
        }
    }

    fun dismiss() {
        if (downloadJob?.isActive == true) downloadJob?.cancel()
        downloadJob = null
        state = UpdateUiState()
    }

    fun openReleasePage(ctx: Context, url: String) {
        if (url.isNotEmpty()) {
            runCatching {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        dismiss()
    }

    fun installedVersion(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "0.0.0"
}

/**
 * 全局更新对话框：挂在 AppRoot 内容之上、崩溃日志之下。
 * 与崩溃对话框一样位于 AppScreen 之外，必须就地提供 PageHost 与背板。
 */
@Composable
fun UpdateDialogHost() {
    val st = UpdateCenter.state
    if (!st.visible) return
    val ctx = LocalContext.current
    val host = remember { PageHost() }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val backdrop = rememberCanvasBackdrop { drawRect(surfaceColor) }
    val info = st.info
    val hasApk = !info?.apkApiUrl.isNullOrEmpty() || !info?.apkUrl.isNullOrEmpty()

    CompositionLocalProvider(
        LocalPageHost provides host,
        LocalAppBackdrop provides backdrop,
    ) {
        GlassDialog(
            visible = true,
            onDismissRequest = {
                if (st.phase != UpdatePhase.DOWNLOADING) UpdateCenter.dismiss()
            },
            title = when (st.phase) {
                UpdatePhase.AVAILABLE -> "发现新版本 ${info?.tagName.orEmpty()}"
                UpdatePhase.DOWNLOADING -> "正在下载更新"
                UpdatePhase.READY -> "更新已下载"
                UpdatePhase.FAILED -> "更新失败"
                UpdatePhase.MESSAGE -> "检查更新"
            },
            text = {
                when (st.phase) {
                    UpdatePhase.AVAILABLE -> ReleaseNotes(info)
                    UpdatePhase.DOWNLOADING -> DownloadProgress(st.progress, info)
                    UpdatePhase.READY -> Text(
                        st.message ?: "安装包已下载完成。点击「安装」打开系统安装界面；" +
                            "若系统不允许安装未知应用，请允许后返回再次点击「安装」。",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                    UpdatePhase.FAILED, UpdatePhase.MESSAGE -> Text(
                        st.message ?: "",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                }
            },
            confirmText = when (st.phase) {
                UpdatePhase.AVAILABLE -> if (hasApk) "下载更新" else "打开发布页"
                UpdatePhase.DOWNLOADING -> null
                UpdatePhase.READY -> "安装"
                UpdatePhase.FAILED -> if (hasApk) "重试" else "知道了"
                UpdatePhase.MESSAGE -> "知道了"
            },
            onConfirm = {
                when (st.phase) {
                    UpdatePhase.AVAILABLE -> if (hasApk) {
                        UpdateCenter.download(ctx)
                    } else {
                        UpdateCenter.openReleasePage(ctx, info?.htmlUrl.orEmpty())
                    }
                    UpdatePhase.READY -> UpdateCenter.install(ctx)
                    UpdatePhase.FAILED -> if (hasApk) UpdateCenter.download(ctx) else UpdateCenter.dismiss()
                    else -> UpdateCenter.dismiss()
                }
            },
            dismissText = when (st.phase) {
                UpdatePhase.AVAILABLE, UpdatePhase.READY -> "稍后"
                UpdatePhase.DOWNLOADING -> "取消"
                UpdatePhase.FAILED -> "关闭"
                UpdatePhase.MESSAGE -> null
            },
            onDismiss = {
                if (st.phase == UpdatePhase.DOWNLOADING) UpdateCenter.cancelDownload() else UpdateCenter.dismiss()
            },
        )
    }
}

@Composable
private fun ReleaseNotes(info: ReleaseInfo?) {
    val ctx = LocalContext.current
    // 版本号取一次即可（安装更新会杀掉进程，不存在运行中变化的场景）
    val current = remember { UpdateCenter.installedVersion(ctx) }
    Column {
        Text(
            "当前版本 $current",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            formatNotes(info?.notes.orEmpty()),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        )
    }
}

@Composable
private fun DownloadProgress(progress: Float, info: ReleaseInfo?) {
    val scheme = MaterialTheme.colorScheme
    val sizeMb = (info?.apkSize ?: 0L) / 1024.0 / 1024.0
    Column {
        Text(
            "正在从 GitHub 下载安装包…",
            fontSize = 14.sp,
            color = scheme.onSurface.copy(alpha = 0.78f),
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(scheme.onSurface.copy(alpha = 0.10f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(scheme.primary),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%" +
                if (sizeMb >= 0.1) " · %.1f MB".format(sizeMb) else "",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

private val markdownLink = Regex("\\[([^]]+)]\\(([^)]+)\\)")

/**
 * GitHub Release 的说明是 Markdown：只做轻量整形（标题去 #、列表换 •、去粗体标记），
 * 不引入 Markdown 渲染库——更新说明以可读文本呈现即可。
 */
private fun formatNotes(raw: String): String {
    if (raw.isBlank()) return "该版本没有填写更新说明。"
    val text = markdownLink.replace(raw.replace("\r\n", "\n"), "$1")
    val out = StringBuilder()
    for (line in text.split('\n')) {
        val t = line.trim()
        when {
            t.isEmpty() -> if (out.isNotEmpty() && !out.endsWith("\n\n")) out.append('\n')
            t.startsWith("#") -> {
                if (out.isNotEmpty() && !out.endsWith("\n\n")) out.append('\n')
                out.append(t.trimStart('#').trim()).append('\n')
            }
            t.startsWith("- ") || t.startsWith("* ") ->
                out.append("• ").append(t.drop(2).trim()).append('\n')
            else -> out.append(t).append('\n')
        }
    }
    return out.toString().replace("**", "").replace("`", "").trim()
}
