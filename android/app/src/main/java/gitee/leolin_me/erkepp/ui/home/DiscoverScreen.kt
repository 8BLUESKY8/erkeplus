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
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.data.ActivityItem
import gitee.leolin_me.erkepp.data.ActivityQuery
import gitee.leolin_me.erkepp.data.ActivityRepository
import gitee.leolin_me.erkepp.data.BlockRules
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
    categoriesEnabled: Boolean,
    showCategoryTags: Boolean,
    selectedCategoryIds: Set<Int>,
    blockedKeywords: String,
    blockedRegexes: String,
    preloadEnabled: Boolean,
    onOpenActivity: (Long) -> Unit,
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
    val visibleCategories = remember(categories, selectedCategoryIds) {
        categories.filter { it.first == 0 || selectedCategoryIds.isEmpty() || it.first in selectedCategoryIds }
    }
    // 请求代号：筛选变化后，在途的旧请求结果一律丢弃，避免把旧数据拼进新列表
    var generation by remember { mutableIntStateOf(0) }

    fun query(targetPage: Int) = ActivityQuery(
        page = targetPage,
        status = statusFilter,
        classifyId = classifyId,
        sortType = sortType,
        search = debouncedSearch,
    )

    fun reload() {
        val gen = generation + 1
        generation = gen
        scope.launch {
            loading = true
            error = null
            runCatching { repository.list(token, query(1)) }
                .onSuccess {
                    if (gen == generation) {
                        items = it.items
                        total = it.total
                        page = 1
                        gridState.requestScrollToItem(0)
                    }
                }
                .onFailure { if (gen == generation) error = it.toUserMessage() }
            if (gen == generation) loading = false
        }
    }

    fun loadMore() {
        if (loadingMore || loading || items.size >= total) return
        val gen = generation
        scope.launch {
            loadingMore = true
            runCatching { repository.list(token, query(page + 1)) }
                .onSuccess {
                    if (gen == generation) {
                        items = (items + it.items).distinctBy { item -> item.id }
                        page = it.pageNum
                        total = it.total
                    }
                }
                .onFailure { if (gen == generation) error = it.toUserMessage() }
            if (gen == generation) loadingMore = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching { repository.categories(token) }
            .onSuccess { if (it.isNotEmpty()) categories = it }
    }

    LaunchedEffect(selectedCategoryIds) {
        if (classifyId != 0 && classifyId !in selectedCategoryIds) classifyId = 0
    }

    LaunchedEffect(search) {
        delay(400)
        debouncedSearch = search
    }

    LaunchedEffect(statusFilter, classifyId, sortType, debouncedSearch) {
        reload()
    }

    val blockRules = remember(blockedKeywords, blockedRegexes) {
        BlockRules.parse(blockedKeywords, blockedRegexes)
    }
    val query = debouncedSearch.trim()
    val visibleItems = remember(items, blockRules, query) {
        items.asSequence()
            .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
            .filterNot { blockRules.isBlocked(it.name) }
            .toList()
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val threshold = if (preloadEnabled) 8 else 2
            items.isNotEmpty() && lastIndex >= items.size - threshold
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) loadMore()
    }

    // 搜索时如果当前页没有命中，自动往后翻几页，避免"搜不到"的错觉
    LaunchedEffect(query, items.size) {
        if (query.isNotEmpty() && visibleItems.isEmpty() && items.size < total && page < 5 && !loading && !loadingMore) {
            loadMore()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            placeholder = { Text("搜索活动名称") },
            leadingIcon = {
                Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null)
            },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 4.dp),
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
            TextButton(onClick = { sortType = if (sortType == 1) 2 else 1 }) {
                Text(if (sortType == 1) "最新" else "最热", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (categoriesEnabled) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleCategories) { (id, label) ->
                    FilterChip(
                        selected = classifyId == id,
                        onClick = { classifyId = id },
                        label = { Text(label) },
                    )
                }
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

            visibleItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有匹配的活动（可能被搜索词或屏蔽规则过滤）", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visibleItems, key = { it.id }, contentType = { "activity" }) { item ->
                    ActivityCard(
                        item = item,
                        showCategoryTags = showCategoryTags,
                        onClick = { onOpenActivity(item.id) },
                    )
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
private fun ActivityCard(item: ActivityItem, showCategoryTags: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val imageRequest = remember(item.cover) {
        ImageRequest.Builder(context)
            .data(item.cover)
            .size(480, 360)
            .crossfade(false)
            .build()
    }
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box {
            AsyncImage(
                model = imageRequest,
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
            text = if (showCategoryTags && item.classifyName.isNotBlank()) {
                "${formatHours(item.hours)}学时 · ${item.classifyName}"
            } else {
                "${formatHours(item.hours)}学时"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${item.enrollCount}人报名 · ${item.organizationName}",
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
    5, 6 -> Color(0xCCC2410C)
    7 -> Color(0xCC047857)
    8 -> Color(0xCC6B7280)
    else -> Color(0xCC000000)
}

private fun formatHours(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(java.util.Locale.US, "%.1f", value)
