package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 参数编码，与官方 H5 的请求拦截器保持一致。
 *
 * 官方是「encodeURI 一次 + axios/qs 拼接时再编码一次」，共两次。
 * 这里只做第一次：传输层（[okhttp3.HttpUrl] 的 query、[okhttp3.FormBody]）会自动完成第二次，
 * 因此这里返回的是**一次编码**后的字符串，直接交给传输层即可。
 */
object ParamsCodec {

    private val json = Json { encodeDefaults = true }

    private const val HEX = "0123456789ABCDEF"

    fun encode(params: JsonObject): String =
        percentEncode(json.encodeToString(JsonObject.serializer(), params))

    /** 等价于 JavaScript 的 encodeURIComponent。 */
    fun percentEncode(value: String): String {
        val out = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-_.!~*'()") {
                out.append(char)
            } else {
                out.append('%').append(HEX[code shr 4]).append(HEX[code and 0x0F])
            }
        }
        return out.toString()
    }
}
