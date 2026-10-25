package gitee.leolin_me.erkepp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.PersonProfile
import gitee.leolin_me.erkepp.data.UserRepository
import gitee.leolin_me.erkepp.data.toUserMessage
import gitee.leolin_me.erkepp.ui.InfoRow
import gitee.leolin_me.erkepp.ui.PageLoading
import gitee.leolin_me.erkepp.ui.SectionCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 人物主页，对应官方 `#/userShows/<id>`。
 *
 * 官方那一页只调 `message/chat/follow/user/info`，摆出来的是：头像与性别、姓名、
 * 「学院 - 专业」（组织账号则是「学院 - 组织名」）、参加活动 / 加入组织 / 广场动态三个计数、
 * 个人标签，以及关注按钮。
 *
 * 不做「点计数进列表」：那三个列表页（别人的活动、别人的组织、广场动态）客户端还没有，
 * 挂个点了没反应的箭头比不挂更糟。
 */
@Composable
fun UserProfileScreen(
    token: String,
    repository: UserRepository,
    userId: Long,
    /** 是不是我自己；自己就不显示关注按钮。 */
    isSelf: Boolean = false,
    onBack: () -> Unit,
) {
    var profile by remember(userId) { mutableStateOf<PersonProfile?>(null) }
    var loading by remember(userId) { mutableStateOf(true) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var followBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId, tick) {
        loading = true
        try {
            profile = repository.info(token, userId)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            error = failure.toUserMessage()
        }
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
            Text(
                text = profile?.name ?: "人物主页",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when {
            loading && profile == null -> PageLoading()

            profile == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = error ?: "没拿到这个人信息",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { tick++ }) { Text("重试") }
            }

            else -> {
                val current = profile!!
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item { ProfileHeader(current) }

                    item {
                        SectionCard(
                            title = "概况",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            InfoRow("参加活动", "共 ${current.activityCount} 次")
                            InfoRow("加入组织", "共 ${current.organizationCount} 个")
                            InfoRow("广场动态", "共 ${current.dynamicCount} 个")
                        }
                    }

                    item {
                        SectionCard(
                            title = "个人标签",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            if (current.tags.isEmpty()) {
                                Text(
                                    text = "没有添加标签",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                TagFlow(current.tags)
                            }
                        }
                    }

                    if (!isSelf) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                if (current.isFollow) {
                                    OutlinedButton(
                                        enabled = !followBusy,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            scope.launch {
                                                followBusy = true
                                                runCatching {
                                                    repository.setFollow(token, userId, follow = false)
                                                }
                                                followBusy = false
                                                tick++
                                            }
                                        },
                                    ) {
                                        Text("已关注，点击取消")
                                    }
                                } else {
                                    Button(
                                        enabled = !followBusy,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            scope.launch {
                                                followBusy = true
                                                runCatching {
                                                    repository.setFollow(token, userId, follow = true)
                                                }
                                                followBusy = false
                                                tick++
                                            }
                                        },
                                    ) {
                                        Text("关注")
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: PersonProfile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(
            url = profile.avatar,
            name = profile.name,
            contentDescription = "头像",
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val gender = when (profile.gender) {
                    1 -> "男"
                    2 -> "女"
                    else -> ""
                }
                if (gender.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = gender,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            val subtitle = profile.subtitle
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFlow(tags: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag ->
            Box(
                modifier = Modifier
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
