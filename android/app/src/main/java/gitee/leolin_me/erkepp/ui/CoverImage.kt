package gitee.leolin_me.erkepp.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.Coil
import coil.request.ImageRequest

/** 封面解码宽度上限：手机上再大也看不出差别，只会白占内存。 */
const val MAX_COVER_WIDTH_PX = 720

/**
 * 按显示宽度解码的图片请求。
 *
 * 服务端封面是整张大图。按原始尺寸解码时，一屏十几张卡片就要几十 MB，滚动时频繁 GC 会卡。
 * 这里把解码宽度压到实际显示宽度，并封顶 [MAX_COVER_WIDTH_PX]。
 * Coil 的尺寸是「不超过」，源图比目标小时不会放大，所以不会二次发虚。
 */
@Composable
fun rememberCoverRequest(
    data: Any?,
    widthDp: Int,
    maxWidthPx: Int = MAX_COVER_WIDTH_PX,
): ImageRequest {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = (widthDp * density.density).toInt().coerceIn(48, maxWidthPx)
    return remember(context, data, widthPx) {
        buildCoverRequest(context, data, widthPx)
    }
}

/** 双列网格中单张活动卡片的宽度（dp），用于算出封面解码像素。 */
@Composable
fun gridCardWidthDp(columns: Int = 2, outerPaddingDp: Int = 16, gapDp: Int = 12): Int {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return ((screenWidthDp - outerPaddingDp * 2 - gapDp * (columns - 1)) / columns).coerceAtLeast(96)
}

/**
 * 预取封面：先解码进内存缓存，滚到那里时直接画出来。
 *
 * 尺寸必须和卡片请求完全一致，否则内存缓存的键对不上，预取就白做了。
 */
fun prefetchCovers(context: Context, urls: List<String>, widthDp: Int) {
    if (urls.isEmpty()) return
    val widthPx = (widthDp * context.resources.displayMetrics.density).toInt()
        .coerceIn(48, MAX_COVER_WIDTH_PX)
    val loader = Coil.imageLoader(context)
    urls.forEach { url -> loader.enqueue(buildCoverRequest(context, url, widthPx)) }
}

private fun buildCoverRequest(context: Context, data: Any?, widthPx: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(data)
        .size(widthPx)
        .crossfade(false)
        .build()
