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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/**
 * 成绩单排行榜（官方 `#/reportRank`）。
 *
 * 两个只读接口：
 * - `student/achievement/rank` 分页取榜单
 * - `student/achievement/self/rank` 取本人所在名次
 *
 * 榜单上的「成绩」就是学分，由服务端按学校配置的学时折算比例算好（`score = hours × 0.025`），
 * 客户端只显示 [RankEntry.score]，不要自己乘。2026-10-24 实测。
 */
enum class RankScope(val level: Int, val label: String) {
    SCHOOL(6, "全校"),
    COLLEGE(8, "院系"),
    MAJOR(9, "专业"),
    CLASS(10, "班级"),
    ;

    companion object {
        /** 官方默认看本班。 */
        val DEFAULT = CLASS

        fun of(level: Int): RankScope = entries.firstOrNull { it.level == level } ?: DEFAULT
    }
}

/** 榜单里的一条。`rownum` 是名次，并列同名次。 */
data class RankEntry(
    val rownum: Int,
    /** 用户 id，点进人物主页用；接口字段名就是 `id`。 */
    val userId: Long,
    val name: String,
    val avatar: String?,
    /** 1 男、2 女；其它值当未知。 */
    val gender: Int,
    /** 成绩（学分）。 */
    val score: Double,
    /** 学时。 */
    val hours: Double,
    /** 学号。接口字段名是 `code`。 */
    val studentNo: String,
    val collegeName: String,
    val majorName: String,
    val className: String,
    val grade: String,
    val campusName: String,
)

/** 一页榜单。[lastPage] 由服务端给；缺失时按 [total] 推断。 */
data class RankPage(
    val total: Int,
    val lastPage: Boolean,
    val list: List<RankEntry>,
)

class RankRepository(private val api: ApiClient) {

    /** 名次所在范围。`pageSize` 官方用 10，这里放宽到 20 少翻几次页。 */
    suspend fun ranks(
        token: String,
        scope: RankScope,
        page: Int,
        pageSize: Int = PAGE_SIZE,
    ): RankPage {
        val response = api.get(
            path = "student/achievement/rank",
            params = buildJsonObject {
                put("pageNum", JsonPrimitive(page))
                put("pageSize", JsonPrimitive(pageSize))
                put("level", JsonPrimitive(scope.level))
            },
            token = token,
        )
        response.requireSuccess()
        val data = response.jsonObject["data"] ?: return RankPage(0, true, emptyList())
        val parsed = parseRankPage(data, page, pageSize)
        AppLog.d(
            "rank",
            "level=${scope.level}(${scope.label}) page=$page size=${parsed.list.size} " +
                "total=${parsed.total} last=${parsed.lastPage}",
        )
        return parsed
    }

    /** 本人名次；未上榜或服务端没给时返回 null。 */
    suspend fun myRank(token: String, scope: RankScope): RankEntry? {
        val response = api.get(
            path = "student/achievement/self/rank",
            params = buildJsonObject { put("level", JsonPrimitive(scope.level)) },
            token = token,
        )
        response.requireSuccess()
        val data = response.jsonObject["data"] ?: return null
        val entry = parseRankEntry(data)
        AppLog.d(
            "rank",
            "mine level=${scope.level} rank=${entry?.rownum} score=${entry?.score} " +
                "hours=${entry?.hours} code=${entry?.studentNo}",
        )
        return entry
    }

    companion object {
        const val PAGE_SIZE = 20
    }

    /**
     * 调试用：把整份榜单按页拉完，日志里输出「名次:姓名」。
     *
     * 用来核对分页的收尾条件，以及本人到底在不在榜单里。
     */
    suspend fun dumpAll(token: String, scope: RankScope) {
        var page = 1
        val rows = mutableListOf<String>()
        while (page <= 50) {
            val result = ranks(token, scope, page)
            rows += result.list.map { "${it.rownum}:${it.name}" }
            if (result.lastPage || result.list.isEmpty()) break
            page++
        }
        AppLog.d("rank", "all level=${scope.level}(${scope.label}) pages=$page count=${rows.size} rows=$rows")
    }

    /**
     * 调试用：把两个接口的原始 JSON 打进日志，核对字段名用。
     *
     * 只有 debug 包的 probe 分支会调它，release 里连调用带方法一起被裁掉。
     */
    suspend fun rawDump(token: String, level: Int, page: Int = 1, pageSize: Int = PAGE_SIZE) {
        val list = api.get(
            path = "student/achievement/rank",
            params = buildJsonObject {
                put("pageNum", JsonPrimitive(page))
                put("pageSize", JsonPrimitive(pageSize))
                put("level", JsonPrimitive(level))
            },
            token = token,
        )
        AppLog.d("rank", "raw level=$level page=$page size=$pageSize list=${list.toString().take(4000)}")
        val mine = api.get(
            path = "student/achievement/self/rank",
            params = buildJsonObject { put("level", JsonPrimitive(level)) },
            token = token,
        )
        AppLog.d("rank", "raw level=$level mine=${mine.toString().take(1500)}")
    }
}

internal fun parseRankPage(data: JsonElement, page: Int = 1, pageSize: Int = RankRepository.PAGE_SIZE): RankPage {
    val obj = data as? JsonObject ?: return RankPage(0, true, emptyList())
    val list = (obj["list"] as? JsonArray)
        ?.mapNotNull { parseRankEntry(it) }
        .orEmpty()
    val total = obj.int("total").let { if (it > 0) it else list.size }
    // 服务端**没有** lastPage 字段（2026-10-24 实测，data 里只有 total / pageSize / pageNum / list），
    // 官方 H5 读到的 `lastPage` 永远是 undefined，靠 `list.length === total` 判断到底。
    // 这里以 total 为准（翻到第几页就够总数），total 缺失时退回「本页没满」。
    val lastPage = obj.boolean("lastPage")
        ?: if (total > 0) page * pageSize >= total else list.size < pageSize
    return RankPage(total = total, lastPage = lastPage, list = list)
}

internal fun parseRankEntry(element: JsonElement?): RankEntry? {
    val item = element as? JsonObject ?: return null
    return RankEntry(
        rownum = item.int("rownum").let { if (it > 0) it else item.int("rank") },
        userId = item.str("id").toLongOrNull() ?: 0L,
        name = item.str("name"),
        avatar = item.str("avatar").takeIf { it.isNotBlank() },
        gender = item.int("gender"),
        score = item.dbl("score"),
        hours = item.dbl("hours"),
        studentNo = item.str("code"),
        collegeName = item.str("collegeName"),
        majorName = item.str("majorName"),
        className = item.str("className"),
        grade = item.str("grade"),
        campusName = item.str("campusName"),
    )
}

/**
 * 成绩的显示文本：最多三位小数，去掉末尾的 0。
 *
 * 榜单上 4.225 与 4.23 是不同的名次，所以不能按两位小数四舍五入。
 */
internal fun rankScoreText(value: Double): String {
    if (value == 0.0) return "0"
    val text = String.format(java.util.Locale.US, "%.3f", value)
    return if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
}

/** 学时同理：可能带小数（例如半小时的活动）。 */
internal fun rankHoursText(value: Double): String {
    if (value == value.toLong().toDouble()) return value.toLong().toString()
    return String.format(java.util.Locale.US, "%.1f", value)
}

private fun JsonObject.str(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.int(key: String): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: 0

private fun JsonObject.boolean(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.dbl(key: String): Double {
    val primitive = this[key] as? JsonPrimitive ?: return 0.0
    primitive.doubleOrNull?.let { return it }
    return primitive.contentOrNull?.toDoubleOrNull() ?: 0.0
}
