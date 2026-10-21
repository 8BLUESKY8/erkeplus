package gitee.leolin_me.erkepp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {

    @Test
    fun `official host is allowed`() {
        assertTrue(UrlPolicy.isAllowedHost("win.9xueqi.com"))
    }

    @Test
    fun `subdomain of official host is allowed`() {
        assertTrue(UrlPolicy.isAllowedHost("static.win.9xueqi.com"))
    }

    @Test
    fun `lookalike host is rejected`() {
        assertFalse(UrlPolicy.isAllowedHost("win.9xueqi.com.evil.example"))
        assertFalse(UrlPolicy.isAllowedHost("evilwin.9xueqi.com.example.org"))
    }

    @Test
    fun `other host is rejected`() {
        assertFalse(UrlPolicy.isAllowedHost("example.com"))
        assertFalse(UrlPolicy.isAllowedHost("9xueqi.com"))
    }

    @Test
    fun `blank host is rejected`() {
        assertFalse(UrlPolicy.isAllowedHost(null))
        assertFalse(UrlPolicy.isAllowedHost(""))
        assertFalse(UrlPolicy.isAllowedHost("   "))
    }
}
