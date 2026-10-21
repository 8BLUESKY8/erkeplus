package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 学校档案。官方把每所学校的校徽上传到 OSS，并把地址写进学校记录，
 * 因此客户端只需按模板取用，不需要自备图片。
 */
class SchoolRepository(private val api: ApiClient) {

    suspend fun all(): List<School> {
        val response = api.get("common/all-school", JsonObject(emptyMap()))
        response.requireSuccess()
        val array = response.jsonObject["data"]?.jsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val item = element.jsonObject
            val id = item["id"]?.jsonPrimitive?.intOrNull?.toString() ?: return@mapNotNull null
            val name = item["name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            School(
                code = id,
                name = name,
                logoUrl = item["logo"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            )
        }.sortedBy { it.name }
    }
}
