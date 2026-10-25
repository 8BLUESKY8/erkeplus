package gitee.leolin_me.erkepp.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.ProfileRepository
import gitee.leolin_me.erkepp.data.toUserMessage
import kotlinx.coroutines.launch

/**
 * 修改登录密码。
 *
 * 成功后当前凭证会失效，所以必须回调 [onPasswordChanged] 让外层清会话、回登录页——
 * 否则之后每个请求都会拿到 `10001`。
 */
@Composable
fun ChangePasswordScreen(
    token: String,
    repository: ProfileRepository,
    onBack: () -> Unit,
    onPasswordChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val problems = PasswordRule.problems(newPwd)
    val repeatMismatch = repeat.isNotEmpty() && repeat != newPwd
    val canSubmit = oldPwd.isNotEmpty() && newPwd.isNotEmpty() && repeat.isNotEmpty() &&
        PasswordRule.isValid(newPwd) && repeat == newPwd && !submitting

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                text = "修改密码",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { visible = !visible }) {
                Text(if (visible) "隐藏" else "显示")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            val transformation =
                if (visible) VisualTransformation.None else PasswordVisualTransformation()

            OutlinedTextField(
                value = oldPwd,
                onValueChange = { oldPwd = it },
                label = { Text("原密码") },
                singleLine = true,
                visualTransformation = transformation,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = newPwd,
                onValueChange = { newPwd = it },
                label = { Text("新密码") },
                singleLine = true,
                visualTransformation = transformation,
                isError = newPwd.isNotEmpty() && problems.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (newPwd.isNotEmpty() && problems.isNotEmpty()) {
                Text(
                    text = "还差：" + problems.joinToString("、"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = repeat,
                onValueChange = { repeat = it },
                label = { Text("确认新密码") },
                singleLine = true,
                visualTransformation = transformation,
                isError = repeatMismatch,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            if (repeatMismatch) {
                Text(
                    text = "两次输入不一致",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error!!,
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
                    Text("修改密码")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "改完需要用新密码重新登录，应用会清除本地登录状态。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { if (!submitting) showConfirm = false },
            title = { Text("确认修改密码") },
            text = { Text("提交成功后当前登录状态会失效，需要用新密码重新登录。") },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        showConfirm = false
                        submitting = true
                        error = null
                        scope.launch {
                            runCatching { repository.updatePassword(token, oldPwd, newPwd, repeat) }
                                .onSuccess { onPasswordChanged() }
                                .onFailure { error = it.toUserMessage() }
                            submitting = false
                        }
                    },
                ) { Text("确认修改") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }, enabled = !submitting) { Text("取消") }
            },
        )
    }
}
