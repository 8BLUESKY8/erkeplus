package gitee.leolin_me.erkepp.data

import android.content.Context

/** 轻量的本地偏好设置（与登录态分开存放）。 */
class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("erkepp_prefs", Context.MODE_PRIVATE)

    /** 是否已确认首次启动的免责声明。 */
    var disclaimerAccepted: Boolean
        get() = prefs.getBoolean(KEY_DISCLAIMER, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DISCLAIMER, value).apply()
        }

    /** 发现页是否显示分类筛选条。 */
    var categoriesEnabled: Boolean
        get() = prefs.getBoolean(KEY_CATEGORIES_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CATEGORIES_ENABLED, value).apply()
        }

    /** 卡片上是否显示分类文字。 */
    var showCategoryTags: Boolean
        get() = prefs.getBoolean(KEY_CATEGORY_TAGS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CATEGORY_TAGS, value).apply()
        }

    /** 用户在设置里勾选的分类入口；空集合表示不限制。 */
    var selectedCategoryIds: Set<Int>
        get() = prefs.getString(KEY_SELECTED_CATEGORIES, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: DEFAULT_CATEGORIES

        set(value) {
            prefs.edit().putString(KEY_SELECTED_CATEGORIES, value.joinToString(",")).apply()
        }

    /** 屏蔽关键词原文（换行/逗号分隔）。 */
    var blockedKeywords: String
        get() = prefs.getString(KEY_BLOCKED_KEYWORDS, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_BLOCKED_KEYWORDS, value).apply()
        }

    /** 屏蔽正则原文（换行/逗号分隔）。 */
    var blockedRegexes: String
        get() = prefs.getString(KEY_BLOCKED_REGEXES, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_BLOCKED_REGEXES, value).apply()
        }

    /** 是否提前预加载下一页。 */
    var preloadEnabled: Boolean
        get() = prefs.getBoolean(KEY_PRELOAD, true)
        set(value) {
            prefs.edit().putBoolean(KEY_PRELOAD, value).apply()
        }

    private companion object {
        const val KEY_DISCLAIMER = "disclaimer_accepted_v1"
        const val KEY_CATEGORIES_ENABLED = "categories_enabled"
        const val KEY_CATEGORY_TAGS = "category_tags"
        const val KEY_SELECTED_CATEGORIES = "selected_categories"
        const val KEY_BLOCKED_KEYWORDS = "blocked_keywords"
        const val KEY_BLOCKED_REGEXES = "blocked_regexes"
        const val KEY_PRELOAD = "preload_enabled"

        val DEFAULT_CATEGORIES = setOf(1440, 1441, 1444)
    }
}
