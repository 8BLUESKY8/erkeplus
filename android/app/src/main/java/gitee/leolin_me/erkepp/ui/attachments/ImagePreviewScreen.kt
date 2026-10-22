package gitee.leolin_me.erkepp.ui.attachments

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.data.AttachmentStorage

/**
 * 图片预览：全屏、双指缩放、双击放大、左右滑动切换同一批附件。
 *
 * 只读本地文件，不联网。
 */
@Composable
fun ImagePreviewScreen(
    names: List<String>,
    startIndex: Int,
    resolve: (String) -> AttachmentStorage.LocalFile?,
    onBack: () -> Unit,
    onShare: (AttachmentStorage.LocalFile) -> Unit,
) {
    if (names.isEmpty()) {
        onBack()
        return
    }
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, names.lastIndex)) { names.size }
    val current = resolve(names[pagerState.currentPage])

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val file = resolve(names[page])
            if (file == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("文件不存在", color = Color.White)
                }
            } else {
                ZoomableImage(file.uri)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x66000000)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "返回",
                    tint = Color.White,
                )
            }
            Spacer(Modifier.weight(1f))
            // 只有一张图时不显示页码，避免 1/1 这种没有信息量的提示
            if (names.size > 1) {
                Surface(color = Color(0x66000000), shape = CircleShape) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${names.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Spacer(Modifier.weight(1f))
                if (current != null) {
                    TextButton(onClick = { onShare(current) }) {
                        Text("分享", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(uri: Uri) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    // 放大后允许的位移上限：超出就夹住，避免图片被拖到屏幕外回不来
    fun clamp(value: Offset, currentScale: Float, size: IntSize): Offset {
        if (size.width == 0 || size.height == 0) return value
        val maxX = size.width * (currentScale - 1f) / 2f
        val maxY = size.height * (currentScale - 1f) / 2f
        return Offset(
            x = value.x.coerceIn(-maxX, maxX),
            y = value.y.coerceIn(-maxY, maxY),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                // 只有在「双指」或「已放大后的单指」时才消费事件；
                // 1 倍时单指横划不消费，留给外层 Pager 翻页。
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) break
                        if (pressed >= 2) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset = clamp(offset + event.calculatePan(), scale, boxSize)
                            }
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1f) {
                            offset = clamp(offset + event.calculatePan(), scale, boxSize)
                            event.changes.forEach { it.consume() }
                        }
                    }
                    // 手势结束再夹一次：缩放过程中尺寸变了也要贴回边界
                    offset = clamp(offset, scale, boxSize)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                        }
                    },
                )
            },
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
