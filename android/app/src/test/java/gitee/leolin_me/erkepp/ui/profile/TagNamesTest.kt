package gitee.leolin_me.erkepp.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/** 标签 id ↔ 名称的映射，字典里没有的 id 直接跳过。 */
class TagNamesTest {

    private val dict = listOf(5 to "阅读", 8 to "学习", 9 to "音乐")

    @Test
    fun `keeps dictionary order of ids`() {
        assertEquals(listOf("阅读", "音乐"), tagNames(listOf(5, 9), dict))
    }

    @Test
    fun `skips unknown ids`() {
        assertEquals(listOf("学习"), tagNames(listOf(8, 404), dict))
    }

    @Test
    fun `empty ids gives empty list`() {
        assertEquals(emptyList<String>(), tagNames(emptyList(), dict))
    }
}
