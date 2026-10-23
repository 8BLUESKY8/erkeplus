package gitee.leolin_me.erkepp.data

/**
 * 活动阶段（用户视角的「这条活动现在能不能报名」）。
 *
 * 官方把整个生命周期塞在 `activityStatus` 一个数字里。实测这个数字并不严格对应时间：
 *
 * | status | 实测阶段的例子 |
 * | --- | --- |
 * | 1 | 报名期（武术与民族传统武术展演，报名 10-22 起） |
 * | 3 | 报名期（21天单词打卡，报名 10-23 09:00 起） |
 * | 4 | 待报名（2026级外国语新生迎新破冰活动，报名 10-24 00:00 起） |
 * | 5 | 报名期 |
 * | 6 | 报名已截止、活动未开始 |
 * | 7 | 进行中 |
 * | 8 | 已结束 |
 *
 * 报名期同时存在 1、3、5 三种数字，说明它混进了新旧两套流程的编号。因此阶段**优先按四个
 * 时间点算**，只有时间全缺时才退回数字。这样官方再调整编号也不会把「报名中」标成空白。
 */
enum class ActivityPhase {
    /** 报名还没开始。 */
    PENDING_ENROLL,

    /** 报名进行中。 */
    ENROLLING,

    /** 报名已截止，活动还没开始。 */
    STARTING,

    /** 活动进行中。 */
    RUNNING,

    /** 活动已结束。 */
    FINISHED,

    /** 时间缺失且数字也认不出来。 */
    UNKNOWN,
}

/**
 * 按时间算阶段。任一时间传 0 表示未知，未知的边界不参与判断。
 *
 * 判定顺序：活动是否已开始 → 报名是否已截止 → 报名是否还没开始 → 全缺时看数字。
 */
fun phaseOf(
    enrollStartTime: Long,
    enrollEndTime: Long,
    startTime: Long,
    endTime: Long,
    status: Int = 0,
    now: Long = System.currentTimeMillis(),
): ActivityPhase {
    if (startTime > 0 && now >= startTime) {
        return if (endTime > 0 && now > endTime) ActivityPhase.FINISHED else ActivityPhase.RUNNING
    }
    if (enrollEndTime > 0 && now > enrollEndTime) return ActivityPhase.STARTING
    if (enrollStartTime > 0 && now < enrollStartTime) return ActivityPhase.PENDING_ENROLL
    if (enrollStartTime <= 0 && enrollEndTime <= 0) return phaseFromStatus(status)
    return ActivityPhase.ENROLLING
}

/**
 * 时间全缺时的兜底。
 *
 * 1 和 3 在官方前端另有「审核中 / 被驳回」的含义（创建者视角），但它们同时也是
 * 公开列表里真实可报名的活动，所以这里不硬猜，直接给 [ActivityPhase.UNKNOWN]。
 */
fun phaseFromStatus(status: Int): ActivityPhase = when (status) {
    4 -> ActivityPhase.PENDING_ENROLL
    5 -> ActivityPhase.ENROLLING
    6 -> ActivityPhase.STARTING
    7 -> ActivityPhase.RUNNING
    8 -> ActivityPhase.FINISHED
    else -> ActivityPhase.UNKNOWN
}

/** 列表项的阶段。 */
fun ActivityItem.phase(now: Long = System.currentTimeMillis()): ActivityPhase =
    phaseOf(enrollStartTime, enrollEndTime, startTime, endTime, status, now)

/** 详情的阶段。 */
fun ActivityDetail.phase(now: Long = System.currentTimeMillis()): ActivityPhase =
    phaseOf(enrollStartTime, enrollEndTime, startTime, endTime, status, now)
