package gitee.leolin_me.erkepp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.webkit.ValueCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.withFrameNanos
import coil.Coil
import coil.ImageLoader
import coil.util.DebugLogger
import gitee.leolin_me.erkepp.data.ActivityActions
import gitee.leolin_me.erkepp.data.ActivityRepository
import gitee.leolin_me.erkepp.data.ApiClient
import gitee.leolin_me.erkepp.data.AppPreferences
import gitee.leolin_me.erkepp.data.AuthRepository
import gitee.leolin_me.erkepp.data.DeviceId
import gitee.leolin_me.erkepp.data.PasswordResetRepository
import gitee.leolin_me.erkepp.data.SchoolRepository
import gitee.leolin_me.erkepp.data.SchoolStore
import gitee.leolin_me.erkepp.data.SessionStore
import gitee.leolin_me.erkepp.data.WebPrefill
import gitee.leolin_me.erkepp.ui.DisclaimerDialog
import gitee.leolin_me.erkepp.ui.ErkeppTheme
import gitee.leolin_me.erkepp.ui.WebShell
import gitee.leolin_me.erkepp.ui.detail.ActivityDetailScreen
import gitee.leolin_me.erkepp.ui.detail.HomeworkScreen
import gitee.leolin_me.erkepp.ui.home.DiscoverScreen
import gitee.leolin_me.erkepp.ui.home.MyActivitiesScreen
import gitee.leolin_me.erkepp.ui.home.ProfileScreen
import gitee.leolin_me.erkepp.ui.login.ForgotPasswordScreen
import gitee.leolin_me.erkepp.ui.login.LoginScreen
import gitee.leolin_me.erkepp.ui.school.SchoolPickerScreen
import gitee.leolin_me.erkepp.ui.settings.SettingsScreen
import gitee.leolin_me.erkepp.ui.settings.SettingsSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class MainTab(val label: String, val iconRes: Int) {
    DISCOVER("发现", R.drawable.ic_explore),
    PROFILE("我的", R.drawable.ic_person),
    SETTINGS("设置", R.drawable.ic_settings),
}

class MainActivity : ComponentActivity() {

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Startup.mark("onCreate")
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppLog.init(this)
        Startup.mark("AppLog.init")

        if (BuildConfig.DEBUG) {
            // 调试包打开 Coil 日志：用来核对预取的封面是否真的命中内存缓存
            Coil.setImageLoader(
                ImageLoader.Builder(this)
                    .crossfade(false)
                    .logger(DebugLogger())
                    .build(),
            )
        }
        Startup.mark("coil")

        val sessionStore = SessionStore(this)
        val preferences = AppPreferences(this)
        val schoolStore = SchoolStore(this)
        val apiClient = ApiClient(deviceId = { DeviceId.get(applicationContext) })
        Startup.mark("deviceId")
        val authRepository = AuthRepository(apiClient)
        val resetRepository = PasswordResetRepository(apiClient)
        val schoolRepository = SchoolRepository(apiClient)
        val activityRepository = ActivityRepository(apiClient)
        val activityActions = ActivityActions(apiClient)
        Startup.mark("repositories")

        setContent {
            Startup.mark("setContent")
            ErkeppTheme {
                var warmUpPages by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    Startup.mark("first frame")
                    // 首帧画完再把另外两页组合出来：启动只画发现页，滑动时它们已经就绪
                    withFrameNanos { }
                    warmUpPages = true
                }
                var session by remember {
                    val loaded = sessionStore.load()
                    Startup.mark("session.load")
                    mutableStateOf(loaded)
                }
                // 调试用：`am start ... --es probe homework_list` 可安全验证只读接口
                val probe = remember { intent.getStringExtra("probe") }
                LaunchedEffect(probe, session) {
                    if (!BuildConfig.DEBUG || probe == null) return@LaunchedEffect
                    val current = session ?: return@LaunchedEffect
                    val detailId = intent.getLongExtra("detail_id", 0L)
                    AppLog.d("probe", "start=$probe detail=$detailId")
                    when (probe) {
                        "homework_list" -> activityActions.homeworkList(current.token)
                        "sign_count" -> if (detailId > 0) activityActions.signCount(current.token, detailId)
                        "work_detail" -> if (detailId > 0) activityActions.workDetail(current.token, detailId)
                        else -> AppLog.d("probe", "unknown probe=$probe")
                    }
                }
                var school by remember { mutableStateOf(schoolStore.load()) }
                var webUrl by remember { mutableStateOf<String?>(null) }
                var tab by rememberSaveable { mutableStateOf(MainTab.DISCOVER) }
                var showForgot by remember { mutableStateOf(false) }
                var showSchoolPicker by remember { mutableStateOf(false) }
                var showExitConfirm by remember { mutableStateOf(false) }
                var showDisclaimer by remember { mutableStateOf(!preferences.disclaimerAccepted) }

                var settingsSection by remember { mutableStateOf(SettingsSection.ROOT) }
                var showMyActivities by remember { mutableStateOf(false) }
                // 支持 `am start ... --el detail_id 566506` 直接开详情，方便调试和截图
                var detailActivityId by remember {
                    mutableStateOf(intent.getLongExtra("detail_id", 0L).takeIf { it > 0L })
                }
                var homeworkActivityId by remember {
                    mutableStateOf(intent.getLongExtra("homework_id", 0L).takeIf { it > 0L })
                }

                // 旧版本会话缺少 rawProfile：启动时补拉一次 my-info，供网页注入使用
                LaunchedEffect(session) {
                    val current = session ?: return@LaunchedEffect
                    if (current.rawProfile.length < 10) {
                        runCatching { authRepository.fetchProfile(current.token) }
                            .onSuccess { (profile, raw) ->
                                val refreshed = current.copy(profile = profile, rawProfile = raw)
                                sessionStore.save(refreshed)
                                session = refreshed
                            }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val current = session
                    when {
                        current == null && showSchoolPicker -> SchoolPickerScreen(
                            repository = schoolRepository,
                            current = school,
                            onBack = { showSchoolPicker = false },
                            onSelect = { picked ->
                                schoolStore.save(picked)
                                school = picked
                                showSchoolPicker = false
                            },
                        )

                        current == null && showForgot -> ForgotPasswordScreen(
                            repository = resetRepository,
                            school = school,
                            onBack = { showForgot = false },
                        )

                        current == null -> LoginScreen(
                            repository = authRepository,
                            school = school,
                            onSuccess = { newSession ->
                                sessionStore.save(newSession)
                                session = newSession
                                tab = MainTab.DISCOVER
                            },
                            onForgotPassword = { showForgot = true },
                            onShowDisclaimer = { showDisclaimer = true },
                            onOpenSchoolPicker = { showSchoolPicker = true },
                        )

                        webUrl != null -> WebShell(
                            modifier = Modifier.fillMaxSize(),
                            url = webUrl.orEmpty(),
                            prefill = WebPrefill.build(current),
                            onPickFiles = { callback ->
                                pendingFileCallback?.onReceiveValue(null)
                                pendingFileCallback = callback
                                filePicker.launch("*/*")
                            },
                            onClose = { webUrl = null },
                        )

                        else -> {
                            val scope = rememberCoroutineScope()
                            val pagerState = rememberPagerState { MainTab.entries.size }
                            var discoverRefreshTick by remember { mutableIntStateOf(0) }
                            var discoverReenterTick by remember { mutableIntStateOf(0) }
                            var lastDiscoverTap by remember { mutableLongStateOf(0L) }
                            var switchJob by remember { mutableStateOf<Job?>(null) }

                            // 切页只由一个来源驱动：
                            // 手指滑动 → 只更新 tab；点导航 → 只动 pager。
                            // 动画可能被列表的惯性滚动打断，所以收尾时强制落到目标页，
                            // 不然高亮停在目标页、内容还在原页，看起来就是「切页失败」。
                            fun goTab(target: MainTab) {
                                switchJob?.cancel()
                                switchJob = scope.launch {
                                    if (pagerState.currentPage != target.ordinal) {
                                        runCatching { pagerState.animateScrollToPage(target.ordinal) }
                                    }
                                    if (pagerState.currentPage != target.ordinal) {
                                        runCatching { pagerState.scrollToPage(target.ordinal) }
                                    }
                                    tab = target
                                }
                            }

                            LaunchedEffect(pagerState) {
                                snapshotFlow { pagerState.currentPage }.collect { page ->
                                    tab = MainTab.entries[page]
                                }
                            }

                            // 落定后再算「重新进入发现页」，避免拖动中途就触发
                            LaunchedEffect(pagerState) {
                                var previous = pagerState.settledPage
                                snapshotFlow { pagerState.settledPage }.collect { page ->
                                    if (page == previous) return@collect
                                    previous = page
                                    if (MainTab.entries[page] == MainTab.DISCOVER) discoverReenterTick++
                                }
                            }

                            BackHandler {
                                if (tab != MainTab.DISCOVER) {
                                    if (showMyActivities) {
                                        showMyActivities = false
                                    } else {
                                        goTab(MainTab.DISCOVER)
                                    }
                                } else {
                                    showExitConfirm = true
                                }
                            }

                            Box(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                HorizontalPager(
                                    state = pagerState,
                                    // 三页全部常驻：来回切页不再重建界面，也不会掉回顶部重拉
                                    beyondViewportPageCount = MainTab.entries.size,
                                    modifier = Modifier.weight(1f),
                                ) { page ->
                                    if (page != 0 && !warmUpPages) {
                                        // 另外两页稍后就绪，这里先占位，不参与首帧
                                        Spacer(Modifier.fillMaxSize())
                                    } else when (MainTab.entries[page]) {
                                        MainTab.DISCOVER -> DiscoverScreen(
                                            token = current.token,
                                            repository = activityRepository,
                                            preferences = preferences,
                                            refreshTick = discoverRefreshTick,
                                            reenterTick = discoverReenterTick,
                                            onOpenActivity = { id -> detailActivityId = id },
                                            onOpenBlockRules = {
                                                settingsSection = SettingsSection.BLOCK_RULES
                                                tab = MainTab.SETTINGS
                                            },
                                        )

                                        MainTab.PROFILE -> {
                                            if (showMyActivities) {
                                                MyActivitiesScreen(
                                                    token = current.token,
                                                    repository = activityRepository,
                                                    onBack = { showMyActivities = false },
                                                    onOpenActivity = { id -> detailActivityId = id },
                                                )
                                            } else {
                                                ProfileScreen(
                                                    session = current,
                                                    onOpenWeb = { webUrl = AppConfig.HOME_URL },
                                                    onOpenMyActivities = { showMyActivities = true },
                                                )
                                            }
                                        }

                                        MainTab.SETTINGS -> SettingsScreen(
                                            session = current,
                                            token = current.token,
                                            repository = activityRepository,
                                            preferences = preferences,
                                            section = settingsSection,
                                            onSectionChange = { settingsSection = it },
                                            onLogout = {
                                                sessionStore.clear()
                                                session = null
                                                webUrl = null
                                                tab = MainTab.DISCOVER
                                            },
                                            onShowDisclaimer = { showDisclaimer = true },
                                        )
                                    }
                                }

                                NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                                    MainTab.entries.forEach { item ->
                                        NavigationBarItem(
                                            selected = tab == item,
                                            onClick = {
                                                if (item == tab) {
                                                    when (item) {
                                                        // 双击「发现」：重新拉取列表
                                                        MainTab.DISCOVER -> {
                                                            val now = SystemClock.uptimeMillis()
                                                            if (now - lastDiscoverTap <= 400L) {
                                                                lastDiscoverTap = 0L
                                                                discoverRefreshTick++
                                                            } else {
                                                                lastDiscoverTap = now
                                                            }
                                                        }

                                                        MainTab.SETTINGS -> settingsSection = SettingsSection.ROOT
                                                        MainTab.PROFILE -> Unit
                                                    }
                                                } else {
                                                    goTab(item)
                                                }
                                            },
                                            icon = {
                                                Icon(
                                                    painter = painterResource(item.iconRes),
                                                    contentDescription = item.label,
                                                )
                                            },
                                            label = { Text(item.label) },
                                        )
                                    }
                                }
                            }

                            // 详情做成覆盖层：分页容器留在组合里，返回时列表不重载、位置不丢
                            val detailId = detailActivityId
                            if (detailId != null) {
                                ActivityDetailScreen(
                                    token = current.token,
                                    repository = activityRepository,
                                    activityId = detailId,
                                    onBack = { detailActivityId = null },
                                    onOpenWeb = { id ->
                                        detailActivityId = null
                                        webUrl = "https://${AppConfig.ALLOWED_HOST}/#/ActivityShow/$id"
                                    },
                                    onOpenUrl = { url ->
                                        runCatching {
                                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    },
                                    onOpenHomework = { id, viewing ->
                                        if (viewing) {
                                            // 查看作业用原生页：接口 `activity/query/work_3_0_1` 已实测
                                            homeworkActivityId = id
                                        } else {
                                            detailActivityId = null
                                            webUrl = "https://${AppConfig.ALLOWED_HOST}/#/assignment/$id"
                                        }
                                    },
                                )
                            }

                            // 查看作业覆盖层
                            val homeworkId = homeworkActivityId
                            if (homeworkId != null) {
                                HomeworkScreen(
                                    activityId = homeworkId,
                                    loader = { activityActions.work(current.token, homeworkId) },
                                    onBack = { homeworkActivityId = null },
                                    onEdit = {
                                        homeworkActivityId = null
                                        webUrl = "https://${AppConfig.ALLOWED_HOST}/#/assignment/$homeworkId"
                                    },
                                    onOpenUrl = { url ->
                                        runCatching {
                                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    },
                                )
                            }
                            }
                        }
                    }
                }

                if (showExitConfirm) {
                    AlertDialog(
                        onDismissRequest = { showExitConfirm = false },
                        title = { Text("退出应用？") },
                        text = { Text("按下退出后需要重新打开。") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showExitConfirm = false
                                    finish()
                                },
                            ) {
                                Text("退出")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitConfirm = false }) {
                                Text("取消")
                            }
                        },
                    )
                }

                if (showDisclaimer) {
                    DisclaimerDialog(
                        onAccept = {
                            preferences.disclaimerAccepted = true
                            showDisclaimer = false
                        },
                    )
                }
            }
        }
    }
}
