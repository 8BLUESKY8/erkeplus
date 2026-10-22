package gitee.leolin_me.erkepp.data

/**
 * 活动搜索：**纯本地匹配**。
 *
 * 官方列表接口 `/page/activity/list` 的 `search` 字段只对 `total` 生效，`list` 仍然返回
 * 未过滤的那一页（实测 `search=挑战杯` 时 `total=5`，但 `list` 是「21天单词打卡」这类
 * 无关活动）。官方 H5 的搜索也是前端做的：
 *
 * ```js
 * this.ActData.filter(i => i.name.indexOf(this.search) > -1)
 * ```
 *
 * 所以这里照做，但比 H5 多做两点：
 * 1. 除了标题，也匹配主办单位与分类名（搜「校团委」「志愿公益」同样有用）；
 * 2. 多个关键词按空格分隔，要求**全部**命中（AND），而不是整串字面匹配。
 */
object ActivitySearch {

    private val separators = charArrayOf(' ', '\u3000', '\t', '\n', ',')

    fun terms(text: String): List<String> =
        text.split(*separators).filter { it.isNotBlank() }

    fun matches(item: ActivityItem, terms: List<String>): Boolean {
        if (terms.isEmpty()) return true
        return terms.all { term -> haystack(item).any { it.contains(term, ignoreCase = true) } }
    }

    fun filter(items: List<ActivityItem>, terms: List<String>): List<ActivityItem> =
        if (terms.isEmpty()) items else items.filter { matches(it, terms) }

    private fun haystack(item: ActivityItem): List<String> = listOfNotNull(
        item.name,
        item.organizationName,
        item.classifyName,
        item.classifySecondName,
    )
}
