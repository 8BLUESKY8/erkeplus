package gitee.leolin_me.erkepp

import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import gitee.leolin_me.erkepp.data.ApiClient
import gitee.leolin_me.erkepp.data.AuthRepository
import gitee.leolin_me.erkepp.data.DeviceId
import gitee.leolin_me.erkepp.data.SessionStore
import gitee.leolin_me.erkepp.ui.ErkeppTheme
import gitee.leolin_me.erkepp.ui.WebShell
import gitee.leolin_me.erkepp.ui.home.HomePlaceholder
import gitee.leolin_me.erkepp.ui.login.LoginScreen

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
        val repository = AuthRepository(ApiClient(deviceId = DeviceId.get(this)))

        setContent {
            ErkeppTheme {
                var session by remember { mutableStateOf(sessionStore.load()) }
                var showWeb by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val current = session
                    when {
                        current == null -> LoginScreen(
                            repository = repository,
                            onSuccess = { newSession ->
                                sessionStore.save(newSession)
                                session = newSession
                            },
                        )

                        showWeb -> WebShell(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.systemBars),
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
            }
        }
    }
}
