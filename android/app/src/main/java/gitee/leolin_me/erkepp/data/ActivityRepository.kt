package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonElement
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
import gitee.leolin_me.erkepp.BuildConfig
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
    /** 报名开始时间；0 表示未知。列表接口通常会返回，是判断「待报名」的依据。 */
    val enrollStartTime: Long = 0L,
    /** 报名截止时间；列表接口不一定返回，0 表示未知。 */
    val enrollEndTime: Long = 0L,
    val enrollCount: Int,
    /** 人数上限；0 表示不限。 */
    val peopleLimit: Int,
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

    /** 签到方式字典的进程内缓存：字典是学校级配置，一次会话内不会变。 */
    private var signWayCache: Map<Int, String>? = null

    /**
     * 签到方式字典：官方 `/dict/school/system-para/list {fieldType:"activity", keyWord:"sign_way"}`。
     *
     * 活动详情里的 `signWay` 是这组字典里 id 的逗号串。本校实测为
     * `1 动态二维码 / 2 静态二维码 / 3 GPS定位`；字典里的 id 是学校配置的，不能写死。
     */
    suspend fun signWays(token: String): Map<Int, String> {
        signWayCache?.let { return it }
        val response = api.get(
            path = "dict/school/system-para/list",
            params = buildJsonObject {
                put("fieldType", JsonPrimitive("activity"))
                put("keyWord", JsonPrimitive("sign_way"))
            },
            token = token,
        )
        response.requireSuccess()
        val array = runCatching { response.jsonObject["data"]?.jsonArray }.getOrNull()
            ?: return emptyMap()
        val map = array.mapNotNull { element ->
            val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = item["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val name = item["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            id to name
        }.toMap()
        if (map.isNotEmpty()) signWayCache = map
        return map
    }


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
     * 官方 H5 先调 `activity/member/identity` 判断身份，再决定进哪个详情页：
     * identity 1 是已参加、2/3 是管理员、其它是未参加。这里照同样的顺序取数，
     * 拿到原始响应会写进调试日志，方便核对字段。
     */
    suspend fun detail(token: String, id: Long): ActivityDetail {
        val params = buildJsonObject { put("id", JsonPrimitive(id)) }
        val identity = runCatching { identity(token, id) }.getOrDefault(0)
        AppLog.d("detail", "identity=$identity activityId=$id")

        val paths = when (identity) {
            1 -> listOf(PARTICIPANT, NON_MEMBER)
            2, 3 -> listOf(MANAGER, NON_MEMBER)
            else -> listOf(NON_MEMBER, PARTICIPANT)
        }

        var lastFailure: Throwable? = null
        for (path in paths) {
            val response = runCatching { api.get(path, params, token) }
                .onFailure { lastFailure = it }
                .getOrNull() ?: continue
            val source = path.substringAfterLast('/')
            val parsed = runCatching {
                response.requireSuccess()
                val data = response.jsonObject["data"]
                if (data == null || data is JsonNull) null
                else parseActivityDetail(data.jsonObject, source).copy(identity = identity)
            }.onFailure { lastFailure = it }.getOrNull()
            if (parsed != null) {
                AppLog.d("detail", "$path ok source=$source identity=$identity")
                AppLog.d("detail", "raw=${response.jsonObject["data"]}")
                return parsed
            }
        }
        throw lastFailure ?: ApiException(-1, "活动详情不可用")
    }

    /** 当前用户与这条活动的关系：1 已参加、2/3 管理员、其它未参加。 */
    suspend fun identity(token: String, id: Long): Int {
        val response = api.get(
            path = "activity/member/identity",
            params = buildJsonObject { put("activityId", JsonPrimitive(id)) },
            token = token,
        )
        response.requireSuccess()
        return response.jsonObject["data"]?.jsonObject?.get("identity")?.jsonPrimitive?.intOrNull ?: 0
    }

    /**
     * 活动公告。
     *
     * 官方两套接口：已参加用 `activity/noticeListNew`，未参加用 `activity/noticeList`，
     * 参数一致（`pageNum`、`pageSize`、`activityId`），返回 `data.list`。
     */
    suspend fun notices(token: String, id: Long, identity: Int): List<ActivityNotice> {
        val params = buildJsonObject {
            put("pageNum", JsonPrimitive(1))
            put("pageSize", JsonPrimitive(50))
            put("activityId", JsonPrimitive(id))
        }
        val paths = if (identity == 1 || identity == 2 || identity == 3) {
            listOf("activity/noticeListNew", "activity/noticeList")
        } else {
            listOf("activity/noticeList", "activity/noticeListNew")
        }
        for (path in paths) {
            val response = runCatching { api.get(path, params, token) }.getOrNull() ?: continue
            val parsed = runCatching {
                response.requireSuccess()
                parseNotices(response.jsonObject["data"])
            }.getOrNull() ?: continue
            AppLog.d("notice", "$path count=${parsed.size}")
            AppLog.d("notice", "raw=${response.jsonObject["data"]}")
            // 有的活动 noticeListNew 只给 total 不给 list，空结果要继续试下一个接口
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    private companion object {
        const val NON_MEMBER = "activity/detail/non-member"
        const val PARTICIPANT = "activity/detail/participant"
        const val MANAGER = "activity/detail/manger"
    }
}

/** 公告列表解析：`data.list`，字段在不同接口里略有差异，做兼容取值。 */
internal fun parseNotices(data: JsonElement?): List<ActivityNotice> {
    if (data == null || data is JsonNull) return emptyList()
    val list = data.jsonObject["list"]?.let { runCatching { it.jsonArray }.getOrNull() } ?: return emptyList()
    return list.mapNotNull { element ->
        val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
        val content = item.stringOrNull("content") ?: item.stringOrNull("noticeContent") ?: return@mapNotNull null
        ActivityNotice(
            id = item["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            title = item.stringOrNull("title") ?: item.stringOrNull("noticeTitle").orEmpty(),
            content = content,
            time = item.stringOrNull("createTime")
                ?: item.stringOrNull("createdAt")
                ?: item.stringOrNull("publishTime")
                ?: item.millisText("ctime"),
            isPopup = (item["isPopup"]?.jsonPrimitive?.intOrNull ?: 0) == 1,
        )
    }
}

private fun kotlinx.serialization.json.JsonObject.millisText(key: String): String {
    val millis = this[key]?.jsonPrimitive?.longOrNull ?: return ""
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
        .format(java.util.Date(millis))
}

private fun kotlinx.serialization.json.JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

/** 活动列表参数：与官方活动页一致，共 11 个，缺项会返回 10002。 */
internal fun buildActivityParams(query: ActivityQuery): JsonObject = buildJsonObject {
    // 固定空串：服务端的 search 只影响 total，不影响 list（搜「挑战杯」得 total=5 但列表照旧
    // 是无关活动），带上去只会让分页提前截断。搜索改由 [ActivitySearch] 在本地做。
    put("search", JsonPrimitive(""))
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
    if (BuildConfig.DEBUG) {
        // 列表字段并不是全部已知，留一条原始样本便于核对
        runCatching { AppLog.d("discover", "listItem=${list.firstOrNull()}") }
    }
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
            enrollStartTime = item["enrollStartTime"]?.jsonPrimitive?.longOrNull ?: 0L,
            enrollEndTime = item["enrollEndTime"]?.jsonPrimitive?.longOrNull ?: 0L,
            enrollCount = item["enrollCount"]?.jsonPrimitive?.intOrNull ?: 0,
            peopleLimit = item["peopleLimit"]?.jsonPrimitive?.intOrNull ?: 0,
            organizationName = item["organizationName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            // 列表接口用 activityStatus，我的活动接口用 status，两个都认
            status = item["activityStatus"]?.jsonPrimitive?.intOrNull
                ?: item["status"]?.jsonPrimitive?.intOrNull ?: 0,
            recommended = (item["isRecommend"]?.jsonPrimitive?.intOrNull ?: 0) == 1,
            limitType = item["limitType"]?.jsonPrimitive?.intOrNull ?: 0,
            full = (item["isfull"]?.jsonPrimitive?.intOrNull ?: 0) != 0,
        )
    }
    return ActivityPage(total = total, pageNum = pageNum, items = items)
}

