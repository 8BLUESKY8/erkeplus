package gitee.leolin_me.erkepp

/** 全应用共用常量。 */
object AppConfig {
    const val HOME_URL = "https://win.9xueqi.com/#/home"
    const val API_BASE_URL = "https://win.9xueqi.com/api/app/client/v1"
    const val ALLOWED_HOST = "win.9xueqi.com"

    /** 版本号直接取构建产物，避免和 build.gradle.kts 各自维护而写岔。 */
    val VERSION: String get() = BuildConfig.VERSION_NAME

    /** 官方 H5 的公共参数取值，改动前需重新实测。 */
    const val PLATFORM = "3"
    const val APP_VERSION = "2.0.5"
}
