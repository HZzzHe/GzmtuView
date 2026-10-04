package com.gzmtuview.ui.exams

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTimeFilled
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gzmtuview.GmuApplication
import com.gzmtuview.data.model.ExamRecord
import com.gzmtuview.data.model.GradeRecord
import com.gzmtuview.data.model.GradeStats
import com.gzmtuview.data.model.Semester
import com.gzmtuview.data.model.computeGradeStats
import com.gzmtuview.data.model.examCountdownText
import com.gzmtuview.data.model.parseExamStart
import com.gzmtuview.data.net.ZFException
import com.gzmtuview.ui.sync.DailySync
import com.gzmtuview.ui.ScreenScaffold
import com.gzmtuview.ui.glass.GlassDialog
import com.gzmtuview.ui.glass.GlassChoiceDropdown
import com.gzmtuview.ui.glass.GlassIconButton
import com.gzmtuview.ui.glass.GlassTopBar
import com.gzmtuview.ui.glass.TintedGlassButton
import com.gzmtuview.ui.glass.progressiveBlur
import com.gzmtuview.ui.settings.settingsPageBase
import com.gzmtuview.ui.wallpaper.Wallpaper
import com.gzmtuview.ui.theme.Seed
import com.gzmtuview.ui.settings.settingsCardBase
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 考试页：左右滑动切换「考试时间 / 成绩」Tab，含倒计时、学期回退、缓存、主动刷新。
 * 对齐 Flutter ExamsPage。
 */
/**
 * 考试页跨切页状态缓存（同 SchedulePageCache：AnimatedContent 切走销毁、
 * 返回时恢复上次数据，避免重新网络加载闪 loading）。
 *
 * 数据字段一律用 snapshot state：每日同步（DailySync）跑在应用级作用域，
 * 用户停在考试页时它才刚取回新数据，普通 var 写入不会触发重组、页面会一直显示旧的。
 */
internal object ExamPageCache {
    val semestersState = mutableStateOf<List<Semester>>(emptyList())
    val pickedState = mutableStateOf<Semester?>(null)
    val examSemesterState = mutableStateOf<Semester?>(null)
    val examFallbackState = mutableStateOf<Semester?>(null)
    val examsState = mutableStateOf<List<ExamRecord>>(emptyList())
    val gradeSemesterState = mutableStateOf<Semester?>(null)
    val gradeFallbackState = mutableStateOf<Semester?>(null)
    val gradesState = mutableStateOf<List<GradeRecord>>(emptyList())
    val gradeStatsState = mutableStateOf(GradeStats(0.0, 0.0))

    /** 数据就绪过一次：回切时 loading 初值据此置 false（只在组合初读一次，不必是状态） */
    var loaded: Boolean = false
    var gradeSettled: Boolean = false

    /** 登出时清空 */
    fun reset() {
        semestersState.value = emptyList()
        pickedState.value = null
        examSemesterState.value = null
        examFallbackState.value = null
        examsState.value = emptyList()
        gradeSemesterState.value = null
        gradeFallbackState.value = null
        gradesState.value = emptyList()
        gradeStatsState.value = GradeStats(0.0, 0.0)
        loaded = false
        gradeSettled = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    onLogout: () -> Unit,
    onNavigate: (Int) -> Unit = {},
) {
    val ctx = LocalContext.current
    val app = GmuApplication.deps(ctx)
    val scope = rememberCoroutineScope()

    // 数据状态直接委托页面缓存：每日同步写入后，停在本页也能立刻看到新数据
    var semesters by ExamPageCache.semestersState
    var picked by ExamPageCache.pickedState

    // 考试 tab
    var examSemester by ExamPageCache.examSemesterState
    var examFallback by ExamPageCache.examFallbackState
    var exams by ExamPageCache.examsState
    var examLoading by remember { mutableStateOf(!ExamPageCache.loaded) }
    var examError by remember { mutableStateOf<String?>(null) }

    // 成绩 tab
    var gradeSemester by ExamPageCache.gradeSemesterState
    var gradeFallback by ExamPageCache.gradeFallbackState
    var grades by ExamPageCache.gradesState
    var gradeStats by ExamPageCache.gradeStatsState
    // 回切时若缓存已有数据则不再转圈（initLoad 缓存短路不会触碰此状态）
    var gradeLoading by remember { mutableStateOf(!ExamPageCache.gradeSettled) }
    var gradeError by remember { mutableStateOf<String?>(null) }

    var showCountdown by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        app.store.displayPrefsFlow().collect { showCountdown = it.showExamCountdown }
    }
    var tab by remember { mutableIntStateOf(0) }
    var showSemesterDialog by remember { mutableStateOf(false) }
    // 两步式：先在对话框内选学期（暂存），点「确认」才提交
    var pendingSemester by remember { mutableStateOf<Semester?>(null) }
    var showRefreshConfirm by remember { mutableStateOf(false) }
    // 降级说明对话框（"回顾"按钮点击弹出）
    var showFallbackInfo by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { 2 })
    // 滑动 Pager 时同步 tab 状态（顶栏学期标题、按钮高亮跟随激活页）
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }.collect { tab = it }
    }
    // 激活页的降级信息（"回顾"按钮显隐 + 对话框文案）
    val activeFallback = if (pagerState.currentPage == 0) examFallback else gradeFallback
    val activeFallbackLabel = if (pagerState.currentPage == 0) "考试安排" else "成绩"
    val activeCurrentLabel =
        if (pagerState.currentPage == 0) examSemester?.label else gradeSemester?.label

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    // ---- 后台静默刷新（跨天 stale 缓存命中后） ----
    suspend fun refreshExamsInBackground(sem: Semester) {
        runCatching {
            val items = app.client.fetchExams(sem)
            app.cache.saveExams(sem, items)
            exams = items
        }
    }

    suspend fun refreshGradesInBackground(sem: Semester) {
        runCatching {
            val g = app.client.fetchGrades(sem)
            app.cache.saveGrades(sem, g)
            grades = g
            gradeStats = computeGradeStats(g)
        }
    }

    suspend fun loadExams(force: Boolean = false) {
        val initial = picked ?: return
        examLoading = true
        examError = null
        examFallback = null
        try {
            val r = app.cache.fetchWithFallback(
                semesters = semesters,
                initial = initial,
                fetch = { app.client.fetchExams(it) },
                loadCache = { app.cache.loadExams(it) },
                saveCache = { s, items -> app.cache.saveExams(s, items) },
                forceRefresh = force,
            )
            examSemester = r.semester
            exams = r.items
            examFallback = r.fallbackFrom
            examLoading = false
            ExamPageCache.loaded = exams.isNotEmpty() || grades.isNotEmpty()
            // 跨天旧缓存才需要后台刷新；每日同步正在跑时让给它，别重复打服务器
            if (r.stale && !DailySync.isRunning) {
                scope.launch { refreshExamsInBackground(r.semester) }
            }
        } catch (e: ZFException) {
            examLoading = false
            examError = e.message
        } catch (e: Exception) {
            examLoading = false
            examError = "网络错误：$e"
        }
    }

    suspend fun loadGrades(force: Boolean = false) {
        val initial = picked ?: return
        gradeLoading = true
        gradeError = null
        gradeFallback = null
        try {
            val r = app.cache.fetchWithFallback(
                semesters = semesters,
                initial = initial,
                fetch = { app.client.fetchGrades(it) },
                loadCache = { app.cache.loadGrades(it) },
                saveCache = { s, items -> app.cache.saveGrades(s, items) },
                forceRefresh = force,
            )
            gradeSemester = r.semester
            grades = r.items
            gradeStats = computeGradeStats(r.items)
            gradeFallback = r.fallbackFrom
            gradeLoading = false
            ExamPageCache.loaded = exams.isNotEmpty() || grades.isNotEmpty()
            ExamPageCache.gradeSettled = true
            if (r.stale && !DailySync.isRunning) {
                scope.launch { refreshGradesInBackground(r.semester) }
            }
        } catch (e: ZFException) {
            gradeLoading = false
            gradeError = e.message
        } catch (e: Exception) {
            gradeLoading = false
            gradeError = "网络错误：$e"
        }
    }

    suspend fun loadAll(force: Boolean = false) {
        loadExams(force)
        loadGrades(force)
    }

    suspend fun initLoad() {
        // 切页返回：数据已从页面缓存恢复，跳过重复网络加载
        if (ExamPageCache.loaded) {
            examLoading = false
            examError = null
            return
        }
        // 当天首次打开时考试/成绩由每日同步一并取回：它正在跑就等结果，
        // 页面自己再发一份就是同一个学期打两次服务器
        if (DailySync.isRunning) DailySync.awaitRunning()
        if (ExamPageCache.loaded) {
            examLoading = false
            examError = null
            return
        }
        // 学期列表：优先用每日同步已拿到的，其次磁盘缓存学期，最后才联网
        val sems = semesters
            .ifEmpty { app.cache.loadCachedSemesters() }
            .ifEmpty { runCatching { app.client.fetchSemesters() }.getOrDefault(emptyList()) }
        if (sems.isEmpty()) {
            examLoading = false
            gradeLoading = false
            examError = "未获取到学期信息"
            gradeError = examError
            return
        }
        semesters = sems
        picked = sems.first()
        // fetchWithFallback 命中当天缓存时不联网，跨天缓存直接返回旧数据（stale 已在上文处理）
        loadAll()
        ExamPageCache.loaded = exams.isNotEmpty() || grades.isNotEmpty()
    }

    LaunchedEffect(Unit) { initLoad() }

    // ---- 当前显示学期标题：跟随激活 tab ----
    val currentSemester = if (tab == 0) examSemester else gradeSemester

    fun doRefresh(force: Boolean) {
        scope.launch {
            if (tab == 0) loadExams(force = true) else loadGrades(force = true)
        }
    }

    // 打开「选择学期」时把当前学期同步为暂存初值
    LaunchedEffect(showSemesterDialog) {
        if (showSemesterDialog) pendingSemester = picked
    }

    val pageBase = settingsPageBase()

    ScreenScaffold(
        // 玻璃对话框挂主窗口 overlay 槽
        overlay = {
            GlassDialog(
                visible = showRefreshConfirm,
                onDismissRequest = { showRefreshConfirm = false },
                title = "确认刷新？",
                textString = "刷新会重新访问教务系统获取最新数据。请勿频繁刷新，以免被服务器判定为异常行为。",
                confirmText = "确认刷新",
                onConfirm = {
                    showRefreshConfirm = false
                    doRefresh(true)
                },
                dismissText = "取消",
                onDismiss = { showRefreshConfirm = false },
            )
            GlassDialog(
                visible = showFallbackInfo,
                onDismissRequest = { showFallbackInfo = false },
                title = "提示",
                textString = "暂无本学期的$activeFallbackLabel，现在为你展示 ${activeCurrentLabel ?: ""} 的$activeFallbackLabel",
                // 官方 DialogContent 规范：取消（玻璃胶囊）+ 好的（主题色胶囊）双按钮
                confirmText = "好的",
                onConfirm = { showFallbackInfo = false },
                dismissText = "取消",
                onDismiss = { showFallbackInfo = false },
            )
            // 选择学期（液态玻璃对话框，两步式：先选后确认；与课表「学期与周次」同设计语言）
            GlassDialog(
                visible = showSemesterDialog,
                onDismissRequest = { showSemesterDialog = false },
                title = "选择学期",
                text = {
                    val accent = Color(0xFF3A8DCC) // 航海蓝：与课表/开关选中态同色
                    GlassChoiceDropdown(
                        options = semesters.map { it.label },
                        selectedIndex = semesters.indexOfFirst { it == pendingSemester },
                        tint = accent,
                        onSelect = { i -> pendingSemester = semesters.getOrNull(i) }, // 仅暂存，点「确认」才生效
                    )
                },
                confirmText = "确认",
                onConfirm = {
                    showSemesterDialog = false
                    val ps = pendingSemester
                    if (ps != null && ps != picked) {
                        picked = ps
                        scope.launch { loadAll() }
                    }
                },
                dismissText = "取消",
                onDismiss = { showSemesterDialog = false },
            )
        },
    ) {
            val listBackdrop = rememberLayerBackdrop()
            var headerPx by remember { mutableIntStateOf(0) }
            val headerTopPad = with(LocalDensity.current) { headerPx.toDp() }
            // 磨砂只覆盖头部，头部下沿外只留一段渐隐收尾（不进列表留白，故不整体下移）；
            // 满强度平台正好等于头部高度——再往下压就会把静止时列表首卡的顶部糊掉
            val frostFade = 28.dp
            val frostHeight = headerTopPad + frostFade
            val frostHold = 1f - frostFade / frostHeight
            Box(Modifier.fillMaxSize().background(Wallpaper.pageBase(pageBase))) {
                // 列表铺满整屏（含头部下方），顶部留白 = 实测头部高度，卡片可滚入头部下方
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().layerBackdrop(listBackdrop),
                ) { page ->
                    if (page == 0) {
                        ExamTabContent(
                            loading = examLoading,
                            error = examError,
                            exams = exams,
                            showCountdown = showCountdown,
                            topPadding = headerTopPad,
                            onRetry = { scope.launch { loadExams() } },
                        )
                    } else {
                        GradeTabContent(
                            loading = gradeLoading,
                            error = gradeError,
                            grades = grades,
                            stats = gradeStats,
                            topPadding = headerTopPad,
                            onRetry = { scope.launch { loadGrades() } },
                        )
                    }
                }
                // 磨砂层：独立装饰节点（无指针输入，触摸穿透给下方列表），必须排在头部之前
                // 才垫在按钮底下。折射滚动列表，满强度区覆盖整个头部、渐隐收尾在头部下方缓冲带。
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(frostHeight)
                        .progressiveBlur(
                            backdrop = listBackdrop,
                            // 磨砂 tint 跟随壁纸有效背板色（均色经压暗）——否则浅色模式下
                            // 近白 pageBase 会在壁纸上糊出一条白带（用户反馈「白色遮罩很丑」）。
                            // 无壁纸时回落 pageBase，行为不变。
                            tint = Wallpaper.effectiveBackdropColor(pageBase),
                            blurRadius = 40.dp,
                            tintIntensity = 0.9f,
                            holdFraction = frostHold,
                        ),
                )
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { headerPx = it.height },
                ) {
                    // 页内常驻顶栏（学期标题 + 刷新/选择学期玻璃按钮），底色透明露出磨砂
                    GlassTopBar(
                        title = currentSemester?.label ?: "考试",
                        backgroundColor = Color.Transparent,
                        // 顶栏落在壁纸/深色磨砂上：标题按背板亮度自适应取深墨或亮墨
                        contentColor = Wallpaper.adaptiveInk(MaterialTheme.colorScheme.onSurface),
                    ) {
                        GlassIconButton(Icons.Filled.Refresh, "刷新", onClick = { showRefreshConfirm = true })
                        Spacer(Modifier.width(8.dp))
                        GlassIconButton(
                            Icons.Outlined.CalendarMonth,
                            "选择学期",
                            onClick = { if (semesters.isNotEmpty()) showSemesterDialog = true },
                        )
                    }
                    // tab 切换：着色液态玻璃按钮（对齐体测页性别按钮范式），
                    // 选中随 Pager 页实时联动（点击 animateScrollToPage / 滑动反向高亮）
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TintedGlassButton(
                            label = "考试时间",
                            icon = Icons.Filled.AccessTimeFilled,
                            selected = pagerState.currentPage == 0,
                            tint = Color(0xFF3A8DCC),
                            pulseKey = pagerState.currentPage,
                            onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        )
                        Spacer(Modifier.width(8.dp))
                        TintedGlassButton(
                            label = "成绩",
                            icon = Icons.Rounded.Assessment,
                            selected = pagerState.currentPage == 1,
                            tint = Color(0xFF34B87A),
                            pulseKey = pagerState.currentPage,
                            onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        )
                        // 降级提醒：当前学期无安排回退展示旧学期时常亮（条件同原横幅），
                        // 点击弹对话框说明（文案与原横幅一致）
                        if (activeFallback != null) {
                            Spacer(Modifier.width(8.dp))
                            TintedGlassButton(
                                label = "回顾中",
                                icon = Icons.Filled.BrowseGallery,
                                selected = true,
                                tint = Color(0xFFFC3C57),
                                onClick = { showFallbackInfo = true },
                            )
                        }
                    }
                }
            }
        }

}

/** 供 Row 使用的点击 modifier 扩展（避免重复写 clickable）。 */

// ---- 考试 Tab ----

@Composable
private fun ExamTabContent(
    loading: Boolean,
    error: String?,
    exams: List<ExamRecord>,
    showCountdown: Boolean,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onRetry: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when {
                loading -> CenterBox(topPadding) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "正在获取考试安排，首次可能需检测可用学期...",
                            // 加载态直接落在页面根上：全局壁纸时这里就是壁纸，跟着背板取色
                            color = Wallpaper.adaptiveInk(scheme.onSurface),
                        )
                    }
                }
                error != null -> ErrorView(error!!, onRetry, topPadding)
                exams.isEmpty() -> CenterBox(topPadding) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.EventNote, null, Modifier.size(48.dp),
                            tint = scheme.outline)
                        Spacer(Modifier.height(12.dp))
                        Text("暂无考试安排")
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp), contentPadding = PaddingValues(top = topPadding + 16.dp, bottom = 100.dp)) {
                    itemsIndexed(exams) { _, e ->
                        ExamCard(e, showCountdown, scheme)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamCard(e: ExamRecord, showCountdown: Boolean, scheme: androidx.compose.material3.ColorScheme) {
    val start = parseExamStart(e.kssj)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(settingsCardBase())
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(if (e.kcmc.isEmpty()) "未知课程" else e.kcmc,
                    fontSize = 16.sp, fontWeight = FontWeight.W600)
                if (e.ksmc.isNotEmpty()) {
                    Text(e.ksmc, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (showCountdown && start != null) {
                examCountdownText(start)?.let { text ->
                    Text(
                        text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W700,
                        color = countdownColor(scheme, start),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        InfoRow(scheme, Icons.Outlined.Schedule, "考试时间", e.kssj)
        val place = listOf(e.xqmc, e.jxlmc, e.cdmc).filter { it.isNotEmpty() }.joinToString(" ")
        InfoRow(scheme, Icons.Outlined.LocationOn, "地点", place)
        if (e.zwh.isNotEmpty()) InfoRow(scheme, Icons.Outlined.EventSeat, "座位号", e.zwh)
        if (e.kkxy.isNotEmpty()) InfoRow(scheme, Icons.Outlined.AccountBalance, "开课学院", e.kkxy)
    }
}

/** 倒计时颜色：>7 天主题蓝；≤7 天橙；当天主题蓝；已过弱灰。 */
private fun countdownColor(scheme: androidx.compose.material3.ColorScheme, start: LocalDateTime): Color {
    val now = LocalDateTime.now()
    val diff = java.time.Duration.between(now, start)
    return when {
        diff.isNegative -> scheme.outline
        diff.toDays() > 7 -> scheme.primary
        diff.toDays() >= 1 -> Color(0xFFEF6C00)
        else -> scheme.primary
    }
}

// ---- 成绩 Tab ----

@Composable
private fun GradeTabContent(
    loading: Boolean,
    error: String?,
    grades: List<GradeRecord>,
    stats: GradeStats,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onRetry: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when {
                loading -> CenterBox(topPadding) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "正在获取成绩，请稍候...",
                            color = Wallpaper.adaptiveInk(scheme.onSurface),
                        )
                    }
                }
                error != null -> ErrorView(error!!, onRetry, topPadding)
                grades.isEmpty() -> CenterBox(topPadding) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Grade, null, Modifier.size(48.dp),
                            tint = scheme.outline)
                        Spacer(Modifier.height(12.dp))
                        Text("暂无成绩记录")
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp), contentPadding = PaddingValues(top = topPadding + 16.dp, bottom = 100.dp)) {
                    item {
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(settingsCardBase())
                                .padding(16.dp),
                        ) {
                            StatItem("总学分", "%.1f".format(stats.totalCredits), scheme, Modifier.weight(1f))
                            StatItem("平均绩点", "%.2f".format(stats.avgGpa), scheme, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    itemsIndexed(grades) { _, g ->
                        GradeCard(g, scheme)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeCard(g: GradeRecord, scheme: androidx.compose.material3.ColorScheme) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(settingsCardBase())
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(if (g.kcmc.isEmpty()) "未知课程" else g.kcmc,
                fontSize = 15.sp, fontWeight = FontWeight.W600,
                modifier = Modifier.weight(1f))
            Text(if (g.cj.isEmpty()) "-" else g.cj,
                fontSize = 18.sp, fontWeight = FontWeight.W700,
                color = cjColor(g.cj))
        }
        if (g.kch.isNotEmpty()) {
            Text(g.kch, fontSize = 11.sp, color = scheme.outline,
                modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            ChipText("学分", "%.1f".format(g.xf), scheme, Modifier.weight(1f))
            if (g.jd.isNotEmpty()) ChipText("绩点", g.jd, scheme, Modifier.weight(1f))
            if (g.kcxzmc.isNotEmpty()) ChipText("性质", g.kcxzmc, scheme, Modifier.weight(1f))
        }
        if (g.ksxz.isNotEmpty() || g.cjbz.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                if (g.ksxz.isNotEmpty()) ChipText("考试", g.ksxz, scheme, Modifier.weight(1f))
                if (g.cjbz.isNotEmpty()) ChipText("备注", g.cjbz, scheme, Modifier.weight(1f))
            }
        }
    }
}

/** 成绩/绩点数值强调色：深色下 Seed(0061A4) 落在暗卡上对比度过低，换亮一档航海蓝 */
@Composable
private fun gradeAccent(): Color =
    if (isSystemInDarkTheme()) Color(0xFF9CCAFF) else Seed

/** 不及格/警示橙：深色下提亮保证可读 */
@Composable
private fun gradeWarn(): Color =
    if (isSystemInDarkTheme()) Color(0xFFFFB26B) else Color(0xFFEF6C00)

@Composable
private fun cjColor(cj: String): Color {
    val v = cj.toDoubleOrNull()
    return if (v != null && v < 60) gradeWarn() else gradeAccent()
}

// ---- 公共小组件 ----

@Composable
private fun CenterBox(topPadding: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = topPadding),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    CenterBox(topPadding) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Text(message, textAlign = TextAlign.Center, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, scheme: androidx.compose.material3.ColorScheme, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.W700,
            color = gradeAccent())
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun ChipText(label: String, value: String, scheme: androidx.compose.material3.ColorScheme, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("$label：", fontSize = 12.sp, color = scheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp)
    }
}

@Composable
private fun InfoRow(
    scheme: androidx.compose.material3.ColorScheme,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    if (value.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, color = scheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp))
        Text(value, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}
