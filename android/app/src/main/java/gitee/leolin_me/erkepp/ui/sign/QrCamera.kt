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
    private var frameBuffer: ByteArray? = null
    private val firstFrameLogged = java.util.concurrent.atomic.AtomicBoolean(false)
    private var continuousFocus = false
    private var rotation = 90
    private var swapped = true
    /** 解码节流间隔：按实测解码耗时自适应，慢机器自动降频。 */
    @Volatile private var decodeIntervalMs = 220L
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
            // 预览格式：绝大多数机器支持 NV21；不支持时保留默认（解 Y 平面两者都成立）
            runCatching { params.previewFormat = ImageFormat.NV21 }
            val viewWidth = texture.width.takeIf { it > 0 } ?: 1080
            val viewHeight = texture.height.takeIf { it > 0 } ?: 1920
            val size = choosePreviewSize(params, viewWidth, viewHeight)
            params.setPreviewSize(size.width, size.height)
            setUpFocus(cam, params)
            cam.parameters = params
            // 方向按摄像头信息与当前屏幕旋转算，不写死 90°：横屏/平板/异形屏都要对
            rotation = computeDisplayOrientation(context)
            swapped = rotation % 180 != 0
            cam.setDisplayOrientation(rotation)
            cam.setPreviewTexture(texture.surfaceTexture)
            previewWidth = size.width
            previewHeight = size.height
            // 整幅画面都显示（留黑边），不再放大裁切：裁切会让视野变窄，看着像贴到屏幕上
            applyFitCenter(texture)
            cam.startPreview()
            accepting = desired
            cam.setPreviewCallback { data, _ -> onFrame(data) }
            camera = cam
            if (torchOn) applyTorch(cam, true)
            startPeriodicFocus(cam)
            val previewSizes = runCatching {
                params.supportedPreviewSizes.orEmpty()
                    .joinToString(",") { "${it.width}x${it.height}" }
            }.getOrDefault("")
            val pictureSizes = runCatching {
                params.supportedPictureSizes.orEmpty()
                    .take(6)
                    .joinToString(",") { "${it.width}x${it.height}" }
            }.getOrDefault("")
            AppLog.d(
                "sign",
                "camera open size=${size.width}x${size.height} view=${viewWidth}x$viewHeight " +
                    "continuousFocus=$continuousFocus maxZoom=${params.maxZoom} " +
                    "previews=$previewSizes pictures=$pictureSizes",
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
        val (surfaceWidth, surfaceHeight) = surfaceSize()
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

    /** 屏幕方向变了：重新算一遍显示方向与预览变换。 */
    fun restart() {
        val texture = view ?: return
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

    /**
     * 变焦：把屏幕上的双指缩放映射到相机档位。
     *
     * 远景扫码时这一下最有效——变焦后同样的预览分辨率只覆盖更窄的视野，
     * 码上的像素密度直接上升，而码本身还留在画面里。
     */
    fun setZoom(factor: Float): Boolean {
        val cam = camera ?: return false
        return runCatching {
            val params = cam.parameters
            if (!params.isZoomSupported) return@runCatching false
            val ratios = params.zoomRatios ?: return@runCatching false
            if (ratios.isEmpty()) return@runCatching false
            val target = (factor.coerceIn(1f, 8f) * 100f).toInt()
            var best = 0
            var bestDiff = Int.MAX_VALUE
            ratios.forEachIndexed { index, ratio ->
                val diff = kotlin.math.abs(ratio - target)
                if (diff < bestDiff) {
                    bestDiff = diff
                    best = index
                }
            }
            params.zoom = best.coerceIn(0, params.maxZoom)
            cam.parameters = params
            AppLog.d("sign", "zoom factor=$factor index=$best/${params.maxZoom} ratio=${ratios.getOrNull(best)}")
            true
        }.getOrDefault(false)
    }

    /**
     * 拍一张全尺寸静帧再解。
     *
     * 预览最多给到 2400×1080，座位离大屏十几米时码上像素本来就不够；
     * 静帧是 4096×3072，等于多出 4 倍边长，是「真的很远」时唯一靠谱的兜底。
     */
    fun captureStill(onResult: (String?) -> Unit) {
        val cam = camera ?: return onResult(null)
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finish(text: String?) {
            if (!finished.compareAndSet(false, true)) return
            restartPreview()
            onResult(text)
        }
        runCatching {
            cam.takePicture(null, null) { data, _ ->
                val text = runCatching { decodeJpeg(data) }.getOrNull()
                finish(text)
            }
        }.onFailure {
            AppLog.d("sign", "still failed ${it.javaClass.simpleName}")
            finish(null)
        }
        // 个别机型 jpeg 回调可能不来，超时后恢复预览
        focusHandler.postDelayed({ finish(null) }, 8000)
    }

    private fun restartPreview() {
        val cam = camera ?: return
        runCatching { cam.startPreview() }
        runCatching { cam.setPreviewCallback { data, _ -> onFrame(data) } }
        accepting = desired
    }

    /**
     * 全尺寸 JPEG → 先整幅降采样解一次（快），不中再解**中心区域的原分辨率**一次。
     *
     * 远处的小码往往只有几十像素，整幅缩到 2048 宽后又糊了；中心区域按原分辨率解，
     * 等于对静帧做了一次 2 倍数字变焦，才是「真的很远」时真正管用的那一下。
     * 内存上两趟加起来峰值约 25MB，比直接解 12MP 位图省一半。
     */
    private fun decodeJpeg(data: ByteArray): String? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, bounds) }
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null

        var sample = 1
        while (maxOf(width, height) / sample > 2600) sample *= 2
        val full = runCatching {
            android.graphics.BitmapFactory.decodeByteArray(
                data,
                0,
                data.size,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }.getOrNull() ?: return null
        val whole = runCatching { decodeQrFromBitmap(full) }.getOrNull()
        AppLog.d(
            "sign",
            "still shot ${width}x$height sample=$sample decoded=${full.width}x${full.height} hit=${whole != null}",
        )
        full.recycle()
        if (whole != null) return whole

        // 第二趟：中心 50% 区域按原分辨率解
        return runCatching {
            val decoder = android.graphics.BitmapRegionDecoder.newInstance(data, 0, data.size, false)
            val rect = android.graphics.Rect(width / 4, height / 4, width * 3 / 4, height * 3 / 4)
            var regionSample = 1
            while (maxOf(rect.width(), rect.height()) / regionSample > 2600) regionSample *= 2
            val region = decoder.decodeRegion(
                rect,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = regionSample },
            )
            decoder.recycle()
            val hit = region?.let { runCatching { decodeQrFromBitmap(it) }.getOrNull() }
            AppLog.d(
                "sign",
                "still center ${rect.width()}x${rect.height()} sample=$regionSample hit=${hit != null}",
            )
            region?.recycle()
            hit
        }.getOrNull()
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
        val width = previewWidth
        val height = previewHeight
        val lumaSize = width * height
        if (data.size < lumaSize) {
            if (firstFrameLogged.compareAndSet(false, true)) {
                AppLog.d("sign", "camera frame too short bytes=${data.size} need=$lumaSize")
            }
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastAttempt < decodeIntervalMs) return
        lastAttempt = now
        busy = true
        if (firstFrameLogged.compareAndSet(false, true)) {
            AppLog.d(
                "sign",
                "camera frame bytes=${data.size} size=${width}x$height",
            )
        }
        // 只要 Y 平面（灰度），并复用同一块缓冲：2400×1080 的帧每 220ms 拷一次，
        // 每次都新分配会明显增加 GC 压力
        var buffer = frameBuffer
        if (buffer == null || buffer.size != lumaSize) {
            buffer = ByteArray(lumaSize)
            frameBuffer = buffer
        }
        System.arraycopy(data, 0, buffer, 0, lumaSize)
        val frame = buffer
        worker.execute {
            val started = SystemClock.elapsedRealtime()
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
                val cost = SystemClock.elapsedRealtime() - started
                adaptInterval(cost)
                busy = false
            }
        }
    }

    /** 慢机器上别死磕高帧率：解码越慢，节流间隔越长，省电也不卡预览。 */
    private fun adaptInterval(costMs: Long) {
        val next = when {
            costMs > 350 -> 600L
            costMs > 150 -> 350L
            costMs > 80 -> 250L
            else -> 180L
        }
        if (next != decodeIntervalMs) {
            decodeIntervalMs = next
            AppLog.d("sign", "decode ${costMs}ms → throttle ${next}ms size=${previewWidth}x$previewHeight")
        }
    }

    /**
     * 选预览尺寸：优先「转正后的长宽比」接近屏幕的，其次宁可大一点。
     *
     * 远距离扫码的瓶颈是「码上有多少像素」，所以只要比例对得上就尽量用大尺寸：
     * 本机 2400×1080 与屏幕同为 2.22 比例，比 1280×576 多出近 4 倍像素，
     * 又不会因为裁切而缩窄视野。上限卡在 2400×1080，再大就是白烧 CPU。
     */
    private fun choosePreviewSize(params: Camera.Parameters, viewWidth: Int, viewHeight: Int): Camera.Size {
        val sizes = params.supportedPreviewSizes.orEmpty()
        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
        val candidates = sizes.filter { it.width >= it.height }
            .ifEmpty { sizes }
            .filter { it.width.toLong() * it.height <= 2400L * 1080L }
            .ifEmpty { sizes }
        val matching = candidates.filter { size ->
            val rotatedAspect = size.height.toFloat() / size.width.toFloat()
            kotlin.math.abs(rotatedAspect - viewAspect) <= viewAspect * 0.02f
        }
        if (matching.isNotEmpty()) {
            return matching.maxByOrNull { it.width.toLong() * it.height }
                ?: matching.first()
        }
        // 没有比例完全对得上的，就挑最接近的（宁可留黑边，不裁切）
        return candidates.minByOrNull { size ->
            val rotatedAspect = size.height.toFloat() / size.width.toFloat()
            kotlin.math.abs(rotatedAspect - viewAspect)
        } ?: candidates.first()
    }

    /** 整幅画面铺进 View，不做裁切：宁可留黑边，也不要无故放大。 */
    private fun applyFitCenter(view: TextureView) {
        val viewWidth = view.width.toFloat()
        val viewHeight = view.height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return
        val (surfaceWidth, surfaceHeight) = surfaceSize()
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

    /** 旋转 90/270 时预览表面宽高互换。 */
    private fun surfaceSize(): Pair<Float, Float> = if (swapped) {
        previewHeight.toFloat() to previewWidth.toFloat()
    } else {
        previewWidth.toFloat() to previewHeight.toFloat()
    }

    /**
     * 显示方向：按后置/前置摄像头方向与屏幕旋转算，不写死 90°。
     * 换台手机、横过来拿、平板，都能摆正。
     */
    private fun computeDisplayOrientation(context: Context): Int = runCatching {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(0, info)
        val displayRotation = runCatching {
            @Suppress("DEPRECATION")
            val manager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            when (manager.defaultDisplay.rotation) {
                android.view.Surface.ROTATION_90 -> 90
                android.view.Surface.ROTATION_180 -> 180
                android.view.Surface.ROTATION_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (360 - (info.orientation + displayRotation) % 360) % 360
        } else {
            (info.orientation - displayRotation + 360) % 360
        }
    }.getOrDefault(90)
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
