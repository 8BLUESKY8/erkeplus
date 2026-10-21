package gitee.leolin_me.erkepp

/** 站内域名判定：只放行官方站点及其子域，避免被任意链接劫持。 */
object UrlPolicy {

    fun isAllowedHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return host == AppConfig.ALLOWED_HOST || host.endsWith(".${AppConfig.ALLOWED_HOST}")
    }
}
