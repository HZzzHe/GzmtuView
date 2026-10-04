package com.gzmtuview.data.net

import com.gzmtuview.data.model.fmtDate
import com.gzmtuview.data.model.kLat
import com.gzmtuview.data.model.kLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 温度数据服务（Open-Meteo，CC-BY 4.0）。对齐 Flutter TemperatureService。
 * 策略（规避限流）：
 * 1. 预打包历史数据（assets/weather_pack.json，查历史零请求）
 * 2. 联网补充缺失日期（archive 历史 + forecast 未来，按连续区间合并请求）
 * 3. 联网结果写入本地缓存文件，下次启动直接复用
 * 4. 429/网络失败静默降级（缺失日期在估算时跳过）
 */
class WeatherService(private val cacheDir: File) {

    private val cache = HashMap<String, Double>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 加载预打包历史数据（assets JSON 内容）。 */
    fun loadBundled(bundledJson: String) {
        try {
            val obj = JSONObject(bundledJson).getJSONObject("data")
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                cache[k] = obj.getDouble(k)
            }
        } catch (_: Exception) {
        }
    }

    /** 从本地缓存文件恢复。 */
    fun loadLocalCache() {
        try {
            val file = File(cacheDir, cacheFileName)
            if (!file.exists()) return
            val obj = JSONObject(file.readText())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                cache[k] = obj.getDouble(k)
            }
        } catch (_: Exception) {
        }
    }

    private fun saveLocalCache() {
        try {
            val out = JSONObject()
            cache.forEach { (k, v) -> out.put(k, v) }
            File(cacheDir, cacheFileName).writeText(out.toString())
        } catch (_: Exception) {
        }
    }

    /** 确保 [start, end] 范围内每天都有温度；缺失的天数联网获取。返回联网获取天数。 */
    suspend fun ensureRange(start: LocalDate, end: LocalDate): Int = withContext(Dispatchers.IO) {
        val missing = mutableListOf<String>()
        var d = start
        while (!d.isAfter(end)) {
            if (!cache.containsKey(fmtDate(d))) missing.add(fmtDate(d))
            d = d.plusDays(1)
        }
        if (missing.isEmpty()) return@withContext 0

        // 合并成连续区间（每段 1 次请求）
        val segments = mutableListOf<Pair<LocalDate, LocalDate>>()
        var segStart: LocalDate? = null
        var prev: LocalDate? = null
        for (key in missing) {
            val day = LocalDate.parse(key)
            if (segStart == null) {
                segStart = day
            } else if (prev != null && day.toEpochDay() - prev.toEpochDay() != 1L) {
                segments.add(segStart to prev)
                segStart = day
            }
            prev = day
        }
        if (segStart != null && prev != null) segments.add(segStart to prev)

        var fetched = 0
        val now = LocalDate.now()
        for ((s, e) in segments) {
            val isFuture = s.isAfter(now.minusDays(1))
            val base = if (isFuture) {
                "https://api.open-meteo.com/v1/forecast"
            } else {
                "https://archive-api.open-meteo.com/v1/archive"
            }
            val url = "$base?latitude=$kLat&longitude=$kLon" +
                "&start_date=${fmtDate(s)}&end_date=${fmtDate(e)}" +
                "&daily=temperature_2m_mean&timezone=Asia%2FShanghai"
            try {
                val req = okhttp3.Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code == 429) return@use // 限流降级
                    val body = resp.body?.string() ?: return@use
                    val daily = JSONObject(body).optJSONObject("daily") ?: return@use
                    val times = daily.optJSONArray("time") ?: return@use
                    val vals = daily.optJSONArray("temperature_2m_mean") ?: return@use
                    for (i in 0 until times.length()) {
                        val k = times.optString(i)
                        val v = vals.optDouble(i, Double.NaN)
                        if (k.isNotEmpty() && !v.isNaN()) cache[k] = v
                    }
                    fetched += times.length()
                }
            } catch (_: Exception) {
                // 网络异常：缺失日期估算时跳过
            }
        }
        saveLocalCache()
        fetched
    }

    /** 读取某日温度；无数据返回 null。 */
    operator fun get(dateKey: String): Double? = cache[dateKey]

    companion object {
        private const val cacheFileName = "weather_cache.json"
    }
}
