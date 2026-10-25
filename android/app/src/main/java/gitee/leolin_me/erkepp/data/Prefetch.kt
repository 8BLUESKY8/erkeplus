package gitee.leolin_me.erkepp.data

/**
 * 二级页的「预取」：手指点下去那一刻就把请求发出去，入场动画那 180ms 正好用来跑网络。
 *
 * 页面就位后先看这里有没有现成的数据，有就直接渲染——那 180ms 不是白等，
 * 也不会出现「转圈先转一会儿再出内容」。
 *
 * 只留最后一次、且有效期很短：详情页重开时不拿旧数据冒充新数据。
 */
object ActivityPrefetch {
    private const val TTL_MS = 10_000L

    private var id: Long = -1
    private var at: Long = 0L
    private var value: ActivityDetail? = null

    @Synchronized
    fun put(activityId: Long, detail: ActivityDetail) {
        id = activityId
        at = System.currentTimeMillis()
        value = detail
    }

    /** 取走即失效：同一份数据只喂给一次页面打开。 */
    @Synchronized
    fun take(activityId: Long): ActivityDetail? {
        val hit = value ?: return null
        if (id != activityId) return null
        if (System.currentTimeMillis() - at > TTL_MS) {
            value = null
            return null
        }
        value = null
        return hit
    }
}

object OrgDetailPrefetch {
    private const val TTL_MS = 10_000L

    private var id: Long = -1
    private var at: Long = 0L
    private var value: OrgDetail? = null

    @Synchronized
    fun put(organizationId: Long, detail: OrgDetail) {
        id = organizationId
        at = System.currentTimeMillis()
        value = detail
    }

    @Synchronized
    fun take(organizationId: Long): OrgDetail? {
        val hit = value ?: return null
        if (id != organizationId) return null
        if (System.currentTimeMillis() - at > TTL_MS) {
            value = null
            return null
        }
        value = null
        return hit
    }
}
