package gitee.leolin_me.erkepp.data

/**
 * 标题屏蔽规则。
 *
 * 关键词按子串匹配（忽略大小写），正则按 [Regex.containsMatchIn]。
 * 非法正则在解析阶段被丢弃，不会让页面崩溃。
 */
class BlockRules(
    private val keywords: List<String>,
    private val patterns: List<Regex>,
) {
    val keywordCount: Int get() = keywords.size
    val patternCount: Int get() = patterns.size
    val isEmpty: Boolean get() = keywords.isEmpty() && patterns.isEmpty()

    fun isBlocked(title: String): Boolean {
        if (isEmpty || title.isBlank()) return false
        return keywords.any { title.contains(it, ignoreCase = true) } ||
            patterns.any { it.containsMatchIn(title) }
    }

    companion object {
        private val separators = Regex("[,，、;；\\n\\r]+")

        fun parse(keywordText: String, regexText: String): BlockRules {
            val keywords = keywordText.split(separators)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val patterns = regexText.split(separators)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { text -> runCatching { Regex(text) }.getOrNull() }
                .distinct()
            return BlockRules(keywords, patterns)
        }
    }
}
