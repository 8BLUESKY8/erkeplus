package gitee.leolin_me.erkepp.ui.detail

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.data.ActivityAttachment
import gitee.leolin_me.erkepp.data.ActivityDetail
import gitee.leolin_me.erkepp.data.ActivityRepository
import gitee.leolin_me.erkepp.data.toUserMessage
import gitee.leolin_me.erkepp.ui.formatHours
import gitee.leolin_me.erkepp.ui.rememberCoverRequest
import gitee.leolin_me.erkepp.ui.statusColor
import gitee.leolin_me.erkepp.ui.statusLabel
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
) {
    var detail by remember(activityId) { mutableStateOf<ActivityDetail?>(null) }
    var error by remember(activityId) { mutableStateOf<String?>(null) }
    var loading by remember(activityId) { mutableStateOf(true) }
    var reloadKey by remember(activityId) { mutableIntStateOf(0) }

    BackHandler(onBack = onBack)

    LaunchedEffect(activityId, reloadKey) {
        loading = true
        error = null
        runCatching { repository.detail(token, activityId) }
            .onSuccess { detail = it }
            .onFailure { error = it.toUserMessage() }
        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            loading && detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                onOpenWeb = { onOpenWeb(activityId) },
                onOpenUrl = onOpenUrl,
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
    }
}

@Composable
private fun DetailContent(detail: ActivityDetail, onOpenWeb: () -> Unit, onOpenUrl: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            CoverHeader(detail)

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = statusLabel(detail.status)
                    if (label.isNotEmpty()) {
                        Pill(text = label, color = statusColor(detail.status))
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

                Spacer(Modifier.height(16.dp))
                StatsCard(detail)
                Spacer(Modifier.height(12.dp))
                ScheduleCard(detail)

                if (detail.hasRestriction) {
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

                if (detail.introduceHtml.isNotBlank()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "活动介绍",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (looksLikeHtml(detail.introduceHtml)) {
                        HtmlBlock(
                            html = detail.introduceHtml,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            onOpenLink = onOpenWeb,
                        )
                    } else {
                        Text(
                            text = detail.introduceHtml.trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 24.sp,
                        )
                    }
                }

                if (detail.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Card {
                        Text(
                            text = "附件",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        detail.attachments.forEach { attachment ->
                            AttachmentRow(
                                attachment = attachment,
                                onClick = { onOpenUrl(attachment.url) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Button(onClick = onOpenWeb, modifier = Modifier.fillMaxWidth()) {
                Text(if (detail.status == 5) "去官方页面报名" else "在官方页面打开")
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
            IconLine(iconRes = R.drawable.ic_place, title = "活动管理员", value = detail.managerName)
        }
    }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
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
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 富文本介绍：只读 WebView，高度按内容撑开，滚动交给外层。 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlBlock(html: String, textColor: Color, onOpenLink: () -> Unit) {
    var contentHeight by remember(html) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val colorHex = String.format("#%06X", textColor.toArgb() and 0xFFFFFF)

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setOnTouchListener { _, _ -> true }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript("document.documentElement.scrollHeight") { value ->
                            val height = value?.trim('"')?.toFloatOrNull()?.toInt() ?: 0
                            if (height > 0) contentHeight = height
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        onOpenLink()
                        return true
                    }
                }
                loadDataWithBaseURL("https://win.9xueqi.com/", wrapHtml(html, colorHex), "text/html", "utf-8", null)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { contentHeight.coerceAtLeast(1).toDp() }),
    )
}

/** 介绍字段多数是纯文本，只有带标签的才需要走 WebView。 */
private fun looksLikeHtml(text: String): Boolean =
    Regex("<\\s*[a-zA-Z][^>]*>").containsMatchIn(text)

private fun wrapHtml(html: String, textColor: String): String = """
    <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
      body { margin: 0; padding: 0; font-size: 15px; line-height: 1.75;
             color: $textColor; word-wrap: break-word; background: transparent; }
      img, video, iframe { max-width: 100% !important; height: auto !important; border-radius: 10px; }
      table { width: 100% !important; border-collapse: collapse; }
      * { max-width: 100% !important; }
    </style></head><body>$html</body></html>
""".trimIndent()

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
