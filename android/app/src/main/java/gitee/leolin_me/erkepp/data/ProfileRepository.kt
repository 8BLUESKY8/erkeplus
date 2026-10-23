package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * 详细个人资料。
 *
 * 来源是官方的 `student/user/my/info/mine`（「我的信息」页用的那个），比 `my-info` 多出
 * 手机号、民族、政治面貌、标签这些字段。字段含义见 `docs/api/win-9xueqi-api.md`。
 */
data class ProfileDetails(
    val name: String,
    val studentNo: String,
    /** 1 男、2 女、其它未知。 */
    val gender: Int,
    val ethnic: String,
    /** 民族列表里的下标 + 1，提交时用它。 */
    val ethnicId: Int,
    /** 0 群众、1 共青团员、2 中共党员、3 其他党派、4 中共预备党员。 */
    val politics: Int,
    /** 服务端给的面貌文案，可能为空，空了用 [politicsText] 兜底。 */
    val politicsName: String,
    val college: String,
    val major: String,
    val className: String,
    val grade: String,
    /** 可能是空串（没绑手机号）。 */
    val mobile: String,
    val avatar: String?,
    /** 标签 id 列表，接口里是 `"8,9,5"` 这种逗号串。 */
    val personTagIds: List<Int>,
) {
    val genderText: String
        get() = when (gender) {
            1 -> "男"
            2 -> "女"
            else -> "未设置"
        }

    val politicsText: String
        get() = politicsName.ifBlank {
            when (politics) {
                1 -> "共青团员"
                2 -> "中共党员"
                3 -> "其他党派"
                4 -> "中共预备党员"
                else -> "群众"
            }
        }

    /** 手机号脱敏：`195****6281` 这种。 */
    val maskedMobile: String
        get() = when {
            mobile.length < 7 -> mobile.ifBlank { "未绑定" }
            else -> mobile.substring(0, 3) + "****" + mobile.takeLast(4)
        }
}

/** 只读：详细资料。写操作见各阶段单独的方法。 */
class ProfileRepository(private val api: ApiClient) {

    suspend fun details(token: String): ProfileDetails {
        val response = api.get("student/user/my/info/mine", buildJsonObject { }, token)
        response.requireSuccess()
        val data = response.jsonObject["data"]?.jsonObject ?: throw ApiException(-1, "个人信息为空")
        return parse(data)
    }

    /**
     * 修改登录密码。
     *
     * 官方成功后会让当前凭证失效（H5 直接清 TOKEN 回登录页），调用方必须清本地会话。
     */
    suspend fun updatePassword(token: String, oldPwd: String, newPwd: String, repeat: String) {
        val response = api.post(
            path = "student/user/update-pwd",
            params = buildJsonObject {
                put("oldPwd", kotlinx.serialization.json.JsonPrimitive(oldPwd))
                put("newPwd", kotlinx.serialization.json.JsonPrimitive(newPwd))
                put("newPwdRepeat", kotlinx.serialization.json.JsonPrimitive(repeat))
            },
            token = token,
        )
        response.requireSuccess()
    }

    private fun parse(data: JsonObject): ProfileDetails {
        fun text(key: String): String = (data[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        fun int(key: String): Int = (data[key] as? JsonPrimitive)?.intOrNull ?: 0
        return ProfileDetails(
            name = text("name"),
            studentNo = text("code"),
            gender = int("gender"),
            ethnic = text("ethnic"),
            ethnicId = int("ethnicId"),
            politics = int("politics"),
            politicsName = text("politicsName"),
            college = text("collegeName"),
            major = text("majorName"),
            className = text("className"),
            grade = text("grade"),
            mobile = text("mobile"),
            avatar = text("avatar").takeIf { it.isNotBlank() },
            personTagIds = text("personTag")
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() },
        )
    }
}

/** 组装 `personTag` 提交值：`[8,9]` → `"8,9"`。 */
internal fun personTagValue(ids: List<Int>): String = ids.joinToString(",")
