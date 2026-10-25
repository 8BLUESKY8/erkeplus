package gitee.leolin_me.erkepp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 长内容折叠。组织详情、活动详情这类页面全是「看起来一样长」的段落，
 * 一屏塞不下就得让用户先看骨架、想细看再展开。
 *
 * 两条规则：默认收起；**只有真的溢出时才出「展开」**——短简介下面挂一个没用的按钮很蠢。
 */

/** 按行数折的长文本，适用于简介、公告正文这类纯文本。 */
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 6,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
) {
    ExpandableText(
        text = AnnotatedString(text),
        modifier = modifier,
        collapsedMaxLines = collapsedMaxLines,
        style = style,
        color = color,
    )
}

@Composable
fun ExpandableText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 6,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflow by remember(text) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                // 展开后不再回写：layout 变成完整高度，这里会把 overflow 冲成 false，按钮就消失了
                if (!expanded) overflow = layout.hasVisualOverflow
            },
        )
        if (overflow) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                modifier = Modifier.heightIn(min = 32.dp),
            ) {
                Text(
                    text = if (expanded) "收起" else "展开全文",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * 按条数折的列表，适用于「负责人」「主办活动」这类一行一条的内容。
 *
 * 用 `count` 而不是列表本身：调用方常把同一条列表分到不同分区里用，
 * 传条数最省事，也不用为每个数据类型写一遍。
 */
@Composable
fun CollapsibleItems(
    count: Int,
    modifier: Modifier = Modifier,
    collapsedCount: Int = 3,
    expandLabel: (Int) -> String = { "展开全部 $it 条" },
    itemContent: @Composable (Int) -> Unit,
) {
    var expanded by remember(count) { mutableStateOf(false) }
    val shown = if (expanded) count else minOf(count, collapsedCount)

    Column(modifier = modifier.fillMaxWidth()) {
        for (index in 0 until shown) {
            itemContent(index)
        }
        if (count > collapsedCount) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                modifier = Modifier.heightIn(min = 32.dp),
            ) {
                Text(
                    text = if (expanded) "收起" else expandLabel(count),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
