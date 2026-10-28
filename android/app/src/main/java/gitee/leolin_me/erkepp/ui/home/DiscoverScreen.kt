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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import gitee.leolin_me.erkepp.ui.InlineLoading
import gitee.leolin_me.erkepp.ui.PageLoading
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
import gitee.leolin_me.erkepp.BuildConfig
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.data.ActivityItem
import gitee.leolin_me.erkepp.data.ActivityPhase
import gitee.leolin_me.erkepp.data.ActivityQuery
import gitee.leolin_me.erkepp.data.ActivityRepository
import gitee.leolin_me.erkepp.data.ActivitySearch
import gitee.leolin_me.erkepp.data.AppPreferences
import gitee.leolin_me.erkepp.data.blocks
import gitee.leolin_me.erkepp.data.BlockRules
import gitee.leolin_me.erkepp.data.ReenterBehavior
import gitee.leolin_me.erkepp.data.phase
import gitee.leolin_me.erkepp.data.toUserMessage
import gitee.leolin_me.erkepp.ui.formatHours
import gitee.leolin_me.erkepp.ui.gridCardWidthDp
import gitee.leolin_me.erkepp.ui.phaseColor
import gitee.leolin_me.erkepp.ui.phaseLabel
import gitee.leolin_me.erkepp.ui.prefetchCovers
import gitee.leolin_me.erkepp.ui.rememberCoverRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import gitee.leolin_me.erkepp.data.DiscoverAds
import gitee.leolin_me.erkepp.data.parseDiscoverAds

private val StatusOptions = listOf(
    // 只有状态 5 是学生端可报名的；1 和 3 是未审核，官方页面底部写「未审核」，学时也未结算
    "5" to "报名中",
    "6" to "待开始",
    "7" to "进行中",
    ALL_STATUS to "全部",
)

/** 「全部」不是固定集合：取设置里勾选的状态。 */
private const val ALL_STATUS = "all"

/**
 * 把勾选的状态拼成服务端要的逗号串。
 *
 * 空集合（用户把四个都取消了）时退回默认值，避免发出 `status=""` 这种服务端会报
 * 「未知数据错误」的请求。
 */
private fun allStatusValue(selected: Set<Int>): String =
    (if (selected.isEmpty()) setOf(5, 6, 7, 8) else selected)
        .sorted()
        .joinToString(",")

/**
 * 旧版本存在 SharedPreferences 里的筛选值。
 *
 * 旧版存的是具体状态串，其中 `4,5,6,7` 是 0.8.1 的「全部」、`1,3,5` 是 0.8.2 早期错写成
 * 「报名中」的值。现在「全部」改成了勾选集合，这些旧值一律算作「全部」。
 */
private fun normalizeStatusFilter(value: String): String = when (value) {
    "5", "6", "7" -> value
    "1,3,5" -> "5"
    "4,5,6,7", "4,5,6,7,8", "1,3,4,5,6,7,8" -> ALL_STATUS
    else -> value
}

/** 列表每页条数（官方 H5 传 10，这里传 20 少翻一倍页数）。 */
private const val PAGE_SIZE = 20

/** 搜索时最多往后扫多少页，防止服务端 total 异常时无限翻页。 */
private const val MAX_SCAN_PAGES = 40

/**
 * 未审核（1）与被驳回（3）的文案。
 *
 * 这两个编号没有阶段，但「已报名」列表里可能还留着（活动在报名后被驳回）。
 * 与其画一个空角标，不如直说。
 */
private fun unapprovedLabel(status: Int): String = when (status) {
    0 -> "未发布"
    1 -> "审核中"
    3 -> "被驳回"
    else -> ""
}

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
    /** 调试用：启动时预填的搜索词（`am start --es search 挑战杯`）。 */
    initialSearch: String = "",
    onOpenActivity: (Long) -> Unit,
    onOpenBlockRules: () -> Unit = {},
    onOpenScan: () -> Unit = {},
    adsActive: Boolean = true,
    onOpenAd: (String) -> Unit = {},
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
    var ads by remember { mutableStateOf(DiscoverAds()) }
    LaunchedEffect(context) {
        ads = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("ads.json").bufferedReader().use { parseDiscoverAds(it.readText()) } }
                .getOrDefault(DiscoverAds())
        }
    }
    val prefetchedCovers = remember { mutableSetOf<String>() }

    var search by rememberSaveable { mutableStateOf(initialSearch) }
    var debouncedSearch by remember { mutableStateOf(initialSearch.trim()) }
    var statusFilter by rememberSaveable { mutableStateOf(normalizeStatusFilter(preferences.filterStatus)) }
    var classifyId by rememberSaveable { mutableIntStateOf(preferences.filterClassify) }
    var sortType by rememberSaveable { mutableIntStateOf(preferences.filterSort) }
    var enrolledOnly by rememberSaveable { mutableStateOf(preferences.filterEnrolled) }
    var hoursMin by remember { mutableStateOf(preferences.filterHoursMin.toDoubleOrNull()) }
    var hoursMax by remember { mutableStateOf(preferences.filterHoursMax.toDoubleOrNull()) }
    var showFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(statusFilter, classifyId, sortType, enrolledOnly, hoursMin, hoursMax, preferences.loadStatuses) {
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
    // 搜索需要全量数据：官方接口的 search 不作用于 list，只能本地匹配，所以要逐页拉完
    var scanning by remember { mutableStateOf(false) }
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
        pageSize = PAGE_SIZE,
        // 「全部」用设置里勾选的状态，其余标签是固定值
        status = if (statusFilter == ALL_STATUS) allStatusValue(preferences.loadStatuses) else statusFilter,
        classifyId = classifyId,
        sortType = sortType,
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
        noProgressCount = 0
        AppLog.d(
            "discover",
            "reload status=$statusFilter sort=$sortType classify=$classifyId enrolled=$enrolledOnly hours=$hoursMin..$hoursMax",
        )
        scope.launch {
            loading = true
            error = null
            // 先清空旧数据：否则新筛选的 total 会和上一批 items 拼在一起，
            // 出现「已加载 20 / 共 5」这种自相矛盾的数字
            items = emptyList()
            total = 0
            runCatching { fetch(1) }
                .onSuccess {
                    AppLog.d("discover", "reload ok total=${it.total} got=${it.items.size}")
                    if (gen == generation) {
                        items = it.items
                        total = it.total
                        page = 1
                        // 不满一页就说明到底了，比信任 total 更稳
                        if (it.items.size < PAGE_SIZE) endReached = true
                        gridState.requestScrollToItem(0)
                    }
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    AppLog.d("discover", "reload fail ${it.javaClass.simpleName} ${it.message}")
                    if (gen == generation) error = it.toUserMessage()
                }
            if (gen == generation) loading = false
        }
    }

    /**
     * 拉下一页并追加，返回这次是否拿到了新数据。
     *
     * [loadingMore] 兼作互斥锁：滚动加载和搜索时的连续扫描都走这里，
     * 保证同一时刻只有一个列表请求在飞。
     */
    suspend fun fetchNext(): Boolean {
        if (loading || loadingMore || endReached || !hasMore) return false
        val gen = generation
        loadingMore = true
        var added = 0
        try {
            AppLog.d("discover", "more request page=${page + 1} loaded=${items.size} total=$total")
            val next = fetch(page + 1)
            if (gen != generation) return false
            val before = items.size
            items = (items + next.items).distinctBy { item -> item.id }
            page = next.pageNum
            total = next.total
            added = items.size - before
            if (added == 0 && next.items.isNotEmpty()) noProgressCount++ else noProgressCount = 0
            AppLog.d("discover", "more ok page=${next.pageNum} got=${next.items.size} added=$added")
            if (next.items.isEmpty() ||
                next.items.size < PAGE_SIZE ||
                (total > 0 && items.size >= total) ||
                noProgressCount >= 2
            ) {
                endReached = true
                AppLog.d("discover", "endReached loaded=${items.size} total=$total")
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            AppLog.d("discover", "more fail ${t.javaClass.simpleName} ${t.message}")
            if (gen == generation) error = t.toUserMessage()
            return false
        } finally {
            loadingMore = false
        }
        return added > 0
    }

    fun loadMore() {
        if (loadingMore) return
        scope.launch { fetchNext() }
    }

    /**
     * 搜索用：把列表拉到最后一页，之后所有匹配都在本地做。
     *
     * 一次搜索最多扫 [MAX_SCAN_PAGES] 页（当前全校活动约 8 页），
     * 扫完之后同一个筛选下的搜索都是瞬时的。
     */
    suspend fun scanAll() {
        if (scanning || endReached || !hasMore) return
        val gen = generation
        scanning = true
        try {
            var pages = 0
            while (gen == generation && !endReached && hasMore && pages < MAX_SCAN_PAGES) {
                pages++
                if (!fetchNext()) break
            }
            if (gen == generation) {
                val terms = ActivitySearch.terms(debouncedSearch)
                val hits = ActivitySearch.filter(items, terms).map { it.name }
                AppLog.d(
                    "discover",
                    "scan done scanned=${items.size}/$total pages=$pages matched=${hits.size} $hits",
                )
            }
        } finally {
            if (gen == generation) scanning = false
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

    // 筛选变化才重新拉列表；搜索词不参与请求，只作用于本地过滤
    LaunchedEffect(statusFilter, classifyId, sortType, enrolledOnly, preferences.loadStatuses) {
        reload()
    }

    // 搜索词一变就本地过滤；数据没拉全时先在后台把列表扫完
    LaunchedEffect(debouncedSearch, generation, loading) {
        if (debouncedSearch.isBlank() || loading) return@LaunchedEffect
        scanAll()
    }

    val blockRules = remember(blockedKeywords, blockedRegexes) {
        BlockRules.parse(blockedKeywords, blockedRegexes)
    }
    val blockedByRules = remember(items, blockRules, preferences.blockEnrolledToo) {
        if (blockRules.isEmpty) 0
        else items.count { blockRules.blocks(it, preferences.blockEnrolledToo) }
    }
    val searchTerms = remember(debouncedSearch) { ActivitySearch.terms(debouncedSearch) }
    // 用 derivedStateOf 而不是普通局部变量：下面的滚动监听在 snapshotFlow 里读这个数量，
    // 普通值只在启动时被捕获一次，会一直用旧数字
    val matchedItems by remember(searchTerms) {
        derivedStateOf { ActivitySearch.filter(items, searchTerms) }
    }
    val visibleItems by remember(searchTerms, blockRules, preferences.blockEnrolledToo) {
        derivedStateOf {
            matchedItems
                .filter { hoursMin == null || it.hours >= hoursMin!! }
                .filter { hoursMax == null || it.hours <= hoursMax!! }
                .filterNot { blockRules.blocks(it, preferences.blockEnrolledToo) }
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
            // 广告和加载提示不参与活动分页的索引计算。
            val last = info.visibleItemsInfo.lastOrNull { it.contentType == "activity" }?.index
                ?.minus(if (ads.top.isNotEmpty() || ads.showPlaceholders) 1 else 0) ?: -1
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
        // 搜索时由 scanAll 负责拉全，这里不插手，避免两边抢同一个请求锁
        if (searchTerms.isNotEmpty()) return@LaunchedEffect
        if (!loading && !loadingMore && !endReached && laidOut &&
            hasMore && visibleItems.size < 12 && autoFillCount < 20
        ) {
            autoFillCount++
            AppLog.d("discover", "auto-fill #$autoFillCount loaded=${items.size} visible=${visibleItems.size} total=$total")
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
                .padding(start = 6.dp, end = 6.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左：筛选（有生效条件时点一个小圆点）；中：搜索框；右：扫一扫
            Box {
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
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 12.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
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
            IconButton(onClick = onOpenScan) {
                Icon(
                    painter = painterResource(R.drawable.ic_scan),
                    contentDescription = "扫一扫",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 调试计数只在 debug 包出现；release 里 BuildConfig.DEBUG 是常量 false，整段会被裁掉
        if (BuildConfig.DEBUG && preferences.developerMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已加载 ${items.size} / 共 $total · 命中 ${matchedItems.size} · 屏蔽 $blockedByRules · 显示 ${visibleItems.size}",
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

        // 搜索时屏蔽数字是整列表的统计，放在这里只会混淆（命中才是用户关心的）
        if (blockedByRules > 0 && searchTerms.isEmpty()) {
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

        // 搜索是本地匹配全量列表：先说清在扫，扫完给出命中数，省得以为"只有这几条"
        if (searchTerms.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (scanning) {
                    InlineLoading(size = 12.dp, strokeWidth = 1.5.dp)
                }
                Text(
                    text = if (scanning) {
                        "正在搜索全部活动… ${items.size}/${total.coerceAtLeast(items.size)}"
                    } else {
                        "在 ${items.size} 条活动里匹配到 ${matchedItems.size} 条"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            if (ads.top.isNotEmpty() || ads.showPlaceholders) {
                item(key = "ad-top", contentType = "ad", span = { GridItemSpan(maxLineSpan) }) {
                    AdCarousel(ads.top, bottom = false, active = adsActive,
                        showPlaceholder = ads.showPlaceholders, onOpen = onOpenAd)
                }
            }
            if (visibleItems.isEmpty()) {
                item(key = "activity-state", contentType = "state", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                        when {
                            loading && items.isEmpty() -> PageLoading()

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

                            scanning && visibleItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    PageLoading(Modifier.size(40.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text("正在搜索…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            visibleItems.isEmpty() && searchTerms.isNotEmpty() ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "没有找到与「${debouncedSearch.trim()}」相关的活动",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                            visibleItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("没有匹配的活动（被屏蔽规则过滤了）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
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
                    // 搜索命中通常只有几条，标题多给一行，别让不同的活动看起来一模一样
                    titleLines = if (searchTerms.isEmpty()) titleLines else maxOf(titleLines, 2),
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
                        InlineLoading(size = 24.dp)
                    }
                }
            }
            if (ads.bottom.isNotEmpty() || ads.showPlaceholders) {
                item(key = "ad-bottom", contentType = "ad", span = { GridItemSpan(maxLineSpan) }) {
                    AdCarousel(ads.bottom, bottom = true, active = adsActive,
                        showPlaceholder = ads.showPlaceholders, onOpen = onOpenAd)
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
            // 阶段优先看时间；算不出来时才用编号兜底（「已报名」列表里会出现已加入但活动被驳回的情况）
            val phase = item.phase()
            val phaseText = phaseLabel(phase).ifEmpty { unapprovedLabel(item.status) }
            if (phaseText.isNotEmpty()) {
                Badge(
                    text = phaseText,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    color = if (phase == ActivityPhase.UNKNOWN) Color(0xCC6B7280) else phaseColor(phase),
                )
            }
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
