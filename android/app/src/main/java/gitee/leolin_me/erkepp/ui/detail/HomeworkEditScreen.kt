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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gitee.leolin_me.erkepp.data.ActivityAttachment
import gitee.leolin_me.erkepp.data.ActivityWork
import gitee.leolin_me.erkepp.data.hasTooWideChar
import gitee.leolin_me.erkepp.data.toUserMessage
import gitee.leolin_me.erkepp.ui.rememberCoverRequest
import kotlinx.coroutines.launch

/**
 * 提交 / 修改作业（原生）。
 *
 * 正文和已有附件来自 `activity/query/work_3_0_1`；提交走 `activity/submit/work_3_0_1`。
 * 新附件先传到官方 OSS（[gitee.leolin_me.erkepp.data.OssUploader]），上传成功后把
 * `url/name/size/type` 一起提交。单张图片不超过 5MB，最多 9 个附件。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeworkEditScreen(
    activityId: Long,
    activityName: String,
    loader: suspend () -> ActivityWork?,
    onSubmit: suspend (String, List<ActivityAttachment>) -> Unit,
    onAttachment: (ActivityAttachment) -> Unit,
    onBack: () -> Unit,
    /** 本次新加的附件（已上传完成，还在等提交）。 */
    addedAttachments: List<ActivityAttachment> = emptyList(),
    uploading: Boolean = false,
    onTakePhoto: () -> Unit = {},
    onPickFile: () -> Unit = {},
    onRemoveAdded: (ActivityAttachment) -> Unit = {},
) {
    var loading by remember(activityId) { mutableStateOf(true) }
    var submitting by remember(activityId) { mutableStateOf(false) }
    var content by remember(activityId) { mutableStateOf("") }
    var loadedAttachments by remember(activityId) { mutableStateOf<List<ActivityAttachment>>(emptyList()) }
    var error by remember(activityId) { mutableStateOf<String?>(null) }
    var confirm by remember(activityId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(activityId) {
        runCatching { loader() }
            .onSuccess { work ->
                content = work?.content.orEmpty()
                loadedAttachments = work?.attachments.orEmpty()
            }
            .onFailure { error = it.toUserMessage() }
        loading = false
    }

    val attachments = loadedAttachments + addedAttachments.filter { added ->
        loadedAttachments.none { it.url == added.url }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                text = "提交作业",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Text(
            text = activityName.ifBlank { "活动 $activityId" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))

        if (loading) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(40.dp))
                CircularProgressIndicator()
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = content,
                onValueChange = { if (it.length <= 500) content = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                label = { Text("作业内容") },
                placeholder = { Text("写点什么，至少要有一句") },
                shape = RoundedCornerShape(12.dp),
                supportingText = {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text("${content.length}/500")
                    }
                },
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "附件（${attachments.size}/9）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            if (attachments.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    attachments.forEach { attachment ->
                        val added = addedAttachments.any { it.url == attachment.url }
                        Box {
                            if (attachment.type == 1) {
                                AsyncImage(
                                    model = rememberCoverRequest(attachment.url, 84, maxWidthPx = 336),
                                    contentDescription = attachment.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onAttachment(attachment) },
                                )
                            } else {
                                Text(
                                    text = attachment.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(vertical = 4.dp)
                                        .clickable { onAttachment(attachment) },
                                )
                            }
                            if (added) {
                                Text(
                                    text = "×",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            shape = RoundedCornerShape(50),
                                        )
                                        .clickable { onRemoveAdded(attachment) }
                                        .padding(horizontal = 5.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (attachments.size < 9) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onTakePhoto,
                        enabled = !uploading && !submitting,
                    ) { Text("拍照") }
                    OutlinedButton(
                        onClick = onPickFile,
                        enabled = !uploading && !submitting,
                    ) { Text("选文件") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (uploading) {
                    "正在上传…"
                } else {
                    "图片单张不超过 5MB，最多 9 个；上传完再点提交"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (uploading) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (hasTooWideChar(content)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "正文里有服务端存不下的表情（🥰 这类），删掉再用文字描述；" +
                        "提交会报「未知数据错误」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        Button(
            onClick = { confirm = true },
            enabled = content.isNotBlank() && !hasTooWideChar(content) && !submitting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Text(if (submitting) "提交中…" else "提交")
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("确认提交作业？") },
            text = { Text("提交会直接写到学校平台，和在官方页面提交是同一次操作。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        submitting = true
                        scope.launch {
                            val ok = runCatching { onSubmit(content, attachments) }.isSuccess
                            submitting = false
                            if (ok) {
                                onBack()
                            } else {
                                error = "提交失败，详见日志 [action]"
                            }
                        }
                    },
                ) {
                    Text("提交")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("取消") }
            },
        )
    }
}
