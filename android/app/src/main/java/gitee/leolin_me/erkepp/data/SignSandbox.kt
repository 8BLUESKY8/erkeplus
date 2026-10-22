package gitee.leolin_me.erkepp.data

import android.content.Context
import gitee.leolin_me.erkepp.AppLog
import kotlinx.coroutines.delay

/**
 * 开发者模式：**离线签到沙盒**。
 *
 * 现场签到只有一次机会，所以先在本地把「详情 → 扫码 → 确认 → 提交 → 结果」整条链路走一遍。
 * 沙盒里的活动是本地拼出来的，提交走 [SandboxSubmit]，**一个字节都不会发到服务器**。
 */
data class SandboxConfig(
    val activityId: Long = 1L,
    val name: String = "模拟签到活动",
    /** 5 报名中 / 6 待开始 / 7 进行中 / 8 已结束。 */
    val status: Int = 7,
    /** 0 未开启 / 1 签到中 / 2 签退中。 */
    val signSwitch: Int = 1,
    /** 逗号分隔的字典 id：1 动态二维码、2 静态二维码、3 GPS定位。 */
    val signWay: String = "1",
    /** 1 签到+签退 / 2 签到 / 3 签退。 */
    val attendanceType: Int = 1,
    /** 2 = 报名已通过。 */
    val applyStatus: Int = 2,
    val isSigner: Int = 0,
    val signInCount: Int = 3,
    val signOutCount: Int = 1,
    /** 模拟提交的结果。 */
    val fail: Boolean = false,
    val failCode: Int = 10002,
    val failMsg: String = "二维码已失效",
) {
    /** 拼成官方 participant 详情那种形状，供详情页直接渲染。 */
    fun toDetail(): ActivityDetail {
        val now = System.currentTimeMillis()
        return ActivityDetail(
            id = activityId,
            name = name,
            cover = null,
            status = status,
            classifyName = "模拟分类",
            organizationName = "离线沙盒",
            organizationAvatar = null,
            hours = 2.0,
            credit = 0.0,
            hoursToCreditRatio = 0.0,
            startTime = now - 3_600_000L,
            endTime = now + 3_600_000L,
            enrollStartTime = now - 86_400_000L,
            enrollEndTime = now + 86_400_000L,
            address = "本地模拟地点",
            contact = "—",
            introduceHtml = "<p>这是离线沙盒活动，不会调用任何网络接口。用来验证扫码与签到界面。</p>",
            peopleLimit = 100,
            joinMemberCount = 12,
            genderLimit = 0,
            gradeNames = emptyList(),
            collegeNames = emptyList(),
            honestLimit = 0.0,
            attachments = emptyList(),
            managerName = "沙盒管理员",
            guidanceNames = emptyList(),
            isSubmitWork = 0,
            signAudit = 0,
            attendanceType = attendanceType,
            limitType = 0,
            applyStatus = applyStatus,
            identity = 1,
            isPostHomework = 0,
            signWay = signWay,
            signSwitch = signSwitch,
            isSigner = isSigner,
            signInCount = signInCount,
            signOutCount = signOutCount,
            source = "sandbox",
        )
    }
}

/** 沙盒配置的持久化：只有一个文件、几个键，不值得再套一层偏好框架。 */
object SignSandboxStore {

    private const val FILE = "sign_sandbox"

    fun load(context: Context): SandboxConfig {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val defaults = SandboxConfig()
        return SandboxConfig(
            activityId = prefs.getLong("activityId", defaults.activityId),
            name = prefs.getString("name", defaults.name).orEmpty(),
            status = prefs.getInt("status", defaults.status),
            signSwitch = prefs.getInt("signSwitch", defaults.signSwitch),
            signWay = prefs.getString("signWay", defaults.signWay).orEmpty(),
            attendanceType = prefs.getInt("attendanceType", defaults.attendanceType),
            applyStatus = prefs.getInt("applyStatus", defaults.applyStatus),
            isSigner = prefs.getInt("isSigner", defaults.isSigner),
            signInCount = prefs.getInt("signInCount", defaults.signInCount),
            signOutCount = prefs.getInt("signOutCount", defaults.signOutCount),
            fail = prefs.getBoolean("fail", defaults.fail),
            failCode = prefs.getInt("failCode", defaults.failCode),
            failMsg = prefs.getString("failMsg", defaults.failMsg).orEmpty(),
        )
    }

    fun save(context: Context, config: SandboxConfig) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong("activityId", config.activityId)
            .putString("name", config.name)
            .putInt("status", config.status)
            .putInt("signSwitch", config.signSwitch)
            .putString("signWay", config.signWay)
            .putInt("attendanceType", config.attendanceType)
            .putInt("applyStatus", config.applyStatus)
            .putInt("isSigner", config.isSigner)
            .putInt("signInCount", config.signInCount)
            .putInt("signOutCount", config.signOutCount)
            .putBoolean("fail", config.fail)
            .putInt("failCode", config.failCode)
            .putString("failMsg", config.failMsg)
            .apply()
    }
}

/**
 * 沙盒提交：造出与真实接口同形状的结果，并把整条链路写进日志（渠道 `[sign]`）。
 * **不发网络请求**。
 */
suspend fun sandboxSubmit(config: SandboxConfig, ticket: SignTicket): ActionOutcome {
    val started = System.currentTimeMillis()
    AppLog.d(
        "sign",
        "sandbox submit id=${ticket.activityId} type=${ticket.type} " +
            "signSwitch=${ticket.signSwitch} sp=${ticket.sp} userId=${ticket.userId} mode=${if (config.fail) "fail" else "ok"}",
    )
    // 沙盒也要对得上号：码里是 #1、配置成 #2 却“成功”，那这套测试就没有意义
    if (ticket.activityId != config.activityId) {
        val message = "这串码不属于当前配置的活动（码里是 #${ticket.activityId}，配置是 #${config.activityId}）"
        AppLog.d("sign", "sandbox reject: $message")
        return ActionOutcome(false, 10002, message)
    }
    delay(600)
    val elapsed = System.currentTimeMillis() - started
    val outcome = if (config.fail) {
        ActionOutcome(false, config.failCode, config.failMsg)
    } else {
        ActionOutcome(true, 0, "ok")
    }
    AppLog.d("sign", "sandbox submit result code=${outcome.code} msg=${outcome.message} elapsed=${elapsed}ms")
    return outcome
}
