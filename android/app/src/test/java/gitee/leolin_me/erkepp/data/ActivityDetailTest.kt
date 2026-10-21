package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityDetailTest {

    private val sample = Json.parseToJsonElement(
        """
        {
          "id": 566506,
          "name": "大学规划主题讲座",
          "logo": "https://example.com/cover.jpg",
          "activityStatus": 5,
          "classifyName": "思想政治引领",
          "organizationName": "校团委",
          "organizationAvatar": "https://example.com/org.png",
          "hours": 5,
          "credit": 0.5,
          "hoursToCreditRatio": 10,
          "startTime": 1792572000000,
          "endTime": 1792582800000,
          "enrollStartTime": 1792480000000,
          "enrollEndTime": 1792560000000,
          "address": "图书馆报告厅",
          "contact": "13800000000",
          "introduce": "<p>活动介绍</p>",
          "peopleLimit": 200,
          "joinMemberCount": 136,
          "genderLimit": 2,
          "gradeList": ["2024级", "2025级"],
          "collegeList": [{"id": 1, "name": "计算机学院"}],
          "honestLimit": 60,
          "attachment": {"url": "https://example.com/file.pdf"},
          "manager": {"name": "刘昊辰", "id": 2197962}
        }
        """.trimIndent(),
    ).jsonObject

    @Test
    fun `parses every field the detail page shows`() {
        val detail = parseActivityDetail(sample, "non-member")

        assertEquals(566506L, detail.id)
        assertEquals("大学规划主题讲座", detail.name)
        assertEquals("https://example.com/cover.jpg", detail.cover)
        assertEquals(5, detail.status)
        assertEquals("思想政治引领", detail.classifyName)
        assertEquals("校团委", detail.organizationName)
        assertEquals(5.0, detail.hours, 0.0)
        assertEquals(0.5, detail.credit, 0.0)
        assertEquals(1792572000000L, detail.startTime)
        assertEquals("图书馆报告厅", detail.address)
        assertEquals(200, detail.peopleLimit)
        assertEquals(136, detail.joinMemberCount)
        assertEquals(2, detail.genderLimit)
        assertEquals(listOf("2024级", "2025级"), detail.gradeNames)
        assertEquals(listOf("计算机学院"), detail.collegeNames)
        assertEquals(60.0, detail.honestLimit, 0.0)
        assertEquals("https://example.com/file.pdf", detail.attachmentUrl)
        assertEquals("刘昊辰", detail.managerName)
        assertEquals("non-member", detail.source)
        assertTrue(detail.hasRestriction)
    }

    @Test
    fun `missing optional fields fall back safely`() {
        val minimal = Json.parseToJsonElement("""{"id": 1, "name": "空活动"}""").jsonObject
        val detail = parseActivityDetail(minimal, "participant")

        assertEquals(1L, detail.id)
        assertEquals("空活动", detail.name)
        assertNull(detail.cover)
        assertNull(detail.attachmentUrl)
        assertEquals(0, detail.status)
        assertEquals(0.0, detail.hours, 0.0)
        assertTrue(detail.gradeNames.isEmpty())
        assertFalse(detail.hasRestriction)
    }

    @Test
    fun `time fields given as strings are still read`() {
        val raw = Json.parseToJsonElement("""{"id": 2, "name": "x", "startTime": "1792572000000"}""").jsonObject
        assertEquals(1792572000000L, parseActivityDetail(raw, "participant").startTime)
    }

    @Test
    fun `status falls back to status key when activityStatus is absent`() {
        val raw = Json.parseToJsonElement("""{"id": 3, "name": "y", "status": 7}""").jsonObject
        assertEquals(7, parseActivityDetail(raw, "participant").status)
    }
}
