package gitee.leolin_me.erkepp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignCodeTest {

    private val checkIn = "qutuo://sign?activityId=566462&userId=1912797&sp=1790672679000&isSignIn=action"
    private val checkOut = "qutuo://sign?activityId=566462&userId=1912797&sp=1790672679000&isSignIn="
    private val waitSign = "qutuo://waitSign?activityId=566462&userId=1912797&sp=1790672679000"

    @Test
    fun `check in code carries action`() {
        val ticket = parseSignCode(checkIn)!!
        assertEquals(566462L, ticket.activityId)
        assertEquals("1912797", ticket.userId)
        assertEquals("1790672679000", ticket.sp)
        assertEquals(2, ticket.type)
        assertEquals(1, ticket.signSwitch)
        assertEquals(SignAction.CHECK_IN, ticket.action)
    }

    @Test
    fun `check out code has empty action`() {
        val ticket = parseSignCode(checkOut)!!
        assertEquals(2, ticket.type)
        assertEquals(2, ticket.signSwitch)
        assertEquals(SignAction.CHECK_OUT, ticket.action)
    }

    @Test
    fun `waitSign branches to type 1 without signSwitch`() {
        val ticket = parseSignCode(waitSign)!!
        assertEquals(1, ticket.type)
        assertNull(ticket.signSwitch)
        assertEquals(SignAction.CHECK_IN_OR_OUT, ticket.action)
    }

    @Test
    fun `yiban suffix is stripped`() {
        val withSuffix = "$checkIn?yiban=yiban_scan_result"
        val ticket = parseSignCode(withSuffix)!!
        assertEquals(566462L, ticket.activityId)
        assertEquals("1790672679000", ticket.sp)
        assertEquals(1, ticket.signSwitch)
        // yiban 尾巴不能影响 action 判断
        assertEquals(SignAction.CHECK_IN, ticket.action)
    }

    @Test
    fun `static code keeps empty sp instead of inventing one`() {
        val ticket = parseSignCode("qutuo://sign?activityId=1&userId=2&sp=&isSignIn=action")!!
        assertEquals("", ticket.sp)
        assertEquals(1, ticket.signSwitch)
    }

    @Test
    fun `waitSign is not mistaken for check out even without action`() {
        val ticket = parseSignCode(waitSign)!!
        assertEquals(1, ticket.type)
        assertEquals(SignAction.CHECK_IN_OR_OUT, ticket.action)
    }

    @Test
    fun `surrounding whitespace and newline are tolerated`() {
        assertEquals(566462L, parseSignCode("  $checkIn\n")!!.activityId)
    }

    @Test
    fun `unrelated text is rejected`() {
        assertNull(parseSignCode(""))
        assertNull(parseSignCode("   "))
        assertNull(parseSignCode("https://example.com/?a=1"))
        assertNull(parseSignCode("qutuo://sign?activityId=abc&userId=2&sp=3"))
        assertNull(parseSignCode("qutuo://sign?userId=2&sp=3"))
        assertNull(parseSignCode("qutuo://sign?activityId=1&sp=3"))
        assertNull(parseSignCode("qutuo://sign"))
    }

    @Test
    fun `extra parameters do not break parsing`() {
        val ticket = parseSignCode(
            "qutuo://sign?foo=1&activityId=9&userId=bob&bar=&sp=1700000000000&isSignIn=action",
        )!!
        assertEquals(9L, ticket.activityId)
        assertEquals("bob", ticket.userId)
    }
}
