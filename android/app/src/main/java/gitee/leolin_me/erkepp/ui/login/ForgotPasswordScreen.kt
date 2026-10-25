package gitee.leolin_me.erkepp.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import gitee.leolin_me.erkepp.ui.InlineLoading
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.data.PasswordPolicy
import gitee.leolin_me.erkepp.data.PasswordResetRepository
import gitee.leolin_me.erkepp.data.School
import gitee.leolin_me.erkepp.data.toUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 原生找回密码页。
 *
 * 表单与官方 `#/resetPassword` 对齐：姓名、学号/工号、手机号、短信验证码、新密码。
 * 短信验证码接口为 `/common/captcha/mobile`，提交接口为 `/common/modify/pwd`（均经实测）。
 */
@Composable
fun ForgotPasswordScreen(
    repository: PasswordResetRepository,
    onBack: () -> Unit,
    school: School = School.default,
) {
    var name by remember { mutableStateOf("") }
    var studentNo by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordRepeat by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var repeatVisible by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var sending by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1_000)
            countdown -= 1
        }
    }

    fun mobileLooksValid(): Boolean = mobile.trim().length == 11 && mobile.trim().all { it.isDigit() }

    fun sendCode() {
        if (sending || countdown > 0) return
        if (!mobileLooksValid()) {
            error = "请输入正确的手机号"
            return
        }
        scope.launch {
            sending = true
            error = null
            runCatching { repository.sendSmsCode(mobile) }
                .onSuccess { countdown = 60 }
                .onFailure { error = it.toUserMessage() }
            sending = false
        }
    }

    fun submit() {
        if (submitting) return
        val checks = listOf(
            (name.isBlank()) to "请输入姓名",
            (studentNo.isBlank()) to "请输入学号/工号",
            (!mobileLooksValid()) to "请输入正确的手机号",
            (smsCode.isBlank()) to "请输入验证码",
            (!PasswordPolicy.isValid(password)) to PasswordPolicy.HINT,
            (password != passwordRepeat) to "两次输入的新密码不一致",
        )
        checks.firstOrNull { it.first }?.let {
            error = it.second
            return
        }
        scope.launch {
            submitting = true
            error = null
            runCatching {
                repository.resetPassword(school, name, studentNo, mobile, smsCode, password)
            }
                .onSuccess { done = true }
                .onFailure { error = it.toUserMessage() }
            submitting = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, enabled = !submitting) {
                Text("返回")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "找回密码",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "需要姓名、学号、绑定手机号和短信验证码。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))

        if (done) {
            Text(
                text = "密码已修改，请用新密码登录。",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("返回登录")
            }
        } else {
            FormField(value = name, onChange = { name = it; error = null }, label = "姓名", imeAction = ImeAction.Next)
            Spacer(Modifier.height(14.dp))
            FormField(
                value = studentNo,
                onChange = { studentNo = it.filter { ch -> ch.isLetterOrDigit() }; error = null },
                label = "学号/工号",
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(14.dp))
            FormField(
                value = mobile,
                onChange = { mobile = it.filter { ch -> ch.isDigit() }.take(11); error = null },
                label = "手机号",
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
            )

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = smsCode,
                    onValueChange = { smsCode = it.filter { ch -> ch.isDigit() }.take(6); error = null },
                    modifier = Modifier.weight(1f),
                    label = { Text("验证码") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { sendCode() }, enabled = countdown == 0 && !sending) {
                    when {
                        sending -> InlineLoading(size = 16.dp)

                        countdown > 0 -> Text("${countdown}s")
                        else -> Text("获取验证码")
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            PasswordField(
                value = password,
                onChange = { password = it; error = null },
                label = "新密码",
                visible = passwordVisible,
                onToggleVisible = { passwordVisible = !passwordVisible },
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = PasswordPolicy.HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            PasswordField(
                value = passwordRepeat,
                onChange = { passwordRepeat = it; error = null },
                label = "确认新密码",
                visible = repeatVisible,
                onToggleVisible = { repeatVisible = !repeatVisible },
                imeAction = ImeAction.Done,
            )

            Spacer(Modifier.height(16.dp))

            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !submitting,
            ) {
                if (submitting) {
                    InlineLoading(size = 20.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("完成", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
    )
}

/** 口令输入框：带锁图标与「小眼睛」，两次输入可以分别点开核对。 */
@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        leadingIcon = {
            Icon(painter = painterResource(R.drawable.ic_lock), contentDescription = null)
        },
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    painter = painterResource(
                        if (visible) R.drawable.ic_visibility else R.drawable.ic_visibility_off,
                    ),
                    contentDescription = if (visible) "隐藏密码" else "显示密码",
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
    )
}
