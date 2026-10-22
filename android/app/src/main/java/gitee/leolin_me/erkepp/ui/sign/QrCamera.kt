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
import gitee.leolin_me.erkepp.AppLog
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
    private val firstFrameLogged = java.util.concurrent.atomic.AtomicBoolean(false)
    private var continuousFocus = false
    private val focusHandler = android.os.Handler(android.os.Looper.getMainLooper())

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
            val viewWidth = texture.width.takeIf { it > 0 } ?: 1080
            val viewHeight = texture.height.takeIf { it > 0 } ?: 1920
            val size = choosePreviewSize(params, viewWidth, viewHeight)
            params.setPreviewSize(size.width, size.height)
            setUpFocus(cam, params)
            cam.parameters = params
            // 竖屏应用固定 90°；预览被转正后，表面上的内容宽高互换
            cam.setDisplayOrientation(90)
            cam.setPreviewTexture(texture.surfaceTexture)
            previewWidth = size.width
            previewHeight = size.height
            // 整幅画面都显示（留黑边），不再放大裁切：裁切会让视野变窄，看着像贴到屏幕上
            applyFitCenter(texture, size.width, size.height)
            cam.startPreview()
            accepting = desired
            cam.setPreviewCallback { data, _ -> onFrame(data) }
            camera = cam
            if (torchOn) applyTorch(cam, true)
            startPeriodicFocus(cam)
            AppLog.d(
                "sign",
                "camera open size=${size.width}x${size.height} view=${viewWidth}x$viewHeight " +
                    "continuousFocus=$continuousFocus",
            )
            null
        } catch (t: Throwable) {
            runCatching { cam.release() }
            "相机启动失败：${t.javaClass.simpleName}"
        }
    }

    /**
     * 对焦：优先连续对焦；不支持时退回「自动对焦 + 定时触发」，
     * 否则拍屏幕这种近距离场景会一直虚着。
     */
    private fun setUpFocus(cam: Camera, params: Camera.Parameters) {
        val modes = params.supportedFocusModes.orEmpty()
        continuousFocus = when {
            modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) -> {
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                true
            }
            modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) -> {
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                true
            }
            modes.contains(Camera.Parameters.FOCUS_MODE_AUTO) -> {
                params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                false
            }
            else -> false
        }
        // 拍屏幕常是近距离，MACRO 可用时优先给对焦区域留出微距能力
        if (!continuousFocus && modes.contains(Camera.Parameters.FOCUS_MODE_MACRO)) {
            runCatching { params.focusMode = Camera.Parameters.FOCUS_MODE_MACRO }
        }
    }

    /** 非连续对焦模式下每 1.5 秒触发一次，避免画面长时间虚着。 */
    private fun startPeriodicFocus(cam: Camera) {
        if (continuousFocus) return
        val runnable = object : Runnable {
            override fun run() {
                if (camera !== cam) return
                runCatching { cam.autoFocus(null) }
                focusHandler.postDelayed(this, 1500)
            }
        }
        focusHandler.postDelayed(runnable, 500)
    }

    /** 点哪儿对哪儿：把点击位置换算成相机坐标，设对焦区域后触发一次自动对焦。 */
    fun focusAt(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
        val cam = camera ?: return
        if (viewWidth <= 0 || viewHeight <= 0) return
        val surfaceWidth = previewHeight.toFloat()
        val surfaceHeight = previewWidth.toFloat()
        if (surfaceWidth <= 0f || surfaceHeight <= 0f) return
        // 预览按「完整显示」摆放：先算缩放和偏移，再把触点换算回表面坐标
        val scale = minOf(viewWidth / surfaceWidth, viewHeight / surfaceHeight)
        val offsetX = (viewWidth - surfaceWidth * scale) / 2f
        val offsetY = (viewHeight - surfaceHeight * scale) / 2f
        val surfaceX = ((x - offsetX) / scale / surfaceWidth).coerceIn(0f, 1f)
        val surfaceY = ((y - offsetY) / scale / surfaceHeight).coerceIn(0f, 1f)
        // 表面经过 90° 旋转，映射到相机坐标（-1000..1000）
        val cx = ((surfaceY * 2f) - 1f) * 1000f
        val cy = ((surfaceX * 2f) - 1f) * -1000f
        val half = 250
        runCatching {
            val params = cam.parameters
            val rect = Camera.Area(
                android.graphics.Rect(
                    (cx - half).toInt().coerceIn(-1000, 1000),
                    (cy - half).toInt().coerceIn(-1000, 1000),
                    (cx + half).toInt().coerceIn(-1000, 1000),
                    (cy + half).toInt().coerceIn(-1000, 1000),
                ),
                1000,
            )
            if (params.maxNumFocusAreas > 0) {
                params.focusAreas = listOf(rect)
                params.meteringAreas = listOf(rect)
                cam.parameters = params
            }
        }.onFailure { AppLog.d("sign", "focus area failed ${it.javaClass.simpleName}") }
        runCatching { cam.autoFocus(null) }
        AppLog.d("sign", "tap focus at ${cx.toInt()},${cy.toInt()}")
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
        focusHandler.removeCallbacksAndMessages(null)
        val cam = camera ?: return
        camera = null
        runCatching { cam.setPreviewCallback(null) }
        runCatching { cam.stopPreview() }
        runCatching { cam.release() }
    }

    private fun onFrame(data: ByteArray?) {
        if (data == null || !accepting || busy) return
        val expected = previewWidth * previewHeight * 3 / 2
        if (firstFrameLogged.compareAndSet(false, true)) {
            AppLog.d(
                "sign",
                "camera frame bytes=${data.size} expected=$expected size=${previewWidth}x$previewHeight",
            )
        }
        if (data.size < previewWidth * previewHeight) {
            // 帧长和声明尺寸对不上时直接跳过，避免把错位的缓冲当图像解出乱码
            if (firstFrameLogged.get()) {
                AppLog.d("sign", "camera frame too short bytes=${data.size} expected=$expected")
                firstFrameLogged.set(false)
            }
            return
        }
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

    /**
     * 选预览尺寸：优先「转正后的长宽比」接近屏幕的，且别太吃 CPU。
     *
     * 之前固定挑最小的 640×480（4:3），竖屏下要裁掉两边才能铺满，视野被压窄一倍多，
     * 看起来就像手机贴在码上。现在按屏幕比例挑，16:9 的 1280×720 在竖屏下最接近。
     */
    private fun choosePreviewSize(params: Camera.Parameters, viewWidth: Int, viewHeight: Int): Camera.Size {
        val sizes = params.supportedPreviewSizes.orEmpty()
        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
        val candidates = sizes.filter { it.width >= it.height }
            .ifEmpty { sizes }
            .filter { it.width * it.height <= 1920 * 1080 }
            .ifEmpty { sizes }
        return candidates.minByOrNull { size ->
            // 转正后长宽比 = 宽/高 的倒数
            val rotatedAspect = size.height.toFloat() / size.width.toFloat()
            val aspectPenalty = kotlin.math.abs(rotatedAspect - viewAspect) * 100f
            // 面积小一点省电，作为次要因素
            val areaPenalty = (size.width * size.height).toFloat() / (1920f * 1080f)
            aspectPenalty + areaPenalty
        } ?: candidates.first()
    }

    /** 整幅画面铺进 View，不做裁切：宁可留黑边，也不要无故放大。 */
    private fun applyFitCenter(view: TextureView, width: Int, height: Int) {
        val viewWidth = view.width.toFloat()
        val viewHeight = view.height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return
        val surfaceWidth = height.toFloat()
        val surfaceHeight = width.toFloat()
        val scale = minOf(viewWidth / surfaceWidth, viewHeight / surfaceHeight)
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
