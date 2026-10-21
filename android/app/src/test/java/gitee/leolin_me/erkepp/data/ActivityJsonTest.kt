package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityJsonTest {

    private val json = Json

    @Test
    fun `params contain the eleven required keys`() {
        val params = buildActivityParams(
            ActivityQuery(
                page = 2,
                status = "5",
                classifyId = 1440,
                sortType = 2,
                search = "毕业",
                activityTime = 1792512000000,
            ),
        )
        assertEquals(11, params.size)
        assertEquals("5", params["status"]!!.jsonPrimitive.content)
        assertEquals(1440, params["classifyId"]!!.jsonPrimitive.int)
        assertEquals(2, params["pageNum"]!!.jsonPrimitive.int)
        assertEquals("毕业", params["search"]!!.jsonPrimitive.content)
        assertEquals(1792512000000, params["activityTime"]!!.jsonPrimitive.content.toLong())
        assertEquals("activity_iseErollment", params["fieldType"]!!.jsonPrimitive.content)
        assertEquals("switch", params["keyWord"]!!.jsonPrimitive.content)
    }

    @Test
    fun `parses a real activity list response`() {
        val body = """
            {"code":0,"data":{"total":133,"pageSize":10,"list":[{
              "isRecommend":1,"isfull":2,"activityStatus":5,
              "logo":"https://gytx-2ketang.oss-cn-hangzhou.aliyuncs.com/upload/image/a.jpg",
              "startTime":1796090400000,"endTime":1796745540000,"id":566950,
              "classifyName":"思想政治引领","hours":5.0,"organizationName":"校团委",
              "name":"毕业生德育铸魂引领活动(淮南校区)","enrollCount":1362,"limitType":0,
              "classifySecondName":"理论学习活动","peopleLimit":20000
            }]}}
        """.trimIndent()

        val page = parseActivityPage(json.parseToJsonElement(body), 1)

        assertEquals(133, page.total)
        assertEquals(1, page.items.size)
        val item = page.items.first()
        assertEquals(566950L, item.id)
        assertEquals("毕业生德育铸魂引领活动(淮南校区)", item.name)
        assertTrue(item.cover!!.endsWith("a.jpg"))
        assertEquals(5.0, item.hours, 0.001)
        assertEquals("思想政治引领", item.classifyName)
        assertEquals("理论学习活动", item.classifySecondName)
        assertEquals("校团委", item.organizationName)
        assertEquals(1362, item.enrollCount)
        assertEquals(5, item.status)
        assertTrue(item.recommended)
        assertTrue(item.full)
    }

    @Test
    fun `missing data yields empty page`() {
        val page = parseActivityPage(json.parseToJsonElement("""{"code":0,"data":null}"""), 3)
        assertEquals(0, page.total)
        assertEquals(3, page.pageNum)
        assertTrue(page.items.isEmpty())
    }

    @Test
    fun `items without id are skipped`() {
        val body = """{"code":0,"data":{"total":1,"list":[{"name":"坏数据"}]}}"""
        val page = parseActivityPage(json.parseToJsonElement(body), 1)
        assertEquals(1, page.total)
        assertTrue(page.items.isEmpty())
    }
}
