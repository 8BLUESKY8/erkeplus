package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 人物主页解析。样本来自 2026-10-25 真机 `message/chat/follow/user/info` 的原始响应
 * （用户 1908337，只把头像 URL 缩短了）。
 */
class UserProfileParseTest {

    private val sample = """
        {"politics":0,"hours":273.5,"politicsName":"","gender":1,"organizationName":"",
         "avatar":"https://x/a.jpg","dynamicCount":0,"organizationCount":3,"activityCount":32,
         "collegeName":"化工与爆破学院","isFollow":0,"identity":1,"schoolId":10361,
         "name":"程子涛","id":1908337,"majorName":"弹药工程与爆炸技术",
         "personTag":[{"name":"电竞·游戏","id":2},{"name":"电影","id":3}]}
    """.trimIndent()

    @Test
    fun `profile keeps the fields the page shows`() {
        val profile = parsePersonProfile(Json.parseToJsonElement(sample))
        assertEquals(1908337L, profile.id)
        assertEquals("程子涛", profile.name)
        assertEquals(1, profile.gender)
        assertEquals(1, profile.identity)
        assertEquals("化工与爆破学院", profile.collegeName)
        assertEquals("弹药工程与爆炸技术", profile.majorName)
        assertEquals(32, profile.activityCount)
        assertEquals(3, profile.organizationCount)
        assertFalse(profile.isFollow)
        assertEquals(listOf("电竞·游戏", "电影"), profile.tags)
    }

    @Test
    fun `student subtitle joins college and major`() {
        val profile = parsePersonProfile(Json.parseToJsonElement(sample))
        assertEquals("化工与爆破学院 · 弹药工程与爆炸技术", profile.subtitle)
    }

    @Test
    fun `organization account uses organization name instead of major`() {
        val org = parsePersonProfile(
            Json.parseToJsonElement(
                """{"id":5,"name":"机器人协会","identity":2,"collegeName":"安徽理工大学",
                   "organizationName":"机器人协会","majorName":"不该出现"}""",
            ),
        )
        assertEquals("安徽理工大学 · 机器人协会", org.subtitle)
    }

    @Test
    fun `missing optional fields do not crash`() {
        val bare = parsePersonProfile(Json.parseToJsonElement("""{"id":7,"name":"某人"}"""))
        assertEquals(7L, bare.id)
        assertEquals("", bare.subtitle)
        assertEquals(0, bare.activityCount)
        assertTrue(bare.tags.isEmpty())
        assertFalse(bare.isFollow)
        assertNull(bare.avatar)
    }

    @Test
    fun `follow flag maps to boolean`() {
        val followed = parsePersonProfile(
            Json.parseToJsonElement("""{"id":1,"name":"x","isFollow":1}"""),
        )
        assertTrue(followed.isFollow)
    }
}
