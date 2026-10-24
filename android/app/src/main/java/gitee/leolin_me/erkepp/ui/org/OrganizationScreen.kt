package gitee.leolin_me.erkepp.ui.org

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.OrgApplyStatus
import gitee.leolin_me.erkepp.data.OrgListItem
import gitee.leolin_me.erkepp.data.OrgListKind
import gitee.leolin_me.erkepp.data.OrganizationRepository
import gitee.leolin_me.erkepp.data.toUserMessage
import gitee.leolin_me.erkepp.AppLog
import gitee.leolin_me.erkepp.ui.profile.AvatarImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 我的组织，对应官方 `#/myOrganization`。
 *
 * 三个列表（我加入的 / 我创建的 / 我退出的）× 三种审核状态（待审核 / 已通过 / 已驳回），
 * 参数和官方一字不差：`pageNum/pageSize/status`，`status` 是审核状态 1/2/3。
 *
 * 申请提交后要重新拉一次列表才能看到「待审核」，所以 [refreshKey] 由外面加一。
 */
@Composable
fun OrganizationScreen(
    token: String,
    repository: OrganizationRepository,
    refreshKey: Int,
    onOpenOrg: (Long, String) -> Unit,
    onJoinScan: () -> Unit,
    onBack: () -> Unit,
) {
    var kind by remember { mutableStateOf(OrgListKind.JOINED) }
    var status by remember { mutableStateOf(OrgApplyStatus.PASSED) }
    var items by remember { mutableStateOf<List<OrgListItem>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var lastPage by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load(target: Int) {
        if (target == 1) {
            loading = true
            error = null
        } else {
            loadingMore = true
        }
        try {
            val result = repository.myList(token, kind, status, target)
            items = if (target == 1) result.list else items + result.list
            page = target
            lastPage = result.lastPage
        } catch (failure: CancellationException) {
            // 组合被销毁时的正常取消，不能当失败处理，更不能吞掉
            throw failure
        } catch (failure: Throwable) {
            // 第二页失败以前是静默的，界面上只剩一个转圈；这里留一行日志好查
            AppLog.d(
                "org",
                "list load failed kind=${kind.name} status=${status.value} page=$target ${failure.message}",
            )
            if (target == 1) error = failure.toUserMessage()
        } finally {
            loading = false
            loadingMore = false
        }
    }

    LaunchedEffect(kind, status, refreshKey) {
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
    // key 里不能放 loadingMore：它在本效果发起的请求里被改写，一改 key 就取消刚发的请求，
    // 然后重进效果再发一次，最后卡在无限循环里（所有组织页就是这么抽的）。
    LaunchedEffect(nearEnd, items.size, lastPage) {
        if (!nearEnd || loadingMore || lastPage || items.isEmpty()) return@LaunchedEffect
        scope.launch { load(page + 1) }
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
                text = "我的组织",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onJoinScan) { Text("扫码加入") }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OrgListKind.entries.forEach { option ->
                FilterChip(
                    selected = kind == option,
                    onClick = { kind = option },
                    label = { Text(option.label) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OrgApplyStatus.entries.forEach { option ->
                FilterChip(
                    selected = status == option,
                    onClick = { status = option },
                    label = { Text(option.label) },
                )
            }
        }

        Text(
            text = "这里只有你参与过的组织；加入要扫组织码，或在组织详情里申请。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                loading && items.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                items.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = error ?: "这个列表还是空的",
                        color = if (error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { scope.launch { load(1) } }) { Text("重试") }
                }

                else -> LazyColumn(state = listState) {
                    items(items, key = { "${kind.name}-$it.id" }) { item ->
                        OrgRow(
                            item = item,
                            fallbackStatus = status,
                            onClick = { onOpenOrg(item.id, item.name) },
                        )
                    }
                    if (!lastPage) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (loadingMore) {
                                    CircularProgressIndicator(Modifier.size(20.dp))
                                } else {
                                    TextButton(onClick = { scope.launch { load(page + 1) } }) {
                                        Text("加载更多")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrgRow(
    item: OrgListItem,
    fallbackStatus: OrgApplyStatus,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarImage(
                    url = item.avatar,
                    name = item.name,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        val identity = orgIdentityText(item.identity)
                        if (identity.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Tag(text = identity, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        text = buildString {
                            append("成员 ${item.memberCount}")
                            if (item.energy > 0) append(" · 活跃 ${orgNumberText(item.energy)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = buildList {
                            if (item.timeText.isNotBlank()) add("${item.timeText} 申请")
                            orgProcessText(item.processStatus)
                                .takeIf { it.isNotEmpty() }
                                ?.let { add(it) }
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                val label = OrgApplyStatus.of(item.status)?.label ?: fallbackStatus.label
                val tint = when (OrgApplyStatus.of(item.status) ?: fallbackStatus) {
                    OrgApplyStatus.PASSED -> MaterialTheme.colorScheme.primary
                    OrgApplyStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                    OrgApplyStatus.REJECTED -> MaterialTheme.colorScheme.error
                }
                Tag(text = label, color = tint)
            }

            if (item.auditRemark.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "驳回理由：${item.auditRemark}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
