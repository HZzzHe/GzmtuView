package com.gzmtuview.ui.schedule

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.gzmtuview.GmuApplication
import com.gzmtuview.data.color.ensureCourseColorIndex
import com.gzmtuview.data.export.CsvExportResult
import com.gzmtuview.data.export.WakeupCsvExporter
import com.gzmtuview.data.model.skipKey
import com.gzmtuview.ui.glass.GlassIconButton
import com.gzmtuview.ui.glass.GlassTopBar
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedCornerStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import com.gzmtuview.ui.glass.AnchoredGlassMenu
import com.gzmtuview.ui.glass.GlassMenuCardPadding
import com.gzmtuview.ui.glass.GlassMenuCardRadius
import com.gzmtuview.ui.glass.GlassMenuCardWidth
import com.gzmtuview.ui.glass.GlassMenuChevron
import com.gzmtuview.ui.glass.GlassMenuDivider
import com.gzmtuview.ui.glass.GlassMenuRow
import com.gzmtuview.ui.glass.GlassMenuRowHeight
import com.gzmtuview.ui.glass.LocalAppBackdrop
import com.gzmtuview.ui.glass.LocalDialogBackdrop
import com.gzmtuview.ui.glass.SemesterPickerList
import com.gzmtuview.ui.glass.luminanceMapped
import com.gzmtuview.ui.glass.revealHeight
import com.gzmtuview.ui.glass.verticalTint
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Loop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.gzmtuview.widget.WidgetPusher
import com.gzmtuview.data.model.Course
import com.gzmtuview.data.model.HolidayAdjustCalendar
import com.gzmtuview.data.model.Semester
import com.gzmtuview.data.model.WeekDates
import com.gzmtuview.data.model.usableFor
import com.gzmtuview.data.net.ZFException
import com.gzmtuview.ui.ScreenScaffold
import com.gzmtuview.ui.sync.DailySync
import com.gzmtuview.ui.sync.SyncPhase
import com.gzmtuview.ui.wallpaper.Wallpaper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.gzmtuview.ui.glass.GlassDialog
import com.gzmtuview.ui.glass.Haptics
import com.gzmtuview.ui.glass.LiquidSlider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/** 课表页：PageView 滑动切周 + 学期/周次面板 + 更多菜单。 */
/**
 * 课表页跨切页状态缓存：AnimatedContent 切走时页面销毁、remember 全部重置，
 * 返回时会闪现"正在获取课表/本学期暂无课程"并重新走加载——数据恢复与回写
 * 都走这个缓存，切回时立即呈现上次数据（initLoad 见缓存时跳过重复加载）。
 */
internal object SchedulePageCache {
    // snapshot state：MainActivity 冷启动预热（IO 线程写入）自动驱动 UI 更新，
    // 页面销毁重建时数据天然保留，无需 remember 层与回写
    val semestersState = mutableStateOf<List<Semester>>(emptyList())
    val selectedState = mutableStateOf<Semester?>(null)
    val coursesState = mutableStateOf<List<Course>>(emptyList())
    val studentNameState = mutableStateOf("")
    val weekDatesState = mutableStateOf<WeekDates?>(null)
    val courseColorIndexState = mutableStateOf<Map<String, Int>?>(null)
    val loadedState = mutableStateOf(false)
    val loadingState = mutableStateOf(true)
    /** 全屏错误态文案（无缓存可展示时由每日同步写入；有缓存时错误只体现在底栏芯片上） */
    val errorState = mutableStateOf<String?>(null)
    /** PC 课表接口失联、备用通道只拿到当前学期：提示可完整收录（进程内只弹一次） */
    val fallbackNoticeState = mutableStateOf(false)

    /** 登出时清空（下个会话重新加载） */
    fun reset() {
        semestersState.value = emptyList()
        selectedState.value = null
        coursesState.value = emptyList()
        studentNameState.value = ""
        weekDatesState.value = null
        courseColorIndexState.value = null
        errorState.value = null
        fallbackNoticeState.value = false
        loadedState.value = false
        loadingState.value = true
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onLogout: () -> Unit,
    onNavigate: (Int) -> Unit = {},
    /** 每日同步判定会话已失效：芯片被点时回登录页（不清凭据，登录页会预填学号） */
    onRelogin: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val app = GmuApplication.deps(ctx)
    val scope = rememberCoroutineScope()

    // 数据状态直接委托页面缓存（跨切页/冷启动预热共享；登出时 reset）
    var semesters by SchedulePageCache.semestersState
    var selected by SchedulePageCache.selectedState
    var courses by SchedulePageCache.coursesState
    var studentName by SchedulePageCache.studentNameState
    var loading by SchedulePageCache.loadingState
    var error by SchedulePageCache.errorState
    var weekDates by SchedulePageCache.weekDatesState
    var courseColorIndex by SchedulePageCache.courseColorIndexState
    // 显示开关走 DataStore Flow 订阅：设置页切换后立即生效，无需重启
    var showStudentName by remember { mutableStateOf(false) }
    var showTeacherName by remember { mutableStateOf(true) }
    var showGridLines by remember { mutableStateOf(false) }
    var showCourseCapsule by remember { mutableStateOf(true) }
    var showWeekStepper by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        app.store.displayPrefsFlow().collect { p ->
            showStudentName = p.showStudentName
            showTeacherName = p.showTeacherName
            showGridLines = p.showGridLines
            showCourseCapsule = p.showCourseCapsule
            showWeekStepper = p.showWeekStepper
        }
    }
    // 节假日调休（beta，默认关）：开启后课表按官方放假/补班重排
    var holidayAdjust by remember { mutableStateOf(false) }
    var showWeekJump by remember { mutableStateOf(false) }
    // 周次跳转对话框：先在对话框内选周次（暂存），点确认才提交
    var pendingWeekF by remember { mutableFloatStateOf(1f) }
    var showRefreshConfirm by remember { mutableStateOf(false) }
    // 「同步失败」芯片点开才展开说明（原离线横幅的文案搬进这里）
    var showSyncFailInfo by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<CsvExportResult?>(null) }
    // 每日同步的变化清单：进程级状态，切页/返回后仍会提示一次，直到用户确认
    val pendingChanges = DailySync.changed.value
    val fallbackNotice by SchedulePageCache.fallbackNoticeState

    val weekCount = weekDates?.weekCount ?: 20
    val pagerState = rememberPagerState(pageCount = { weekCount + 1 }, initialPage = 1)
    val currentPage = pagerState.currentPage // 0=全部，1..N=周

    // 定位当周：跳转不能写在取数分支里——冷启动预热（MainActivity）、缓存秒开、
    // 当日新鲜缓存直接 return 这几条路径都不经过 fetchAndApply，写漏的表现就是
    // 「打开软件停在第 1 周」。统一由这一个 effect 消费 weekDates：任何一条路径把
    // 周次日期送进状态都会定位一次，换学期（WeekDates 换实例）同理。
    // weekDates 为 null（拿不到开学日期）时不动，留在 initialPage=1。
    LaunchedEffect(weekDates) {
        val wd = weekDates ?: return@LaunchedEffect
        pagerState.scrollToPage(wd.weekOf(LocalDate.now()))
    }

    // 打开对话框时把当前值同步为暂存初值（周次跳转：全部页归到第 1 周）
    LaunchedEffect(showWeekJump) {
        if (showWeekJump) {
            pendingWeekF = currentPage.coerceIn(1, weekCount).toFloat()
        }
    }

    suspend fun ensureColorIndex(cs: List<Course>) {
        courseColorIndex = ensureCourseColorIndex(app.store, cs)
    }

    // 「不上了」标记集（Course.skipKey()）：课表半透明 + 小部件隐藏该课该天安排
    var skippedKeys by remember {
        mutableStateOf(app.store.loadSkippedCourses())
    }

    /** 推送桌面小部件数据（课表/开学日/颜色变化后的唯一出口，失败静默）。
     *  调休开关直接同步读偏好：小部件按当天日期自行重排，必须与开关当前值一致。 */
    fun pushWidget(cs: List<Course>, wd: WeekDates?) {
        WidgetPusher.push(ctx, cs, wd, courseColorIndex, skippedKeys, app.store.loadHolidayAdjust())
    }

    /** 标记/撤销某节课「不上了」（仅该日期当天）：持久化 + 半透明 + 小部件隐藏该课当天安排。 */
    fun toggleSkip(c: Course, date: LocalDate) {
        val key = c.skipKey(date)
        val next = if (key in skippedKeys) skippedKeys - key else skippedKeys + key
        skippedKeys = next
        scope.launch { app.store.saveSkippedCourses(next) }
        pushWidget(courses, weekDates)
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    suspend fun fetchAndApply(sem: Semester) {
        val prevWd = app.cache.load(sem)?.weekDates
        val (result, wd) = coroutineScope {
            val r = async { app.client.fetchSchedule(sem) }
            val w = async { app.client.fetchWeekDates() }
            r.await() to w.await()
        }
        // 诊断：教务实际返回的课表内容（课程数 + 全部课名，用于远程定位数据问题）
        android.util.Log.d(
            "ScheduleDiag",
            "fetchSchedule(${sem.label}) -> ${result.courses.size} 门: " +
                result.courses.joinToString("/") { "${it.name}(周${it.dayOfWeek}第${it.sections.firstOrNull()}节)" },
        )
        // 周次日期取失败/页面标着别的学期时沿用同学期上一份缓存：
        // 一次请求抖动不能把开学日抹掉（小部件与自动定位都靠它）
        val matchWd = wd.usableFor(sem, SchedulePageCache.semestersState.value.firstOrNull())
            ?: prevWd
        app.cache.save(sem, result, matchWd)
        courses = result.courses
        studentName = result.studentName
        weekDates = matchWd
        loading = false
        error = null
        ensureColorIndex(result.courses)
        pushWidget(result.courses, matchWd)
    }

    suspend fun loadSchedule(forceRefresh: Boolean = false) {
        val sem = selected ?: return
        val cached = app.cache.load(sem)
        if (!forceRefresh && cached != null && cached.fetchedAt == LocalDate.now()) {
            courses = cached.result.courses
            studentName = cached.result.studentName
            weekDates = cached.weekDates
            loading = false
            error = null
            ensureColorIndex(cached.result.courses)
            pushWidget(cached.result.courses, cached.weekDates)
            return
        }
        if (!forceRefresh && cached != null) {
            // 跨天旧缓存：先展示，后台更新
            courses = cached.result.courses
            studentName = cached.result.studentName
            weekDates = cached.weekDates
            loading = false
            error = null
            ensureColorIndex(cached.result.courses)
            pushWidget(cached.result.courses, cached.weekDates)
            scope.launch { runCatching { fetchAndApply(sem) } }
            return
        }
        loading = true
        error = null
        try {
            fetchAndApply(sem)
        } catch (e: ZFException) {
            loading = false
            error = e.message
        } catch (e: Exception) {
            loading = false
            error = "网络错误：$e"
        }
    }

    /**
     * 只读盘：把该学期的磁盘缓存铺到页面上。返回值表示"读到过缓存文件"，
     * 与"有没有课"无关（空课表也是有效数据，不该继续转圈）。
     * 联网一律交给 [com.gzmtuview.ui.sync.DailySync]，页面不再自己发请求。
     */
    suspend fun hydrateFromCache(): Boolean {
        val sems = app.cache.loadCachedSemestersWithData()
            .ifEmpty { app.cache.loadCachedSemesters() }
        if (sems.isEmpty()) return false
        semesters = sems
        val sem = selected ?: sems.first().also { selected = it }
        val cached = app.cache.load(sem) ?: return false
        courses = cached.result.courses
        studentName = cached.result.studentName
        weekDates = cached.weekDates
        ensureColorIndex(cached.result.courses)
        pushWidget(cached.result.courses, cached.weekDates)
        return true
    }

    suspend fun initLoad() {
        holidayAdjust = app.store.loadHolidayAdjust()
        // 切页返回：数据已从页面缓存恢复，所见即缓存
        if (SchedulePageCache.loadedState.value && courses.isNotEmpty()) {
            // 冷启动预热只搬课表与 loaded，颜色映射可能为空；不补的话首屏先画课名哈希
            // 兜底色，等每日同步写回映射色时整页换色
            if (courseColorIndex == null) ensureColorIndex(courses)
            loading = false
            return
        }
        // 当天首次打开不再全屏等课表：有旧缓存就先摆旧课表，同步进度落在底栏槽的芯片上。
        // 只有"连一个缓存文件都没有"（新装/清空数据后首启）才留在全屏加载，
        // 由每日同步决定结局——成功写入课程或写入全屏错误态。
        if (hydrateFromCache()) {
            SchedulePageCache.loadedState.value = true
            error = null
            loading = false
        } else {
            loading = DailySync.isRunning
        }
    }


    LaunchedEffect(Unit) { initLoad() }

    // —— "更多"菜单状态（玻璃浮层挂 AppScreen overlay，主窗口坐标，backdrop 取色正确）——
    var menuOpen by remember { mutableStateOf(false) }
    // 打开/收起动画期间真实按钮都不绘制：按钮替身（放大淡出、icon 交接）由菜单浮层负责。
    // 收起时按钮一旦提前回到卡片下面，玻璃按钮会被卡片采样成一块不透明白底。
    var menuClosing by remember { mutableStateOf(false) }
    // 兜底：菜单浮层若被宿主摘除（设置覆盖页会抑制父页浮层），收起协程随之取消、
    // onClosed 不再回调；超时后无条件交还按钮，避免返回课表后「更多」一直不出现。
    // 2s 而非 500ms：收起动画会被系统 animator_duration_scale 成倍放慢，
    // 500ms 会在慢速/无障碍倍率下提前放出真实按钮，动画中露出两个⋮。
    LaunchedEffect(menuClosing) {
        if (menuClosing) {
            delay(2000)
            menuClosing = false
        }
    }

    // —— 玻璃对话框（手动刷新确认，官方 DialogContent 配方）——
    var refreshShow by remember { mutableStateOf(false) }
    // 课程详情对话框的选中课程与所在日期（ScheduleGrid 点击回调提升；"全部"页日期为 null）
    var detailCourse by remember { mutableStateOf<com.gzmtuview.data.model.Course?>(null) }
    var detailDate by remember { mutableStateOf<LocalDate?>(null) }
    var refreshShown by remember { mutableStateOf(false) }
    val refreshPop = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(refreshShow) {
        if (refreshShow) {
            refreshShown = true
            refreshPop.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 500f))
        } else if (refreshShown) {
            refreshPop.animateTo(0f, tween(durationMillis = 120))
            refreshShown = false
        }
    }

    ScreenScaffold(
        // 周次步进器 / 同步芯片：挂到底栏同层浮层槽（捕获层之外），内部玻璃才能折射真实壁纸，
        // 而不是像页面 body 内的玻璃只能折射纯色（用户反馈「纯黑/纯白」的根因）。
        // 右手拇指位切周，省去「点周次→滑块→确定」三步；长按任一半开「周次跳转」对话框。
        // 窄屏三档自适应：与底栏同行全尺寸 → 同行等比缩小（≥minStepScale）
        // → 缩到阈值仍与底栏重叠则整体抬到底栏正上方（恢复全尺寸）。
        // 每日同步进行中时，同一个位置、同一副尺寸换成「同步中」芯片——
        // 加载提示从全屏页缩成角落一颗，且与「显示周次步进器」开关无关（一定要看得见）。
        bottomOverlay = {
            val phase = DailySync.phase.value
            val chipLabel = when (phase) {
                SyncPhase.Syncing -> "同步中"
                SyncPhase.NeedLogin -> "重新登录"
                SyncPhase.Failed -> "同步失败"
                SyncPhase.Idle -> null
            }
            if (chipLabel != null || showWeekStepper) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    val endMargin = 18.dp
                    val gap = 8.dp
                    val fullW = stepHalfWidth * 2
                    // 同行可容纳的步进器最大宽：页宽 - 右缘 - 间隙 - 底栏右缘
                    val inlineMax = maxWidth - endMargin - gap - bottomBarRightEdge
                    val (scale, lifted) = when {
                        inlineMax >= fullW -> 1f to false
                        inlineMax >= fullW * minStepScale -> (inlineMax / fullW) to false
                        else -> 1f to true
                    }
                    val padBottom = if (lifted) 16.dp + stepBarHeight + 8.dp else 16.dp
                    if (chipLabel != null) {
                        SyncChip(
                            label = chipLabel,
                            icon = when (phase) {
                                SyncPhase.NeedLogin -> Icons.Outlined.Person
                                SyncPhase.Failed -> Icons.Outlined.ErrorOutline
                                else -> Icons.Rounded.Loop
                            },
                            ink = if (phase == SyncPhase.Syncing) SyncBlue else SyncRed,
                            // 同步中：图标旋转 + 底色忽明忽暗；失败/重新登录是常亮警示红
                            active = phase == SyncPhase.Syncing,
                            onClick = when (phase) {
                                // 会话失效点它去重新登录；同步失败点它展开说明并按需再试
                                SyncPhase.NeedLogin -> onRelogin
                                SyncPhase.Failed -> ({ showSyncFailInfo = true })
                                else -> null
                            },
                            scale = scale,
                            modifier = Modifier.padding(end = endMargin, bottom = padBottom),
                        )
                    } else {
                        WeekStepper(
                            canPrev = currentPage > 1,
                            canNext = currentPage < weekCount,
                            onPrev = { scope.launch { pagerState.animateScrollToPage((currentPage - 1).coerceAtLeast(1)) } },
                            onNext = { scope.launch { pagerState.animateScrollToPage((currentPage + 1).coerceAtMost(weekCount)) } },
                            onJump = { if (semesters.isNotEmpty()) showWeekJump = true },
                            scale = scale,
                            modifier = Modifier.padding(
                                end = endMargin,
                                bottom = padBottom,
                            ),
                        )
                    }
                }
            }
        },
        // 菜单玻璃浮层（主窗口坐标；Popup 子窗口内 drawBackdrop 采样坐标会错位）
        overlay = {
            ScheduleMoreMenu(
                open = menuOpen,
                items = listOf(
                    ScheduleMenuItem("设置") { onNavigate(3) },
                    ScheduleMenuItem("导出课表 CSV") {
                        if (courses.isEmpty()) {
                            toast("当前暂无课表可导出")
                        } else {
                            scope.launch {
                                val out = WakeupCsvExporter.export(ctx, courses)
                                if (out == null) {
                                    toast("课表导出失败")
                                } else {
                                    exportResult = out
                                }
                            }
                        }
                    },
                    ScheduleMenuItem("学期选择", expandable = true) {},
                    // 条目顺序：搜索夹在学期选择与手动刷新之间；搜索为独立覆盖页（dest=4）
                    ScheduleMenuItem("搜索") { onNavigate(4) },
                    ScheduleMenuItem("手动刷新") { refreshShow = true },
                ),
                semesters = semesters,
                selectedSemester = selected,
                onSemesterSelect = { sem ->
                    if (menuOpen) {
                        menuOpen = false
                        menuClosing = true
                    }
                    if (sem != selected) {
                        selected = sem
                        scope.launch { loadSchedule() } // 换学期重载（内部可能自动定位当前周）
                    }
                },
                onDismiss = {
                    if (menuOpen) {
                        menuOpen = false
                        menuClosing = true
                    }
                },
                // 卡片完整缩回按钮位置后才让真实按钮出现，最后一帧两者尺寸/位置/icon 对齐
                onClosed = { menuClosing = false },
            )
        // 手动刷新确认（玻璃对话框，官方 DialogContent 配方）
        GlassDialog(
            visible = refreshShow,
            onDismissRequest = { refreshShow = false },
            title = "确认刷新？",
            textString = "刷新会重新访问教务系统获取最新数据。请勿频繁刷新，以免被服务器判定为异常行为。",
            confirmText = "确认刷新",
            onConfirm = {
                refreshShow = false
                scope.launch { loadSchedule(forceRefresh = true) }
            },
            dismissText = "取消",
            onDismiss = { refreshShow = false },
        )
        // 每日同步发现数据变了：玻璃对话框（原来的 Toast 会被切页销毁、也压不住底栏）
        GlassDialog(
            visible = pendingChanges.isNotEmpty(),
            onDismissRequest = { DailySync.changed.value = emptyList() },
            title = "数据有更新",
            textString = "本次同步检测到${pendingChanges.joinToString("、")}发生变化，" +
                "请留意最新的课程与安排。",
            confirmText = "知道了",
            onConfirm = { DailySync.changed.value = emptyList() },
            dismissText = null,
            onDismiss = { DailySync.changed.value = emptyList() },
        )
        // 备用通道只拿到当前学期：提示可完整收录补齐历史学期（等课表加载完再弹，不挡加载态）
        GlassDialog(
            visible = fallbackNotice && !loading,
            onDismissRequest = { SchedulePageCache.fallbackNoticeState.value = false },
            title = "学期列表不完整",
            textString = "教务 PC 课表接口暂不可用，已改用手机版备用通道，目前只能获取当前学期。" +
                "完整收录可补齐其他学期的课程、考试与成绩。",
            confirmText = "去完整收录",
            onConfirm = {
                SchedulePageCache.fallbackNoticeState.value = false
                onNavigate(4)
            },
            dismissText = "知道了",
            onDismiss = { SchedulePageCache.fallbackNoticeState.value = false },
        )
        // 「同步失败」芯片点开：说清楚现在看到的是缓存，并按需再跑一次
        // （原顶部离线横幅的文案搬进这里，横幅本身已取消——与芯片重复提示）
        GlassDialog(
            visible = showSyncFailInfo,
            onDismissRequest = { showSyncFailInfo = false },
            title = "同步失败",
            textString = "离线模式：展示本地缓存数据，联网后自动更新。",
            confirmText = "再试一次",
            onConfirm = {
                showSyncFailInfo = false
                DailySync.start(app)
            },
            dismissText = "关闭",
            onDismiss = { showSyncFailInfo = false },
        )
        // 周次跳转（液态玻璃对话框，两步式：先选周次，点「跳转」才提交）
        GlassDialog(
            visible = showWeekJump,
            onDismissRequest = { showWeekJump = false },
            title = "周次跳转",
            text = {
                val caption = MaterialTheme.colorScheme.onSurfaceVariant
                val week = pendingWeekF.roundToInt().coerceIn(1, weekCount)
                Text(
                    "周次 · 第 $week 周",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = caption,
                )
                Spacer(Modifier.height(16.dp))
                // 官方 LiquidSlider 滑块（折射对话框面板表面纯色，
                // 不折射主窗口内容——避免课表网格线/课程定位辅助线被折射进滑块）
                LiquidSlider(
                    value = { pendingWeekF },
                    onValueChange = { v ->
                        // 每跨一整周振一次（与上方「第 N 周」读数同档位，连续拖动不会每像素都响）
                        val prev = pendingWeekF.roundToInt().coerceIn(1, weekCount)
                        val next = v.roundToInt().coerceIn(1, weekCount)
                        if (next != prev) Haptics.tick()
                        pendingWeekF = v
                    },
                    valueRange = 1f..weekCount.toFloat(),
                    visibilityThreshold = 0.01f,
                    backdrop = LocalDialogBackdrop.current ?: LocalAppBackdrop.current,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                )
            },
            confirmText = "跳转",
            onConfirm = {
                showWeekJump = false
                val targetWeek = pendingWeekF.roundToInt().coerceIn(1, weekCount)
                scope.launch { pagerState.scrollToPage(targetWeek) }
            },
            dismissText = "取消",
            onDismiss = { showWeekJump = false },
        )
    exportResult?.let { res ->
        GlassDialog(
            visible = true,
            onDismissRequest = { exportResult = null },
            title = "课表已导出",
            text = {
                Column {
                    Text(
                        "已保存为 WakeUp 课程表兼容 CSV，可通过文件管理器或分享发送到 WakeUp 导入：",
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.68f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(res.displayPath, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmText = "分享",
            onConfirm = {
                exportResult = null
                val uri = res.shareUri
                if (uri != null) {
                    runCatching {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(android.content.Intent.createChooser(intent, "分享课表 CSV"))
                    }.onFailure { toast("无法调起分享") }
                }
            },
            dismissText = "复制路径",
            onDismiss = {
                exportResult = null
                runCatching {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("路径", res.displayPath))
                    toast("文件路径已复制")
                }
            },
        )
    }

    // 课程详情（玻璃对话框，从 ScheduleGrid 提升到 overlay 主窗口坐标）
    detailCourse?.let { c ->
        val date = detailDate
        val isSkipped = date != null && c.skipKey(date) in skippedKeys
        // 调休开启时该日可能整体放假/补班：手动「不上了」在放假日已无意义
        val holidayName = date?.let { if (holidayAdjust) HolidayAdjustCalendar.holidayName(it) else null }
        val makeup = date?.let { if (holidayAdjust) HolidayAdjustCalendar.makeupOf(it) else null }
        GlassDialog(
            visible = true,
            onDismissRequest = { detailCourse = null; detailDate = null },
            title = c.name,
            text = {
                val rows = mutableListOf(
                    "教师" to c.teacher,
                    "教室" to listOf(c.location, c.campus).filter { it.isNotEmpty() }.joinToString(" "),
                    "节次" to c.rawTime,
                    "周次" to c.weeksText,
                    "教学班" to c.className,
                    "课程性质" to c.type,
                    "学分" to (if (c.credit > 0) c.credit.toString() else ""),
                )
                // 按天标记：明示「不上了」只作用于这一次课
                if (date != null) {
                    rows.add(0, "本次日期" to "${date.monthValue}月${date.dayOfMonth}日")
                }
                when {
                    holidayName != null -> rows.add(0, "当天安排" to "${holidayName}放假，本节停课")
                    makeup != null -> {
                        val src = makeup.replaysDate
                        rows.add(
                            0,
                            "当天安排" to "调休补班，补 ${src.monthValue}月${src.dayOfMonth}日" +
                                "（周${"一二三四五六日"[src.dayOfWeek.value - 1]}）的课",
                        )
                    }
                }
                rows.filter { it.second.isNotEmpty() }.forEach { (label, value) ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.width(72.dp))
                        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
            // 「不上了」：仅该日期当天半透明 + 小部件不显示；再次点击同卡可「要上课」恢复。
            // "全部"页无具体日期，无法确定是哪一次课 → 不提供标记按钮
            neutralText = if (date == null || holidayName != null) null else if (isSkipped) "要上课" else "不上了",
            neutralColor = if (isSkipped) Color(0xFF34B87A) else Color(0xFFFC3C57),
            onNeutral = {
                if (date != null) toggleSkip(c, date)
                detailCourse = null
                detailDate = null
            },
            confirmText = "关闭",
            onConfirm = { detailCourse = null; detailDate = null },
        )
    }        },
    ) {
            Column(Modifier.fillMaxSize()) {
                // 页内常驻顶栏（随页面参与切页/push 动画；玻璃按钮折射顶栏局部内容）。
                // 周次选择器与学期标题同行（titleTrailing），对齐紧凑顶栏
                GlassTopBar(
                    title = selected?.label ?: "课表",
                    // 壁纸显示期间顶栏不再铺不透明底：否则课表上方会出现一条实心色带
                    backgroundColor = if (Wallpaper.shown) Color.Transparent else MaterialTheme.colorScheme.surface,
                    // 顶栏直接落在壁纸上：标题/后缀按背板亮度自适应取深墨或亮墨
                    contentColor = Wallpaper.adaptiveInk(MaterialTheme.colorScheme.onSurface),
                    titleTrailing = {
                        Row(
                            Modifier
                                .clickable { if (semesters.isNotEmpty()) showWeekJump = true }
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val sub = buildString {
                                if (showStudentName && studentName.isNotEmpty()) {
                                    append(studentName); append(" · ")
                                }
                                append(if (currentPage == 0) "全部课程" else "第 $currentPage 周")
                            }
                            val subInk = Wallpaper.adaptiveInk(MaterialTheme.colorScheme.onSurfaceVariant, variant = true)
                            Text(sub, fontSize = 12.sp, color = subInk)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.UnfoldMore, null, Modifier.size(14.dp), tint = subInk)
                        }
                    },
                    // 与系统相册对齐：右侧按钮离屏幕边缘留 18dp（默认 8dp 太贴边）
                    trailingEndPadding = 18.dp,
                ) {
                    // 打开/收起期间隐藏真实按钮：玻璃按钮位于捕获子树内，给它套 alpha/scale 层会在
                    // HyperOS 上按矩形合成（按钮阴影变方、菜单里出现按钮区接缝）；
                    // 收起时它还会被尚未缩完的卡片采样成一块不透明白底。
                    // 「⋮」的淡出/浮现由菜单卡片自己画（overlay，捕获层之外），见 ScheduleMoreMenu。
                    if (!menuOpen && !menuClosing) {
                        GlassIconButton(Icons.Filled.MoreHoriz, "更多", onClick = { menuOpen = true })
                    }
                }
                // 离线不再有页内横幅：当日同步失败只由底栏那颗「同步失败」芯片说明，
                // 点它才展开说明对话框（避免横幅与芯片同时出现）。
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        loading -> Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("正在获取课表，首次可能需检测可用学期...")
                        }
                        error != null -> Column(
                            Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(12.dp))
                            Text(error!!, textAlign = TextAlign.Center, fontSize = 14.sp)
                            Spacer(Modifier.height(16.dp))
                            FilledTonalButton(onClick = {
                                error = null
                                loading = true
                                DailySync.start(app)
                            }) { Text("重试") }
                        }
                        courses.isEmpty() -> Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Outlined.EventBusy, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(12.dp))
                            Text("本学期暂无课程")
                        }
                        else -> {
                            val wd = weekDates
                            Box(Modifier.fillMaxSize()) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                ) { page ->
                                    if (page == 0) {
                                        ScheduleGrid(
                                            courses = courses,
                                            showTeacherName = showTeacherName,
                                            showGridLines = showGridLines,
                                            showCourseCapsule = showCourseCapsule,
                                            courseColorIndex = courseColorIndex,
                                            skippedKeys = skippedKeys,
                                            onCourseDetail = { c, d -> detailCourse = c; detailDate = d },
                                        )
                                    } else {
                                        val dates = wd?.let { (1..7).map { d -> it.dateOf(page, d) } }
                                        ScheduleGrid(
                                            courses = courses,
                                            currentWeek = page,
                                            currentWeekDates = dates,
                                            showTeacherName = showTeacherName,
                                            showGridLines = showGridLines,
                                            showCourseCapsule = showCourseCapsule,
                                            courseColorIndex = courseColorIndex,
                                            skippedKeys = skippedKeys,
                                            holidayAdjust = holidayAdjust,
                                            weekDates = wd,
                                            onCourseDetail = { c, d -> detailCourse = c; detailDate = d },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}

/** 课表页「更多」菜单项；动画与排版统一由 [ScheduleMoreMenu] 负责。 */
private class ScheduleMenuItem(
    val label: String,
    /** true：不执行 [action]，点击展开下方二级栏（如学期选择） */
    val expandable: Boolean = false,
    val action: () -> Unit,
)

/** 二级栏浮起时主菜单的缩小比例（相册实测约 94.5%，右缘/上沿不动） */
private const val MenuSubShrink = 0.05f

/** 二级栏面底色（近不透明，隔断被盖住的主菜单行，避免深色模式下透出） */
private const val MenuSubFill = 0.95f

/** 二级栏上沿比触发行多出的呼吸位：卡片上移同样的距离，标题行位置不动 */
private val MenuSubTopCap = 8.dp

/**
 * 「更多」菜单：对齐系统相册的容器展开/收回（卡片实现见 [AnchoredGlassMenu]）。
 * - 打开：「⋮」在按钮处淡出，同时卡片以 36dp 的圆弹出，停顿一帧后再长成菜单；
 * - 收回：卡片缩回 36dp 的圆，「⋮」在中段于卡面中央渐显并跟着卡片中心走，收完由真实按钮接走。
 * 全程只有卡片这一个白色表面，**不再绘制不透明的按钮替身**——替身是方底 + 自带阴影，
 * 正是用户看到的「白块 / 方形阴影」；按钮动效全部由卡片本身承担。
 */
@Composable
private fun ScheduleMoreMenu(
    open: Boolean,
    items: List<ScheduleMenuItem>,
    semesters: List<Semester>,
    selectedSemester: Semester?,
    onSemesterSelect: (Semester) -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    // 二级栏（学期选择）：叠在「学期选择」行上展开（相册里 视图 的二级卡就是盖住该行
    // 及其下的兄弟行，而不是另起一张隔开的下拉卡）。锚点由该行布局实测得到。
    var subOpen by remember { mutableStateOf(false) }
    var subVisible by remember { mutableStateOf(false) }
    // 触发行在卡片内的 y（Dp）；二级卡标题行与该行上沿重合（两边同内衬、同行高）
    var subAnchorY by remember { mutableStateOf(0.dp) }
    val subProgress = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(subOpen) {
        if (subOpen) subVisible = true
        subProgress.animateTo(
            if (subOpen) 1f else 0f,
            tween(
                durationMillis = if (subOpen) 240 else 180,
                easing = FastOutSlowInEasing,
            ),
        )
        if (!subOpen) subVisible = false
    }
    // 菜单每次打开时重置二级栏；关闭时二级栏随菜单一起离场（不做自己的收起动画）
    LaunchedEffect(open) {
        if (open) {
            subOpen = false
            subVisible = false
            subProgress.snapTo(0f)
        } else {
            subOpen = false
        }
    }

    AnchoredGlassMenu(
        open = open,
        icon = Icons.Filled.MoreHoriz,
        // 二级栏显示期间（含收起动画），点卡内/卡外都只收二级栏；
        // 收完后的下一次点击才恢复原有语义（点外部关菜单、点条目进页面）
        onDismiss = {
            if (subVisible) subOpen = false else onDismiss()
        },
        onClosed = onClosed,
        blockContentClicks = subVisible,
        shrink = { 1f - MenuSubShrink * subProgress.value },
        content = {
            // 菜单标题行：点它收起菜单（相册二级栏的返回语义）
            GlassMenuRow(label = "菜单") { onDismiss() }
            GlassMenuDivider()
            items.forEach { item ->
                val trailing: (@Composable () -> Unit)? =
                    if (item.expandable) {
                        { GlassMenuChevron(expanded = subOpen) }
                    } else {
                        null
                    }
                GlassMenuRow(
                    label = item.label,
                    modifier = if (item.expandable) {
                        Modifier.onGloballyPositioned { coords ->
                            val y = with(density) { coords.positionInParent().y.toDp() }
                            if (y != subAnchorY) subAnchorY = y
                        }
                    } else {
                        Modifier
                    },
                    trailing = trailing,
                ) {
                    if (item.expandable) {
                        subOpen = !subOpen
                    } else {
                        onDismiss()
                        item.action()
                    }
                }
            }
        },
        secondary = {
            // 二级栏：叠在「学期选择」行上展开（相册二级卡同款——盖住该行与其下的
            // 兄弟行，而不是在整张菜单下方另起一张）。上沿比触发行多留 8dp 呼吸位，
            // 卡片同步上移 8dp，标题文字仍落在被盖住的那一行上，不会跳。
            if (open && subVisible) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        // 固定在被点行原来的位置：主菜单缩小是往右下退，二级卡不动，
                        // 标题文字才能与点击前那行逐像素对齐，不会在展开瞬间位移。
                        .offset {
                            IntOffset(0, (subAnchorY - MenuSubTopCap).roundToPx())
                        },
                ) {
                    SemesterSubmenu(
                        semesters = semesters,
                        selected = selectedSemester,
                        onSelect = onSemesterSelect,
                        onCollapse = { subOpen = false },
                        progress = { subProgress.value },
                    )
                }
            }
        },
    )
}

/**
 * 「更多」菜单的二级栏：学期列表，当前学期蓝字标记，点选即切换。
 * 与主菜单同一套玻璃配方，但面底更实（隔断被盖住的主菜单行）、阴影恒定：
 * 相册里二级卡从出现起就是一张完整的浮起卡片，不随高度「长阴影」。
 */
@Composable
private fun SemesterSubmenu(
    semesters: List<Semester>,
    selected: Semester?,
    onSelect: (Semester) -> Unit,
    onCollapse: () -> Unit,
    progress: () -> Float,
) {
    val scheme = MaterialTheme.colorScheme
    val surfaceSolid = scheme.surface
    val density = LocalDensity.current
    // 标题行实测高度：决定「标题卡」的最小高度（系统字体放大时行高会变）
    var headerH by remember { mutableStateOf(GlassMenuRowHeight) }
    val minCardH = GlassMenuCardPadding + MenuSubTopCap + headerH + GlassMenuCardPadding
    Box(
        Modifier
            .width(GlassMenuCardWidth.dp)
            .drawBackdrop(
                backdrop = LocalAppBackdrop.current,
                shape = { RoundedCornerShape(GlassMenuCardRadius.dp) },
                effects = {
                    verticalTint(surfaceSolid, 0.50f, 300 * density.density)
                    vibrancy()
                    blur(22f.dp.toPx())
                    // 二级卡不画透镜高光：叠加在主菜单上时，深色模式里那圈高光会被
                    // 看成「学期选择上方裂开的一条线」。层次交给面底深浅 + 阴影。
                },
                shadow = {
                    Shadow(
                        radius = 18.dp,
                        offset = DpOffset(0.dp, 5.dp),
                        color = Color.Black.copy(alpha = 0.16f),
                    )
                },
                onDrawSurface = {
                    drawRect(surfaceSolid.copy(alpha = MenuSubFill))
                },
            )
            // 玻璃与阴影不受裁切；只裁内容。最小高度 = 标题卡（相册先出现完整标题卡，
            // 再从下方长出列表），不会从 0 高度露出一条线。
            .clipToBounds()
            .revealHeight(progress, minCardH),
    ) {
        Column(Modifier.padding(GlassMenuCardPadding).padding(top = MenuSubTopCap)) {
            // 卡内标题 = 被盖住的那一行：无图标、同字号/内衬，箭头朝上，点它收回二级栏
            GlassMenuRow(
                label = "学期选择",
                modifier = Modifier.onSizeChanged {
                    headerH = with(density) { it.height.toDp() }
                },
                trailing = { GlassMenuChevron(expanded = true) },
            ) { onCollapse() }
            GlassMenuDivider()
            SemesterPickerList(
                semesters = semesters,
                selected = selected,
                accent = SyncBlue,
                onSelect = onSelect,
            )
        }
    }
}


/** 与玻璃底栏同高同排：AppShell 里 LiquidBottomTabs 高 = 64dp × heightScale(0.81) */
private val stepBarHeight = (64f * 0.81f).dp

/**
 * 半宽 44dp（总宽 88dp）。比端头圆半径窄，所以箭头不能按半宽居中：
 * 半宽中心在 22dp，端头圆心在 h/2=25.92dp，需各自向外侧偏移 (h-半宽)/2。
 */
private val stepHalfWidth = 44.dp

/** 按动时箭头顺着自己的指向再顶出去多远（点按动效的位移量，随 scale 等比） */
private val stepArrowNudge = 1.5.dp

/** 玻璃底栏右缘（AppShell：start 18dp + 3 tab×78dp + 8×0.81 内衬）；步进器同行避让基准 */
private val bottomBarRightEdge = (18 + 3 * 78 + 8 * 0.81f).dp

/** 步进器与底栏同行时等比缩小的下限；缩到该比例仍重叠则整体抬到底栏正上方 */
private val minStepScale = 0.82f

/**
 * 周次步进器：一颗玻璃胶囊，左半切上一周、右半切下一周（放在课表右下角拇指位）。
 *
 * 折射源用真实 [LocalAppBackdrop]（同 LiquidBottomTabs / 更多菜单）：本控件挂在
 * AppShell 的 bottomOverlay 槽，位于全局捕获层【之外】，所以能采样真实背板折射壁纸。
 * （若放回页面 body 内，采样祖先层会渲染树自引用 → HyperOS 3 / RenderThread SIGSEGV，
 *  且 body 内的玻璃只能折射纯色，正是之前「纯黑/纯白」的根因。）
 */
@Composable
private fun WeekStepper(
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: () -> Unit,
    /** 等比缩放（窄屏与底栏同行时缩小）；1f = 与底栏同高同比例 */
    scale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // 真实背板：控件已在捕获层之外，采样祖先层安全，折射出壁纸/页面内容
    val backdrop = LocalAppBackdrop.current
    // 本控件只出现在课表页，而课表页只要开了壁纸就一定在底栏区域画壁纸，
    // 所以底色/墨色按壁纸下缘亮度定深浅（同底栏），无壁纸时仍是主题色。
    val chromeInk = Wallpaper.chromeInk(scheme.onSurface)
    val glassFill = rememberUpdatedState(
        Wallpaper.chromeContainer(scheme.surface).copy(alpha = 0.35f),
    )
    val iconColor = chromeInk
    val pressedBg = chromeInk.copy(alpha = 0.10f)
    val disabledAlpha = 0.28f
    // 等比缩放后的几何：高度/半宽/箭头同心偏移/按动位移都随 scale，保持端头圆同心关系
    val barH = stepBarHeight * scale
    val halfW = stepHalfWidth * scale
    val arrowOffset = (barH - halfW) / 2
    val nudge = stepArrowNudge * scale
    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                // Circular → Outline.Rounded 走 graphicsLayer 裁剪，胶囊边不发锯齿
                shape = { Capsule(style = RoundedCornerStyle.Circular) },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                shadow = { Shadow(radius = 6f.dp, offset = DpOffset(0f.dp, 2f.dp)) },
                onDrawSurface = { drawRect(glassFill.value) },
            )
            .height(barH)
            .width(halfW * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepArrow(
            Icons.Outlined.ChevronLeft, "上一周", canPrev, iconColor, pressedBg, disabledAlpha,
            contentOffset = arrowOffset,
            pressDx = -nudge,
            onClick = onPrev, onLongClick = onJump,
            scale = scale,
            modifier = Modifier.fillMaxHeight().width(halfW),
        )
        StepArrow(
            Icons.Outlined.ChevronRight, "下一周", canNext, iconColor, pressedBg, disabledAlpha,
            contentOffset = -arrowOffset,
            pressDx = nudge,
            onClick = onNext, onLongClick = onJump,
            scale = scale,
            modifier = Modifier.fillMaxHeight().width(halfW),
        )
    }
}

/** 航海蓝：与考试页「考试时间」按钮、学期选择高亮同色（同步中芯片的底色） */
private val SyncBlue = Color(0xFF3A8DCC)

/** 警示红：与考试页「回顾中」按钮同色（同步失败 / 重新登录芯片的底色） */
private val SyncRed = Color(0xFFFC3C57)

/**
 * 同步芯片：与 [WeekStepper] 同位同尺寸（88dp×步进器高，随同一个 scale 等比），
 * 把「当天首次打开要等的全屏加载页」缩成角落一颗提示。
 *
 * 表面一律用官方着色按钮配方（Hue 混合 + 着色覆盖），与考试页的「考试时间」/「回顾中」
 * 同材质：着色覆盖率作用在**折射+ Vibrancy 之后的玻璃**上，所以浅色/深色下的明暗
 * 天然跟随系统主题，不需要再写一套按主题分支的颜色。
 * - `active=true`（同步中·航海蓝）：覆盖率在 0.62↔0.92 之间往复呼吸（忽明忽暗）；
 * - `active=false`（失败/重新登录·警示红）：固定 0.75，与「回顾中」逐参数一致。
 *
 * 呼吸只改**着色矩形的颜色 alpha**，不碰图层 alpha/缩放；图标旋转同样是单纯变换
 * （[androidx.compose.ui.draw.rotate]）——这里套的是玻璃本体，图层级动画会触发
 * HyperOS 3 丢层。非同步中时不读 `wash`，玻璃本体就不会逐帧重绘。
 */
@Composable
private fun SyncChip(
    label: String,
    icon: ImageVector,
    ink: Color,
    active: Boolean,
    onClick: (() -> Unit)?,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalAppBackdrop.current
    // 画表面时必须现读的取值一律装进 State：组合器会把「捕获组合期局部值」的 lambda
    // 连同首次取值一起缓存（§6.4 红线 9，周次步进器换主题底色不跟随就是这么来的）
    val inkNow = rememberUpdatedState(ink)
    val activeNow = rememberUpdatedState(active)
    val spin = rememberInfiniteTransition(label = "syncSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "syncAngle",
    )
    // 一次明→暗 1400ms，Reverse 折回去：比旋转慢半档，读起来是「在忙」而不是闪烁。
    // 下限 0.62 而不是更低：白字要始终压得住（考试页选中态是恒定 0.75，这里绕它摆动）
    val wash by spin.animateFloat(
        initialValue = 0.62f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "syncWash",
    )
    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule(style = RoundedCornerStyle.Circular) },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                shadow = { Shadow(radius = 6f.dp, offset = DpOffset(0f.dp, 2f.dp)) },
                onDrawSurface = {
                    val c = inkNow.value
                    drawRect(c, blendMode = BlendMode.Hue)
                    drawRect(c.copy(alpha = if (activeNow.value) wash else 0.75f))
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = null, indication = null) { onClick() }
                } else {
                    Modifier
                },
            )
            .height(stepBarHeight * scale)
            .width(stepHalfWidth * 2 * scale),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            Modifier.size(18.dp * scale).rotate(if (active) angle else 0f),
            tint = Color.White,
        )
        Spacer(Modifier.width(5.dp * scale))
        Text(label, fontSize = (11 * scale).sp, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun StepArrow(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    iconColor: Color,
    pressedBg: Color,
    disabledAlpha: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    contentOffset: Dp,
    pressDx: Dp,
    /** 等比缩放（跟随 WeekStepper.scale）；缩小圆斑与图标尺寸 */
    scale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    // 点按动效：一个 0..1 按压量同时驱动圆斑扩散、图标压缩与箭头顺向顶出，
    // 松手用带轻微过冲的 spring 弹回——快速连点也看得到「按下去再弹起」。
    // 只改尺寸/偏移/颜色 alpha，不用 graphicsLayer 的 scale/alpha：
    // HyperOS 3 会丢弃捕获层子树里的这类 offscreen 层（红线）。
    val press by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = 0.62f, stiffness = 900f, visibilityThreshold = 0.001f),
        label = "stepArrowPress",
    )
    Box(
        modifier
            .fillMaxHeight()
            // 按压态自己跟踪，不走 MutableInteractionSource：foundation 在「祖先手势处于
            // Waiting / Compose 根位于可滚动容器内」时会把 PressInteraction.Press 延后
            // TapIndicationDelay(80ms) 才发射，抬手早于该时刻就直接丢弃；同一命中路径上
            // 的兄弟滚动容器（这里是铺满的 HorizontalPager）一旦抢走 Move，还会改发 Cancel。
            // 两种情况都表现为「快按看不到动效，只有按住不放才有」。
            // 本节点只读事件、绝不 consume，点击/长按语义与触感仍完全交给后面的 combinedClickable。
            .pointerInput(enabled) {
                pressed = false
                awaitEachGesture {
                    // 本版 awaitEachGesture 会立刻执行块（它只在两轮之间等所有指针抬起），
                    // 按下必须自己 await：否则一挂载就进按压态、圆斑再也收不回去。
                    awaitFirstDown(requireUnconsumed = false)
                    if (enabled) {
                        pressed = true
                        try {
                            while (
                                awaitPointerEvent(PointerEventPass.Main).changes.any { it.pressed }
                            ) {
                                // 等这批指针全部抬起（含 cancel，此时 pressed 同样为 false）
                            }
                        } finally {
                            // 协程被取消（如 enabled 翻转）也要复位，不能把圆斑留在按下态
                            pressed = false
                        }
                    }
                }
            }
            .combinedClickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                onLongClickLabel = "周次跳转",
                // 长按不再自己加振动：combinedClickable 触发长按时内部已经走一次
                // LocalHapticFeedback(LongPress)（foundation 1.12.0 CombinedClickableNode），
                // 这里再 Haptics.tap() 就是两下。禁用态收不到点击，触感自然跟随 enabled。
                onLongClick = onLongClick,
                onClick = {
                    Haptics.tap()
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 圆斑（含图标）整体偏移量由调用方给定，使其与胶囊端头圆同心；
        // 命中区仍是整半宽，不受偏移影响。
        // 不能用 RoundedCornerShape(percent=50)：它默认 Continuous squircle，
        // 比玻璃的 Circular Capsule 更「鼓」，按压时会从玻璃边缘顶出灰色尖角。
        Box(
            Modifier
                .offset(x = contentOffset + pressDx * press)
                .size((stepBarHeight - 12.dp) * scale * (0.82f + 0.18f * press))
                .clip(CircleShape)
                .background(pressedBg.copy(alpha = pressedBg.alpha * press.coerceIn(0f, 1f))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                description,
                Modifier.size(22.dp * scale * (1f - 0.06f * press)),
                tint = iconColor.copy(alpha = if (enabled) 1f else disabledAlpha),
            )
        }
    }
}

