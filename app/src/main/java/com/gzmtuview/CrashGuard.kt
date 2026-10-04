package com.gzmtuview

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 全局崩溃捕获：堆栈写入 files/crash_last.txt，下次启动由 AppRoot 弹窗展示，
 * 便于远程（无 adb）定位闪退。
 */
object CrashGuard : Thread.UncaughtExceptionHandler {
    private const val FILE_NAME = "crash_last.txt"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun install(app: GmuApplication) {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    fun lastCrashFile(app: GmuApplication): File = File(app.filesDir, FILE_NAME)

    fun clear(app: GmuApplication) {
        runCatching { lastCrashFile(app).delete() }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        runCatching {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val app = GmuApplication.instance
            lastCrashFile(app).writeText(
                buildString {
                    appendLine("time=${System.currentTimeMillis()}")
                    appendLine("thread=${t.name}")
                    appendLine(sw.toString())
                },
            )
        }
        defaultHandler?.uncaughtException(t, e)
    }
}
