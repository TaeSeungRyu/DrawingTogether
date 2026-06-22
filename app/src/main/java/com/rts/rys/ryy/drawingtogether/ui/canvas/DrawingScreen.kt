package com.rts.rys.ryy.drawingtogether.ui.canvas

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rts.rys.ryy.drawingtogether.drawing.model.BackgroundImage
import com.rts.rys.ryy.drawingtogether.photo.CameraCaptureFile
import com.rts.rys.ryy.drawingtogether.photo.PhotoLoader
import com.rts.rys.ryy.drawingtogether.session.SessionManager
import com.rts.rys.ryy.drawingtogether.session.SessionState
import com.rts.rys.ryy.drawingtogether.works.PngComposer
import com.rts.rys.ryy.drawingtogether.works.WorkStore
import kotlinx.coroutines.launch

private const val ASPECT_TOAST_TEXT = "사진 비율로 화면을 맞췄어요"
private const val SAVED_TOAST_TEXT = "작품을 저장했어요"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DrawingViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    var showSaveDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }

    // 멀티모드 — Connected이면 우측에 peer indicator. 화면을 떠날 때 disconnect.
    val session = remember { SessionManager.get(context) }
    val sessionState by session.state.collectAsState()
    DisposableEffect(Unit) {
        onDispose {
            if (session.state.value is SessionState.Connected ||
                session.state.value is SessionState.Handshaking) {
                session.disconnect()
            }
        }
    }

    // 예상치 못한 끊김 알림. 사용자의 명시적 disconnect 는 SessionManager 에서 Failed 를 우회하므로
    // 여기 토스트는 진짜 끊김에서만 발화. 캔버스 데이터는 그대로 남아있으니 저장 안내까지 같이.
    LaunchedEffect(sessionState) {
        if (sessionState is SessionState.Failed) {
            Toast.makeText(
                context,
                "상대와의 연결이 끊겼어요. 그림은 그대로 두면 저장할 수 있어요.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // Phase 3-A 양방향 stroke 동기화.
    // outbound: VM 의 로컬 DrawingEvent → 25ms 코얼레서 → Connected 일 때만 Frame.Event 로 송신.
    // inbound:  SessionManager 가 받은 원격 이벤트를 VM.applyRemoteEvent 로 흘려보냄.
    LaunchedEffect(vm, session) {
        com.rts.rys.ryy.drawingtogether.transport.runOutboundCoalescer(
            input = vm.outboundEvents,
            intervalMs = 25L,
            send = { event ->
                if (session.state.value is SessionState.Connected) {
                    runCatching {
                        session.transport.send(
                            com.rts.rys.ryy.drawingtogether.transport.Frame.Event(event)
                        )
                    }
                }
            },
        )
    }
    LaunchedEffect(vm, session) {
        session.incomingDrawing.collect { event ->
            vm.applyRemoteEvent(event)
        }
    }

    // 갤러리 선택 — 권한 불필요 (Android PhotoPicker)
    val pickPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    PhotoLoader.load(context, uri, BackgroundImage.Source.Gallery)
                }.onSuccess {
                    vm.setBackground(it)
                    Toast.makeText(context, ASPECT_TOAST_TEXT, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 카메라 촬영 — 우리 매니페스트엔 CAMERA 권한 선언 안 함 (시스템 카메라 앱이 처리)
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val capturePhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            scope.launch {
                runCatching {
                    PhotoLoader.load(context, uri, BackgroundImage.Source.Camera)
                }.onSuccess {
                    vm.setBackground(it)
                    Toast.makeText(context, ASPECT_TOAST_TEXT, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로",
                    )
                }
            },
            actions = {
                // 좁은 폭(예: 갤럭시 S21) 에서 액션이 겹치면 가로 스크롤로 풀어준다.
                // weight(1f) — Phase 2에서 이 Row 뒤에 peer indicator를 두면 indicator는
                // 우측에 고정되고 액션 행만 좌측에서 스크롤된다. (doc/ui-layout.md §4)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MergeBackgroundToggle(
                        checked = vm.canvas.mergeBackgroundOnSave,
                        onCheckedChange = vm::setMergeBackgroundOnSave,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    CuteToolButton(
                        text = "사진",
                        onClick = {
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    CuteToolButton(
                        text = "촬영",
                        onClick = {
                            val uri = CameraCaptureFile.create(context)
                            pendingCameraUri = uri
                            capturePhoto.launch(uri)
                        },
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    if (vm.canvas.background != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        CuteToolButton(
                            text = "제거",
                            onClick = { vm.setBackground(null) },
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    CuteToolButton(
                        text = "저장",
                        onClick = {
                            nameInput = ""
                            showSaveDialog = true
                        },
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                // Phase 2 — 멀티모드 연결 상태 표시. weight(1f) Row 다음이라 우측에 고정.
                val s = sessionState
                if (s is SessionState.Connected) {
                    PeerIndicator(nick = s.remoteNick)
                }
            },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val bg = vm.canvas.background
            val canvasModifier = if (bg != null) {
                Modifier
                    .aspectRatio(bg.aspectRatio)
                    .background(Color.White)
            } else {
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
            }
            DrawingCanvas(
                state = vm.canvas,
                tool = vm.tool,
                onStrokeStart = vm::strokeStart,
                onStrokeAppend = vm::strokeAppend,
                onStrokeEnd = vm::strokeEnd,
                modifier = canvasModifier,
            )
        }

        Toolbar(
            tool = vm.tool,
            canUndo = vm.canvas.canUndo,
            onColor = vm::selectColor,
            onEraser = vm::toggleEraser,
            onBrush = vm::setBrush,
            onShape = vm::setShape,
            onStrokeWidth = vm::setStrokeWidth,
            onUndo = vm::undoLastLocal,
            onClear = vm::clearAll,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showSaveDialog) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        val submit = submit@{
            val raw = nameInput.trim()
            if (raw.isEmpty()) return@submit
            showSaveDialog = false
            scope.launch {
                val includeBg = vm.canvas.mergeBackgroundOnSave
                val mergedBg = vm.canvas.background != null && includeBg
                val bitmap = PngComposer.compose(vm.canvas, density, includeBg)
                WorkStore.get(context).save(bitmap, mergedBg, raw)
                Toast.makeText(context, SAVED_TOAST_TEXT, Toast.LENGTH_SHORT).show()
            }
        }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("작품 저장") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(40) },
                    singleLine = true,
                    label = { Text("이름") },
                    placeholder = { Text("예) 곰돌이") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.focusRequester(focus),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { submit() },
                    enabled = nameInput.trim().isNotEmpty(),
                ) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("취소") }
            },
        )
    }
}
