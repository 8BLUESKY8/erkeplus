package gitee.leolin_me.erkepp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import gitee.leolin_me.erkepp.ui.InlineLoading
import gitee.leolin_me.erkepp.ui.PageLoading
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.AppPreferences
import gitee.leolin_me.erkepp.data.StorageEntry
import gitee.leolin_me.erkepp.data.StorageItem
import gitee.leolin_me.erkepp.data.StorageReport
import gitee.leolin_me.erkepp.data.StorageRepository
import gitee.leolin_me.erkepp.data.formatStorageSize
import kotlinx.coroutines.launch

/**
 * 数据管理：把应用里会自己长大的东西列出来，逐项清理。
 *
 * 进来先扫一遍盘，每项显示实际占用；默认勾选的都是清了会自动重来的缓存，
 * 回收站和网页登录态这类会丢东西的默认不勾。
 * 上次清理时勾了哪些会记住，下次进来照旧。
 */
@Composable
fun StorageScreen(
    repository: StorageRepository,
    preferences: AppPreferences,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<StorageReport?>(null) }
    var scanning by remember { mutableStateOf(true) }
    var clearing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var selected by remember {
        mutableStateOf(
            preferences.storageSelection
                ?: StorageItem.defaultSelection,
        )
    }

    suspend fun rescan() {
        scanning = true
        report = runCatching { repository.scan(context) }.getOrNull()
        scanning = false
    }

    LaunchedEffect(Unit) { rescan() }

    val current = report
    // 现在真有东西可清的才算数：0 字节的项勾着也没意义，清完重扫时会自动退出选中
    val available = current?.entries?.filter { it.bytes > 0 }?.map { it.item.key }?.toSet().orEmpty()
    val selectedItems = StorageItem.entries.filter { it.key in selected && it.key in available }
    val selectedBytes = current?.bytesOf(selectedItems) ?: 0L

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                text = "数据管理",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(enabled = !scanning && !clearing, onClick = {
                scope.launch { rescan() }
            }) { Text("重新扫描") }
        }

        when {
            scanning && current == null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PageLoading()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "正在扫描各组件占用…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            current == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("没扫出占用情况", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { scope.launch { rescan() } }) { Text("重试") }
            }

            else -> {
                SummaryCard(
                    appBytes = current.appBytes,
                    cleanableBytes = current.entries.sumOf { it.bytes },
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 8.dp,
                    ),
                ) {
                    items(current.entries, key = { it.item.key }) { entry ->
                        StorageRow(
                            entry = entry,
                            checked = entry.item.key in selected && entry.bytes > 0,
                            enabled = !clearing && entry.bytes > 0,
                            onToggle = { checked ->
                                selected = if (checked) {
                                    selected + entry.item.key
                                } else {
                                    selected - entry.item.key
                                }
                            },
                        )
                    }
                }

                BottomBar(
                    selectedCount = selectedItems.size,
                    selectedBytes = selectedBytes,
                    clearing = clearing,
                    scanning = scanning,
                    onClear = { confirm = true },
                )
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }

    if (confirm) {
        val items = selectedItems
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("确认清理") },
            text = {
                Column {
                    Text(
                        text = "数据无价，手下留情，你真的想好了吗？",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(10.dp))
                    items.forEach { item ->
                        val entry = current?.entryOf(item)
                        Text(
                            text = "· ${item.title}  ${formatStorageSize(entry?.bytes ?: 0L)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (item.risky) {
                            Text(
                                text = "   ${item.detail}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "共 ${formatStorageSize(selectedBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        clearing = true
                        message = null
                        scope.launch {
                            val freed = runCatching { repository.clear(context, items) }.getOrDefault(0L)
                            preferences.storageSelection = selected
                            clearing = false
                            rescan()
                            message = if (freed > 0) {
                                "已清理 ${formatStorageSize(freed)}"
                            } else {
                                "这些数据已经是干净的了"
                            }
                        }
                    },
                ) { Text("确认清理", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("算了") }
            },
        )
    }
}

@Composable
private fun SummaryCard(appBytes: Long, cleanableBytes: Long) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "应用数据合计",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatStorageSize(appBytes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "可清理",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatStorageSize(cleanableBytes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StorageRow(
    entry: StorageEntry,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggle(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle(it) }, enabled = enabled)
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = entry.item.title, style = MaterialTheme.typography.bodyLarge)
                if (entry.item.risky) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "会丢数据",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Text(
                text = entry.item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatStorageSize(entry.bytes),
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.bytes > 0) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (entry.count > 0) {
                Text(
                    text = "${entry.count} 个文件",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    selectedCount: Int,
    selectedBytes: Long,
    clearing: Boolean,
    scanning: Boolean,
    onClear: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "已选 $selectedCount 项",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatStorageSize(selectedBytes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Button(
                onClick = onClear,
                enabled = !clearing && !scanning && selectedCount > 0,
            ) {
                if (clearing) {
                    InlineLoading(size = 16.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (clearing) "清理中…" else "清理选中项")
            }
        }
    }
}
