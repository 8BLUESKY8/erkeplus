package gitee.leolin_me.erkepp.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.ProfileDetails
import gitee.leolin_me.erkepp.data.ProfileRepository
import gitee.leolin_me.erkepp.data.toUserMessage

/**
 * 详细个人资料。
 *
 * 官方把这件事拆成「我的信息」`#/myInfor` 与「设置」`#/settings` 两个页面，
 * 这里合成一页：上半部分只读展示，下半部分放账号安全与可编辑项（后续阶段逐个接上）。
 */
@Composable
fun PersonalInfoScreen(
    token: String,
    repository: ProfileRepository,
    onBack: () -> Unit,
) {
    var details by remember { mutableStateOf<ProfileDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { repository.details(token) }
            .onSuccess { details = it; error = null }
            .onFailure { error = it.toUserMessage() }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                text = "详细资料",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }

        val current = details
        when {
            loading -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }

            current == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error ?: "没拿到个人资料", color = MaterialTheme.colorScheme.error)
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                HeaderCard(current)
                Spacer(Modifier.height(12.dp))
                InfoCard("基本信息") {
                    InfoRow("学号", current.studentNo)
                    InfoRow("性别", current.genderText)
                    InfoRow("民族", current.ethnic.ifBlank { "未填写" })
                    InfoRow("政治面貌", current.politicsText)
                }
                Spacer(Modifier.height(12.dp))
                InfoCard("院系") {
                    InfoRow("学院", current.college.ifBlank { "—" })
                    InfoRow("专业", current.major.ifBlank { "—" })
                    InfoRow("班级", current.className.ifBlank { "—" })
                    InfoRow("年级", current.grade.ifBlank { "—" }, last = true)
                }
                Spacer(Modifier.height(12.dp))
                InfoCard("账号") {
                    InfoRow("手机号", current.maskedMobile, last = true)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "姓名、学号、院系、专业、班级由学校维护，应用内不可修改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HeaderCard(details: ProfileDetails) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarImage(
                url = details.avatar,
                name = details.name,
                contentDescription = "头像",
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = details.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = details.studentNo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, last: Boolean = false) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(96.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
        if (!last) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp)
                    .height(1.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {}
        }
    }
}
