package gitee.leolin_me.erkepp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 二级页面的统一外壳：不透明的背景 + 入场位移淡入。
 *
 * 之前这些页面直接铺在标签页上面，自身没有背景色，底下的列表会透上来；
 * 现在统一套这一层，既遮住底页，也顺便给出「从右边进来」的过渡。
 */
@Composable
fun SecondaryPage(content: @Composable () -> Unit) {
    var appeared by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "secondaryPage",
    )
    LaunchedEffect(Unit) { appeared = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = (1f - progress) * 48.dp.toPx()
                alpha = progress
            },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            content()
        }
    }
}
