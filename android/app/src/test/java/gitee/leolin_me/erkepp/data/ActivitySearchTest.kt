package gitee.leolin_me.erkepp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivitySearchTest {

    private fun item(
        id: Long,
        name: String,
        organization: String = "",
        classify: String = "",
        second: String? = null,
    ) = ActivityItem(
        id = id,
        name = name,
        cover = null,
        hours = 0.0,
        classifyName = classify,
        classifySecondName = second,
        startTime = 0L,
        endTime = 0L,
        enrollEndTime = 0L,
        enrollCount = 0,
        peopleLimit = 0,
        organizationName = organization,
        status = 5,
        recommended = false,
        limitType = 0,
        full = false,
    )

    private val pool = listOf(
        item(1, "第十九届“挑战杯”课外学术科技作品竞赛校内选拔赛", "校团委", "创新创业"),
        item(2, "挑战杯红色专项活动", "校团委", "创新创业"),
        item(3, "21天单词打卡", "人工智能26-7_2026", "综合素质拓展"),
        item(4, "志愿服务进社区", "青年志愿者协会", "志愿公益"),
        item(5, "企业家进校园讲座", "校团委", "创新创业"),
    )

    @Test
    fun `empty query keeps everything`() {
        assertEquals(pool.size, ActivitySearch.filter(pool, ActivitySearch.terms("")).size)
    }

    @Test
    fun `matches title substring`() {
        val hits = ActivitySearch.filter(pool, ActivitySearch.terms("挑战杯"))
        assertEquals(listOf(1L, 2L), hits.map { it.id })
    }

    @Test
    fun `matches organization and classify too`() {
        assertEquals(listOf(4L), ActivitySearch.filter(pool, ActivitySearch.terms("志愿者")).map { it.id })
        assertEquals(listOf(1L, 2L, 5L), ActivitySearch.filter(pool, ActivitySearch.terms("创新创业")).map { it.id })
    }

    @Test
    fun `multiple terms must all hit`() {
        assertEquals(listOf(2L), ActivitySearch.filter(pool, ActivitySearch.terms("挑战杯 红色")).map { it.id })
        assertTrue(ActivitySearch.filter(pool, ActivitySearch.terms("挑战杯 不存在")).isEmpty())
    }

    @Test
    fun `keywords are split on spaces and full width spaces`() {
        assertEquals(listOf("挑战杯", "红色"), ActivitySearch.terms("  挑战杯　红色 "))
    }

    @Test
    fun `no match yields empty rather than everything`() {
        assertFalse(ActivitySearch.matches(pool[2], ActivitySearch.terms("挑战杯")))
        assertTrue(ActivitySearch.filter(pool, ActivitySearch.terms("挑战杯")).none { it.id == 3L })
    }
}
