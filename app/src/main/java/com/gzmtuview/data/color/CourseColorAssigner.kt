package com.gzmtuview.data.color

import androidx.compose.ui.graphics.Color
import com.gzmtuview.data.model.Course
import com.gzmtuview.data.pref.SettingsStore
import kotlin.math.abs

/**
 * 课程颜色自动分配（规格表 v1.0）。
 * 每学期从 30 色色板为每门课程分配固定颜色索引；算法确定性：同输入同输出。
 * 与 Flutter 版 course_color_assigner.dart 完全一致（双版本颜色一致）。
 */

/** 上课记录：课程标识、星期（1-7）、起始节、结束节（闭区间）。 */
data class CourseEntry(val courseId: String, val day: Int, val start: Int, val end: Int)

/** 30 色色板单项：色相 + 调类（true=A / false=B）。 */
data class CourseColorSpec(val hue: Double, val isA: Boolean)

/** 30 色色板（规格表 v1.0，A/B 双音调交替）。 */
val kCoursePalette: List<CourseColorSpec> = listOf(
    CourseColorSpec(5.0, true), CourseColorSpec(19.0, false),
    CourseColorSpec(33.0, true), CourseColorSpec(47.0, false),
    CourseColorSpec(61.0, true), CourseColorSpec(75.0, false),
    CourseColorSpec(89.0, true), CourseColorSpec(103.0, false),
    CourseColorSpec(116.0, true), CourseColorSpec(129.0, false),
    CourseColorSpec(142.0, true), CourseColorSpec(155.0, false),
    CourseColorSpec(166.0, true), CourseColorSpec(177.0, false),
    CourseColorSpec(188.0, true), CourseColorSpec(199.0, false),
    CourseColorSpec(210.0, true), CourseColorSpec(221.0, false),
    CourseColorSpec(232.0, true), CourseColorSpec(244.0, false),
    CourseColorSpec(256.0, true), CourseColorSpec(268.0, false),
    CourseColorSpec(280.0, true), CourseColorSpec(292.0, false),
    CourseColorSpec(304.0, true), CourseColorSpec(316.0, false),
    CourseColorSpec(326.0, true), CourseColorSpec(336.0, false),
    CourseColorSpec(346.0, true), CourseColorSpec(356.0, false),
)

/** 惩罚权重常量。 */
const val kWtSameTone = 2.0
const val kWtHueGap = 1.0
const val kWtChange = 3.0
/** 同色惩罚：大于相邻冲突最大惩罚，确保课程数 ≤ 30 时全唯一。 */
const val kWtSameColor = 5.0
/** 局部优化最大轮数。 */
const val kLocalSearchRounds = 20

/** 环形色相距离（0~180）。 */
fun hueDistance(a: Double, b: Double): Double {
    val d = abs(a - b) % 360.0
    return if (d <= 180) d else 360 - d
}

/** 从课程列表生成上课记录：按星期 + 连续节次段拆分。 */
fun buildEntries(courses: List<Course>): List<CourseEntry> {
    val entries = mutableListOf<CourseEntry>()
    for (c in courses) {
        if (c.sections.isEmpty()) continue
        var start = c.sections.first()
        var prev = start
        for (s in c.sections.drop(1)) {
            if (s != prev + 1) {
                entries.add(CourseEntry(c.name, c.dayOfWeek, start, prev))
                start = s
            }
            prev = s
        }
        entries.add(CourseEntry(c.name, c.dayOfWeek, start, prev))
    }
    return entries
}

/** 相邻建边：垂直（同日跨节相邻）或水平（相邻日节次重叠），无序对、去重、排除自身。 */
fun buildAdjacency(entries: List<CourseEntry>): Set<Pair<String, String>> {
    val adj = mutableSetOf<Pair<String, String>>()
    for (i in entries.indices) {
        for (j in i + 1 until entries.size) {
            val a = entries[i]
            val b = entries[j]
            if (a.courseId == b.courseId) continue
            val vertical = a.day == b.day &&
                (a.end + 1 == b.start || b.end + 1 == a.start)
            val horizontal = abs(a.day - b.day) == 1 &&
                a.start <= b.end && b.start <= a.end
            if (vertical || horizontal) {
                adj.add(
                    if (a.courseId < b.courseId) a.courseId to b.courseId
                    else b.courseId to a.courseId
                )
            }
        }
    }
    return adj
}

/** 两颜色索引的相邻惩罚（同调 + 色相间隔 < 60°）。 */
private fun specPairPenalty(i1: Int, i2: Int): Double {
    val s1 = kCoursePalette[i1]
    val s2 = kCoursePalette[i2]
    var p = 0.0
    if (s1.isA == s2.isA) p += kWtSameTone
    val gap = hueDistance(s1.hue, s2.hue)
    if (gap < 60) p += kWtHueGap * (60 - gap) / 60
    return p
}

/** 总代价 = 相邻对惩罚之和 + 全局同色惩罚 + 3.0 × 变更数。 */
private fun totalCost(
    courseIds: List<String>,
    adj: Set<Pair<String, String>>,
    colorOf: Map<String, Int>,
    old: Map<String, Int>?,
): Double {
    var cost = 0.0
    for ((c1, c2) in adj) {
        cost += specPairPenalty(colorOf[c1]!!, colorOf[c2]!!)
    }
    // 全局同色惩罚
    for (i in courseIds.indices) {
        for (j in i + 1 until courseIds.size) {
            if (colorOf[courseIds[i]] == colorOf[courseIds[j]]) cost += kWtSameColor
        }
    }
    if (old != null) {
        for (id in courseIds) {
            val prev = old[id]
            if (prev != null && prev != colorOf[id]) cost += kWtChange
        }
    }
    return cost
}

/**
 * 课程颜色自动分配（确定性）：贪心初始化 + 局部优化。
 * @param courses 本学期全部课程
 * @param entries 上课记录（可由 buildEntries 生成）
 * @param old 上一学期/上次的映射（用于稳定沿用），无则 null
 * @return Map<课程名, 颜色索引>
 */
fun assignColors(
    courses: List<Course>,
    entries: List<CourseEntry>,
    old: Map<String, Int>?,
): Map<String, Int> {
    val courseIds = courses.map { it.name }.toSet().toMutableList()
    courseIds.sort()
    val adj = buildAdjacency(entries)
    val colorOf = mutableMapOf<String, Int>()

    // 贪心顺序：有旧色课程优先（先沿用旧色），同层按名称升序
    val greedyOrder = courseIds.sortedWith(compareBy(
        { if (old?.containsKey(it) == true) 0 else 1 },
        { it },
    ))

    // 1) 贪心初始化
    for (id in greedyOrder) {
        var bestCost = Double.POSITIVE_INFINITY
        var bestColor = 0
        for (ci in kCoursePalette.indices) {
            var p = 0.0
            for ((c1, c2) in adj) {
                if (c1 == id && colorOf.containsKey(c2)) {
                    p += specPairPenalty(ci, colorOf[c2]!!)
                } else if (c2 == id && colorOf.containsKey(c1)) {
                    p += specPairPenalty(ci, colorOf[c1]!!)
                }
            }
            for (other in colorOf.values) {
                if (other == ci) p += kWtSameColor
            }
            val prev = old?.get(id)
            if (prev != null && prev != ci) p += kWtChange
            if (p < bestCost - 1e-9 || (p == bestCost && ci < bestColor)) {
                bestCost = p
                bestColor = ci
            }
        }
        colorOf[id] = bestColor
    }

    // 2) 局部优化：固定 20 轮，单课程换色 + 相邻互换，仅严格下降采纳
    for (round in 0 until kLocalSearchRounds) {
        var improved = false
        // 单课程换色
        for (id in greedyOrder) {
            val base = totalCost(courseIds, adj, colorOf, old)
            val cur = colorOf[id]!!
            for (ci in kCoursePalette.indices) {
                if (ci == cur) continue
                colorOf[id] = ci
                val c = totalCost(courseIds, adj, colorOf, old)
                if (c < base - 1e-9) {
                    improved = true
                    break
                }
                colorOf[id] = cur
            }
        }
        // 相邻两课程互换颜色
        val adjSnapshot = adj.toList()
        for ((c1, c2) in adjSnapshot) {
            val base = totalCost(courseIds, adj, colorOf, old)
            val v1 = colorOf[c1]!!
            val v2 = colorOf[c2]!!
            colorOf[c1] = v2
            colorOf[c2] = v1
            val c = totalCost(courseIds, adj, colorOf, old)
            if (c < base - 1e-9) {
                improved = true
            } else {
                colorOf[c1] = v1
                colorOf[c2] = v2
            }
        }
        if (!improved) break
    }

    // 3) 后处理：课程数 ≤ 30 时强制全部唯一
    if (courseIds.size <= kCoursePalette.size) {
        val used = mutableSetOf<Int>()
        val duplicates = mutableListOf<String>()
        for (id in courseIds) {
            val c = colorOf[id]!!
            if (!used.add(c)) duplicates.add(id)
        }
        val unused = (kCoursePalette.indices).filter { it !in used }.toMutableList()
        for (id in duplicates) {
            var best = Double.POSITIVE_INFINITY
            var bestColor = unused.first()
            for (u in unused) {
                var p = 0.0
                for ((c1, c2) in adj) {
                    if (c1 == id) {
                        p += specPairPenalty(u, colorOf[c2]!!)
                    } else if (c2 == id) {
                        p += specPairPenalty(u, colorOf[c1]!!)
                    }
                }
                if (p < best - 1e-9) {
                    best = p
                    bestColor = u
                }
            }
            colorOf[id] = bestColor
            used.add(bestColor)
            unused.remove(bestColor)
        }
    }

    return colorOf
}

/** 课表内容 hash：用于判断课表是否变化（决定是否重算颜色映射）。 */
fun courseContentHash(courses: List<Course>): String {
    val sorted = courses.sortedWith(compareBy(
        { it.name },
        { it.dayOfWeek },
        { it.sections.joinToString(",") },
    ))
    val buf = StringBuilder()
    for (c in sorted) {
        buf.append(c.name).append('|')
            .append(c.dayOfWeek).append('|')
            .append(c.sections.joinToString(",")).append('|')
            .append(c.weeks.joinToString(",")).append(';')
    }
    return buf.toString()
}

/**
 * 颜色映射的唯一写入口：课表内容 hash 没变就沿用旧映射（不重新配色），
 * 变了才以旧映射为先验重算并落盘。课表页与每日同步共用同一段逻辑，避免两处漂移。
 */
suspend fun ensureCourseColorIndex(
    store: SettingsStore,
    courses: List<Course>,
): Map<String, Int>? {
    if (courses.isEmpty()) return null
    return runCatching {
        val hash = courseContentHash(courses)
        val saved = store.loadCourseColorMap()
        if (saved != null && saved.first == hash) {
            saved.second
        } else {
            val map = assignColors(courses, buildEntries(courses), saved?.second)
            store.saveCourseColorMap(hash, map)
            map
        }
    }.getOrNull()
}

/** 颜色索引 → （卡片底, 顶部胶囊条, 卡内文字）三色。按 HSL 取色公式 v1.0。 */fun courseColorsForIndex(index: Int, isDark: Boolean): Triple<Color, Color, Color> {
    val spec = kCoursePalette[((index % kCoursePalette.size) + kCoursePalette.size) % kCoursePalette.size]
    val h = spec.hue
    val background: Color
    val bar: Color
    val text: Color
    if (isDark) {
        background = colorFromHSL(h, if (spec.isA) 0.32 else 0.42, if (spec.isA) 0.25 else 0.33)
        bar = colorFromHSL(h, if (spec.isA) 0.45 else 0.50, if (spec.isA) 0.62 else 0.70)
        text = Color.White
    } else {
        background = colorFromHSL(h, if (spec.isA) 0.45 else 0.58, if (spec.isA) 0.91 else 0.87)
        bar = colorFromHSL(h, if (spec.isA) 0.50 else 0.55, if (spec.isA) 0.42 else 0.36)
        text = colorFromHSL(h, if (spec.isA) 0.45 else 0.50, if (spec.isA) 0.28 else 0.24)
    }
    return Triple(background, bar, text)
}

/** HSL → sRGB（与 Flutter HSLColor 一致的数学转换）。h∈[0,360), s,l∈[0,1]。 */
fun colorFromHSL(h: Double, s: Double, l: Double, alpha: Float = 1f): Color {
    if (s <= 0.0) {
        val g = l.toFloat().coerceIn(0f, 1f)
        return Color(g, g, g, alpha)
    }
    val hh = ((h % 360.0) + 360.0) % 360.0 / 360.0
    fun hue2rgb(p: Double, q: Double, t0: Double): Double {
        var t = t0
        if (t < 0.0) t += 1.0
        if (t > 1.0) t -= 1.0
        if (t < 1.0 / 6.0) return p + (q - p) * 6.0 * t
        if (t < 1.0 / 2.0) return q
        if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6.0
        return p
    }
    val q = if (l < 0.5) l * (1.0 + s) else l + s - l * s
    val p = 2.0 * l - q
    val r = hue2rgb(p, q, hh + 1.0 / 3.0)
    val g = hue2rgb(p, q, hh)
    val b = hue2rgb(p, q, hh - 1.0 / 3.0)
    return Color(
        r.toFloat().coerceIn(0f, 1f),
        g.toFloat().coerceIn(0f, 1f),
        b.toFloat().coerceIn(0f, 1f),
        alpha,
    )
}
