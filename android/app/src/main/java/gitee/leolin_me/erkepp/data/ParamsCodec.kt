package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 参数编码，与官方 H5 的请求拦截器保持一致：
 * 先对 JSON 做一次 [encodeURIComponent]（axios 拼 query 时再做一次），因此这里是两次。
 */
object ParamsCodec {

    private val json = Json { encodeDefaults = true }

    private const val HEX = "0123456789ABCDEF"

    fun encode(params: JsonObject): String = percentEncode(percentEncode(json.encodeToString(JsonElement.serializer(), params)))

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
