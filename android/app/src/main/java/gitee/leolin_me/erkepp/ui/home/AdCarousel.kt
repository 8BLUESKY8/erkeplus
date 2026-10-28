package gitee.leolin_me.erkepp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.SubcomposeAsyncImage
import gitee.leolin_me.erkepp.data.DiscoverAd
import kotlinx.coroutines.delay

@Composable
fun AdCarousel(
    ads: List<DiscoverAd>,
    bottom: Boolean,
    active: Boolean,
    showPlaceholder: Boolean,
    onOpen: (String) -> Unit,
) {
    if (ads.isEmpty() && !showPlaceholder) return
    val aspect = if (bottom) 1080f / 420f else 3f
    val shape = RoundedCornerShape(16.dp)
    val slotTitle = if (bottom) "广告位 B · 发现页底部" else "广告位 A · 发现页横幅"
    if (ads.isEmpty()) {
        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(
                Modifier.fillMaxWidth().aspectRatio(aspect).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(slotTitle, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text("预留广告位", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    val pager = rememberPagerState(pageCount = { ads.size })
    val dragging by pager.interactionSource.collectIsDraggedAsState()
    var paused by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active, paused, dragging, pager.currentPage, ads) {
        if (!active || paused || dragging || ads.size < 2) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(5_000)
                if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % ads.size)
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            HorizontalPager(state = pager, key = { ads[it].id }, modifier = Modifier.fillMaxWidth()) { page ->
                val ad = ads[page]
                SubcomposeAsyncImage(
                    model = ad.imageUrl,
                    contentDescription = ad.title,
                    contentScale = ContentScale.Crop,
                    loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    } },
                    error = { Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(ad.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    } },
                    modifier = Modifier.fillMaxWidth().aspectRatio(aspect)
                        .clickable(role = Role.Button, onClickLabel = "打开${ad.title}") { onOpen(ad.targetUrl) },
                )
            }
            Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .9f)) {
                Text("广告", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        if (ads.size > 1) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End) {
                Text("${pager.currentPage + 1} / ${ads.size}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { paused = !paused }) { Text(if (paused) "继续轮播" else "暂停轮播") }
            }
        }
    }
}
