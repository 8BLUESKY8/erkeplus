package gitee.leolin_me.erkepp.ui.org

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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import gitee.leolin_me.erkepp.ui.InlineLoading
import gitee.leolin_me.erkepp.ui.PageLoading
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gitee.leolin_me.erkepp.AppLog
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.data.OrgChoices
import gitee.leolin_me.erkepp.data.OrgNature
import gitee.leolin_me.erkepp.data.OrganizationRepository
import gitee.leolin_me.erkepp.data.toUserMessage
import gitee.leolin_me.erkepp.ui.gridCardWidthDp
import gitee.leolin_me.erkepp.ui.prefetchCovers
import gitee.leolin_me.erkepp.ui.profile.shouldLoadMore
import gitee.leolin_me.erkepp.ui.rememberCoverRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 目录页的三个 tab，和官方一致：未申请（全部组织）/ 已申请 / 已加入。 */
private enum class OrgTab(val label: String) {
    ALL("未申请"),
    APPLIED("已申请"),
    JOINED("已加入"),
}

/** 角标的语气，颜色在 Composable 里查（fetch 不是 Composable，拿不到主题）。 */
private enum class BadgeKind { NORMAL, PENDING, DANGER }

/** 三个接口的返回长得都不一样，统一成卡片要的东西。 */
private data class OrgCell(
    val id: Long,
    val name: String,
    val avatar: String?,
    val meta: String,
    val intro: String,
    val badge: String?,
    val badgeKind: BadgeKind,
)

/**
 * 所有组织，对应官方 `#/service/organize`（页面标题「校园组织」）。
 *
 * 版式照发现页：药丸搜索框、chip 行、双列卡片网格。三个 tab 对应官方那三个：
 * - 未申请：`organization/page/organization/list`（分类 + 排序 + 分页）
 * - 已申请：`organization/my-apply-list1`
 * - 已加入：`organization/my-list`（按分类，不带 status）
 *
 * 搜索是本地做的：服务端不认 `search`（实测传「协会」「人工智能」都还是 140 条），
 * 所以搜的时候把当前 tab + 分类的整份列表拉完再匹配，结果按「tab|分类|排序」缓存。
 *
 * 翻页听「预加载下一页」：开着时剩 8 条就取下一页并预取图片，关掉剩 2 条才取。
 */
@Composable
fun AllOrganizationsScreen(
    token: String,
    repository: OrganizationRepository,
    preloadEnabled: Boolean,
    onOpenOrg: (Long, String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val coverWidthDp = gridCardWidthDp()

    var choices by remember { mutableStateOf(OrgChoices.FALLBACK) }
    var tab by remember { mutableStateOf(OrgTab.ALL) }
    var nature by remember { mutableIntStateOf(0) }
    // 0 = 最新、1 = 最热：官方下拉就是这么发给接口的
    var sortType by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    var items by remember { mutableStateOf<List<OrgCell>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var lastPage by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var matches by remember { mutableStateOf<List<OrgCell>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val listCache = remember { mutableMapOf<String, List<OrgCell>>() }

    suspend fun fetch(current: OrgTab, target: Int): Pair<List<OrgCell>, Pair<Int, Boolean>> = when (current) {
        OrgTab.ALL -> {
            val result = repository.catalog(token, "", nature, sortType, target)
            result.list.map { item ->
                OrgCell(
                    id = item.id,
                    name = item.name,
                    avatar = item.avatar,
                    meta = "成员 ${item.memberCount} · 活跃 ${orgNumberText(item.energy)}",
                    intro = item.introduce,
                    badge = when {
                        item.joined -> "已加入"
                        item.canApply -> "申请加入"
                        else -> null
                    },
                    badgeKind = BadgeKind.NORMAL,
                )
            } to (result.total to result.lastPage)
        }

        OrgTab.APPLIED -> {
            val result = repository.applied(token, nature, target)
            result.list.map { item ->
                OrgCell(
                    id = item.organizationId,
                    name = item.name,
                    avatar = item.avatar,
                    meta = buildString {
                        append("成员 ${item.memberCount}")
                        if (item.applyTime.isNotBlank()) append(" · ${item.applyTime} 申请")
                    },
                    intro = if (item.refuseReason.isNotBlank()) "驳回理由：${item.refuseReason}" else "",
                    badge = when (item.applyStatus) {
                        2 -> "已通过"
                        3 -> "已驳回"
                        else -> "待审核"
                    },
                    badgeKind = when (item.applyStatus) {
                        2 -> BadgeKind.NORMAL
                        3 -> BadgeKind.DANGER
                        else -> BadgeKind.PENDING
                    },
                )
            } to (result.total to result.lastPage)
        }

        OrgTab.JOINED -> {
            val result = repository.joined(token, nature, target)
            result.list.map { item ->
                OrgCell(
                    id = item.id,
                    name = item.name,
                    avatar = item.avatar,
                    meta = "成员 ${item.memberCount} · 活跃 ${orgNumberText(item.energy)}",
                    intro = item.introduce,
                    badge = "已加入",
                    badgeKind = BadgeKind.NORMAL,
                )
            } to (result.total to result.lastPage)
        }
    }

    suspend fun load(target: Int) {
        if (target == 1) {
            loading = true
            error = null
        } else {
            loadingMore = true
        }
        try {
            val (cells, meta) = fetch(tab, target)
            items = if (target == 1) cells else items + cells
            total = meta.first
            lastPage = meta.second
            page = target
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            AppLog.d("org", "catalog load failed tab=${tab.name} page=$target ${failure.message}")
            if (target == 1) error = failure.toUserMessage()
        } finally {
            loading = false
            loadingMore = false
        }
    }

    /** 拉当前 tab + 分类的整份列表（分页到底），给本地搜索用。 */
    suspend fun fullList(current: OrgTab): List<OrgCell> {
        val key = "${current.name}|$nature|$sortType"
        listCache[key]?.let { return it }
        val collected = mutableListOf<OrgCell>()
        var cursor = 1
        while (cursor <= 40) {
            val (cells, meta) = try {
                fetch(current, cursor)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                AppLog.d("org", "search pages failed at $cursor ${failure.message}")
                break
            }
            collected += cells
            if (meta.second || cells.isEmpty()) break
            cursor++
        }
        listCache[key] = collected
        return collected
    }

    LaunchedEffect(Unit) { choices = repository.choices(token) }

    LaunchedEffect(search) {
        if (search.trim() == query) return@LaunchedEffect
        delay(350)
        query = search.trim()
    }

    LaunchedEffect(tab, nature, sortType, query) {
        if (query.isEmpty()) {
            matches = null
            searching = false
            items = emptyList()
            page = 0
            lastPage = true
            load(1)
            return@LaunchedEffect
        }
        searching = true
        val all = fullList(tab)
        val lowered = query.lowercase()
        matches = all.filter {
            it.name.lowercase().contains(lowered) || it.intro.lowercase().contains(lowered)
        }
        searching = false
        AppLog.d("org", "search \"$query\" tab=${tab.name} hits=${matches?.size} of ${all.size}")
    }

    val shown = matches ?: items
    val gridState = rememberLazyGridState()
    // key 里不能放 loadingMore：那会在请求发出后把效果重启，翻页永远空转
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            shouldLoadMore(last, info.totalItemsCount, preloadEnabled)
        }
    }
    LaunchedEffect(nearEnd, items.size, lastPage, matches) {
        if (matches != null) return@LaunchedEffect
        if (!nearEnd || loadingMore || lastPage || items.isEmpty()) return@LaunchedEffect
        val info = gridState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (!shouldLoadMore(last, info.totalItemsCount, preloadEnabled)) return@LaunchedEffect
        scope.launch { load(page + 1) }
    }

    // 图片预取：只热屏幕外还没滚到的那几张
    val prefetched = remember { mutableSetOf<String>() }
    LaunchedEffect(shown.size, preloadEnabled, matches) {
        if (!preloadEnabled || shown.isEmpty()) return@LaunchedEffect
        val visibleEnd = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val pending = shown.asSequence()
            .drop((visibleEnd + 1).coerceAtLeast(0))
            .mapNotNull { it.avatar }
            .filter { prefetched.add(it) }
            .take(12)
            .toList()
        if (pending.isNotEmpty()) {
            AppLog.d("org", "prefetch ${pending.size} covers")
            prefetchCovers(context, pending, coverWidthDp)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 标题行沿用其它二级页：返回 + 页面名
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                text = "所有组织",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }

        // 搜索：药丸形状，和发现页一致
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("搜索组织名称") },
            leadingIcon = {
                Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null)
            },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    TextButton(onClick = { search = "" }) { Text("清除") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
        )

        // 三个 tab：未申请 / 已申请 / 已加入
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OrgTab.entries.forEach { option ->
                FilterChip(
                    selected = tab == option,
                    onClick = { tab = option },
                    label = { Text(option.label, style = MaterialTheme.typography.bodySmall) },
                )
            }
        }

        // 分类：用官方字典给的顺序和名字
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(choices.natures.size) { index ->
                val option: OrgNature = choices.natures[index]
                FilterChip(
                    selected = nature == option.id,
                    onClick = { nature = option.id },
                    label = { Text(option.name, style = MaterialTheme.typography.bodySmall) },
                )
            }
        }

        // 排序只对「未申请」有意义；另两个 tab 是我自己的记录，顺序由时间决定
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    searching -> "正在翻整份列表比对…"
                    matches != null -> "本地匹配到 ${matches!!.size} 个组织（服务端不认搜索词）"
                    total > 0 -> "共 $total 个组织"
                    else -> "校园里的组织都在这儿"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (tab == OrgTab.ALL) {
                choices.sorts.forEach { option ->
                    TextButton(onClick = { sortType = option.id }) {
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (sortType == option.id) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }

        when {
            (loading || searching) && shown.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { PageLoading() }

            shown.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = error ?: when {
                        query.isNotEmpty() -> "没搜到这个组织"
                        tab == OrgTab.APPLIED -> "还没申请过任何组织"
                        tab == OrgTab.JOINED -> "还没加入任何组织"
                        else -> "还没有组织"
                    },
                    color = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { scope.launch { load(1) } }) { Text("重试") }
            }

            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }, contentType = { "org" }) { cell ->
                    OrgCard(cell, coverWidthDp) { onOpenOrg(cell.id, cell.name) }
                }
                if (matches == null && !lastPage) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loadingMore) {
                                InlineLoading(size = 22.dp)
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

@Composable
private fun OrgCard(cell: OrgCell, coverWidthDp: Int, onClick: () -> Unit) {
    val coverRequest = rememberCoverRequest(cell.avatar, coverWidthDp)
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (cell.avatar != null) {
                    // 组织 logo 大多是方的：1:1 铺满（Crop），既不留白边也不会把圆形徽记裁掉
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // 没有 logo 时整块铺满：用主题容器色（不是灰），首字用对应的对比色。
                    // 用 primary 太扎眼——没 logo 的组织不少，一屏会变成一片亮蓝
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = cell.name.take(1),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            if (cell.badge != null) {
                val tint = when (cell.badgeKind) {
                    BadgeKind.NORMAL -> MaterialTheme.colorScheme.primary
                    BadgeKind.PENDING -> MaterialTheme.colorScheme.tertiary
                    BadgeKind.DANGER -> MaterialTheme.colorScheme.error
                }
                OrgBadge(cell.badge, tint, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = cell.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = cell.meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (cell.intro.isNotBlank()) {
            Text(
                text = cell.intro,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OrgBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
