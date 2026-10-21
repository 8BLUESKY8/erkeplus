package gitee.leolin_me.erkepp.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID

/** 设备标识：官方接口要求固定 device 参数，首次安装生成后保持不变。 */
object DeviceId {
    private const val PREFS = "erkepp_device"
    private const val KEY = "device_id"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val value = UUID.randomUUID().toString().uppercase()
        prefs.edit().putString(KEY, value).apply()
        return value
    }
}

/**
 * 会话持久化。token 与个人信息加密后写入 SharedPreferences，
 * 明文只在内存中出现；退出登录即清除。
 */
class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("erkepp_session", Context.MODE_PRIVATE)
    private val json = Json

    fun load(): Session? {
        val encrypted = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { decode(Crypto.decrypt(encrypted)) }.getOrNull()
    }

    fun save(session: Session) {
        prefs.edit().putString(KEY_SESSION, Crypto.encrypt(encode(session))).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encode(session: Session): String {
        val profile = buildJsonObject {
            put("name", JsonPrimitive(session.profile.name))
            put("studentNo", JsonPrimitive(session.profile.studentNo))
            put("college", JsonPrimitive(session.profile.college))
            put("major", JsonPrimitive(session.profile.major))
            put("credit", JsonPrimitive(session.profile.credit))
            put("hours", JsonPrimitive(session.profile.hours))
            put("userId", JsonPrimitive(session.profile.userId))
        }
        val root = buildJsonObject {
            put("token", JsonPrimitive(session.token))
            put("schoolCode", JsonPrimitive(session.school.code))
            put("schoolName", JsonPrimitive(session.school.name))
            put("rawProfile", JsonPrimitive(session.rawProfile))
            put("profile", profile)
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun decode(text: String): Session {
        val root = json.parseToJsonElement(text).jsonObject
        val profile = root["profile"]!!.jsonObject
        fun text(key: String): String = profile[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        return Session(
            token = root["token"]!!.jsonPrimitive.content,
            school = School(
                code = root["schoolCode"]!!.jsonPrimitive.content,
                name = root["schoolName"]!!.jsonPrimitive.content,
            ),
            rawProfile = root["rawProfile"]?.jsonPrimitive?.contentOrNull ?: "{}",
            profile = UserProfile(
                name = text("name"),
                studentNo = text("studentNo"),
                college = text("college"),
                major = text("major"),
                credit = profile["credit"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                hours = profile["hours"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                userId = profile["userId"]?.jsonPrimitive?.longOrNull ?: 0L,
            ),
        )
    }

    private companion object {
        const val KEY_SESSION = "session"
    }
}
