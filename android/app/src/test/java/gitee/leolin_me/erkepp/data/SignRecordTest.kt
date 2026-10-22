package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 打卡记录的解析：字段取自官方 `activity/sign/one/list`。 */
class SignRecordTest {

    private val json = Json

    @Test
    fun `parses check in and check out`() {
        val body = """
            [{"type":1,"signTime":1792902000000,"isSupplement":0},
             {"type":2,"signTime":1792905600000,"isSupplement":0}]
        """.trimIndent()
        val records = parseSignRecords(json.parseToJsonElement(body))
        assertEquals(2, records.size)
        // 按时间倒序：签退在前
        assertEquals("签退", records[0].action)
        assertEquals("签到", records[1].action)
        assertEquals(1792905600000L, records[0].time)
    }

    @Test
    fun `supplement records are marked`() {
        val body = """[{"type":1,"signTime":1792902000000,"isSupplement":1}]"""
        val records = parseSignRecords(json.parseToJsonElement(body))
        assertEquals("签到（补签）", records.single().label)
        assertTrue(records.single().supplement)
    }

    @Test
    fun `empty and malformed data yields nothing`() {
        assertTrue(parseSignRecords(null).isEmpty())
        assertTrue(parseSignRecords(json.parseToJsonElement("[]")).isEmpty())
        assertTrue(parseSignRecords(json.parseToJsonElement("""{"list":[]}""")).isEmpty())
        // 缺 signTime 的记录跳过，不抛异常
        assertTrue(parseSignRecords(json.parseToJsonElement("""[{"type":1}]""")).isEmpty())
    }
}
