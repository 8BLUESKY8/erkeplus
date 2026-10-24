package gitee.leolin_me.erkepp.ui.org

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.ActivityItem
import gitee.leolin_me.erkepp.data.OrgDetail
import gitee.leolin_me.erkepp.data.OrganizationRepository
import gitee.leolin_me.erkepp.data.toUserMessage
import gitee.leolin_me.erkepp.ui.profile.AvatarImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 组织详情。扫码扫到组织码、或在「我的组织」里点一条，都进这里。
 *
 * 官方的 `#/orgDetail/:id` 信息很多（公告、相册、成员、荣誉…），这一版先给最该看的：
 * 这是谁、有多少人、简介、办过哪些活动，以及在不够了解之前不让点的那颗「申请加入」。
 *
 * 申请加入后**不能自己退出**（官方在点之前也是这么提示的），所以确认框里把这句话说清楚。
 */
@Composable
fun OrgDetailScreen(
    token: String,
    repository: OrganizationRepository,
    organizationId: Long,
    initialName: String = "",
    onOpenActivity: (Long) -> Unit,
    onApplied: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<OrgDetail?>(null) }
    var activities by remember { mutableStateOf<List<ActivityItem>>(emptyList()) }
    // 主办活动只拉第一页，标题里的「共 N 个」用服务端的 total，不能拿已加载条数冒充
    var activityTotal by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    var showApply by remember { mutableStateOf(false) }
    var applyInfo by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(organizationId, tick) {
        loading = true
        try {
            detail = repository.detail(token, organizationId)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            error = failure.toUserMessage()
        }
        // 活动列表是附加信息，拉失败不影响详情
        val activityPage = try {
            repository.activities(token, organizationId, 1)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            null
        }
        activities = activityPage?.items.orEmpty()
        activityTotal = activityPage?.total?.takeIf { it > 0 } ?: activities.size
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
                text = detail?.name ?: initialName.ifBlank { "组织详情" },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        when {
            loading && detail == null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            detail == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = error ?: "没拿到组织信息",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { tick++ }) { Text("重试") }
            }

            else -> {
                val current = detail!!
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item { OrgHeader(current) }
                    if (current.intro.isNotBlank()) {
                        item { OrgIntro(current.intro) }
                    }
                    item {
                        Text(
                            text = if (activityTotal > activities.size) {
                                "主办的活动（共 $activityTotal 个，这里显示最近 ${activities.size} 个）"
                            } else {
                                "主办的活动（${activities.size}）"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                        )
                    }
                    if (activities.isEmpty()) {
                        item {
                            Text(
                                text = "这个组织暂时没有可看的活动",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    } else {
                        items(activities, key = { it.id }) { activity ->
                            OrgActivityRow(activity) { onOpenActivity(activity.id) }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }

                OrgActionBar(
                    detail = current,
                    onApply = {
                        submitError = null
                        showApply = true
                    },
                )
            }
        }
    }

    if (showApply) {
        val joined = detail
        AlertDialog(
            onDismissRequest = { if (!submitting) showApply = false },
            title = { Text("申请加入${joined?.name?.let { "「$it」" }.orEmpty()}") },
            text = {
                Column {
                    Text(
                        text = "申请交上去要组织管理员审核。官方提示：加入组织后不能自己退出。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = applyInfo,
                        onValueChange = { applyInfo = it },
                        label = { Text("申请理由（必填）") },
                        placeholder = { Text("班级、姓名、想参加什么，写清楚一点") },
                        minLines = 3,
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (submitError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = submitError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = applyInfo.isNotBlank() && !submitting,
                    onClick = {
                        scope.launch {
                            submitting = true
                            submitError = null
                            runCatching { repository.join(token, organizationId, applyInfo.trim()) }
                                .onSuccess {
                                    submitting = false
                                    showApply = false
                                    applyInfo = ""
                                    Toast.makeText(context, "申请已提交，等管理员审核", Toast.LENGTH_SHORT).show()
                                    onApplied()
                                    tick++
                                }
                                .onFailure {
                                    submitting = false
                                    submitError = it.toUserMessage()
                                }
                        }
                    },
                ) {
                    Text(if (submitting) "提交中…" else "提交申请")
                }
            },
            dismissButton = {
                TextButton(enabled = !submitting, onClick = { showApply = false }) { Text("再想想") }
            },
        )
    }
}

@Composable
private fun OrgHeader(detail: OrgDetail) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(
                url = detail.avatar,
                name = detail.name,
                contentDescription = "组织头像",
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = detail.name, style = MaterialTheme.typography.titleLarge)
                val second = listOf(detail.natureName, detail.categoryName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (second.isNotEmpty()) {
                    Text(
                        text = second,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Stat("成员", "${detail.memberCount}")
            Stat("活跃", orgNumberText(detail.energy))
            Stat("状态", orgProcessText(detail.processStatus).ifBlank { "—" })
        }

        Spacer(Modifier.height(10.dp))
        val lines = buildList {
            if (detail.affiliationName.isNotBlank()) add("挂靠：${detail.affiliationName}")
            if (detail.chairmanName.isNotBlank()) add("会长：${detail.chairmanName}")
            if (detail.guiderName.isNotBlank()) add("指导老师：${detail.guiderName}")
        }
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OrgIntro(intro: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = "简介", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = intro,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun OrgActivityRow(item: ActivityItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarImage(
                url = item.cover,
                name = item.name,
                contentDescription = null,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val whenText = orgActivityTime(item.startTime, item.endTime)
                Text(
                    text = buildList {
                        if (whenText.isNotBlank()) add(whenText)
                        if (item.hours > 0) add("${orgNumberText(item.hours)} 学时")
                        if (item.classifyName.isNotBlank()) add(item.classifyName)
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OrgActionBar(detail: OrgDetail, onApply: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            detail.canApply -> Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (detail.applyStatus == 3) "重新申请" else "申请加入")
            }

            else -> {
                val text = when {
                    detail.applyStatus == 1 -> "已提交申请，等待审核"
                    detail.applyStatus == 2 -> "已加入这个组织"
                    detail.applyStatus == 3 -> "申请被驳回，可以重新申请"
                    detail.isJoin == 1 -> "已加入这个组织"
                    detail.rule == 3 -> "这个组织不接受自己申请"
                    detail.isAdd != 1 -> "这个组织暂未开放加入"
                    else -> ""
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
