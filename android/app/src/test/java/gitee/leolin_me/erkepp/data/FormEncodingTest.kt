package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.FormBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 回归测试：曾经因为“自己编码两次 + OkHttp 再编码一次”导致服务端报
 * 「参数格式不正确，参数必须是 JSON 格式」。这里锁定最终落到线路上的层数。
 */
class FormEncodingTest {

    @Test
    fun `form body carries params double encoded on the wire`() {
        val params = buildJsonObject {
            put("schoolCode", JsonPrimitive("10361"))
            put("code", JsonPrimitive("2026306588"))
        }
        val body = FormBody.Builder().add("params", ParamsCodec.encode(params)).build()
        val buffer = Buffer()
        body.writeTo(buffer)

        assertEquals(
            "params=%257B%2522schoolCode%2522%253A%252210361%2522%252C%2522code%2522%253A%25222026306588%2522%257D",
            buffer.readUtf8(),
        )
    }
}
