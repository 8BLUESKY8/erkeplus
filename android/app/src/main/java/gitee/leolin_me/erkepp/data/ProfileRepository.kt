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

    /**
     * 更换手机号：先给**当前**手机号发验证码，再带着验证码与新号提交。
     *
     * 官方没有「新号也收一次码」的环节，`code` 是旧号收到的那个。
     */
    suspend fun sendMobileCaptcha(token: String, mobile: String) {
        val response = api.post(
            path = "common/captcha/mobile",
            params = buildJsonObject {
                put("countryCode", kotlinx.serialization.json.JsonPrimitive("86"))
                put("mobile", kotlinx.serialization.json.JsonPrimitive(mobile))
                put("type", kotlinx.serialization.json.JsonPrimitive(2))
            },
            token = token,
        )
        response.requireSuccess()
    }

    suspend fun updateMobile(token: String, oldMobile: String, code: String, newMobile: String) {
        val response = api.post(
            path = "student/user/update-mobile",
            params = buildJsonObject {
                put("oldMobile", kotlinx.serialization.json.JsonPrimitive(oldMobile))
                put("countryCode", kotlinx.serialization.json.JsonPrimitive(86))
                put("code", kotlinx.serialization.json.JsonPrimitive(code))
                put("newMobile", kotlinx.serialization.json.JsonPrimitive(newMobile))
            },
            token = token,
        )
        response.requireSuccess()
    }

    /** 民族文化列表：下标 + 1 就是提交用的 `ethnicId`（与官方 H5 的算法一致）。 */
    suspend fun nations(token: String): List<Pair<Int, String>> {
        val response = api.get("student/get/nation_list", buildJsonObject { }, token)
        response.requireSuccess()
        val array = response.jsonObject["data"] as? JsonArray ?: return emptyList()
        return array.mapIndexedNotNull { index, element ->
            val item = element as? JsonObject ?: return@mapIndexedNotNull null
            val name = (item["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapIndexedNotNull null
            (index + 1) to name
        }
    }

    /** 改性别：1 男、2 女。 */
    suspend fun updateGender(token: String, gender: Int) =
        updateField(token, "gender", kotlinx.serialization.json.JsonPrimitive(gender))

    /** 改政治面貌：0 群众、1 共青团员、2 中共党员、3 其他党派、4 中共预备党员。 */
    suspend fun updatePolitics(token: String, politics: Int) =
        updateField(token, "politics", kotlinx.serialization.json.JsonPrimitive(politics))

    /** 改民族：`ethnicId` 是民族列表下标 + 1。 */
    suspend fun updateEthnic(token: String, ethnicId: Int) =
        updateField(token, "ethnicId", kotlinx.serialization.json.JsonPrimitive(ethnicId))

    /** `update-info` 一次只认一个字段，所以统一走这里。 */
    private suspend fun updateField(token: String, key: String, value: kotlinx.serialization.json.JsonPrimitive) {
        val response = api.post(
            path = "student/user/update-info",
            params = buildJsonObject { put(key, value) },
            token = token,
        )
        response.requireSuccess()
    }

    /** 标签字典 `dict/user/tag`：`[{id, name}]`。 */
    suspend fun tagDictionary(token: String): List<Pair<Int, String>> {
        val response = api.get("dict/user/tag", buildJsonObject { }, token)
        response.requireSuccess()
        val array = response.jsonObject["data"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = (item["id"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val name = (item["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            id to name
        }
    }

    /** 保存标签：`personTag` 是逗号分隔的 id 串。 */
    suspend fun updateTags(token: String, ids: List<Int>) {
        val response = api.post(
            path = "student/user/update-person-tag",
            params = buildJsonObject {
                put("personTag", kotlinx.serialization.json.JsonPrimitive(personTagValue(ids)))
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

/** 更换手机号时的输入校验：大陆号码，1 开头、共 11 位。 */
fun isValidMobile(mobile: String): Boolean =
    mobile.length == 11 && mobile[0] == '1' && mobile.all { it.isDigit() }
