package gitee.leolin_me.erkepp.data

/**
 * 版本号解析与比较。
 *
 * 只处理本项目会用到的形式：`v0.2.0`、`0.2.0`、`0.2`、`0.2.0-debug`，
 * 前三段数字按语义化版本比较，`-` 之后的后缀直接忽略。
 * 返回 null 表示这段文本不是版本号（比如 tag 写成 `release-2026`）。
 */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {

    override fun compareTo(other: Version): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("^[vV]?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:[-+].*)?$")

        fun parse(text: String): Version? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            val match = pattern.find(trimmed) ?: return null
            val (major, minor, patch) = match.destructured
            return Version(
                major = major.toIntOrNull() ?: return null,
                minor = minor.toIntOrNull() ?: 0,
                patch = patch.toIntOrNull() ?: 0,
            )
        }
    }
}
