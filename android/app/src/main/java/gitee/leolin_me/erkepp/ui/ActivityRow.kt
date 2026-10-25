package gitee.leolin_me.erkepp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.ActivityItem
import gitee.leolin_me.erkepp.ui.org.orgActivityTime
import gitee.leolin_me.erkepp.ui.org.orgNumberText
import gitee.leolin_me.erkepp.ui.profile.AvatarImage

/**
 * 「图 + 两行字」的活动行：封面、标题、时间 · 学时 · 分类。
 *
 * 组织详情、人物主页的活动列表都用它——之前各写一份，样式一改就要改两处。
 */
@Composable
fun ActivityRow(
    item: ActivityItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
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
            val meta = buildList {
                if (whenText.isNotBlank()) add(whenText)
                if (item.hours > 0) add("${orgNumberText(item.hours)} 学时")
                if (item.classifyName.isNotBlank()) add(item.classifyName)
            }.joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
