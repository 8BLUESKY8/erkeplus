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

    /** 简洁模式：不加载图片。 */
    var simpleMode: Boolean
        get() = prefs.getBoolean(KEY_SIMPLE_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SIMPLE_MODE, value).apply()
        }

    /** 卡片标题最多显示几行（1–3）。 */
    var titleLines: Int
        get() = prefs.getInt(KEY_TITLE_LINES, 2).coerceIn(1, 3)
        set(value) {
            prefs.edit().putInt(KEY_TITLE_LINES, value.coerceIn(1, 3)).apply()
        }

    // ---- 发现页筛选记忆 ----
    var filterStatus: String
        get() = prefs.getString(KEY_FILTER_STATUS, "5").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_FILTER_STATUS, value).apply()
        }

    var filterSort: Int
        get() = prefs.getInt(KEY_FILTER_SORT, 1)
        set(value) {
            prefs.edit().putInt(KEY_FILTER_SORT, value).apply()
        }

    var filterClassify: Int
        get() = prefs.getInt(KEY_FILTER_CLASSIFY, 0)
        set(value) {
            prefs.edit().putInt(KEY_FILTER_CLASSIFY, value).apply()
        }

    var filterEnrolled: Boolean
        get() = prefs.getBoolean(KEY_FILTER_ENROLLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FILTER_ENROLLED, value).apply()
        }

    var filterHoursMin: String
        get() = prefs.getString(KEY_FILTER_HOURS_MIN, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_FILTER_HOURS_MIN, value).apply()
        }

    var filterHoursMax: String
        get() = prefs.getString(KEY_FILTER_HOURS_MAX, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_FILTER_HOURS_MAX, value).apply()
        }

    // ---- 卡片显示内容 ----
    var cardShowHours: Boolean
        get() = prefs.getBoolean(KEY_CARD_HOURS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CARD_HOURS, value).apply()
        }

    var cardShowCount: Boolean
        get() = prefs.getBoolean(KEY_CARD_COUNT, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CARD_COUNT, value).apply()
        }

    var cardShowOrganization: Boolean
        get() = prefs.getBoolean(KEY_CARD_ORG, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CARD_ORG, value).apply()
        }

    /** 卡片附加的自定义文字，留空则不显示。 */
    var cardCustomText: String
        get() = prefs.getString(KEY_CARD_TEXT, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_CARD_TEXT, value).apply()
        }

    private companion object {
        const val KEY_DISCLAIMER = "disclaimer_accepted_v1"
        const val KEY_CATEGORIES_ENABLED = "categories_enabled"
        const val KEY_CATEGORY_TAGS = "category_tags"
        const val KEY_SELECTED_CATEGORIES = "selected_categories"
        const val KEY_BLOCKED_KEYWORDS = "blocked_keywords"
        const val KEY_BLOCKED_REGEXES = "blocked_regexes"
        const val KEY_PRELOAD = "preload_enabled"
        const val KEY_SIMPLE_MODE = "simple_mode"
        const val KEY_TITLE_LINES = "title_lines"
        const val KEY_FILTER_STATUS = "filter_status"
        const val KEY_FILTER_SORT = "filter_sort"
        const val KEY_FILTER_CLASSIFY = "filter_classify"
        const val KEY_FILTER_ENROLLED = "filter_enrolled"
        const val KEY_FILTER_HOURS_MIN = "filter_hours_min"
        const val KEY_FILTER_HOURS_MAX = "filter_hours_max"
        const val KEY_CARD_HOURS = "card_show_hours"
        const val KEY_CARD_COUNT = "card_show_count"
        const val KEY_CARD_ORG = "card_show_org"
        const val KEY_CARD_TEXT = "card_custom_text"

        val DEFAULT_CATEGORIES = setOf(1440, 1441, 1444)
    }
}
