package gitee.leolin_me.erkepp.ui.profile

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import gitee.leolin_me.erkepp.ui.InlineLoading
import gitee.leolin_me.erkepp.ui.PageLoading
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.ProfileRepository
import gitee.leolin_me.erkepp.data.toUserMessage
import kotlinx.coroutines.launch

/**
 * 个人标签（官方 `#/myTags`）。
 *
 * 字典来自 `dict/user/tag`，保存时把选中的 id 拼成逗号串交给 `update-person-tag`。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsScreen(
    token: String,
    repository: ProfileRepository,
    initialIds: List<Int>,
    onBack: () -> Unit,
    onSaved: (List<Int>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tags by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var selected by remember { mutableStateOf(initialIds.toSet()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { repository.tagDictionary(token) }
            .onSuccess { tags = it; error = null }
            .onFailure { error = it.toUserMessage() }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                text = "我的标签",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "已选 ${selected.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            loading -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { PageLoading() }

            tags.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = error ?: "学校还没配置标签",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = {
                    scope.launch {
                        loading = true
                        runCatching { repository.tagDictionary(token) }
                            .onSuccess { tags = it; error = null }
                            .onFailure { error = it.toUserMessage() }
                        loading = false
                    }
                }) { Text("重试") }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "选几个能代表自己的标签，会显示在个人主页。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { (id, name) ->
                        FilterChip(
                            selected = id in selected,
                            onClick = {
                                selected = if (id in selected) selected - id else selected + id
                            },
                            label = { Text(name) },
                        )
                    }
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        saving = true
                        error = null
                        scope.launch {
                            runCatching { repository.updateTags(token, selected.toList().sorted()) }
                                .onSuccess { onSaved(selected.toList().sorted()) }
                                .onFailure { error = it.toUserMessage() }
                            saving = false
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (saving) {
                        InlineLoading(size = 18.dp)
                    } else {
                        Text("保存标签")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 详情页展示用：把标签 id 映射成名字，字典里没有的跳过。 */
fun tagNames(ids: List<Int>, dictionary: List<Pair<Int, String>>): List<String> {
    val map = dictionary.toMap()
    return ids.mapNotNull { map[it] }
}

/** 小提示：让「标签」行在没有字典时也可读。 */
@Composable
fun TagSummary(ids: List<Int>, dictionary: List<Pair<Int, String>>) {
    val names = tagNames(ids, dictionary)
    Text(
        text = if (names.isEmpty()) "未设置" else names.joinToString(" "),
        style = MaterialTheme.typography.bodyMedium,
    )
}
