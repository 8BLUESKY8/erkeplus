package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 选版逻辑：必须在列表里挑**版本号最大**的，而不是第一个。
 *
 * Gitee 的发行版列表按创建时间正序返回，第一个通常是最老的版本；
 * 0.8.8 就是因为取了第一个，导致「检查更新」永远显示已是最新。
 */
class UpdateCheckerTest {

    private val checker = UpdateChecker()

    private fun array(text: String) = Json.parseToJsonElement(text).jsonArray

    @Test
    fun `picks the highest version when list is oldest first`() {
        val releases = array(
            """
            [
              {"tag_name":"v0.8.1","assets":[{"browser_download_url":"https://x/erkepp-0.8.1.apk"}]},
              {"tag_name":"v0.8.8","assets":[{"browser_download_url":"https://x/erkepp-0.8.8.apk"}]}
            ]
            """.trimIndent(),
        )
        val latest = checker.pickLatestRelease(releases)!!
        assertEquals(Version(0, 8, 8), latest.version)
        assertEquals("https://x/erkepp-0.8.8.apk", latest.apkUrl)
        assertEquals("https://gitee.com/LeoLin_ME/erkeplus/releases/tag/v0.8.8", latest.url)
    }

    @Test
    fun `skips tags that are not versions`() {
        val releases = array(
            """
            [
              {"tag_name":"release-2026"},
              {"tag_name":"v0.2.0"},
              {"tag_name":"v0.10.0"}
            ]
            """.trimIndent(),
        )
        assertEquals(Version(0, 10, 0), checker.pickLatestRelease(releases)!!.version)
    }

    @Test
    fun `empty list gives null`() {
        assertNull(checker.pickLatestRelease(array("[]")))
    }

    @Test
    fun `tags fallback also picks highest`() {
        val tags = array("""[{"name":"v0.1.0"},{"name":"v0.9.2"}]""")
        assertEquals(Version(0, 9, 2), checker.pickLatestTag(tags)!!.version)
        assertEquals(null, checker.pickLatestTag(tags)!!.apkUrl)
    }
}
