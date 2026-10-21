package gitee.leolin_me.erkepp.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {

    @Test
    fun `accepts password with all required classes`() {
        assertTrue(PasswordPolicy.isValid("Aa123456!"))
        assertTrue(PasswordPolicy.isValid("Zz9@aaaa"))
    }

    @Test
    fun `rejects too short password`() {
        assertFalse(PasswordPolicy.isValid("Aa1!"))
    }

    @Test
    fun `rejects password without digit`() {
        assertFalse(PasswordPolicy.isValid("Aaabcdef!"))
    }

    @Test
    fun `rejects password without uppercase`() {
        assertFalse(PasswordPolicy.isValid("aa123456!"))
    }

    @Test
    fun `rejects password without lowercase`() {
        assertFalse(PasswordPolicy.isValid("AA123456!"))
    }

    @Test
    fun `rejects password without special character`() {
        assertFalse(PasswordPolicy.isValid("Aa1234567"))
    }
}
