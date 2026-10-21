package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ParamsCodecTest {

    @Test
    fun `unreserved characters are kept as is`() {
        assertEquals("Aa0-_.!~*'()", ParamsCodec.percentEncode("Aa0-_.!~*'()"))
    }

    @Test
    fun `reserved characters are percent encoded`() {
        assertEquals("%7B%22a%22%3A1%7D", ParamsCodec.percentEncode("""{"a":1}"""))
    }

    @Test
    fun `space becomes percent twenty not plus`() {
        assertEquals("%20", ParamsCodec.percentEncode(" "))
    }

    @Test
    fun `non ascii is encoded as utf8 bytes`() {
        assertEquals("%E4%B8%AD", ParamsCodec.percentEncode("中"))
    }

    @Test
    fun `params are encoded twice like the official h5`() {
        val params = buildJsonObject {
            put("schoolCode", JsonPrimitive("10361"))
            put("code", JsonPrimitive("2026306588"))
        }
        val once = ParamsCodec.percentEncode("""{"schoolCode":"10361","code":"2026306588"}""")
        assertEquals(ParamsCodec.percentEncode(once), ParamsCodec.encode(params))
    }

    @Test
    fun `empty object is encoded twice`() {
        assertEquals("%257B%257D", ParamsCodec.encode(JsonObject(emptyMap())))
    }
}
