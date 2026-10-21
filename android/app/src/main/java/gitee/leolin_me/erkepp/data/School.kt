package gitee.leolin_me.erkepp.data

/**
 * 学校。当前只在校内使用，因此登录页不提供选择界面；
 * 若将来拓展到其他学校，在此登记后由登录页按需切换即可，接口层无需改动。
 *
 * 校徽地址来自官方学校档案接口 `/common/all-school` 的 `logo` 字段。
 */
data class School(
    val code: String,
    val name: String,
    val logoUrl: String? = null,
) {
    companion object {
        val AUST = School(code = "10361", name = "安徽理工大学")

        val all: List<School> = listOf(AUST)

        val default: School = AUST
    }
}
