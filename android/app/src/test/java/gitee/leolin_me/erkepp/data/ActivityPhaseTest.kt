package gitee.leolin_me.erkepp.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 阶段判定的边界：阶段是按时间算的，所以不依赖 2026 年这批活动的编号。
 * 时间都用「相对 now 的毫秒」写，任何一天跑都成立。
 */
class ActivityPhaseTest {

    private val hour = 60L * 60 * 1000
    private val day = 24 * hour
    private val now = 1_700_000_000_000L

    /** 全部参数都是绝对毫秒；0 表示未知。 */
    private fun at(
        enrollStart: Long = 0,
        enrollEnd: Long = 0,
        start: Long = 0,
        end: Long = 0,
        status: Int = 0,
    ) = phaseOf(enrollStart, enrollEnd, start, end, status, now)

    @Test
    fun `before enrollment starts`() {
        // 明天 0 点开始报名，活动在后天
        assertEquals(
            ActivityPhase.PENDING_ENROLL,
            at(enrollStart = now + day, enrollEnd = now + 2 * day, start = now + 3 * day, end = now + 3 * day + hour),
        )
    }

    @Test
    fun `inside enrollment window`() {
        assertEquals(
            ActivityPhase.ENROLLING,
            at(enrollStart = now - day, enrollEnd = now + day, start = now + 2 * day, end = now + 2 * day + hour),
        )
    }

    @Test
    fun `enrollment closed and activity not started`() {
        assertEquals(
            ActivityPhase.STARTING,
            at(enrollStart = now - 2 * day, enrollEnd = now - hour, start = now + day, end = now + day + hour),
        )
    }

    @Test
    fun `activity running`() {
        assertEquals(
            ActivityPhase.RUNNING,
            at(enrollStart = now - 3 * day, enrollEnd = now - 2 * day, start = now - hour, end = now + day),
        )
    }

    @Test
    fun `activity finished`() {
        assertEquals(
            ActivityPhase.FINISHED,
            at(enrollStart = now - 3 * day, enrollEnd = now - 2 * day, start = now - day, end = now - hour),
        )
    }

    @Test
    fun `enrollment start is inclusive`() {
        // 报名开始的那一刻就算报名中
        assertEquals(ActivityPhase.ENROLLING, at(enrollStart = now, enrollEnd = now + day, start = now + 2 * day))
    }

    @Test
    fun `activity start flips to running`() {
        assertEquals(ActivityPhase.RUNNING, at(start = now, end = now + day))
    }

    @Test
    fun `missing times fall back to status`() {
        assertEquals(ActivityPhase.PENDING_ENROLL, at(status = 4))
        assertEquals(ActivityPhase.ENROLLING, at(status = 5))
        assertEquals(ActivityPhase.STARTING, at(status = 6))
        assertEquals(ActivityPhase.RUNNING, at(status = 7))
        assertEquals(ActivityPhase.FINISHED, at(status = 8))
    }

    @Test
    fun `unknown status stays unknown instead of guessing`() {
        assertEquals(ActivityPhase.UNKNOWN, at(status = 0))
    }

    @Test
    fun `unapproved activities never report a phase`() {
        // 1 和 3 是未审核：官方页面底部就写「未审核」，学时也没结算
        assertEquals(ActivityPhase.UNKNOWN, at(status = 1))
        assertEquals(ActivityPhase.UNKNOWN, at(status = 3))
        // 哪怕报名时间已经开了也不给阶段
        assertEquals(
            ActivityPhase.UNKNOWN,
            at(enrollStart = now - hour, enrollEnd = now + day, start = now + 2 * day, status = 3),
        )
    }

    @Test
    fun `time wins over a stale status`() {
        // 状态还是 4（待报名），但报名时间已经开了：按时间算
        assertEquals(
            ActivityPhase.ENROLLING,
            at(enrollStart = now - hour, enrollEnd = now + day, start = now + 2 * day, status = 4),
        )
    }

    @Test
    fun `closed enrollment wins over status 5`() {
        assertEquals(
            ActivityPhase.STARTING,
            at(enrollStart = now - 3 * day, enrollEnd = now - hour, start = now + day, status = 5),
        )
    }
}
