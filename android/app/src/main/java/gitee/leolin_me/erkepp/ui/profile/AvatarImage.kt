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

/**
 * 预取头像字节，给排行榜这类长列表用。
 *
 * 只下载并落盘、不解码：滚动时 [AvatarImage] 直接读本地文件，不会在滑动中途等网络。
 * 已经在缓存里的 URL 会直接跳过，所以重复调用是安全的。
 */
suspend fun prefetchAvatars(context: android.content.Context, urls: List<String>) {
    if (urls.isEmpty()) return
    withContext(Dispatchers.IO) {
        for (url in urls) {
            if (url.isBlank()) continue
            runCatching { AvatarBytes.load(context, url) }
        }
    }
}

/** 头像字节的取用与缓存：同名 URL 只下载一次，换头像（URL 变）才会重新拉。 */
private object AvatarBytes {
    private const val MAX_BYTES = 8 * 1024 * 1024

    /**
     * 头像缓存的总上限。
     *
     * 排行榜一屏就是几十张、全校榜单一轮上百张，单个头像从几十 KB 到几 MB 不等。
     * 以前只写不删，实测攒到过 81MB；这里按「最近使用」回收，只留 24MB。
     */
    private const val CACHE_BYTES = 24L * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun dir(context: android.content.Context) = File(context.cacheDir, "avatar")

    fun load(context: android.content.Context, url: String): ByteArray? {
        val directory = dir(context)
        val file = File(directory, hash(url))
        if (file.isFile && file.length() in 1..MAX_BYTES) {
            val cached = runCatching { file.readBytes() }.getOrNull()
            if (cached != null) {
                // 命中就把时间戳往后挪，回收时按「最近用过」留
                runCatching { file.setLastModified(System.currentTimeMillis()) }
                return cached
            }
        }
        val bytes = runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes()
            }
        }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        runCatching {
            directory.mkdirs()
            file.writeBytes(bytes)
        }
        trim(directory)
        return bytes
    }

    private fun hash(url: String): String =
        MessageDigest.getInstance("MD5").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

    /**
     * 把目录收回 [CACHE_BYTES] 以内，按最后修改时间从旧到新删。
     *
     * 一个头像文件就是一张图的原始字节（动图也在这里，不能只留第一帧），所以这里
     * 只按总量回收，不按单文件大小挑。
     */
    fun trim(directory: File) {
        val files = directory.listFiles() ?: return
        val total = files.sumOf { it.length() }
        if (total <= CACHE_BYTES) return
        var left = total
        for (file in files.sortedBy { it.lastModified() }) {
            if (left <= CACHE_BYTES) break
            left -= file.length()
            runCatching { file.delete() }
        }
        AppLog.d("avatar", "trim cache files=${files.size} before=${total / 1024}KB")
    }
}

/**
 * 启动时收一次头像缓存。
 *
 * 老版本没有上限，可能已经攒了几十兆；升级后第一次启动就该收回到 [CACHE_BYTES] 左右，
 * 不用等用户再打开排行榜。
 */
fun trimAvatarCache(context: android.content.Context) {
    AvatarBytes.trim(File(context.cacheDir, "avatar"))
}
