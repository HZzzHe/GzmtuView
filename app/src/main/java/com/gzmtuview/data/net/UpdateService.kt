package com.gzmtuview.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** GitHub Release 最新版本信息。 */
data class ReleaseInfo(
    val tagName: String,
    val title: String,
    val htmlUrl: String,
)

/**
 * 更新检查服务（预留）：查询 GitHub Releases 的最新发布版本。
 * 仓库地址来自 [ProjectRepo.latestReleaseApi]；未配置仓库、网络失败或尚无 Release
 * 时返回 null，由调用方降级提示。
 */
object UpdateService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 拉取最新 Release；失败返回 null。 */
    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val api = ProjectRepo.latestReleaseApi ?: return@withContext null
        try {
            val req = okhttp3.Request.Builder()
                .url(api)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val obj = JSONObject(body)
                ReleaseInfo(
                    tagName = obj.optString("tag_name"),
                    title = obj.optString("name"),
                    htmlUrl = obj.optString("html_url"),
                )
            }
        } catch (_: Exception) {
            null
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
