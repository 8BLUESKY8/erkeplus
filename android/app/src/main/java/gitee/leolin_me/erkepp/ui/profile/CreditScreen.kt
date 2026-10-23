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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gitee.leolin_me.erkepp.data.CreditDetail
import gitee.leolin_me.erkepp.data.CreditGrade
import gitee.leolin_me.erkepp.data.CreditRecord
import gitee.leolin_me.erkepp.data.CreditRepository
import gitee.leolin_me.erkepp.data.gradeOf
import gitee.leolin_me.erkepp.data.toUserMessage
import gitee.leolin_me.erkepp.ui.rememberCoverRequest
import kotlinx.coroutines.launch

/**
 * 诚信值页：对应官方 `#/honestValue`。
 *
 * 结构照官方：分数与等级在最上面，下面是待守约 / 已守约 / 已失约 / 申诉记录四类记录。
 * 三个接口都只读，见 [CreditRepository]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditScreen(
    token: String,
    repository: CreditRepository,
    onOpenActivity: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<CreditDetail?>(null) }
    var grades by remember { mutableStateOf<List<CreditGrade>>(emptyList()) }
    var showRules by remember { mutableStateOf(false) }

    var tab by remember { mutableIntStateOf(TAB_PENDING) }
    var records by remember { mutableStateOf<List<CreditRecord>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var listLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { repository.detail(token) }
            .onSuccess { detail = it; error = null }
            .onFailure { error = it.toUserMessage() }
        grades = runCatching { repository.grades(token) }.getOrDefault(emptyList())
        loading = false
    }

    suspend fun loadRecords(targetPage: Int) {
        listLoading = true
        runCatching { repository.records(token, tab, targetPage) }
            .onSuccess { (count, list) ->
                total = count
                records = if (targetPage == 1) list else records + list
                page = targetPage
                error = null
            }
            .onFailure { error = it.toUserMessage() }
        listLoading = false
    }

    // 切标签重拉第一页
    LaunchedEffect(tab) {
        records = emptyList()
        total = 0
        loadRecords(1)
    }

    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= records.size - 3
        }
    }
    LaunchedEffect(nearEnd, records.size, total) {
        if (nearEnd && !listLoading && records.size < total) loadRecords(page + 1)
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
                text = "诚信值",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (detail != null) {
                TextButton(onClick = { showRules = true }) { Text("规则") }
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            detail == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(error ?: "没拿到诚信值", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = {
                    scope.launch {
                        loading = true
                        runCatching { repository.detail(token) }
                            .onSuccess { detail = it; error = null }
                            .onFailure { error = it.toUserMessage() }
                        loading = false
                    }
                }) { Text("重试") }
            }

            else -> {
                val current = detail!!
                ScoreCard(
                    credit = current.credit,
                    grade = grades.gradeOf(current.credit),
                    updateTime = current.updateTime,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TabOptions.forEach { (type, label) ->
                        FilterChip(
                            selected = tab == type,
                            onClick = { tab = type },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                when {
                    records.isEmpty() && listLoading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    records.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = error ?: emptyText(tab),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> LazyColumn(state = listState) {
                        items(records, key = { "${tab}-${it.relationId}-${it.startTime}" }) { record ->
                            // 官方点卡片会跳到对应活动，这里保持一致
                            RecordRow(record = record, onClick = { onOpenActivity(record.relationId) })
                        }
                        if (records.size < total) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator(Modifier.size(20.dp)) }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            title = { Text("诚信值规则") },
            text = {
                Column {
                    Text(
                        text = "分数与等级由学校配置，下面是当前生效的分数段：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    grades.forEach { grade ->
                        Text("${grade.minScore} ~ ${grade.maxScore}  ${grade.grade}")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "报名后不签到、不签退会扣分；被扣分可以在官方页面申诉。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showRules = false }) { Text("知道了") } },
        )
    }
}

private const val TAB_PENDING = 1
private const val TAB_KEPT = 2
private const val TAB_BROKEN = 3
private const val TAB_APPEAL = 4

private val TabOptions = listOf(
    TAB_PENDING to "待守约",
    TAB_KEPT to "已守约",
    TAB_BROKEN to "已失约",
    TAB_APPEAL to "申诉记录",
)

private fun emptyText(tab: Int): String = when (tab) {
    TAB_PENDING -> "没有待守约的活动"
    TAB_KEPT -> "还没有守约记录"
    TAB_BROKEN -> "没有失约记录，继续保持"
    else -> "没有申诉记录"
}

@Composable
private fun ScoreCard(credit: Double, grade: String, updateTime: Long) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatScore(credit),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (grade.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "信用$grade",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
            if (updateTime > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "更新于 ${shortDate(updateTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecordRow(record: CreditRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (record.logo != null) {
                AsyncImage(
                    model = rememberCoverRequest(record.logo, 56, maxWidthPx = 224),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val line = buildList {
                if (record.startTime > 0) add(shortRange(record.startTime, record.endTime))
                if (record.comment.isNotBlank()) add(record.comment)
            }.joinToString(" · ")
            if (line.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val amount = record.amount
        when {
            amount != 0.0 -> Text(
                text = (if (record.operator == "+") "+" else "-") + formatScore(kotlin.math.abs(amount)),
                style = MaterialTheme.typography.titleSmall,
                color = if (record.operator == "+") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )

            record.operator == "+" -> Text(
                text = "守约",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun formatScore(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", value)

private fun shortDate(millis: Long): String =
    java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.CHINA).format(java.util.Date(millis))

private fun shortRange(start: Long, end: Long): String {
    val format = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
    val from = format.format(java.util.Date(start))
    return if (end > start) "$from 至 ${format.format(java.util.Date(end))}" else from
}
