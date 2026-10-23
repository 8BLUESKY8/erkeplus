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
import kotlinx.serialization.json.longOrNull

/**
 * 诚信值。
 *
 * 官方页面 `#/honestValue` 用三个接口：
 * - `credit/user/detail` 取本人分数与更新时间
 * - `dict/school/system-para/credit/list` 取分数段与等级（完美 / 优异 / 良好 / 中等 / 一般 / 较差）
 * - `credit/user/list` 取记录，`type` 1 待守约、2 已守约、3 已失约、4 申诉记录
 *
 * 全部只读。2026-10-23 实测。
 */
data class CreditDetail(
    val credit: Double,
    /** 分数更新时间；0 表示服务端没给。 */
    val updateTime: Long,
    /** 服务端给的拉黑说明，通常是 null。 */
    val blacklist: String?,
)

/** 分数段：`minScore..maxScore` 对应 [grade]。 */
data class CreditGrade(
    val minScore: Int,
    val maxScore: Int,
    val grade: String,
)

/** 一条诚信记录。字段随 `type` 不同，用不到的留默认值。 */
data class CreditRecord(
    val relationId: Long,
    val name: String,
    val logo: String?,
    val startTime: Long,
    val endTime: Long,
    /** 扣分/加分值；待守约没有这个字段，记 0。 */
    val amount: Double,
    /** `+` 或 `-`。 */
    val operator: String,
    val comment: String,
    /** 待守约里是活动状态；其它类型没有。 */
    val status: Int,
)

/** 一条申诉记录。`status` 是 `appealStatus`：1 待审核、3 被驳回、其它为已通过。 */
data class CreditAppeal(
    val relationId: Long,
    val name: String,
    val logo: String?,
    val time: Long,
    val status: Int,
)

class CreditRepository(private val api: ApiClient) {

    suspend fun detail(token: String): CreditDetail {
        val response = api.get("credit/user/detail", buildJsonObject { }, token)
        response.requireSuccess()
        val data = response.jsonObject["data"] as? JsonObject ?: return CreditDetail(0.0, 0L, null)
        return CreditDetail(
            credit = data.dbl("credit"),
            updateTime = data.num("faithUpdateTime"),
            blacklist = data.str("blacklist").takeIf { it.isNotBlank() },
        )
    }

    suspend fun grades(token: String): List<CreditGrade> {
        val response = api.get("dict/school/system-para/credit/list", buildJsonObject { }, token)
        response.requireSuccess()
        val data = response.jsonObject["data"] ?: return emptyList()
        val parsed = parseCreditGrades(data)
        AppLog.d("credit", "grades=${parsed.size} raw=${data.toString().take(160)}")
        return parsed
    }

    /**
     * 申诉记录。
     *
     * 注意：**不是** `credit/user/list` 的 type=4——官方 H5 的「申诉记录」走的是独立接口
     * `credit/user/appeal-list`。`credit/user/list` 只有 type 1/2/3（待守约/已守约/已失约），
     * 传 0、3、4 都会返回失约记录，拿它当申诉列表会把两者混在一起。
     */
    suspend fun appeals(token: String, page: Int, pageSize: Int = 20): Pair<Int, List<CreditAppeal>> {
        val response = api.get(
            path = "credit/user/appeal-list",
            params = buildJsonObject {
                put("searchName", JsonPrimitive(""))
                put("pageNum", JsonPrimitive(page))
                put("pageSize", JsonPrimitive(pageSize))
            },
            token = token,
        )
        response.requireSuccess()
        val data = response.jsonObject["data"] as? JsonObject ?: return 0 to emptyList()
        val total = data.int("total")
        val list = (data["list"] as? JsonArray)?.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            CreditAppeal(
                relationId = item.num("relationId"),
                name = item.str("relationName"),
                logo = item.str("logo").takeIf { it.isNotBlank() },
                // 申诉时间字段没有实测样本，服务端给哪个用哪个
                time = item.num("appealTime").takeIf { it > 0 } ?: item.num("ctime"),
                status = item.int("appealStatus"),
            )
        }.orEmpty()
        return total to list
    }

    /** 记录列表：返回总数与当前页。 */
    suspend fun records(token: String, type: Int, page: Int, pageSize: Int = 20): Pair<Int, List<CreditRecord>> {
        val response = api.get(
            path = "credit/user/list",
            params = buildJsonObject {
                put("searchName", kotlinx.serialization.json.JsonPrimitive(""))
                put("type", kotlinx.serialization.json.JsonPrimitive(type))
                put("pageNum", kotlinx.serialization.json.JsonPrimitive(page))
                put("pageSize", kotlinx.serialization.json.JsonPrimitive(pageSize))
            },
            token = token,
        )
        response.requireSuccess()
        val data = response.jsonObject["data"] as? JsonObject ?: return 0 to emptyList()
        val total = data.int("total")
        val list = (data["list"] as? JsonArray)?.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            CreditRecord(
                relationId = item.num("relationId"),
                name = item.str("relationName"),
                logo = item.str("logo").takeIf { it.isNotBlank() },
                startTime = item.num("startTime"),
                endTime = item.num("endTime"),
                amount = item.dbl("amount"),
                operator = item.str("operator"),
                comment = item.str("comment"),
                status = item.int("status"),
            )
        }.orEmpty()
        return total to list
    }
}

/** 分数对应的等级；超出所有区间时返回空串。 */
fun List<CreditGrade>.gradeOf(score: Double): String =
    firstOrNull { score >= it.minScore && score <= it.maxScore }?.grade.orEmpty()

/** 解析分数段数组：按分数从高到低排，方便界面按顺序列规则。 */
internal fun parseCreditGrades(data: JsonElement): List<CreditGrade> {
    val array = data as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        CreditGrade(
            minScore = item.score("minScore"),
            maxScore = item.score("maxScore"),
            grade = item.str("grade"),
        )
    }.sortedByDescending { it.minScore }
}

/**
 * 分数段字段按 double 读再截断。
 *
 * 服务端有时给 `100`，有时给 `100.0`；`intOrNull` 对后者返回 null，直接用会把 100 读成 0。
 */
private fun JsonObject.score(key: String): Int {
    val primitive = this[key] as? JsonPrimitive ?: return 0
    val value = primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull()
    return value?.toInt() ?: 0
}

private fun JsonObject.str(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.int(key: String): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: 0

private fun JsonObject.num(key: String): Long {
    val primitive = this[key] as? JsonPrimitive ?: return 0L
    primitive.longOrNull?.let { return it }
    return primitive.contentOrNull?.toLongOrNull() ?: 0L
}

private fun JsonObject.dbl(key: String): Double {
    val primitive = this[key] as? JsonPrimitive ?: return 0.0
    primitive.doubleOrNull?.let { return it }
    return primitive.contentOrNull?.toDoubleOrNull() ?: 0.0
}
