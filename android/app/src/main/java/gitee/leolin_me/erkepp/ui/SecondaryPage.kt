package gitee.leolin_me.erkepp.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 二级页面外壳：不透明背景 + 入场位移淡入 + 预测性返回退场。
 *
 * 返回手势由 [PredictiveBackHandler] 接管：系统给出手势进度（0..1），
 * 这里直接把它映射成位移和透明度，所以手指拖到一半就能看到页面退到一半，
 * 中途松手会弹回且不导航。三键导航或旧系统拿不到进度时，退化成一次收尾动画。
 *
 * 正文要**等入场动画走完再组合**：页面一上来就发请求、解析 JSON、铺列表，会和动画抢同一帧，
 * 表现就是「点进去卡一下」。放在外壳里统一处理，新加的二级页不用再各自记一遍这件事。
 *
 * 用法：页面自己不再注册 BackHandler，返回一律交给这一层。
 */
@Composable
fun SecondaryPage(onBack: () -> Unit, content: @Composable () -> Unit) {
    var appeared by remember { mutableStateOf(false) }
    val appearProgress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "secondaryPageAppear",
    )
    LaunchedEffect(Unit) { appeared = true }

    // 动画走完再放行「就位」；万一动画没跑（异常兜底），800ms 后也放行，不能让页面一直空着
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withTimeoutOrNull(800) { snapshotFlow { appearProgress }.first { it >= 1f } }
        settled = true
    }

    // 0f = 停在当前页；1f = 已完全退出
    val backProgress = remember { Animatable(0f) }

    PredictiveBackHandler(enabled = true) { events ->
        try {
            events.collect { event -> backProgress.snapTo(event.progress) }
            // 手势已提交：补完退场动画，再真正导航
            backProgress.animateTo(1f, tween(durationMillis = 120))
            onBack()
            backProgress.snapTo(0f)
        } catch (cancelled: CancellationException) {
            // 中途松手：弹回原位，不导航
            backProgress.animateTo(
                0f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            )
            throw cancelled
        }
    }

    val progress = backProgress.value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = appearProgress * (1f - progress)
                translationX = (1f - appearProgress) * 48.dp.toPx() + progress * 96.dp.toPx()
                scaleX = 1f - progress * 0.04f
                scaleY = 1f - progress * 0.04f
            },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (settled) {
                content()
            } else {
                // 动画期间只放一个转圈，别让正文的请求和布局跟动画抢帧
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}
