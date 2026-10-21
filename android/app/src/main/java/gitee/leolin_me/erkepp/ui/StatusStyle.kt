package gitee.leolin_me.erkepp.ui

import androidx.compose.ui.graphics.Color

/** 活动状态的文字与配色，发现页卡片和详情页共用。 */
fun statusLabel(status: Int): String = when (status) {
    5 -> "报名中"
    6 -> "待开始"
    7 -> "进行中"
    8 -> "已结束"
    else -> ""
}

fun statusColor(status: Int): Color = when (status) {
    5, 6 -> Color(0xCCC2410C)
    7 -> Color(0xCC047857)
    8 -> Color(0xCC6B7280)
    else -> Color(0xCC000000)
}

/** 学时显示：整数不带小数点。 */
fun formatHours(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.1f", value)
