package gitee.leolin_me.erkepp.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.ActivityItem
import gitee.leolin_me.erkepp.data.UserRepository
import gitee.leolin_me.erkepp.data.toUserMessage
import gitee.leolin_me.erkepp.ui.ActivityRow
import gitee.leolin_me.erkepp.ui.EmptyState
import gitee.leolin_me.erkepp.ui.PageLoading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 别人参加过的活动，对应官方 `#/myAcitive/:id/:name`。
 *
 * 官方那一页还有分类/排序的 tab，但它的 `changeClassify` / `changeSort` 并没有把参数发给接口
 * （`activity/other-list` 只认 `pageNum`/`pageSize`/`userId`），所以这里不做假筛选，直接按时间倒序分页。
 */
@Composable
fun PersonActivitiesScreen(
    token: String,
    repository: UserRepository,
    userId: Long,
    personName: String,
    preloadEnabled: Boolean = true,
    onOpenActivity: (Long) -> Unit,
    onBack: () -> Unit,
) {
    var items by remember(userId) { mutableStateOf<List<ActivityItem>>(emptyList()) }
    var total by remember(userId) { mutableIntStateOf(0) }
    var page by remember(userId) { mutableIntStateOf(0) }
    var lastPage by remember(userId) { mutableStateOf(true) }
    var loading by remember(userId) { mutableStateOf(true) }
    var loadingMore by remember(userId) { mutableStateOf(false) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun load(target: Int) {
        if (target == 1) {
            loading = true
            loadingMore = false
        } else {
            loadingMore = true
        }
        try {
            val result = repository.activities(token, userId, target)
            items = if (target == 1) result.items else items + result.items
            total = result.total
            page = target
            lastPage = result.items.isEmpty() ||
                (result.total > 0 && target * UserRepository.ACTIVITY_PAGE_SIZE >= result.total)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            if (target == 1) error = failure.toUserMessage()
        } finally {
            loading = false
            loadingMore = false
        }
    }

    LaunchedEffect(userId, tick) {
        items = emptyList()
        page = 0
        lastPage = true
        load(1)
    }

    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    // key 里不放 loadingMore：它在本效果发起的请求里被改写，会取消刚发的请求、翻页空转
    LaunchedEffect(nearEnd, items.size, lastPage) {
        if (!nearEnd || loadingMore || lastPage || items.isEmpty()) return@LaunchedEffect
        if (!preloadEnabled && items.size - (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) > 2) {
            return@LaunchedEffect
        }
        scope.launch { load(page + 1) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                text = if (personName.isBlank()) "参加的活动" else "$personName 参加的活动",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when {
            loading && items.isEmpty() -> PageLoading()

            error != null && items.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { tick++ }) { Text("重试") }
            }

            items.isEmpty() -> EmptyState("没有参加活动记录")

            else -> LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                item {
                    Text(
                        text = "共 $total 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                items(items, key = { it.id }) { activity ->
                    ActivityRow(
                        item = activity,
                        onClick = { onOpenActivity(activity.id) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (!lastPage) {
                    item {
                        Text(
                            text = if (loadingMore) "正在加载…" else "上滑加载更多",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
