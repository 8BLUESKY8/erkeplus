package gitee.leolin_me.erkepp.ui.school

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gitee.leolin_me.erkepp.data.School
import gitee.leolin_me.erkepp.data.SchoolRepository
import gitee.leolin_me.erkepp.data.toUserMessage
import gitee.leolin_me.erkepp.ui.rememberCoverRequest
import kotlinx.coroutines.delay

/**
 * 隐藏的选校页：登录页长按校名 5 秒进入。
 *
 * 学校与校徽都来自官方档案接口 `/common/all-school`，
 * 当前默认仍是安徽理工大学，这里只用于验证多学校扩展。
 */
@Composable
fun SchoolPickerScreen(
    repository: SchoolRepository,
    current: School,
    onBack: () -> Unit,
    onSelect: (School) -> Unit,
) {
    var schools by remember { mutableStateOf<List<School>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    // 与服务端搜索一致：官方也是把输入框内容作为 name 参数请求 /common/all-school
    LaunchedEffect(query) {
        loading = true
        error = null
        if (query.isNotBlank()) delay(300)
        runCatching { repository.list(query) }
            .onSuccess { schools = it }
            .onFailure { error = it.toUserMessage() }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("选择学校", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "共 ${schools.size} 所学校",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("搜索学校名称") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBack) { Text("返回") }
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(schools, key = { it.code }) { school ->
                    SchoolRow(
                        school = school,
                        selected = school.code == current.code,
                        onClick = { onSelect(school) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SchoolRow(school: School, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (school.logoUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = school.name.take(1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            AsyncImage(
                model = rememberCoverRequest(school.logoUrl, 36, maxWidthPx = 144),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = school.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                text = "当前",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
