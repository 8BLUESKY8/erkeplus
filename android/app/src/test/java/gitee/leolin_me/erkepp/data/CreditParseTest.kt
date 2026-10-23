package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** 诚信值三个接口的解析，样本取自 2026-10-23 的真实响应。 */
class CreditParseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `grades parse scores and order by score`() {
        val raw = """
            [{"maxViolateNumber":0,"minScore":100,"grade":"完美","id":295,"maxScore":100,"minViolateNumber":0},
             {"maxViolateNumber":3,"minScore":90,"grade":"优异","id":296,"maxScore":99,"minViolateNumber":1},
             {"maxViolateNumber":6,"minScore":80,"grade":"良好","id":297,"maxScore":89,"minViolateNumber":4},
             {"maxViolateNumber":9,"minScore":70,"grade":"中等","id":298,"maxScore":79,"minViolateNumber":7},
             {"maxViolateNumber":12,"minScore":60,"grade":"一般","id":299,"maxScore":69,"minViolateNumber":10},
             {"maxViolateNumber":null,"minScore":0,"grade":"较差","id":300,"maxScore":59,"minViolateNumber":13}]
        """.trimIndent()
        val grades = parseCreditGrades(json.parseToJsonElement(raw))
        assertEquals(6, grades.size)
        assertEquals(100, grades.first().minScore)
        assertEquals("完美", grades.first().grade)
        assertEquals("优异", grades.gradeOf(98.33))
        assertEquals("完美", grades.gradeOf(100.0))
        assertEquals("较差", grades.gradeOf(0.0))
        assertEquals("", grades.gradeOf(-1.0))
    }

    @Test
    fun `detail and records parse`() {
        val detail = json.parseToJsonElement(
            """{"code":0,"data":{"faithUpdateTime":1792227641000,"blacklist":null,"credit":98.33}}""",
        ).jsonObject
        val data = detail["data"]!!.jsonObject
        assertEquals(98.33, (data["credit"] as kotlinx.serialization.json.JsonPrimitive).content.toDouble(), 0.001)
        assertEquals(1792227641000L, (data["faithUpdateTime"] as kotlinx.serialization.json.JsonPrimitive).content.toLong())
    }
}
