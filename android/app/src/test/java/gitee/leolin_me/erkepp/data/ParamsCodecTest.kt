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

    /**
     * 只编码一次：第二次由 OkHttp 的 query / 表单编码完成，
     * 最终在线路上是 `params=%257B...`（与官方 H5 一致）。
     */
    @Test
    fun `params are encoded exactly once`() {
        val params = buildJsonObject {
            put("schoolCode", JsonPrimitive("10361"))
            put("code", JsonPrimitive("2026306588"))
        }
        assertEquals(
            "%7B%22schoolCode%22%3A%2210361%22%2C%22code%22%3A%222026306588%22%7D",
            ParamsCodec.encode(params),
        )
    }

    @Test
    fun `empty object is encoded once`() {
        assertEquals("%7B%7D", ParamsCodec.encode(JsonObject(emptyMap())))
    }
}
