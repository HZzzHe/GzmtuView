package com.gzmtuview.ui.schedule

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gzmtuview.GmuApplication
import com.gzmtuview.data.color.courseColorsForIndex
import com.gzmtuview.data.color.kCoursePalette
import com.gzmtuview.data.model.Course
import com.gzmtuview.data.model.ExamRecord
import com.gzmtuview.data.model.GradeRecord
import com.gzmtuview.ui.exams.ExamPageCache
import com.gzmtuview.data.model.Semester
import com.gzmtuview.ui.ScreenScaffold
import com.gzmtuview.ui.glass.FrostScrollPage
import com.gzmtuview.ui.glass.GlassDialog
import com.gzmtuview.ui.glass.GlassIconButton
import com.gzmtuview.ui.glass.TintedGlassButton
import com.gzmtuview.ui.settings.GroupDivider
import com.gzmtuview.ui.settings.SectionLabel
import com.gzmtuview.ui.settings.SettingsGroup
import com.gzmtuview.ui.settings.settingsPageBaseResolved
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs

private val kWeekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val SearchBlue = Color(0xFF3A8DCC)
private val GradeGreen = Color(0xFF34B87A)

private enum class SearchCategory(val label: String) {
    ALL("全部"),
    COURSE("课程"),
    ROOM("教室"),
    EXAM("考试"),
    TEACHER("教师"),
}

/** 同一门课（同教学班）的多节安排合并为一条搜索结果，附带同学期成绩（若有）。 */
private data class CourseHit(
    val semester: Semester,
    val name: String,
    val teacher: String,
    val className: String,
    val meetings: List<Course>,
    val grade: GradeRecord?,
)

/** 一条考试安排，附带同学期同科目的成绩（若有）。 */
private data class ExamHit(
    val semester: Semester,
    val exam: ExamRecord,
    val grade: GradeRecord?,
)

private class SearchIndex(
    val courses: List<CourseHit>,
    val exams: List<ExamHit>,
)

/**
 * 课表搜索页：跨全部已收录学期，检索课程、教师、教室以及考试科目/考场。
 * 数据默认来自本地课表/考试/成绩缓存；页面底部「完整收录」可联网补齐全部学期
 * （受 5 次/秒限速）。命中考试或课程时，若该学期已有成绩则一并展示分数、绩点与学分。
 * 作为 MainTabs 覆盖式二级页（dest = 4），与设置页共用预测性返回。
 */
@Composable
fun ScheduleSearchScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SearchCategory.ALL) }
    val ctx = LocalContext.current
    val app = GmuApplication.deps(ctx)
    var index by remember { mutableStateOf<SearchIndex?>(null) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var collecting by remember { mutableStateOf(false) }
    var collectStatus by remember { mutableStateOf<String?>(null) }
    var showCollectConfirm by remember { mutableStateOf(false) }
    // 等覆盖页滑入结束再聚焦，避免键盘与 push 动画抢帧
    LaunchedEffect(Unit) {
        delay(380)
        runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(Unit) {
        // 当前学期可能刚刷新、尚未与磁盘完全一致：优先用页面内存里的那份
        val currentSemester = SchedulePageCache.selectedState.value
        val currentCourses = SchedulePageCache.coursesState.value
        index = withContext(Dispatchers.IO) {
            buildSearchIndex(app, currentSemester, currentCourses)
        }
    }

    val q = query.trim()
    val courseHits = remember(index, q, category) {
        index?.courses?.filter { it.matches(q, category) }.orEmpty()
    }
    val examHits = remember(index, q, category) {
        index?.exams?.filter { it.matches(q, category) }.orEmpty()
    }
    val total = courseHits.size + examHits.size
    val pageBase = settingsPageBaseResolved()

    fun startCompleteCollect() {
        if (collecting) return
        collecting = true
        collectStatus = "正在重新获取学期列表…"
        scope.launch {
            try {
                val summary = collectAllData(app) { collectStatus = it }
                // 新收录的学期已落盘：立即刷新课表/考试页的学期列表，无需重启
                refreshSemesterListsFromDisk(app)
                // 收录完成后按磁盘重建索引（当前学期也走新落盘的数据）
                val currentSemester = SchedulePageCache.selectedState.value
                val currentCourses = SchedulePageCache.coursesState.value
                index = withContext(Dispatchers.IO) {
                    buildSearchIndex(app, currentSemester, currentCourses)
                }
                collectStatus = buildString {
                    if (summary.courses == 0 && summary.exams == 0 && summary.grades == 0) {
                        append("收录完成，但未获取到可用数据")
                    } else {
                        append(
                            "收录完成：${summary.semesters} 个学期，" +
                                "${summary.courses} 门课程 / ${summary.exams} 场考试 / " +
                                "${summary.grades} 条成绩",
                        )
                    }
                    if (summary.failures > 0) append("；${summary.failures} 项未获取")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                collectStatus = "完整收录失败：${e.message ?: "网络错误"}"
            } finally {
                collecting = false
            }
        }
    }

    ScreenScaffold(
        hideBottomBar = true,
        overlay = {
            // 完整收录先二次确认：会重新访问教务并逐学期请求，必须让用户明确触发
            GlassDialog(
                visible = showCollectConfirm,
                onDismissRequest = { showCollectConfirm = false },
                title = "完整收录？",
                textString = "将重新获取学期列表，并逐学期收录全部学期的课程、考试与成绩。" +
                    "请求限制为每秒 5 次，学期较多时需要等待一段时间；" +
                    "已收录的数据不会被空结果覆盖。",
                confirmText = "开始收录",
                onConfirm = {
                    showCollectConfirm = false
                    startCompleteCollect()
                },
                dismissText = "取消",
                onDismiss = { showCollectConfirm = false },
            )
        },
    ) {
        FrostScrollPage(
            title = "搜索",
            pageBase = pageBase,
            leading = {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onClick = onBack)
            },
        ) {
            SearchField(
                query = query,
                onQuery = { query = it },
                focusRequester = focusRequester,
            )
            Spacer(Modifier.height(14.dp))
            CategoryRow(selected = category, onSelect = { category = it })
            Spacer(Modifier.height(20.dp))

            if (index == null) {
                SectionLabel("已收录数据")
                SettingsGroup {
                    Text(
                        "正在读取已收录的课表、考试与成绩…",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (total == 0) {
                SectionLabel(if (q.isEmpty()) "已收录数据" else "搜索结果")
                SettingsGroup {
                    Text(
                        when {
                            q.isNotEmpty() -> "没有找到与「$q」相关的${if (category == SearchCategory.ALL) "内容" else category.label}。"
                            category == SearchCategory.ALL -> "还没有已收录的课表、考试或成绩数据。"
                            else -> "「${category.label}」分类下暂无已收录数据。"
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val colorIndex = SchedulePageCache.courseColorIndexState.value
                if (courseHits.isNotEmpty()) {
                    SectionLabel("课程 · ${courseHits.size} 门")
                    SettingsGroup {
                        courseHits.forEachIndexed { i, hit ->
                            if (i > 0) GroupDivider()
                            CourseHitRow(hit, colorIndex)
                        }
                    }
                }
                if (courseHits.isNotEmpty() && examHits.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                }
                if (examHits.isNotEmpty()) {
                    SectionLabel("考试 · ${examHits.size} 条")
                    SettingsGroup {
                        examHits.forEachIndexed { i, hit ->
                            if (i > 0) GroupDivider()
                            ExamHitRow(hit)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            CollectionFooter(
                collecting = collecting,
                status = collectStatus,
                onCollect = { if (!collecting) showCollectConfirm = true },
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * 页面底部收录提示：当前只展示本地已收录的数据，点「完整收录」联网补齐。
 * 收录中隐藏点击并展示进度/结果。
 */
@Composable
private fun CollectionFooter(
    collecting: Boolean,
    status: String?,
    onCollect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val hint = buildAnnotatedString {
        append("数据为目前收录的结果，点此")
        withStyle(SpanStyle(color = SearchBlue, fontWeight = FontWeight.Medium)) {
            append("完整收录")
        }
        append("获取全部学期数据。")
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            hint,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = !collecting) { onCollect() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
        if (status != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (collecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = SearchBlue,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    status,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    color = if (collecting) SearchBlue else scheme.onSurfaceVariant.copy(alpha = 0.65f),
                )
            }
        }
    }
}

/** 搜索框：白卡内嵌无边框输入，左侧放大镜，有内容时右侧一键清除。 */
@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    focusRequester: FocusRequester,
) {
    val scheme = MaterialTheme.colorScheme
    SettingsGroup {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                null,
                Modifier.size(20.dp),
                tint = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "搜索课程、教室、考试或教师",
                        fontSize = 16.sp,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.Close,
                    "清除",
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onQuery("") },
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/** 分类按钮：全部 / 课程 / 教室 / 考试 / 教师，沿用考试页的着色玻璃胶囊。 */
@Composable
private fun CategoryRow(selected: SearchCategory, onSelect: (SearchCategory) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchCategory.entries.forEach { cat ->
            TintedGlassButton(
                label = cat.label,
                selected = selected == cat,
                tint = SearchBlue,
                onClick = { onSelect(cat) },
            )
        }
    }
}

/** 课程结果行：课程色条 + 课名 + 学期 + 教师/教室 + 每条上课安排 + 已有成绩。 */
@Composable
private fun CourseHitRow(hit: CourseHit, colorIndex: Map<String, Int>?) {
    val scheme = MaterialTheme.colorScheme
    val index = colorIndex?.get(hit.name)
        ?: (abs(hit.name.hashCode()) % kCoursePalette.size)
    val accent = courseColorsForIndex(index, isSystemInDarkTheme()).second
    val locations = hit.meetings.map { it.location }.filter { it.isNotEmpty() }.distinct()
    val rooms = locations.ifEmpty {
        hit.meetings.map { it.campus }.filter { it.isNotEmpty() }.distinct()
    }
    val meta = listOf(hit.teacher, rooms.joinToString("、"))
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .width(4.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    hit.name,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    hit.semester.label,
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    meta,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                hit.meetings.joinToString("\n") { meetingText(it) },
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            hit.grade?.let {
                Spacer(Modifier.height(2.dp))
                GradeLine(it, includeTeacher = false)
            }
        }
    }
}

/** 考试结果行：考试科目 + 学期 + 考试名称/时间 + 考场/座位 + 已有成绩。 */
@Composable
private fun ExamHitRow(hit: ExamHit) {
    val scheme = MaterialTheme.colorScheme
    val exam = hit.exam
    val time = listOf(exam.ksmc, exam.kssj).filter { it.isNotEmpty() }.joinToString(" · ")
    val room = listOf(exam.xqmc, exam.jxlmc, exam.cdmc)
        .filter { it.isNotEmpty() }
        .joinToString(" ")
    val seat = exam.zwh.takeIf { it.isNotEmpty() && it != "0" }
    val place = listOf(room, seat?.let { "座位 $it" })
        .filterNotNull()
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .width(4.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SearchBlue),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    subjectName(exam.kcmc),
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    hit.semester.label,
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
            if (time.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    time,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (place.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    place,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
            hit.grade?.let {
                Spacer(Modifier.height(2.dp))
                GradeLine(it, includeTeacher = true)
            }
        }
    }
}

/** 已有成绩：分数 / 绩点 / 学分，考试与课程结果共用；绿色强调。 */
@Composable
private fun GradeLine(grade: GradeRecord, includeTeacher: Boolean) {
    val parts = buildList {
        add("成绩 ${grade.cj}")
        if (grade.jd.isNotBlank() && grade.jd != "无") add("绩点 ${grade.jd}")
        if (grade.xf > 0) add("${creditText(grade.xf)} 学分")
        if (includeTeacher && grade.jsxm.isNotBlank() && grade.jsxm != "无") add("教师 ${grade.jsxm}")
    }
    Text(
        parts.joinToString(" · "),
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
        color = GradeGreen,
    )
}

private fun creditText(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

/** 考试科目名带课程号前缀（如 "12274A0161/物流法律法规"），成绩表只存纯课名。 */
private fun subjectName(raw: String): String =
    raw.substringAfterLast('/').trim().ifEmpty { raw.trim() }

private fun meetingText(c: Course): String {
    val day = kWeekdayLabels.getOrElse(c.dayOfWeek - 1) { "周${c.dayOfWeek}" }
    val sections = c.rawTime.ifBlank {
        if (c.sections.isEmpty()) "" else "${c.sections.first()}-${c.sections.last()}节"
    }
    return listOf(day, sections, c.weeksText)
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
}

private fun CourseHit.matches(q: String, category: SearchCategory): Boolean {
    if (q.isEmpty()) return category != SearchCategory.EXAM
    return when (category) {
        SearchCategory.ALL -> name.contains(q, true) ||
            teacher.contains(q, true) ||
            className.contains(q, true) ||
            meetings.any { it.location.contains(q, true) || it.campus.contains(q, true) }
        SearchCategory.COURSE -> name.contains(q, true) || className.contains(q, true)
        SearchCategory.ROOM -> meetings.any {
            it.location.contains(q, true) || it.campus.contains(q, true)
        }
        SearchCategory.EXAM -> false
        SearchCategory.TEACHER -> teacher.contains(q, true)
    }
}

private fun ExamHit.matches(q: String, category: SearchCategory): Boolean {
    if (q.isEmpty()) {
        return category == SearchCategory.ALL ||
            category == SearchCategory.ROOM ||
            category == SearchCategory.EXAM
    }
    return when (category) {
        SearchCategory.ALL, SearchCategory.EXAM -> {
            exam.kcmc.contains(q, true) ||
                exam.ksmc.contains(q, true) ||
                exam.cdmc.contains(q, true) ||
                exam.jxlmc.contains(q, true) ||
                exam.xqmc.contains(q, true)
        }
        SearchCategory.ROOM -> {
            exam.cdmc.contains(q, true) ||
                exam.jxlmc.contains(q, true) ||
                exam.xqmc.contains(q, true)
        }
        SearchCategory.COURSE, SearchCategory.TEACHER -> false
    }
}

private fun GradeRecord.hasScore(): Boolean {
    val v = cj.trim()
    return v.isNotEmpty() && v != "无"
}

/**
 * 读取全部已收录学期：课表、考试、成绩各自独立缓存，只展示实际存在的本地数据。
 * 优先用磁盘数据（完整收录刚写入的那份），磁盘缺失时才回落到页面内存。
 */
private suspend fun buildSearchIndex(
    app: GmuApplication,
    currentSemester: Semester?,
    currentCourses: List<Course>,
): SearchIndex {
    val semesters = (app.cache.loadCachedSemesters() + listOfNotNull(currentSemester?.takeIf { it.isStandard }))
        .distinct()
        .sortedByDescending { it.rank }
    val courses = mutableListOf<CourseHit>()
    val exams = mutableListOf<ExamHit>()
    for (sem in semesters) {
        val diskCourses = app.cache.load(sem)?.result?.courses.orEmpty()
        val schedule = when {
            diskCourses.isNotEmpty() -> diskCourses
            sem == currentSemester -> currentCourses
            else -> emptyList()
        }
        val gradeList = app.cache.loadGrades(sem)?.items.orEmpty()
            .filter { it.hasScore() }
        // 考试科目名可能带 "课程号/" 前缀，统一按纯课名分组；同名课程优先匹配教师
        val grades = gradeList.groupBy { subjectName(it.kcmc) }
        fun gradeOf(name: String, teacher: String? = null): GradeRecord? {
            val list = grades[subjectName(name)] ?: return null
            return if (teacher.isNullOrBlank()) {
                list.firstOrNull()
            } else {
                list.firstOrNull { it.jsxm == teacher } ?: list.firstOrNull()
            }
        }
        schedule.groupBy { Triple(it.name, it.teacher, it.className) }
            .forEach { (key, meetings) ->
                courses += CourseHit(
                    semester = sem,
                    name = key.first,
                    teacher = key.second,
                    className = key.third,
                    meetings = meetings
                        .distinctBy { "${it.dayOfWeek}|${it.rawTime}|${it.weeksText}|${it.location}" }
                        .sortedWith(compareBy({ it.dayOfWeek }, { it.sections.firstOrNull() ?: 0 })),
                    grade = gradeOf(key.first, key.second),
                )
            }
        app.cache.loadExams(sem)?.items.orEmpty().forEach { exam ->
            exams += ExamHit(sem, exam, gradeOf(exam.kcmc))
        }
    }
    return SearchIndex(
        courses = courses.sortedWith(
            compareByDescending<CourseHit> { it.semester.rank }.thenBy { it.name },
        ),
        exams = exams.sortedWith(
            compareByDescending<ExamHit> { it.semester.rank }.thenBy { it.exam.kssj },
        ),
    )
}

/** 请求起点最小间隔 200ms（≈每秒最多 5 次），完整收录期间共用同一个节拍器。 */
private class RequestPacer(private val minIntervalMs: Long = 200L) {
    private var lastAt = 0L

    suspend fun await() {
        if (lastAt > 0) {
            val wait = lastAt + minIntervalMs - SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
        }
        lastAt = SystemClock.elapsedRealtime()
    }
}

private data class CollectSummary(
    val semesters: Int,
    val courses: Int,
    val exams: Int,
    val grades: Int,
    val failures: Int,
)

/** 完整收录写盘后立即刷新课表/考试页的学期列表，免得用户重启才看到新收录的学期。 */
private suspend fun refreshSemesterListsFromDisk(app: GmuApplication) {
    val cached = app.cache.loadCachedSemestersWithData()
        .ifEmpty { app.cache.loadCachedSemesters() }
    if (cached.isEmpty()) return
    val merged = (cached + listOfNotNull(SchedulePageCache.selectedState.value))
        .distinct()
    val sorted = app.client.sortSemesters(merged)
    SchedulePageCache.semestersState.value = sorted
    ExamPageCache.semestersState.value = sorted
}

/**
 * 完整收录：重新读取教务的学年列表，对每个候选学期拉取课程、考试、成绩并写入本地缓存。
 * 所有网络请求共用 [RequestPacer]，起点间隔 ≥200ms（每秒不超过 5 次）；
 * 课程为空的学期不再请求考试/成绩。单个学期/单项失败只计数、不中断其余学期；
 * 空结果不落盘，避免污染已有缓存与列表。
 */
private suspend fun collectAllData(
    app: GmuApplication,
    onProgress: (String) -> Unit,
): CollectSummary {
    val pacer = RequestPacer()
    pacer.await()
    val years = app.client.fetchYearList()
    val candidates = app.client.buildSemesterCandidates(years, LocalDate.now().year)
    if (candidates.isEmpty()) throw IllegalStateException("未获取到学期列表")

    var semesters = 0
    var courses = 0
    var exams = 0
    var grades = 0
    var failures = 0
    candidates.forEachIndexed { index, sem ->
        onProgress("正在收录 ${sem.label}（${index + 1}/${candidates.size}）…")
        var saved = false
        // 课程为空（有效的“无课学期”）即停止该学期的考试/成绩请求：
        // 这类学期没有可收录的数据，继续请求只会制造无意义的失败计数
        var scheduleKnownEmpty = false
        try {
            pacer.await()
            val result = app.client.fetchSchedule(sem)
            if (result.courses.isNotEmpty()) {
                app.cache.save(sem, result)
                courses += result.courses.size
                saved = true
            } else {
                scheduleKnownEmpty = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failures++
        }
        if (!scheduleKnownEmpty) {
            try {
                pacer.await()
                val items = app.client.fetchExams(sem)
                if (items.isNotEmpty()) {
                    app.cache.saveExams(sem, items)
                    exams += items.size
                    saved = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failures++
            }
            try {
                pacer.await()
                val items = app.client.fetchGrades(sem)
                if (items.isNotEmpty()) {
                    app.cache.saveGrades(sem, items)
                    grades += items.size
                    saved = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failures++
            }
        }
        if (saved) semesters++
    }
    return CollectSummary(semesters, courses, exams, grades, failures)
}
