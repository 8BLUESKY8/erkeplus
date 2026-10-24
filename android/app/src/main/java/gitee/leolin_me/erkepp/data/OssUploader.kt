package gitee.leolin_me.erkepp.data

import android.util.Base64
import gitee.leolin_me.erkepp.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 阿里云 STS 临时凭证。官方 `common/aliyun/sts/assume-role`。 */
data class StsToken(
    val accessKeyId: String,
    val accessKeySecret: String,
    val securityToken: String,
)

/**
 * 往官方 OSS 传作业附件。
 *
 * 官方 H5 用 ali-oss：先取 STS 临时凭证，再直接 PUT 到
 * `gytx-2ketang`（图片）或 `gytx-temp`（其它文件），对象路径是
 * `activity/<毫秒时间戳>/<文件名>`。这里不引 SDK，自己按 OSS V1 签名实现——
 * ali-oss 默认就是 V1，同一套凭证、同一个桶，服务端认。
 */
class OssUploader(private val api: ApiClient) {

    private val client = OkHttpClient.Builder().build()

    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

    suspend fun sts(token: String): StsToken {
        val response = api.get("common/aliyun/sts/assume-role", buildJsonObject { }, token)
        response.requireSuccess()
        val data = response.jsonObject["data"] as? JsonObject ?: error("没拿到 STS 凭证")
        return StsToken(
            accessKeyId = (data["accessKeyId"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            accessKeySecret = (data["accessKeySecret"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            securityToken = (data["accessSecurityToken"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
        )
    }

    /**
     * 上传一份文件，返回可公开访问的地址。
     *
     * 图片走 `gytx-2ketang`（H5 也是这个桶），其它文件走 `gytx-temp`。
     */
    suspend fun upload(
        token: String,
        bytes: ByteArray,
        fileName: String,
        mime: String,
    ): String = withContext(Dispatchers.IO) {
        val credentials = sts(token)
        val bucket = if (mime.startsWith("image/")) IMAGE_BUCKET else TEMP_BUCKET
        val key = "activity/${System.currentTimeMillis()}/${safeName(fileName)}"
        val host = "https://$bucket.$ENDPOINT/$key"
        val date = dateFormat.format(Date())
        val contentType = mime.ifBlank { "application/octet-stream" }

        // OSS V1 签名：PUT\nContent-MD5\nContent-Type\nDate\nCanonicalizedOSSHeaders + CanonicalizedResource
        val canonicalHeaders = "x-oss-security-token:${credentials.securityToken}\n"
        val stringToSign = "PUT\n\n$contentType\n$date\n$canonicalHeaders/$bucket/$key"
        val signature = hmacSha1(credentials.accessKeySecret, stringToSign)
        val authorization = "OSS ${credentials.accessKeyId}:$signature"

        val request = Request.Builder()
            .url(host)
            .header("Date", date)
            .header("Authorization", authorization)
            .header("x-oss-security-token", credentials.securityToken)
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .build()

        val result = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("上传失败（HTTP ${response.code}）")
                }
            }
        }
        result.getOrThrow()
        AppLog.d("oss", "upload ok bucket=$bucket key=$key size=${bytes.size}")
        host
    }

    /** 删除刚传上去的对象，调试与「取消上传」用。 */
    suspend fun delete(token: String, url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val credentials = sts(token)
            val uri = url.substringAfter("://")
            val bucket = uri.substringBefore('.')
            val hostPart = uri.substringBefore('/')
            val key = uri.substringAfter('/')
            val date = dateFormat.format(Date())
            val canonicalHeaders = "x-oss-security-token:${credentials.securityToken}\n"
            val stringToSign = "DELETE\n\n\n$date\n$canonicalHeaders/$bucket/$key"
            val signature = hmacSha1(credentials.accessKeySecret, stringToSign)
            val request = Request.Builder()
                .url("https://$hostPart/$key")
                .header("Date", date)
                .header("Authorization", "OSS ${credentials.accessKeyId}:$signature")
                .header("x-oss-security-token", credentials.securityToken)
                .delete()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun hmacSha1(secret: String, text: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA1"))
        return Base64.encodeToString(mac.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").take(80).ifBlank { "file" }

    companion object {
        private const val ENDPOINT = "oss-cn-hangzhou.aliyuncs.com"
        private const val IMAGE_BUCKET = "gytx-2ketang"
        private const val TEMP_BUCKET = "gytx-temp"
    }
}

/** 作业附件的类型编号：1 图片、3 Word、4 Excel、5 PDF、6 其它。 */
fun attachmentTypeOf(mime: String): Int = when {
    mime.startsWith("image/") -> 1
    mime == "application/msword" ||
        mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> 3

    mime == "application/vnd.ms-excel" ||
        mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> 4

    mime == "application/pdf" -> 5
    else -> 6
}
