package gitee.leolin_me.erkepp

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.webkit.ValueCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import gitee.leolin_me.erkepp.ui.home.DiscoverScreen
import gitee.leolin_me.erkepp.ui.home.MyActivitiesScreen
import gitee.leolin_me.erkepp.ui.home.ProfileScreen
import gitee.leolin_me.erkepp.ui.login.ForgotPasswordScreen
import gitee.leolin_me.erkepp.ui.login.LoginScreen
import gitee.leolin_me.erkepp.ui.school.SchoolPickerScreen
import gitee.leolin_me.erkepp.ui.settings.SettingsScreen
import gitee.leolin_me.erkepp.ui.settings.SettingsSection
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
                var school by remember { mutableStateOf(schoolStore.load()) }
                var webUrl by remember { mutableStateOf<String?>(null) }
                var tab by rememberSaveable { mutableStateOf(MainTab.DISCOVER) }
                var showForgot by remember { mutableStateOf(false) }
                var showSchoolPicker by remember { mutableStateOf(false) }
                var showExitConfirm by remember { mutableStateOf(false) }
                var showDisclaimer by remember { mutableStateOf(!preferences.disclaimerAccepted) }

                var settingsSection by remember { mutableStateOf(SettingsSection.ROOT) }
                var showMyActivities by remember { mutableStateOf(false) }

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

                            // 左右滑动与底部导航双向同步；滑动落定后才切 tab
                            LaunchedEffect(pagerState) {
                                var previous = pagerState.settledPage
                                snapshotFlow { pagerState.settledPage }.collect { page ->
                                    if (page == previous) return@collect
                                    previous = page
                                    tab = MainTab.entries[page]
                                    if (tab == MainTab.DISCOVER) discoverReenterTick++
                                }
                            }
                            LaunchedEffect(tab) {
                                if (pagerState.currentPage != tab.ordinal) {
                                    pagerState.animateScrollToPage(tab.ordinal)
                                }
                            }

                            BackHandler {
                                if (tab != MainTab.DISCOVER) {
                                    if (showMyActivities) {
                                        showMyActivities = false
                                    } else {
                                        tab = MainTab.DISCOVER
                                    }
                                } else {
                                    showExitConfirm = true
                                }
                            }

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
                                            onOpenActivity = { id ->
                                                webUrl = "https://${AppConfig.ALLOWED_HOST}/#/ActivityShow/$id"
                                            },
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
                                                    onOpenActivity = { id ->
                                                        webUrl = "https://${AppConfig.ALLOWED_HOST}/#/ActivityShow/$id"
                                                    },
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
                                                    tab = item
                                                    scope.launch { pagerState.animateScrollToPage(item.ordinal) }
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
