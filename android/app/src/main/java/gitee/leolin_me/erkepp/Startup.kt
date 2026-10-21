package gitee.leolin_me.erkepp

import android.os.Process
import android.os.SystemClock
import android.util.Log

/**
 * 冷启动分段计时，只在调试包里使用。
 *
 * 基准是本进程的启动时刻（[Process.getStartUptimeMillis]），所以每个标记都是
 * 「进程起来到现在过了多少毫秒」，可以直接看出哪一段最贵。
 * 读法：`adb logcat -s startup:D`
 */
object Startup {

    private val base: Long = Process.getStartUptimeMillis()

    fun mark(label: String) {
        if (!BuildConfig.DEBUG) return
        val elapsed = SystemClock.uptimeMillis() - base
        Log.d("startup", "$label +${elapsed}ms")
    }
}
