package gitee.leolin_me.erkepp.ui.profile

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import gitee.leolin_me.erkepp.ui.PageLoading
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.ProfileDetails
import gitee.leolin_me.erkepp.data.ProfileRepository
import gitee.leolin_me.erkepp.data.toUserMessage
import kotlinx.coroutines.launch

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
    onOpenPassword: () -> Unit,
    onOpenMobile: (String) -> Unit,
    onOpenTags: (List<Int>) -> Unit,
    refreshKey: Int = 0,
    onBack: () -> Unit,
) {
    var details by remember { mutableStateOf<ProfileDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tagDict by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    // 每写成功一次自增，触发重新拉资料
    var localTick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<String?>(null) }
    var nations by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var submitting by remember { mutableStateOf(false) }
    var writeError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey, localTick) {
        loading = true
        runCatching { repository.details(token) }
            .onSuccess { details = it; error = null }
            .onFailure { error = it.toUserMessage() }
        // 标签字典只在第一次进来时拉，用来把 id 显示成名字
        if (tagDict.isEmpty()) {
            tagDict = runCatching { repository.tagDictionary(token) }.getOrDefault(emptyList())
        }
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
            ) { PageLoading() }

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
                    ActionRow("性别", current.genderText, onClick = { editing = "gender" })
                    ActionRow("民族", current.ethnic.ifBlank { "未填写" }, onClick = {
                        editing = "ethnic"
                        if (nations.isEmpty()) {
                            scope.launch {
                                nations = runCatching { repository.nations(token) }.getOrDefault(emptyList())
                            }
                        }
                    })
                    ActionRow("政治面貌", current.politicsText, onClick = { editing = "politics" }, last = true)
                }
                Spacer(Modifier.height(12.dp))
                InfoCard("院系") {
                    InfoRow("学院", current.college.ifBlank { "—" })
                    InfoRow("专业", current.major.ifBlank { "—" })
                    InfoRow("班级", current.className.ifBlank { "—" })
                    InfoRow("年级", current.grade.ifBlank { "—" }, last = true)
                }
                Spacer(Modifier.height(12.dp))
                InfoCard("标签") {
                    ActionRow(
                        label = "我的标签",
                        action = tagNames(current.personTagIds, tagDict)
                            .joinToString(" ")
                            .ifBlank { "未设置" },
                        onClick = { onOpenTags(current.personTagIds) },
                        last = true,
                    )
                }
                Spacer(Modifier.height(12.dp))
                InfoCard("账号安全") {
                    ActionRow(
                        label = "手机号",
                        action = current.maskedMobile,
                        onClick = { onOpenMobile(current.mobile) },
                    )
                    ActionRow("登录密码", "修改", onClick = onOpenPassword, last = true)
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

    // 三处单字段修改共用一套选择对话框；一次只提交一个字段（官方 update-info 如此）
    val current = details
    if (current != null && editing != null && !submitting) {
        val title = when (editing) {
            "gender" -> "选择性别"
            "ethnic" -> "选择民族"
            else -> "选择政治面貌"
        }
        val options = when (editing) {
            "gender" -> listOf(1 to "男", 2 to "女")
            "ethnic" -> nations
            else -> listOf(
                0 to "群众",
                1 to "共青团员",
                2 to "中共党员",
                3 to "其他党派",
                4 to "中共预备党员",
            )
        }
        val selected = when (editing) {
            "gender" -> current.gender
            "ethnic" -> current.ethnicId
            else -> current.politics
        }
        ChoiceDialog(
            title = title,
            options = options,
            selected = selected,
            error = writeError,
            onDismiss = {
                editing = null
                writeError = null
            },
            onPick = { value ->
                val field = editing
                submitting = true
                writeError = null
                scope.launch {
                    runCatching {
                        when (field) {
                            "gender" -> repository.updateGender(token, value)
                            "ethnic" -> repository.updateEthnic(token, value)
                            else -> repository.updatePolitics(token, value)
                        }
                    }
                        .onSuccess {
                            editing = null
                            localTick++
                        }
                        .onFailure { writeError = it.toUserMessage() }
                    submitting = false
                }
            },
        )
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    error: String?,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (options.isEmpty()) {
                    Text(
                        text = "列表还没加载出来，稍后再试。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(value) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (value == selected) {
                            Text(
                                text = "当前",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
private fun ActionRow(
    label: String,
    action: String,
    onClick: () -> Unit,
    last: Boolean = false,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
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
                text = action,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
