package gitee.leolin_me.erkepp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 服务端存不了 4 字节 UTF-8（emoji）；这里锁住判断与清理。 */
class HomeworkTextTest {

    @Test
    fun `common emoji are detected as too wide`() {
        assertTrue(hasTooWideChar("test 🥰"))
        assertTrue(hasTooWideChar("😀"))
        assertTrue(hasTooWideChar("a🙂b"))
    }

    @Test
    fun `chinese and bmp symbols are fine`() {
        assertFalse(hasTooWideChar("大家都非常热情"))
        // ❤️ 是 U+2764 + U+FE0F，仍在 BMP，服务端能存
        assertFalse(hasTooWideChar("I ❤️ AUST"))
        assertFalse(hasTooWideChar("①②③ ★ ☆"))
        assertFalse(hasTooWideChar(""))
    }

    @Test
    fun `strip removes only the wide characters`() {
        assertEquals("test ", stripTooWideChars("test 🥰"))
        assertEquals("ab", stripTooWideChars("a🙂b"))
        assertEquals("I ❤️ AUST", stripTooWideChars("I ❤️ AUST"))
        assertEquals("好吧", stripTooWideChars("好吧🥰"))
    }
}
