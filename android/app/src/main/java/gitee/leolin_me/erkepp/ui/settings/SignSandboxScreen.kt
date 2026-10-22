package gitee.leolin_me.erkepp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.SandboxConfig

/**
 * 开发者模式：离线签到沙盒的配置页。
 *
 * 所有改动即时生效并落盘；点「打开模拟活动详情」会渲染一张本地详情页，
 * 里面的「扫码签到」走的是同一套相机与确认逻辑，但提交由 [gitee.leolin_me.erkepp.data.sandboxSubmit]
 * 本地造结果，**不联网**。
 */
@Composable
fun SignSandboxScreen(
    config: SandboxConfig,
    onConfigChange: (SandboxConfig) -> Unit,
    onOpenDetail: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("签到沙盒", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "本地假活动，提交不会联网。用来在真活动之前把扫码链路走一遍。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "扫码时二维码里的 activityId 最好等于上面的活动 id，否则确认框会显示「活动 #其它」。\n" +
                "提交成功后「已签到」只在本次会话内生效，重开应用回到配置值。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card {
            Column(Modifier.padding(12.dp)) {
                SectionTitle("活动")
                OutlinedTextField(
                    value = config.name,
                    onValueChange = { onConfigChange(config.copy(name = it)) },
                    label = { Text("活动名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = config.activityId.toString(),
                    onValueChange = { input ->
                        input.filter { it.isDigit() }
                            .toLongOrNull()
                            ?.let { onConfigChange(config.copy(activityId = it)) }
                    },
                    label = { Text("活动 id（扫码二维码里的 activityId 要对得上）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Card {
            Column(Modifier.padding(12.dp)) {
                SectionTitle("活动状态")
                Chips(
                    options = listOf(5 to "报名中", 6 to "待开始", 7 to "进行中", 8 to "已结束"),
                    selected = config.status,
                    onSelect = { onConfigChange(config.copy(status = it)) },
                )
                SectionTitle("报名状态")
                Chips(
                    options = listOf(2 to "报名已通过", 1 to "其它（不能签到）"),
                    selected = config.applyStatus,
                    onSelect = { onConfigChange(config.copy(applyStatus = it)) },
                )
            }
        }

        Card {
            Column(Modifier.padding(12.dp)) {
                SectionTitle("签到开关")
                Chips(
                    options = listOf(0 to "未开启", 1 to "签到中", 2 to "签退中"),
                    selected = config.signSwitch,
                    onSelect = { onConfigChange(config.copy(signSwitch = it)) },
                )
                SectionTitle("签到方式")
                Chips(
                    options = listOf(
                        1 to "动态二维码",
                        2 to "静态二维码",
                        3 to "GPS 定位",
                    ),
                    selected = config.signWay.split(',').firstOrNull()?.toIntOrNull() ?: 1,
                    onSelect = { onConfigChange(config.copy(signWay = it.toString())) },
                )
                Text(
                    text = "选 GPS 定位时不会出现扫码按钮（官方学生端也没开放）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionTitle("考勤标准")
                Chips(
                    options = listOf(1 to "签到+签退", 2 to "仅签到", 3 to "仅签退"),
                    selected = config.attendanceType,
                    onSelect = { onConfigChange(config.copy(attendanceType = it)) },
                )
                SectionTitle("我是否已签到")
                Chips(
                    options = listOf(0 to "未签到", 1 to "已签到"),
                    selected = config.isSigner,
                    onSelect = { onConfigChange(config.copy(isSigner = it)) },
                )
            }
        }

        Card {
            Column(Modifier.padding(12.dp)) {
                SectionTitle("模拟提交结果")
                Chips(
                    options = listOf(0 to "成功", 1 to "失败"),
                    selected = if (config.fail) 1 else 0,
                    onSelect = { onConfigChange(config.copy(fail = it == 1)) },
                )
                if (config.fail) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = config.failCode.toString(),
                            onValueChange = { input ->
                                input.filter { it.isDigit() }
                                    .toIntOrNull()
                                    ?.let { onConfigChange(config.copy(failCode = it)) }
                            },
                            label = { Text("code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(140.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = config.failMsg,
                            onValueChange = { onConfigChange(config.copy(failMsg = it)) },
                            label = { Text("服务端 msg") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Button(onClick = onOpenDetail, modifier = Modifier.fillMaxWidth()) {
            Text("打开模拟活动详情")
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onConfigChange(SandboxConfig()) }) { Text("恢复默认") }
            TextButton(onClick = onBack) { Text("返回") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label, style = MaterialTheme.typography.bodySmall) },
            )
        }
    }
}
