package gitee.leolin_me.erkepp.ui.attachments

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.AttachmentStorage
import gitee.leolin_me.erkepp.data.Downloads
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「我的 → 附件」：下载进度与已下载文件的管理。
 *
 * 全屏页面，不带底部标签栏。
 */
@Composable
fun AttachmentsScreen(
    files: List<AttachmentStorage.LocalFile>,
    downloads: Map<String, Downloads.Task>,
    onBack: () -> Unit,
    onPreviewImage: (List<String>, Int) -> Unit,
    onOpenFile: (AttachmentStorage.LocalFile) -> Unit,
    onShare: (AttachmentStorage.LocalFile) -> Unit,
    onDelete: (AttachmentStorage.LocalFile) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (Downloads.Task) -> Unit,
) {

    val active = downloads.values.filter {
        it.state in setOf(Downloads.State.QUEUED, Downloads.State.RUNNING, Downloads.State.FAILED)
    }
    val images = files.filter { it.mime.startsWith("image/") }.map { it.name }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("附件", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(
                text = "Download/ErkePlus",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        if (active.isEmpty() && files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有下载任何附件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "在活动详情或作业里点附件即可下载",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (active.isNotEmpty()) {
                item { SectionTitle("正在下载") }
                items(active, key = { it.url }) { task ->
                    DownloadRow(
                        task = task,
                        onCancel = { onCancelDownload(task.url) },
                        onRetry = { onRetryDownload(task) },
                    )
                }
            }

            if (files.isNotEmpty()) {
                item { SectionTitle("已下载") }
                items(files, key = { it.name + it.uri }) { file ->
                    val imageIndex = images.indexOf(file.name)
                    FileRow(
                        file = file,
                        onPreview = {
                            if (imageIndex >= 0) {
                                onPreviewImage(images, imageIndex)
                            } else {
                                onOpenFile(file)
                            }
                        },
                        onShare = { onShare(file) },
                        onDelete = { onDelete(file) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun DownloadRow(task: Downloads.Task, onCancel: () -> Unit, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            if (task.state == Downloads.State.FAILED) {
                Text(
                    text = task.error ?: "下载失败",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${(task.progress * 100).toInt()}% · ${task.sizeText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (task.state == Downloads.State.FAILED) {
            TextButton(onClick = onRetry) { Text("重试") }
        } else {
            TextButton(onClick = onCancel) { Text("取消") }
        }
    }
}

@Composable
private fun FileRow(
    file: AttachmentStorage.LocalFile,
    onPreview: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onPreview)
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
                text = file.name.substringAfterLast('.', "").uppercase().take(4),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${Downloads.formatSize(file.size)} · ${formatTime(file.modified)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onShare) { Text("分享") }
        TextButton(onClick = onDelete) { Text("删除") }
    }
}

private fun formatTime(millis: Long): String =
    if (millis <= 0) "" else SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))
