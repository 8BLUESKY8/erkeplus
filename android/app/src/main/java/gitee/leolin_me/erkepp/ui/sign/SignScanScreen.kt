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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.data.ActivityActions
import gitee.leolin_me.erkepp.data.ActivityRepository
import gitee.leolin_me.erkepp.data.SignAction
import gitee.leolin_me.erkepp.data.SignOutcome
import gitee.leolin_me.erkepp.data.SignTicket
import gitee.leolin_me.erkepp.data.parseSignCode
import gitee.leolin_me.erkepp.ui.SecondaryPage
import kotlinx.coroutines.Dispatchers
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
    token: String,
    target: SignTarget,
    developerMode: Boolean,
    repository: ActivityRepository,
    actions: ActivityActions,
    onBack: () -> Unit,
    onSigned: () -> Unit,
    /** 调试用：直接把一段扫码文本当成本次扫到的内容（`--es qr <文本>`）。 */
    initialQrText: String? = null,
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permission = if (granted) PermissionState.GRANTED else PermissionState.DENIED
    }

    LaunchedEffect(Unit) {
        if (permission == PermissionState.ASKING) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun accept(text: String, source: String) {
        val parsed = parseSignCode(text)
        if (parsed == null) {
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

    // 调试入口：把外部给的文本当作扫到的内容
    LaunchedEffect(Unit) {
        if (!initialQrText.isNullOrBlank()) accept(initialQrText, "probe")
    }

    // 扫到的码属于另一个活动时，去把它的名字取回来，确认框里更好认
    LaunchedEffect(ticket) {
        val current = ticket ?: return@LaunchedEffect
        if (current.activityId == target.activityId) return@LaunchedEffect
        otherName = runCatching { repository.detail(token, current.activityId).name }.getOrNull()
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
                ScanFrame(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 60.dp)
                        .fillMaxWidth(0.72f)
                        .aspectRatio(1f),
                )
                Text(
                    text = hint ?: "把活动现场的二维码放进框内",
                    color = if (hint == null) Color(0xE6FFFFFF) else Color(0xFFFFC46B),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp, start = 32.dp, end = 32.dp),
                )
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
                        text = "扫码签到",
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

            // 开发者模式才给的兜底入口：手工粘贴 / 从相册识别
            if (developerMode && ticket == null && outcome == null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = { showPaste = true }) {
                        Text("粘贴二维码文本", color = Color(0x99FFFFFF), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Text("从图片识别", color = Color(0x99FFFFFF), style = MaterialTheme.typography.bodySmall)
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
                    if (developerMode) {
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
                        scope.launch {
                            val response = actions.sign(token, current)
                            submitting = false
                            outcome = response
                            if (response.success) onSigned()
                        }
                    },
                ) {
                    if (submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
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
                    if (developerMode) {
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

    if (showPaste) {
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

    AndroidView(
        modifier = modifier,
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
}
