package gitee.leolin_me.erkepp.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gitee.leolin_me.erkepp.AppLog
import java.io.File

/**
 * 开发者模式：签到日志。
 *
 * 现场只有一次机会，出了事要能当场把证据带回来——所以这里把 `[sign]` / `[action]` / `[probe]`
 * 的日志单独捞出来，可以直接一键复制发出去。
 */
@Composable
fun SignLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    val lines = remember(tick) { readSignLog(context) }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("签到日志", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${lines.size} 行 · 存于 files/logs/erkepp.log",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { tick++ }) { Text("刷新") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val text = lines.joinToString("\n")
                    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    manager?.setPrimaryClip(ClipData.newPlainText("erkepp-sign-log", text))
                    copied = true
                    AppLog.d("sign", "log copied lines=${lines.size}")
                },
                enabled = lines.isNotEmpty(),
            ) { Text(if (copied) "已复制" else "复制全部") }
            TextButton(onClick = onBack) { Text("返回") }
        }
        Spacer(Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                if (lines.isEmpty()) {
                    Text(
                        text = "还没有签到相关日志。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = lines.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

/** 读两份日志（当前 + 轮转的上一份），只留签到链路相关的行，最多 400 行。 */
private fun readSignLog(context: Context): List<String> {
    val dir = File(context.filesDir, "logs")
    val files = listOf(File(dir, "erkepp.log.1"), File(dir, "erkepp.log"))
    val interesting = listOf("[sign]", "[action]", "[probe]")
    return files.asSequence()
        .filter { it.exists() }
        .flatMap { file ->
            runCatching { file.readLines() }.getOrDefault(emptyList()).asSequence()
        }
        .filter { line -> interesting.any { line.contains(it) } }
        .toList()
        .takeLast(400)
}
