package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 学校档案。官方把每所学校的校徽上传到 OSS，并把地址写进学校记录，
 * 因此客户端只需按模板取用，不需要自备图片。
 *
 * 列表顺序由服务端决定（官方页面也是原样渲染），客户端不做二次排序；
 * 搜索同样交给服务端，参数为 `name`。
 */
class SchoolRepository(private val api: ApiClient) {

    suspend fun list(name: String? = null): List<School> {
        val params = if (name.isNullOrBlank()) {
            JsonObject(emptyMap())
        } else {
            buildJsonObject { put("name", JsonPrimitive(name.trim())) }
        }
        val response = api.get("common/all-school", params)
        response.requireSuccess()
        val array = response.jsonObject["data"]?.jsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val item = element.jsonObject
            val id = item["id"]?.jsonPrimitive?.intOrNull?.toString() ?: return@mapNotNull null
            val schoolName = item["name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            School(
                code = id,
                name = schoolName,
                logoUrl = item["logo"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            )
        }
    }
}
