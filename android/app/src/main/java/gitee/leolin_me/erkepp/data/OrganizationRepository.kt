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

/** 组织目录里的一个分类（官方 `dict/organization/choice-sort` 的 `filterArray`）。 */
data class OrgNature(val id: Int, val name: String)

/**
 * 排序方式。
 *
 * 官方页面里的下拉是写死的 `最新 / 最热`，但发给接口的值是 **0 / 1**
 * （`popClick`: 最新 → sortType=0、最热 → sortType=1，见 chunk 17）。
 * 字典里那个 `sortArray`（1 最热、2 最活跃）是给别的页面用的，这里不能拿来当本页的排序。
 */
data class OrgSort(val id: Int, val name: String)

data class OrgChoices(
    val natures: List<OrgNature>,
    val sorts: List<OrgSort>,
) {
    companion object {
        val SORTS = listOf(OrgSort(0, "最新"), OrgSort(1, "最热"))

        /** 字典拉不到时的兜底：只给「全部」，至少页面还能用。 */
        val FALLBACK = OrgChoices(natures = listOf(OrgNature(0, "全部")), sorts = SORTS)
    }
}

/**
 * 组织目录的一条（官方 `organization/page/organization/list`）。
 *
 * 和 `organization/all-list` 不是同一个接口：这一版带分类、排序、搜索，
 * 也是官方「服务 → 组织」页真正在用的那个。
 */
data class OrgCatalogItem(
    val id: Long,
    val name: String,
    val avatar: String?,
    val memberCount: Int,
    val energy: Double,
    val introduce: String,
    /** 我在这个组织里的身份：3 管理员、2 负责人、0 没加入。 */
    val identity: Int,
    /** 加入规则，`3` 表示不接受自己申请。 */
    val rule: Int,
    /** 是否开放加入。 */
    val isAdd: Int,
    /**
     * 官方条目的 `isElect`。实测 2026-10-24 这一页 **100 条全是 1**，
     * 所以官方模板那套「isElect==1 → 换届中」在目录里等于给每条都挂同一块牌子，
     * 客户端不拿它当「正在换届」用，只在详情里参考。
     */
    val isElect: Int,
) {
    /** 能申请：接受申请（`rule != 3`）且开放加入。 */
    val canApply: Boolean
        get() = rule != 3 && isAdd == 1

    val joined: Boolean get() = identity != 0
}

data class OrgCatalogPage(val total: Int, val list: List<OrgCatalogItem>, val lastPage: Boolean)

data class OrgDetail(    val id: Long,
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
     * 组织目录（官方 `#/service/organize`）。
     *
     * 参数与官方一致：`search`（服务端搜索）、`nature`（分类，0 = 全部）、
     * `sortType`（1 最热、2 最活跃）、`pageNum`、`pageSize`。
     */
    suspend fun catalog(
        token: String,
        search: String,
        nature: Int,
        sortType: Int,
        page: Int,
        pageSize: Int = PAGE_SIZE,
    ): OrgCatalogPage {
        val response = api.get(
            path = "organization/page/organization/list",
            params = buildJsonObject {
                put("search", JsonPrimitive(search))
                put("nature", JsonPrimitive(nature))
                put("sortType", JsonPrimitive(sortType))
                put("pageNum", JsonPrimitive(page))
                put("pageSize", JsonPrimitive(pageSize))
            },
            token = token,
        )
        response.requireSuccess()
        val data = response.jsonObject["data"] ?: return OrgCatalogPage(0, emptyList(), true)
        val parsed = parseOrgCatalogPage(data, page, pageSize)
        AppLog.d(
            "org",
            "catalog search=${search.take(20)} nature=$nature sort=$sortType page=$page " +
                "size=${parsed.list.size} total=${parsed.total}",
        )
        return parsed
    }

    /**
     * 分类字典（`filterArray`：全部 / 学生会组织 / 社团与协会 / 志愿组织 / 其他）。
     *
     * 排序**不取**字典里的 `sortArray`：那是给别的页面用的（1 最热、2 最活跃），
     * 本页排序是写死的「最新 / 最热」，接口值是 0 / 1，见 [OrgSort]。
     */
    suspend fun choices(token: String): OrgChoices {
        val response = runCatching {
            api.get(path = "dict/organization/choice-sort", params = buildJsonObject {}, token = token)
        }.getOrNull() ?: return OrgChoices.FALLBACK
        val data = (response as? JsonObject)?.get("data") as? JsonObject ?: return OrgChoices.FALLBACK
        val natures = (data["filterArray"] as? JsonArray)
            ?.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                OrgNature(obj.orgInt("id"), obj.orgStr("name"))
            }
            .orEmpty()
        AppLog.d("org", "choices natures=${natures.map { "${it.id}:${it.name}" }}")
        return if (natures.isEmpty()) {
            OrgChoices.FALLBACK
        } else {
            OrgChoices(natures = natures, sorts = OrgChoices.SORTS)
        }
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
        // 官方「服务 → 组织」那一页真正用的接口与分类字典
        dump("nature", "dict/organization/choice-sort", buildJsonObject {}, 3000)
        // 服务端搜索：确认 search 参数真的在过滤
        for (term in listOf("人工智能", "协会")) {
            dump(
                "catalog search=$term",
                "organization/page/organization/list",
                buildJsonObject {
                    put("search", JsonPrimitive(term))
                    put("pageSize", JsonPrimitive(3))
                    put("nature", JsonPrimitive(0))
                    put("sortType", JsonPrimitive(1))
                    put("pageNum", JsonPrimitive(1))
                },
                700,
            )
        }
        // 一页 100 条，用来统计 isElect / isAdd / rule 的分布
        dump(
            "catalog100",
            "organization/page/organization/list",
            buildJsonObject {
                put("search", JsonPrimitive(""))
                put("pageSize", JsonPrimitive(100))
                put("nature", JsonPrimitive(0))
                put("sortType", JsonPrimitive(1))
                put("pageNum", JsonPrimitive(1))
            },
            40000,
        )
        for (nature in listOf(-1, 0, 2, 3, 5, 19)) {
            for (sort in 1..2) {
                dump(
                    "catalog n=$nature s=$sort",
                    "organization/page/organization/list",
                    buildJsonObject {
                        put("search", JsonPrimitive(""))
                        put("pageSize", JsonPrimitive(2))
                        put("nature", JsonPrimitive(nature))
                        put("sortType", JsonPrimitive(sort))
                        put("pageNum", JsonPrimitive(1))
                    },
                    900,
                )
            }
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

internal fun parseOrgCatalogPage(
    data: JsonElement,
    page: Int = 1,
    pageSize: Int = OrganizationRepository.PAGE_SIZE,
): OrgCatalogPage {
    val obj = data as? JsonObject ?: return OrgCatalogPage(0, emptyList(), true)
    val list = (obj["list"] as? JsonArray)?.mapNotNull { parseOrgCatalogItem(it) }.orEmpty()
    val rawTotal = obj.orgInt("total")
    val lastPage = if (rawTotal > 0) page * pageSize >= rawTotal else list.size < pageSize
    return OrgCatalogPage(
        total = if (rawTotal > 0) rawTotal else list.size,
        list = list,
        lastPage = lastPage,
    )
}

internal fun parseOrgCatalogItem(element: JsonElement?): OrgCatalogItem? {
    val item = element as? JsonObject ?: return null
    val id = item.orgLong("id")
    if (id <= 0) return null
    return OrgCatalogItem(
        id = id,
        name = item.orgStr("name"),
        avatar = item.orgStr("avatar").takeIf { it.isNotBlank() },
        memberCount = item.orgInt("memberCount"),
        energy = item.orgDbl("energy"),
        introduce = item.orgStr("introduce"),
        identity = item.orgInt("identity"),
        rule = item.orgInt("rule"),
        isAdd = item.orgInt("isAdd"),
        isElect = item.orgInt("isElect"),
    )
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
