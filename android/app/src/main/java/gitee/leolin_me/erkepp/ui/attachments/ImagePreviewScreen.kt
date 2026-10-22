package gitee.leolin_me.erkepp.ui.attachments

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.res.painterResource
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
    BackHandler(onBack = onBack)
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
            Surface(color = Color(0x66000000), shape = CircleShape) {
                Text(
                    text = "${pagerState.currentPage + 1}/${names.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
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
