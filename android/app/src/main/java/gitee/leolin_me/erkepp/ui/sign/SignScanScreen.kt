package gitee.leolin_me.erkepp.ui.sign

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import gitee.leolin_me.erkepp.ui.InlineLoading
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import gitee.leolin_me.erkepp.AppLog
import gitee.leolin_me.erkepp.BuildConfig
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.data.SignAction
import gitee.leolin_me.erkepp.data.SignOutcome
import gitee.leolin_me.erkepp.data.SignTicket
import gitee.leolin_me.erkepp.data.parseSignCode
import gitee.leolin_me.erkepp.ui.SecondaryPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 从哪个活动点进来的（决定标题与“扫到的码是不是这个活动”）。 */
data class SignTarget(
    val activityId: Long,
    val activityName: String,
    val signSwitch: Int,
)

/**
 * 扫码签到 / 签退。
 *
 * 官方把这个动作交给宿主 App 的扫码 bridge，自己只处理「扫到的文本」。这里补齐前半段：
 * 自己开相机解出二维码文本，后半段完全照官方实现（[parseSignCode] + `activity/sign/in-out`）。
 *
 * 提交前一定弹确认：签到和签退都不可撤销，扫错一个码就把动作做反了。
 */
@Composable
fun SignScanScreen(
    target: SignTarget,
    developerMode: Boolean,
    submit: suspend (SignTicket) -> SignOutcome,
    onBack: () -> Unit,
    onSigned: () -> Unit,
    /** 调试用：直接把一段扫码文本当成本次扫到的内容（`--es qr <文本>`）。 */
    initialQrText: String? = null,
    /** 扫到别的活动的码时，用它把活动名查回来；沙盒不提供。 */
    resolveName: suspend (Long) -> String? = { null },
    /** 离线沙盒：不会去查活动名，也不联网。 */
    sandbox: Boolean = false,
    /** 顶部标题；空串时按 sandbox 自动取。 */
    title: String = "",
    /** 不是签到码的文本交给它处理；返回 true 表示已处理（本页会被关掉）。 */
    onOtherText: ((String) -> Boolean)? = null,
    /**
     * 只认组织码（「加入组织」用）。
     *
     * 同一个扫码页要能扫组织码，但**不能**把签到码签了：两个入口长得一样，
     * 站错地方签错到，比多扫一次麻烦得多。所以这个模式下签到解析整段跳过。
     */
    orgOnly: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var permission by remember {
        mutableStateOf(
            if (hasCameraPermission(context)) PermissionState.GRANTED else PermissionState.ASKING,
        )
    }
    var torch by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var ticket by remember { mutableStateOf<SignTicket?>(null) }
    var otherName by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<SignOutcome?>(null) }
    var submittedAction by remember { mutableStateOf<SignAction?>(null) }
    var showPaste by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var captureTick by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permission = if (granted) PermissionState.GRANTED else PermissionState.DENIED
    }

    LaunchedEffect(Unit) {
        if (permission == PermissionState.ASKING) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun accept(text: String, source: String) {
        if (orgOnly) {
            if (onOtherText?.invoke(text) == true) return
            hint = "这不是组织码"
            AppLog.d("sign", "reject org-only source=$source text=${text.take(80)}")
            return
        }
        val parsed = parseSignCode(text)
        if (parsed == null) {
            // 首页扫一扫还要兼管活动分享码、组织码；交给外面分流
            if (onOtherText?.invoke(text) == true) return
            hint = "这不是活动签到码"
            AppLog.d("sign", "reject source=$source text=${text.take(80)}")
            return
        }
        hint = null
        ticket = parsed
        AppLog.d(
            "sign",
            "scan source=$source id=${parsed.activityId} type=${parsed.type} " +
                "signSwitch=${parsed.signSwitch} sp=${parsed.sp}",
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()?.let { decodeQrFromBitmap(it) }
            }
            if (text == null) hint = "这张图里没解出二维码" else accept(text, "image")
        }
    }

    LaunchedEffect(Unit) {
        AppLog.d(
            "sign",
            "scan page open id=${target.activityId} signSwitch=${target.signSwitch} " +
                "sandbox=$sandbox build=${BuildConfig.VERSION_NAME}/${BuildConfig.VERSION_CODE}",
        )
        if (!initialQrText.isNullOrBlank()) accept(initialQrText, "probe")
    }

    // 扫到的码属于另一个活动时，去把它的名字取回来，确认框里更好认（沙盒不联网）
    LaunchedEffect(ticket) {
        val current = ticket ?: return@LaunchedEffect
        if (sandbox) return@LaunchedEffect
        if (current.activityId == target.activityId) return@LaunchedEffect
        otherName = runCatching { resolveName(current.activityId) }.getOrNull()
    }

    SecondaryPage(onBack = onBack) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            when (permission) {
                PermissionState.GRANTED -> QrPreview(
                    active = ticket == null && outcome == null,
                    torch = torch,
                    captureTick = captureTick,
                    onCaptureResult = { text ->
                        capturing = false
                        if (text == null) {
                            hint = "这张没解出来，靠近一点或点一下对焦再试"
                        } else {
                            accept(text, "still")
                        }
                    },
                    onDecoded = { text -> accept(text, "camera") },
                    onError = { message -> cameraError = message },
                    modifier = Modifier.fillMaxSize(),
                )

                PermissionState.DENIED -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "需要相机权限才能扫码",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "扫码签到要读现场二维码，只用这一次，不会保存拍到的画面。",
                        color = Color(0xB3FFFFFF),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("再次申请", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    ),
                                )
                            }
                        },
                    ) {
                        Text("去系统设置打开", color = MaterialTheme.colorScheme.primary)
                    }
                }

                PermissionState.ASKING -> Unit
            }

            // 取景框与提示
            if (permission == PermissionState.GRANTED && cameraError == null) {
                // 取景框按可用空间算，横屏/矮屏也不会顶出画面
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val side = minOf(maxWidth * 0.72f, maxHeight * 0.5f)
                    ScanFrame(modifier = Modifier.size(side).padding(bottom = 40.dp))
                }
                Text(
                    text = hint ?: if (orgOnly) "把组织码放进框内" else "把活动现场的二维码放进框内",
                    color = if (hint == null) Color(0xE6FFFFFF) else Color(0xFFFFC46B),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 128.dp, start = 32.dp, end = 32.dp),
                )
                // 离得远、屏幕太小时预览分辨率不够，用全尺寸静帧兜底
                TextButton(
                    onClick = {
                        if (!capturing) {
                            capturing = true
                            captureTick++
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 84.dp),
                ) {
                    Text(
                        text = if (capturing) "正在拍…" else "扫不到？拍一张",
                        color = Color(0xCCFFFFFF),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            cameraError?.let { message ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(message, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "可以在系统里授权相机后重进这一页。",
                        color = Color(0xB3FFFFFF),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // 顶部：关闭 + 标题 + 手电筒
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(
                    iconRes = R.drawable.ic_arrow_back,
                    description = "返回",
                    onClick = onBack,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { if (sandbox) "扫码签到（沙盒）" else "扫码签到" },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (target.activityName.isNotBlank()) {
                        Text(
                            text = target.activityName,
                            color = Color(0xB3FFFFFF),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (permission == PermissionState.GRANTED) {
                    CircleIconButton(
                        iconRes = R.drawable.ic_flash,
                        description = if (torch) "关闭手电筒" else "打开手电筒",
                        selected = torch,
                        onClick = { torch = !torch },
                    )
                }
            }

            // 从相册识别是正常需求：别人发来的活动分享码、签到码截图都要能读。
            // 手工粘贴只给开发者模式，普通人用不上。
            if (ticket == null && outcome == null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Text(
                            text = "从图片识别",
                            color = Color(0xCCFFFFFF),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (BuildConfig.DEBUG && developerMode) {
                        TextButton(onClick = { showPaste = true }) {
                            Text("粘贴二维码文本", color = Color(0x99FFFFFF), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    val current = ticket
    if (current != null && outcome == null) {
        val name = when {
            current.activityId == target.activityId -> target.activityName
            !otherName.isNullOrBlank() -> otherName!!
            else -> "活动 #${current.activityId}"
        }
        val warn = signSwitchWarning(current, target.signSwitch)
        AlertDialog(
            onDismissRequest = { if (!submitting) ticket = null },
            title = { Text("确认${current.action.label}") },
            text = {
                Column {
                    Text(text = name, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "将提交",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = current.action.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (warn != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = warn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (BuildConfig.DEBUG && developerMode) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "type=${current.type} signSwitch=${current.signSwitch} " +
                                "sp=${current.sp.ifEmpty { "空（静态码）" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        submitting = true
                        submittedAction = current.action
                        val started = System.currentTimeMillis()
                        AppLog.d(
                            "sign",
                            "submit start id=${current.activityId} type=${current.type} " +
                                "signSwitch=${current.signSwitch} sp=${current.sp} " +
                                "userId=${current.userId} sandbox=$sandbox",
                        )
                        scope.launch {
                            val response = runCatching { submit(current) }.getOrElse { error ->
                                SignOutcome(false, -1, error.javaClass.simpleName + ": " + error.message)
                            }
                            submitting = false
                            outcome = response
                            AppLog.d(
                                "sign",
                                "submit done code=${response.code} msg=${response.message} " +
                                    "elapsed=${System.currentTimeMillis() - started}ms",
                            )
                            if (response.success) onSigned()
                        }
                    },
                ) {
                    if (submitting) {
                        InlineLoading(size = 16.dp)
                    } else {
                        Text("提交")
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !submitting, onClick = { ticket = null }) { Text("取消") }
            },
        )
    }

    val result = outcome
    if (result != null) {
        // 成功时服务端只会回 "ok"，界面用自己的话说清楚做了什么
        val head = if (result.success) "${submittedAction?.label ?: "签到"}成功" else result.message
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (result.success) "已提交" else "提交失败") },
            text = {
                Column {
                    Text(
                        text = head,
                        color = if (result.success) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    if (BuildConfig.DEBUG && developerMode) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "code=${result.code} msg=${result.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (result.success) {
                            onBack()
                        } else {
                            outcome = null
                            ticket = null
                        }
                    },
                ) {
                    Text(if (result.success) "完成" else "重新扫码")
                }
            },
            dismissButton = if (result.success) {
                null
            } else {
                { TextButton(onClick = onBack) { Text("关闭") } }
            },
        )
    }

    if (BuildConfig.DEBUG && showPaste) {
        AlertDialog(
            onDismissRequest = { showPaste = false },
            title = { Text("粘贴二维码文本") },
            text = {
                Column {
                    Text(
                        text = "以 qutuo:// 开头的那串。现场码带时效标记，过期的文本签不上。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        singleLine = false,
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPaste = false
                        accept(pasteText, "paste")
                    },
                ) { Text("解析") }
            },
            dismissButton = { TextButton(onClick = { showPaste = false }) { Text("取消") } },
        )
    }
}

private enum class PermissionState { ASKING, GRANTED, DENIED }

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * 扫到的是签退码、但老师现在开着签到（或反过来）时给一句提醒。
 * 活动详情可能已经过时，所以只提醒不拦截。
 */
private fun signSwitchWarning(ticket: SignTicket, signSwitch: Int): String? = when {
    ticket.signSwitch == 2 && signSwitch == 1 -> "老师现在开的是签到，这串码是签退码，可能是上一轮留下的。"
    ticket.signSwitch == 1 && signSwitch == 2 -> "老师现在开的是签退，这串码是签到码，可能是上一轮留下的。"
    else -> null
}

@Composable
private fun CircleIconButton(
    iconRes: Int,
    description: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Color(0x66FFFFFF) else Color(0x33000000)),
    ) {
        androidx.compose.material3.Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 四个角括号，不做整块遮罩：遮住预览的取景框在暗处看不清码。 */
@Composable
private fun ScanFrame(modifier: Modifier = Modifier) {
    val color = Color.White
    Canvas(modifier = modifier) {
        val arm = size.minDimension * 0.12f
        val width = 3.dp.toPx()
        val w = size.width
        val h = size.height

        // 左上
        drawLine(color, Offset(0f, 0f), Offset(arm, 0f), width, StrokeCap.Round)
        drawLine(color, Offset(0f, 0f), Offset(0f, arm), width, StrokeCap.Round)
        // 右上
        drawLine(color, Offset(w - arm, 0f), Offset(w, 0f), width, StrokeCap.Round)
        drawLine(color, Offset(w, 0f), Offset(w, arm), width, StrokeCap.Round)
        // 左下
        drawLine(color, Offset(0f, h - arm), Offset(0f, h), width, StrokeCap.Round)
        drawLine(color, Offset(0f, h), Offset(arm, h), width, StrokeCap.Round)
        // 右下
        drawLine(color, Offset(w - arm, h), Offset(w, h), width, StrokeCap.Round)
        drawLine(color, Offset(w, h - arm), Offset(w, h), width, StrokeCap.Round)
        // 中间一条细扫描线，纯装饰
        drawRect(
            color = Color(0x33FFFFFF),
            topLeft = Offset(w * 0.08f, h / 2),
            size = Size(w * 0.84f, 1.dp.toPx()),
        )
    }
}

/** 相机预览：TextureView + [QrCamera]。 */
@Composable
private fun QrPreview(
    active: Boolean,
    torch: Boolean,
    captureTick: Int,
    onCaptureResult: (String?) -> Unit,
    onDecoded: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val decodedRef = remember { mutableStateOf<(String) -> Unit>({}) }
    val errorRef = remember { mutableStateOf<(String) -> Unit>({}) }
    val engine = remember { QrCamera(context) { text -> decodedRef.value(text) } }
    SideEffect {
        decodedRef.value = onDecoded
        errorRef.value = onError
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> engine.pause()
                Lifecycle.Event.ON_START -> engine.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            engine.shutdown()
        }
    }

    LaunchedEffect(active) { engine.setDecoding(active) }
    LaunchedEffect(torch) { engine.setTorch(torch) }

    // 双指缩放：远景扫码时靠它把码「拉近」，比单纯换个更大的预览还管用
    var zoom by remember { mutableFloatStateOf(1f) }
    var focusPoint by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(600)
            focusPoint = null
        }
    }

    // 屏幕方向变了就重开相机：显示方向和预览变换都得重算（activity 声明了 configChanges，
    // 不重开的话画面会一直歪着）
    val orientation = LocalConfiguration.current.orientation
    var lastOrientation by remember { mutableIntStateOf(orientation) }
    LaunchedEffect(orientation) {
        if (orientation != lastOrientation) {
            lastOrientation = orientation
            engine.restart()
        }
    }
    // 「拍一张」：外部按一次 +1，这里拍一张全尺寸静帧再解
    val captureRef = remember { mutableStateOf<(String?) -> Unit>({}) }
    SideEffect { captureRef.value = onCaptureResult }
    LaunchedEffect(captureTick) {
        if (captureTick > 0) engine.captureStill { text -> captureRef.value(text) }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        focusPoint = offset
                        engine.focusAt(offset.x, offset.y, size.width, size.height)
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, gestureZoom, _ ->
                        if (gestureZoom != 1f) {
                            val next = (zoom * gestureZoom).coerceIn(1f, 8f)
                            // 有些不支持变焦的机型会返回 false，这时不显示倍率
                            if (engine.setZoom(next)) zoom = next
                        }
                    }
                },
            factory = { ctx ->
                val textureView = TextureView(ctx)
                textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        val error = engine.attach(textureView)
                        if (error != null) errorRef.value(error)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(
                        surface: android.graphics.SurfaceTexture,
                    ): Boolean {
                        engine.pause()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) = Unit
                }
                textureView
            },
        )
        focusPoint?.let { point ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0xCCFFFFFF),
                    radius = 34.dp.toPx(),
                    center = point,
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawCircle(color = Color(0xCCFFFFFF), radius = 3.dp.toPx(), center = point)
            }
        }
        if (zoom > 1.05f) {
            Text(
                text = "×%.1f".format(zoom),
                color = Color(0xCCFFFFFF),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 104.dp),
            )
        }
    }
}
