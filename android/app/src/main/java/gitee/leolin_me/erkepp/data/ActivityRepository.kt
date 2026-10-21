package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import gitee.leolin_me.erkepp.AppLog
import java.util.Calendar

data class ActivityItem(
    val id: Long,
    val name: String,
    val cover: String?,
    val hours: Double,
    val classifyName: String,
    val classifySecondName: String?,
    val startTime: Long,
    val endTime: Long,
    val enrollCount: Int,
    val organizationName: String,
    val status: Int,
    val recommended: Boolean,
    val limitType: Int,
    val full: Boolean,
)

data class ActivityPage(
    val total: Int,
    val pageNum: Int,
    val items: List<ActivityItem>,
)

data class ActivityQuery(
    val page: Int = 1,
    val pageSize: Int = 20,
    val status: String = "5",
    val classifyId: Int = 0,
    val sortType: Int = 1,
    val search: String = "",
    /** 官方要求传“当天 0 点”的毫秒时间戳。 */
    val activityTime: Long = startOfToday(),
) {
    companion object {
        fun startOfToday(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

/** 活动列表。参数与官方活动页一致，共 11 个，缺项会返回 10002。 */
class ActivityRepository(private val api: ApiClient) {

    /** 活动分类：官方 `/dict/activity/choice-sort?moduleId=1` 的 filterArray。 */
    suspend fun categories(token: String): List<Pair<Int, String>> {
        val response = api.get(
            path = "dict/activity/choice-sort",
            params = buildJsonObject { put("moduleId", JsonPrimitive("1")) },
            token = token,
        )
        response.requireSuccess()
        val array = response.jsonObject["data"]?.jsonObject?.get("filterArray")?.jsonArray
            ?: return emptyList()
        return array.mapNotNull { element ->
            val item = element.jsonObject
            val id = item["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val name = item["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            id to name
        }
    }

    suspend fun list(token: String, query: ActivityQuery): ActivityPage {
        val response = api.get("page/activity/list", buildActivityParams(query), token)
        response.requireSuccess()
        return parseActivityPage(response, query.page)
    }

    /** 我的活动（含已报名）。官方用独立接口，与活动列表不是同一套筛选。 */
    suspend fun myList(token: String, page: Int, pageSize: Int = 20): ActivityPage {
        val response = api.get(
            path = "activity/my-list",
            params = buildJsonObject {
                put("pageNum", JsonPrimitive(page))
                put("pageSize", JsonPrimitive(pageSize))
                put("applyStatus", JsonPrimitive(""))
                put("status", JsonPrimitive("1,2,3,4,5,6,7,8,9,10,11,12"))
            },
            token = token,
        )
        response.requireSuccess()
        return parseActivityPage(response, page)
    }

    /**
     * 活动详情。
     *
     * 未参加的用户走 `activity/detail/non-member`，已参加的走 `activity/detail/participant`。
     * 先按「未参加」请求，报错或没有数据再按「已参加」请求；
     * 首次拿到真实数据时会把原始 JSON 写进日志，方便核对字段。
     */
    suspend fun detail(token: String, id: Long): ActivityDetail {
        val params = buildJsonObject { put("id", JsonPrimitive(id)) }
        var lastFailure: Throwable? = null
        for (path in DETAIL_PATHS) {
            val response = runCatching { api.get(path, params, token) }
                .onFailure { lastFailure = it }
                .getOrNull() ?: continue
            val source = path.substringAfterLast('/')
            val parsed = runCatching {
                response.requireSuccess()
                val data = response.jsonObject["data"]
                if (data == null || data is JsonNull) null else parseActivityDetail(data.jsonObject, source)
            }.onFailure { lastFailure = it }.getOrNull()
            if (parsed != null) {
                AppLog.d("detail", "$path ok source=$source introduceLength=${parsed.introduceHtml.length}")
                AppLog.d("detail", "raw=${response.jsonObject["data"]}")
                return parsed
            }
        }
        throw lastFailure ?: ApiException(-1, "活动详情不可用")
    }

    private companion object {
        val DETAIL_PATHS = listOf("activity/detail/non-member", "activity/detail/participant")
    }
}

/** 活动列表参数：与官方活动页一致，共 11 个，缺项会返回 10002。 */
internal fun buildActivityParams(query: ActivityQuery): JsonObject = buildJsonObject {
    put("search", JsonPrimitive(query.search))
    put("pageNum", JsonPrimitive(query.page))
    put("pageSize", JsonPrimitive(query.pageSize))
    put("sortType", JsonPrimitive(query.sortType))
    put("activityTime", JsonPrimitive(query.activityTime))
    put("classifyId", JsonPrimitive(query.classifyId))
    put("status", JsonPrimitive(query.status))
    put("level", JsonPrimitive(0))
    put("organizationName", JsonPrimitive(""))
    put("fieldType", JsonPrimitive("activity_iseErollment"))
    put("keyWord", JsonPrimitive("switch"))
}

internal fun parseActivityPage(response: kotlinx.serialization.json.JsonElement, pageNum: Int): ActivityPage {
    val dataElement = response.jsonObject["data"]
    if (dataElement == null || dataElement is kotlinx.serialization.json.JsonNull) {
        return ActivityPage(0, pageNum, emptyList())
    }
    val data = dataElement.jsonObject
    val total = data["total"]?.jsonPrimitive?.intOrNull ?: 0
    val list = data["list"]?.jsonArray ?: return ActivityPage(total, pageNum, emptyList())
    val items = list.mapNotNull { element ->
        val item = element.jsonObject
        val id = item["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
        ActivityItem(
            id = id,
            name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            cover = item["logo"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            hours = item["hours"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            classifyName = item["classifyName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            classifySecondName = item["classifySecondName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            startTime = item["startTime"]?.jsonPrimitive?.longOrNull ?: 0L,
            endTime = item["endTime"]?.jsonPrimitive?.longOrNull ?: 0L,
            enrollCount = item["enrollCount"]?.jsonPrimitive?.intOrNull ?: 0,
            organizationName = item["organizationName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            status = item["activityStatus"]?.jsonPrimitive?.intOrNull ?: 0,
            recommended = (item["isRecommend"]?.jsonPrimitive?.intOrNull ?: 0) == 1,
            limitType = item["limitType"]?.jsonPrimitive?.intOrNull ?: 0,
            full = (item["isfull"]?.jsonPrimitive?.intOrNull ?: 0) != 0,
        )
    }
    return ActivityPage(total = total, pageNum = pageNum, items = items)
}

