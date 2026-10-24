package gitee.leolin_me.erkepp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 数据管理页的纯逻辑：体积文案、默认勾选、项的唯一性。 */
class StorageFormatTest {

    @Test
    fun `sizes use 1024 steps and one decimal`() {
        assertEquals("0 B", formatStorageSize(0))
        assertEquals("0 B", formatStorageSize(-1))
        assertEquals("118 B", formatStorageSize(118))
        assertEquals("1.0 KB", formatStorageSize(1024))
        assertEquals("1.5 KB", formatStorageSize(1536))
        assertEquals("1.0 MB", formatStorageSize(1024L * 1024))
        assertEquals("24.0 MB", formatStorageSize(24L * 1024 * 1024))
        assertEquals("1.5 GB", formatStorageSize(1536L * 1024 * 1024))
    }

    @Test
    fun `risky items are never checked on the first visit`() {
        assertTrue("默认总该勾点东西", StorageItem.defaultSelection.isNotEmpty())
        StorageItem.entries.filter { it.risky }.forEach {
            assertFalse("${it.title} 会丢数据，不该默认勾选", it.key in StorageItem.defaultSelection)
        }
        StorageItem.entries.filterNot { it.risky }.forEach {
            assertTrue("${it.title} 清了会自动重来，应该默认勾选", it.key in StorageItem.defaultSelection)
        }
    }

    @Test
    fun `every item key is unique and resolvable`() {
        val keys = StorageItem.entries.map { it.key }
        assertEquals("key 不能重复", keys.size, keys.toSet().size)
        keys.forEach { assertEquals(it, StorageItem.of(it)?.key) }
        assertNull(StorageItem.of("nope"))
    }

    @Test
    fun `report sums only the requested items`() {
        val report = StorageReport(
            appBytes = 1000,
            entries = listOf(
                StorageEntry(StorageItem.AVATAR, 600, 3),
                StorageEntry(StorageItem.COVER, 300, 2),
                StorageEntry(StorageItem.TRASH, 100, 1),
            ),
        )
        assertEquals(900, report.bytesOf(listOf(StorageItem.AVATAR, StorageItem.COVER)))
        assertEquals(0, report.bytesOf(emptyList()))
        assertEquals(600L, report.entryOf(StorageItem.AVATAR)?.bytes ?: 0L)
    }
}
