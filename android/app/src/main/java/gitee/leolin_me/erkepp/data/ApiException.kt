package gitee.leolin_me.erkepp.data

class ApiException(val code: Int, message: String) : Exception(message)

/** 服务端业务错误的统一提示。 */
fun Throwable.toUserMessage(): String = when (this) {
    is ApiException -> when (code) {
        10001 -> "登录状态已失效，请重新登录"
        30005, 30006, 30007 -> message ?: "账号需要先在官方网页完成绑定"
        else -> message?.takeIf { it.isNotBlank() } ?: "请求失败（$code）"
    }

    is java.io.IOException -> "网络连接失败，请检查网络后重试"
    else -> "出了点问题，请稍后重试"
}
