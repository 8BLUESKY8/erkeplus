package gitee.leolin_me.erkepp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.data.ActivityItem
import gitee.leolin_me.erkepp.data.ActivityQuery
import gitee.leolin_me.erkepp.data.ActivityRepository
import gitee.leolin_me.erkepp.data.toUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val StatusOptions = listOf(
    "5" to "报名中",
    "6" to "待开始",
    "7" to "进行中",
    "4,5,6,7" to "全部",
)

/**
 * 发现页（概念版）：双列封面卡片流。
 *
 * 数据全部来自官方接口：列表 `/page/activity/list`、分类 `/dict/activity/choice-sort`。
 */
@Composable
fun DiscoverScreen(
    token: String,
    repository: ActivityRepository,
    onOpenActivity: (Long) -> Unit,
    onOpenProfile: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    var search by rememberSaveable { mutableStateOf("") }
    var debouncedSearch by remember { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("5") }
    var classifyId by rememberSaveable { mutableIntStateOf(0) }
    var sortType by rememberSaveable { mutableIntStateOf(1) }

    var items by remember { mutableStateOf<List<ActivityItem>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf(listOf(0 to "全部")) }

    fun query(targetPage: Int) = ActivityQuery(
        page = targetPage,
        status = statusFilter,
        classifyId = classifyId,
        sortType = sortType,
        search = debouncedSearch,
    )

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching { repository.list(token, query(1)) }
                .onSuccess {
                    items = it.items
                    total = it.total
                    page = 1
                }
                .onFailure { error = it.toUserMessage() }
            loading = false
        }
    }

    fun loadMore() {
        if (loadingMore || items.size >= total) return
        scope.launch {
            loadingMore = true
            runCatching { repository.list(token, query(page + 1)) }
                .onSuccess {
                    items = items + it.items
                    page = it.pageNum
                    total = it.total
                }
                .onFailure { error = it.toUserMessage() }
            loadingMore = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching { repository.categories(token) }
            .onSuccess { if (it.isNotEmpty()) categories = it }
    }

    LaunchedEffect(search) {
        delay(400)
        debouncedSearch = search
    }

    LaunchedEffect(statusFilter, classifyId, sortType, debouncedSearch) {
        reload()
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            items.isNotEmpty() && lastIndex >= items.size - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) loadMore()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "发现",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenProfile) { Text("我的") }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("搜索活动名称") },
            leadingIcon = {
                Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null)
            },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(StatusOptions) { (value, label) ->
                FilterChip(
                    selected = statusFilter == value,
                    onClick = { statusFilter = value },
                    label = { Text(label) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(categories) { (id, label) ->
                    FilterChip(
                        selected = classifyId == id,
                        onClick = { classifyId = id },
                        label = { Text(label) },
                    )
                }
            }
            TextButton(onClick = { sortType = if (sortType == 1) 2 else 1 }) {
                Text(if (sortType == 1) "最新" else "最热", style = MaterialTheme.typography.bodyMedium)
            }
        }

        when {
            loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null && items.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { reload() }) { Text("重试") }
            }

            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有符合条件的活动", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    ActivityCard(item = item, onClick = { onOpenActivity(item.id) })
                }
                if (loadingMore) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(item: ActivityItem, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box {
            AsyncImage(
                model = item.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (item.recommended) {
                Badge(text = "推荐", modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
            }
            Badge(
                text = statusLabel(item.status),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                color = statusColor(item.status),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${formatHours(item.hours)}学时 · ${item.classifyName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${item.organizationName} · ${item.enrollCount}人报名",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier = Modifier, color: Color = Color(0xCC000000)) {
    Surface(
        modifier = modifier,
        color = color,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun statusLabel(status: Int): String = when (status) {
    5 -> "报名中"
    6 -> "待开始"
    7 -> "进行中"
    8 -> "已结束"
    else -> ""
}

private fun statusColor(status: Int): Color = when (status) {
    5, 6 -> Color(0xCCB7791F)
    7 -> Color(0xCC0E9668)
    8 -> Color(0xCC7A7A7A)
    else -> Color(0xCC000000)
}

private fun formatHours(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(java.util.Locale.US, "%.1f", value)
