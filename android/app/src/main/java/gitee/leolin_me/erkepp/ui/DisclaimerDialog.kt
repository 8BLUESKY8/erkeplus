package gitee.leolin_me.erkepp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 首次启动必须确认的说明。
 *
 * 不允许点击外部关闭：这是使用前提，不是提示。
 */
@Composable
fun DisclaimerDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("使用前请先了解") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Section(
                    title = "个人学习作品，非官方",
                    body = "「二课++」是一名在校学生的个人学习项目，与安徽理工大学及第二课堂平台运营方" +
                        "没有任何隶属或授权关系。",
                )
                Section(
                    title = "数据以官网为准",
                    body = "本应用不保证数据准确、功能可用或长期维护。涉及学分、学时、报名、审核等" +
                        "与你切身相关的事项，请务必到官方网站复核。因使用本应用产生的后果由使用者" +
                        "自行承担。若学校或平台方认为不妥，作者会立即停止分发。",
                )
                Section(
                    title = "凭据怎么处理",
                    body = "密码不保存，只在登录时发送给官方站点一次；登录凭证使用 Android 系统密钥库" +
                        "加密存储，退出登录即清除。不接入任何第三方统计、广告或崩溃上报。",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("我已知悉")
            }
        },
    )
}

@Composable
private fun Section(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
