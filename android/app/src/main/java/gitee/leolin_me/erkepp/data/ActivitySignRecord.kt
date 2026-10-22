package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 一次签到 / 签退记录。
 *
 * 字段来自官方 `activity/sign/one/list`（在 H5 的「打卡记录」里渲染）：
 * `type`（1 签到、其它签退）、`signTime`（毫秒时间戳）、`isSupplement`（1 表示补签）。
 */
data class SignRecord(
    val action: String,
    val time: Long,
    val supplement: Boolean,
) {
    val label: String get() = if (supplement) "$action（补签）" else action
}

internal fun parseSignRecords(data: JsonElement?): List<SignRecord> {
    if (data == null || data is JsonNull) return emptyList()
    val array = runCatching { data.jsonArray }.getOrNull() ?: return emptyList()
    return array.mapNotNull { element ->
        val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
        val time = item["signTime"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
        SignRecord(
            action = if ((item["type"]?.jsonPrimitive?.intOrNull ?: 2) == 1) "签到" else "签退",
            time = time,
            supplement = (item["isSupplement"]?.jsonPrimitive?.intOrNull ?: 0) == 1,
        )
    }.sortedByDescending { it.time }
}
