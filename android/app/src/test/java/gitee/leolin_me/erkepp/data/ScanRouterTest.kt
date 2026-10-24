package gitee.leolin_me.erkepp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 首页「扫一扫」的分流：活动分享码要能取出 activityid，签到码交给 parseSignCode。 */
class ScanRouterTest {

    @Test
    fun `share link yields activity id`() {
        val link = "https://win.9xueqi.com/?sourceName=schActivityCode@Xj&activityid=574449"
        assertEquals(574449L, ScanRouter.activityIdFromShareLink(link))
    }

    @Test
    fun `parameter name is case insensitive`() {
        val link = "https://win.9xueqi.com/?sourceName=schActivityCode@Xj&activityId=42"
        assertEquals(42L, ScanRouter.activityIdFromShareLink(link))
    }

    @Test
    fun `sign code is not treated as share link`() {
        val sign = "qutuo://sign?activityId=566462&userId=1912797&sp=1790672679000&isSignIn=action"
        // 这是签到码，交给 parseSignCode；这里顺便说明它不会被当成分享码
        assertNull(ScanRouter.activityIdFromShareLink(sign))
        assertEquals(566462L, parseSignCode(sign)!!.activityId)
    }

    @Test
    fun `organization code is recognized`() {
        assertTrue(ScanRouter.isOrganizationCode("qutuo://joinOrganization?id=17"))
        assertFalse(ScanRouter.isOrganizationCode("https://win.9xueqi.com/?activityid=1"))
    }

    @Test
    fun `organization code yields organization id`() {
        // 样本来自用户真机扫到的组织码，官方会在后面补一段 ?yiban=
        val scanned = "qutuo://joinOrganization?id=39261?yiban=yiban_scan_result"
        assertEquals(39261L, ScanRouter.organizationId(scanned))
        assertEquals(39261L, ScanRouter.organizationId("qutuo://joinOrganization?id=39261"))
    }

    @Test
    fun `organization id ignores other codes`() {
        assertNull(
            ScanRouter.organizationId(
                "qutuo://sign?activityId=566462&userId=1912797&sp=1790672679000&isSignIn=action",
            ),
        )
        assertNull(ScanRouter.organizationId("qutuo://joinOrganization?id=abc"))
        assertNull(ScanRouter.organizationId("qutuo://joinOrganization"))
        assertNull(ScanRouter.organizationId(""))
    }

    @Test
    fun `unrelated text yields nothing`() {
        assertNull(ScanRouter.activityIdFromShareLink("https://example.com/not-a-sign-code"))
        assertNull(ScanRouter.activityIdFromShareLink("https://win.9xueqi.com/?sourceName=x"))
        assertNull(ScanRouter.activityIdFromShareLink(""))
    }
}
