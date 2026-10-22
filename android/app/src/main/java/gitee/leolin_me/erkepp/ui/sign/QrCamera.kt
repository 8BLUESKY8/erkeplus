@file:Suppress("DEPRECATION")

package gitee.leolin_me.erkepp.ui.sign

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.Camera
import android.os.SystemClock
import android.view.TextureView
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * 二维码相机：camera1 API 取 NV21 预览帧，交给 zxing 解码。
 *
 * 为什么不用 CameraX：只为扫一个码不值得引入整套 CameraX（约 0.6 MB，还有 lifecycle 绑定）。
 * camera1 只需要「预览 + 一帧回调」，而 zxing 的 [PlanarYUVLuminanceSource] 正好吃 NV21，
 * 连格式转换都省了。QR 检测本身对旋转不敏感（靠三个定位图案算角度），所以不做旋转矩阵。
 *
 * 解码在单独线程做，每 120 ms 最多一次；命中一次后 [accepting] 关掉，不再继续吃帧。
 */
class QrCamera(private val context: Context, private val onDecoded: (String) -> Unit) {

    private var camera: Camera? = null
    private var view: TextureView? = null
    private var previewWidth = 0
    private var previewHeight = 0
    private var torchOn = false

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "erkepp-qr").apply { isDaemon = true }
    }
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    @Volatile private var busy = false
    @Volatile private var accepting = false
    @Volatile private var desired = true
    @Volatile private var lastAttempt = 0L

    /**
     * 只控制「要不要解码」，不动相机：扫码弹确认框时预览仍然亮着，
     * 不至于把屏幕变成一块黑。相机的开关交给生命周期与 [detach]。
     */
    fun setDecoding(enabled: Boolean) {
        desired = enabled
        accepting = enabled
        if (enabled) lastAttempt = 0L
    }

    /** 打开相机并把预览画到 [texture] 上。成功返回 null，失败返回可展示给用户的文案。 */
    fun attach(texture: TextureView): String? {
        releaseCamera()
        view = texture
        val cam = try {
            Camera.open()
        } catch (t: Throwable) {
            return if (t is RuntimeException) "相机打不开，可能被其他应用占用" else "相机打不开：${t.javaClass.simpleName}"
        }
        return try {
            val params = cam.parameters
            params.previewFormat = ImageFormat.NV21
            val size = choosePreviewSize(params)
            params.setPreviewSize(size.width, size.height)
            val focusModes = params.supportedFocusModes.orEmpty()
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            }
            cam.parameters = params
            // 竖屏应用固定 90°；预览被转正后，表面上的内容宽高互换
            cam.setDisplayOrientation(90)
            cam.setPreviewTexture(texture.surfaceTexture)
            previewWidth = size.width
            previewHeight = size.height
            applyCenterCrop(texture, size.width, size.height)
            cam.startPreview()
            accepting = desired
            cam.setPreviewCallback { data, _ -> onFrame(data) }
            camera = cam
            if (torchOn) applyTorch(cam, true)
            null
        } catch (t: Throwable) {
            runCatching { cam.release() }
            "相机启动失败：${t.javaClass.simpleName}"
        }
    }

    /** 退到后台：释放相机，但记住 View，回来还能接着开。 */
    fun pause() {
        accepting = false
        releaseCamera()
    }

    fun resume() {
        val texture = view ?: return
        if (camera != null) return
        if (!texture.isAvailable) return
        attach(texture)
    }

    fun detach() {
        pause()
        view = null
    }

    fun shutdown() {
        detach()
        worker.shutdownNow()
    }

    fun setTorch(on: Boolean): Boolean {
        torchOn = on
        val cam = camera ?: return false
        return applyTorch(cam, on)
    }

    private fun applyTorch(cam: Camera, on: Boolean): Boolean = runCatching {
        val params = cam.parameters
        val supported = params.supportedFlashModes.orEmpty().contains(Camera.Parameters.FLASH_MODE_TORCH)
        if (supported) {
            params.flashMode = if (on) Camera.Parameters.FLASH_MODE_TORCH else Camera.Parameters.FLASH_MODE_OFF
            cam.parameters = params
            true
        } else {
            false
        }
    }.getOrDefault(false)

    private fun releaseCamera() {
        accepting = false
        val cam = camera ?: return
        camera = null
        runCatching { cam.setPreviewCallback(null) }
        runCatching { cam.stopPreview() }
        runCatching { cam.release() }
    }

    private fun onFrame(data: ByteArray?) {
        if (data == null || !accepting || busy) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAttempt < 120) return
        lastAttempt = now
        busy = true
        val width = previewWidth
        val height = previewHeight
        // 预览回调复用同一块缓冲，必须拷一份再交给后台线程
        val frame = data.copyOf()
        worker.execute {
            try {
                val source = PlanarYUVLuminanceSource(frame, width, height, 0, 0, width, height, false)
                val text = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))?.text
                if (!text.isNullOrBlank() && accepting) {
                    accepting = false
                    onDecoded(text)
                }
            } catch (_: NotFoundException) {
                // 绝大多数帧里没有码，属于正常情况
            } catch (_: Throwable) {
                // zxing 偶尔抛格式/校验异常，忽略继续扫
            } finally {
                busy = false
            }
        }
    }

    /** 选一个够用又不至于太重的预览尺寸（尽量 ≥640×480，且不超过 1280×720）。 */
    private fun choosePreviewSize(params: Camera.Parameters): Camera.Size {
        val sizes = params.supportedPreviewSizes.orEmpty()
        val landscape = sizes.filter { it.width >= it.height }
        val candidates = landscape.ifEmpty { sizes }
        val sensible = candidates.filter { it.width * it.height >= 640 * 480 }
        val capped = sensible.filter { it.width * it.height <= 1280 * 720 }
        val pool = (capped.ifEmpty { sensible }).ifEmpty { candidates }
        return pool.minByOrNull { it.width * it.height } ?: candidates.first()
    }

    /**
     * 预览铺满 View 且不变形：`setDisplayOrientation(90)` 之后表面内容是 `h×w`，
     * 按“填满后居中裁切”算一个缩放与平移。
     */
    private fun applyCenterCrop(view: TextureView, width: Int, height: Int) {
        val viewWidth = view.width.toFloat()
        val viewHeight = view.height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return
        val surfaceWidth = height.toFloat()
        val surfaceHeight = width.toFloat()
        val scale = maxOf(viewWidth / surfaceWidth, viewHeight / surfaceHeight)
        view.setTransform(
            Matrix().apply {
                setScale(scale, scale)
                postTranslate(
                    (viewWidth - surfaceWidth * scale) / 2f,
                    (viewHeight - surfaceHeight * scale) / 2f,
                )
            },
        )
    }
}

/** 从一张图片里解二维码，用于「从相册选」与调试入口。 */
fun decodeQrFromBitmap(bitmap: Bitmap): String? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }
    return runCatching {
        val source = RGBLuminanceSource(width, height, pixels)
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))?.text
    }.getOrNull()
}
