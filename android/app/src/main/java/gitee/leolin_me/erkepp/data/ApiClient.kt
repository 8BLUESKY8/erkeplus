package gitee.leolin_me.erkepp.data

import gitee.leolin_me.erkepp.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 官方接口的最小封装。
 *
 * 与站内 H5 的请求拦截器保持一致：公共参数放 query，业务参数放 `params`，
 * GET 与 POST 都携带 `Authorization`（值为裸 JWT）。
 */
class ApiClient(
    private val deviceId: String,
    private val baseUrl: String = AppConfig.API_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { encodeDefaults = true }

    suspend fun get(path: String, params: JsonObject, token: String? = null): JsonElement {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(path.trimStart('/'))
            .addQueryParameter("params", ParamsCodec.encode(params))
            .apply { commonParams(token) }
            .build()
        return execute(Request.Builder().url(url).apply { authorization(token) }.get().build())
    }

    suspend fun post(path: String, params: JsonObject, token: String? = null): JsonElement {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(path.trimStart('/'))
            .apply { commonParams(token) }
            .build()
        val body = FormBody.Builder().add("params", ParamsCodec.encode(params)).build()
        return execute(Request.Builder().url(url).apply { authorization(token) }.post(body).build())
    }

    private fun HttpUrl.Builder.commonParams(token: String?) {
        addQueryParameter("token", token.orEmpty())
        addQueryParameter("platform", AppConfig.PLATFORM)
        addQueryParameter("version", AppConfig.APP_VERSION)
        addQueryParameter("device", deviceId)
        addQueryParameter("timestamp", System.currentTimeMillis().toString())
    }

    private fun Request.Builder.authorization(token: String?): Request.Builder =
        apply { if (!token.isNullOrEmpty()) header("Authorization", token) }

    private suspend fun execute(request: Request): JsonElement = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, "服务暂不可用（HTTP ${response.code}）")
            runCatching { json.parseToJsonElement(text) }
                .getOrElse { throw ApiException(-1, "响应格式异常") }
        }
    }
}
