package gitee.leolin_me.erkepp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 整页加载：全应用只有这一种转圈。
 *
 * 以前外壳用一个 28dp 细圈、页面各自用 Material 默认的 40dp 粗圈，切过去像换了一个动画；
 * 统一成同一个尺寸和位置，外壳→页面的交接就只是一次不易察觉的重启，而不是换一套。
 */
@Composable
fun PageLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.dp,
        )
    }
}
