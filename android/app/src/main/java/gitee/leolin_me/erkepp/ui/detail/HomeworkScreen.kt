package gitee.leolin_me.erkepp.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.data.ActivityAttachment
import gitee.leolin_me.erkepp.data.ActivityWork
import gitee.leolin_me.erkepp.data.toUserMessage
import gitee.leolin_me.erkepp.ui.rememberCoverRequest

/**
 * 查看作业（原生，只读）。
 *
 * 数据来自官方 `activity/query/work_3_0_1`，就是 `#/assignmentinfor/:id` 页面用的接口。
 * 底部的「修改作业」跳到官方页面处理，属于写操作。
 */
@Composable
fun HomeworkScreen(
    activityId: Long,
    loader: suspend () -> ActivityWork?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var work by remember(activityId) { mutableStateOf<ActivityWork?>(null) }
    var error by remember(activityId) { mutableStateOf<String?>(null) }
    var loading by remember(activityId) { mutableStateOf(true) }
    var reloadKey by remember(activityId) { mutableIntStateOf(0) }


    LaunchedEffect(activityId, reloadKey) {
        loading = true
        error = null
        runCatching { loader() }
            .onSuccess { work = it }
            .onFailure { error = it.toUserMessage() }
        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("返回") }
                Text("查看作业", style = MaterialTheme.typography.titleMedium)
            }

            when {
                loading && work == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                work == null || (work!!.content.isBlank() && work!!.attachments.isEmpty()) -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = error ?: "还没有可显示的内容",
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { reloadKey++ }) { Text("重试") }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        SectionLabel("提交内容")
                        Spacer(Modifier.height(6.dp))
                        SelectionContainer {
                            Text(
                                text = work!!.content.ifBlank { "（没有文字内容）" },
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 24.sp,
                            )
                        }

                        if (work!!.attachments.isNotEmpty()) {
                            Spacer(Modifier.height(18.dp))
                            SectionLabel("附件（${work!!.attachments.size}）")
                            Spacer(Modifier.height(8.dp))
                            Attachments(work!!.attachments, onOpenUrl)
                        }

                        Spacer(Modifier.height(28.dp))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                            Text("修改作业")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Attachments(attachments: List<ActivityAttachment>, onOpenUrl: (String) -> Unit) {
    val images = attachments.filter { it.type == 1 }
    val files = attachments.filter { it.type != 1 }

    if (images.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            images.forEach { image ->
                AsyncImage(
                    model = rememberCoverRequest(image.url, 96, maxWidthPx = 384),
                    contentDescription = image.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenUrl(image.url) },
                )
            }
        }
    }

    files.forEach { file ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenUrl(file.url) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = file.extension,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
