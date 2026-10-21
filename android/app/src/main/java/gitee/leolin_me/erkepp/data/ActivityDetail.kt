package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 活动附件。
 *
 * 官方详情页的 `attachment` 是**数组**，每项形如
 * `{"attachmentType": 5, "name": "通知.pdf", "url": "https://..."}`。
 * `attachmentType`：1 图片、3 Word、4 Excel、5 PDF、6 其它。
 */
data class ActivityAttachment(
    val name: String,
    val url: String,
    val type: Int,
) {
    /** 显示用后缀：没有文件名时用类型兜底。 */
    val extension: String
        get() {
            val fromName = name.substringAfterLast('.', "")
            if (fromName.isNotBlank() && fromName.length <= 4) return fromName.uppercase()
            return when (type) {
                1 -> "IMG"
                3 -> "DOC"
                4 -> "XLS"
                5 -> "PDF"
                else -> "FILE"
            }
        }
}

/**
 * 活动详情。
 *
 * 字段取自官方 H5 的「活动详情（未参加）」页面（`activity/detail/non-member`），
 * 已参加的用户走 `activity/detail/participant`，两者字段大体一致。
 */
data class ActivityDetail(
    val id: Long,
    val name: String,
    val cover: String?,
    val status: Int,
    val classifyName: String,
    val organizationName: String,
    val organizationAvatar: String?,
    val hours: Double,
    val credit: Double,
    val hoursToCreditRatio: Double,
    val startTime: Long,
    val endTime: Long,
    val enrollStartTime: Long,
    val enrollEndTime: Long,
    val address: String,
    val contact: String,
    val introduceHtml: String,
    val peopleLimit: Int,
    val joinMemberCount: Int,
    val genderLimit: Int,
    val gradeNames: List<String>,
    val collegeNames: List<String>,
    val honestLimit: Double,
    val attachments: List<ActivityAttachment>,
    /** 活动管理员姓名（官方详情页也会显示）。 */
    val managerName: String,
    /** 指导老师，可能为空。 */
    val guidanceNames: List<String>,
    /** 是否需要提交作业：1 是，0 否。 */
    val isSubmitWork: Int,
    /** 报名审核标记，含义待真机确认。 */
    val signAudit: Int,
    /** 考勤方式枚举，含义待真机确认。 */
    val attendanceType: Int,
    /** 报名模式：0 个人报名，其它为团队报名。 */
    val limitType: Int,
    /** 报名审核状态，participant 接口才会给。 */
    val applyStatus: Int,
    /** 1 已参加、2/3 管理员、0 未参加。 */
    val identity: Int = 0,
    /** 是否已提交作业：1 已提交（查看作业）、0 未提交（交作业）。 */
    val isPostHomework: Int = 0,
    /** 数据来自哪个接口：`non-member` 或 `participant`。 */
    val source: String,
) {
    /** 是否限制了参与人群，用于决定要不要显示「参与限制」一栏。 */
    val hasRestriction: Boolean
        get() = genderLimit != 0 || gradeNames.isNotEmpty() || collegeNames.isNotEmpty() || honestLimit > 0
}

internal fun parseActivityDetail(data: JsonObject, source: String): ActivityDetail = ActivityDetail(
    id = data.long("id"),
    name = data.text("name"),
    cover = data.text("logo").takeIf { it.isNotBlank() },
    status = data.int("activityStatus").takeIf { it != 0 } ?: data.int("status"),
    classifyName = data.text("classifyName"),
    organizationName = data.text("organizationName"),
    organizationAvatar = data.text("organizationAvatar").takeIf { it.isNotBlank() },
    hours = data.double("hours"),
    credit = data.double("credit"),
    hoursToCreditRatio = data.double("hoursToCreditRatio"),
    startTime = data.long("startTime"),
    endTime = data.long("endTime"),
    enrollStartTime = data.long("enrollStartTime"),
    enrollEndTime = data.long("enrollEndTime"),
    address = data.text("address"),
    contact = data.text("contact"),
    introduceHtml = data.text("introduce"),
    peopleLimit = data.int("peopleLimit"),
    joinMemberCount = data.int("joinMemberCount"),
    genderLimit = data.int("genderLimit"),
    gradeNames = data.nameList("gradeList"),
    collegeNames = data.nameList("collegeList"),
    honestLimit = data.double("honestLimit"),
    attachments = data.attachments("attachment"),
    managerName = (data.element("manager") as? JsonObject)?.text("name").orEmpty(),
    guidanceNames = data.nameList("guidanceList"),
    isSubmitWork = data.int("isSubmitWork"),
    signAudit = data.int("signAudit"),
    attendanceType = data.int("attendanceType"),
    limitType = (data.element("activityLimitType") as? JsonObject)?.int("type") ?: 0,
    applyStatus = data.int("applyStatus"),
    isPostHomework = data.int("isPostHomework"),
    source = source,
)

private fun JsonObject.element(key: String): JsonElement? =
    this[key]?.takeIf { it !is JsonNull }

private fun JsonObject.text(key: String): String =
    (element(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.int(key: String): Int =
    (element(key) as? JsonPrimitive)?.intOrNull ?: 0

private fun JsonObject.long(key: String): Long {
    val primitive = element(key) as? JsonPrimitive ?: return 0L
    primitive.longOrNull?.let { return it }
    // 少数字段可能给的是字符串，按数字再试一次
    return primitive.contentOrNull?.toLongOrNull() ?: 0L
}

private fun JsonObject.double(key: String): Double {
    val primitive = element(key) as? JsonPrimitive ?: return 0.0
    primitive.doubleOrNull?.let { return it }
    return primitive.contentOrNull?.toDoubleOrNull() ?: 0.0
}

/** 年级 / 学院列表既可能是字符串数组，也可能是对象数组，取 name 字段。 */
private fun JsonObject.nameList(key: String): List<String> {
    val array = (element(key) as? JsonElement)?.let { runCatching { it.jsonArray }.getOrNull() } ?: return emptyList()
    return array.mapNotNull { item ->
        when (item) {
            is JsonPrimitive -> item.contentOrNull?.takeIf { it.isNotBlank() }
            is JsonObject -> item.text("name").takeIf { it.isNotBlank() }
            else -> null
        }
    }
}

private fun JsonObject.attachments(key: String): List<ActivityAttachment> {
    val element = element(key) ?: return emptyList()
    val items: List<JsonElement> = when (element) {
        is kotlinx.serialization.json.JsonArray -> element.toList()
        else -> listOf(element)
    }
    return items.mapNotNull { item ->
        val url = when (item) {
            is JsonPrimitive -> item.contentOrNull
            is JsonObject -> item.text("url").ifBlank { item.text("path") }.ifBlank { item.text("fileUrl") }
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val name = (item as? JsonObject)?.text("name").orEmpty().ifBlank {
            url.substringAfterLast('/').substringBefore('?')
        }
        val type = (item as? JsonObject)?.int("attachmentType") ?: 0
        ActivityAttachment(name = name, url = url, type = type)
    }
}
