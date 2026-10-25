package gitee.leolin_me.erkepp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockRulesTest {

    @Test
    fun `empty rules block nothing`() {
        val rules = BlockRules.parse("", "")
        assertTrue(rules.isEmpty)
        assertFalse(rules.isBlocked("任何标题"))
    }

    @Test
    fun `keyword match is case insensitive and by substring`() {
        val rules = BlockRules.parse("讲座, 打卡", "")
        assertEquals(2, rules.keywordCount)
        assertTrue(rules.isBlocked("大学规划主题讲座"))
        assertTrue(rules.isBlocked("21天打卡活动"))
        assertFalse(rules.isBlocked("志愿服务活动"))
    }

    @Test
    fun `keywords split on multiple separators`() {
        val rules = BlockRules.parse("讲座\n打卡；问卷、签到", "")
        assertEquals(4, rules.keywordCount)
    }

    @Test
    fun `regex rules match`() {
        val rules = BlockRules.parse("", "^\\d+天.*打卡$")
        assertEquals(1, rules.patternCount)
        assertTrue(rules.isBlocked("21天单词打卡"))
        assertFalse(rules.isBlocked("21天单词挑战"))
    }

    @Test
    fun `invalid regex is dropped instead of crashing`() {
        val rules = BlockRules.parse("", "[unclosed")
        assertEquals(0, rules.patternCount)
        assertEquals(1, rules.invalidPatternCount)
        assertFalse(rules.isBlocked("任意标题"))
    }

    @Test
    fun `blank title never blocked`() {
        val rules = BlockRules.parse("讲座", "")
        assertFalse(rules.isBlocked("   "))
    }

    // ---- 已报名的活动默认免疫 ----

    private fun item(title: String, applyStatus: Int) = ActivityItem(
        id = 1L,
        name = title,
        cover = null,
        hours = 2.0,
        classifyName = "",
        classifySecondName = null,
        startTime = 0L,
        endTime = 0L,
        enrollCount = 0,
        peopleLimit = 0,
        organizationName = "",
        status = 5,
        recommended = false,
        limitType = 0,
        full = false,
        applyStatus = applyStatus,
    )

    @Test
    fun `enrolled activities skip the rules by default`() {
        val rules = BlockRules.parse("打卡", "")
        // 1 待审核、2 已通过：我报过名，不该被藏
        assertFalse(rules.blocks(item("21天单词打卡", 1)))
        assertFalse(rules.blocks(item("21天单词打卡", 2)))
        // 0 没申请、3 被驳回：照常屏蔽
        assertTrue(rules.blocks(item("21天单词打卡", 0)))
        assertTrue(rules.blocks(item("21天单词打卡", 3)))
    }

    @Test
    fun `explicit switch makes rules apply to enrolled activities`() {
        val rules = BlockRules.parse("打卡", "")
        assertTrue(rules.blocks(item("21天单词打卡", 2), blockEnrolledToo = true))
        // 打开开关不影响没报名的
        assertTrue(rules.blocks(item("21天单词打卡", 0), blockEnrolledToo = true))
    }

    @Test
    fun `enrolled activities with clean titles are visible anyway`() {
        val rules = BlockRules.parse("讲座", "")
        assertFalse(rules.blocks(item("志愿服务", 2)))
        assertFalse(rules.blocks(item("志愿服务", 0)))
    }
}
