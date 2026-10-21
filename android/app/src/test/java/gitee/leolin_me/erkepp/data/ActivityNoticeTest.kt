package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityNoticeTest {

    @Test
    fun `parses notice list with ctime and isPopup`() {
        val data = Json.parseToJsonElement(
            """
            {"total":1,"pageSize":50,"pageNum":1,"list":[
              {"activityId":574449,"isPopup":1,"id":26199,"content":"一周内三个项目分享内容相同，请选择对应日期。","ctime":1792497702000}
            ]}
            """.trimIndent(),
        )
        val notices = parseNotices(data)
        assertEquals(1, notices.size)
        assertTrue(notices[0].isPopup)
        assertEquals(26199L, notices[0].id)
        assertEquals("2026-10-20 20:01", notices[0].time)
        assertTrue(notices[0].plainText.contains("一周内三个项目分享内容相同"))
    }

    @Test
    fun `html content is flattened for the popup`() {
        val data = Json.parseToJsonElement(
            """{"list":[{"id":1,"isPopup":0,"content":"<p>第一段</p><br/>第二段<b>重点</b>"}]}""",
        )
        val notice = parseNotices(data).single()
        assertEquals("第一段\n第二段重点", notice.plainText)
    }

    @Test
    fun `empty or missing list yields no notices`() {
        assertTrue(parseNotices(Json.parseToJsonElement("""{"total":0,"list":[]}""")).isEmpty())
        assertTrue(parseNotices(Json.parseToJsonElement("""{"total":0}""")).isEmpty())
        assertTrue(parseNotices(null).isEmpty())
    }

    @Test
    fun `item without content is skipped`() {
        val data = Json.parseToJsonElement("""{"list":[{"id":1},{"id":2,"content":"有内容"}]}""")
        val notices = parseNotices(data)
        assertEquals(1, notices.size)
        assertEquals(2L, notices[0].id)
    }
}
