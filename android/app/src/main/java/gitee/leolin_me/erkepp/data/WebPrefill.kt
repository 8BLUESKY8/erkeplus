package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 注入官方网页的 localStorage 键值。
 *
 * 官方 H5 在登录成功后不只写 `Token`，还会写 `resv`、`isRegister`、`identity` 等一组键；
 * 只写 Token 会被路由守卫弹回登录页，所以这里按官方登录流程原样补齐。
 */
object WebPrefill {

    fun build(session: Session): Map<String, String> {
        val raw = runCatching { Json.parseToJsonElement(session.rawProfile).jsonObject }.getOrNull()
        fun rawText(key: String): String =
            raw?.get(key)?.jsonPrimitive?.contentOrNull.orEmpty()

        return buildMap {
            put("Token", session.token)
            put("resv", session.rawProfile)
            put("isRegister", rawText("status"))
            put("identity", rawText("identity"))
            put("avatar", rawText("avatar"))
            put("grade", rawText("grade"))
            put("hourUnit", rawText("hourUnit"))
            put("userId", session.profile.userId.toString())
            put("name", session.profile.name)
            put("stuCode", session.profile.studentNo)
            put("collegeName", session.profile.college)
            put("majorName", session.profile.major)
            put("schoolId", session.school.code)
            put("schoolName", session.school.name)
            put("schoolShortName", rawText("schoolShortName"))
        }
    }
}
