package gitee.leolin_me.erkepp

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 轻量文件日志：写到应用私有目录 `files/logs/erkepp.log`。
 *
 * 写文件放在单独的后台线程，调用方（包括主线程）只做入队，不阻塞启动，也不卡滑动。
 * 超过 256KB 时轮转为 `erkepp.log.1`（轮转和格式化都在后台线程里做）。
 * 读取方式：`adb shell run-as <包名> cat files/logs/erkepp.log`
 */
object AppLog {

    private const val MAX_BYTES = 256 * 1024

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "erkepp-log").apply { isDaemon = true }
    }

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var baseDir: File? = null

    fun init(context: Context) {
        if (baseDir == null) baseDir = context.filesDir
    }

    fun d(tag: String, message: String) {
        // release 包不往 logcat 写：正式用户的手机不该被 adb 一插就能看到内部参数。
        // 文件日志保留，方便出问题时用「签到日志」导出。
        if (BuildConfig.DEBUG) Log.d(tag, message)
        if (baseDir == null) return
        executor.execute { runCatching { write(tag, message) } }
    }

    private fun write(tag: String, message: String) {
        val root = baseDir ?: return
        val dir = File(root, "logs").apply { mkdirs() }
        val file = File(dir, "erkepp.log")
        synchronized(this) {
            if (file.length() > MAX_BYTES) {
                file.renameTo(File(dir, "erkepp.log.1"))
            }
            file.appendText("${timeFormat.format(Date())} [$tag] $message\n")
        }
    }
}
