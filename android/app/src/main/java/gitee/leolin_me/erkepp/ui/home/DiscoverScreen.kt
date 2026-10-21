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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import gitee.leolin_me.erkepp.AppLog
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.data.ActivityItem
import gitee.leolin_me.erkepp.data.ActivityQuery
import gitee.leolin_me.erkepp.data.ActivityRepository
import gitee.leolin_me.erkepp.data.AppPreferences
import gitee.leolin_me.erkepp.data.BlockRules
import gitee.leolin_me.erkepp.data.ReenterBehavior
import gitee.leolin_me.erkepp.data.toUserMessage
import gitee.leolin_me.erkepp.ui.formatHours
import gitee.leolin_me.erkepp.ui.gridCardWidthDp
import gitee.leolin_me.erkepp.ui.prefetchCovers
import gitee.leolin_me.erkepp.ui.rememberCoverRequest
import gitee.leolin_me.erkepp.ui.statusColor
import gitee.leolin_me.erkepp.ui.statusLabel
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
    preferences: AppPreferences,
    refreshTick: Int = 0,
    reenterTick: Int = 0,
    onOpenActivity: (Long) -> Unit,
    onOpenBlockRules: () -> Unit = {},
) {
    val categoriesEnabled = preferences.categoriesEnabled
    val showCategoryTags = preferences.showCategoryTags
    val selectedCategoryIds = preferences.selectedCategoryIds
    val preloadEnabled = preferences.preloadEnabled
    val showCover = preferences.showCover
    val titleLines = preferences.titleLines
    val blockedKeywords = preferences.blockedKeywords
    val blockedRegexes = preferences.blockedRegexes
    val showHours = preferences.cardShowHours
    val showCount = preferences.cardShowCount
    val showOrganization = preferences.cardShowOrganization
    val showTime = preferences.cardShowTime
    val customText = preferences.cardCustomText
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val coverWidthDp = gridCardWidthDp()
    val context = LocalContext.current
    val prefetchedCovers = remember { mutableSetOf<String>() }

    var search by rememberSaveable { mutableStateOf("") }
    var debouncedSearch by remember { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf(preferences.filterStatus) }
    var classifyId by rememberSaveable { mutableIntStateOf(preferences.filterClassify) }
    var sortType by rememberSaveable { mutableIntStateOf(preferences.filterSort) }
    var enrolledOnly by rememberSaveable { mutableStateOf(preferences.filterEnrolled) }
    var hoursMin by remember { mutableStateOf(preferences.filterHoursMin.toDoubleOrNull()) }
    var hoursMax by remember { mutableStateOf(preferences.filterHoursMax.toDoubleOrNull()) }
    var showFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(statusFilter, classifyId, sortType, enrolledOnly, hoursMin, hoursMax) {
        preferences.filterStatus = statusFilter
        preferences.filterClassify = classifyId
        preferences.filterSort = sortType
        preferences.filterEnrolled = enrolledOnly
        preferences.filterHoursMin = hoursMin?.let { formatHours(it) } ?: ""
        preferences.filterHoursMax = hoursMax?.let { formatHours(it) } ?: ""
    }

    var items by remember { mutableStateOf<List<ActivityItem>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var autoFillCount by remember { mutableIntStateOf(0) }
    var noProgressCount by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    // total 为 0 表示服务端没有返回总数（例如 my-list），此时按“还有”处理，直到拉到空页
    val hasMore = total <= 0 || items.size < total
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

    suspend fun fetch(targetPage: Int) = if (enrolledOnly) {
        repository.myList(token, targetPage)
    } else {
        repository.list(token, query(targetPage))
    }

    fun reload() {
        val gen = generation + 1
        generation = gen
        endReached = false
        autoFillCount = 0
        AppLog.d(
            "discover",
            "reload status=$statusFilter sort=$sortType classify=$classifyId enrolled=$enrolledOnly search=$debouncedSearch hours=$hoursMin..$hoursMax",
        )
        scope.launch {
            loading = true
            error = null
            runCatching { fetch(1) }
                .onSuccess {
                    AppLog.d("discover", "reload ok total=${it.total} got=${it.items.size}")
                    if (gen == generation) {
                        items = it.items
                        total = it.total
                        page = 1
                        gridState.requestScrollToItem(0)
                    }
                }
                .onFailure {
                    AppLog.d("discover", "reload fail ${it.javaClass.simpleName} ${it.message}")
                    if (gen == generation) error = it.toUserMessage()
                }
            if (gen == generation) loading = false
        }
    }

    fun loadMore() {
        if (loadingMore || loading || endReached || !hasMore) return
        val gen = generation
        scope.launch {
            loadingMore = true
            AppLog.d("discover", "more request page=${page + 1} loaded=${items.size} total=$total")
            runCatching { fetch(page + 1) }
                .onSuccess {
                    AppLog.d(
                        "discover",
                        "more ok page=${it.pageNum} got=${it.items.size} added=${(items + it.items).distinctBy { i -> i.id }.size - items.size}",
                    )
                    if (gen == generation) {
                        val before = items.size
                        items = (items + it.items).distinctBy { item -> item.id }
                        page = it.pageNum
                        total = it.total
                        val added = items.size - before
                        if (added == 0 && it.items.isNotEmpty()) noProgressCount++ else noProgressCount = 0
                        if (it.items.isEmpty() || (total > 0 && items.size >= total) || noProgressCount >= 2) {
                            endReached = true
                            AppLog.d("discover", "endReached loaded=${items.size} total=$total")
                        }
                    }
                }
                .onFailure {
                    AppLog.d("discover", "more fail ${it.javaClass.simpleName} ${it.message}")
                    if (gen == generation) error = it.toUserMessage()
                }
            if (gen == generation) loadingMore = false
        }
    }

    // 双击「发现」图标：手动重新拉取
    LaunchedEffect(refreshTick) {
        if (refreshTick == 0) return@LaunchedEffect
        AppLog.d("discover", "manual refresh tick=$refreshTick")
        reload()
    }

    // 切回发现页：按用户设置保持位置、回到顶部或重载
    LaunchedEffect(reenterTick) {
        if (reenterTick == 0) return@LaunchedEffect
        when (preferences.reenterBehavior) {
            ReenterBehavior.TOP -> if (items.isNotEmpty()) gridState.animateScrollToItem(0)
            ReenterBehavior.RELOAD -> reload()
            else -> Unit
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

    LaunchedEffect(statusFilter, classifyId, sortType, debouncedSearch, enrolledOnly) {
        reload()
    }

    val blockRules = remember(blockedKeywords, blockedRegexes) {
        BlockRules.parse(blockedKeywords, blockedRegexes)
    }
    val blockedByRules = remember(items, blockRules) {
        if (blockRules.isEmpty) 0 else items.count { blockRules.isBlocked(it.name) }
    }
    val visibleItems by remember(blockRules) {
        derivedStateOf {
            val keyword = debouncedSearch.trim()
            items.asSequence()
                .filter { keyword.isEmpty() || it.name.contains(keyword, ignoreCase = true) }
                .filter { hoursMin == null || it.hours >= hoursMin!! }
                .filter { hoursMax == null || it.hours <= hoursMax!! }
                .filterNot { blockRules.isBlocked(it.name) }
                .toList()
        }
    }

    // 封面预取：新到的一批先解码进内存缓存，滚过去时不再临时解码，减少掉帧
    LaunchedEffect(items, coverWidthDp) {
        val pending = buildList {
            for (item in items) {
                val cover = item.cover?.takeIf { it.isNotBlank() } ?: continue
                if (prefetchedCovers.add(cover)) add(cover)
                if (size >= 12) break
            }
        }
        if (pending.isNotEmpty()) {
            AppLog.d("discover", "prefetch ${pending.size} covers at ${coverWidthDp}dp")
            prefetchCovers(context, pending, coverWidthDp)
        }
    }

    // 滚动驱动：只要接近末尾就继续加载，直到没有更多
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last to visibleItems.size
        }.collect { (last, count) ->
            val threshold = if (preloadEnabled) 8 else 2
            if (count > 0 && hasMore && last >= count - threshold) {
                loadMore()
            }
        }
    }

    // 显示不足一屏时持续补页，直到没有更多或显示够 12 条；最多 20 次兜底
    LaunchedEffect(gridState.canScrollForward, items.size, visibleItems.size) {
        val laidOut = gridState.layoutInfo.totalItemsCount > 0
        if (!loading && !loadingMore && !endReached && laidOut &&
            hasMore && visibleItems.size < 12 && autoFillCount < 20
        ) {
            autoFillCount++
            AppLog.d("discover", "auto-fill #$autoFillCount loaded=${items.size} visible=${visibleItems.size} total=$total")
            loadMore()
        }
    }

    // 搜索时如果当前页没有命中，自动往后翻页，避免"搜不到"的错觉
    LaunchedEffect(debouncedSearch, items.size) {
        val keyword = debouncedSearch.trim()
        if (keyword.isNotEmpty() && visibleItems.isEmpty() && hasMore && !loading && !loadingMore) {
            loadMore()
        }
    }

    val activeFilterCount =
        (if (classifyId != 0) 1 else 0) +
            (if (hoursMin != null || hoursMax != null) 1 else 0) +
            (if (sortType != 1) 1 else 0)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索活动名称") },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
            )
            IconButton(onClick = { showFilterSheet = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = "筛选",
                    tint = if (activeFilterCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (activeFilterCount > 0) {
                Text(
                    text = "$activeFilterCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (preferences.developerMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已加载 ${items.size} / 共 $total · 屏蔽 $blockedByRules · 显示 ${visibleItems.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (sortType == 1) "最新" else "最热",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(StatusOptions) { (value, label) ->
                FilterChip(
                    selected = !enrolledOnly && statusFilter == value,
                    onClick = {
                        enrolledOnly = false
                        statusFilter = value
                    },
                    label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                )
            }
            item {
                FilterChip(
                    selected = enrolledOnly,
                    onClick = { enrolledOnly = !enrolledOnly },
                    label = { Text("已报名", style = MaterialTheme.typography.bodySmall) },
                )
            }
        }

        if (blockedByRules > 0) {
            Text(
                text = "屏蔽规则隐藏了 $blockedByRules 条，点此去设置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBlockRules() }
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            )
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
                        showHours = showHours,
                        showCount = showCount,
                        showOrganization = showOrganization,
                        showTime = showTime,
                        customText = customText,
                        showCover = showCover,
                        coverWidthDp = coverWidthDp,
                        titleLines = titleLines,
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

    if (showFilterSheet) {
        FilterSheet(
            categories = if (categoriesEnabled) visibleCategories else emptyList(),
            classifyId = classifyId,
            onClassifyChange = { classifyId = it },
            sortType = sortType,
            onSortChange = { sortType = it },
            hoursMin = hoursMin,
            hoursMax = hoursMax,
            onHoursChange = { min, max ->
                hoursMin = min
                hoursMax = max
            },
            onReset = {
                classifyId = 0
                sortType = 1
                hoursMin = null
                hoursMax = null
            },
            onDismiss = { showFilterSheet = false },
        )
    }
}

@Composable
private fun ActivityCard(
    item: ActivityItem,
    showCategoryTags: Boolean,
    showHours: Boolean,
    showCount: Boolean,
    showOrganization: Boolean,
    showTime: Boolean,
    customText: String,
    showCover: Boolean,
    coverWidthDp: Int,
    titleLines: Int,
    onClick: () -> Unit,
) {
    val coverRequest = rememberCoverRequest(item.cover, coverWidthDp)
    // 图片解码好后淡入。直接「啪」一下贴上，滑动时会像图跟不上文字
    var coverLoaded by remember(item.id) { mutableStateOf(false) }
    val coverAlpha by animateFloatAsState(
        targetValue = if (coverLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "coverAlpha",
    )
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box {
            if (showCover) {
                AsyncImage(
                    model = coverRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { coverLoaded = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        // 淡入结束后撤掉图层，避免每张图长期多一层合成
                        .then(
                            if (coverAlpha < 1f) {
                                Modifier.graphicsLayer { alpha = coverAlpha }
                            } else {
                                Modifier
                            },
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
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
            maxLines = titleLines,
            overflow = TextOverflow.Ellipsis,
        )
        val metaFirst = buildList {
            if (showHours) add("${formatHours(item.hours)}学时")
            if (showCategoryTags && item.classifyName.isNotBlank()) add(item.classifyName)
        }.joinToString(" · ")
        val metaSecond = buildList {
            if (showCount && item.enrollCount > 0) {
                add(
                    if (item.peopleLimit > 0) {
                        "${item.enrollCount}/${item.peopleLimit}人报名"
                    } else {
                        "${item.enrollCount}人报名"
                    },
                )
            }
            if (showOrganization && item.organizationName.isNotBlank()) add(item.organizationName)
        }.joinToString(" · ")

        val timeHint = if (showTime) timeReference(item) else ""

        if (metaFirst.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = metaFirst,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (metaSecond.isNotEmpty()) {
            Text(
                text = metaSecond,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (timeHint.isNotEmpty()) {
            Text(
                text = timeHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (customText.isNotBlank()) {
            Text(
                text = customText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

/**
 * 卡片上的时间参考：报名中看报名截止，已截止看开始时间，已开始或结束看结束时间。
 * 列表接口若没返回报名截止时间，会自然退到下一档。
 */
private fun timeReference(item: ActivityItem, now: Long = System.currentTimeMillis()): String = when {
    item.enrollEndTime > now -> "报名截止 ${cardTime(item.enrollEndTime)}"
    item.startTime > now -> "开始 ${cardTime(item.startTime)}"
    item.endTime > 0 -> "结束 ${cardTime(item.endTime)}"
    else -> ""
}

private fun cardTime(millis: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(millis))

@Composable
private fun HoursFilterDialog(
    initialMin: Double?,
    initialMax: Double?,
    onDismiss: () -> Unit,
    onApply: (Double?, Double?) -> Unit,
) {
    var minText by remember { mutableStateOf(initialMin?.let { formatHours(it) } ?: "") }
    var maxText by remember { mutableStateOf(initialMax?.let { formatHours(it) } ?: "") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按学时筛选") },
        text = {
            Column {
                Text(
                    "留空表示不限",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { input -> minText = input.filter { it.isDigit() || it == '.' } },
                        label = { Text("最少") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { input -> maxText = input.filter { it.isDigit() || it == '.' } },
                        label = { Text("最多") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(minText.toDoubleOrNull(), maxText.toDoubleOrNull()) }) {
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(onClick = { onApply(null, null) }) {
                Text("清除")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    categories: List<Pair<Int, String>>,
    classifyId: Int,
    onClassifyChange: (Int) -> Unit,
    sortType: Int,
    onSortChange: (Int) -> Unit,
    hoursMin: Double?,
    hoursMax: Double?,
    onHoursChange: (Double?, Double?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var minText by remember { mutableStateOf(hoursMin?.let { formatHours(it) } ?: "") }
    var maxText by remember { mutableStateOf(hoursMax?.let { formatHours(it) } ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            if (categories.isNotEmpty()) {
                Text("分类", style = MaterialTheme.typography.titleSmall)
                LazyRow(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(categories) { (id, label) ->
                        FilterChip(
                            selected = classifyId == id,
                            onClick = { onClassifyChange(id) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Text("学时范围", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = minText,
                    onValueChange = { input -> minText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("最少") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { input -> maxText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("最多") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Text("排序", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = sortType == 1,
                    onClick = { onSortChange(1) },
                    label = { Text("最新") },
                )
                FilterChip(
                    selected = sortType == 2,
                    onClick = { onSortChange(2) },
                    label = { Text("最热") },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onReset) { Text("重置") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        onHoursChange(minText.toDoubleOrNull(), maxText.toDoubleOrNull())
                        onDismiss()
                    },
                ) {
                    Text("完成")
                }
            }
        }
    }
}
