package gitee.leolin_me.erkepp.data

import gitee.leolin_me.erkepp.ui.profile.rankSubtitle
import gitee.leolin_me.erkepp.ui.profile.shouldLoadMore
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 排行榜两个接口的解析与显示文本。
 *
 * 样本照抄 2026-10-24 的真实响应：`data` 里只有 `total` / `pageSize` / `pageNum` / `list`，
 * 没有 `lastPage`；`rownum` 可能是数字，也可能是空串。
 */
class RankParseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun entry(
        rownum: String = "4",
        name: String = "赵振国",
        score: Double = 0.0,
        hours: Double = 0.0,
        college: String = "数学与大数据学院",
        major: String = "信息与计算科学",
        klass: String = "信计26-3",
    ) = RankEntry(
        rownum = rownum.toIntOrNull() ?: 0,
        userId = 0L,
        name = name,
        avatar = null,
        gender = 1,
        score = score,
        hours = hours,
        studentNo = "2026306588",
        collegeName = college,
        majorName = major,
        className = klass,
        grade = "2026",
        campusName = "淮南校区",
    )

    @Test
    fun `page parses entries and derives lastPage when server omits it`() {
        val raw = """
            {"total":39,"pageSize":20,"pageNum":1,"list":[
              {"hours":7.0,"code":"2026306614","gender":1,"campusName":"淮南校区","className":"信计26-3",
               "avatar":"https://example.com/a.jpg","rownum":1,"score":0.175,"collegeName":"数学与大数据学院",
               "grade":2026,"name":"王俊文","id":2497513,"majorName":"信息与计算科学"},
              {"hours":3.0,"code":"2026306578","gender":2,"campusName":"淮南校区","className":"信计26-3",
               "avatar":"https://example.com/b.jpg","rownum":2,"score":0.075,"collegeName":"数学与大数据学院",
               "grade":2026,"name":"夏晓毓","id":2497477,"majorName":"信息与计算科学"}
            ]}
        """.trimIndent()
        val page = parseRankPage(json.parseToJsonElement(raw), page = 1, pageSize = 20)

        assertEquals(39, page.total)
        assertFalse(page.lastPage)
        assertEquals(2, page.list.size)

        val first = page.list.first()
        assertEquals(1, first.rownum)
        // 点人名进人物主页靠这个 id，不能丢
        assertEquals(2497513L, first.userId)
        assertEquals("王俊文", first.name)
        assertEquals("2026306614", first.studentNo)
        assertEquals(0.175, first.score, 0.0001)
        assertEquals(7.0, first.hours, 0.0001)
        assertEquals("信息与计算科学", first.majorName)
        assertEquals("信计26-3", first.className)
        assertEquals("数学与大数据学院", first.collegeName)
        assertEquals("淮南校区", first.campusName)
        assertEquals(1, first.gender)
        assertEquals("https://example.com/a.jpg", first.avatar)
    }

    @Test
    fun `empty rownum becomes zero and a short page is the last one`() {
        val raw = """
            {"total":39,"pageSize":20,"pageNum":2,"list":[
              {"hours":0.0,"code":"2026306580","gender":2,"className":"信计26-3","rownum":"",
               "score":0.0,"collegeName":"数学与大数据学院","grade":2026,"name":"许可莹",
               "id":2497479,"majorName":"信息与计算科学","avatar":""}
            ]}
        """.trimIndent()
        val page = parseRankPage(json.parseToJsonElement(raw), page = 2, pageSize = 20)

        assertEquals(0, page.list.first().rownum)
        assertNull("空头像要当成没有", page.list.first().avatar)
        assertTrue("不够一页就是最后一页", page.lastPage)
    }

    @Test
    fun `full last page is detected by total`() {
        val items = (1..20).joinToString(",") {
            """{"rownum":$it,"name":"n$it","score":0.0,"hours":0.0,"code":"c$it"}"""
        }
        val raw = """{"total":100,"pageSize":20,"pageNum":5,"list":[$items]}"""
        val page = parseRankPage(json.parseToJsonElement(raw), page = 5, pageSize = 20)

        assertEquals(20, page.list.size)
        assertTrue("第 5 页正好到总数", page.lastPage)

        val earlier = parseRankPage(json.parseToJsonElement(raw), page = 4, pageSize = 20)
        assertFalse(earlier.lastPage)
    }

    @Test
    fun `missing total falls back to the page size`() {
        val raw = """{"list":[{"rownum":1,"name":"a","score":0.1}]}"""
        val page = parseRankPage(json.parseToJsonElement(raw), page = 1, pageSize = 20)
        assertEquals(1, page.total)
        assertTrue(page.lastPage)
    }

    @Test
    fun `score text keeps up to three decimals`() {
        assertEquals("0", rankScoreText(0.0))
        assertEquals("0.025", rankScoreText(0.025))
        assertEquals("0.05", rankScoreText(0.05))
        assertEquals("0.075", rankScoreText(0.075))
        assertEquals("3.7", rankScoreText(3.7))
        assertEquals("4.225", rankScoreText(4.225))
        assertEquals("100", rankScoreText(100.0))
    }

    @Test
    fun `hours text drops the trailing zero`() {
        assertEquals("0", rankHoursText(0.0))
        assertEquals("7", rankHoursText(7.0))
        assertEquals("2.5", rankHoursText(2.5))
    }

    @Test
    fun `subtitle follows the selected scope`() {
        val entry = entry()
        assertEquals("数学与大数据学院 · 信息与计算科学", rankSubtitle(entry, RankScope.SCHOOL))
        assertEquals("信息与计算科学 · 信计26-3", rankSubtitle(entry, RankScope.COLLEGE))
        assertEquals("信计26-3", rankSubtitle(entry, RankScope.MAJOR))
        assertEquals("信息与计算科学", rankSubtitle(entry, RankScope.CLASS))
    }

    @Test
    fun `scope maps the official level numbers`() {
        assertEquals(RankScope.SCHOOL, RankScope.of(6))
        assertEquals(RankScope.COLLEGE, RankScope.of(8))
        assertEquals(RankScope.MAJOR, RankScope.of(9))
        assertEquals(RankScope.CLASS, RankScope.of(10))
        assertEquals(RankScope.CLASS, RankScope.of(0))
    }

    @Test
    fun `preload decides how early the next page is requested`() {
        // 共 21 项（前三名 + 20 条），看到第 14 项时：开预加载要提前取，关掉还不用
        assertTrue(shouldLoadMore(lastVisible = 14, itemCount = 21, preloadEnabled = true))
        assertFalse(shouldLoadMore(lastVisible = 14, itemCount = 21, preloadEnabled = false))
        // 只剩 2 项时两种情况都要取
        assertTrue(shouldLoadMore(lastVisible = 19, itemCount = 21, preloadEnabled = false))
        // 还没开始布局、或列表为空时不取
        assertFalse(shouldLoadMore(lastVisible = -1, itemCount = 21, preloadEnabled = true))
        assertFalse(shouldLoadMore(lastVisible = 0, itemCount = 0, preloadEnabled = true))
    }
}
