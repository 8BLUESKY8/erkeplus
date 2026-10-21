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
        assertFalse(rules.isBlocked("任意标题"))
    }

    @Test
    fun `blank title never blocked`() {
        val rules = BlockRules.parse("讲座", "")
        assertFalse(rules.isBlocked("   "))
    }
}
