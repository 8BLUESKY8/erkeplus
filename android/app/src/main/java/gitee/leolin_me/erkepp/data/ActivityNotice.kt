package gitee.leolin_me.erkepp.data

/** 活动公告。官方 `activity/noticeList` 的 `data.list` 元素。 */
data class ActivityNotice(
    val id: Long,
    val title: String,
    val content: String,
    /** 发布时间文本，官方没给标准字段名时留空。 */
    val time: String,
    /** 官方 `isPopup`：1 表示进入详情页时要弹窗提醒。 */
    val isPopup: Boolean,
) {
    /** 弹窗里用的纯文本：去掉 HTML 标签，压缩多余空白。 */
    val plainText: String
        get() = content
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("[ \\t]+"), " ")
            .trim()
}
