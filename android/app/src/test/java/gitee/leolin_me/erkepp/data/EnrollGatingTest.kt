package gitee.leolin_me.erkepp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 报名 / 取消报名的准入判断。
 *
 * 这些条件决定「按钮出不出现」，判断错了就是让用户白点、或者更糟——在不能报的时候给出报名入口。
 * 真正的限制（年级 / 学院 / 诚信值）由服务端判定，这里只覆盖客户端能确定的部分。
 */
class EnrollGatingTest {

    private val hour = 60 * 60 * 1000L
    private val now = System.currentTimeMillis()

    private fun detail(
        identity: Int = 0,
        status: Int = 5,
        applyStatus: Int = 0,
        enrollEndTime: Long = now + 2 * hour,
        startTime: Long = now + 48 * hour,
        peopleLimit: Int = 30,
        joinMemberCount: Int = 10,
        enrollMaterial: Int = 0,
        cancelStatus: String? = null,
    ) = ActivityDetail(
        id = 1L,
        name = "测试活动",
        cover = null,
        status = status,
        classifyName = "",
        organizationName = "",
        organizationAvatar = null,
        hours = 2.0,
        credit = 0.0,
        hoursToCreditRatio = 0.0,
        startTime = startTime,
        endTime = startTime + hour,
        enrollStartTime = now - 24 * hour,
        enrollEndTime = enrollEndTime,
        address = "",
        contact = "",
        introduceHtml = "",
        peopleLimit = peopleLimit,
        joinMemberCount = joinMemberCount,
        genderLimit = 0,
        gradeNames = emptyList(),
        collegeNames = emptyList(),
        honestLimit = 0.0,
        attachments = emptyList(),
        managerName = "",
        guidanceNames = emptyList(),
        isSubmitWork = 0,
        signAudit = 0,
        attendanceType = 1,
        limitType = 0,
        applyStatus = applyStatus,
        identity = identity,
        isPostHomework = 0,
        signWay = "1",
        signSwitch = 0,
        isSigner = 0,
        signInCount = 0,
        signOutCount = 0,
        enrollMaterial = enrollMaterial,
        cancelStatus = cancelStatus,
        source = "participant",
    )

    @Test
    fun `can enroll when open and not full`() {
        assertTrue(detail().canEnroll)
        assertEquals("", detail().enrollBlockedReason)
    }

    @Test
    fun `cannot enroll when already joined`() {
        assertFalse(detail(identity = 1, applyStatus = 2).canEnroll)
    }

    @Test
    fun `cannot enroll when full`() {
        val full = detail(peopleLimit = 30, joinMemberCount = 30)
        assertFalse(full.canEnroll)
        assertEquals("名额已满", full.enrollBlockedReason)
    }

    @Test
    fun `unlimited capacity never counts as full`() {
        assertTrue(detail(peopleLimit = 0, joinMemberCount = 999).canEnroll)
    }

    @Test
    fun `cannot enroll when deadline passed`() {
        val closed = detail(enrollEndTime = now - hour)
        assertFalse(closed.canEnroll)
        assertEquals("报名已截止", closed.enrollBlockedReason)
    }

    @Test
    fun `cannot enroll when status is not enrolling`() {
        val started = detail(status = 7)
        assertFalse(started.canEnroll)
        assertEquals("活动当前不在报名中", started.enrollBlockedReason)
    }

    @Test
    fun `activity with material form is not enrollable natively`() {
        val material = detail(enrollMaterial = 1)
        assertFalse(material.canEnroll)
        assertTrue(material.enrollBlockedReason.contains("报名材料"))
    }

    @Test
    fun `cancel allowed when activity is far away`() {
        assertTrue(detail(identity = 1, applyStatus = 2).canCancelEnroll)
    }

    @Test
    fun `cancel blocked within one hour of start`() {
        val soon = detail(identity = 1, applyStatus = 2, startTime = now + 30 * 60 * 1000L)
        assertFalse(soon.canCancelEnroll)
    }

    @Test
    fun `reason required only after approval`() {
        assertTrue(detail(identity = 1, applyStatus = 2).cancelNeedsReason)
        assertFalse(detail(identity = 1, applyStatus = 1).cancelNeedsReason)
    }

    @Test
    fun `cancel pending hides cancel entry`() {
        val pending = detail(identity = 1, applyStatus = 2, cancelStatus = "1")
        assertFalse(pending.canCancelEnroll)
        assertEquals("取消审核中", pending.applyStatusText)
    }

    @Test
    fun `apply status text`() {
        assertEquals("报名已通过", detail(identity = 1, applyStatus = 2).applyStatusText)
        assertEquals("已提交，等待审核", detail(identity = 1, applyStatus = 1).applyStatusText)
        assertEquals("已报名", detail(identity = 1, applyStatus = 3).applyStatusText)
    }
}
