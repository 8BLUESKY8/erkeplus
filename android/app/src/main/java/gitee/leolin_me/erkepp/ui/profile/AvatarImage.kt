package gitee.leolin_me.erkepp.ui.profile

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import gitee.leolin_me.erkepp.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 头像。
 *
 * 官方不限制头像的文件类型：实测用户上传的动图是 **GIF89a**，但落盘时被命名成 `.jpg`，
 * 服务端还回 `Content-Type: image/jpeg`。Coil 按 MIME 判断类型，会把它当静态图只画第一帧。
 *
 * 所以这里绕开 Coil，自己取字节、按**文件头**判断格式：
 * - Android 9+ 用 [ImageDecoder] 解成 `AnimatedImageDrawable`，手动 start 后循环播放
 * - 更老的系统只有静态位图，退化成第一帧
 *
 * 取到的字节按 URL 缓存在 `cacheDir/avatar/`，同一个头像只下载一次。
 */
@Composable
fun AvatarImage(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    var drawable by remember(url) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        val bytes = withContext(Dispatchers.IO) { AvatarBytes.load(context, url) } ?: return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) { decode(context, bytes) }
        AppLog.d("avatar", "loaded len=${bytes.size} animated=${decoded is AnimatedImageDrawable}")
        drawable = decoded
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val current = drawable
        if (current != null) {
            // 用 ImageView 画：AnimatedImageDrawable 在 ImageView 上会自动播放，
            // Compose 的 DrawablePainter 不会替我们 start()
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                },
                update = { view ->
                    view.setImageDrawable(current)
                    if (current is AnimatedImageDrawable && !current.isRunning) current.start()
                },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

private fun decode(context: android.content.Context, bytes: ByteArray): Drawable? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))).also { drawable ->
                if (drawable is AnimatedImageDrawable) {
                    drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    drawable.start()
                }
            }
        }.getOrNull()
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return BitmapDrawable(context.resources, bitmap)
}

/** 头像字节的取用与缓存：同名 URL 只下载一次，换头像（URL 变）才会重新拉。 */
private object AvatarBytes {

    private const val MAX_BYTES = 8 * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun load(context: android.content.Context, url: String): ByteArray? {
        val file = File(File(context.cacheDir, "avatar"), hash(url))
        if (file.isFile && file.length() in 1..MAX_BYTES) {
            runCatching { return file.readBytes() }
        }
        val bytes = runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes()
            }
        }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
        return bytes
    }

    private fun hash(url: String): String =
        MessageDigest.getInstance("MD5").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
}
