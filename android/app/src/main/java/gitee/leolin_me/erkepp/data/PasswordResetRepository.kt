package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 找回密码。接口与官方 H5 的 `#/resetPassword` 一致：
 * 先发短信验证码，再提交姓名、学号、手机号、验证码与新密码。
 */
class PasswordResetRepository(private val api: ApiClient) {

    suspend fun sendSmsCode(mobile: String) {
        val response = api.post(
            path = "common/captcha/mobile",
            params = buildJsonObject {
                put("countryCode", JsonPrimitive("86"))
                put("mobile", JsonPrimitive(mobile.trim()))
                put("type", JsonPrimitive(2))
            },
        )
        response.requireSuccess()
    }

    suspend fun resetPassword(
        school: School,
        name: String,
        studentNo: String,
        mobile: String,
        smsCode: String,
        password: String,
    ) {
        val response = api.post(
            path = "common/modify/pwd",
            params = buildJsonObject {
                put("mobile", JsonPrimitive(mobile.trim()))
                put("countryCode", JsonPrimitive(86))
                put("code", JsonPrimitive(smsCode.trim()))
                put("zhtxCode", JsonPrimitive(studentNo.trim()))
                put("userName", JsonPrimitive(name.trim()))
                put("password", JsonPrimitive(password))
                put("passwordRepeat", JsonPrimitive(password))
                put("schoolId", JsonPrimitive(school.code))
            },
        )
        response.requireSuccess()
    }
}
