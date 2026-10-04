package com.gzmtuview

import android.app.Application
import android.content.Context
import com.gzmtuview.data.cache.ScheduleCache
import com.gzmtuview.data.net.ZFClient
import com.gzmtuview.data.pref.SettingsStore
import com.gzmtuview.ui.glass.Haptics
import com.gzmtuview.ui.wallpaper.Wallpaper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/** 应用级依赖容器（单例）。 */
class GmuApplication : Application() {

    lateinit var store: SettingsStore
        private set
    lateinit var cache: ScheduleCache
        private set
    lateinit var client: ZFClient
        private set

    /**
     * 每日同步专用作用域（应用级）：作业必须活得比页面协程久——课表/考试页都在
     * AnimatedContent 里，切页即销毁各自的 rememberCoroutineScope。
     * 退出登录由 DailySync.cancel() 取消作业，故这里不随生命周期关闭。
     */
    val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashGuard.install(this)
        store = SettingsStore(applicationContext)
        cache = ScheduleCache(applicationContext)
        // 触感（beta，默认关）：挂载 Vibrator + 读一次偏好，触发点不再查盘
        Haptics.attach(applicationContext)
        Haptics.enabled = store.loadHapticFeedback()
        // 自定义壁纸（beta，默认关）：读一次偏好，本地壁纸文件解码放 IO 协程
        Wallpaper.attach(applicationContext)
        // 初始化 ZFClient（读取 baseUrl/preferHttps 偏好；DataStore 首次读取放 IO）
        client = runBlocking(Dispatchers.IO) {
            val baseUrl = store.loadBaseUrl()
            val preferHttps = store.loadPreferHttps()
            ZFClient(
                preferHttps = preferHttps,
                store = store,
                semesterCache = store,
                baseUrlOverride = baseUrl,
            )
        }
    }

    companion object {
        lateinit var instance: GmuApplication
            private set

        fun deps(context: Context): GmuApplication =
            context.applicationContext as GmuApplication
    }
}
