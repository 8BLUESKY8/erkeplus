package gitee.leolin_me.erkepp.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gitee.leolin_me.erkepp.data.CreditDetail
import gitee.leolin_me.erkepp.data.Session
import gitee.leolin_me.erkepp.ui.rememberCoverRequest

/**
 * 「我的」：个人信息、三个数字（学分 / 学时 / 诚信值）和入口。
 *
 * 原来只有一个问候语加两个按钮，太空。这一版把学号、年级这些能拿到的信息都摆出来，
 * 数字做成一行三卡，入口改成列表行。
 */
@Composable
fun ProfileScreen(
    session: Session,
    credit: CreditDetail?,
    onOpenCredit: () -> Unit,
    onOpenAttachments: () -> Unit,
    onOpenWeb: () -> Unit,
    onRefreshProfile: () -> Unit,
) {
    val profile = session.profile
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        // 头像来自官方 my-info 的 avatar，缓存在会话里；没有就退回姓名首字
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(profile.avatar, profile.name)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val second = listOf(profile.college, profile.major)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (second.isNotEmpty()) {
                    Text(
                        text = second,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onRefreshProfile) { Text("刷新资料") }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoChip("学号", profile.studentNo)
            if (profile.grade.isNotBlank()) InfoChip("年级", profile.grade)
            if (profile.className.isNotBlank()) InfoChip("班级", profile.className)
        }

        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard("学分", format(profile.credit), Modifier.weight(1f))
            StatCard("学时", format(profile.hours), Modifier.weight(1f))
            StatCard(
                label = "诚信值",
                value = credit?.let { formatScore(it.credit) } ?: "—",
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenCredit),
            )
        }

        Spacer(Modifier.height(22.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                EntryRow(
                    title = "诚信值",
                    subtitle = "守约记录、失约记录与申诉",
                    onClick = onOpenCredit,
                )
                EntryRow(
                    title = "我的附件",
                    subtitle = "已下载的活动附件与回收站",
                    onClick = onOpenAttachments,
                )
                EntryRow(
                    title = "打开官方页面",
                    subtitle = "网页版完整功能：成绩单、组织、问卷等",
                    onClick = onOpenWeb,
                    showDivider = false,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "其余页面会陆续换成原生界面；暂时没有的，走上面那个官方网页入口。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Avatar(url: String?, name: String) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = rememberCoverRequest(url, 56, maxWidthPx = 224),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = name.take(1),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EntryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showDivider) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp)
                    .height(1.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {}
        }
    }
}

private fun format(value: Double): String =
    if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", value)
    }

private fun formatScore(value: Double): String =
    if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", value)
    }
