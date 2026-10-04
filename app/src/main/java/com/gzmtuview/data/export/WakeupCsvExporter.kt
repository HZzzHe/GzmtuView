package com.gzmtuview.data.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.gzmtuview.data.model.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 导出结果。 */
data class CsvExportResult(
    val displayPath: String,
    val shareUri: Uri?,
)

/**
 * WakeUp 课程表 CSV 导出（对齐 Flutter wakeup_export.dart）。
 */
object WakeupCsvExporter {

    /** 课程周次 → WakeUp 周数格式（优先原始文本，缺失时按周集合兜底）。 */
    fun wakeupWeeksText(course: Course): String {
        val raw = course.weeksText.trim()
        if (raw.isNotEmpty()) {
            var s = raw.replace("周", "").trim()
            var suffix = ""
            when {
                s.contains("(单双)") -> s = s.replace("(单双)", "")
                s.contains("(单)") -> {
                    s = s.replace("(单)", "")
                    suffix = "单"
                }
                s.contains("(双)") -> {
                    s = s.replace("(双)", "")
                    suffix = "双"
                }
            }
            val parts = s.split(Regex("[,，]"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (parts.isNotEmpty()) {
                return if (suffix.isEmpty()) parts.joinToString("、")
                else parts.map { "$it$suffix" }.joinToString("、")
            }
        }
        return fromSet(course.weeks)
    }

    /** 兜底：按周集合生成（完整连续 → a-b；否则按单/双合并）。 */
    private fun fromSet(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted = weeks.sorted()
        val full = sorted.size == sorted.last() - sorted.first() + 1
        if (full) {
            return if (sorted.first() == sorted.last()) "${sorted.first()}"
            else "${sorted.first()}-${sorted.last()}"
        }
        val odds = sorted.filter { it % 2 == 1 }
        val evens = sorted.filter { it % 2 == 0 }
        val parts = mutableListOf<String>()
        if (odds.isNotEmpty()) parts.add("${odds.first()}-${odds.last()}单")
        if (evens.isNotEmpty()) parts.add("${evens.first()}-${evens.last()}双")
        return parts.joinToString("、")
    }

    /** 生成 WakeUp 课程表兼容 CSV（UTF-8 with BOM）。 */
    fun buildWakeupCsv(courses: List<Course>): String {
        val sb = StringBuilder("\uFEFF")
        sb.append("课程名称,星期,开始节数,结束节数,老师,地点,周数\n")
        for (c in courses) {
            if (c.sections.isEmpty()) continue
            val row = listOf(
                c.name,
                c.dayOfWeek.toString(),
                c.sections.first().toString(),
                c.sections.last().toString(),
                c.teacher,
                c.location,
                wakeupWeeksText(c),
            )
            sb.append(row.joinToString(",") { escape(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun escape(s: String): String {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"${s.replace("\"", "\"\"")}\""
        }
        return s
    }

    /**
     * 清理下载目录里的历史导出：MediaStore 同名插入不会覆盖而是追加 "(1)/(2)" 后缀，
     * 反复导出会积压一堆副本——写入前删除精确同名与 "(n)" 副本，保证目录里只留最新一份。
     */
    private fun purgePreviousExports(ctx: Context, fileName: String) {
        if (Build.VERSION.SDK_INT < 29) return
        val base = fileName.substringBeforeLast('.')
        runCatching {
            ctx.contentResolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.Downloads.DISPLAY_NAME} = ? OR ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                arrayOf(fileName, "$base (%).csv"),
            )
        }
    }

    /**
     * 保存 CSV 到下载目录（Android 10+ MediaStore；旧版本写 app 外部下载目录）。
     * 同时在缓存目录留一份副本供 FileProvider 分享。返回展示路径与分享 uri。
     */
    suspend fun export(
        ctx: Context,
        courses: List<Course>,
        fileName: String = "gmucalendar_schedule.csv",
    ): CsvExportResult? = withContext(Dispatchers.IO) {
        if (courses.isEmpty()) return@withContext null
        val csv = buildWakeupCsv(courses)
        try {
            val displayPath: String
            val shareUri: Uri?
            if (Build.VERSION.SDK_INT >= 29) {
                purgePreviousExports(ctx, fileName)
                val resolver = ctx.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                        ?: run {
                            resolver.delete(uri, null, null)
                            return@withContext null
                        }
                    displayPath = "下载/$fileName"
                } else {
                    return@withContext null
                }
            } else {
                val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
                val f = File(dir, fileName)
                f.writeText(csv, Charsets.UTF_8)
                displayPath = f.absolutePath
            }
            // 缓存副本供分享
            val shareFile = File(ctx.cacheDir, "exports/$fileName").apply {
                parentFile?.mkdirs()
                writeText(csv, Charsets.UTF_8)
            }
            shareUri = runCatching {
                FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", shareFile)
            }.getOrNull()
            CsvExportResult(displayPath, shareUri)
        } catch (_: Exception) {
            null
        }
    }
}
