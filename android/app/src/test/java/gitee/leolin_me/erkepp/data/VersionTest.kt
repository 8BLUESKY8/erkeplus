package gitee.leolin_me.erkepp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun `parses common tag forms`() {
        assertEquals(Version(0, 2, 0), Version.parse("v0.2.0"))
        assertEquals(Version(0, 2, 0), Version.parse("0.2.0"))
        assertEquals(Version(1, 0, 0), Version.parse("v1.0"))
        assertEquals(Version(2, 3, 4), Version.parse("2.3.4-debug"))
        assertEquals(Version(2, 3, 4), Version.parse(" 2.3.4 "))
    }

    @Test
    fun `rejects non version text`() {
        assertNull(Version.parse("release-2026"))
        assertNull(Version.parse(""))
        assertNull(Version.parse("v"))
    }

    @Test
    fun `compares by major then minor then patch`() {
        assertTrue(Version.parse("0.3.0")!! > Version.parse("0.2.9")!!)
        assertTrue(Version.parse("1.0.0")!! > Version.parse("0.9.9")!!)
        assertTrue(Version.parse("0.2.1")!! > Version.parse("0.2.0")!!)
        assertEquals(0, Version.parse("v0.2.0")!!.compareTo(Version.parse("0.2.0")!!))
    }

    @Test
    fun `debug suffix does not make it newer`() {
        val installed = Version.parse("0.2.0-debug")!!
        val remote = Version.parse("v0.2.0")!!
        assertEquals(0, remote.compareTo(installed))
    }
}
