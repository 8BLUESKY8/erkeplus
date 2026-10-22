package gitee.leolin_me.erkepp.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.AppConfig
import gitee.leolin_me.erkepp.data.ActivityRepository
import gitee.leolin_me.erkepp.data.AppPreferences
import gitee.leolin_me.erkepp.data.BlockRules
import gitee.leolin_me.erkepp.data.ReenterBehavior
import gitee.leolin_me.erkepp.data.Session
import gitee.leolin_me.erkepp.data.UpdateChecker
import gitee.leolin_me.erkepp.data.UpdateResult
import kotlinx.coroutines.launch

/** 设置页当前停在哪一层。SIGN_* 两项只在开发者模式下出现。 */
enum class SettingsSection { ROOT, BLOCK_RULES, SIGN_SANDBOX, SIGN_LOG }

/**
 * 设置：发现页、活动卡片、内容屏蔽、账号、关于。
 *
 * 偏好全部读自 [AppPreferences]，它本身就是 Compose 状态，改一下立刻生效并落盘，
 * 所以这里不再接收一堆值和回调。
 */
@Composable
fun SettingsScreen(
    session: Session,
    token: String,
    repository: ActivityRepository,
    preferences: AppPreferences,
    section: SettingsSection,
    onSectionChange: (SettingsSection) -> Unit,
    onLogout: () -> Unit,
    onShowDisclaimer: () -> Unit,
) {
    var categories by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var updateDialog by remember { mutableStateOf<UpdateDialog?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val updateChecker = remember { UpdateChecker() }

    LaunchedEffect(Unit) {
        runCatching { repository.categories(token) }.onSuccess { categories = it }
    }

    val rules = remember(preferences.blockedKeywords, preferences.blockedRegexes) {
        BlockRules.parse(preferences.blockedKeywords, preferences.blockedRegexes)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("设置", style = MaterialTheme.typography.headlineSmall)

        SettingGroup(title = "发现页") {
            SettingSwitchRow(
                title = "显示分类筛选条",
                subtitle = "关闭后只用状态和搜索筛选",
                checked = preferences.categoriesEnabled,
                onCheckedChange = { preferences.categoriesEnabled = it },
            )
            SettingSwitchRow(
                title = "预加载下一页",
                subtitle = "滚动接近底部前就提前请求，翻页更顺滑",
                checked = preferences.preloadEnabled,
                onCheckedChange = { preferences.preloadEnabled = it },
            )
            if (preferences.categoriesEnabled) {
                CategoryChipList(
                    categories = categories,
                    selected = preferences.selectedCategoryIds,
                    onChange = { preferences.selectedCategoryIds = it },
                )
            }
            ReenterChips(
                current = preferences.reenterBehavior,
                onChange = { preferences.reenterBehavior = it },
            )
        }

        SettingGroup(title = "活动卡片") {
            SettingSwitchRow(
                title = "显示封面",
                subtitle = "关闭后卡片只显示文字，省流量也更快",
                checked = preferences.showCover,
                onCheckedChange = { preferences.showCover = it },
            )
            SettingSwitchRow(
                title = "显示分类标签",
                subtitle = "在卡片上显示「思想政治引领」这类分类名",
                checked = preferences.showCategoryTags,
                onCheckedChange = { preferences.showCategoryTags = it },
            )
            SettingSwitchRow(
                title = "显示学时",
                subtitle = "例如「5 学时」",
                checked = preferences.cardShowHours,
                onCheckedChange = { preferences.cardShowHours = it },
            )
            SettingSwitchRow(
                title = "显示报名人数",
                subtitle = "例如「1363 人报名」",
                checked = preferences.cardShowCount,
                onCheckedChange = { preferences.cardShowCount = it },
            )
            SettingSwitchRow(
                title = "显示主办组织",
                subtitle = "例如「校团委」",
                checked = preferences.cardShowOrganization,
                onCheckedChange = { preferences.cardShowOrganization = it },
            )
            SettingSwitchRow(
                title = "显示时间参考",
                subtitle = "报名中显示报名截止；已截止显示开始时间；已开始显示结束时间",
                checked = preferences.cardShowTime,
                onCheckedChange = { preferences.cardShowTime = it },
            )
            OutlinedTextField(
                value = preferences.cardCustomText,
                onValueChange = { preferences.cardCustomText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                label = { Text("卡片附加文字") },
                placeholder = { Text("留空则不显示") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            TitleLinesChips(
                titleLines = preferences.titleLines,
                onChange = { preferences.titleLines = it },
            )
        }

        SettingGroup(title = "内容屏蔽") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSectionChange(SettingsSection.BLOCK_RULES) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("屏蔽规则", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = blockRuleSummary(rules),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "修改",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        SettingGroup(title = "账号") {
            Text(
                text = "${session.profile.name} · ${session.profile.studentNo}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedButton(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("退出登录")
            }
        }

        SettingGroup(title = "关于") {
            SettingSwitchRow(
                title = "开发者模式",
                subtitle = "显示已加载 / 总数 / 过滤后数量等调试信息",
                checked = preferences.developerMode,
                onCheckedChange = { preferences.developerMode = it },
            )
            if (preferences.developerMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSectionChange(SettingsSection.SIGN_SANDBOX) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("签到沙盒", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "离线假活动：配置状态、扫码、提交全流程，不联网",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSectionChange(SettingsSection.SIGN_LOG) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("签到日志", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "扫码 / 提交的关键日志，可一键复制",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !checking) {
                        checking = true
                        scope.launch {
                            val result = updateChecker.check(AppConfig.VERSION)
                            checking = false
                            updateDialog = when (result) {
                                is UpdateResult.Available -> UpdateDialog.Available(
                                    version = result.version.toString(),
                                    url = result.url,
                                    notes = result.notes,
                                )

                                UpdateResult.UpToDate -> UpdateDialog.Message("已经是最新版本（${AppConfig.VERSION}）。")
                                UpdateResult.NoRelease -> UpdateDialog.Message("仓库里还没有发布版本。")
                                is UpdateResult.Failed -> UpdateDialog.Message("检查失败：${result.message}")
                            }
                        }
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("检查更新", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (checking) "正在检查…" else "当前版本 ${AppConfig.VERSION}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "检查",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShowDisclaimer)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("免责声明与隐私说明", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = { showResetConfirm = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("恢复默认设置", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录？") },
            text = { Text("退出后本地登录凭证会被清除，需要重新输入学号和密码。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    },
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("恢复默认设置？") },
            text = { Text("发现页、活动卡片、内容屏蔽和筛选都会回到默认值。登录状态不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        preferences.resetDisplay()
                    },
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    updateDialog?.let { dialog ->
        when (dialog) {
            is UpdateDialog.Available -> AlertDialog(
                onDismissRequest = { updateDialog = null },
                title = { Text("发现新版本 ${dialog.version}") },
                text = {
                    Text(
                        text = dialog.notes.trim().ifBlank { "去发布页看看这次改了什么。" }.take(600),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            updateDialog = null
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dialog.url)))
                            }
                        },
                    ) {
                        Text("去发布页")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { updateDialog = null }) { Text("以后再说") }
                },
            )

            is UpdateDialog.Message -> AlertDialog(
                onDismissRequest = { updateDialog = null },
                title = { Text("检查更新") },
                text = { Text(dialog.text) },
                confirmButton = {
                    TextButton(onClick = { updateDialog = null }) { Text("好") }
                },
            )
        }
    }
}

/** 检查更新的弹窗内容。 */
private sealed interface UpdateDialog {
    data class Available(val version: String, val url: String, val notes: String) : UpdateDialog

    data class Message(val text: String) : UpdateDialog
}

/** 分区卡片：一组相关设置放在同一块底板上，标题在块内。 */
@Composable
private fun SettingGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChipList(
    categories: List<Pair<Int, String>>,
    selected: Set<Int>,
    onChange: (Set<Int>) -> Unit,
) {
    val entries = categories.filter { it.first != 0 }
    if (entries.isEmpty()) return
    Text(
        text = "显示哪些分类入口",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { (id, label) ->
            val checked = selected.isEmpty() || id in selected
            FilterChip(
                selected = checked,
                onClick = { onChange(toggle(selected, categories, id, !checked)) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReenterChips(current: String, onChange: (String) -> Unit) {
    Text(
        text = "重新进入发现页",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            ReenterBehavior.KEEP to "保持位置",
            ReenterBehavior.TOP to "回到顶部",
            ReenterBehavior.RELOAD to "重新加载",
        ).forEach { (value, label) ->
            FilterChip(
                selected = current == value,
                onClick = { onChange(value) },
                label = { Text(label) },
            )
        }
    }
    Text(
        text = "双击底部「发现」图标可直接刷新",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleLinesChips(titleLines: Int, onChange: (Int) -> Unit) {
    Text(
        text = "标题显示行数",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2, 3).forEach { lines ->
            FilterChip(
                selected = titleLines == lines,
                onClick = { onChange(lines) },
                label = { Text("$lines 行") },
            )
        }
    }
}

/** 勾选状态为空集合时表示“全部显示”，此处在第一次改动时展开为完整集合。 */
private fun toggle(
    selected: Set<Int>,
    categories: List<Pair<Int, String>>,
    id: Int,
    checked: Boolean,
): Set<Int> {
    val base = if (selected.isEmpty()) {
        categories.map { it.first }.filter { it != 0 }.toSet()
    } else {
        selected
    }.toMutableSet()
    if (checked) base.add(id) else base.remove(id)
    return base
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
