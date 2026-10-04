package com.gzmtuview.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gzmtuview.R
import com.gzmtuview.GmuApplication
import com.gzmtuview.data.net.ZFException
import com.gzmtuview.ui.glass.GlassActionButton
import com.gzmtuview.ui.settings.GroupDivider
import com.gzmtuview.ui.settings.SectionLabel
import com.gzmtuview.ui.settings.SettingsGroup
import com.gzmtuview.ui.settings.settingsPageBase
import kotlinx.coroutines.launch

/** 横幅母版 `UI/icon/登录页横幅*.svg` 的 viewBox 1240.36×547.25 宽高比。 */
private const val BANNER_RATIO = 1240.36f / 547.25f

/**
 * 登录页：沿用 App 内「灰底 + 白卡 + 灰色小节标题 + 发丝分隔线」排版，
 * 输入行按体测页的「左标签 · 右输入」内联式（无浮起描边框），主按钮用玻璃 CTA。
 * 宽屏（平板/横屏）限宽 440dp 居中，避免输入行被拉成一条横贯全屏的长线。
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    /** 会话失效跳回来时的一句话说明（占用下方原有的错误提示位，点登录即消失） */
    hint: String? = null,
) {
    val ctx = LocalContext.current
    val app = GmuApplication.deps(ctx)
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var obscure by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(hint) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // 学号是本机的、密码才是失效的那个：预填学号，用户只需重输密码
    LaunchedEffect(Unit) {
        runCatching { app.store.loadCredentials() }.getOrNull()?.let { c ->
            if (username.isEmpty()) username = c.username
        }
    }

    fun doLogin() {
        usernameError = if (username.trim().isEmpty()) "请输入学号" else null
        passwordError = if (password.isEmpty()) "请输入密码" else null
        if (usernameError != null || passwordError != null) return
        loading = true
        error = null
        scope.launch {
            try {
                app.client.login(username.trim(), password)
                runCatching { app.store.saveCredentials(username.trim(), password) }
                onLoggedIn()
            } catch (e: ZFException) {
                error = if (e.authFailed) {
                    "${e.message}\n\n如已多次尝试失败，请先前往教务系统网页确认密码，避免账号被临时冻结。"
                } else {
                    e.message
                }
            } catch (e: Exception) {
                error = "网络错误：$e"
            } finally {
                loading = false
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(settingsPageBase())
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 品牌横幅：nodpi 位图，浅/深两版靠 drawable-night 限定符跟随系统主题自动切换。
            // 独立位图，不能引用 R.mipmap.ic_launcher——
            // API 26+ 解析为 adaptive-icon XML，painterResource 不支持
            Image(
                painter = painterResource(R.drawable.login_banner),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .widthIn(max = 130.dp)
                    .aspectRatio(BANNER_RATIO),
            )
            Spacer(Modifier.height(40.dp))

            SectionLabel("教务系统账号")
            SettingsGroup {
                AccountField(
                    label = "学号",
                    icon = Icons.Outlined.Person,
                    value = username,
                    error = usernameError,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                    onValue = {
                        username = it
                        usernameError = null
                    },
                )
                GroupDivider()
                AccountField(
                    label = "密码",
                    icon = Icons.Outlined.Lock,
                    value = password,
                    error = passwordError,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    obscured = obscure,
                    onToggleObscure = { obscure = !obscure },
                    onSubmit = { doLogin() },
                    onValue = {
                        password = it
                        passwordError = null
                    },
                )
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = scheme.error,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                )
            }
            Spacer(Modifier.height(26.dp))
            GlassActionButton(
                label = if (loading) "登录中…" else "登 录",
                icon = if (loading) null else Icons.AutoMirrored.Outlined.Login,
                enabled = !loading,
                loading = loading,
                onClick = { doLogin() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "账号密码仅加密保存在本机系统安全存储中，\n仅用于登录教务系统查询本人课表。",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 账号输入行：左图标 + 标签 + 左对齐输入，密码行右侧带可见性切换。
 * 错误提示挂在行内下缘，与输入文字左缘对齐（图标 20 + 间隙 12 + 标签 38 + 间隙 12）。
 */
@Composable
private fun AccountField(
    label: String,
    icon: ImageVector,
    value: String,
    error: String?,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onValue: (String) -> Unit,
    obscured: Boolean = false,
    onToggleObscure: () -> Unit = {},
    onSubmit: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 13.dp)) {
        // 固定行高：两行的视觉高度必须一致（尾控件不得反撑高度）
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 16.sp, color = scheme.onSurface, modifier = Modifier.width(38.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        "必填",
                        fontSize = 16.sp,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.35f),
                        maxLines = 1,
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
                    ),
                    cursorBrush = SolidColor(scheme.primary),
                    visualTransformation =
                        if (obscured) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (keyboardType == KeyboardType.Password) {
                val eyeInteraction = remember { MutableInteractionSource() }
                val eyePressed by eyeInteraction.collectIsPressedAsState()
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (eyePressed) scheme.onSurface.copy(alpha = 0.08f) else Color.Transparent
                        )
                        .clickable(interactionSource = eyeInteraction, indication = null) { onToggleObscure() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (obscured) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        if (obscured) "显示密码" else "隐藏密码",
                        Modifier.size(20.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (error != null) {
            Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                Spacer(Modifier.width(74.dp))
                Text(
                    error,
                    fontSize = 12.sp,
                    color = scheme.error,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}
