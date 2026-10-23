package gitee.leolin_me.erkepp.data

/**
 * 活动阶段（用户视角的「这条活动现在能不能报名」）。
 *
 * 官方把整个生命周期塞在 `activityStatus` 一个数字里，而学生端真正能看到的只有一部分：
 *
 * | status | 学生端看到的样子 | 样本 |
 * | --- | --- | --- |
 * | 1 | 未审核（底部按钮就是「未审核」，0 学时） | 572514 武术与民族传统武术展演 |
 * | 3 | 未审核 | 576140 21天单词打卡 |
 * | 4 | 待报名 | 576596 2026级外国语新生迎新破冰活动 |
 * | 5 | 报名中 | 566950 毕业生德育铸魂引领活动 |
 * | 6 | 报名已截止、活动未开始 | 575705 |
 * | 7 | 进行中 | 574792 |
 * | 8 | 已结束 | 569941 |
 *
 * 1 和 3 是没通过审核的活动：官方页面底部按钮写「未审核」，学时也还没结算（详情里 `hours`
 * 为 0，只有 `definedHours`）。它们只在按状态过滤列表时才会冒出来，学生端不展示，所以
 * 本类一律给 [ActivityPhase.UNKNOWN]，既不画角标也不给报名入口。
 *
 * 其余活动阶段按四个时间点算，时间缺失时才退回数字。这样官方再调整编号也不会
 * 把「报名中」标成空白。
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

    /** 未审核、时间缺失或编号认不出来。 */
    UNKNOWN,
}

/** 未审核（1）与被驳回（3）：学生端不可报名，也没有阶段。 */
private val UNAPPROVED_STATUS = setOf(1, 3)

/**
 * 按时间算阶段。任一时间传 0 表示未知，未知的边界不参与判断。
 *
 * 判定顺序：是否未审核 → 活动是否已开始 → 报名是否已截止 → 报名是否还没开始 → 全缺时看数字。
 */
fun phaseOf(
    enrollStartTime: Long,
    enrollEndTime: Long,
    startTime: Long,
    endTime: Long,
    status: Int = 0,
    now: Long = System.currentTimeMillis(),
): ActivityPhase {
    if (status in UNAPPROVED_STATUS) return ActivityPhase.UNKNOWN
    if (startTime > 0 && now >= startTime) {
        return if (endTime > 0 && now > endTime) ActivityPhase.FINISHED else ActivityPhase.RUNNING
    }
    if (enrollEndTime > 0 && now > enrollEndTime) return ActivityPhase.STARTING
    if (enrollStartTime > 0 && now < enrollStartTime) return ActivityPhase.PENDING_ENROLL
    if (enrollStartTime <= 0 && enrollEndTime <= 0) return phaseFromStatus(status)
    return ActivityPhase.ENROLLING
}

/** 时间全缺时的兜底。1、3 不给阶段，理由见类注释。 */
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
