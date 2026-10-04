package com.gzmtuview.ui.tools.physical

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gzmtuview.data.model.PhysicalTestResult
import com.gzmtuview.data.model.PhysicalTestScorer
import com.gzmtuview.data.model.TestGender
import com.gzmtuview.data.model.TestGrade
import com.gzmtuview.ui.ScreenScaffold
import com.gzmtuview.ui.glass.FrostScrollPage
import com.gzmtuview.ui.glass.GlassIconButton
import com.gzmtuview.ui.glass.TintedGlassButton
import com.gzmtuview.ui.settings.GroupDivider
import com.gzmtuview.ui.settings.SectionLabel
import com.gzmtuview.ui.settings.SettingsGroup
import com.gzmtuview.ui.settings.SettingsRow
import com.gzmtuview.ui.settings.settingsCardBase
import com.gzmtuview.ui.settings.settingsPageBase

private val Orange = Color(0xFFFF9800)

/**
 * 体测成绩计算：实时评分，总分 <50 橙色提醒。对齐 Flutter PhysicalTestPage。
 * 输入区沿用设置页「灰底 + 白卡 + 灰色小节标题」语言：无边框输入嵌在白卡行内，
 * 左侧项目名、右侧数值（右对齐）+ 灰色单位，行间发丝分隔线。
 */
@Composable
fun PhysicalTestScreen(onBack: () -> Unit) {
    var gender by remember { mutableStateOf(TestGender.MALE) }
    var grade by remember { mutableStateOf(TestGrade.JUNIOR_SENIOR) }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var vital by remember { mutableStateOf("") }
    var fifty by remember { mutableStateOf("") }
    var reach by remember { mutableStateOf("") }
    var jump by remember { mutableStateOf("") }
    var pull by remember { mutableStateOf("") }
    var run by remember { mutableStateOf("") }

    // ---- 实时派生结果 ----
    val h = height.trim().toDoubleOrNull()
    val w = weight.trim().toDoubleOrNull()
    val bmi = if (h != null && h > 0 && w != null) w / ((h / 100) * (h / 100)) else null
    val vitalV = vital.trim().toDoubleOrNull()
    val fiftyV = fifty.trim().toDoubleOrNull()
    val reachV = reach.trim().toDoubleOrNull()
    val jumpV = jump.trim().toDoubleOrNull()
    val pullV = pull.trim().toDoubleOrNull()
    val runSec = parseRunSeconds(run)

    val hasAny = listOf(bmi, vitalV, fiftyV, reachV, jumpV, pullV, runSec).any { it != null }
    val result = if (hasAny) {
        PhysicalTestScorer.score(
            gender = gender,
            grade = grade,
            bmi = bmi,
            vitalCapacity = vitalV,
            fiftyMeter = fiftyV,
            sitAndReach = reachV,
            longJump = jumpV,
            pullUpSitUp = pullV,
            enduranceSeconds = runSec?.toDouble(),
        )
    } else null

    val pageBase = settingsPageBase()
    ScreenScaffold(hideBottomBar = true) {
        FrostScrollPage(
            title = "体测成绩计算",
            pageBase = pageBase,
            leading = {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onClick = onBack)
            },
        ) {
            // ---- 受测者 ----
            SectionLabel("受测者")
            SettingsGroup {
                SettingsRow(
                    title = "性别",
                    showChevron = false,
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 男=航海蓝 / 女=玫粉
                            TintedGlassButton(
                                label = "男",
                                icon = Icons.Filled.Male,
                                selected = gender == TestGender.MALE,
                                tint = Color(0xFF3A8DCC),
                                onClick = { gender = TestGender.MALE },
                            )
                            TintedGlassButton(
                                label = "女",
                                icon = Icons.Filled.Female,
                                selected = gender == TestGender.FEMALE,
                                tint = Color(0xFFE86A9E),
                                onClick = { gender = TestGender.FEMALE },
                            )
                        }
                    },
                )
                GroupDivider()
                SettingsRow(
                    title = "年级",
                    showChevron = false,
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 大一大二=青绿·书本打基础 / 大三大四=琥珀橙·学位帽临近毕业
                            TintedGlassButton(
                                label = "大一大二",
                                icon = Icons.AutoMirrored.Outlined.MenuBook,
                                selected = grade == TestGrade.FRESHMAN_SOPHOMORE,
                                tint = Color(0xFF34B87A),
                                onClick = { grade = TestGrade.FRESHMAN_SOPHOMORE },
                            )
                            TintedGlassButton(
                                label = "大三大四",
                                icon = Icons.Outlined.School,
                                selected = grade == TestGrade.JUNIOR_SENIOR,
                                tint = Color(0xFFE09540),
                                onClick = { grade = TestGrade.JUNIOR_SENIOR },
                            )
                        }
                    },
                )
            }
            Spacer(Modifier.height(20.dp))

            // ---- 身体数据 ----
            SectionLabel("身体数据")
            SettingsGroup {
                PField("身高", "cm", "170", height, KeyboardType.Decimal) { height = it }
                GroupDivider()
                PField("体重", "kg", "60", weight, KeyboardType.Decimal) { weight = it }
                if (bmi != null) {
                    GroupDivider()
                    BmiRow(bmi)
                }
            }
            Spacer(Modifier.height(20.dp))

            // ---- 测试项目 ----
            SectionLabel("测试项目")
            SettingsGroup {
                PField("肺活量", "mL", "4500", vital, KeyboardType.Decimal) { vital = it }
                GroupDivider()
                PField("50 米跑", "秒", "7.5", fifty, KeyboardType.Decimal) { fifty = it }
                GroupDivider()
                PField("坐位体前屈", "cm", "10", reach, KeyboardType.Decimal) { reach = it }
                GroupDivider()
                PField("立定跳远", "cm", "230", jump, KeyboardType.Decimal) { jump = it }
                GroupDivider()
                PField(
                    if (gender == TestGender.MALE) "引体向上" else "一分钟仰卧起坐",
                    "次", "15", pull, KeyboardType.Decimal,
                ) { pull = it }
                GroupDivider()
                PField(
                    if (gender == TestGender.MALE) "1000 米跑" else "800 米跑",
                    "分:秒", "3:40", run, KeyboardType.Text,
                ) { run = it }
            }
            Spacer(Modifier.height(26.dp))

            // ---- 结果 ----
            if (result != null) {
                SectionLabel("评分结果")
                PhysicalResultView(result)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** "3'40\"" / "3:40" / "3.40" → 秒；无法解析返回 null。 */
private fun parseRunSeconds(text: String): Int? {
    val t = text.trim().replace("\"", "").replace("'", ":")
    val parts = t.split(":")
    if (parts.size != 2) return null
    val m = parts[0].toIntOrNull()
    val s = parts[1].toIntOrNull()
    if (m == null || s == null || s >= 60) return null
    return m * 60 + s
}

/**
 * 白卡内的一行输入：左侧项目名 + 右侧无边框数值（右对齐）+ 灰色单位。
 * 未填写时以淡灰占位提示参考值，聚焦时光标用主题色。
 */
@Composable
private fun PField(
    label: String,
    unit: String,
    hint: String,
    value: String,
    keyboardType: KeyboardType,
    onValue: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, color = scheme.onSurface, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Box(contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty()) {
                Text(
                    hint,
                    fontSize = 16.sp,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.4f),
                    textAlign = TextAlign.End,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W500,
                    color = scheme.onSurface,
                    textAlign = TextAlign.End,
                ),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = {}),
                modifier = Modifier.widthIn(min = 56.dp, max = 132.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(unit, fontSize = 13.sp, color = scheme.onSurfaceVariant)
    }
}

/** 身高+体重齐了就在卡内补一行 BMI（灰字，不参与输入）。 */
@Composable
private fun BmiRow(bmi: Double) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("BMI", fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            "%.1f · %s".format(bmi, bmiBand(bmi)),
            fontSize = 13.sp,
            fontWeight = FontWeight.W500,
            color = scheme.onSurfaceVariant,
        )
    }
}

private fun bmiBand(bmi: Double): String = when {
    bmi < 17.9 -> "偏瘦"
    bmi < 24 -> "正常"
    bmi < 28 -> "超重"
    else -> "肥胖"
}

@Composable
private fun PhysicalResultView(r: PhysicalTestResult) {
    val scheme = MaterialTheme.colorScheme
    val totalColor = if (r.failed) Orange else scheme.primary
    Column(Modifier.fillMaxWidth()) {
        // 总分卡
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(settingsCardBase())
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("总分", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                Text(
                    "已测 ${r.filledCount}/7 项 · " +
                        if (r.filledCount >= 7)
                            (if (r.failed) "不及格（<50）" else "及格")
                        else "继续填写实时更新",
                    fontSize = 12.sp,
                    color = if (r.failed) Orange else scheme.onSurfaceVariant,
                )
            }
            Text(
                "%.1f".format(r.totalScore),
                fontSize = 40.sp,
                fontWeight = FontWeight.W700,
                color = totalColor,
            )
        }
        Spacer(Modifier.height(12.dp))
        // 各项明细
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(settingsCardBase()),
        ) {
            r.items.forEachIndexed { i, item ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontSize = 15.sp)
                        if (item.note.isNotEmpty()) {
                            Text(item.note, fontSize = 11.sp, color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (item.bonus > 0) "${item.total}（+${item.bonus}）" else "${item.total}",
                        fontWeight = FontWeight.W600,
                        color = if (item.score < 60) Orange else scheme.primary,
                    )
                }
                if (i != r.items.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        thickness = 0.6.dp,
                        color = scheme.outlineVariant,
                    )
                }
            }
        }
    }
}
