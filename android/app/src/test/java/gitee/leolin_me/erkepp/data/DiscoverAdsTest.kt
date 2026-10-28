package gitee.leolin_me.erkepp.data

import org.junit.Assert.*
import org.junit.Test

class DiscoverAdsTest {
    @Test fun separatesSlotsAndSkipsInvalidEntries() {
        val result = parseDiscoverAds("""{
          "showPlaceholders": false,
          "top": [
            {"id":"a","title":"推广","imageUrl":"https://example.com/a.jpg","targetUrl":"https://example.com/page"},
            {"id":"a","imageUrl":"https://example.com/a.jpg","targetUrl":"https://example.com/duplicate"},
            {"id":"b","imageUrl":"http://example.com/a.jpg","targetUrl":"https://example.com/page"},
            {"id":"c","imageUrl":"https://example.com/a.jpg","targetUrl":"javascript:alert(1)"},
            {"id":"d","enabled":false,"imageUrl":"https://example.com/a.jpg","targetUrl":"https://example.com/page"},
            null
          ],
          "bottom": [{"id":"footer","imageUrl":"https://example.com/b.jpg","targetUrl":"https://example.com/footer"}]
        }""")
        assertEquals(listOf("a"), result.top.map { it.id })
        assertEquals(listOf("footer"), result.bottom.map { it.id })
        assertFalse(result.showPlaceholders)
    }

    @Test fun brokenConfigurationFallsBackToReservedSlots() {
        assertEquals(DiscoverAds(), parseDiscoverAds("not json"))
        assertEquals(DiscoverAds(), parseDiscoverAds("{}"))
    }
}
