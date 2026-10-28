package gitee.leolin_me.erkepp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.webkit.ValueCallback
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import gitee.leolin_me.erkepp.data.attachmentTypeOf
import gitee.leolin_me.erkepp.data.ActivityAttachment
import gitee.leolin_me.erkepp.data.ActivityDetail
import gitee.leolin_me.erkepp.data.ActivityQuery
import gitee.leolin_me.erkepp.data.ActivityPrefetch
import gitee.leolin_me.erkepp.data.ActivityRepository
import gitee.leolin_me.erkepp.data.CreditDetail
import gitee.leolin_me.erkepp.data.CreditRepository
import gitee.leolin_me.erkepp.data.ProfileRepository
import gitee.leolin_me.erkepp.data.RankRepository
import gitee.leolin_me.erkepp.data.RankScope
import gitee.leolin_me.erkepp.data.OrgDetailPrefetch
import gitee.leolin_me.erkepp.data.OrganizationRepository
import gitee.leolin_me.erkepp.data.UserRepository
import gitee.leolin_me.erkepp.data.SignSandboxStore
import gitee.leolin_me.erkepp.data.sandboxSubmit
import gitee.leolin_me.erkepp.data.AttachmentStorage
import gitee.leolin_me.erkepp.data.Downloads
import gitee.leolin_me.erkepp.data.ApiClient
import gitee.leolin_me.erkepp.data.AppPreferences
import gitee.leolin_me.erkepp.data.AuthRepository
import gitee.leolin_me.erkepp.data.DeviceId
import gitee.leolin_me.erkepp.data.OssUploader
import gitee.leolin_me.erkepp.data.PasswordResetRepository
import gitee.leolin_me.erkepp.data.SchoolRepository
import gitee.leolin_me.erkepp.data.SchoolStore
import gitee.leolin_me.erkepp.data.ScanRouter
import gitee.leolin_me.erkepp.data.SessionStore
import gitee.leolin_me.erkepp.data.StorageRepository
import gitee.leolin_me.erkepp.data.WebPrefill
import gitee.leolin_me.erkepp.ui.DisclaimerDialog
import gitee.leolin_me.erkepp.ui.ErkeppTheme
import gitee.leolin_me.erkepp.ui.SecondaryPage
import gitee.leolin_me.erkepp.ui.WebShell
import gitee.leolin_me.erkepp.ui.attachments.AttachmentActionsSheet
import gitee.leolin_me.erkepp.ui.attachments.AttachmentsScreen
import gitee.leolin_me.erkepp.ui.attachments.ImagePreviewScreen
import gitee.leolin_me.erkepp.ui.detail.ActivityDetailScreen
import gitee.leolin_me.erkepp.ui.detail.HomeworkEditScreen
import gitee.leolin_me.erkepp.ui.detail.HomeworkScreen
import gitee.leolin_me.erkepp.ui.detail.compressForUpload
import gitee.leolin_me.erkepp.ui.home.DiscoverScreen
import gitee.leolin_me.erkepp.ui.home.ProfileScreen
import gitee.leolin_me.erkepp.ui.login.ForgotPasswordScreen
import gitee.leolin_me.erkepp.ui.login.LoginScreen
import gitee.leolin_me.erkepp.ui.more.MoreScreen
import gitee.leolin_me.erkepp.ui.org.AllOrganizationsScreen
import gitee.leolin_me.erkepp.ui.org.OrgDetailScreen
import gitee.leolin_me.erkepp.ui.org.OrganizationScreen
import gitee.leolin_me.erkepp.ui.profile.ChangeMobileScreen
import gitee.leolin_me.erkepp.ui.profile.ChangePasswordScreen
import gitee.leolin_me.erkepp.ui.profile.CreditScreen
import gitee.leolin_me.erkepp.ui.profile.PersonalInfoScreen
import gitee.leolin_me.erkepp.ui.profile.PersonActivitiesScreen
import gitee.leolin_me.erkepp.ui.profile.UserProfileScreen
import gitee.leolin_me.erkepp.ui.profile.RankScreen
import gitee.leolin_me.erkepp.ui.profile.TagsScreen
import gitee.leolin_me.erkepp.ui.school.SchoolPickerScreen
import gitee.leolin_me.erkepp.ui.settings.BlockRulesScreen
import gitee.leolin_me.erkepp.ui.settings.SignLogScreen
import gitee.leolin_me.erkepp.ui.settings.SignSandboxScreen
import gitee.leolin_me.erkepp.ui.settings.SettingsScreen
import gitee.leolin_me.erkepp.ui.settings.StorageScreen
import gitee.leolin_me.erkepp.ui.settings.SettingsSection
import gitee.leolin_me.erkepp.ui.sign.SignScanScreen
import gitee.leolin_me.erkepp.ui.sign.SignTarget
import gitee.leolin_me.erkepp.ui.sign.decodeQrFromBitmap
import gitee.leolin_me.erkepp.data.parseSignCode
import gitee.leolin_me.erkepp.data.SignOutcome
import gitee.leolin_me.erkepp.data.SignTicket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

enum class MainTab(val label: String, val iconRes: Int) {
    DISCOVER("发现", R.drawable.ic_explore),
    PROFILE("我的", R.drawable.ic_person),
    MORE("更多", R.drawable.ic_more),
    SETTINGS("设置", R.drawable.ic_settings),
}

/**
 * 本地会话里个人资料的结构版本。
 *
 * 每次给 `my-info` 加新字段（头像、学分…）就 +1：老会话缺少这些值，进「我的」时会补拉一次，
 * 拉完写回并记下版本，之后不再重复请求。
 */
private const val PROFILE_SCHEMA = 1

class MainActivity : ComponentActivity() {

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null

    /** 提交作业时拍照写到的临时文件；拍完读出来上传。 */
    private var pendingPhotoFile: java.io.File? = null

    /** 待上传的作业附件：启动器只管把 uri 丢进来，真正的上传在界面里做（要 token）。 */
    private val pendingUpload = androidx.compose.runtime.mutableStateOf<Pair<Uri, String>?>(null)

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
    }

    /** 相册/文件选了东西：交给界面压缩后上传。 */
    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingUpload.value = uri to (displayNameOf(uri) ?: "file-${System.currentTimeMillis()}")
        }
    }

    /** 从内容提供者拿文件名，拿不到就用兜底名。 */
    private fun displayNameOf(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
    }.getOrNull()

    /** 拍照：相机把照片写进 FileProvider 给的临时文件，回来再读。 */
    private val cameraCapture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        // 相机在前台时本进程可能被回收，回来重建后字段是空的：退回到「最近一张」兜底
        val file = pendingPhotoFile ?: newestCameraPhoto()
        pendingPhotoFile = null
        AppLog.d("hw", "camera result ok=$ok file=${file?.name}")
        if (ok && file != null && file.isFile) pendingUpload.value = Uri.fromFile(file) to file.name
    }

    /** cache/camera 里 10 分钟内最新的一张，用来兜底进程被回收的情况。 */
    private fun newestCameraPhoto(): java.io.File? = runCatching {
        java.io.File(cacheDir, "camera").listFiles()
            ?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }
            ?.takeIf { System.currentTimeMillis() - it.lastModified() < 10 * 60_000L }
    }.getOrNull()

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
        val creditRepository = CreditRepository(apiClient)
        val rankRepository = RankRepository(apiClient)
        val organizationRepository = OrganizationRepository(apiClient)
        val profileRepository = ProfileRepository(apiClient)
        val userRepository = UserRepository(apiClient)
        val storageRepository = StorageRepository()
        val ossUploader = OssUploader(apiClient)
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
                val probe = remember { if (BuildConfig.DEBUG) intent.getStringExtra("probe") else null }
                // 调试用：`am start ... --es search 挑战杯` 预填发现页搜索词，免去手打
                val debugSearch = remember {
                    if (BuildConfig.DEBUG) intent.getStringExtra("search").orEmpty() else ""
                }
                var school by remember { mutableStateOf(schoolStore.load()) }
                var webUrl by remember { mutableStateOf<String?>(null) }
                var tab by rememberSaveable { mutableStateOf(MainTab.DISCOVER) }
                var showForgot by remember { mutableStateOf(false) }
                var showSchoolPicker by remember { mutableStateOf(false) }
                var showDisclaimer by remember { mutableStateOf(!preferences.disclaimerAccepted) }

                var settingsSection by remember { mutableStateOf(SettingsSection.ROOT) }
                var showCredit by remember { mutableStateOf(false) }
                var showRank by remember { mutableStateOf(false) }
                // 调试用：`--es probe rank_page --es level 6` 直接开指定范围的榜单
                var rankLevel by remember { mutableIntStateOf(10) }
                var showPersonalInfo by remember { mutableStateOf(false) }
                var showChangePassword by remember { mutableStateOf(false) }
                var showChangeMobile by remember { mutableStateOf(false) }
                var showTags by remember { mutableStateOf(false) }
                var showStorage by remember { mutableStateOf(false) }
                // 「更多」里的组织：所有组织目录、只认组织码的扫码页
                var showAllOrgs by remember { mutableStateOf(false) }
                var showJoinScan by remember { mutableStateOf(false) }
                // 「我的」里的我的组织，和它点开的详情
                var showOrganizations by remember { mutableStateOf(false) }
                var orgDetailTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }
                // 别人的主页（官方 #/userShows/<id>），以及它点开的「参加的活动」
                var userPageTarget by remember { mutableStateOf<Long?>(null) }
                var personActivitiesTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }
                // 申请提交后让「我的组织」重新拉，才能看到新的待审核
                var orgListTick by remember { mutableIntStateOf(0) }
                var pendingTagIds by remember { mutableStateOf<List<Int>>(emptyList()) }
                var pendingMobile by remember { mutableStateOf("") }
                var personalInfoTick by remember { mutableIntStateOf(0) }
                // 「我的」上的诚信值摘要：进这一页时拉一次，失败就显示「—」，不挡别的入口
                var creditDetail by remember { mutableStateOf<CreditDetail?>(null) }
                // 支持 `am start ... --el detail_id 566506` 直接开详情，方便调试和截图
                var detailActivityId by remember {
                    mutableStateOf(intent.getLongExtra("detail_id", 0L).takeIf { it > 0L })
                }
                var homeworkActivityId by remember {
                    mutableStateOf(intent.getLongExtra("homework_id", 0L).takeIf { it > 0L })
                }
                var attachmentsVisible by remember { mutableStateOf(intent.getBooleanExtra("attachments", false)) }
                var attachmentFiles by remember { mutableStateOf<List<AttachmentStorage.LocalFile>>(emptyList()) }
                var trashFiles by remember { mutableStateOf<List<AttachmentStorage.LocalFile>>(emptyList()) }
                var previewNames by remember { mutableStateOf<List<String>>(emptyList()) }
                var previewIndex by remember { mutableIntStateOf(0) }
                var pendingDownload by remember { mutableStateOf<ActivityAttachment?>(null) }
                var attachmentSheet by remember { mutableStateOf<ActivityAttachment?>(null) }
                var homeworkEditId by remember { mutableStateOf<Long?>(null) }
                var homeworkTitle by remember { mutableStateOf("") }
                var signScan by remember { mutableStateOf<SignTarget?>(null) }
                var showHomeScan by remember { mutableStateOf(false) }
                var presetSignText by remember { mutableStateOf<String?>(null) }
                // 离线签到沙盒：假的详情数据 + 是否走本地提交
                var sandboxConfig by remember { mutableStateOf(SignSandboxStore.load(this)) }
                var sandboxDetail by remember { mutableStateOf<ActivityDetail?>(null) }
                var signScanSandbox by remember { mutableStateOf(false) }
                // 签到成功后让详情重拉一次，及时显示「已签到」
                var detailRefreshTick by remember { mutableIntStateOf(0) }
                var attachmentsInTrash by remember { mutableStateOf(false) }
                var pendingPreviewName by remember { mutableStateOf<String?>(null) }
                val downloadTasks by Downloads.tasks.collectAsState()
                val activity = this

                fun shareFile(file: AttachmentStorage.LocalFile) {
                    runCatching {
                        startActivity(
                            Intent(Intent.ACTION_SEND)
                                .setType(file.mime)
                                .putExtra(Intent.EXTRA_STREAM, AttachmentStorage.shareableUri(activity, file))
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        )
                    }
                }

                fun openAttachmentFile(file: AttachmentStorage.LocalFile) {
                    if (file.mime.startsWith("image/")) {
                        val images = attachmentFiles.filter { it.mime.startsWith("image/") }.map { it.name }
                        previewNames = images
                        previewIndex = images.indexOf(file.name).coerceAtLeast(0)
                    } else {
                        runCatching {
                            startActivity(
                                Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(AttachmentStorage.shareableUri(activity, file), file.mime)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }

                fun downloadAttachment(attachment: ActivityAttachment) {
                    Downloads.enqueue(activity, attachment.url, attachment.name, attachment.size)
                    Toast.makeText(activity, "已开始下载，进度见「我的 → 附件」", Toast.LENGTH_SHORT).show()
                }

                fun handleAttachmentTap(attachment: ActivityAttachment) {
                    // 下载、预览、分享、其他应用打开都在面板里选，下载必须由用户点一下才发生
                    attachmentSheet = attachment
                }

                LaunchedEffect(attachmentsVisible, downloadTasks.values.map { it.state }) {
                    if (attachmentsVisible) attachmentFiles = AttachmentStorage.list(activity)
                }

                // 「下载并预览」：这一项下载完成后自动打开预览
                LaunchedEffect(downloadTasks) {
                    val name = pendingPreviewName ?: return@LaunchedEffect
                    val finished = downloadTasks.values.any {
                        it.name == name && it.state == Downloads.State.DONE
                    }
                    if (!finished) return@LaunchedEffect
                    pendingPreviewName = null
                    attachmentFiles = AttachmentStorage.list(activity)
                    attachmentFiles.firstOrNull { it.name == name }?.let { openAttachmentFile(it) }
                }

                LaunchedEffect(probe, session) {
                    if (!BuildConfig.DEBUG || probe == null) return@LaunchedEffect
                    val current = session ?: return@LaunchedEffect
                    val detailId = intent.getLongExtra("detail_id", 0L)
                    AppLog.d("probe", "start=$probe detail=$detailId")
                    when (probe) {
                        "homework_list" -> activityActions.homeworkList(current.token)
                        "sign_dict" -> activityRepository.signWays(current.token)
                        "sign_records" -> activityActions.mySignRecords(current.token)
                        "sign_history" -> if (detailId > 0) {
                            activityActions.signHistoryRaw(current.token, detailId, current.profile.userId)
                        }
                        "scan_image" -> {
                            val file = java.io.File(activity.filesDir, "qr.png")
                            val text = if (file.exists()) {
                                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                    ?.let { decodeQrFromBitmap(it) }
                            } else {
                                null
                            }
                            AppLog.d("probe", "scan_image text=$text ticket=${text?.let { parseSignCode(it) }}")
                        }
                        "sign_count" -> if (detailId > 0) activityActions.signCount(current.token, detailId)
                        "work_detail" -> if (detailId > 0) activityActions.workDetail(current.token, detailId)
                        "download" -> {
                            val url = intent.getStringExtra("url")
                            val name = intent.getStringExtra("name") ?: "probe.bin"
                            if (url != null) {
                                Downloads.enqueue(activity, url, name, intent.getLongExtra("size", 0L))
                            }
                        }
                        "attachments" -> {
                            attachmentFiles = AttachmentStorage.list(activity)
                            attachmentsVisible = true
                        }
                        "attachments_grid" -> {
                            preferences.attachmentsGridView = true
                            attachmentFiles = AttachmentStorage.list(activity)
                            attachmentsVisible = true
                        }
                        "restore_test" -> {
                            AttachmentStorage.listTrash(activity).forEach { AttachmentStorage.restore(activity, it) }
                            attachmentFiles = AttachmentStorage.list(activity)
                            trashFiles = AttachmentStorage.listTrash(activity)
                            AppLog.d("storage", "restore_test files=${attachmentFiles.size} trash=${trashFiles.size}")
                        }                        "trash_test" -> {
                            val name = intent.getStringExtra("name").orEmpty()
                            val file = AttachmentStorage.find(activity, name)
                            val moved = file?.let { AttachmentStorage.trash(activity, it) }
                            attachmentFiles = AttachmentStorage.list(activity)
                            trashFiles = AttachmentStorage.listTrash(activity)
                            AppLog.d(
                                "storage",
                                "trash_test name=$name moved=$moved files=${attachmentFiles.size} trash=${trashFiles.size}",
                            )
                            attachmentsVisible = true
                        }
                        "sandbox" -> sandboxDetail = sandboxConfig.toDetail()
                        // 沙盒 + 真相机：扫屏幕上的二维码（不带预设文本）
                        "sandbox_camera" -> {
                            sandboxConfig = SignSandboxStore.load(activity)
                            sandboxDetail = sandboxConfig.toDetail()
                            signScanSandbox = true
                            signScan = SignTarget(
                                activityId = sandboxConfig.activityId,
                                activityName = sandboxConfig.name,
                                signSwitch = sandboxConfig.signSwitch,
                            )
                        }
                        // 沙盒整链路：假详情 → 扫码页（文本取自 files/qr.png）
                        "sandbox_scan" -> {
                            sandboxConfig = SignSandboxStore.load(activity)
                            sandboxDetail = sandboxConfig.toDetail()
                            signScanSandbox = true
                            signScan = SignTarget(
                                activityId = sandboxConfig.activityId,
                                activityName = sandboxConfig.name,
                                signSwitch = sandboxConfig.signSwitch,
                            )
                            val qrFile = java.io.File(activity.filesDir, "qr.png")
                            presetSignText = if (qrFile.exists()) {
                                android.graphics.BitmapFactory.decodeFile(qrFile.absolutePath)
                                    ?.let { decodeQrFromBitmap(it) }
                            } else {
                                null
                            }
                        }
                        "sign_scan" -> if (detailId > 0) {
                            val loaded = runCatching { activityRepository.detail(current.token, detailId) }.getOrNull()
                            signScan = SignTarget(detailId, loaded?.name.orEmpty(), loaded?.signSwitch ?: 0)
                            // 有 files/qr.png 就直接当成本次扫到的码，免去对着屏幕扫
                            val qrFile = java.io.File(activity.filesDir, "qr.png")
                            presetSignText = if (qrFile.exists()) {
                                android.graphics.BitmapFactory.decodeFile(qrFile.absolutePath)
                                    ?.let { decodeQrFromBitmap(it) }
                            } else {
                                null
                            }
                            AppLog.d("probe", "sign_scan preset=$presetSignText")
                        }
                        "homework_edit" -> if (detailId > 0) homeworkEditId = detailId
                        "attachment_sheet" -> {
                            val url = intent.getStringExtra("url")
                            val name = intent.getStringExtra("name") ?: "probe.png"
                            if (url != null) {
                                attachmentSheet = ActivityAttachment(
                                    name = name,
                                    url = url,
                                    type = 1,
                                    size = intent.getLongExtra("size", 0L),
                                )
                            }
                        }
                        "stack_detail" -> {
                            // 复现「从列表点进详情」的层级：详情压在主页之上
                            if (detailId > 0) detailActivityId = detailId
                        }
                        "block_rules" -> settingsSection = SettingsSection.BLOCK_RULES
                        // 调试用：按给定参数提交一次作业，定位服务端 10000 的来源
                        "submit_test" -> {
                            val activityId = intent.getLongExtra("activity_id", 573570L)
                            val contentFile = intent.getStringExtra("content_file")
                            val content = if (contentFile != null) {
                                java.io.File(contentFile).readText()
                            } else {
                                intent.getStringExtra("content").orEmpty()
                            }
                            val urlsFile = intent.getStringExtra("urls_file")
                            val urls = urlsFile
                                ?.let { file ->
                                    java.io.File(file).readLines()
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                }
                                .orEmpty()
                            AppLog.d("probe", "submit_test id=$activityId chars=${content.length} files=${urls.size}")
                            val result = runCatching {
                                activityActions.submitWork(
                                    current.token,
                                    activityId,
                                    content,
                                    urls.map { url ->
                                        ActivityAttachment(name = url.substringAfterLast('/'), url = url, type = 1, size = 0)
                                    },
                                )
                            }
                            AppLog.d("probe", "submit_test result=${result.exceptionOrNull()?.message ?: "ok"}")
                        }
                        // 调试用：验证 STS + OSS 上传链路（传一张 1x1 的图再删掉）
                        "oss_test" -> {
                            val bytes = android.util.Base64.decode(
                                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
                                android.util.Base64.DEFAULT,
                            )
                            val url = ossUploader.upload(current.token, bytes, "probe.png", "image/png")
                            AppLog.d("probe", "oss_test url=$url")
                            val deleted = ossUploader.delete(current.token, url)
                            AppLog.d("probe", "oss_test delete=$deleted")
                        }
                        // 调试用：把「已报名」列表打出来，核对某活动在报名列表里的状态
                        "mylist_dump" -> {
                            val pages = intent.getStringExtra("pages")?.toIntOrNull() ?: 6
                            for (p in 1..pages) {
                                val result = runCatching {
                                    activityRepository.myList(current.token, p)
                                }.getOrNull() ?: break
                                AppLog.d(
                                    "probe",
                                    "mylist_dump page=$p total=${result.total} got=${result.items.size}",
                                )
                                result.items.forEach { item ->
                                    AppLog.d(
                                        "probe",
                                        "mylist_item id=${item.id} st=${item.status} " +
                                            "start=${item.startTime} end=${item.endTime} name=${item.name}",
                                    )
                                }
                                if (result.items.isEmpty()) break
                            }
                        }
                        // 调试用：把某个 status 的前几页原样打出来，核对「某活动为什么不在列表里」
                        "list_dump" -> {
                            val status = intent.getStringExtra("status") ?: "5,6,7,8"
                            val pages = intent.getStringExtra("pages")?.toIntOrNull() ?: 5
                            for (p in 1..pages) {
                                val result = runCatching {
                                    activityRepository.list(current.token, ActivityQuery(page = p, status = status))
                                }.getOrNull() ?: break
                                AppLog.d(
                                    "probe",
                                    "list_dump status=$status page=$p total=${result.total} got=${result.items.size}",
                                )
                                result.items.forEach { item ->
                                    AppLog.d(
                                        "probe",
                                        "list_item id=${item.id} st=${item.status} " +
                                            "start=${item.startTime} end=${item.endTime} " +
                                            "enrollEnd=${item.enrollEndTime} name=${item.name}",
                                    )
                                }
                                if (result.items.isEmpty()) break
                            }
                        }
                        "storage" -> showStorage = true
                        "rank_page" -> {
                            rankLevel = intent.getStringExtra("level")?.toIntOrNull() ?: 10
                            showRank = true
                        }
                        "rank_all" -> rankRepository.dumpAll(
                            token = current.token,
                            scope = RankScope.of(intent.getStringExtra("level")?.toIntOrNull() ?: 10),
                        )
                        // 排行榜原始响应：只看字段，方便核对解析
                        "rank_raw" -> rankRepository.rawDump(
                            token = current.token,
                            level = intent.getStringExtra("level")?.toIntOrNull() ?: 10,
                            page = intent.getStringExtra("page")?.toIntOrNull() ?: 1,
                            pageSize = intent.getStringExtra("size")?.toIntOrNull() ?: 20,
                        )
                        // 组织这一族接口的原始响应：只看字段，方便核对解析
                        "org_probe" -> organizationRepository.rawDump(
                            token = current.token,
                            orgId = intent.getLongExtra("org_id", 39261L),
                        )
                        // 通知这一族的原始响应：为「消息做成真通知」做接口核对（只读）
                        "notify_probe" -> {
                            val unread = runCatching {
                                apiClient.get("message/notice/un-read-count", buildJsonObject {}, current.token)
                            }
                            AppLog.d(
                                "notify",
                                "raw unread " + unread.map { it.toString().take(800) }
                                    .getOrElse { "ERR ${it.message}" },
                            )
                            val list = runCatching {
                                apiClient.get(
                                    "message/notice/list",
                                    buildJsonObject {
                                        put("pageNum", JsonPrimitive(1))
                                        put("pageSize", JsonPrimitive(10))
                                    },
                                    current.token,
                                )
                            }
                            AppLog.d(
                                "notify",
                                "raw list " + list.map { it.toString().take(4000) }
                                    .getOrElse { "ERR ${it.message}" },
                            )
                        }
                        // 人物详情：官方 #/userShows/<id> 用的那一个接口，先只看字段
                        "user_probe" -> {
                            val userId = intent.getLongExtra("user_id", 1908337L)
                            val raw = runCatching {
                                apiClient.get(
                                    "message/chat/follow/user/info",
                                    buildJsonObject { put("id", JsonPrimitive(userId)) },
                                    current.token,
                                )
                            }
                            AppLog.d(
                                "user",
                                "raw id=$userId " + raw.map { it.toString().take(3000) }
                                    .getOrElse { "ERR ${it.message}" },
                            )
                        }
                        "orgs" -> showOrganizations = true
                        "user_page" -> userPageTarget = intent.getLongExtra("user_id", 1908337L)
                        // 别人的活动列表：官方 #/myAcitive/:id 用的接口
                        "user_acts" -> {
                            val userId = intent.getLongExtra("user_id", 1908337L)
                            val raw = runCatching {
                                apiClient.get(
                                    "activity/other-list",
                                    buildJsonObject {
                                        put("pageNum", JsonPrimitive(1))
                                        put("pageSize", JsonPrimitive(5))
                                        put("userId", JsonPrimitive(userId))
                                    },
                                    current.token,
                                )
                            }
                            AppLog.d(
                                "user",
                                "acts id=$userId " + raw.map { it.toString().take(2500) }
                                    .getOrElse { "ERR ${it.message}" },
                            )
                        }
                        "all_orgs" -> showAllOrgs = true
                        "org_detail" -> orgDetailTarget =
                            intent.getLongExtra("org_id", 39261L) to
                                intent.getStringExtra("org_name").orEmpty()
                        "join_scan" -> showJoinScan = true
                        "preview_image" -> {
                            attachmentFiles = AttachmentStorage.list(activity)
                            val name = intent.getStringExtra("name")
                            val images = attachmentFiles.filter { it.mime.startsWith("image/") }.map { it.name }
                            previewNames = images
                            previewIndex = images.indexOf(name).coerceAtLeast(0)
                        }
                        else -> AppLog.d("probe", "unknown probe=$probe")
                    }
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

                        else -> {
                            val scope = rememberCoroutineScope()
                            // 预取：点下去那一刻就把详情请求发出去，入场动画那 180ms 正好用来跑网络。
                            // 页面就位后先看有没有现成数据，有就直接画出来
                            val openActivity: (Long) -> Unit = { id ->
                                scope.launch {
                                    runCatching { activityRepository.detail(current.token, id) }
                                        .onSuccess { ActivityPrefetch.put(id, it) }
                                }
                                detailActivityId = id
                            }
                            val openOrg: (Long, String) -> Unit = { id, name ->
                                scope.launch {
                                    runCatching { organizationRepository.detail(current.token, id) }
                                        .onSuccess { OrgDetailPrefetch.put(id, it) }
                                }
                                orgDetailTarget = id to name
                            }
                            val pagerState = rememberPagerState { MainTab.entries.size }
                            var discoverRefreshTick by remember { mutableIntStateOf(0) }
                            var discoverReenterTick by remember { mutableIntStateOf(0) }
                            var lastDiscoverTap by remember { mutableLongStateOf(0L) }
                            var switchJob by remember { mutableStateOf<Job?>(null) }

                            // 作业附件：本次新上传的（提交时会和已有的一起发出去）
                            val homeworkAdded = remember { mutableStateListOf<ActivityAttachment>() }
                            var homeworkUploading by remember { mutableStateOf(false) }
                            LaunchedEffect(homeworkEditId) {
                                homeworkAdded.clear()
                                homeworkUploading = false
                            }

                            // 拍照/选图之后在这里统一压缩 + 上传，失败用 Toast 说一声
                            val uploadRequest = pendingUpload.value
                            LaunchedEffect(uploadRequest) {
                                val request = uploadRequest ?: return@LaunchedEffect
                                homeworkUploading = true
                                AppLog.d("hw", "upload start uri=${request.first} name=${request.second}")
                                val result = runCatching {
                                    val (uri, name) = request
                                    val raw = activity.contentResolver.openInputStream(uri)
                                        ?.use { it.readBytes() } ?: error("读不到这个文件")
                                    val guessed = activity.contentResolver.getType(uri)
                                    val mime = guessed ?: if (name.endsWith(".png", true)) "image/png" else "image/jpeg"
                                    val bytes = if (mime.startsWith("image/")) compressForUpload(raw) else raw
                                    if (bytes.size > 5 * 1024 * 1024) error("单个附件不能超过 5MB")
                                    val url = ossUploader.upload(current.token, bytes, name, mime)
                                    ActivityAttachment(
                                        name = name,
                                        url = url,
                                        type = attachmentTypeOf(mime),
                                        size = bytes.size.toLong(),
                                    )
                                }
                                homeworkUploading = false
                                // 传完再清：一开头就清会把 key 改掉，等于自己取消自己
                                pendingUpload.value = null
                                // 拍完的原图留在 cache 里没用，传完就删
                                if (request.first.scheme == "file") {
                                    runCatching { java.io.File(request.first.path.orEmpty()).delete() }
                                }
                                result.onSuccess { homeworkAdded.add(it) }
                                    .onFailure {
                                        AppLog.d("hw", "upload failed: ${it.message}")
                                        Toast.makeText(
                                            activity,
                                            "上传失败：${it.message ?: "请重试"}",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                            }

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

                            // 进「我的」时顺带拉一次诚信值，用来在统计卡上显示分数
                            LaunchedEffect(tab) {
                                if (tab == MainTab.PROFILE && creditDetail == null) {
                                    creditDetail = runCatching { creditRepository.detail(current.token) }
                                        .getOrNull()
                                }
                            }

                            // 头像 / 年级这些字段在旧会话里没有：进「我的」时补拉一次并落盘，
                            // 之后就一直用缓存，只有点「刷新资料」才会再取
                            // 刷新会话里缓存的资料。
                            // 落盘要在 IO 线程做：写的是 SharedPreferences + AndroidKeyStore 加密，
                            // 放在主线程上会让「点开详细资料」先卡一下。
                            fun reloadProfile() {
                                val s = session ?: return
                                scope.launch {
                                    runCatching { authRepository.fetchProfile(s.token) }
                                        .onSuccess { (profile, raw) ->
                                            val updated = s.copy(profile = profile, rawProfile = raw)
                                            session = updated
                                            withContext(Dispatchers.IO) { sessionStore.save(updated) }
                                            preferences.profileSchema = PROFILE_SCHEMA
                                        }
                                }
                            }
                            LaunchedEffect(tab, preferences.profileSchema) {
                                val s = session ?: return@LaunchedEffect
                                // 老会话里没有头像 / 学分：补拉一次并落盘，之后走缓存
                                if (tab == MainTab.PROFILE && preferences.profileSchema < PROFILE_SCHEMA) {
                                    runCatching { authRepository.fetchProfile(s.token) }
                                        .onSuccess { (profile, raw) ->
                                            val updated = s.copy(profile = profile, rawProfile = raw)
                                            session = updated
                                            withContext(Dispatchers.IO) { sessionStore.save(updated) }
                                            preferences.profileSchema = PROFILE_SCHEMA
                                        }
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

                            // 底部 tab 的返回：不在「发现」页时先回发现页。
                            //
                            // 这里必须用 PredictiveBackHandler 而不是 BackHandler：后者在系统眼里
                            // 「没有动画」，手势一开始系统就会放它自己那套「退出应用」的预览——
                            // 画面往右下缩、后面露黑，跟实际结果（只是切个 tab）对不上。
                            // 注册成动画回调后系统不再插手，拖动期间画面保持不动，松手才滑回发现页。
                            PredictiveBackHandler(enabled = tab != MainTab.DISCOVER) { events ->
                                try {
                                    events.collect { }
                                    goTab(MainTab.DISCOVER)
                                } catch (cancelled: CancellationException) {
                                    // 手势中途松手：什么都不用做，页没动过
                                    throw cancelled
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
                                            initialSearch = debugSearch,
                                            onOpenActivity = { id -> openActivity(id) },
                                            onOpenBlockRules = {
                                                settingsSection = SettingsSection.BLOCK_RULES
                                                // 必须走 goTab：只改 tab 的话 pager 还停在发现页，
                                                // 返回后就成了「底部高亮在设置、页面还是发现页」
                                                goTab(MainTab.SETTINGS)
                                            },
                                            onOpenScan = { showHomeScan = true },
                                            adsActive = pagerState.settledPage == MainTab.DISCOVER.ordinal && webUrl == null,
                                            onOpenAd = { target ->
                                                // 外部广告不经过会注入官方登录态的 WebShell。
                                                runCatching {
                                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                                                }.onFailure {
                                                    Toast.makeText(this@MainActivity, "无法打开链接，请安装浏览器后重试", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                        )

                                        MainTab.PROFILE -> ProfileScreen(
                                            session = current,
                                            credit = creditDetail,
                                            onOpenPersonalInfo = {
                                                showPersonalInfo = true
                                                // 顺便刷新会话里的头像与学分，回来时「我的」也是新的
                                                reloadProfile()
                                            },
                                            onOpenCredit = { showCredit = true },
                                            onOpenRank = { showRank = true },
                                            onOpenOrganizations = { showOrganizations = true },
                                            onOpenAttachments = { attachmentsVisible = true },
                                            onOpenStorage = { showStorage = true },
                                            onOpenWeb = { webUrl = AppConfig.HOME_URL },
                                        )

                                        MainTab.MORE -> MoreScreen(
                                            onOpenAllOrganizations = { showAllOrgs = true },
                                            onJoinOrganization = { showJoinScan = true },
                                            onOpenPersonalInfo = {
                                                showPersonalInfo = true
                                                reloadProfile()
                                            },
                                            onOpenWeb = { webUrl = AppConfig.HOME_URL },
                                        )

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
                                                        MainTab.PROFILE, MainTab.MORE -> Unit
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

                            // 覆盖层按「谁压谁」的顺序写：先出现的在下，后出现的在上
                            // 我的活动、屏蔽规则、附件 → 详情 → 作业 → 图片预览

                            // 首页扫一扫：签到码直接走签到流程，活动分享码打开详情
                            // SignScanScreen 自己带一层 SecondaryPage，外面不要再套：
                            // 两层叠着手势返回时系统会以为没人接，先播一遍「应用退出」的预览（露黑）
                            if (showHomeScan) {
                                SignScanScreen(
                                    target = SignTarget(0L, "", 0),
                                    title = "扫一扫",
                                    developerMode = BuildConfig.DEBUG && preferences.developerMode,
                                    submit = { ticket -> activityActions.sign(current.token, ticket) },
                                    resolveName = { id ->
                                        runCatching {
                                            activityRepository.detail(current.token, id).name
                                        }.getOrNull()
                                    },
                                    onOtherText = { text ->
                                        val id = ScanRouter.activityIdFromShareLink(text)
                                        val orgId = ScanRouter.organizationId(text)
                                        when {
                                            id != null -> {
                                                showHomeScan = false
                                                openActivity(id)
                                                true
                                            }

                                            orgId != null -> {
                                                showHomeScan = false
                                                openOrg(orgId, "")
                                                true
                                            }

                                            else -> false
                                        }
                                    },
                                    onBack = { showHomeScan = false },
                                    onSigned = {
                                        Toast.makeText(activity, "签到已提交", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }

                            if (settingsSection == SettingsSection.BLOCK_RULES) {
                                SecondaryPage(onBack = { settingsSection = SettingsSection.ROOT }) {
                                    BlockRulesScreen(
                                        preferences = preferences,
                                        onBack = { settingsSection = SettingsSection.ROOT },
                                    )
                                }
                            }

                            // 签到沙盒 / 签到日志 / 沙盒详情：只在 debug 包存在。
                            // BuildConfig.DEBUG 是常量，release 里这几段会被 R8 整体裁掉。
                            if (BuildConfig.DEBUG && settingsSection == SettingsSection.SIGN_SANDBOX) {
                                SecondaryPage(onBack = { settingsSection = SettingsSection.ROOT }) {
                                    SignSandboxScreen(
                                        config = sandboxConfig,
                                        onConfigChange = { updated ->
                                            sandboxConfig = updated
                                            SignSandboxStore.save(activity, updated)
                                        },
                                        onOpenDetail = { sandboxDetail = sandboxConfig.toDetail() },
                                        onBack = { settingsSection = SettingsSection.ROOT },
                                    )
                                }
                            }

                            // 签到日志：现场出问题能一键复制带回来
                            if (BuildConfig.DEBUG && settingsSection == SettingsSection.SIGN_LOG) {
                                SecondaryPage(onBack = { settingsSection = SettingsSection.ROOT }) {
                                    SignLogScreen(onBack = { settingsSection = SettingsSection.ROOT })
                                }
                            }

                            // 沙盒的假详情页：不联网，底部动作条也隐藏；release 里不编译进来
                            val fakeDetail = if (BuildConfig.DEBUG) sandboxDetail else null
                            if (fakeDetail != null) {
                                SecondaryPage(onBack = { sandboxDetail = null }) {
                                    ActivityDetailScreen(
                                        token = current.token,
                                        repository = activityRepository,
                                        activityId = fakeDetail.id,
                                        onBack = { sandboxDetail = null },
                                        onOpenWeb = {},
                                        onOpenUrl = {},
                                        onOpenHomework = { _, _, _ -> },
                                        onOpenAttachment = {},
                                        onOpenSignScan = { target ->
                                            signScanSandbox = true
                                            signScan = target
                                        },
                                        detailOverride = fakeDetail,
                                        sandbox = true,
                                    )
                                }
                            }

                            if (attachmentsVisible) {
                                SecondaryPage(onBack = {
                                    // 回收站里返回先回附件页，再返回才离开
                                    if (attachmentsInTrash) {
                                        attachmentsInTrash = false
                                    } else {
                                        attachmentsVisible = false
                                    }
                                }) {
                                    AttachmentsScreen(
                                        files = attachmentFiles,
                                        downloads = downloadTasks,
                                        trashFiles = trashFiles,
                                        gridView = preferences.attachmentsGridView,
                                        deleteToTrash = preferences.deleteToTrash,
                                        trashSupported = AttachmentStorage.trashSupported(),
                                        inTrash = attachmentsInTrash,
                                        onInTrashChange = { attachmentsInTrash = it },
                                        onBack = { attachmentsVisible = false },
                                        onToggleView = {
                                            preferences.attachmentsGridView = !preferences.attachmentsGridView
                                        },
                                        onDeleteToTrashChange = { preferences.deleteToTrash = it },
                                        onPreviewImage = { names, index ->
                                            previewNames = names
                                            previewIndex = index
                                        },
                                        onOpenFile = { file -> openAttachmentFile(file) },
                                        onShare = { file -> shareFile(file) },
                                        onTrash = { file ->
                                            AttachmentStorage.trash(activity, file)
                                            attachmentFiles = AttachmentStorage.list(activity)
                                        },
                                        onDeleteForever = { file ->
                                            AttachmentStorage.delete(activity, file)
                                            attachmentFiles = AttachmentStorage.list(activity)
                                            trashFiles = AttachmentStorage.listTrash(activity)
                                        },
                                        onRestore = { file ->
                                            AttachmentStorage.restore(activity, file)
                                            trashFiles = AttachmentStorage.listTrash(activity)
                                            attachmentFiles = AttachmentStorage.list(activity)
                                        },
                                        onOpenTrash = { trashFiles = AttachmentStorage.listTrash(activity) },
                                        onCancelDownload = { url -> Downloads.cancel(url) },
                                        onRetryDownload = { task ->
                                            Downloads.retry(activity, task.url, task.name, task.expectedSize)
                                        },
                                    )
                                }
                            }

                            // 所有组织 / 我的组织 / 加入组织扫码 → 组织详情 → 活动详情，
                            // 严格按「谁压谁」排：组织列表留在最底下，返回时滚动位置和数据都还在

                            // 所有组织：官方 #/service/organize 的只读目录
                            if (showAllOrgs) {
                                SecondaryPage(onBack = { showAllOrgs = false }) {
                                    AllOrganizationsScreen(
                                        token = current.token,
                                        repository = organizationRepository,
                                        preloadEnabled = preferences.preloadEnabled,
                                        onOpenOrg = { id, name -> openOrg(id, name) },
                                        onBack = { showAllOrgs = false },
                                    )
                                }
                            }

                            // 我的组织：三个列表 × 三种审核状态
                            if (showOrganizations) {
                                SecondaryPage(onBack = { showOrganizations = false }) {
                                    OrganizationScreen(
                                        token = current.token,
                                        repository = organizationRepository,
                                        refreshKey = orgListTick,
                                        onOpenOrg = { id, name -> openOrg(id, name) },
                                        onJoinScan = { showJoinScan = true },
                                        onBack = { showOrganizations = false },
                                    )
                                }
                            }

                            // 加入组织：只认组织码，签到码在这个入口不生效。同样不套 SecondaryPage
                            if (showJoinScan) {
                                SignScanScreen(
                                    target = SignTarget(0L, "", 0),
                                    title = "加入组织",
                                    orgOnly = true,
                                    developerMode = BuildConfig.DEBUG && preferences.developerMode,
                                    submit = { SignOutcome(false, -1, "这个入口不签到") },
                                    onBack = { showJoinScan = false },
                                    onSigned = {},
                                    onOtherText = { text ->
                                        val orgId = ScanRouter.organizationId(text)
                                        if (orgId != null) {
                                            showJoinScan = false
                                            openOrg(orgId, "")
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                )
                            }

                            // 排行榜要压在「我的」之上，同时**在人物主页之下**（点人名进主页）
                            if (showRank) {
                                SecondaryPage(onBack = { showRank = false }) {
                                    RankScreen(
                                        token = current.token,
                                        repository = rankRepository,
                                        myStudentNo = current.profile.studentNo,
                                        preloadEnabled = preferences.preloadEnabled,
                                        initialScope = RankScope.of(rankLevel),
                                        onOpenPerson = { id -> userPageTarget = id },
                                        onBack = { showRank = false },
                                    )
                                }
                            }

                            // 组织详情：扫组织码进来的、从「我的组织」点进来的都在这里。
                            // 点主办活动时**不清 orgDetailTarget**，详情压在组织页上面，
                            // 返回时组织页原样还在，不会退回首页
                            val orgTarget = orgDetailTarget
                            if (orgTarget != null) {
                                SecondaryPage(onBack = { orgDetailTarget = null }) {
                                    OrgDetailScreen(
                                        token = current.token,
                                        repository = organizationRepository,
                                        organizationId = orgTarget.first,
                                        initialName = orgTarget.second,
                                        onOpenPerson = { id -> userPageTarget = id },
                                        onOpenActivity = { id -> openActivity(id) },
                                        // 申请交上去后让「我的组织」重新拉，才能看到新的待审核
                                        onApplied = { orgListTick++ },
                                        onBack = { orgDetailTarget = null },
                                    )
                                }
                            }

                            // 人物主页：官方 #/userShows/<id>，从排行榜、组织成员等处的头像点进来
                            val userTarget = userPageTarget
                            if (userTarget != null) {
                                SecondaryPage(onBack = { userPageTarget = null }) {
                                    UserProfileScreen(
                                        token = current.token,
                                        repository = userRepository,
                                        userId = userTarget,
                                        isSelf = userTarget == current.profile.userId,
                                        onOpenActivities = { id, name ->
                                            personActivitiesTarget = id to name
                                        },
                                        onBack = { userPageTarget = null },
                                    )
                                }
                            }

                            // 别人参加过的活动：压在人物主页之上，返回时回到人物主页
                            val personActs = personActivitiesTarget
                            if (personActs != null) {
                                SecondaryPage(onBack = { personActivitiesTarget = null }) {
                                    PersonActivitiesScreen(
                                        token = current.token,
                                        repository = userRepository,
                                        userId = personActs.first,
                                        personName = personActs.second,
                                        preloadEnabled = preferences.preloadEnabled,
                                        onOpenActivity = { id -> openActivity(id) },
                                        onBack = { personActivitiesTarget = null },
                                    )
                                }
                            }

                            // 详情压在附件/我的活动之上，从「我的活动」点进来才不会被遮住
                            val detailId = detailActivityId
                            if (detailId != null) {
                                SecondaryPage(onBack = { detailActivityId = null }) {
                                    ActivityDetailScreen(
                                        token = current.token,
                                        repository = activityRepository,
                                        activityId = detailId,
                                        onBack = { detailActivityId = null },
                                        onOpenWeb = { id ->
                                            // 只压一层网页：详情留在下面，返回时回到详情
                                            webUrl = "https://${AppConfig.ALLOWED_HOST}/#/ActivityShow/$id"
                                        },
                                        onOpenUrl = { url ->
                                            runCatching {
                                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            }
                                        },
                                        onOpenHomework = { id, viewing, name ->
                                            if (viewing) {
                                                // 查看作业用原生页：接口 `activity/query/work_3_0_1` 已实测
                                                homeworkActivityId = id
                                            } else {
                                                // 交作业也走原生页：附件先传官方 OSS，再调 submit
                                                homeworkTitle = name
                                                homeworkEditId = id
                                            }
                                        },
                                        onOpenAttachment = { attachment -> handleAttachmentTap(attachment) },
                                        onOpenSignScan = { target -> signScan = target },
                                        onEnroll = { id ->
                                            activityActions.enrollPerson(current.token, id)
                                        },
                                        onCancelEnroll = { id, reason ->
                                            activityActions.cancelEnroll(current.token, id, reason)
                                        },
                                        loadSignHistory = { id ->
                                            activityActions.signHistory(current.token, id, current.profile.userId)
                                        },
                                        refreshKey = detailRefreshTick,
                                    )
                                }
                            }

                            // 查看作业压在详情之上
                            val homeworkId = homeworkActivityId
                            if (homeworkId != null) {
                                SecondaryPage(onBack = { homeworkActivityId = null }) {
                                    HomeworkScreen(
                                        activityId = homeworkId,
                                        loader = { activityActions.work(current.token, homeworkId) },
                                        onBack = { homeworkActivityId = null },
                                        onEdit = { homeworkEditId = homeworkId },
                                        onAttachment = { attachment -> attachmentSheet = attachment },
                                    )
                                }
                            }

                            // 提交 / 修改作业：写操作，提交前有确认
                            val editId = homeworkEditId
                            if (editId != null) {
                                SecondaryPage(onBack = { homeworkEditId = null }) {
                                    HomeworkEditScreen(
                                        activityId = editId,
                                        activityName = homeworkTitle,
                                        loader = { activityActions.work(current.token, editId) },
                                        onSubmit = { text, files ->
                                            activityActions.submitWork(current.token, editId, text, files)
                                            Toast.makeText(activity, "作业已提交", Toast.LENGTH_SHORT).show()
                                        },
                                        onAttachment = { attachment -> attachmentSheet = attachment },
                                        onBack = { homeworkEditId = null },
                                        addedAttachments = homeworkAdded,
                                        uploading = homeworkUploading,
                                        onTakePhoto = {
                                            runCatching {
                                                val dir = java.io.File(activity.cacheDir, "camera")
                                                    .apply { mkdirs() }
                                                val file = java.io.File(
                                                    dir,
                                                    "photo-${System.currentTimeMillis()}.jpg",
                                                )
                                                pendingPhotoFile = file
                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                    activity,
                                                    "${activity.packageName}.files",
                                                    file,
                                                )
                                                cameraCapture.launch(uri)
                                            }.onFailure {
                                                Toast.makeText(activity, "打不开相机", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onPickFile = { photoPicker.launch("*/*") },
                                        onRemoveAdded = { attachment ->
                                            homeworkAdded.removeAll { it.url == attachment.url }
                                        },
                                    )
                                }
                            }

                            // 扫码签到压在详情之上：相机页必须独占，不能有下层内容透出来。
                            // 同样不要再套 SecondaryPage：SignScanScreen 自带，套两层会出假退出预览。
                            val scan = signScan
                            if (scan != null) {
                                SignScanScreen(
                                    target = scan,
                                    developerMode = BuildConfig.DEBUG && preferences.developerMode,
                                    submit = { ticket ->
                                        if (signScanSandbox) {
                                            sandboxSubmit(sandboxConfig, ticket)
                                        } else {
                                            activityActions.sign(current.token, ticket)
                                        }
                                    },
                                    resolveName = { id ->
                                        // 沙盒不联网：对不上号就显示「活动 #id」
                                        if (signScanSandbox) {
                                            null
                                        } else {
                                            runCatching {
                                                activityRepository.detail(current.token, id).name
                                            }.getOrNull()
                                        }
                                    },
                                    onBack = {
                                        signScan = null
                                        signScanSandbox = false
                                        presetSignText = null
                                    },
                                    onSigned = {
                                        AppLog.d("sign", "signed id=${scan.activityId} sandbox=$signScanSandbox")
                                        if (signScanSandbox) {
                                            // 沙盒里让详情立刻变成「已签到」，链路才算闭环
                                            sandboxConfig = sandboxConfig.copy(
                                                isSigner = 1,
                                                signInCount = sandboxConfig.signInCount + 1,
                                            )
                                            sandboxDetail = sandboxConfig.toDetail()
                                        } else {
                                            detailRefreshTick++
                                        }
                                    },
                                    initialQrText = presetSignText,
                                    sandbox = signScanSandbox,
                                )
                            }

                            // 附件操作面板：下载 / 预览 / 其他应用打开 / 分享 / 删除
                            attachmentSheet?.let { attachment ->
                                val local = AttachmentStorage.find(activity, attachment.name)
                                val isImage = AttachmentStorage.mimeOf(attachment.name).startsWith("image/")
                                AttachmentActionsSheet(
                                    attachment = attachment,
                                    downloaded = local != null,
                                    isImage = isImage,
                                    onDownload = {
                                        downloadAttachment(attachment)
                                        attachmentSheet = null
                                    },
                                    onDownloadAndPreview = {
                                        pendingPreviewName = attachment.name
                                        downloadAttachment(attachment)
                                        attachmentSheet = null
                                    },
                                    onPreview = {
                                        // 从详情/作业进来只预览这一张，不再拿全局已下载图片凑成 1/5
                                        attachmentFiles = AttachmentStorage.list(activity)
                                        previewNames = listOf(attachment.name)
                                        previewIndex = 0
                                        attachmentSheet = null
                                    },
                                    onOpenWithOther = {
                                        local?.let { openAttachmentFile(it) }
                                        attachmentSheet = null
                                    },
                                    onShare = {
                                        local?.let { shareFile(it) }
                                        attachmentSheet = null
                                    },
                                    onDelete = {
                                        local?.let { file ->
                                            AttachmentStorage.delete(activity, file)
                                            attachmentFiles = AttachmentStorage.list(activity)
                                        }
                                        attachmentSheet = null
                                    },
                                    onDismiss = { attachmentSheet = null },
                                )
                            }

                            if (previewNames.isNotEmpty()) {
                                SecondaryPage(onBack = { previewNames = emptyList() }) {
                                    ImagePreviewScreen(
                                        names = previewNames,
                                        startIndex = previewIndex,
                                        resolve = { name -> attachmentFiles.firstOrNull { it.name == name } },
                                        onBack = { previewNames = emptyList() },
                                        onShare = { file -> shareFile(file) },
                                    )
                                }
                            }

                            // 详细资料：只读展示，后续阶段接上改密码 / 换手机号 / 改资料
                            if (showPersonalInfo) {
                                SecondaryPage(onBack = { showPersonalInfo = false }) {
                                    PersonalInfoScreen(
                                        token = current.token,
                                        repository = profileRepository,
                                        onOpenPassword = { showChangePassword = true },
                                        onOpenMobile = { mobile ->
                                            pendingMobile = mobile
                                            showChangeMobile = true
                                        },
                                        onOpenTags = { ids ->
                                            pendingTagIds = ids
                                            showTags = true
                                        },
                                        refreshKey = personalInfoTick,
                                        onBack = { showPersonalInfo = false },
                                    )
                                }
                            }

                            // 标签：字典多选，保存后回到资料页并刷新
                            if (showTags) {
                                SecondaryPage(onBack = { showTags = false }) {
                                    TagsScreen(
                                        token = current.token,
                                        repository = profileRepository,
                                        initialIds = pendingTagIds,
                                        onBack = { showTags = false },
                                        onSaved = {
                                            showTags = false
                                            personalInfoTick++
                                        },
                                    )
                                }
                            }

                            // 更换手机号：验证码发到旧号，成功后回到资料页并刷新
                            if (showChangeMobile) {
                                SecondaryPage(onBack = { showChangeMobile = false }) {
                                    ChangeMobileScreen(
                                        token = current.token,
                                        repository = profileRepository,
                                        currentMobile = pendingMobile,
                                        onBack = { showChangeMobile = false },
                                        onUpdated = {
                                            showChangeMobile = false
                                            personalInfoTick++
                                            reloadProfile()
                                            Toast.makeText(activity, "手机号已更换", Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                }
                            }

                            // 修改密码：成功后官方会让当前凭证失效，这里同步清会话回登录页
                            if (showChangePassword) {
                                SecondaryPage(onBack = { showChangePassword = false }) {
                                    ChangePasswordScreen(
                                        token = current.token,
                                        repository = profileRepository,
                                        onBack = { showChangePassword = false },
                                        onPasswordChanged = {
                                            showChangePassword = false
                                            showPersonalInfo = false
                                            webUrl = null
                                            sessionStore.clear()
                                            session = null
                                            tab = MainTab.DISCOVER
                                            Toast.makeText(
                                                activity,
                                                "密码已修改，请用新密码登录",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        },
                                    )
                                }
                            }

                            // 诚信值：对应官方 #/honestValue，只读
                            if (showCredit) {
                                SecondaryPage(onBack = { showCredit = false }) {
                                    CreditScreen(
                                        token = current.token,
                                        repository = creditRepository,
                                        onOpenActivity = { id ->
                                            showCredit = false
                                            openActivity(id)
                                        },
                                        onBack = { showCredit = false },
                                    )
                                }
                            }

                            // 数据管理：扫描缓存 / 日志 / 回收站占用，逐项清理
                            if (showStorage) {
                                SecondaryPage(onBack = { showStorage = false }) {
                                    StorageScreen(
                                        repository = storageRepository,
                                        preferences = preferences,
                                        onBack = { showStorage = false },
                                    )
                                }
                            }

                            // 官方网页压在整摞页面最上面：从详情进网页，返回就回详情，
                            // 而不是把详情一起关掉（以前 back 会一路退回列表甚至退出应用）
                            if (webUrl != null) {
                                SecondaryPage(onBack = { webUrl = null }) {
                                    WebShell(
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
                                }
                            }

                            pendingDownload?.let { attachment ->
                                AlertDialog(
                                    onDismissRequest = { pendingDownload = null },
                                    title = { Text("这个文件有点大") },
                                    text = {
                                        Text(
                                            "${attachment.name}\n${Downloads.formatSize(attachment.size)}，建议在 Wi-Fi 下下载。",
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                downloadAttachment(attachment)
                                                pendingDownload = null
                                            },
                                        ) {
                                            Text("继续下载")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { pendingDownload = null }) { Text("取消") }
                                    },
                                )
                            }
                        }
                    }
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
