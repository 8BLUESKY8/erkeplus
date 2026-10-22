package gitee.leolin_me.erkepp.data

/**
 * 现场签到二维码的解析。**纯函数，可离线断言。**
 *
 * 官方生成的码是明文（bundle 里 `Encrypt` / `Decrypt` 都是恒等函数），三种形式：
 *
 * ```
 * qutuo://sign?activityId=566462&userId=1912797&sp=1790672679000&isSignIn=action   ← 签到
 * qutuo://sign?activityId=566462&userId=1912797&sp=1790672679000&isSignIn=         ← 签退
 * qutuo://waitSign?activityId=566462&userId=1912797&sp=1790672679000              ← 待签到（学生自己的码）
 * ```
 *
 * 易班/今日校园扫码后会追加 `?yiban=yiban_scan_result`。
 *
 * `sp` 是防截图的时效标记：动态码是服务端时间戳，每 10 秒 +10000；静态码是空串。
 * **这里原样转发，不做任何“补时间”处理**——服务端是否接受由它判定。
 *
 * 提交时的 `type` / `signSwitch` 完全对应官方 `window.BackScan` 的分支：
 *
 * | 码里的特征 | type | signSwitch |
 * | --- | --- | --- |
 * | `waitSign` | 1 | 不带 |
 * | `sign` + `action` | 2 | 1（签到） |
 * | `sign` 无 `action` | 2 | 2（签退） |
 */
data class SignTicket(
    val activityId: Long,
    /** 二维码里的 userId，原样转发；官方也是转发的。 */
    val userId: String,
    /** 防截图标记，静态二维码为空串。 */
    val sp: String,
    val type: Int,
    /** 仅 [type] == 2 时有值：1 签到、2 签退。 */
    val signSwitch: Int?,
    /** 原始文本，便于日志排查。 */
    val raw: String,
) {
    /** 这次扫码要做的动作，用于界面文案。 */
    val action: SignAction
        get() = when {
            type == 1 -> SignAction.CHECK_IN_OR_OUT
            signSwitch == 1 -> SignAction.CHECK_IN
            else -> SignAction.CHECK_OUT
        }
}

enum class SignAction(val label: String) {
    CHECK_IN("签到"),
    CHECK_OUT("签退"),
    CHECK_IN_OR_OUT("签到 / 签退"),
}

/** 解析扫码结果；不是签到码时返回 null（不抛异常，由界面给提示）。 */
fun parseSignCode(raw: String): SignTicket? {
    val text = raw.trim().substringBefore("?yiban=")
    if (text.isEmpty()) return null

    val isWaitSign = text.contains("waitSign")
    val isSign = text.contains("sign")
    if (!isWaitSign && !isSign) return null

    val query = text.substringAfter('?', "")
    if (query.isEmpty()) return null

    fun param(name: String): String? = query.split('&')
        .firstOrNull { it.substringBefore('=').trim() == name }
        ?.substringAfter('=', "")
        ?.trim()

    val activityId = param("activityId")?.toLongOrNull()?.takeIf { it > 0 } ?: return null
    val userId = param("userId")?.takeIf { it.isNotEmpty() } ?: return null
    val sp = param("sp").orEmpty()

    return if (isWaitSign) {
        SignTicket(activityId, userId, sp, type = 1, signSwitch = null, raw = text)
    } else {
        // 用 "action" 判断签到：官方就是 indexOf("action")
        val signSwitch = if (text.contains("action")) 1 else 2
        SignTicket(activityId, userId, sp, type = 2, signSwitch = signSwitch, raw = text)
    }
}
