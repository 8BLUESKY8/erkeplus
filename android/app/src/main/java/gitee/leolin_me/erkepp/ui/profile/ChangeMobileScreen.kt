package gitee.leolin_me.erkepp.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import gitee.leolin_me.erkepp.ui.InlineLoading
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.ProfileRepository
import gitee.leolin_me.erkepp.data.isValidMobile
import gitee.leolin_me.erkepp.data.toUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 更换手机号。
 *
 * 关键点：验证码发到**当前**手机号（官方接口如此），新号只是提交值。
 */
@Composable
fun ChangeMobileScreen(
    token: String,
    repository: ProfileRepository,
    currentMobile: String,
    onBack: () -> Unit,
    onUpdated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var newMobile by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    var sending by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }

    // 倒计时：每秒减一，归零后可以重发
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1_000)
            countdown -= 1
        }
    }

    val mobileValid = isValidMobile(newMobile)
    val canSubmit = mobileValid && code.length in 4..8 && !submitting

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                text = "更换手机号",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "当前手机号：${maskMobile(currentMobile)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "验证码会发到当前手机号，不是新号。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = newMobile,
                onValueChange = { input -> newMobile = input.filter { it.isDigit() }.take(11) },
                label = { Text("新手机号") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = newMobile.isNotEmpty() && !mobileValid,
                supportingText = if (newMobile.isNotEmpty() && !mobileValid) {
                    { Text("请输入 11 位手机号") }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { input -> code = input.filter { it.isDigit() }.take(8) },
                    label = { Text("验证码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                TextButton(
                    enabled = countdown == 0 && !sending && currentMobile.isNotBlank(),
                    onClick = {
                        sending = true
                        error = null
                        hint = null
                        scope.launch {
                            runCatching { repository.sendMobileCaptcha(token, currentMobile) }
                                .onSuccess {
                                    countdown = 60
                                    hint = "验证码已发送到 ${maskMobile(currentMobile)}"
                                }
                                .onFailure { error = it.toUserMessage() }
                            sending = false
                        }
                    },
                ) {
                    Text(
                        when {
                            sending -> "发送中…"
                            countdown > 0 -> "${countdown}s"
                            else -> "获取验证码"
                        },
                    )
                }
            }

            hint?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { showConfirm = true },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (submitting) {
                    InlineLoading(size = 18.dp)
                } else {
                    Text("更换手机号")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { if (!submitting) showConfirm = false },
            title = { Text("确认更换手机号") },
            text = {
                Column {
                    Text("旧号：${maskMobile(currentMobile)}")
                    Text("新号：${maskMobile(newMobile)}")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "换绑后新旧号都能用于登录吗？以学校规则为准，应用只提交这次变更。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        showConfirm = false
                        submitting = true
                        error = null
                        scope.launch {
                            runCatching { repository.updateMobile(token, currentMobile, code, newMobile) }
                                .onSuccess { onUpdated() }
                                .onFailure { error = it.toUserMessage() }
                            submitting = false
                        }
                    },
                ) { Text("确认更换") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }, enabled = !submitting) { Text("取消") }
            },
        )
    }
}

private fun maskMobile(mobile: String): String =
    if (mobile.length < 7) mobile else mobile.substring(0, 3) + "****" + mobile.takeLast(4)
