package gitee.leolin_me.erkepp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 整页加载：全应用只有这一种转圈。
 *
 * 用 Material 的默认规格（40dp，圈更粗）——尺寸统一、分量也统一，比细圈更稳。
 * 外壳入场时画它，页面自己的加载态也用它：两边形状一致，交接就只是一次不易察觉的重启，
 * 而不是「换个动画重来一次」。
 */
@Composable
fun PageLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * 行内加载：跟着文字或按钮走的小圈。
 *
 * 尺寸仍然按场景给，但实现只有这一份——线宽和颜色跟着主题走，各处不会再各写一遍。
 */
@Composable
fun InlineLoading(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    strokeWidth: Dp = 2.dp,
    color: Color = Color.Unspecified,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = if (color.isSpecified) color else MaterialTheme.colorScheme.primary,
        strokeWidth = strokeWidth,
    )
}
