package gitee.leolin_me.erkepp.data

/**
 * 学校配置。当前只在校内使用，因此不提供选择界面；
 * 若将来拓展到其他学校，在此登记后由登录页按需切换即可，接口层无需改动。
 */
data class School(
    val code: String,
    val name: String,
) {
    companion object {
        val AUST = School(code = "10361", name = "安徽理工大学")

        val all: List<School> = listOf(AUST)

        val default: School = AUST
    }
}
