package gitee.leolin_me.erkepp.ui.detail

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 富文本介绍的原生渲染：以前这里是 WebView，打开详情要 150～200ms，还依赖 JS 回调量高度。
 * 官方这段 HTML 就是编辑器导出的简单标签，用纯文本 + 粗体就够了。
 */
class HtmlTextTest {

    @Test
    fun `paragraphs become separate lines`() {
        val text = htmlToAnnotated("<p>第一段</p><p>第二段</p>")
        assertEquals("第一段\n第二段", text.text)
    }

    @Test
    fun `br becomes one line break`() {
        assertEquals("行一\n行二", htmlToAnnotated("行一<br/>行二").text)
        assertEquals("行一\n行二", htmlToAnnotated("行一<br>行二").text)
    }

    @Test
    fun `entities are decoded`() {
        assertEquals("A&B<C>D", htmlToAnnotated("A&amp;B&lt;C&gt;D").text)
        // 编辑器的空格常写成实体
        assertEquals("前 后", htmlToAnnotated("前&nbsp;后").text)
        assertEquals("—…", htmlToAnnotated("&mdash;&hellip;").text)
        assertEquals("中", htmlToAnnotated("&#20013;").text)
    }

    @Test
    fun `bold spans are kept`() {
        val text = htmlToAnnotated("<b>加粗</b>普通")
        assertEquals("加粗普通", text.text)
        val spans = text.spanStyles
        assertTrue(spans.any { it.start == 0 && it.end == 2 && it.item.fontWeight == FontWeight.SemiBold })
    }

    @Test
    fun `unknown tags and images are dropped without leaving artifacts`() {
        assertEquals("只有文字", htmlToAnnotated("<img src=\"x.png\">只有文字").text)
        assertEquals(
            "A\nB",
            htmlToAnnotated("<div><span style=\"color:red\">A</span></div><div>B</div>").text,
        )
        // 内容前后的空段落不占行
        assertEquals("正文", htmlToAnnotated("<p></p><p>正文</p><p>&nbsp;</p>").text)
    }

    @Test
    fun `plain text passes through unchanged`() {
        assertEquals("没有标签的纯文本", htmlToAnnotated("没有标签的纯文本").text)
    }
}
