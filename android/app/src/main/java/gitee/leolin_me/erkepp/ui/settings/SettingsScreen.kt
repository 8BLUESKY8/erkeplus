package gitee.leolin_me.erkepp.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.AppConfig
import gitee.leolin_me.erkepp.data.ActivityRepository
import gitee.leolin_me.erkepp.data.Session

/**
 * 设置：分类展示方式、账号、关于。
 *
 * 分类入口与卡片标签都由用户决定，默认只显示三个常用分类，避免一屏塞不下。
 */
@Composable
fun SettingsScreen(
    session: Session,
    token: String,
    repository: ActivityRepository,
    categoriesEnabled: Boolean,
    showCategoryTags: Boolean,
    selectedCategoryIds: Set<Int>,
    blockedKeywords: String,
    blockedRegexes: String,
    preloadEnabled: Boolean,
    onCategoriesEnabledChange: (Boolean) -> Unit,
    onShowCategoryTagsChange: (Boolean) -> Unit,
    onSelectedCategoriesChange: (Set<Int>) -> Unit,
    onBlockedKeywordsChange: (String) -> Unit,
    onBlockedRegexesChange: (String) -> Unit,
    onPreloadChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onShowDisclaimer: () -> Unit,
) {
    var categories by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { repository.categories(token) }.onSuccess { categories = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("设置", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(20.dp))
        Text("发现页分类", style = MaterialTheme.typography.titleSmall)

        SettingSwitchRow(
            title = "显示分类筛选条",
            subtitle = "关闭后不显示分类，只用状态和搜索筛选",
            checked = categoriesEnabled,
            onCheckedChange = onCategoriesEnabledChange,
        )
        SettingSwitchRow(
            title = "卡片显示分类标签",
            subtitle = "在活动卡片上显示「思想政治引领」这类分类名",
            checked = showCategoryTags,
            onCheckedChange = onShowCategoryTagsChange,
        )

        if (categoriesEnabled) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "显示哪些分类入口",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            categories.filter { it.first != 0 }.forEach { (id, label) ->
                val checked = selectedCategoryIds.isEmpty() || id in selectedCategoryIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectedCategoriesChange(toggle(selectedCategoryIds, categories, id, !checked)) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { value ->
                            onSelectedCategoriesChange(toggle(selectedCategoryIds, categories, id, value))
                        },
                    )
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()

        Spacer(Modifier.height(16.dp))
        Text("内容屏蔽", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "标题命中的活动不会出现在发现页。关键词按子串匹配，正则每行一条。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedTextField(
            value = blockedKeywords,
            onValueChange = onBlockedKeywordsChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            label = { Text("屏蔽关键词") },
            placeholder = { Text("例如：讲座、打卡") },
            minLines = 2,
            shape = RoundedCornerShape(12.dp),
        )
        OutlinedTextField(
            value = blockedRegexes,
            onValueChange = onBlockedRegexesChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            label = { Text("屏蔽正则") },
            placeholder = { Text("例如：^\\d+天.*打卡$") },
            minLines = 2,
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(16.dp))
        SettingSwitchRow(
            title = "预加载下一页",
            subtitle = "滚动接近底部前就提前请求，翻页更顺滑",
            checked = preloadEnabled,
            onCheckedChange = onPreloadChange,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()

        Spacer(Modifier.height(16.dp))
        Text("账号", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${session.profile.name} · ${session.profile.studentNo}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { showLogoutConfirm = true }) {
            Text("退出登录")
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()

        Spacer(Modifier.height(16.dp))
        Text("关于", style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onShowDisclaimer, modifier = Modifier.padding(start = 0.dp)) {
            Text("免责声明与隐私说明")
        }
        Text(
            text = "版本 ${AppConfig.VERSION}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
