package gitee.leolin_me.erkepp.ui.attachments

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gitee.leolin_me.erkepp.data.AttachmentStorage
import gitee.leolin_me.erkepp.data.Downloads
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「我的 → 附件」：下载进度、已下载文件管理、回收站。
 *
 * 两种视图：分条列表与双列卡片，图片都显示缩略图（文件名对用户没有意义）。
 * 删除有二次确认，并可选择放进系统回收站，勾选状态会被记住。
 */
@Composable
fun AttachmentsScreen(
    files: List<AttachmentStorage.LocalFile>,
    downloads: Map<String, Downloads.Task>,
    trashFiles: List<AttachmentStorage.LocalFile>,
    gridView: Boolean,
    deleteToTrash: Boolean,
    trashSupported: Boolean,
    inTrash: Boolean,
    onInTrashChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onToggleView: () -> Unit,
    onDeleteToTrashChange: (Boolean) -> Unit,
    onPreviewImage: (List<String>, Int) -> Unit,
    onOpenFile: (AttachmentStorage.LocalFile) -> Unit,
    onShare: (AttachmentStorage.LocalFile) -> Unit,
    onTrash: (AttachmentStorage.LocalFile) -> Unit,
    onDeleteForever: (AttachmentStorage.LocalFile) -> Unit,
    onRestore: (AttachmentStorage.LocalFile) -> Unit,
    onOpenTrash: () -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (Downloads.Task) -> Unit,
) {
    BackHandler(onBack = onBack)

    var confirmTarget by remember { mutableStateOf<AttachmentStorage.LocalFile?>(null) }
    var toTrash by remember(deleteToTrash) { mutableStateOf(deleteToTrash) }

    val active = downloads.values.filter {
        it.state in setOf(Downloads.State.QUEUED, Downloads.State.RUNNING, Downloads.State.FAILED)
    }
    val images = files.filter { it.mime.startsWith("image/") }.map { it.name }

    fun openFile(file: AttachmentStorage.LocalFile) {
        val index = images.indexOf(file.name)
        if (index >= 0) onPreviewImage(images, index) else onOpenFile(file)
    }

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
            TextButton(onClick = { if (inTrash) onInTrashChange(false) else onBack() }) { Text("返回") }
            Text(
                text = if (inTrash) "回收站" else "附件",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            if (!inTrash) {
                TextButton(onClick = onToggleView) { Text(if (gridView) "列表" else "网格") }
                TextButton(
                    onClick = {
                        onOpenTrash()
                        onInTrashChange(true)
                    },
                ) { Text("回收站") }
            }
        }

        if (inTrash) {
            if (!trashSupported) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "当前系统没有回收站，删除即彻底删除",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }
            if (trashFiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("回收站是空的", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(trashFiles, key = { it.name + it.uri }) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Thumb(file, 44.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = Downloads.formatSize(file.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onRestore(file) }) { Text("还原") }
                        TextButton(onClick = { confirmTarget = file }) { Text("彻底删除") }
                    }
                }
            }
            confirmTarget?.let { target ->
                DeleteDialog(
                    target = target,
                    trashSupported = trashSupported,
                    toTrash = false,
                    onToTrashChange = { },
                    onConfirm = {
                        onDeleteForever(target)
                        confirmTarget = null
                    },
                    onDismiss = { confirmTarget = null },
                )
            }
            return@Column
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

        if (active.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionTitle("正在下载")
                active.forEach { task ->
                    DownloadRow(
                        task = task,
                        onCancel = { onCancelDownload(task.url) },
                        onRetry = { onRetryDownload(task) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (files.isNotEmpty()) {
            if (gridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(files, key = { it.name + it.uri }) { file ->
                        FileCard(
                            file = file,
                            onOpen = { openFile(file) },
                            onShare = { onShare(file) },
                            onDelete = { confirmTarget = file },
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { SectionTitle("已下载") }
                    items(files, key = { it.name + it.uri }) { file ->
                        FileRow(
                            file = file,
                            onOpen = { openFile(file) },
                            onShare = { onShare(file) },
                            onDelete = { confirmTarget = file },
                        )
                    }
                }
            }
        }
    }

    confirmTarget?.let { target ->
        DeleteDialog(
            target = target,
            trashSupported = trashSupported,
            toTrash = toTrash,
            onToTrashChange = {
                toTrash = it
                onDeleteToTrashChange(it)
            },
            onConfirm = {
                if (trashSupported && toTrash) onTrash(target) else onDeleteForever(target)
                confirmTarget = null
            },
            onDismiss = { confirmTarget = null },
        )
    }
}

@Composable
private fun DeleteDialog(
    target: AttachmentStorage.LocalFile,
    trashSupported: Boolean,
    toTrash: Boolean,
    onToTrashChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除这个文件？") },
        text = {
            Column {
                Text(target.name, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (trashSupported) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = toTrash, onCheckedChange = onToTrashChange)
                        Text("放进回收站，可以还原")
                    }
                } else {
                    Text(
                        "当前系统没有回收站，删除后无法恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun Thumb(file: AttachmentStorage.LocalFile, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        if (file.mime.startsWith("image/")) {
            AsyncImage(
                model = file.uri,
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = file.name.substringAfterLast('.', "").uppercase().take(4),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
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
                LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth())
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
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(file, 48.dp)
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

@Composable
private fun FileCard(
    file: AttachmentStorage.LocalFile,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            if (file.mime.startsWith("image/")) {
                AsyncImage(
                    model = file.uri,
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = file.name.substringAfterLast('.', "").uppercase().take(4),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${Downloads.formatSize(file.size)} · ${formatTime(file.modified)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onShare) { Text("分享") }
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

private fun formatTime(millis: Long): String =
    if (millis <= 0) "" else SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))
