package gitee.leolin_me.erkepp.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 密码规则与官方前端正则等价：≥8 位 + 数字 + 大写 + 小写 + 特殊符号。 */
class PasswordRuleTest {

    @Test
    fun `accepts a password with everything`() {
        assertTrue(PasswordRule.isValid("Aa1!aaaa"))
    }

    @Test
    fun `rejects short password`() {
        assertTrue(PasswordRule.problems("Aa1!aaa").contains("至少 8 位"))
    }

    @Test
    fun `rejects missing categories`() {
        assertTrue(PasswordRule.problems("aaaaaaaa").contains("包含数字"))
        assertTrue(PasswordRule.problems("aaaaaaaa").contains("包含大写字母"))
        assertTrue(PasswordRule.problems("AAAAAAAA1!").contains("包含小写字母"))
        assertTrue(PasswordRule.problems("Aaaaaaaa1").contains("包含特殊符号"))
        assertFalse(PasswordRule.isValid("Aaaaaaaa1"))
    }

    @Test
    fun `official regex and rule agree`() {
        val regex = Regex("^\\S*(?=\\S{8,})(?=\\S*\\d)(?=\\S*[A-Z])(?=\\S*[a-z])(?=\\S*[!@#$%^&*? ])\\S*$")
        val samples = listOf("Aa1!aaaa", "Aa1!aaa", "aaaaaaaa", "AAAAAAAA1!", "Aaaaaaaa1", "", "Aa1! aaaa ")
        samples.forEach { sample ->
            assertEquals("sample=$sample", regex.matches(sample), PasswordRule.isValid(sample))
        }
    }
}
