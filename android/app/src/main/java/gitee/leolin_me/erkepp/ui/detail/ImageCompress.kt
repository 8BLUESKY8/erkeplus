package gitee.leolin_me.erkepp.ui.detail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * 上传前压一下图。
 *
 * 官方限制单张 5MB，手机原图动辄 4~8MB。这里把最长边缩到 1600px、JPEG 质量 85，
 * 一张 4000px 的照片通常压到 300~600KB，字迹（集章卡那种）仍然看得清。
 * 压不小、或者根本不是图片，就原样返回，交给上层按 5MB 拦。
 */
fun compressForUpload(bytes: ByteArray, maxSide: Int = 1600, quality: Int = 85): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return bytes

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = runCatching {
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull() ?: return bytes

    val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        decoded
    }

    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    if (scaled !== decoded) scaled.recycle()
    decoded.recycle()

    val compressed = out.toByteArray()
    // 压完反而更大（小图常见）就用原图
    return if (compressed.size in 1 until bytes.size) compressed else bytes
}
