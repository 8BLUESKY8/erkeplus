package gitee.leolin_me.erkepp.data

import gitee.leolin_me.erkepp.AppLog
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 写操作的统一结果：直接把 `code` / `msg` 交给界面，不在客户端猜测业务含义。 */
data class ActionOutcome(val success: Boolean, val code: Int, val message: String)

/** 签到沿用旧名字，避免调用方大改。 */
typealias SignOutcome = ActionOutcome

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

    /**
     * 签到 / 签退：官方 `POST activity/sign/in-out`。
     *
     * 请求体严格照官方扫码回调拼（`window.BackScan`）：
     * - `type=1`（waitSign 码）：`{id, userId, type, sp}`
     * - `type=2`（活动码）：`{id, userId, type, sp, signSwitch}`
     *
     * `userId` 用**二维码里的值**原样转发（官方也是转发的，不是当前登录用户）；
     * `sp` 原样转发，静态码是空串。服务端怎么校验由它决定，客户端不自作聪明。
     *
     * 这是写操作，只允许由用户主动扫码后触发。
     */
    suspend fun sign(token: String, ticket: SignTicket): SignOutcome {
        val params = buildJsonObject {
            put("id", JsonPrimitive(ticket.activityId))
            put("userId", JsonPrimitive(ticket.userId))
            put("type", JsonPrimitive(ticket.type))
            put("sp", JsonPrimitive(ticket.sp))
            ticket.signSwitch?.let { put("signSwitch", JsonPrimitive(it)) }
        }
        val response = call(
            method = "POST",
            path = "activity/sign/in-out",
            token = token,
            extra = "id=${ticket.activityId} type=${ticket.type} signSwitch=${ticket.signSwitch} sp=${ticket.sp}",
        ) {
            api.post(path = "activity/sign/in-out", params = params, token = token)
        } ?: return SignOutcome(false, -1, "没有响应，请检查网络")

        val code = response.code()
        val message = runCatching {
            (response as JsonObject)["msg"]?.jsonPrimitive?.content
        }.getOrNull().orEmpty()
        return SignOutcome(
            success = code == 0,
            code = code,
            message = message.ifBlank { if (code == 0) "成功" else "失败（code=$code）" },
        )
    }

    /** 我的签到签退记录：官方 `activity/my-sign-in-out-list {pageNum,pageSize,name}`。 */
    suspend fun mySignRecords(token: String, page: Int = 1, pageSize: Int = 20): JsonElement? =
        call("GET", "activity/my-sign-in-out-list", token, "page=$page size=$pageSize") {
            api.get(
                path = "activity/my-sign-in-out-list",
                params = buildJsonObject {
                    put("pageNum", JsonPrimitive(page))
                    put("pageSize", JsonPrimitive(pageSize))
                    put("name", JsonPrimitive(""))
                },
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

    // ---- 报名 / 取消报名：官方个人报名路径（团队报名暂不接）----

    /**
     * 个人报名：官方 `POST activity/enroll/person {id, personMaterial}`。
     *
     * `personMaterial` 是报名材料数组；没有材料的活动官方也传空数组，这里照做。
     * 需要填材料的活动（`enrollMaterial=1`）界面层会要求去官方页面，不调用这里。
     */
    suspend fun enrollPerson(token: String, activityId: Long): ActionOutcome {
        val response = call(
            method = "POST",
            path = "activity/enroll/person",
            token = token,
            extra = "id=$activityId",
        ) {
            api.post(
                path = "activity/enroll/person",
                params = buildJsonObject {
                    put("id", JsonPrimitive(activityId))
                    put("personMaterial", kotlinx.serialization.json.buildJsonArray { })
                },
                token = token,
            )
        } ?: return ActionOutcome(false, -1, "没有响应，请检查网络")
        return response.toActionOutcome()
    }

    /**
     * 取消报名：官方 `POST activity/enroll/cancel/person {id, applyInfo}`。
     *
     * 报名已通过时 `applyInfo`（理由）必填，提交后变成「取消审核」；
     * 未通过时官方仍会带这个字段，空串即可。距开始不足 1 小时的判断在界面层做。
     */
    suspend fun cancelEnroll(token: String, activityId: Long, applyInfo: String): ActionOutcome {
        val response = call(
            method = "POST",
            path = "activity/enroll/cancel/person",
            token = token,
            extra = "id=$activityId reason=${applyInfo.length}字",
        ) {
            api.post(
                path = "activity/enroll/cancel/person",
                params = buildJsonObject {
                    put("id", JsonPrimitive(activityId))
                    put("applyInfo", JsonPrimitive(applyInfo))
                },
                token = token,
            )
        } ?: return ActionOutcome(false, -1, "没有响应，请检查网络")
        return response.toActionOutcome()
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

    /** 把 `{code, msg}` 摊成界面能直接用的小对象。 */
    private fun JsonElement.toActionOutcome(): ActionOutcome {
        val code = code()
        val msg = runCatching {
            (this as JsonObject)["msg"]?.jsonPrimitive?.content
        }.getOrNull().orEmpty()
        return ActionOutcome(
            success = code == 0,
            code = code,
            message = msg.ifBlank { if (code == 0) "成功" else "失败（code=$code）" },
        )
    }
}
