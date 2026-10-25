package gitee.leolin_me.erkepp.ui.org

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import gitee.leolin_me.erkepp.ui.PageLoading
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
import gitee.leolin_me.erkepp.data.OrgDetailPrefetch
import gitee.leolin_me.erkepp.data.OrgMember
import gitee.leolin_me.erkepp.data.OrgMembers
import gitee.leolin_me.erkepp.data.OrganizationRepository
import gitee.leolin_me.erkepp.data.toUserMessage
import gitee.leolin_me.erkepp.ui.ActivityRow
import gitee.leolin_me.erkepp.ui.CollapsibleItems
import gitee.leolin_me.erkepp.ui.ExpandableText
import gitee.leolin_me.erkepp.ui.InfoRow
import gitee.leolin_me.erkepp.ui.SectionCard
import gitee.leolin_me.erkepp.ui.profile.AvatarImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 组织详情。扫码扫到组织码、或在「我的组织」「所有组织」里点一条，都进这里。
 *
 * 版式照官方 `#/orgDetail/:id`，但官方拆成「简介 / 公告 / 相册 / 活动」四个 tab，
 * 切过去经常是空的；这里改成单页自上而下铺开，空的分区直接不出现，
 * 长内容（简介、负责人、主办活动）先收着，想看再展开。
 *
 * 申请加入后**不能自己退出**（官方在点之前也是这么提示的），所以确认框里把这句话说清楚。
 */
@Composable
fun OrgDetailScreen(
    token: String,
    repository: OrganizationRepository,
    organizationId: Long,
    initialName: String = "",
    /** 点负责人进他的主页。 */
    onOpenPerson: (Long) -> Unit = {},
    onOpenActivity: (Long) -> Unit,
    onApplied: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detail by remember(organizationId) { mutableStateOf<OrgDetail?>(null) }
    var members by remember(organizationId) { mutableStateOf(OrgMembers.EMPTY) }
    var activities by remember(organizationId) { mutableStateOf<List<ActivityItem>>(emptyList()) }
    // 主办活动只拉第一页，标题里的「共 N 个」用服务端的 total，不能拿已加载条数冒充
    var activityTotal by remember(organizationId) { mutableIntStateOf(0) }
    var loading by remember(organizationId) { mutableStateOf(true) }
    var error by remember(organizationId) { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    var showApply by remember { mutableStateOf(false) }
    var applyInfo by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    // 附加信息（成员、活动）拉失败不影响详情本身，所以吞掉异常但保留取消
    suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        null
    }

    LaunchedEffect(organizationId, tick) {
        loading = true
        try {
            // 点下去那一刻已经预取过，入场动画期间网络就跑完了
            detail = OrgDetailPrefetch.take(organizationId)
                ?: repository.detail(token, organizationId)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            error = failure.toUserMessage()
        }
        if (detail != null) {
            val page = quietly { repository.activities(token, organizationId, 1) }
            activities = page?.items.orEmpty()
            activityTotal = page?.total?.takeIf { it > 0 } ?: activities.size
            members = quietly { repository.members(token, organizationId) } ?: OrgMembers.EMPTY
        }
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
            ) { PageLoading() }

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
                val leaders = members.leaders
                LazyColumn(modifier = Modifier.weight(1f)) {
                    // `activityCount` 和活动列表的 total 会打架（实测 13 vs 9），
                    // 同页出现两个「活动数」只会让人以为哪里坏了，统一以列表 total 为准
                    val activityCount = activityTotal.takeIf { it > 0 } ?: current.activityCount
                    item { OrgHeader(current, activityCount) }

                    if (current.intro.isNotBlank()) {
                        item {
                            SectionCard(
                                title = "简介",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                ExpandableText(text = current.intro, collapsedMaxLines = 6)
                            }
                        }
                    }

                    item { OrgInfoCard(current) }

                    if (leaders.isNotEmpty()) {
                        item {
                            SectionCard(
                                title = "负责人",
                                trailing = "共 ${leaders.size} 位",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                CollapsibleItems(count = leaders.size, collapsedCount = 4) { index ->
                                    LeaderRow(leaders[index], onOpenPerson)
                                }
                            }
                        }
                    }

                    item {
                        SectionCard(
                            title = "主办活动",
                            trailing = if (activityTotal > 0) "共 $activityTotal 个" else null,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            if (activities.isEmpty()) {
                                Text(
                                    text = if (loading) "正在加载…" else "这个组织暂时没有可看的活动",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                CollapsibleItems(count = activities.size, collapsedCount = 3) { index ->
                                    val activity = activities[index]
                                    ActivityRow(activity, onClick = { onOpenActivity(activity.id) })
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(20.dp)) }
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

// ---- 头部 ----

@Composable
private fun OrgHeader(detail: OrgDetail, activityCount: Int) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(
                url = detail.avatar,
                name = detail.name,
                contentDescription = "组织头像",
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val second = listOf(detail.natureName, detail.categoryName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (second.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = second,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val tags = buildList {
                    orgIdentityText(detail.identity).takeIf { it.isNotBlank() }?.let { add(it to false) }
                    // 只有异常状态才值得占一行；「已注册」是常态，说出来等于噪音
                    orgProcessText(detail.processStatus)
                        .takeIf { it.isNotBlank() && it != "已注册" }
                        ?.let { add(it to true) }
                }
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { (text, alert) -> Tag(text, alert) }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Stat("成员", "${detail.memberCount}")
            Stat("活动", "$activityCount")
            Stat("活力值", orgNumberText(detail.energy))
        }
    }
}

@Composable
private fun Tag(text: String, alert: Boolean) {
    val container = if (alert) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val onContainer = if (alert) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = onContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
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

// ---- 分区卡片 ----

/** 组织档案：官方「简介」tab 里那一组「组织归属 / 组织性质 / 指导老师 / 成立时间」。 */
@Composable
private fun OrgInfoCard(detail: OrgDetail) {
    val rows = buildList {
        if (detail.parentName.isNotBlank()) add("上级组织" to detail.parentName)
        if (detail.affiliationName.isNotBlank()) add("挂靠单位" to detail.affiliationName)
        if (detail.natureName.isNotBlank()) add("组织性质" to detail.natureName)
        if (detail.categoryName.isNotBlank()) add("类别" to detail.categoryName)
        if (detail.guiderName.isNotBlank()) add("指导老师" to detail.guiderName)
        orgDate(detail.foundTime).takeIf { it.isNotBlank() }?.let { add("成立时间" to it) }
        if (detail.campusName.isNotBlank()) add("校区" to detail.campusName)
    }
    if (rows.isEmpty()) return

    SectionCard(
        title = "组织信息",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        rows.forEach { (label, value) -> InfoRow(label, value) }
    }
}

@Composable
private fun LeaderRow(member: OrgMember, onOpenPerson: (Long) -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = member.id > 0) { onOpenPerson(member.id) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(
            url = member.avatar,
            name = member.name,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = member.name, style = MaterialTheme.typography.bodyMedium)
                if (member.duty.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = member.duty,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            val sub = listOf(member.collegeName, member.majorName)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---- 主办活动 ----
// 行样式在 ui/ActivityRow.kt，组织详情和人物主页共用

// ---- 底部操作条 ----

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
