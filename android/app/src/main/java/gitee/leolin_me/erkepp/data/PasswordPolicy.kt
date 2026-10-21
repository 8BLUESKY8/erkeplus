package gitee.leolin_me.erkepp.data

/** 与官方找回密码页一致的口令强度规则。 */
object PasswordPolicy {

    private val RULE = Regex("""^\S*(?=\S{8,})(?=\S*\d)(?=\S*[A-Z])(?=\S*[a-z])(?=\S*[!@#$%^&*? ])\S*$""")

    const val HINT = "至少 8 位，需包含大写字母、小写字母、数字和特殊字符"

    fun isValid(password: String): Boolean = RULE.matches(password)
}
