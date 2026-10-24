package gitee.leolin_me.erkepp.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.ui.EntryRow

/**
 * 「更多」：底部导航的第四个入口，聚合「不属于发现、也不属于我」的页面。
 *
 * 放这里的都是**公共入口**：所有组织、加入组织、修改个人资料、官方页面。
 * 「我的组织」不在这里——它是「我的」的一部分，放下面那页。
 * 以后做成原生的官方页面（成绩单、问卷、申报…）也先往这里加，不加底部 tab。
 */
@Composable
fun MoreScreen(
    onOpenAllOrganizations: () -> Unit,
    onJoinOrganization: () -> Unit,
    onOpenPersonalInfo: () -> Unit,
    onOpenWeb: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "更多",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "还没搬进底部导航的页面，都从这里进。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))
        SectionLabel("组织")
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                EntryRow(
                    title = "所有组织",
                    subtitle = "全校组织目录，先看简介再决定申不申请",
                    onClick = onOpenAllOrganizations,
                )
                EntryRow(
                    title = "加入组织",
                    subtitle = "扫组织码，直接进那个组织的详情",
                    onClick = onJoinOrganization,
                    showDivider = false,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("账号")
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                EntryRow(
                    title = "修改个人资料",
                    subtitle = "和「我的 → 详细资料」是同一页，改一个地方两处都变",
                    onClick = onOpenPersonalInfo,
                )
                EntryRow(
                    title = "打开官方页面",
                    subtitle = "网页版完整功能：成绩单、问卷、申报等",
                    onClick = onOpenWeb,
                    showDivider = false,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "这里只放入口，不放新东西；没有对应页面的功能仍然走官方网页。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
