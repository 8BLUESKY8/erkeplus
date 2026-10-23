package gitee.leolin_me.erkepp.ui.profile

/**
 * 密码强度规则，与官方前端一致：至少 8 位，且同时含数字、大写字母、小写字母、特殊符号。
 *
 * 官方正则：`^\S*(?=\S{8,})(?=\S*\d)(?=\S*[A-Z])(?=\S*[a-z])(?=\S*[!@#$%^&*? ])\S*$`
 * 这里拆成逐条判断，好在界面上告诉用户差哪一条。
 */
object PasswordRule {

    const val MIN_LENGTH = 8

    private const val SPECIAL = "!@#$%^&*?"

    fun problems(password: String): List<String> = buildList {
        // 官方正则虽然把空格写进了特殊符号类，但整体用 \S*，所以空格实际不被接受
        if (password.any { it.isWhitespace() }) add("不能包含空格")
        if (password.length < MIN_LENGTH) add("至少 ${MIN_LENGTH} 位")
        if (password.none { it.isDigit() }) add("包含数字")
        if (password.none { it.isUpperCase() }) add("包含大写字母")
        if (password.none { it.isLowerCase() }) add("包含小写字母")
        if (password.none { it in SPECIAL }) add("包含特殊符号")
    }

    fun isValid(password: String): Boolean = problems(password).isEmpty()
}
