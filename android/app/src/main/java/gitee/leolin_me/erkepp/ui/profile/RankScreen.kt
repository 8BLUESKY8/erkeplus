package gitee.leolin_me.erkepp.ui.profile

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import gitee.leolin_me.erkepp.ui.InlineLoading
import gitee.leolin_me.erkepp.ui.PageLoading
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.AppLog
import gitee.leolin_me.erkepp.data.RankEntry
import gitee.leolin_me.erkepp.data.RankRepository
import gitee.leolin_me.erkepp.data.RankScope
import gitee.leolin_me.erkepp.data.rankHoursText
import gitee.leolin_me.erkepp.data.rankScoreText
import gitee.leolin_me.erkepp.data.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 成绩排行榜：对应官方 `#/reportRank`。
 *
 * 结构照官方：顶部是范围（全校 / 院系 / 专业 / 班级），下面是前三名和完整榜单，
 * 底部固定一条「我的排名」。两个接口都只读，见 [RankRepository]。
 *
 * 榜上的「成绩」就是学分，服务端按学校配置折好，这里直接显示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankScreen(
    token: String,
    repository: RankRepository,
    /** 本人的学号，用来在榜单里高亮自己那一行。 */
    myStudentNo: String,
    /** 用用户的「预加载」开关决定提前翻页与预取头像。 */
    preloadEnabled: Boolean,
    initialScope: RankScope = RankScope.DEFAULT,
    /** 点某个人进他的主页；`id <= 0` 的条目不响应。 */
    onOpenPerson: (Long) -> Unit = {},
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var current by remember { mutableStateOf(initialScope) }
    var entries by remember { mutableStateOf<List<RankEntry>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var lastPage by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var myRank by remember { mutableStateOf<RankEntry?>(null) }

    suspend fun load(targetPage: Int) {
        if (targetPage > 1) loadingMore = true else loading = true
        try {
            val result = repository.ranks(token, current, targetPage)
            total = result.total
            lastPage = result.lastPage
            entries = if (targetPage == 1) result.list else entries + result.list
            page = targetPage
            error = null
        } catch (cancelled: CancellationException) {
            // 换范围时旧请求会被取消，这不算错误，也不能把取消当成失败写进界面
            throw cancelled
        } catch (failure: Throwable) {
            if (targetPage == 1) error = failure.toUserMessage()
        } finally {
            loading = false
            loadingMore = false
        }
    }

    // 换范围就从头拉。榜单先拉第一页，再问本人名次：全校这种大范围并行请求时，
    // self/rank 有时会返回空的 rownum（服务端像是要先有榜单才排得出名次）。
    LaunchedEffect(current) {
        entries = emptyList()
        total = 0
        page = 0
        lastPage = true
        error = null
        myRank = null
        load(1)
        myRank = runCatching { repository.myRank(token, current) }.getOrNull()
    }

    val listState = rememberLazyListState()
    // 用布局信息判断「快到底了」，不要用 entries.size：列表刚拿到第一页时布局还没量完，
    // derivedStateOf 会短暂给出 true，结果一进页面就多翻一页。
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            shouldLoadMore(last, info.totalItemsCount, preloadEnabled)
        }
    }
    LaunchedEffect(nearEnd, entries.size, lastPage) {
        if (!nearEnd || loadingMore || loading || lastPage) return@LaunchedEffect
        // 再确认一次：状态更新和重新布局之间可能差一帧
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (!shouldLoadMore(last, info.totalItemsCount, preloadEnabled)) return@LaunchedEffect
        load(page + 1)
    }

    // 头像预取：只热后面的、还没滚到的几张，滚到时不用等网络
    val prefetchedAvatars = remember { mutableSetOf<String>() }
    LaunchedEffect(entries.size, preloadEnabled) {
        if (!preloadEnabled || entries.isEmpty()) return@LaunchedEffect
        // LazyColumn 的第 0 项是前三名，entries[i] 在第 i+1 项，所以从可见末项 +2 开始取
        val visibleEnd = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val pending = entries.asSequence()
            .drop((visibleEnd + 2).coerceAtLeast(0))
            .mapNotNull { it.avatar }
            .filter { prefetchedAvatars.add(it) }
            .take(12)
            .toList()
        if (pending.isNotEmpty()) {
            AppLog.d("rank", "prefetch ${pending.size} avatars")
            prefetchAvatars(context, pending)
        }
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
                text = "成绩排行榜",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RankScope.entries.forEach { option ->
                FilterChip(
                    selected = current == option,
                    onClick = { current = option },
                    label = { Text(option.label) },
                )
            }
        }

        Text(
            text = "成绩就是学分，由学校按学时折算；并列同名次。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
        )
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            when {
                loading && entries.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { PageLoading() }

                entries.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = error ?: "这份榜单还没有数据",
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { scope.launch { load(1) } }) { Text("重试") }
                }

                else -> LazyColumn(state = listState) {
                    item { Podium(entries.take(3), current, onOpenPerson) }
                    items(entries.drop(3)) { entry ->
                        RankRow(
                            entry = entry,
                            scope = current,
                            isMe = myStudentNo.isNotBlank() && entry.studentNo == myStudentNo,
                            onOpenPerson = onOpenPerson,
                        )
                    }
                    if (!lastPage) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) { InlineLoading(size = 20.dp) }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }

        myRank?.let { MyRankBar(entry = it, scope = current) }
    }
}

/** 前三名：2-1-3 排开，冠军大一号。 */
@Composable
private fun Podium(entries: List<RankEntry>, scope: RankScope, onOpenPerson: (Long) -> Unit) {
    if (entries.isEmpty()) return
    val order = listOfNotNull(entries.getOrNull(1), entries.getOrNull(0), entries.getOrNull(2))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            order.forEach { entry ->
                PodiumItem(
                    entry = entry,
                    scope = scope,
                    onOpenPerson = onOpenPerson,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PodiumItem(
    entry: RankEntry,
    scope: RankScope,
    onOpenPerson: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val champion = entry.rownum == 1
    Column(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clickable(enabled = entry.userId > 0) { onOpenPerson(entry.userId) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 冠军头像大一号，给他多出来的 12dp 由两侧补在上方，名字和三行才会对齐
        Spacer(Modifier.height(if (champion) 0.dp else 12.dp))
        RankBadge(rank = entry.rownum)
        Spacer(Modifier.height(8.dp))
        AvatarImage(
            url = entry.avatar,
            name = entry.name,
            contentDescription = null,
            modifier = Modifier
                .size(if (champion) 56.dp else 44.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (champion) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = rankScoreText(entry.score),
            style = if (champion) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (champion) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        val subtitle = podiumSubtitle(entry, scope)
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val color = when (rank) {
        1 -> Color(0xFFD9A21B)
        2 -> Color(0xFF9AA0A6)
        3 -> Color(0xFFB07C4F)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(color = color, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

@Composable
private fun RankRow(
    entry: RankEntry,
    scope: RankScope,
    isMe: Boolean,
    onOpenPerson: (Long) -> Unit = {},
) {
    val background = if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(enabled = entry.userId > 0) { onOpenPerson(entry.userId) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rankText(entry.rownum),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp),
        )
        Spacer(Modifier.width(8.dp))
        AvatarImage(
            url = entry.avatar,
            name = entry.name,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isMe) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "我",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            val subtitle = rankSubtitle(entry, scope)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = rankScoreText(entry.score),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                // 0 分的人很多，压成灰色，蓝色的才是有学时的
                color = if (entry.score > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = "${rankHoursText(entry.hours)} 学时",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 底部固定的「我的排名」，官方也是钉在底部的。 */
@Composable
private fun MyRankBar(entry: RankEntry, scope: RankScope) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rankText(entry.rownum),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(36.dp),
            )
            Spacer(Modifier.width(8.dp))
            AvatarImage(
                url = entry.avatar,
                name = entry.name,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = "（我自己）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val subtitle = rankSubtitle(entry, scope)
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = rankScoreText(entry.score),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${rankHoursText(entry.hours)} 学时",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 名次：100 名开外只显示「100+」，和官方一致。 */
private fun rankText(rownum: Int): String = when {
    rownum <= 0 -> "—"
    rownum > 100 -> "100+"
    else -> rownum.toString()
}

/**
 * 该不该取下一页。
 *
 * 开了「预加载下一页」就提前 8 条开始取，关掉只在剩 2 条时才取——省流量省电，
 * 代价是滚到底会有一下短暂的空档。和发现页用同一套阈值。
 */
internal fun shouldLoadMore(lastVisible: Int, itemCount: Int, preloadEnabled: Boolean): Boolean {
    if (itemCount <= 0 || lastVisible < 0) return false
    val threshold = if (preloadEnabled) 8 else 2
    return lastVisible >= itemCount - threshold
}

/** 副标题随范围变：全校看院系 + 专业，院系看专业 + 班级，专业看班级，本班看专业。 */
internal fun rankSubtitle(entry: RankEntry, scope: RankScope): String = when (scope) {
    RankScope.SCHOOL -> listOf(entry.collegeName, entry.majorName)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    RankScope.COLLEGE -> listOf(entry.majorName, entry.className)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    RankScope.MAJOR -> entry.className.ifBlank { entry.majorName }

    RankScope.CLASS -> entry.majorName.ifBlank { entry.className }
}

/**
 * 前三名的副标题：那一栏只有屏宽的三分之一，放不下「学院 · 专业」，
 * 所以各范围只取最能把人区分开的那一项。
 */
private fun podiumSubtitle(entry: RankEntry, scope: RankScope): String = when (scope) {
    RankScope.SCHOOL -> entry.collegeName
    RankScope.COLLEGE -> entry.majorName
    RankScope.MAJOR -> entry.className
    RankScope.CLASS -> entry.majorName
}
