package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 已提交的作业。
 *
 * 来自官方 `activity/query/work_3_0_1?activityId=`（`#/assignmentinfor/:id` 页面用的接口）。
 * 实测字段：`activityId`、`introduce`（提交的正文）、`attachment`（附件数组）、`attachUrls`（逗号拼接的同一批地址）。
 */
data class ActivityWork(
    val activityId: Long,
    val content: String,
    val attachments: List<ActivityAttachment>,
)

internal fun parseWork(element: JsonElement?): ActivityWork? {
    if (element == null || element is JsonNull) return null
    val data = runCatching { element.jsonObject }.getOrNull() ?: return null
    return ActivityWork(
        activityId = data["activityId"]?.jsonPrimitive?.longOrNull ?: 0L,
        content = data["introduce"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        attachments = data.attachmentList("attachment"),
    )
}

private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
