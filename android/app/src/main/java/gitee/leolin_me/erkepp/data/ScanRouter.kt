package gitee.leolin_me.erkepp.data

/**
 * 扫码文本的分流。官方主页那个「扫一扫」同时兼做三件事：签到码、活动分享码、组织码。
 *
 * - 签到/签退：`qutuo://sign?...`、`qutuo://waitSign?...` → [parseSignCode]
 * - 活动分享码：`https://win.9xueqi.com/?sourceName=schActivityCode@Xj&activityid=574449`
 * - 组织码：文本里带 `joinOrganization`
 */
object ScanRouter {

    /** 从活动分享链接里取出 activityid；不是活动码返回 null。 */
    fun activityIdFromShareLink(raw: String): Long? {
        val text = raw.trim().substringBefore("?yiban=")
        // 只认「网页链接」或带 schActivityCode 的分享码：
        // 签到码里也有 activityId=，不能误判成分享码
        val looksLikeShare = text.contains("sourceName=schActivityCode", ignoreCase = true) ||
            (text.startsWith("http", ignoreCase = true) && text.contains("activityid=", ignoreCase = true))
        if (!looksLikeShare) return null
        // 参数名大小写不统一（activityid / activityId 都出现过）
        val value = text.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.substringBefore('=').trim().equals("activityid", ignoreCase = true) }
            ?.substringAfter('=', "")
            ?.trim()
            ?: return null
        return value.toLongOrNull()?.takeIf { it > 0 }
    }

    fun isOrganizationCode(raw: String): Boolean = raw.contains("joinOrganization")

    /** 识别不了时给用户的提示。 */
    fun unknownHint(): String = "无法识别的二维码"
}
