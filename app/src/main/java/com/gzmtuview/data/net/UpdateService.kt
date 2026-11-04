package com.gzmtuview.data.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** GitHub Release 最新版本信息。 */
data class ReleaseInfo(
    val tagName: String,
    val title: String,
    val htmlUrl: String,
    val notes: String,
    /** Release 页上的资产地址（github.com；该域在部分网络下不可达） */
    val apkUrl: String?,
    /** API 资产地址（api.github.com/repos/.../releases/assets/:id，下载时优先用它） */
    val apkApiUrl: String?,
    val apkName: String?,
    val apkSize: Long,
)

/**
 * 更新检查与安装包下载：查询 GitHub Releases 的最新发布版本，并把 APK 下到应用缓存。
 * 仓库地址来自 [ProjectRepo.latestReleaseApi]；未配置仓库、网络失败或尚无 Release
 * 时返回 null，由调用方降级提示。
 */
object UpdateService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 安装包走 GitHub 的对象存储重定向，网络慢时 15s 读超时不够用
    private val downloadClient = client.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 拉取最新 Release；失败或未配置仓库返回 null。 */
    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val apiUrl = ProjectRepo.latestReleaseApi ?: return@withContext null
        try {
            val req = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val obj = JSONObject(body)
                // 同一个 Release 也可能带别的附件，优先取 arm64-v8a（唯一构建目标）
                val assets = obj.optJSONArray("assets")
                val candidates = if (assets != null) {
                    (0 until assets.length())
                        .mapNotNull { assets.optJSONObject(it) }
                        .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
                } else {
                    emptyList()
                }
                val apk = candidates.firstOrNull {
                    it.optString("name").contains("arm64", ignoreCase = true)
                } ?: candidates.firstOrNull()
                ReleaseInfo(
                    tagName = obj.optString("tag_name"),
                    title = obj.optString("name"),
                    htmlUrl = obj.optString("html_url"),
                    notes = obj.optString("body"),
                    apkUrl = apk?.optString("browser_download_url")?.takeIf { it.isNotEmpty() },
                    apkApiUrl = apk?.optString("url")?.takeIf { it.isNotEmpty() },
                    apkName = apk?.optString("name")?.takeIf { it.isNotEmpty() },
                    apkSize = apk?.optLong("size") ?: 0L,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 下载安装包到 `cacheDir/update/`（每次先清掉旧包），进度回调 0..1。
     * 下载期间可被协程取消：取消时抛 CancellationException，半截文件留待下次清理。
     */
    suspend fun downloadApk(
        ctx: Context,
        info: ReleaseInfo,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        // 优先 api.github.com：github.com 的下载页在校园网/移动网络常被阻断，
        // 而 API 资产地址在同一网络下实测可用（2026-11-03 真机超时确认）
        val url = info.apkApiUrl ?: info.apkUrl
            ?: throw IllegalStateException("该版本没有提供安装包")
        val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        // 附件名来自网络，只取最后一段，避免拼出目录穿越路径
        val safeName = (info.apkName ?: "update.apk").substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { "update.apk" }
        val target = File(dir, safeName)
        val part = File(dir, "$safeName.part")
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .build()
        downloadClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("下载失败（HTTP ${resp.code}）")
            val body = resp.body ?: throw IllegalStateException("下载失败（空响应）")
            val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize
            var lastPct = -1
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        written += read
                        if (total > 0) {
                            val pct = (written * 100 / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct / 100f)
                            }
                        }
                    }
                }
            }
        }
        if (!part.renameTo(target)) throw IllegalStateException("下载失败（无法保存安装包）")
        target
    }

    /** 校验下载到的 APK：必须是本应用，且 versionCode 高于已安装版本。返回 null 表示通过。 */
    fun validateApk(ctx: Context, file: File): String? {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(file.absolutePath, 0)
            ?: return "安装包损坏或不是 APK"
        if (archive.packageName != ctx.packageName) return "安装包不属于本应用"
        @Suppress("DEPRECATION")
        val installed = pm.getPackageInfo(ctx.packageName, 0)
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            return "安装包版本不高于当前版本"
        }
        return null
    }

    /** 系统是否已允许本应用安装未知来源应用。 */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** 调起系统安装器；调用前需确认 [canInstall]。 */
    fun installApk(ctx: Context, file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        true
    }.getOrDefault(false)

    /** 打开「安装未知应用」授权页（返回后需再点一次安装）。 */
    fun openInstallPermission(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** [remoteTag] 是否比 [currentVersion] 新（忽略 v 前缀与预发布后缀）。 */
    fun isNewer(remoteTag: String, currentVersion: String): Boolean {
        val r = parse(remoteTag)
        val c = parse(currentVersion)
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parse(v: String): List<Int> =
        v.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split('.', '-', '+')
            .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
}
