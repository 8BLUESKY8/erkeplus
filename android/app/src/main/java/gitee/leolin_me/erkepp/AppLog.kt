package gitee.leolin_me.erkepp

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量文件日志：写到应用私有目录 `files/logs/erkepp.log`。
 *
 * 只在本地留存，不联网。超过 256KB 时轮转为 `erkepp.log.1`。
 * 读取方式：`adb shell run-as <包名> cat files/logs/erkepp.log`
 */
object AppLog {

    private const val MAX_BYTES = 256 * 1024
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        if (logFile != null) return
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        logFile = File(dir, "erkepp.log")
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        val file = logFile ?: return
        val line = "${timeFormat.format(Date())} [$tag] $message\n"
        runCatching {
            synchronized(this) {
                if (file.length() > MAX_BYTES) {
                    file.renameTo(File(file.parentFile, "erkepp.log.1"))
                }
                file.appendText(line)
            }
        }
    }
}
