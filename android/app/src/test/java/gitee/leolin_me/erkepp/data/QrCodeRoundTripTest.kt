package gitee.leolin_me.erkepp.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 二维码「编码 → 解码 → 解析」闭环。
 *
 * 只验证算法链路（不碰相机）。相机那一层由真机扫码验证，用的正是同一个 [parseSignCode]。
 * PNG 测试图由 `.tools/make-qr.java` 生成（单元测试跑在 android.jar 上，没有 java.awt）。
 */
class QrCodeRoundTripTest {

    private val payloads = listOf(
        "check-in" to
            "qutuo://sign?activityId=566462&userId=1912797&sp=1790672679000&isSignIn=action",
        "check-out" to
            "qutuo://sign?activityId=566462&userId=1912797&sp=1790672679000&isSignIn=",
        "wait-sign" to
            "qutuo://waitSign?activityId=566462&userId=1912797&sp=1790672679000",
    )

    @Test
    fun `encode then decode then parse round trip`() {
        val reader = MultiFormatReader()
        payloads.forEach { (name, text) ->
            val pixels = renderPixels(text)
            val decoded = reader.decodeWithState(
                BinaryBitmap(HybridBinarizer(RGBLuminanceSource(SIZE, SIZE, pixels))),
            ).text
            assertEquals(name, text, decoded)
            assertNotNull(name, parseSignCode(decoded))
        }
    }

    @Test
    fun `parsed ticket matches the branch the official code takes`() {
        assertEquals(1, parseSignCode(payloads[0].second)!!.signSwitch)
        assertEquals(2, parseSignCode(payloads[1].second)!!.signSwitch)
        assertEquals(1, parseSignCode(payloads[2].second)!!.type)
    }

    /** 把二维码矩阵摊成 ARGB 像素，避免依赖 AWT。 */
    private fun renderPixels(text: String): IntArray {
        val matrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            SIZE,
            SIZE,
            mapOf(EncodeHintType.MARGIN to 2),
        )
        val pixels = IntArray(SIZE * SIZE) { 0xFFFFFFFF.toInt() }
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) pixels[y * SIZE + x] = 0xFF000000.toInt()
            }
        }
        return pixels
    }

    private companion object {
        const val SIZE = 720
    }
}
