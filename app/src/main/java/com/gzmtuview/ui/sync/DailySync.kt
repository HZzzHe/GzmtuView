package com.gzmtuview.ui.sync

import androidx.compose.runtime.mutableStateOf
import com.gzmtuview.GmuApplication
import com.gzmtuview.data.cache.CachedSchedule
import com.gzmtuview.data.cache.coursesChanged
import com.gzmtuview.data.cache.examListChanged
import com.gzmtuview.data.cache.gradeListChanged
import com.gzmtuview.data.color.ensureCourseColorIndex
import com.gzmtuview.data.model.Course
import com.gzmtuview.data.model.Semester
import com.gzmtuview.data.model.WeekDates
import com.gzmtuview.data.model.computeGradeStats
import com.gzmtuview.data.model.usableFor
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
 * 「今天同步过了没有」看 [com.gzmtuview.data.pref.SettingsStore.loadLastSyncDay]
 * （今天这一趟已经跑完），而不是三份缓存的 `fetchedAt`：考试/成绩接口失败是设计内的
 * 静默降级（`runCatching { … }.getOrNull() ?: return`），那两份缓存永远到不了「今天」，
 * 拿它们当门控的表现就是用户反馈的「每次打开都重新同步」。
 * 每份数据自己的 `fetchedAt` 仍是断点续传凭据：同步中途被杀进程时标记还没写下，
 * 下次打开只补没取到的部分。
 *
 * 「同步中」芯片只在**课表本身要联网**时才亮；只补考试/成绩的后台趟次不打扰用户。
 */
object DailySync {
    val phase = mutableStateOf(SyncPhase.Idle)

    /** 本次同步发生变化的数据种类；非空即提示一次，由课表页消费后清空 */
    val changed = mutableStateOf<List<String>>(emptyList())

    private var job: Job? = null

    /** 同步作业是否正在跑（考试页据此决定要不要自己再发一份请求） */
    val isRunning: Boolean get() = job?.isActive == true

    /**
     * 当天首次打开时调用，幂等：作业在跑 / 今天已经跑完都直接返回。
     * 判断只读盘不联网——所以每天第二次打开不会有芯片闪现。
     */
    fun start(app: GmuApplication) {
        if (job?.isActive == true) return
        job = app.syncScope.launch {
            val today = LocalDate.now()
            val cachedFirst = app.cache.loadCachedSemesters().firstOrNull()
            val cached = cachedFirst?.let { app.cache.load(it) }
            val newestFresh = cached?.fetchedAt == today
            // DataStore 读失败只当"没跑过"处理；setup 阶段抛异常会直接冲掉整个协程
            val doneToday =
                runCatching { app.store.loadLastSyncDay() }.getOrNull() == today.toString()

            if (doneToday && newestFresh && cachedFirst != null && cached != null) {
                // 今天这一趟已经跑完：除补齐缺失的周次日期外不发任何请求。
                // 但仍要把本地状态补齐——学期选择列表、小部件（当天新增的小部件只能靠这里拿数据）。
                hydrateSemesterList(app)
                val healed = healWeekDatesIfMissing(app, cachedFirst, cached)
                pushWidget(app, healed.result.courses, healed.weekDates)
                finishIdle()
                return@launch
            }
            // 先用缓存里最新学期预判芯片；run() 拿到真正目标学期后会再校正一次
            if (!newestFresh) phase.value = SyncPhase.Syncing
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

    private suspend fun run(app: GmuApplication, cachedFirst: Semester?, today: LocalDate) {
        val sems = try {
            app.client.fetchSemesters()
        } catch (e: Exception) {
            // 只补考试/成绩的后台趟次不因为学期接口抖动亮红芯片；课表本身要取时才算真失败
            if (phase.value == SyncPhase.Syncing) fail(e) else phase.value = SyncPhase.Idle
            return
        }
        if (sems.isEmpty()) {
            if (phase.value == SyncPhase.Syncing) fail(ZFException("未获取到学期信息"))
            else phase.value = SyncPhase.Idle
            return
        }
        val sem = sems.first()
        SchedulePageCache.semestersState.value = sems
        ExamPageCache.semestersState.value = sems
        // 用户没手动换过学期（还停在缓存里那个）才跟随网络返回的当前学期，
        // 否则同步结束不该把他正在看的学期拽走
        val shown = SchedulePageCache.selectedState.value
        if (shown == null || shown == cachedFirst) SchedulePageCache.selectedState.value = sem

        if (app.cache.load(sem)?.fetchedAt == today) {
            // 目标学期今天已经取过：本次只是补考试/成绩，不亮「同步中」
            if (phase.value == SyncPhase.Syncing) phase.value = SyncPhase.Idle
        } else {
            phase.value = SyncPhase.Syncing
        }

        val changes = mutableListOf<String>()
        // 课表那一步已经判定会话失效/断网时，别再对着同一个死会话取考试和成绩
        if (!syncSchedule(app, sem, today, changes)) return
        syncExams(app, sem, today, changes)
        syncGrades(app, sem, today, changes)
        if (changes.isNotEmpty()) changed.value = changes
        // 整趟走完才记「今天已跑完」。考试/成绩接口失败已被 runCatching 吞掉、也算走完——
        // 它们不能把门控永远卡在「没同步过」（旧实现每次打开都重跑）。
        // 放在最后而不是开头：途中被杀进程时标记还没写下，下次打开会补没取到的部分。
        runCatching { app.store.saveLastSyncDay(today.toString()) }
        finishIdle()
    }

    /** 课表 + 周次日期：同步的关键部分，失败要在芯片上说清楚。返回 false 表示这一趟应中止。 */
    private suspend fun syncSchedule(
        app: GmuApplication,
        sem: Semester,
        today: LocalDate,
        changes: MutableList<String>,
    ): Boolean {
        val cached = app.cache.load(sem)
        if (cached?.fetchedAt == today) {
            // 课表今天已经取过：不再联网，但小部件必须继续跟着走。
            // 用户当天新加的小部件、被系统或清理工具抹掉的 HomeWidgetPreferences，
            // 只能靠这条零请求路径补齐；否则课表明明好的，小部件却一直停在
            // 「打开 App 同步课表」——这正是用户 2026-10-29 反馈的症状之一。
            val healed = healWeekDatesIfMissing(app, sem, cached)
            val selected = SchedulePageCache.selectedState.value
            if (selected == null || selected == sem) {
                SchedulePageCache.weekDatesState.value = healed.weekDates
            }
            pushWidget(app, healed.result.courses, healed.weekDates)
            return true
        }
        val fetched = try {
            coroutineScope {
                val r = async { app.client.fetchSchedule(sem) }
                val w = async { app.client.fetchWeekDates() }
                r.await() to w.await()
            }
        } catch (e: Exception) {
            fail(e)
            return false
        }
        val (schedule, rawWd) = fetched
        // 周次日期没取到、或页面明确标着别的学期时，沿用同一学期上一份缓存的开学日：
        // 一次请求失败不能把已确认的日期抹掉（小部件靠它算「今天第几周」）。
        // 目标学期就是网络返回的当前学期，所以页面没带学期标识时按当前学期页接受。
        val matchWd = rawWd.usableFor(sem, sem) ?: cached?.weekDates
        app.cache.save(sem, schedule, matchWd)
        if (cached != null && coursesChanged(cached.result.courses, schedule.courses)) changes.add("课表")
        // 用户手动切到了别的学期：同步结果只喂小部件，别把他正在看的那页顶掉
        val showing = SchedulePageCache.selectedState.value
        if (showing == null || showing == sem) {
            SchedulePageCache.coursesState.value = schedule.courses
            SchedulePageCache.studentNameState.value = schedule.studentName
            SchedulePageCache.weekDatesState.value = matchWd
            SchedulePageCache.courseColorIndexState.value =
                ensureCourseColorIndex(app.store, schedule.courses)
        }
        SchedulePageCache.loadingState.value = false
        SchedulePageCache.errorState.value = null
        SchedulePageCache.loadedState.value = true
        pushWidget(app, schedule.courses, matchWd)
        return true
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

    /** 零请求路径仍然要把「学期选择」列表补全（磁盘上只有取过数的学期有文件，比服务器给的少） */
    private suspend fun hydrateSemesterList(app: GmuApplication) {
        val valid = runCatching { app.store.loadValidSemesters() }.getOrNull()
        if (!valid.isNullOrEmpty()) {
            SchedulePageCache.semestersState.value = valid
            ExamPageCache.semestersState.value = valid
        }
    }

    /**
     * 缓存里没有周次日期时补一次（只发取周次日期这一个请求）。
     * 上一次同步若碰上接口抖动/学期页不匹配，开学日会丢——后果是小部件只会显示
     * 「打开 App 同步课表」、课表页也永远停在第 1 周，不能干等到明天的下一次全量同步。
     */
    private suspend fun healWeekDatesIfMissing(
        app: GmuApplication,
        sem: Semester,
        cached: CachedSchedule,
    ): CachedSchedule {
        if (cached.weekDates != null) return cached
        val wd = runCatching { app.client.fetchWeekDates() }.getOrNull()
            ?.usableFor(sem, sem) ?: return cached
        app.cache.save(sem, cached.result, wd)
        val selected = SchedulePageCache.selectedState.value
        if (selected == null || selected == sem) {
            SchedulePageCache.weekDatesState.value = wd
        }
        return cached.copy(weekDates = wd)
    }

    /** 小部件推送的唯一收口：色表、跳过标记、调休开关都按当前持久化状态带齐 */
    private suspend fun pushWidget(app: GmuApplication, courses: List<Course>, wd: WeekDates?) {
        if (courses.isEmpty()) return
        // 读偏好是阻塞 DataStore；失败不能把同步作业（或零请求快速路径）带崩
        runCatching {
            WidgetPusher.push(
                app,
                courses,
                wd,
                ensureCourseColorIndex(app.store, courses),
                app.store.loadSkippedCourses(),
                app.store.loadHolidayAdjust(),
            )
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
