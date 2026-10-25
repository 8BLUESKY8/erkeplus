package gitee.leolin_me.erkepp.ui.detail

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * 官方富文本介绍转成 Compose 文本。
 *
 * 之前这里塞了一个只读 WebView：创建、加载、`evaluateJavascript` 量高度，一次要 150～200ms，
 * 而且是主线程——打开活动详情「卡一帧」就是它。官方这段内容其实就是编辑器导出的简单 HTML
 * （段落、换行、粗体、链接文字），用原生文本渲染完全够用，还不用等异步回调。
 *
 * 规则：`br` 换算行，块级标签换段，`b`/`strong` 变粗体，其余标签丢掉，常见实体解码。
 */
internal fun htmlToAnnotated(html: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    var boldDepth = 0
    var pendingBreak = false
    var wrote = false

    fun emitText(raw: String) {
        val text = decodeEntities(raw).replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return
        if (pendingBreak && wrote) append("\n")
        pendingBreak = false
        if (boldDepth > 0) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text) }
        } else {
            append(text)
        }
        wrote = true
    }

    fun emitBreak() {
        if (wrote) pendingBreak = true
    }

    for (match in HTML_TAG.findAll(html)) {
        if (match.range.first > cursor) emitText(html.substring(cursor, match.range.first))
        val closing = match.groupValues[1] == "/"
        val name = match.groupValues[2].lowercase()
        when {
            name == "br" -> emitBreak()
            name == "b" || name == "strong" -> {
                boldDepth = if (closing) (boldDepth - 1).coerceAtLeast(0) else boldDepth + 1
            }
            name in BLOCK_TAGS -> emitBreak()
        }
        cursor = match.range.last + 1
    }
    if (cursor < html.length) emitText(html.substring(cursor))
}

private val HTML_TAG = Regex("<\\s*(/?)\\s*([a-zA-Z][a-zA-Z0-9]*)([^>]*)>")

private val BLOCK_TAGS = setOf(
    "p", "div", "li", "tr", "td", "ul", "ol", "table", "blockquote", "section", "article",
    "h1", "h2", "h3", "h4", "h5", "h6",
)

internal fun decodeEntities(raw: String): String {
    if (raw.indexOf('&') < 0) return raw
    var text = raw
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&hellip;", "…")
    text = NUMERIC_ENTITY.replace(text) { match ->
        val code = match.groupValues[1].toIntOrNull()
        if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else match.value
    }
    return text.replace("&amp;", "&")
}

private val NUMERIC_ENTITY = Regex("&#(\\d{1,7});")
