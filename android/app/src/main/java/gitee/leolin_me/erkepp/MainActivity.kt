package gitee.leolin_me.erkepp

import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppLog.init(this)

        val sessionStore = SessionStore(this)
        val preferences = AppPreferences(this)
        val schoolStore = SchoolStore(this)
        val apiClient = ApiClient(deviceId = DeviceId.get(this))
        val authRepository = AuthRepository(apiClient)
        val resetRepository = PasswordResetRepository(apiClient)
        val schoolRepository = SchoolRepository(apiClient)
        val activityRepository = ActivityRepository(apiClient)

        setContent {
            ErkeppTheme {
                var session by remember { mutableStateOf(sessionStore.load()) }
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
                            Box(modifier = Modifier.weight(1f)) {
                                when (tab) {
                                    MainTab.DISCOVER -> DiscoverScreen(
                                        token = current.token,
                                        repository = activityRepository,
                                        preferences = preferences,
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
                                            if (item == MainTab.SETTINGS) {
                                                settingsSection = SettingsSection.ROOT
                                            }
                                            tab = item
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
