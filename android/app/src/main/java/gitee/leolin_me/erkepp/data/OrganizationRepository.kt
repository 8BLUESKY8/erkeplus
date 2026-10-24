package gitee.leolin_me.erkepp.data

import gitee.leolin_me.erkepp.AppLog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 我的组织（官方 `#/myOrganization`）与组织详情。
 *
 * 四个只读接口 + 一个写接口：
 * - `organization/my-list` 我加入的
 * - `organization/my-create-list` 我创建的
 * - `organization/my-quit-list` 我退出的
 * - `organization/detail` 组织详情（扫码进的就是这里）
 * - `organization/user-apply/add` 申请加入
 *
 * 三者共用 `pageNum / pageSize / status`，`status` 是申请的审核状态 1 待审核 / 2 已通过 / 3 已驳回。
 * 列表项里的 `orgProcessStatus` 是**字符串码**（`re3`、`ya2`…），不是数字，照官方原样保留再翻译。
 */
enum class OrgListKind(val path: String, val label: String) {
    JOINED("organization/my-list", "我加入的"),
    CREATED("organization/my-create-list", "我创建的"),
    QUIT("organization/my-quit-list", "我退出的"),
}

/** 加入申请的审核状态。 */
enum class OrgApplyStatus(val value: Int, val label: String) {
    PENDING(1, "待审核"),
    PASSED(2, "已通过"),
    REJECTED(3, "已驳回"),
    ;

    companion object {
        fun of(value: Int): OrgApplyStatus? = entries.firstOrNull { it.value == value }
    }
}

/** 组织自身的生命周期状态，码来自官方 `orgProcessStatus`。 */
internal fun orgProcessLabel(code: String): String = when (code) {
    "re1" -> "待注册"
    "re2" -> "注册中"
    "re3" -> "已注册"
    "ap2" -> "评优中"
    "ya1" -> "待年审"
    "ya2" -> "年审中"
    "ya3" -> "整改中"
    "ct1" -> "换届中"
    "cw1" -> "待注销"
    "cw2" -> "注销中"
    "cw3" -> "已注销"
    else -> ""
}

/** 我在组织里的身份：3 管理员、2 负责人，其余按成员处理。 */
internal fun orgIdentityLabel(identity: Int): String = when (identity) {
    3 -> "管理员"
    2 -> "负责人"
    else -> ""
}

data class OrgListItem(
    val id: Long,
    val name: String,
    val avatar: String?,
    val memberCount: Int,
    /** 申请审核状态，见 [OrgApplyStatus]。 */
    val status: Int,
    val identity: Int,
    /** 组织生命周期状态码，见 [orgProcessLabel]。 */
    val processStatus: String,
    /** 加入 / 创建 / 退出的时间，已格式化。 */
    val timeText: String,
    /** 驳回理由，仅 `status == 3` 时有值。 */
    val auditRemark: String,
    /** 组织的活跃值；官方列表上显示成「活跃 xxx」。 */
    val energy: Double,
    /** 一句话简介，组织目录里显示。 */
    val introduce: String,
)

data class OrgPage(val total: Int, val list: List<OrgListItem>, val lastPage: Boolean)

data class OrgDetail(
    val id: Long,
    val name: String,
    val avatar: String?,
    val memberCount: Int,
    /** 组织性质，例如「社团」「学院」。官方拿它去查评优字典。 */
    val nature: String,
    /** 性质的名字，直接显示。 */
    val natureName: String,
    /** 分类，例如「学术科技类」。 */
    val categoryName: String,
    /** 挂靠单位。 */
    val affiliationName: String,
    val chairmanName: String,
    val guiderName: String,
    /** 简介，可能带 HTML。 */
    val intro: String,
    val energy: Double,
    val processStatus: String,
    /** 我的申请状态：0 未申请、1 待审核、2 通过、3 驳回。 */
    val applyStatus: Int,
    /** 是否已经有记录（申请中或已加入）。 */
    val isJoin: Int,
    /** 组织是否开放加入。 */
    val isAdd: Int,
    /** 加入规则，官方用 `rule == 3` 表示不接受申请。 */
    val rule: Int,
) {
    /**
     * 能不能点「申请加入」。照官方 `orgDetail` 页的按钮条件，但用 `applyStatus` 兜住重复申请：
     * 官方靠 `isElect == 0` 才显示按钮，`isElect` 只在扫码进来的那条路径上是 0，
     * 直接进详情时是 null，按钮反而不出现。这里以「没申请过 / 被驳回」为准更稳。
     */
    val canApply: Boolean
        get() = (applyStatus == 0 || applyStatus == 3) && rule != 3 && isAdd == 1
}

class OrganizationRepository(private val api: ApiClient) {

    suspend fun myList(
        token: String,
        kind: OrgListKind,
        status: OrgApplyStatus,
        page: Int,
        pageSize: Int = PAGE_SIZE,
    ): OrgPage {
        val response = api.get(
            path = kind.path,
            params = buildJsonObject {
                put("pageNum", JsonPrimitive(page))
                put("pageSize", JsonPrimitive(pageSize))
                put("status", JsonPrimitive(status.value))
            },
            token = token,
        )
        response.requireSuccess()
        val data = response.jsonObject["data"] ?: return OrgPage(0, emptyList(), true)
        val parsed = parseOrgPage(data, page, pageSize)
        AppLog.d(
            "org",
            "list kind=${kind.name} status=${status.value} page=$page size=${parsed.list.size} " +
                "total=${parsed.total}",
        )
        return parsed
    }

    /**
     * 组织目录（官方 `#/service/organize`，页面标题「校园组织」）。
     *
     * `type` 在实测里传 1 和 2 返回的是同一批（都是 131 个），所以固定传 1。
     */
    suspend fun all(token: String, page: Int, pageSize: Int = PAGE_SIZE): OrgPage {
        val response = api.get(
            path = "organization/all-list",
            params = buildJsonObject {
                put("pageNum", JsonPrimitive(page))
                put("pageSize", JsonPrimitive(pageSize))
                put("type", JsonPrimitive(1))
            },
            token = token,
        )
        response.requireSuccess()
        val data = response.jsonObject["data"] ?: return OrgPage(0, emptyList(), true)
        val parsed = parseOrgPage(data, page, pageSize)
        AppLog.d("org", "all page=$page size=${parsed.list.size} total=${parsed.total}")
        return parsed
    }

    suspend fun detail(token: String, id: Long): OrgDetail {
        val response = api.get(
            path = "organization/detail",
            params = buildJsonObject { put("id", JsonPrimitive(id)) },
            token = token,
        )
        response.requireSuccess()
        val data = response.jsonObject["data"] ?: throw ApiException(-1, "没拿到组织信息")
        val detail = parseOrgDetail(data)
        AppLog.d("org", "detail id=$id name=${detail.name} members=${detail.memberCount}")
        return detail
    }

    /** 组织主办的活动，分页同活动列表。 */
    suspend fun activities(
        token: String,
        organizationId: Long,
        page: Int,
        pageSize: Int = ACTIVITY_PAGE_SIZE,
    ): ActivityPage {
        val response = api.get(
            path = "organization/activity-list",
            params = buildJsonObject {
                put("pageNum", JsonPrimitive(page))
                put("pageSize", JsonPrimitive(pageSize))
                put("organizationId", JsonPrimitive(organizationId))
            },
            token = token,
        )
        response.requireSuccess()
        return parseActivityPage(response, page)
    }

    /**
     * 申请加入。`applyInfo` 是申请理由，官方必填（空着会弹「请输入申请信息」）。
     *
     * 官方在点之前还会提示一句「申请加入组织后无法退出」，这里由界面负责说这句话。
     */
    suspend fun join(token: String, organizationId: Long, applyInfo: String) {
        val response = api.post(
            path = "organization/user-apply/add",
            params = buildJsonObject {
                put("id", JsonPrimitive(organizationId))
                put("applyInfo", JsonPrimitive(applyInfo))
            },
            token = token,
        )
        response.requireSuccess()
        AppLog.d("org", "join ok org=$organizationId info=${applyInfo.length}字")
    }

    companion object {
        const val PAGE_SIZE = 20
        const val ACTIVITY_PAGE_SIZE = 10
    }

    /**
     * 调试用：把这一族接口的原始 JSON 打出来，核对字段名用。
     *
     * 只有 debug 包的 probe 分支会调它，release 里连调用带方法一起被裁掉。
     */
    suspend fun rawDump(token: String, orgId: Long) {
        suspend fun dump(label: String, path: String, params: JsonObject, take: Int = 2500) {
            val result = runCatching { api.get(path, params, token) }
            AppLog.d(
                "org",
                "raw $label ${result.map { it.toString().take(take) }.getOrElse { "ERR ${it.message}" }}",
            )
        }

        for (status in 1..3) {
            for (kind in OrgListKind.entries) {
                dump(
                    "${kind.name}$status",
                    kind.path,
                    buildJsonObject {
                        put("pageNum", JsonPrimitive(1))
                        put("pageSize", JsonPrimitive(3))
                        put("status", JsonPrimitive(status))
                    },
                )
            }
        }
        // 组织目录，顺便确认 type 的含义（H5 的 /organization/:type）
        for (type in 1..2) {
            dump(
                "all$type",
                "organization/all-list",
                buildJsonObject {
                    put("pageNum", JsonPrimitive(1))
                    put("pageSize", JsonPrimitive(3))
                    put("type", JsonPrimitive(type))
                },
            )
        }
        // 第二页：客户端翻页到底行不行，直接看返回码和条数
        for (page in 1..2) {
            dump(
                "allp$page",
                "organization/all-list",
                buildJsonObject {
                    put("pageNum", JsonPrimitive(page))
                    put("pageSize", JsonPrimitive(20))
                    put("type", JsonPrimitive(1))
                },
                600,
            )
        }
        for (id in listOf(orgId, 17085L)) {
            val raw = runCatching {
                api.get("organization/detail", buildJsonObject { put("id", JsonPrimitive(id)) }, token)
            }
            val data = (raw.getOrNull()?.let { it as? JsonObject }?.get("data")) as? JsonObject
            AppLog.d("org", "detail id=$id err=${raw.exceptionOrNull()?.message} keys=${data?.keys?.sorted()}")
            if (data != null) {
                AppLog.d(
                    "org",
                    "detail id=$id brief isJoin=${data["isJoin"]} isElect=${data["isElect"]} " +
                        "isAdd=${data["isAdd"]} rule=${data["rule"]} applyStatus=${data["applyStatus"]} " +
                        "identity=${data["identity"]} memberCount=${data["memberCount"]} " +
                        "avatar=${data["avatar"]} nature=${data["nature"]} " +
                        "members=${(data["memberList"] as? JsonArray)?.size} " +
                        "introduce=${data["introduce"]?.toString()?.take(160)}",
                )
            }
        }
        dump(
            "acts",
            "organization/activity-list",
            buildJsonObject {
                put("pageNum", JsonPrimitive(1))
                put("pageSize", JsonPrimitive(2))
                put("organizationId", JsonPrimitive(orgId))
            },
        )
        dump(
            "members",
            "organization/member-list",
            buildJsonObject {
                put("organizationId", JsonPrimitive(orgId))
                put("searchKey", JsonPrimitive(""))
                put("userLastElectId", JsonPrimitive(""))
            },
        )
    }
}

internal fun parseOrgPage(data: JsonElement, page: Int = 1, pageSize: Int = OrganizationRepository.PAGE_SIZE): OrgPage {
    val obj = data as? JsonObject ?: return OrgPage(0, emptyList(), true)
    val list = (obj["list"] as? JsonArray)?.mapNotNull { parseOrgItem(it) }.orEmpty()
    val rawTotal = obj.orgInt("total")
    val lastPage = if (rawTotal > 0) page * pageSize >= rawTotal else list.size < pageSize
    return OrgPage(total = if (rawTotal > 0) rawTotal else list.size, list = list, lastPage = lastPage)
}

internal fun parseOrgItem(element: JsonElement?): OrgListItem? {
    val item = element as? JsonObject ?: return null
    val id = item.orgLong("id")
    if (id <= 0) return null
    val joinTime = item.orgTime("joinTime").ifBlank { item.orgTime("ctime") }
    return OrgListItem(
        id = id,
        name = item.orgStr("name"),
        avatar = item.orgStr("avatar").takeIf { it.isNotBlank() },
        memberCount = item.orgInt("memberCount"),
        status = item.orgInt("status"),
        identity = item.orgInt("identity"),
        processStatus = item.orgStr("orgProcessStatus"),
        timeText = joinTime,
        auditRemark = item.orgStr("auditRemark"),
        energy = item.orgDbl("energy"),
        introduce = item.orgStr("introduce"),
    )
}

internal fun parseOrgDetail(data: JsonElement): OrgDetail {
    val item = data as? JsonObject ?: throw ApiException(-1, "没拿到组织信息")
    return OrgDetail(
        id = item.orgLong("id"),
        name = item.orgStr("name"),
        avatar = item.orgStr("avatar").takeIf { it.isNotBlank() },
        memberCount = item.orgInt("memberCount"),
        nature = item.orgStr("nature"),
        natureName = item.orgStr("natureName"),
        categoryName = item.orgStr("categoryName"),
        affiliationName = item.orgStr("affiliationName"),
        chairmanName = item.orgStr("chairmanName"),
        guiderName = item.orgStr("guiderName"),
        intro = item.orgStr("introduce").ifBlank {
            listOf("introduction", "intro", "describe", "content")
                .map { item.orgStr(it) }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        },
        energy = item.orgDbl("energy"),
        processStatus = item.orgStr("orgProcessStatus"),
        applyStatus = item.orgInt("applyStatus"),
        isJoin = item.orgInt("isJoin"),
        isAdd = item.orgInt("isAdd"),
        rule = item.orgInt("rule"),
    )
}

private fun JsonObject.orgStr(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.orgInt(key: String): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: 0

private fun JsonObject.orgLong(key: String): Long =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L

private fun JsonObject.orgDbl(key: String): Double {
    val primitive = this[key] as? JsonPrimitive ?: return 0.0
    primitive.doubleOrNull?.let { return it }
    return primitive.contentOrNull?.toDoubleOrNull() ?: 0.0
}

/**
 * 时间字段：可能是毫秒时间戳，也可能已经是 `2026-10-24` 这种文本，两种都认。
 *
 * 官方列表用 `dateFormat` 过滤器渲染，说明后端给的是时间戳。
 */
internal fun JsonObject.orgTime(key: String): String {
    val primitive = this[key] as? JsonPrimitive ?: return ""
    val raw = primitive.contentOrNull.orEmpty()
    val millis = primitive.contentOrNull?.toLongOrNull() ?: return raw
    // 小于 1e11 的多半是秒级时间戳；再小就不是时间了
    val normalized = if (millis in 1_000_000_000L..99_999_999_999L) millis * 1000 else millis
    if (normalized < 100_000_000_000L) return raw
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(normalized))
}
