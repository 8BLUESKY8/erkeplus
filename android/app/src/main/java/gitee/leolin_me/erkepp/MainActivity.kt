package gitee.leolin_me.erkepp

import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import gitee.leolin_me.erkepp.data.ApiClient
import gitee.leolin_me.erkepp.data.AppPreferences
import gitee.leolin_me.erkepp.data.AuthRepository
import gitee.leolin_me.erkepp.data.DeviceId
import gitee.leolin_me.erkepp.data.PasswordResetRepository
import gitee.leolin_me.erkepp.data.SchoolRepository
import gitee.leolin_me.erkepp.data.SchoolStore
import gitee.leolin_me.erkepp.data.SessionStore
import gitee.leolin_me.erkepp.ui.DisclaimerDialog
import gitee.leolin_me.erkepp.ui.ErkeppTheme
import gitee.leolin_me.erkepp.ui.WebShell
import gitee.leolin_me.erkepp.ui.home.HomePlaceholder
import gitee.leolin_me.erkepp.ui.login.ForgotPasswordScreen
import gitee.leolin_me.erkepp.ui.login.LoginScreen
import gitee.leolin_me.erkepp.ui.school.SchoolPickerScreen

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

        val sessionStore = SessionStore(this)
        val preferences = AppPreferences(this)
        val schoolStore = SchoolStore(this)
        val apiClient = ApiClient(deviceId = DeviceId.get(this))
        val repository = AuthRepository(apiClient)
        val resetRepository = PasswordResetRepository(apiClient)
        val schoolRepository = SchoolRepository(apiClient)

        setContent {
            ErkeppTheme {
                var session by remember { mutableStateOf(sessionStore.load()) }
                var school by remember { mutableStateOf(schoolStore.load()) }
                var showWeb by remember { mutableStateOf(false) }
                var showForgot by remember { mutableStateOf(false) }
                var showSchoolPicker by remember { mutableStateOf(false) }
                var showDisclaimer by remember { mutableStateOf(!preferences.disclaimerAccepted) }

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
                            repository = repository,
                            school = school,
                            onSuccess = { newSession ->
                                sessionStore.save(newSession)
                                session = newSession
                            },
                            onForgotPassword = { showForgot = true },
                            onShowDisclaimer = { showDisclaimer = true },
                            onOpenSchoolPicker = { showSchoolPicker = true },
                        )

                        showWeb -> WebShell(
                            modifier = Modifier.fillMaxSize(),
                            onPickFiles = { callback ->
                                pendingFileCallback?.onReceiveValue(null)
                                pendingFileCallback = callback
                                filePicker.launch("*/*")
                            },
                            onClose = { showWeb = false },
                        )

                        else -> HomePlaceholder(
                            session = current,
                            onOpenWeb = { showWeb = true },
                            onLogout = {
                                sessionStore.clear()
                                session = null
                            },
                        )
                    }
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
