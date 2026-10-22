package gitee.leolin_me.erkepp.data

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 活动分享：链接与二维码。
 *
 * 官方「活动二维码」的内容是前端拼出来的分享链接（`Encrypts` 是恒等函数）：
 *
 * ```
 * https://win.9xueqi.com/?sourceName=schActivityCode@Xj&activityid=<id>
 * ```
 *
 * 别人扫这个码会打开官方活动详情页去报名。
 */
object ActivityShare {

    private const val HOST = "https://win.9xueqi.com"
    private const val SOURCE = "schActivityCode@Xj"

    fun shareUrl(activityId: Long): String = "$HOST/?sourceName=$SOURCE&activityid=$activityId"

    /** 生成二维码位图；内容为空或编码失败返回 null。 */
    fun qrBitmap(text: String, sizePx: Int = 640): Bitmap? {
        if (text.isBlank() || sizePx <= 0) return null
        return runCatching {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val pixels = IntArray(matrix.width * matrix.height)
            for (y in 0 until matrix.height) {
                val offset = y * matrix.width
                for (x in 0 until matrix.width) {
                    pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
                .apply { setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height) }
        }.getOrNull()
    }
}
