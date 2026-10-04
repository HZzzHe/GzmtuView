package com.gzmtuview.ui.sync

import androidx.compose.runtime.mutableStateOf
import com.gzmtuview.GmuApplication
import com.gzmtuview.data.cache.coursesChanged
import com.gzmtuview.data.cache.examListChanged
import com.gzmtuview.data.cache.gradeListChanged
import com.gzmtuview.data.color.ensureCourseColorIndex
import com.gzmtuview.data.model.Semester
import com.gzmtuview.data.model.computeGradeStats
import com.gzmtuview.data.net.ZFException
import com.gzmtuview.data.net.isNetworkError
import com.gzmtuview.ui.exams.ExamPageCache
import com.gzmtuview.ui.schedule.SchedulePageCache
import com.gzmtuview.widget.WidgetPusher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 每日同步阶段：课表页底栏槽的芯片据此渲染。 */
enum class SyncPhase { Idle, Syncing, NeedLogin, Failed }

/**
 * 每日同步：当天第一次打开应用时，把「课表 + 周次日期 + 考试时间 + 成绩」在一次作业里取完。
 *
 * 为什么不放在页面协程里：AnimatedContent 切页会销毁页面的 rememberCoroutineScope，
 * 用户切到考试页/工具箱的那一刻，正在跑的同步会被连带取消——只有应用级作用域
 * （[GmuApplication.syncScope]）能保证"一次同步喂三个页面"。
 *
 * 「今天同步过了没有」不用额外记日期，直接看三份缓存各自的 fetchedAt：
 * 任何一部分没写成功（进程被杀、单接口失败），下次打开只补那一部分，
 * 已经取回的部分不会被重复请求——既满足「没同步完就关掉、下次仍会同步」，
 * 也天然实现了断点续传。
 */
object DailySync {
    val phase = mutableStateOf(SyncPhase.Idle)

    /** 本次同步发生变化的数据种类；非空即提示一次，由课表页消费后清空 */
    val changed = mutableStateOf<List<String>>(emptyList())

    private var job: Job? = null

    /** 同步作业是否正在跑（考试页据此决定要不要自己再发一份请求） */
    val isRunning: Boolean get() = job?.isActive == true

    /**
     * 当天首次打开时调用，幂等：作业在跑 / 今天已经同步过都直接返回。
     * 判断「同步过没有」只读磁盘不联网——所以每天第二次打开不会有芯片闪现。
     */
    fun start(app: GmuApplication) {
        if (job?.isActive == true) return
        job = app.syncScope.launch {
            val today = LocalDate.now()
            val cachedFirst = app.cache.loadCachedSemesters().firstOrNull()
            if (cachedFirst != null && isSyncedToday(app, cachedFirst, today)) {
                // 今天已同步过：一个请求都不发，但仍用带 3 天有效期、读盘即得的学期缓存
                // 补全「学期选择」列表——磁盘上只有取过数的学期有文件，比服务器给的少。
                val valid = runCatching { app.store.loadValidSemesters() }.getOrNull()
                if (!valid.isNullOrEmpty()) {
                    SchedulePageCache.semestersState.value = valid
                    ExamPageCache.semestersState.value = valid
                }
                finishIdle()
                return@launch
            }
            phase.value = SyncPhase.Syncing
            changed.value = emptyList()
            try {
                run(app, cachedFirst, today)
            } catch (e: CancellationException) {
                phase.value = SyncPhase.Idle
                throw e
            } catch (e: Exception) {
                fail(e)
            } finally {
                if (phase.value == SyncPhase.Syncing) finishIdle()
            }
        }
    }

    /** 收尾：芯片退场；页面若还在转圈而课表确实是空的，就别再转了（空课表也是同步结果） */
    private fun finishIdle() {
        phase.value = SyncPhase.Idle
        if (SchedulePageCache.coursesState.value.isEmpty()) SchedulePageCache.loadingState.value = false
    }

    /** 等当前作业结束：考试页在同步进行中进入时，先等结果再决定要不要自己取数 */
    suspend fun awaitRunning() {
        job?.join()
    }

    /** 退出登录：取消在跑的作业并复位，避免旧会话继续写页面缓存 */
    fun cancel() {
        job?.cancel()
        job = null
        phase.value = SyncPhase.Idle
        changed.value = emptyList()
    }

    private suspend fun isSyncedToday(app: GmuApplication, sem: Semester, today: LocalDate) =
        app.cache.load(sem)?.fetchedAt == today &&
            app.cache.loadExams(sem)?.fetchedAt == today &&
            app.cache.loadGrades(sem)?.fetchedAt == today

    private suspend fun run(app: GmuApplication, cachedFirst: Semester?, today: LocalDate) {
        val sems = try {
            app.client.fetchSemesters()
        } catch (e: Exception) {
            fail(e)
            return
        }
        if (sems.isEmpty()) {
            fail(ZFException("未获取到学期信息"))
            return
        }
        val sem = sems.first()
        SchedulePageCache.semestersState.value = sems
        ExamPageCache.semestersState.value = sems
        // 用户没手动换过学期（还停在缓存里那个）才跟随网络返回的当前学期，
        // 否则同步结束不该把他正在看的学期拽走
        val shown = SchedulePageCache.selectedState.value
        if (shown == null || shown == cachedFirst) SchedulePageCache.selectedState.value = sem

        val changes = mutableListOf<String>()
        syncSchedule(app, sem, today, changes)
        // 课表那一步已经判定会话失效/断网时，别再对着同一个死会话取考试和成绩
        if (phase.value != SyncPhase.Syncing) return
        syncExams(app, sem, today, changes)
        syncGrades(app, sem, today, changes)
        if (changes.isNotEmpty()) changed.value = changes
        finishIdle()
    }

    /** 课表 + 周次日期：同步的关键部分，失败要在芯片上说清楚 */
    private suspend fun syncSchedule(
        app: GmuApplication,
        sem: Semester,
        today: LocalDate,
        changes: MutableList<String>,
    ) {
        val cached = app.cache.load(sem)
        if (cached?.fetchedAt == today) return
        val fetched = try {
            val (schedule, wd) = coroutineScope {
                val r = async { app.client.fetchSchedule(sem) }
                val w = async { app.client.fetchWeekDates() }
                r.await() to w.await()
            }
            val matchWd = if (wd != null && wd.matches(sem)) wd else null
            app.cache.save(sem, schedule, matchWd)
            schedule to matchWd
        } catch (e: Exception) {
            fail(e)
            return
        }
        val (schedule, matchWd) = fetched
        if (cached != null && coursesChanged(cached.result.courses, schedule.courses)) changes.add("课表")
        SchedulePageCache.coursesState.value = schedule.courses
        SchedulePageCache.studentNameState.value = schedule.studentName
        SchedulePageCache.weekDatesState.value = matchWd
        SchedulePageCache.loadingState.value = false
        SchedulePageCache.errorState.value = null
        SchedulePageCache.loadedState.value = true
        val colorIndex = ensureCourseColorIndex(app.store, schedule.courses)
        SchedulePageCache.courseColorIndexState.value = colorIndex
        WidgetPusher.push(
            app, schedule.courses, matchWd, colorIndex,
            app.store.loadSkippedCourses(), app.store.loadHolidayAdjust(),
        )
    }

    private suspend fun syncExams(
        app: GmuApplication,
        sem: Semester,
        today: LocalDate,
        changes: MutableList<String>,
    ) {
        val cached = app.cache.loadExams(sem)
        if (cached?.fetchedAt == today) return
        // 考试/成绩是同一趟车上的顺路活：单个接口失败不打断同步，页面原有的回退兜底照旧
        val items = runCatching { app.client.fetchExams(sem) }.getOrNull() ?: return
        app.cache.saveExams(sem, items)
        if (cached != null && examListChanged(cached.items, items)) changes.add("考试时间")
        val showing = ExamPageCache.examSemesterState.value
        if (items.isNotEmpty() && (!ExamPageCache.loaded || showing == null || showing == sem)) {
            ExamPageCache.examsState.value = items
            ExamPageCache.examSemesterState.value = sem
            ExamPageCache.examFallbackState.value = null
            ExamPageCache.loaded = true
        }
    }

    private suspend fun syncGrades(
        app: GmuApplication,
        sem: Semester,
        today: LocalDate,
        changes: MutableList<String>,
    ) {
        val cached = app.cache.loadGrades(sem)
        if (cached?.fetchedAt == today) return
        val items = runCatching { app.client.fetchGrades(sem) }.getOrNull() ?: return
        app.cache.saveGrades(sem, items)
        // 取到了就是"已就绪"（空成绩也是有效结果）：成绩 tab 不必再空转一圈
        ExamPageCache.gradeSettled = true
        if (cached != null && gradeListChanged(cached.items, items)) changes.add("成绩")
        val showing = ExamPageCache.gradeSemesterState.value
        if (items.isNotEmpty() && (!ExamPageCache.loaded || showing == null || showing == sem)) {
            ExamPageCache.gradesState.value = items
            ExamPageCache.gradeStatsState.value = computeGradeStats(items)
            ExamPageCache.gradeSemesterState.value = sem
            ExamPageCache.gradeFallbackState.value = null
            ExamPageCache.loaded = true
        }
    }

    /** 失败分类：会话类错误 → 芯片「重新登录」，其余一律 → 「同步失败」（芯片点开看说明） */
    private fun fail(e: Throwable) {
        val auth = e is ZFException && (e.authFailed || e.sessionExpired)
        phase.value = if (auth) SyncPhase.NeedLogin else SyncPhase.Failed
        // 一点缓存都没有（新装/清空后首次）时，全屏错误态比角落芯片更早被看到
        if (SchedulePageCache.coursesState.value.isEmpty()) {
            SchedulePageCache.loadingState.value = false
            SchedulePageCache.errorState.value =
                if (isNetworkError(e)) "网络不可用，且暂无缓存课表"
                else (e as? ZFException)?.message ?: "网络错误：$e"
        }
    }
}
