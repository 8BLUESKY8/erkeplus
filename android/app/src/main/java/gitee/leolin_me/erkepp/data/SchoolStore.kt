package gitee.leolin_me.erkepp.data

import android.content.Context

/** 当前选中的学校。默认安徽理工大学，隐藏的选校页可以改。 */
class SchoolStore(context: Context) {

    private val prefs = context.getSharedPreferences("erkepp_school", Context.MODE_PRIVATE)

    fun load(): School {
        val code = prefs.getString(KEY_CODE, null) ?: return School.default
        val name = prefs.getString(KEY_NAME, null) ?: return School.default
        return School(
            code = code,
            name = name,
            logoUrl = prefs.getString(KEY_LOGO, null),
        )
    }

    fun save(school: School) {
        prefs.edit()
            .putString(KEY_CODE, school.code)
            .putString(KEY_NAME, school.name)
            .putString(KEY_LOGO, school.logoUrl)
            .apply()
    }

    private companion object {
        const val KEY_CODE = "code"
        const val KEY_NAME = "name"
        const val KEY_LOGO = "logo"
    }
}
