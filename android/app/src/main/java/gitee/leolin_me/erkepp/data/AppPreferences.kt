package gitee.leolin_me.erkepp.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 轻量本地偏好设置（与登录态分开存放）。
 *
 * 每个字段本身就是 Compose 状态：界面直接读这个类，改了自动重组，写时自动落盘。
 * 只有一份数据，所以不需要在 Activity 里再镜像一份，也不会出现两边不同步。
 */
class AppPreferences(context: Context) {

    private val store = context.getSharedPreferences("erkepp_prefs", Context.MODE_PRIVATE)

    /** 是否已确认首次启动的免责声明。 */
    var disclaimerAccepted: Boolean by prefBoolean(KEY_DISCLAIMER, false)

    /** 发现页是否显示分类筛选条。 */
    var categoriesEnabled: Boolean by prefBoolean(KEY_CATEGORIES_ENABLED, true)

    /** 卡片上是否显示分类文字。 */
    var showCategoryTags: Boolean by prefBoolean(KEY_CATEGORY_TAGS, true)

    /** 用户在设置里勾选的分类入口；空集合表示不限制。 */
    var selectedCategoryIds: Set<Int> by prefCategories(KEY_SELECTED_CATEGORIES, DEFAULT_CATEGORIES)

    /** 屏蔽关键词原文（换行/逗号分隔）。 */
    var blockedKeywords: String by prefString(KEY_BLOCKED_KEYWORDS, "")

    /** 屏蔽正则原文（换行/逗号分隔）。 */
    var blockedRegexes: String by prefString(KEY_BLOCKED_REGEXES, "")

    /** 是否提前预加载下一页。 */
    var preloadEnabled: Boolean by prefBoolean(KEY_PRELOAD, true)

    /** 重新进入发现页时的行为，取值见 [ReenterBehavior]。 */
    var reenterBehavior: String by prefString(KEY_REENTER_BEHAVIOR, ReenterBehavior.KEEP)

    /** 卡片是否显示活动封面图。 */
    var showCover: Boolean by prefBoolean(KEY_SHOW_COVER, true)

    private var titleLinesState by mutableStateOf(store.getInt(KEY_TITLE_LINES, 2).coerceIn(1, 3))

    /** 卡片标题最多显示几行（1–3）。 */
    var titleLines: Int
        get() = titleLinesState
        set(value) {
            val clamped = value.coerceIn(1, 3)
            if (titleLinesState == clamped) return
            titleLinesState = clamped
            store.edit().putInt(KEY_TITLE_LINES, clamped).apply()
        }

    // ---- 发现页筛选记忆 ----
    var filterStatus: String by prefString(KEY_FILTER_STATUS, "5")
    var filterSort: Int by prefInt(KEY_FILTER_SORT, 1)
    var filterClassify: Int by prefInt(KEY_FILTER_CLASSIFY, 0)
    var filterEnrolled: Boolean by prefBoolean(KEY_FILTER_ENROLLED, false)
    var filterHoursMin: String by prefString(KEY_FILTER_HOURS_MIN, "")
    var filterHoursMax: String by prefString(KEY_FILTER_HOURS_MAX, "")

    // ---- 卡片显示内容 ----
    var cardShowHours: Boolean by prefBoolean(KEY_CARD_HOURS, true)
    var cardShowCount: Boolean by prefBoolean(KEY_CARD_COUNT, true)
    var cardShowOrganization: Boolean by prefBoolean(KEY_CARD_ORG, true)
    var cardCustomText: String by prefString(KEY_CARD_TEXT, "")

    /** 卡片是否显示时间参考（报名截止 / 活动开始 / 活动结束）。 */
    var cardShowTime: Boolean by prefBoolean(KEY_CARD_TIME, true)

    /** 开发者模式：显示加载计数等调试信息。 */
    var developerMode: Boolean by prefBoolean(KEY_DEVELOPER, false)

    /** 恢复默认的显示与筛选设置。不动登录态，也不动免责声明确认。 */
    fun resetDisplay() {
        categoriesEnabled = true
        showCategoryTags = true
        selectedCategoryIds = DEFAULT_CATEGORIES
        blockedKeywords = ""
        blockedRegexes = ""
        preloadEnabled = true
        showCover = true
        titleLines = 2
        cardShowHours = true
        cardShowCount = true
        cardShowOrganization = true
        cardCustomText = ""
        cardShowTime = true
        filterStatus = "5"
        filterSort = 1
        filterClassify = 0
        filterEnrolled = false
        filterHoursMin = ""
        filterHoursMax = ""
        reenterBehavior = ReenterBehavior.KEEP
    }

    private fun prefBoolean(key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        private var state by mutableStateOf(store.getBoolean(key, default))

        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = state

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            if (state == value) return
            state = value
            store.edit().putBoolean(key, value).apply()
        }
    }

    private fun prefInt(key: String, default: Int) = object : ReadWriteProperty<Any?, Int> {
        private var state by mutableStateOf(store.getInt(key, default))

        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = state

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (state == value) return
            state = value
            store.edit().putInt(key, value).apply()
        }
    }

    private fun prefString(key: String, default: String) = object : ReadWriteProperty<Any?, String> {
        private var state by mutableStateOf(store.getString(key, null) ?: default)

        override fun getValue(thisRef: Any?, property: KProperty<*>): String = state

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            if (state == value) return
            state = value
            store.edit().putString(key, value).apply()
        }
    }

    private fun prefCategories(key: String, default: Set<Int>) = object : ReadWriteProperty<Any?, Set<Int>> {
        private var state by mutableStateOf(
            store.getString(key, null)
                ?.split(',')
                ?.mapNotNull { it.toIntOrNull() }
                ?.toSet()
                ?: default,
        )

        override fun getValue(thisRef: Any?, property: KProperty<*>): Set<Int> = state

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Set<Int>) {
            if (state == value) return
            state = value
            store.edit().putString(key, value.joinToString(",")).apply()
        }
    }

    private companion object {
        const val KEY_DISCLAIMER = "disclaimer_accepted_v1"
        const val KEY_CATEGORIES_ENABLED = "categories_enabled"
        const val KEY_CATEGORY_TAGS = "category_tags"
        const val KEY_SELECTED_CATEGORIES = "selected_categories"
        const val KEY_BLOCKED_KEYWORDS = "blocked_keywords"
        const val KEY_BLOCKED_REGEXES = "blocked_regexes"
        const val KEY_PRELOAD = "preload_enabled"
        const val KEY_REENTER_BEHAVIOR = "reenter_behavior"
        const val KEY_SHOW_COVER = "show_cover"
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
        const val KEY_CARD_TIME = "card_show_time"
        const val KEY_CARD_TEXT = "card_custom_text"
        const val KEY_DEVELOPER = "developer_mode"

        val DEFAULT_CATEGORIES = setOf(1440, 1441, 1444)
    }
}

/** 重新进入（切回）发现页时的行为。 */
object ReenterBehavior {
    /** 保持离开时的位置，什么都不做。 */
    const val KEEP = "keep"

    /** 回到顶部。 */
    const val TOP = "top"

    /** 重新加载第一页。 */
    const val RELOAD = "reload"
}
