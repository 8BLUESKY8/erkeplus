package gitee.leolin_me.erkepp.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import gitee.leolin_me.erkepp.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 应用内下载并安装新版本。
 *
 * 流程：把 APK 下到 `cache/update/`，再交给系统安装器（FileProvider 授权的 `content://`）。
 * Android 8 以上安装第三方 APK 需要「安装未知应用」权限，没给就先把用户送到系统开关。
 */
object ApkUpdater {

    private const val MAX_BYTES = 80L * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** 没给「安装未知应用」权限时，需要先引导用户打开。 */
    fun needsInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { AppLog.d("update", "打开安装权限页失败 ${it.javaClass.simpleName}") }
    }

    /**
     * 下载 APK 到缓存目录，[onProgress] 给 0..1。返回下好的文件。
     * 下载失败不删除半成品，调用方可以重试（同名文件会被覆盖）。
     */
    suspend fun download(
        context: Context,
        url: String,
        versionName: String,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val target = File(dir, "erkepp-$versionName.apk")
        AppLog.d("update", "download start url=$url -> ${target.name}")

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("下载失败：HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("下载失败：内容为空")
            val total = body.contentLength()
            if (total > MAX_BYTES) throw IllegalStateException("安装包异常大（${total / 1024 / 1024} MB），已放弃")
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        read += count
                        if (read > MAX_BYTES) throw IllegalStateException("安装包超过 80 MB，已放弃")
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (target.length() <= 0L) throw IllegalStateException("下载的文件是空的")
        onProgress(1f)
        AppLog.d("update", "download done ${target.name} ${target.length()} bytes")
        target
    }

    /** 交给系统安装器；返回 false 表示没找到可处理的应用。 */
    fun install(context: Context, file: File): Boolean {
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        AppLog.d("update", "install ${file.name}")
        return runCatching { context.startActivity(intent) }
            .onFailure { AppLog.d("update", "调起安装器失败 ${it.javaClass.simpleName}") }
            .isSuccess
    }
}
