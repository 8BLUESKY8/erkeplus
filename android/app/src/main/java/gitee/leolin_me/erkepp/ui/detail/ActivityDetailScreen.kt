package gitee.leolin_me.erkepp.ui.detail

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import gitee.leolin_me.erkepp.ui.ExpandableText
import gitee.leolin_me.erkepp.ui.InfoRow
import gitee.leolin_me.erkepp.ui.PageLoading
import gitee.leolin_me.erkepp.ui.SectionCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.data.ActionOutcome
import gitee.leolin_me.erkepp.data.ActivityAttachment
import gitee.leolin_me.erkepp.data.ActivityDetail
import gitee.leolin_me.erkepp.data.ActivityNotice
import gitee.leolin_me.erkepp.data.ActivityPhase
import gitee.leolin_me.erkepp.data.ActivityPrefetch
import gitee.leolin_me.erkepp.data.ActivityRepository
import gitee.leolin_me.erkepp.data.ActivityShare
import gitee.leolin_me.erkepp.data.SignRecord
import gitee.leolin_me.erkepp.data.phase
import gitee.leolin_me.erkepp.data.toUserMessage
import gitee.leolin_me.erkepp.ui.formatHours
import gitee.leolin_me.erkepp.ui.phaseColor
import gitee.leolin_me.erkepp.ui.phaseLabel
import gitee.leolin_me.erkepp.ui.rememberCoverRequest
import gitee.leolin_me.erkepp.ui.sign.SignTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 活动详情（原生，只读）。
 *
 * 数据来自官方 `activity/detail/non-member`，已参加的用户回落到 `activity/detail/participant`。
 * 这一版不写数据：底部按钮跳到官方页面完成报名。
 */
@Composable
fun ActivityDetailScreen(
    token: String,
    repository: ActivityRepository,
    activityId: Long,
    onBack: () -> Unit,
    onOpenWeb: (Long) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenHomework: (Long, Boolean, String) -> Unit,
    onOpenAttachment: (ActivityAttachment) -> Unit,
    onOpenSignScan: (SignTarget) -> Unit = {},
    /** 报名：只在用户确认后才会调用。 */
    onEnroll: suspend (Long) -> ActionOutcome = { ActionOutcome(false, -1, "未接线") },
    /** 取消报名：第二个参数是理由（报名已通过时必填）。 */
    onCancelEnroll: suspend (Long, String) -> ActionOutcome = { _, _ -> ActionOutcome(false, -1, "未接线") },
    /** 打卡记录：官方 `activity/sign/one/list`。 */
    loadSignHistory: suspend (Long) -> List<SignRecord> = { emptyList() },
    /** 外部让详情重新拉一次（例如刚签到完）。 */
    refreshKey: Int = 0,
    /** 离线沙盒：直接用这份数据渲染，跳过网络与公告。 */
    detailOverride: ActivityDetail? = null,
    /** 沙盒模式下隐藏会联网的底部按钮。 */
    sandbox: Boolean = false,
) {
    var detail by remember(activityId) { mutableStateOf<ActivityDetail?>(null) }
    var error by remember(activityId) { mutableStateOf<String?>(null) }
    var loading by remember(activityId) { mutableStateOf(true) }
    var reloadKey by remember(activityId) { mutableIntStateOf(0) }
    var notices by remember(activityId) { mutableStateOf<List<ActivityNotice>>(emptyList()) }
    var noticeIndex by remember(activityId) { mutableIntStateOf(0) }
    var showNotices by remember(activityId) { mutableStateOf(false) }
    var showShare by remember(activityId) { mutableStateOf(false) }
    // 签到方式字典：signWay 里存的是学校配置的 id，名字要单独查
    var signWayNames by remember(activityId) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    // 报名 / 取消报名：确认框、理由、结果
    var showEnrollConfirm by remember(activityId) { mutableStateOf(false) }
    var showCancelConfirm by remember(activityId) { mutableStateOf(false) }
    var cancelReason by remember(activityId) { mutableStateOf("") }
    var actionBusy by remember(activityId) { mutableStateOf(false) }
    var actionResult by remember(activityId) { mutableStateOf<ActionOutcome?>(null) }
    // 打卡记录
    var showHistory by remember(activityId) { mutableStateOf(false) }
    var historyRecords by remember(activityId) { mutableStateOf<List<SignRecord>?>(null) }
    var historyLoading by remember(activityId) { mutableStateOf(false) }
    val actionScope = rememberCoroutineScope()

    fun runAction(block: suspend () -> ActionOutcome) {
        if (actionBusy) return
        actionBusy = true
        actionScope.launch {
            val outcome = runCatching { block() }
                .getOrElse { error ->
                    ActionOutcome(false, -1, error.message ?: error.javaClass.simpleName)
                }
            actionBusy = false
            actionResult = outcome
            if (outcome.success) {
                // 报名状态变了，重新拉一次详情
                reloadKey++
            }
        }
    }


    LaunchedEffect(activityId, reloadKey, refreshKey, detailOverride) {
        if (detailOverride != null) {
            detail = detailOverride
            loading = false
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        // 点下去那一刻已经预取过：入场那 180ms 里网络就跑完了，这里直接拿结果
        val cached = ActivityPrefetch.take(activityId)
        runCatching { cached ?: repository.detail(token, activityId) }
            .onSuccess { loaded ->
                detail = loaded
                // 公告失败不影响详情：拿不到就当没有公告
                runCatching { repository.notices(token, activityId, loaded.identity) }
                    .onSuccess {
                        notices = it
                        noticeIndex = 0
                        // 官方用 isPopup 标记「进页面就弹」，有标记才主动弹，否则只在页面里留入口
                        showNotices = it.any { notice -> notice.isPopup }
                    }
            }
            .onFailure { error = it.toUserMessage() }
        loading = false
    }

    // 只有已参加、且活动配了签到方式时才需要字典；失败就当没有，不打扰用户
    LaunchedEffect(detail?.signWay, detail?.identity) {
        val current = detail ?: return@LaunchedEffect
        if (current.identity != 1 || current.signWay.isBlank()) return@LaunchedEffect
        if (signWayNames.isNotEmpty()) return@LaunchedEffect
        if (sandbox) {
            // 沙盒不联网：字典名字用本校实测值，仅用于本地演示
            signWayNames = mapOf(1 to "动态二维码", 2 to "静态二维码", 3 to "GPS定位")
            return@LaunchedEffect
        }
        runCatching { repository.signWays(token) }.onSuccess { signWayNames = it }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            loading && detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PageLoading()
            }

            detail == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { reloadKey++ }) { Text("重试") }
            }

            else -> DetailContent(
                detail = detail!!,
                noticePreview = notices.firstOrNull()?.plainText,
                signWayNames = signWayNames,
                sandbox = sandbox,
                onOpenNotices = {
                    noticeIndex = 0
                    showNotices = true
                },
                onOpenWeb = { onOpenWeb(activityId) },
                onOpenUrl = onOpenUrl,
                onOpenHomework = { onOpenHomework(activityId, detail!!.isPostHomework == 1, detail!!.name) },
                onOpenAttachment = onOpenAttachment,
                onEnroll = { showEnrollConfirm = true },
                onCancelEnroll = {
                    cancelReason = ""
                    showCancelConfirm = true
                },
                onOpenSignHistory = {
                    showHistory = true
                    if (historyRecords == null && !historyLoading) {
                        historyLoading = true
                        actionScope.launch {
                            historyRecords = runCatching { loadSignHistory(activityId) }.getOrDefault(emptyList())
                            historyLoading = false
                        }
                    }
                },
                onOpenSignScan = {
                    val current = detail!!
                    onOpenSignScan(
                        SignTarget(
                            activityId = current.id,
                            activityName = current.name,
                            signSwitch = current.signSwitch,
                        ),
                    )
                },
            )
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(12.dp)
                .clip(CircleShape)
                .background(Color(0x59000000)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "返回",
                tint = Color.White,
            )
        }

        // 分享：官方「活动二维码」是同一个分享链接，这里做成二维码 + 复制/分享
        if (detail != null) {
            IconButton(
                onClick = { showShare = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Color(0x59000000)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_share),
                    contentDescription = "分享活动",
                    tint = Color.White,
                )
            }
        }
    }

    // 有公告就弹出来提醒，和官方一致；多条时依次翻
    val notice = notices.getOrNull(noticeIndex)
    if (showNotices && notice != null) {
        AlertDialog(
            onDismissRequest = { showNotices = false },
            title = { Text(notice.title.ifBlank { "活动公告" }) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (notice.time.isNotBlank()) {
                        Text(
                            text = notice.time,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    // 公告里常有群号，允许长按选中复制
                    SelectionContainer {
                        Text(text = notice.plainText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                if (noticeIndex < notices.lastIndex) {
                    TextButton(onClick = { noticeIndex++ }) {
                        Text("下一条（${noticeIndex + 2}/${notices.size}）")
                    }
                } else {
                    TextButton(onClick = { showNotices = false }) { Text("知道了") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotices = false }) { Text("关闭") }
            },
        )
    }

    // 报名确认：写操作，绝不自动提交
    val current = detail
    if (showEnrollConfirm && current != null) {
        AlertDialog(
            onDismissRequest = { if (!actionBusy) showEnrollConfirm = false },
            title = { Text("确认报名") },
            text = {
                Column {
                    Text(text = current.name, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "审核：" + if (current.signAudit == 1) "需要管理员审核" else "无需审核，报名即通过",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (current.peopleLimit > 0) {
                        Text(
                            text = "名额：${current.joinMemberCount}/${current.peopleLimit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "活动时间：" + formatRange(current.startTime, current.endTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "确认后会立即提交报名。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !actionBusy,
                    onClick = {
                        showEnrollConfirm = false
                        runAction { onEnroll(current.id) }
                    },
                ) {
                    Text(if (actionBusy) "提交中…" else "确认报名")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnrollConfirm = false }) { Text("再想想") }
            },
        )
    }

    // 取消报名确认：报名已通过时官方要求填理由，提交后进入「取消审核」
    if (showCancelConfirm && current != null) {
        val full = current.peopleLimit > 0 && current.joinMemberCount >= current.peopleLimit
        AlertDialog(
            onDismissRequest = { if (!actionBusy) showCancelConfirm = false },
            title = { Text("取消报名") },
            text = {
                Column {
                    Text(text = current.name, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    if (current.cancelNeedsReason) {
                        Text(
                            text = "报名已通过，取消需要管理员审核，请写明理由。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = cancelReason,
                            onValueChange = { cancelReason = it },
                            label = { Text("取消理由") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = "取消后名额会释放。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (full) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "这个活动名额已满，取消后大概率报不回来。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                val canSubmit = !actionBusy && (!current.cancelNeedsReason || cancelReason.isNotBlank())
                TextButton(
                    enabled = canSubmit,
                    onClick = {
                        showCancelConfirm = false
                        val reason = cancelReason.trim()
                        runAction { onCancelEnroll(current.id, reason) }
                    },
                ) {
                    Text(if (actionBusy) "提交中…" else "确认取消")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("保留报名") }
            },
        )
    }

    // 报名结果：成功失败都直接给服务端 msg
    val outcome = actionResult
    if (outcome != null) {
        AlertDialog(
            onDismissRequest = { actionResult = null },
            title = { Text(if (outcome.success) "已提交" else "没有成功") },
            text = {
                Column {
                    Text(
                        text = outcome.message,
                        color = if (outcome.success) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "code=${outcome.code}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { actionResult = null }) { Text("知道了") }
            },
        )
    }

    // 分享：活动二维码 + 复制链接
    if (showShare && detail != null) {
        ShareSheet(
            activityId = detail!!.id,
            activityName = detail!!.name,
            onDismiss = { showShare = false },
        )
    }

    // 打卡记录：官方「打卡记录」那个入口，只读
    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("打卡记录") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    val records = historyRecords
                    when {
                        historyLoading -> Text(
                            text = "正在读取…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        records.isNullOrEmpty() -> Text(
                            text = "暂无签到签退记录！",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        else -> records.forEach { record ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = record.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (record.action == "签到") {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = signTimeText(record.time),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistory = false }) { Text("知道了") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    detail: ActivityDetail,
    noticePreview: String?,
    signWayNames: Map<Int, String>,
    sandbox: Boolean,
    onOpenNotices: () -> Unit,
    onOpenWeb: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenHomework: () -> Unit,
    onOpenAttachment: (ActivityAttachment) -> Unit,
    onEnroll: () -> Unit,
    onCancelEnroll: () -> Unit,
    onOpenSignHistory: () -> Unit,
    onOpenSignScan: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 分区懒加载：整页一次性组合时，首次显示要 150～230ms；改成一块一个 item，
        // 首帧只组合屏幕内的两三块，滚动时才组合后面的
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { CoverHeader(detail) }

            // 标题区：状态、分类、活动名、主办组织、倒计时
            item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val phase = detail.phase()
                    val label = phaseLabel(phase)
                    if (label.isNotEmpty()) {
                        Pill(text = label, color = phaseColor(phase))
                        Spacer(Modifier.width(8.dp))
                    }
                    if (detail.classifyName.isNotBlank()) {
                        Text(
                            text = detail.classifyName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = detail.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                if (detail.organizationName.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (detail.organizationAvatar != null) {
                            AsyncImage(
                                model = rememberCoverRequest(detail.organizationAvatar, 28, maxWidthPx = 112),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = detail.organizationName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (detail.phase() == ActivityPhase.ENROLLING && detail.enrollEndTime > System.currentTimeMillis()) {
                    Spacer(Modifier.height(10.dp))
                    CountdownChip(detail.enrollEndTime)
                }
            }
            }

            // 学时 / 报名人数 / 结束时间
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(16.dp))
                    StatsCard(detail)
                }
            }

            // 报名时间、活动时间、地点、联系方式
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(12.dp))
                    ScheduleCard(detail)
                }
            }

            if (detail.identity == 1 && detail.attendanceType != 0) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(12.dp))
                        SignCard(
                            detail = detail,
                            signWayNames = signWayNames,
                            onScan = onOpenSignScan,
                            onOpenHistory = onOpenSignHistory,
                        )
                    }
                }
            }

            if (noticePreview != null) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(12.dp))
                        Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenNotices),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "最新公告",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = noticePreview.replace("\n", " "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "查看",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    }
                }
            }

            if (detail.hasRestriction) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(12.dp))
                        Card {
                        Text(
                            text = "参与限制",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        when (detail.genderLimit) {
                            1 -> InfoLine(label = "性别", value = "限男生")
                            2 -> InfoLine(label = "性别", value = "限女生")
                        }
                        if (detail.gradeNames.isNotEmpty()) {
                            InfoLine(label = "年级", value = detail.gradeNames.joinToString("、"))
                        }
                        if (detail.collegeNames.isNotEmpty()) {
                            InfoLine(label = "学院", value = detail.collegeNames.joinToString("、"))
                        }
                        if (detail.honestLimit > 0) {
                            InfoLine(label = "诚信值", value = "不低于 ${formatHours(detail.honestLimit)}")
                        }
                    }
                    }
                }
            }

            if (detail.introduceHtml.isNotBlank()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = "活动介绍",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        // 介绍默认折到 8 行：整段一次性排出来既占地，也让首帧要光栅化几百个汉字
                        ExpandableText(
                            text = if (looksLikeHtml(detail.introduceHtml)) {
                                htmlToAnnotated(detail.introduceHtml)
                            } else {
                                AnnotatedString(detail.introduceHtml.trim())
                            },
                            collapsedMaxLines = 8,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                        )
                    }
                }
            }

            if (detail.attachments.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(16.dp))
                        Card {
                            Text(
                                text = "附件",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(6.dp))
                            val images = detail.attachments.filter { it.type == 1 }
                            if (images.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    images.forEach { image ->
                                        AsyncImage(
                                            model = rememberCoverRequest(image.url, 84, maxWidthPx = 336),
                                            contentDescription = image.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(84.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { onOpenAttachment(image) },
                                        )
                                    }
                                }
                                if (detail.attachments.any { it.type != 1 }) Spacer(Modifier.height(8.dp))
                            }
                            detail.attachments.filterNot { it.type == 1 }.forEach { attachment ->
                                AttachmentRow(
                                    attachment = attachment,
                                    onClick = { onOpenAttachment(attachment) },
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(28.dp)) }
        }

        // 沙盒不显示底部动作条：那两个按钮都会联网
        if (!sandbox) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                // 作业入口按官方实际行为给：活动已开始/已结束，或本人已交过作业（查看作业）
                val phase = detail.phase()
                if (detail.identity == 1 &&
                    (phase == ActivityPhase.RUNNING || phase == ActivityPhase.FINISHED || detail.isPostHomework == 1)
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onOpenHomework,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (detail.isPostHomework == 1) "查看作业" else "交作业")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                // 报名 / 取消报名：官方规则下能做的直接做，做不了的说清原因
                when {
                    detail.identity == 0 && detail.canEnroll -> {
                        Button(onClick = onEnroll, modifier = Modifier.fillMaxWidth()) {
                            Text("立即报名")
                        }
                    }

                    detail.identity == 0 && detail.enrollBlockedReason.isNotBlank() -> {
                        Text(
                            text = detail.enrollBlockedReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    detail.identity == 1 && detail.canCancelEnroll -> {
                        OutlinedButton(onClick = onCancelEnroll, modifier = Modifier.fillMaxWidth()) {
                            Text("取消报名")
                        }
                    }

                    detail.identity == 1 -> {
                        Text(
                            text = detail.applyStatusText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                TextButton(onClick = onOpenWeb, modifier = Modifier.fillMaxWidth()) {
                    Text("在官方页面查看")
                }
            }
        }
    }
}

@Composable
private fun CoverHeader(detail: ActivityDetail) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (detail.cover != null) {
            AsyncImage(
                model = rememberCoverRequest(detail.cover, 400, maxWidthPx = 1080),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x66000000),
                        0.45f to Color(0x00000000),
                        1f to Color(0x33000000),
                    ),
                ),
        )
    }
}

/** 学时 / 学分 / 报名人数，做成一眼能看见的数字条。 */
@Composable
private fun StatsCard(detail: ActivityDetail) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatCell(value = formatHours(detail.hours), label = "学时", modifier = Modifier.weight(1f))
            StatDivider()
            StatCell(
                value = if (detail.peopleLimit > 0) {
                    "${detail.joinMemberCount}/${detail.peopleLimit}"
                } else {
                    "${detail.joinMemberCount}"
                },
                label = "已报名",
                modifier = Modifier.weight(1.2f),
            )
            StatDivider()
            StatCell(
                value = if (detail.endTime > 0) shortDate(detail.endTime) else "—",
                label = "结束时间",
                modifier = Modifier.weight(1.2f),
            )
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
}

@Composable
private fun ScheduleCard(detail: ActivityDetail) {
    Card {
        IconLine(
            iconRes = R.drawable.ic_schedule,
            title = "报名时间",
            value = formatRange(detail.enrollStartTime, detail.enrollEndTime),
        )
        IconLine(
            iconRes = R.drawable.ic_schedule,
            title = "活动时间",
            value = formatRange(detail.startTime, detail.endTime),
        )
        if (detail.address.isNotBlank()) {
            IconLine(iconRes = R.drawable.ic_place, title = "活动地点", value = detail.address)
        }
        if (detail.contact.isNotBlank()) {
            IconLine(iconRes = R.drawable.ic_place, title = "联系方式", value = detail.contact)
        }
        if (detail.managerName.isNotBlank()) {
            IconLine(iconRes = R.drawable.ic_person, title = "活动管理员", value = detail.managerName)
        }
        if (detail.guidanceNames.isNotEmpty()) {
            IconLine(
                iconRes = R.drawable.ic_person,
                title = "指导老师",
                value = detail.guidanceNames.joinToString("、"),
            )
        }
        InfoLine(label = "报名方式", value = if (detail.limitType == 0) "个人报名" else "团队报名")
        InfoLine(label = "报名审核", value = if (detail.signAudit == 1) "需要审核" else "无需审核")
        if (detail.identity == 1) {
            InfoLine(label = "报名状态", value = applyStatusText(detail.applyStatus))
        }
        InfoLine(label = "考勤标准", value = attendanceLabel(detail.attendanceType))
        InfoLine(label = "提交作业", value = if (detail.isSubmitWork == 1) "需要提交" else "不需要")    }
}

/**
 * 签到卡片。
 *
 * 「扫码签到」的显示条件与官方 H5 的按钮逐条对齐（已参加、报名通过、状态 5～8、
 * 签到已开启、签到方式含二维码）。定位签到官方学生端只展示地图信息、没给提交入口，
 * 所以这里也不提供，避免造出一个官方没有的功能。
 */
@Composable
private fun SignCard(
    detail: ActivityDetail,
    signWayNames: Map<Int, String>,
    onScan: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val wayNames = detail.signWayIds.map { id -> signWayNames[id] ?: "方式 $id" }
    val hasQrWay = detail.signWayIds.any { it == 1 || it == 2 }
    val unavailable = when {
        !hasQrWay -> "这个活动用定位签到，暂不支持。"
        detail.signSwitch == 0 -> "老师还没开启签到，开启后这里会出现扫码按钮。"
        detail.applyStatus != 2 -> "报名通过后才能签到。"
        detail.status !in 5..8 -> "活动还没到签到时间。"
        else -> null
    }

    Card {
        Text(
            text = "签到",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        InfoLine(label = "签到方式", value = wayNames.joinToString("、").ifBlank { "—" })
        InfoLine(label = "当前状态", value = signSwitchLabel(detail.signSwitch))
        if (detail.isSigner == 1) {
            InfoLine(label = "我的状态", value = "已签到")
        }
        if (detail.signInCount > 0 || detail.signOutCount > 0) {
            InfoLine(
                label = "已签人数",
                value = "签到 ${detail.signInCount} 人 · 签退 ${detail.signOutCount} 人",
            )
        }
        if (unavailable == null) {
            Spacer(Modifier.height(10.dp))
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Text("扫码签到")
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                text = unavailable,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (detail.applyStatus == 2 && detail.attendanceType != 4) {
            TextButton(
                onClick = onOpenHistory,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("打卡记录")
            }
        }
    }
}

/**
 * 分享面板：二维码 + 链接。
 *
 * 码的内容与官方「活动二维码」完全一致（`?sourceName=schActivityCode@Xj&activityid=`），
 * 别人扫了会打开官方详情页去报名；复制链接更适合发到群里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(
    activityId: Long,
    activityName: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val url = remember(activityId) { ActivityShare.shareUrl(activityId) }
    val qr = remember(url) { ActivityShare.qrBitmap(url, 640) }
    var copied by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "分享活动",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = activityName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            if (qr != null) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = "活动二维码",
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(10.dp),
                )
            } else {
                Text("二维码生成失败", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "扫码打开官方活动页，可直接报名",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            SelectionContainer {
                Text(
                    text = url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        manager?.setPrimaryClip(ClipData.newPlainText("activity", url))
                        copied = true
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (copied) "已复制" else "复制链接")
                }
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, activityName)
                            putExtra(Intent.EXTRA_TEXT, "$activityName\n$url")
                        }
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(intent, "分享活动").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("分享")
                }
            }
        }
    }
}

/** 签到开关：0 未开启、1 签到中、2 签退中（官方 `activity/sign/switch` 的 handleType）。 */
private fun signSwitchLabel(signSwitch: Int): String = when (signSwitch) {
    1 -> "签到中"
    2 -> "签退中"
    else -> "未开启"
}

/** 报名状态：已实测 `2` 为「报名已通过」，其余值暂时按「已报名」显示。 */
private fun applyStatusText(applyStatus: Int): String = when (applyStatus) {
    2 -> "报名已通过"
    else -> "已报名"
}

/** 考勤方式，取自官方详情页的枚举：1 签到+签退、2 签到、3 签退、其它无需考勤。 */
private fun attendanceLabel(type: Int): String = when (type) {
    1 -> "签到 + 签退"
    2 -> "签到"
    3 -> "签退"
    else -> "无需考勤"
}

/** 报名截止倒计时。30 秒刷新一次即可，不需要每秒跳。 */
@Composable
private fun CountdownChip(endTime: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endTime) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val remain = endTime - now
    if (remain <= 0) return
    val days = remain / 86_400_000L
    val hours = (remain % 86_400_000L) / 3_600_000L
    val minutes = (remain % 3_600_000L) / 60_000L
    val text = buildString {
        append("距报名结束 ")
        if (days > 0) append("$days 天 ")
        if (days > 0 || hours > 0) append("$hours 小时 ")
        append("$minutes 分")
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun IconLine(iconRes: Int, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, textColor: Color = Color.White) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    // 卡片样式统一在 ui/Common.kt，这边只保留一个短名字给本页的十几处调用点用
    SectionCard(content = content)
}

@Composable
private fun AttachmentRow(attachment: ActivityAttachment, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = attachment.extension,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = attachment.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "打开",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    // 本页的标签列窄一点，其余样式走公用组件
    InfoRow(label = label, value = value, labelWidth = 64.dp, modifier = Modifier.padding(vertical = 1.dp))
}

/** 介绍字段多数是纯文本，只有带标签的才需要按富文本渲染。 */
private fun looksLikeHtml(text: String): Boolean =
    Regex("<\\s*[a-zA-Z][^>]*>").containsMatchIn(text)

private fun formatRange(start: Long, end: Long): String = when {
    start > 0 && end > 0 -> "${formatTime(start)} ~ ${formatTime(end)}"
    start > 0 -> formatTime(start)
    end > 0 -> formatTime(end)
    else -> "不限"
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))

private fun shortDate(millis: Long): String =
    SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(millis))

/** 打卡记录里的时间：到分钟就够。 */
private fun signTimeText(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))
