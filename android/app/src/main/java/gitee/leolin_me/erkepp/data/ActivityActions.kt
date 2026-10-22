package gitee.leolin_me.erkepp.data

import gitee.leolin_me.erkepp.AppLog
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 活动写操作与相关只读查询的封装。
 *
 * **目前没有任何界面入口**，也没有任何地方会自动调用写接口：
 * 报名、取消、签到、补签这些方法只是把官方 H5 的请求形状固定下来，
 * 调用时把请求参数和原始响应写进日志（`adb logcat -s action`，
 * 或 `files/logs/erkepp.log` 里的 `[action]` 行），供以后实测使用。
 *
 * 参数取自官方 H5 源码：
 * - 个人报名 `POST activity/enroll/person`：`{id, personMaterial?}`
 * - 团队报名 `POST activity/enroll/team`：`{id, teamName, teamAvatar, teamMembers, teamMaterial}`
 * - 取消报名 `POST activity/enroll/cancel/person`：`{id}`
 * - 补签 `POST activity/sign/repair`：`{activityId, userIds}`
 * - 作业列表 `GET activity/submit/homework/list`：`{pageNum, pageSize}`
 * - 签到人数 `GET activity/sign/count`：`{id}`
 */
class ActivityActions(private val api: ApiClient) {

    // ---- 只读，可安全调用 ----

    suspend fun homeworkList(token: String, page: Int = 1, pageSize: Int = 20): JsonElement? =
        call("GET", "activity/submit/homework/list", token) {
            api.get(
                path = "activity/submit/homework/list",
                params = buildJsonObject {
                    put("pageNum", JsonPrimitive(page))
                    put("pageSize", JsonPrimitive(pageSize))
                },
                token = token,
            )
        }

    suspend fun signCount(token: String, activityId: Long): JsonElement? =
        call("GET", "activity/sign/count", token, "id=$activityId") {
            api.get(
                path = "activity/sign/count",
                params = buildJsonObject { put("id", JsonPrimitive(activityId)) },
                token = token,
            )
        }

    /** 查看作业：官方 `#/assignmentinfor/:id` 页面用的就是这个接口。 */
    suspend fun workDetail(token: String, activityId: Long): JsonElement? =
        call("GET", "activity/query/work_3_0_1", token, "activityId=$activityId") {
            api.get(
                path = "activity/query/work_3_0_1",
                params = buildJsonObject { put("activityId", JsonPrimitive(activityId)) },
                token = token,
            )
        }

    /** 同上，但把 `data` 解析成领域对象。 */
    suspend fun work(token: String, activityId: Long): ActivityWork? {
        val response = workDetail(token, activityId) ?: return null
        val data = runCatching { (response as JsonObject)["data"] }.getOrNull()
        return parseWork(data)
    }

    /**
     * 提交 / 修改作业。官方 `POST activity/submit/work_3_0_1`，body 为
     * `{activityId, introduce, attachment[]}`；`attachment` 元素形状与详情里的附件一致。
     *
     * 写操作：调用前应有用户确认。返回码非 0 直接抛异常。
     */
    suspend fun submitWork(
        token: String,
        activityId: Long,
        introduce: String,
        attachments: List<ActivityAttachment>,
    ) {
        val files = kotlinx.serialization.json.buildJsonArray {
            attachments.forEach { attachment ->
                add(
                    buildJsonObject {
                        put("id", JsonPrimitive(0))
                        put("url", JsonPrimitive(attachment.url))
                        put("name", JsonPrimitive(attachment.name))
                        put("size", JsonPrimitive(attachment.size))
                        put("type", JsonPrimitive(attachment.type))
                    },
                )
            }
        }
        val response = call(
            method = "POST",
            path = "activity/submit/work_3_0_1",
            token = token,
            extra = "activityId=$activityId chars=${introduce.length} files=${attachments.size}",
        ) {
            api.post(
                path = "activity/submit/work_3_0_1",
                params = buildJsonObject {
                    put("activityId", JsonPrimitive(activityId))
                    put("introduce", JsonPrimitive(introduce))
                    put("attachment", files)
                },
                token = token,
            )
        } ?: error("提交失败：没有响应")

        val code = response.code()
        if (code != 0) error("提交失败：code=$code")
    }

    // ---- 写操作：命名与日志就绪，但一处都不许接线 ----

    suspend fun enrollPerson(token: String, activityId: Long, personMaterial: String? = null): JsonElement? =
        call("POST", "activity/enroll/person", token, "id=$activityId") {
            api.post(
                path = "activity/enroll/person",
                params = buildJsonObject {
                    put("id", JsonPrimitive(activityId))
                    if (!personMaterial.isNullOrBlank()) put("personMaterial", JsonPrimitive(personMaterial))
                },
                token = token,
            )
        }

    suspend fun enrollTeam(
        token: String,
        activityId: Long,
        teamName: String,
        teamAvatar: String,
        teamMembers: String,
        teamMaterial: String? = null,
    ): JsonElement? = call("POST", "activity/enroll/team", token, "id=$activityId team=$teamName") {
        val body = buildJsonObject {
            put("id", JsonPrimitive(activityId))
            put("teamName", JsonPrimitive(teamName))
            put("teamAvatar", JsonPrimitive(teamAvatar))
            put("teamMembers", JsonPrimitive(teamMembers))
            if (teamMaterial != null) put("teamMaterial", JsonPrimitive(teamMaterial))
        }
        api.post(path = "activity/enroll/team", params = body, token = token)
    }

    suspend fun cancelEnroll(token: String, activityId: Long): JsonElement? =
        call("POST", "activity/enroll/cancel/person", token, "id=$activityId") {
            api.post(
                path = "activity/enroll/cancel/person",
                params = buildJsonObject { put("id", JsonPrimitive(activityId)) },
                token = token,
            )
        }

    suspend fun signRepair(token: String, activityId: Long, userIds: List<Long>): JsonElement? =
        call("POST", "activity/sign/repair", token, "activityId=$activityId users=${userIds.size}") {
            val users = userIds.joinToString(",") { it.toString() }
            api.post(
                path = "activity/sign/repair",
                params = buildJsonObject {
                    put("activityId", JsonPrimitive(activityId))
                    put("userIds", JsonPrimitive(users))
                },
                token = token,
            )
        }

    /**
     * 统一出口：先记请求，再记原始响应或异常。
     * 不把结果映射成业务对象，避免「看起来成功了」的错觉——判断留给实测的人。
     */
    private suspend fun call(
        method: String,
        path: String,
        token: String,
        extra: String = "",
        block: suspend () -> JsonElement,
    ): JsonElement? {
        AppLog.d("action", "→ $method $path $extra")
        return runCatching { block() }
            .onSuccess { response ->
                AppLog.d("action", "← $path code=${response.code()} $response")
            }
            .onFailure { error ->
                AppLog.d("action", "✗ $path ${error.javaClass.simpleName}: ${error.message}")
            }
            .getOrNull()
    }

    private fun JsonElement.code(): Int =
        runCatching { (this as JsonObject)["code"]?.jsonPrimitive?.content?.toIntOrNull() }.getOrNull() ?: -1
}
