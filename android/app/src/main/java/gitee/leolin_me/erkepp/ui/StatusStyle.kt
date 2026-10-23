package gitee.leolin_me.erkepp.ui

import androidx.compose.ui.graphics.Color
import gitee.leolin_me.erkepp.data.ActivityPhase

/** 阶段文案：发现页卡片角标、详情页标签共用。空串表示不显示标签。 */
fun phaseLabel(phase: ActivityPhase): String = when (phase) {
    ActivityPhase.PENDING_ENROLL -> "待报名"
    ActivityPhase.ENROLLING -> "报名中"
    ActivityPhase.STARTING -> "待开始"
    ActivityPhase.RUNNING -> "进行中"
    ActivityPhase.FINISHED -> "已结束"
    ActivityPhase.UNKNOWN -> ""
}

fun phaseColor(phase: ActivityPhase): Color = when (phase) {
    // 待报名用更暗一档的琥珀色，和「报名中」区分开
    ActivityPhase.PENDING_ENROLL -> Color(0xCCB45309)
    ActivityPhase.ENROLLING, ActivityPhase.STARTING -> Color(0xCCC2410C)
    ActivityPhase.RUNNING -> Color(0xCC047857)
    ActivityPhase.FINISHED -> Color(0xCC6B7280)
    ActivityPhase.UNKNOWN -> Color(0xCC000000)
}

/** 学时显示：整数不带小数点。 */
fun formatHours(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.1f", value)
