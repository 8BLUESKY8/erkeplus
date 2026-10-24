package gitee.leolin_me.erkepp.ui.org

import gitee.leolin_me.erkepp.data.orgIdentityLabel
import gitee.leolin_me.erkepp.data.orgProcessLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 活跃值：整数就不带小数点，省得列表里全是 `.0`。 */
internal fun orgNumberText(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(Locale.US, "%.1f", value)

internal fun orgProcessText(code: String): String = orgProcessLabel(code)

internal fun orgIdentityText(identity: Int): String = orgIdentityLabel(identity)

/** 活动时间范围：跨天写全，同一天只写一次日期。 */
internal fun orgActivityTime(start: Long, end: Long): String {
    if (start <= 0 && end <= 0) return ""
    val day = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val clock = SimpleDateFormat("HH:mm", Locale.CHINA)
    return when {
        start <= 0 -> day.format(Date(end))
        end <= 0 -> day.format(Date(start))
        day.format(Date(start)) == day.format(Date(end)) ->
            "${day.format(Date(start))} ${clock.format(Date(start))}~${clock.format(Date(end))}"
        else -> "${day.format(Date(start))}~${day.format(Date(end))}"
    }
}
