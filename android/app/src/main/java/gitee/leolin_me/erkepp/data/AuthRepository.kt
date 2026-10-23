package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class UserProfile(
    val name: String,
    val studentNo: String,
    val college: String,
    val major: String,
    val credit: Double,
    val hours: Double,
    val userId: Long = 0L,
    /** 年级，例如「2026级」；服务端没给就是空串。 */
    val grade: String = "",
    /** 班级名，例如「信息与计算科学26-1」。 */
    val className: String = "",
)

data class Session(
    val token: String,
    val profile: UserProfile,
    val school: School,
    /** 官方 my-info 原始 JSON，注入网页时作为 resv 使用。 */
    val rawProfile: String = "{}",
)

class AuthRepository(private val api: ApiClient) {

    suspend fun login(school: School, studentNo: String, password: String): Session {
        val response = api.post(
            path = "token",
            params = buildJsonObject {
                put("schoolCode", JsonPrimitive(school.code))
                put("code", JsonPrimitive(studentNo.trim()))
                put("password", JsonPrimitive(password))
                put("openId", JsonPrimitive(""))
            },
        )
        response.requireSuccess()
        val token = response.jsonObject["data"]?.jsonObject
            ?.get("access_token")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: throw ApiException(-1, "登录成功但未返回凭证")

        val (profile, raw) = fetchProfile(token)
        return Session(token = token, profile = profile, school = school, rawProfile = raw)
    }

    suspend fun fetchProfile(token: String): Pair<UserProfile, String> {
        val response = api.get("student/user/my-info", JsonObject(emptyMap()), token)
        response.requireSuccess()
        val data = response.jsonObject["data"]?.jsonObject ?: throw ApiException(-1, "个人信息为空")
        fun text(key: String): String = data[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        fun number(key: String): Double = data[key]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val profile = UserProfile(
            name = text("name"),
            studentNo = text("code"),
            college = text("collegeName"),
            major = text("majorName"),
            credit = number("credit"),
            hours = number("hours"),
            userId = data["id"]?.jsonPrimitive?.intOrNull?.toLong() ?: 0L,
            grade = text("grade"),
            className = text("className"),
        )
        return profile to data.toString()
    }
}
