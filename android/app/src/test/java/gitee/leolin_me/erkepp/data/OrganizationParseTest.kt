package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 组织解析。样本都来自 2026-10-24 真机 `organization` 这族接口的原始响应，
 * 不是照 H5 猜出来的字段名。
 */
class OrganizationParseTest {

    private val item = """
        {"joinTime":1792804049000,"introduce":"化学与火药纹画协会","chairmaId":1555524,
         "memberCount":119,"orgProcessStatus":"re3","auditRemark":"","avatar":"",
         "chatGroupId":"O10361_17054","name":"化学与火药纹画社","id":17054,
         "currentFlowCategory":"organ-3","energy":663.0,"status":1}
    """.trimIndent()

    private val classItem = """
        {"joinTime":1791795112000,"introduce":"由班级生成","chairmaId":0,"memberCount":39,
         "orgProcessStatus":"re3","auditRemark":"","avatar":"","chatGroupId":"O10361_64536",
         "identity":1,"name":"信计26-3_2026","auditSatus":"","id":64536,
         "currentFlowCategory":"organ-7","energy":100.0}
    """.trimIndent()

    @Test
    fun `pending application keeps its status and time`() {
        val parsed = parseOrgItem(Json.parseToJsonElement(item))!!
        assertEquals(17054L, parsed.id)
        assertEquals("化学与火药纹画社", parsed.name)
        assertEquals(119, parsed.memberCount)
        assertEquals(OrgApplyStatus.PENDING.value, parsed.status)
        assertEquals(663.0, parsed.energy, 0.001)
        // 时间戳按毫秒转成日期，而不是原样显示 13 位数字
        assertEquals("2026-10-24", parsed.timeText)
        assertNull(parsed.avatar)
    }

    @Test
    fun `record without status is treated as unknown`() {
        val parsed = parseOrgItem(Json.parseToJsonElement(classItem))!!
        assertEquals(64536L, parsed.id)
        assertEquals(0, parsed.status)
        assertNull(OrgApplyStatus.of(parsed.status))
        // 身份 1 = 普通成员，界面上不显示标签
        assertEquals(1, parsed.identity)
        assertEquals("", orgIdentityLabel(parsed.identity))
    }

    @Test
    fun `page uses total to decide last page`() {
        val page = parseOrgPage(
            Json.parseToJsonElement("""{"total":2,"pageSize":3,"pageNum":1,"list":[$item]}"""),
            page = 1,
            pageSize = 3,
        )
        assertEquals(2, page.total)
        assertTrue(page.lastPage)

        val second = parseOrgPage(
            Json.parseToJsonElement("""{"total":40,"pageSize":20,"pageNum":1,"list":[]}"""),
            page = 1,
            pageSize = 20,
        )
        assertFalse(second.lastPage)
    }

    @Test
    fun `organization status codes are translated`() {
        assertEquals("已注册", orgProcessLabel("re3"))
        assertEquals("年审中", orgProcessLabel("ya2"))
        assertEquals("已注销", orgProcessLabel("cw3"))
        // 认不出来的码不编造文字
        assertEquals("", orgProcessLabel("zz9"))
    }

    @Test
    fun `pending application blocks a second one`() {
        val detail = parseOrgDetail(
            Json.parseToJsonElement(
                """
                {"id":39261,"name":"人工智能协会","avatar":"","memberCount":343,
                 "nature":2,"natureName":"社团协会","categoryName":"学术科技类",
                 "affiliationName":"安徽理工大学","chairmanName":"人工智能协会","guiderName":"朱宇",
                 "introduce":"大学生人工智能协会","energy":925.0,"orgProcessStatus":"re3",
                 "applyStatus":1,"isJoin":1,"isAdd":1,"rule":1}
                """.trimIndent(),
            ),
        )
        assertEquals("社团协会", detail.natureName)
        assertEquals(343, detail.memberCount)
        assertEquals("大学生人工智能协会", detail.intro)
        // 待审核：不能再点一次「申请加入」
        assertFalse(detail.canApply)
    }

    @Test
    fun `rejected application can be sent again`() {
        val detail = parseOrgDetail(
            Json.parseToJsonElement(
                """
                {"id":17085,"name":"机器人协会","memberCount":1021,"applyStatus":3,
                 "isJoin":0,"isAdd":1,"rule":2}
                """.trimIndent(),
            ),
        )
        assertEquals(3, detail.applyStatus)
        assertTrue(detail.canApply)
    }

    @Test
    fun `closed or invite-only organizations hide apply`() {        val closed = parseOrgDetail(
            Json.parseToJsonElement(
                """{"id":1,"name":"x","applyStatus":0,"isJoin":0,"isAdd":0,"rule":1}""",
            ),
        )
        assertFalse(closed.canApply)

        val inviteOnly = parseOrgDetail(
            Json.parseToJsonElement(
                """{"id":2,"name":"y","applyStatus":0,"isJoin":0,"isAdd":1,"rule":3}""",
            ),
        )
        assertFalse(inviteOnly.canApply)

        val open = parseOrgDetail(
            Json.parseToJsonElement(
                """{"id":3,"name":"z","applyStatus":0,"isJoin":0,"isAdd":1,"rule":1}""",
            ),
        )
        assertTrue(open.canApply)
    }

    // ---- 组织目录（#/service/organize 用的那套接口） ----

    private val catalogItem = """
        {"chatGroupId":"O10361_17085","introduce":"大学生机器人协会","identity":0,
         "chairmaId":1555513,"memberCount":874,"name":"机器人协会","rule":2,"id":17085,
         "avatar":"","isAdd":1,"energy":3505.0,"isElect":1}
    """.trimIndent()

    @Test
    fun `catalog page reads total and last page`() {
        val page = parseOrgCatalogPage(
            Json.parseToJsonElement("""{"total":140,"pageSize":20,"pageNum":1,"list":[$catalogItem]}"""),
            page = 1,
            pageSize = 20,
        )
        assertEquals(140, page.total)
        assertFalse(page.lastPage)
        val item = page.list.single()
        assertEquals(17085L, item.id)
        assertEquals("机器人协会", item.name)
        assertEquals(874, item.memberCount)
        assertEquals(3505.0, item.energy, 0.001)
        // 目录里 isElect 实测恒为 1，不能因此把每条都标成「换届中」，也不能挡掉申请
        assertEquals(1, item.isElect)
        assertTrue(item.canApply)
        assertFalse(item.joined)
    }

    @Test
    fun `catalog item without rule cannot apply`() {
        val closed = parseOrgCatalogItem(
            Json.parseToJsonElement("""{"id":1,"name":"a","rule":3,"isAdd":1,"identity":0}"""),
        )!!
        assertFalse(closed.canApply)

        val notOpen = parseOrgCatalogItem(
            Json.parseToJsonElement("""{"id":2,"name":"b","rule":1,"isAdd":0,"identity":0}"""),
        )!!
        assertFalse(notOpen.canApply)

        val joined = parseOrgCatalogItem(
            Json.parseToJsonElement("""{"id":3,"name":"c","rule":1,"isAdd":1,"identity":2}"""),
        )!!
        assertTrue(joined.joined)
    }

    @Test
    fun `sort options match what the site sends`() {
        // 官方下拉是「最新 / 最热」，发给接口的值是 0 / 1；字典里的 1 最热、2 最活跃不属于这一页
        assertEquals(listOf(0 to "最新", 1 to "最热"), OrgChoices.SORTS.map { it.id to it.name })
        assertEquals(0, OrgChoices.SORTS.first().id)
        // 字典拉不到时至少还有「全部」
        assertEquals(listOf("全部"), OrgChoices.FALLBACK.natures.map { it.name })
    }

    // ---- 「已申请」用的是另一个接口，字段名全都不一样 ----

    @Test
    fun `applied list maps organization fields and status`() {
        val page = parseOrgAppliedPage(
            Json.parseToJsonElement(
                """
                {"total":2,"pageSize":3,"pageNum":1,"list":[
                  {"organizationId":39261,"organizationName":"人工智能协会","memberCount":441,
                   "organizationAvatar":"","applyRefuseReason":"","id":1072528,
                   "applyTime":1792803677000,"applyStatus":1,"energy":925.0},
                  {"organizationId":17054,"organizationName":"化学与火药纹画社","memberCount":119,
                   "organizationAvatar":"","applyRefuseReason":"材料不全","id":1072658,
                   "applyTime":1792804049000,"applyStatus":3,"energy":772.0}
                ]}
                """.trimIndent(),
            ),
            page = 1,
            pageSize = 3,
        )
        assertEquals(2, page.total)
        assertTrue(page.lastPage)
        val pending = page.list.first()
        // 组织的 id 用 organizationId，不是申请记录自己的 id
        assertEquals(39261L, pending.organizationId)
        assertEquals("人工智能协会", pending.name)
        assertEquals(1, pending.applyStatus)
        assertEquals("2026-10-24", pending.applyTime)
        assertNull(pending.avatar)

        val rejected = page.list.last()
        assertEquals(3, rejected.applyStatus)
        assertEquals("材料不全", rejected.refuseReason)
    }
}
