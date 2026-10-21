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

    private companion object {
        const val KEY_DISCLAIMER = "disclaimer_accepted_v1"
    }
}
