package com.gzmtuview

import com.gzmtuview.data.color.ensureCourseColorIndex
import com.gzmtuview.ui.schedule.SchedulePageCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gzmtuview.ui.AppRoot
import com.gzmtuview.ui.theme.GmuViewTheme

/** GMU View 原生版主入口。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 冷启动预热：把磁盘课表缓存读进页面级缓存（IO 协程，快于首帧组合），
        // 课表页组合时 SchedulePageCache 已有数据 → 无"正在获取课表"闪现
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val app = GmuApplication.deps(this@MainActivity)
                // 默认学期取「磁盘上确实有课」的最新学期：升级残留的空学期缓存
                // （如 2026_12.json 零课程）不能顶到首位，否则每次启动都停在没课的第二学期
                val sems = app.cache.loadCachedSemestersWithData()
                    .ifEmpty { app.cache.loadCachedSemesters() }
                if (sems.isNotEmpty()) {
                    val first = sems.first()
                    SchedulePageCache.semestersState.value = sems
                    SchedulePageCache.selectedState.value = first
                    app.cache.load(first)?.let { c ->
                        SchedulePageCache.coursesState.value = c.result.courses
                        SchedulePageCache.studentNameState.value = c.result.studentName
                        SchedulePageCache.weekDatesState.value = c.weekDates
                        // 颜色映射必须一起预热：只搬课表不搬映射，首屏会先用课名哈希兜底色，
                        // 等每日同步写回映射色时整页「换色」
                        SchedulePageCache.courseColorIndexState.value =
                            ensureCourseColorIndex(app.store, c.result.courses)
                        SchedulePageCache.loadedState.value = true
                        SchedulePageCache.loadingState.value = false
                    }
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            GmuViewTheme {
                AppRoot()
            }
        }
    }
}
