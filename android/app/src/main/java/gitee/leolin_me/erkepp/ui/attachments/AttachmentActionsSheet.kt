package gitee.leolin_me.erkepp.ui.attachments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.ActivityAttachment
import gitee.leolin_me.erkepp.data.Downloads

/**
 * 附件操作面板：下载、预览、其他应用打开、分享、删除。
 *
 * 未下载时只有「下载」一项，下载本身要经过用户点这一下才发生。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentActionsSheet(
    attachment: ActivityAttachment,
    downloaded: Boolean,
    isImage: Boolean,
    onDownload: () -> Unit,
    onDownloadAndPreview: () -> Unit,
    onPreview: () -> Unit,
    onOpenWithOther: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = Downloads.formatSize(if (attachment.size > 0) attachment.size else 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            if (!downloaded) {
                val large = attachment.size > Downloads.LARGE_FILE_BYTES
                ActionRow(
                    text = if (large) {
                        "下载（${Downloads.formatSize(attachment.size)}，注意流量）"
                    } else {
                        "下载"
                    },
                    onClick = onDownload,
                )
                ActionRow(
                    text = "下载并预览",
                    onClick = onDownloadAndPreview,
                    highlight = true,
                )
                Text(
                    text = "下载后可以预览、分享和用其他应用打开",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            } else {
                if (isImage) {
                    ActionRow("预览", onPreview)
                }
                ActionRow("在其他应用打开", onOpenWithOther)
                ActionRow("分享", onShare)
                ActionRow("删除本地文件", onDelete, danger = true)
            }
        }
    }
}

@Composable
private fun ActionRow(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    highlight: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                danger -> MaterialTheme.colorScheme.error
                highlight -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
