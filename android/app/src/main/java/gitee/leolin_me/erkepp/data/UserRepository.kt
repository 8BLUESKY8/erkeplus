package gitee.leolin_me.erkepp.data

import gitee.leolin_me.erkepp.AppLog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 别人的主页，对应官方 `#/userShows/<id>`。
 *
 * 数据只从 `message/chat/follow/user/info` 取——官方那一页也只调这一个接口。
 * 它给什么就显示什么：官方页面没摆出来的字段（比如 `hours` 学时）默认也不摆。
 *
 * 名字不叫 `UserProfile`：`data` 包里已经有同名类（登录后自己的资料），两者字段完全不同。
 */
data class PersonProfile(
    val id: Long,
    val name: String,
    val avatar: String?,
    /** 1 男、2 女，其它表示没填。 */
    val gender: Int,
    /** 1 学生、2 组织账号。 */
    val identity: Int,
    val collegeName: String,
    val majorName: String,
    val organizationName: String,
    val activityCount: Int,
    val organizationCount: Int,
    val dynamicCount: Int,
    /**
     * 学时。接口给了，官方页面没摆，所以界面上也不显示。
     * 先解析出来，是为了以后要展示时不用再改数据层（也避免以后误以为接口没给）。
     */
    val hours: Double,
    val isFollow: Boolean,
    val tags: List<String>,
) {
    /** 身份 1 显示「学院 · 专业」，身份 2 显示「学院 · 组织名」。 */
    val subtitle: String
        get() = listOf(collegeName, if (identity == 2) organizationName else majorName)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
}

internal fun parsePersonProfile(data: JsonElement): PersonProfile {
    val item = data as? JsonObject ?: throw ApiException(-1, "没拿到用户信息")
    val tagArray = item["personTag"] as? JsonArray
    val tags = buildList {
        tagArray?.forEach { element ->
            val tag = (element as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
            if (!tag.isNullOrBlank()) add(tag)
        }
    }
    return PersonProfile(
        id = item["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
        name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        avatar = item["avatar"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
        gender = item["gender"]?.jsonPrimitive?.intOrNull ?: 0,
        identity = item["identity"]?.jsonPrimitive?.intOrNull ?: 0,
        collegeName = item["collegeName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        majorName = item["majorName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        organizationName = item["organizationName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        activityCount = item["activityCount"]?.jsonPrimitive?.intOrNull ?: 0,
        organizationCount = item["organizationCount"]?.jsonPrimitive?.intOrNull ?: 0,
        dynamicCount = item["dynamicCount"]?.jsonPrimitive?.intOrNull ?: 0,
        hours = item["hours"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
        isFollow = (item["isFollow"]?.jsonPrimitive?.intOrNull ?: 0) == 1,
        tags = tags,
    )
}

class UserRepository(private val api: ApiClient) {

    suspend fun info(token: String, userId: Long): PersonProfile {
        val response = api.get(
            path = "message/chat/follow/user/info",
            params = buildJsonObject { put("id", JsonPrimitive(userId)) },
            token = token,
        )
        response.requireSuccess()
        val data = response.jsonObject["data"] ?: throw ApiException(-1, "没拿到用户信息")
        val profile = parsePersonProfile(data)
        AppLog.d("user", "info id=$userId name=${profile.name} follow=${profile.isFollow}")
        return profile
    }

    /**
     * 别人参加过的活动，官方 `#/myAcitive/:id`。
     *
     * 返回项和活动列表同族（`logo/name/hours/startTime/endTime/status`），直接复用 [parseActivityPage]。
     */
    suspend fun activities(
        token: String,
        userId: Long,
        page: Int,
        pageSize: Int = ACTIVITY_PAGE_SIZE,
    ): ActivityPage {
        val response = api.get(
            path = "activity/other-list",
            params = buildJsonObject {
                put("pageNum", JsonPrimitive(page))
                put("pageSize", JsonPrimitive(pageSize))
                put("userId", JsonPrimitive(userId))
            },
            token = token,
        )
        response.requireSuccess()
        val parsed = parseActivityPage(response, page)
        AppLog.d("user", "acts id=$userId page=$page got=${parsed.items.size} total=${parsed.total}")
        return parsed
    }

    /**
     * 关注 / 取消关注。写操作，只由用户点按钮触发。
     *
     * 官方接口分两个：`follow/add` 与 `follow/delete`，参数都只有 `id`。
     */
    suspend fun setFollow(token: String, userId: Long, follow: Boolean) {
        val path = if (follow) "message/chat/follow/add" else "message/chat/follow/delete"
        val response = api.post(
            path = path,
            params = buildJsonObject { put("id", JsonPrimitive(userId)) },
            token = token,
        )
        response.requireSuccess()
        AppLog.d("user", "follow id=$userId set=$follow ok")
    }

    companion object {
        const val ACTIVITY_PAGE_SIZE = 10
    }
}
