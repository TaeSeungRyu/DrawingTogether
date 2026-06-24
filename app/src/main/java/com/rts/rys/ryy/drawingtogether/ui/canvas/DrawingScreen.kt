package com.rts.rys.ryy.drawingtogether.ui.canvas

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rts.rys.ryy.drawingtogether.drawing.model.BackgroundImage
import com.rts.rys.ryy.drawingtogether.drawing.model.PeerId
import com.rts.rys.ryy.drawingtogether.photo.CameraCaptureFile
import com.rts.rys.ryy.drawingtogether.photo.PhotoLoader
import androidx.compose.ui.graphics.asAndroidBitmap
import com.rts.rys.ryy.drawingtogether.session.BackgroundChange
import com.rts.rys.ryy.drawingtogether.session.SessionManager
import com.rts.rys.ryy.drawingtogether.session.SessionState
import com.rts.rys.ryy.drawingtogether.transport.FileTransferDirection
import com.rts.rys.ryy.drawingtogether.transport.FileTransferStatus
import com.rts.rys.ryy.drawingtogether.transport.Frame
import com.rts.rys.ryy.drawingtogether.transport.nearby.TransportMode
import com.rts.rys.ryy.drawingtogether.ui.DrawMode
import kotlinx.coroutines.delay
import com.rts.rys.ryy.drawingtogether.works.PngComposer
import com.rts.rys.ryy.drawingtogether.works.WorkStore
import kotlinx.coroutines.launch

private const val ASPECT_TOAST_TEXT = "사진 비율로 화면을 맞췄어요"
private const val SAVED_TOAST_TEXT = "저장됐어요. 갤러리로 내보내려면 \"최근 작업\" 에서 열어주세요."

// Phase 4-G: 동기화 다이얼로그 단계.
private sealed class SyncStep {
    data object None : SyncStep()
    data object DuoConfirm : SyncStep()
    data object PartyPicker : SyncStep()
    data class PartyConfirm(
        val target: com.rts.rys.ryy.drawingtogether.session.RemotePeerInfo,
    ) : SyncStep()
}

// 사진 broadcast. FILE 페이로드 + PhotoMeta(BYTES).
// - 함께 모드(Duo): 양쪽 메인 캔버스에 같은 사진 (1:1 공유 캔버스).
// - 모임 모드(Party): 자기 메인 + 다른 사람들이 보는 자기 미니뷰. SessionManager 가 sender 박힌
//   BackgroundChange.Photo 를 emit → DrawingScreen 이 발신자의 peerCanvases.background 에 적용.
//
// 큰 사진 안정성: 원본 uri 대신 PhotoLoader 가 다운샘플한 image.bitmap 을 JPEG cache 로
// 변환해 송신 — Nearby FILE 가 원본 (수십 MB) 보낼 때 중간에 끊기는 현상 회피.
private suspend fun shareBackgroundToPeer(
    context: android.content.Context,
    session: SessionManager,
    image: BackgroundImage,
) {
    if (session.state.value !is SessionState.Connected) return
    runCatching {
        val cacheUri = bitmapToCacheUri(context, image.bitmap)
        val payloadId = session.transport.sendFile(cacheUri)
        val byteSize = context.contentResolver
            .openAssetFileDescriptor(cacheUri, "r")
            ?.use { it.length }
            ?: 0L
        session.transport.send(
            Frame.PhotoMeta(
                payloadId = payloadId,
                byteSize = byteSize,
                widthPx = image.widthPx,
                heightPx = image.heightPx,
                mime = "image/jpeg",
            )
        )
    }
}

// "동기화" 응답용 — 현재 캔버스의 ImageBitmap 을 cache/snapshots/ 에 JPEG 로 저장하고
// FileProvider URI 반환. URI 는 곧 Nearby FILE 페이로드로 송신.
private suspend fun bitmapToCacheUri(
    context: android.content.Context,
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
): android.net.Uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val dir = java.io.File(context.cacheDir, "snapshots").apply { mkdirs() }
    val file = java.io.File(dir, "snap_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { out ->
        bitmap.asAndroidBitmap()
            .compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
    }
    androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}

// "동기화" 응답용 — strokes 를 CBOR 로 직렬화해 cache/snapshots/ 에 저장하고 URI 반환.
// BYTES 페이로드 32KB 한도를 회피하기 위해 FILE 페이로드로 송신 (Phase 3.5-A).
private suspend fun strokesToCacheUri(
    context: android.content.Context,
    strokes: List<com.rts.rys.ryy.drawingtogether.drawing.model.Stroke>,
): android.net.Uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val dir = java.io.File(context.cacheDir, "snapshots").apply { mkdirs() }
    val file = java.io.File(dir, "strokes_${System.currentTimeMillis()}.cbor")
    file.outputStream().use { out ->
        out.write(
            com.rts.rys.ryy.drawingtogether.transport.codec.FrameCodec.encodeStrokes(strokes)
        )
    }
    androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}

// 모임 모드에서 내가 동기화로 자기 메인 캔버스를 갱신한 직후, 그 결과를 다른 참가자들에게도
// broadcast 해 "다른 화면의 내 미니뷰" 까지 동기화. respondToSnapshotRequest 와 비슷하지만:
//  - targetPeerId 빈 문자열 (broadcast)
//  - 받는 측의 SessionManager 분기: sender == 내 peerId 매칭 후 peerCanvases[내] 갱신
private suspend fun broadcastMyCanvasAsPeer(
    context: android.content.Context,
    session: SessionManager,
    strokes: List<com.rts.rys.ryy.drawingtogether.drawing.model.Stroke>,
    background: BackgroundImage?,
) {
    if (session.state.value !is SessionState.Connected) return
    runCatching {
        val strokesUri = strokesToCacheUri(context, strokes)
        val strokesPayloadId = session.transport.sendFile(strokesUri)
        session.transport.send(
            Frame.Snapshot(
                strokesPayloadId = strokesPayloadId,
                hasPhoto = background != null,
                targetPeerId = "",
            )
        )
        if (background != null) {
            val uri = bitmapToCacheUri(context, background.bitmap)
            val payloadId = session.transport.sendFile(uri)
            session.transport.send(
                Frame.PhotoMeta(
                    payloadId = payloadId,
                    byteSize = context.contentResolver
                        .openAssetFileDescriptor(uri, "r")
                        ?.use { it.length }
                        ?: 0L,
                    widthPx = background.widthPx,
                    heightPx = background.heightPx,
                    mime = "image/jpeg",
                    targetPeerId = "",
                )
            )
        } else {
            session.transport.send(Frame.PhotoRemove(targetPeerId = ""))
        }
    }
}

// SnapshotReq 를 받은 쪽에서 호출. 현재 strokes + photo 를 상대에게 전송.
// 1) strokes → cache CBOR 파일 → sendFile → Frame.Snapshot(strokesPayloadId, hasPhoto) 송신
//    (BYTES 32KB 한도 회피 — Phase 3.5-A. 큰 캔버스에서도 안전)
// 2) hasPhoto == true: bitmap → cache JPEG → sendFile + Frame.PhotoMeta
//    hasPhoto == false: Frame.PhotoRemove (상대 캔버스의 사진도 제거되도록)
//
// Phase 4-G: targetPeerId 가 비어있으면 broadcast (Duo 1:1 — 어차피 1명). 명시되면 호스트가
// 직접 연결된 피어면 sendTo, 아니면 (다른 조인자) 호스트 거쳐 relay 위해 broadcast(여기서는
// 호스트만 받음 → 호스트가 relay). 가장 단순하게 호스트가 target 인 경우와 그 외를 분기:
//   - 직접 연결 (handshakes 안에 target peerId): sendFileTo / sendTo 로 unicast
//   - 그 외: send / sendFile broadcast → 호스트가 relay (handleIncoming.Snapshot/PhotoMeta
//     에서 pendingRelays 등록 후 forwardFile)
private suspend fun respondToSnapshotRequest(
    context: android.content.Context,
    session: SessionManager,
    targetPeerId: String,
    strokes: List<com.rts.rys.ryy.drawingtogether.drawing.model.Stroke>,
    background: BackgroundImage?,
) {
    if (session.state.value !is SessionState.Connected) return
    runCatching {
        // 1) strokes → FILE + Snapshot 메타
        val strokesUri = strokesToCacheUri(context, strokes)
        val strokesPayloadId = session.transport.sendFile(strokesUri)
        session.transport.send(
            Frame.Snapshot(
                strokesPayloadId = strokesPayloadId,
                hasPhoto = background != null,
                targetPeerId = targetPeerId,
            )
        )
        if (background != null) {
            val uri = bitmapToCacheUri(context, background.bitmap)
            val payloadId = session.transport.sendFile(uri)
            session.transport.send(
                Frame.PhotoMeta(
                    payloadId = payloadId,
                    byteSize = context.contentResolver
                        .openAssetFileDescriptor(uri, "r")
                        ?.use { it.length }
                        ?: 0L,
                    widthPx = background.widthPx,
                    heightPx = background.heightPx,
                    mime = "image/jpeg",
                    targetPeerId = targetPeerId,
                )
            )
        } else {
            session.transport.send(Frame.PhotoRemove(targetPeerId = targetPeerId))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    mode: DrawMode = DrawMode.Single,
    onBack: () -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DrawingViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val snackbarHostState = remember { SnackbarHostState() }
    var showSaveDialog by remember { mutableStateOf(false) }
    // Phase 4-G: 동기화 다이얼로그 단계 — Duo 는 컨펌 1단계, Party 는 피커 → 컨펌 2단계.
    var syncStep by remember { mutableStateOf<SyncStep>(SyncStep.None) }
    // 모임 모드 동기화 응답 후 broadcast 대기 플래그. strokes 가 먼저 적용된 다음 사진이
    // 도착해 vm.canvas.background 까지 갱신된 시점에 broadcast 송신 — 사진 적용 전에 송신하면
    // 사진 없는 빈 캔버스가 다른 참가자의 미니뷰에 가버린다.
    var pendingPartySyncBroadcast by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }

    // Phase 4-D: 모드별 SessionManager 인스턴스. Single 도 Duo 싱글톤을 쓰지만
    // Pairing 을 거치지 않으니 transport 가 idle 이라 무해.
    val transportMode = when (mode) {
        DrawMode.Party -> TransportMode.Party
        else -> TransportMode.Duo
    }
    val session = remember(transportMode) { SessionManager.get(context, transportMode) }
    val sessionState by session.state.collectAsState()

    // Phase 4-B: 발신 DrawingEvent.authorId 를 실제 설치 UUID 로 박는다.
    // 1:1 함께 모드에서도 정확한 작성자 식별 — 싱글 모드는 Pairing 거치지 않고 진입이라
    // 무해 (canvas 가 author 를 라우팅에 쓰지 않음).
    LaunchedEffect(session) {
        vm.setAuthor(PeerId(session.peerId))
    }

    // Phase 4-C: 모임 모드는 인바운드 stroke 을 peer 별 캔버스로 라우팅. 그 외는 Shared 유지.
    LaunchedEffect(mode) {
        vm.setRouting(
            when (mode) {
                DrawMode.Party -> CanvasRouting.PerPeer
                else -> CanvasRouting.Shared
            }
        )
    }

    // 끊긴 피어의 미니 캔버스 정리 — remotePeers 에 없는 PeerId 의 캔버스 데이터 제거.
    if (mode == DrawMode.Party) {
        LaunchedEffect(session) {
            session.remotePeers.collect { peers ->
                val active = peers.map { it.peerId }.toSet()
                vm.peerCanvases.keys.toList().forEach { id ->
                    if (id !in active) vm.peerCanvases.remove(id)
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (session.state.value is SessionState.Connected ||
                session.state.value is SessionState.Handshaking) {
                session.disconnect()
            }
        }
    }

    // 예상치 못한 끊김 알림. 사용자의 명시적 disconnect 는 SessionManager 에서 Failed 를 우회하므로
    // 여기 알림은 진짜 끊김에서만 발화. 캔버스 데이터는 그대로 남아있고, "재연결" 액션을 누르면
    // PairingScreen 으로 가서 재페어링. 페어링 성공 시 popBackStack 으로 이 화면으로 복귀 →
    // 같은 ViewModel + CanvasState 유지, 끊기기 전 자기 stroke 그대로 보존.
    //
    // 모임 모드 호스트 예외: 마지막 조인자가 나가도 알림 안 띄움 — 호스트는 그대로 자기 캔버스에서
    // 작업하다가 도구바 "방 열기" 로 새 조인자를 받는 흐름을 쓴다.
    LaunchedEffect(sessionState) {
        if (sessionState is SessionState.Failed) {
            val isPartyHost = mode == DrawMode.Party &&
                session.transport.localRole == com.rts.rys.ryy.drawingtogether.transport.Role.Host
            if (isPartyHost) return@LaunchedEffect
            val result = snackbarHostState.showSnackbar(
                message = "상대와의 연결이 끊겼어요. 그림은 그대로예요.",
                actionLabel = "재연결",
                duration = SnackbarDuration.Long,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onReconnect()
            }
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

    // 원격 배경 변경. 모드별 라우팅:
    //  - 함께 모드(Duo): 자기 메인 캔버스에 적용 (1:1 공유 캔버스)
    //  - 모임 모드(Party): 발신자의 peerCanvases.background 에 적용 — 자기 메인은 변경 없음.
    //    다른 사람들이 보는 그 발신자의 미니뷰에 사진이 나타남.
    //  - sender 미상 (안전망): 자기 메인 fallback.
    LaunchedEffect(vm, session) {
        session.incomingBackground.collect { change ->
            val partyTargetCanvas = if (mode == DrawMode.Party) {
                change.senderPeerId?.let { id ->
                    vm.peerCanvases.getOrPut(id) {
                        com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState()
                    }
                }
            } else null
            when (change) {
                is BackgroundChange.Photo -> {
                    runCatching {
                        PhotoLoader.load(context, change.uri, BackgroundImage.Source.Remote)
                    }.onSuccess { image ->
                        if (partyTargetCanvas != null) partyTargetCanvas.setBackground(image)
                        else vm.setBackground(image)
                    }
                }
                is BackgroundChange.Remove -> {
                    if (partyTargetCanvas != null) partyTargetCanvas.setBackground(null)
                    else vm.setBackground(null)
                }
            }
            // 모임 모드 동기화 응답 — strokes 적용 후 사진까지 적용된 시점에 자기 캔버스를
            // broadcast 해 다른 참가자가 보는 내 미니뷰도 동기화. sender=null 은 동기화 응답
            // (자기 메인 적용) 케이스라 broadcast 트리거 시점으로 적절.
            if (mode == DrawMode.Party &&
                change.senderPeerId == null &&
                pendingPartySyncBroadcast
            ) {
                pendingPartySyncBroadcast = false
                broadcastMyCanvasAsPeer(
                    context = context,
                    session = session,
                    strokes = vm.canvas.strokes.toList(),
                    background = vm.canvas.background,
                )
            }
        }
    }
    LaunchedEffect(vm, session) {
        session.incomingMergeToggle.collect { enabled ->
            vm.setMergeBackgroundOnSave(enabled)
        }
    }

    // "동기화" — 상대가 내 캔버스 상태를 요청 (SnapshotReq). 현재 strokes + photo 로 응답.
    // Phase 4-G: requester peerId 가 동반 → 응답 frame 에 박아 호스트 relay 가 라우팅 가능.
    LaunchedEffect(vm, session) {
        session.snapshotRequests.collect { request ->
            respondToSnapshotRequest(
                context = context,
                session = session,
                targetPeerId = request.requesterPeerId.value,
                strokes = vm.canvas.strokes.toList(),
                background = vm.canvas.background,
            )
        }
    }
    // 원격 stroke 통째 도착. 모드별 라우팅:
    //  - 함께 모드(Duo): sender 가 박혀 와도 무시. 1:1 공유 캔버스라 항상 자기 메인에 덮어쓰기.
    //  - 모임 모드(Party): sender 라우팅
    //      sender == null (동기화 응답) → 자기 메인 + 사진 적용 후 broadcast 송신 플래그
    //      sender != null (broadcast) → peerCanvases[sender] 적용 (그 peer 미니뷰 동기화)
    LaunchedEffect(vm, session) {
        session.incomingSnapshot.collect { event ->
            if (mode != DrawMode.Party) {
                vm.applyRemoteSnapshot(event.strokes)
                return@collect
            }
            val sender = event.senderPeerId
            if (sender == null) {
                vm.applyRemoteSnapshot(event.strokes)
                pendingPartySyncBroadcast = true
            } else {
                val peerCanvas = vm.peerCanvases.getOrPut(sender) {
                    com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState()
                }
                peerCanvas.applySnapshot(event.strokes)
            }
        }
    }

    // 사진 송수신 로딩 오버레이. 최대 2분, 타임아웃·실패 시 토스트.
    var transferLabel by remember { mutableStateOf<String?>(null) }
    var transferFraction by remember { mutableStateOf(0f) }
    LaunchedEffect(session) {
        session.photoTransfers.collect { event ->
            when (event.status) {
                FileTransferStatus.InProgress -> {
                    transferLabel = if (event.direction == FileTransferDirection.Outgoing)
                        "사진 전송 중..." else "사진 받는 중..."
                    transferFraction = event.fraction
                }
                FileTransferStatus.Success -> {
                    transferLabel = null
                    transferFraction = 0f
                }
                FileTransferStatus.Failure -> {
                    transferLabel = null
                    transferFraction = 0f
                    Toast.makeText(context, "사진 동기화에 실패했어요.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    // 2분 타임아웃 — transferLabel 이 켜진 채로 120초 이상 유지되면 강제 해제 + 토스트.
    // key 를 transferLabel 로 두면 새 transfer 시작·종료마다 타이머 리셋.
    // Phase 4 모임 모드에선 호스트 relay 거치는 사진 전송도 있어 1분이 빠듯할 수 있음 → 2분으로.
    LaunchedEffect(transferLabel) {
        if (transferLabel != null) {
            delay(120_000L)
            if (transferLabel != null) {
                transferLabel = null
                transferFraction = 0f
                Toast.makeText(context, "사진 동기화가 2분을 넘겨 중단했어요.", Toast.LENGTH_SHORT).show()
            }
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
                }.onSuccess { image ->
                    vm.setBackground(image)
                    Toast.makeText(context, ASPECT_TOAST_TEXT, Toast.LENGTH_SHORT).show()
                    shareBackgroundToPeer(context, session, image)
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
                }.onSuccess { image ->
                    vm.setBackground(image)
                    Toast.makeText(context, ASPECT_TOAST_TEXT, Toast.LENGTH_SHORT).show()
                    shareBackgroundToPeer(context, session, image)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
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
                val actionsScroll = rememberScrollState()
                val actionsFadeColor = MaterialTheme.colorScheme.surface
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(actionsScroll)
                        .fadingEdgeHorizontal(
                            leftFade = actionsScroll.canScrollBackward,
                            rightFade = actionsScroll.canScrollForward,
                            fadeColor = actionsFadeColor,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MergeBackgroundToggle(
                        checked = vm.canvas.mergeBackgroundOnSave,
                        onCheckedChange = { value ->
                            vm.setMergeBackgroundOnSave(value)
                            // 모임 모드는 자기 토글만 적용, broadcast 안 함 (자기 사진 정책).
                            if (mode != DrawMode.Party &&
                                session.state.value is SessionState.Connected) {
                                scope.launch {
                                    runCatching { session.transport.send(Frame.MergeBackground(value)) }
                                }
                            }
                        },
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
                            onClick = {
                                vm.setBackground(null)
                                if (session.state.value is SessionState.Connected) {
                                    scope.launch {
                                        runCatching { session.transport.send(Frame.PhotoRemove()) }
                                    }
                                }
                            },
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
                // Phase 2 — 함께 모드 연결 상태 표시. weight(1f) Row 다음이라 우측에 고정.
                val s = sessionState
                if (s is SessionState.Connected) {
                    PeerIndicator(nick = s.remoteNick)
                }
            },
        )

        // Phase 4-E: 모임 모드는 캔버스 영역을 자기 캔버스 + 미니 뷰로 분할.
        // 그 외(싱글/함께)는 기존 단일 캔버스 letterbox.
        if (mode == DrawMode.Party) {
            val cfg = LocalConfiguration.current
            val isLandscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
            val peers by session.remotePeers.collectAsState()
            PartyCanvasArea(
                vm = vm,
                peers = peers,
                isLandscape = isLandscape,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                MyCanvasContent(vm = vm)
            }
        }

        // 모임 모드 호스트만 "방 열기" 버튼 노출 — 광고를 다시 켜 새 조인자(또는 끊긴 조인자의
        // 재참여) 를 받는다. localRole 은 transport.stop() 까지는 변경되지 않으니 화면 진입 시점에
        // 한 번 평가하면 충분.
        val isPartyHost = mode == DrawMode.Party &&
            session.transport.localRole == com.rts.rys.ryy.drawingtogether.transport.Role.Host

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
            guideCross = vm.guideCross,
            guideGrid = vm.guideGrid,
            onToggleGuideCross = vm::toggleGuideCross,
            onSelectGuideGrid = vm::selectGuideGrid,
            // 동기화 버튼은 Connected 일 때만 노출. 모드별 다이얼로그 분기:
            //  - Duo: 바로 컨펌 (1:1 이라 상대 1명 확정)
            //  - Party: 피어 피커 (target 선택) → 컨펌
            onSync = if (sessionState is SessionState.Connected) {
                {
                    syncStep = if (mode == DrawMode.Party) {
                        SyncStep.PartyPicker
                    } else {
                        SyncStep.DuoConfirm
                    }
                }
            } else null,
            onOpenRoom = if (isPartyHost) {
                {
                    scope.launch {
                        runCatching { session.transport.startAdvertising() }
                            .onSuccess {
                                Toast.makeText(
                                    context,
                                    "방을 열었어요. 새 친구가 들어올 수 있어요.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
        // 끊김 알림 / 재연결 액션을 위한 SnackBar.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val activeLabel = transferLabel
    if (activeLabel != null) {
        TransferLoadingOverlay(label = activeLabel, fraction = transferFraction)
    }

    // Phase 4-G: 동기화 다이얼로그 — Duo 1단계 / Party 2단계.
    when (val step = syncStep) {
        SyncStep.None -> Unit
        SyncStep.DuoConfirm -> {
            AlertDialog(
                onDismissRequest = { syncStep = SyncStep.None },
                title = { Text("동기화") },
                text = {
                    Text("상대방 데이터를 전부 가져와 적용 합니다.\n내가 그린 그림은 전부 제거됩니다.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        syncStep = SyncStep.None
                        scope.launch {
                            runCatching { session.transport.send(Frame.SnapshotReq()) }
                        }
                    }) { Text("적용") }
                },
                dismissButton = {
                    TextButton(onClick = { syncStep = SyncStep.None }) { Text("취소") }
                },
            )
        }
        SyncStep.PartyPicker -> {
            val peers by session.remotePeers.collectAsState()
            AlertDialog(
                onDismissRequest = { syncStep = SyncStep.None },
                title = { Text("누구 캔버스를 가져올까요?") },
                text = {
                    Column {
                        if (peers.isEmpty()) {
                            Text(
                                text = "아직 참가자가 없어요.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            peers.forEach { peer ->
                                TextButton(
                                    onClick = {
                                        syncStep = SyncStep.PartyConfirm(peer)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("🟢  ${peer.nick}")
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { syncStep = SyncStep.None }) { Text("취소") }
                },
            )
        }
        is SyncStep.PartyConfirm -> {
            AlertDialog(
                onDismissRequest = { syncStep = SyncStep.None },
                title = { Text("${step.target.nick} 의 캔버스 가져오기") },
                text = {
                    Column {
                        Text(
                            text = "상대방 데이터를 전부 가져와 적용 합니다.\n" +
                                "내가 그린 그림은 전부 제거됩니다.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ⓘ 상대방 사진이 있는 경우 사진 정보도 가져옵니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        syncStep = SyncStep.None
                        scope.launch {
                            runCatching {
                                session.transport.send(
                                    Frame.SnapshotReq(
                                        targetPeerId = step.target.peerId.value,
                                        requesterPeerId = session.peerId,
                                    )
                                )
                            }
                        }
                    }) { Text("적용") }
                },
                dismissButton = {
                    TextButton(onClick = { syncStep = SyncStep.None }) { Text("취소") }
                },
            )
        }
    }

    // 모임 모드 호스트가 "방 열기" 로 광고를 다시 켰을 때 새 조인자의 토큰 컨펌 다이얼로그.
    // PartyPairingScreen 의 동일 흐름을 Draw 단계에서도 노출 — 새 조인자가 자연스럽게 합류.
    val pendingConn by session.transport.pending.collectAsState()
    val p = pendingConn
    if (p != null && mode == DrawMode.Party) {
        AlertDialog(
            onDismissRequest = {
                scope.launch { session.transport.rejectPending() }
            },
            title = { Text("${p.remoteNick}과 연결할까요?") },
            text = {
                Column {
                    Text("양쪽 기기에 같은 토큰이 보이면 안전한 연결입니다.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = p.token,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { session.transport.acceptPending() }
                }) { Text("연결") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { session.transport.rejectPending() }
                }) { Text("취소") }
            },
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
                Toast.makeText(context, SAVED_TOAST_TEXT, Toast.LENGTH_LONG).show()
            }
        }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("작품 저장") },
            text = {
                Column {
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "💡 앱 안에 저장돼요. 갤러리로 내보내려면 \"최근 작업\" " +
                            "에서 작품을 열어 \"저장\" 을 눌러주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

// 자기 캔버스 — 사진 있으면 사진 비율 letterbox, 없으면 fillMaxSize 흰 배경.
// 모든 모드 동일 동작 — 모임 모드도 자기 캔버스는 자기 사진 비율로 표시.
// (트레이드오프: 자기 캔버스 비율과 미니 뷰 슬롯 비율이 다르면 같은 stroke 가 모양이 약간
// 다르게 보임. 사진 비율 letterbox 의 자연스러움을 우선.)
@Composable
private fun MyCanvasContent(vm: DrawingViewModel) {
    val bg = vm.canvas.background
    val canvasModifier = if (bg != null) {
        Modifier.aspectRatio(bg.aspectRatio).background(Color.White)
    } else {
        Modifier.fillMaxSize().background(Color.White)
    }
    DrawingCanvas(
        state = vm.canvas,
        tool = vm.tool,
        onStrokeStart = vm::strokeStart,
        onStrokeAppend = vm::strokeAppend,
        onStrokeEnd = vm::strokeEnd,
        modifier = canvasModifier,
        guideCross = vm.guideCross,
        guideGridCells = vm.guideGrid.cells,
    )
}

// Phase 4-E: 모임 모드 캔버스 영역. 자기 캔버스 + 피어 미니 뷰들.
// doc/ui-layout.md §5.4 의 weight(3f)/weight(1f) 가이드.
// - 자기 캔버스 forceSquare = 1:1 letterbox → 모든 참가자 동일 비율 → 미니 뷰에서 모양 일치.
// - 미니 슬롯은 항상 PARTY_MINI_SLOTS 개 고정 — 빈 자리는 EmptyMiniSlot placeholder. 새 조인자
//   들어와도 자기 캔버스 영역 크기가 변하지 않는다.
@Composable
private fun PartyCanvasArea(
    vm: DrawingViewModel,
    peers: List<com.rts.rys.ryy.drawingtogether.session.RemotePeerInfo>,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isLandscape) {
        Row(modifier = modifier) {
            Box(
                modifier = Modifier
                    .weight(3f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                MyCanvasContent(vm = vm)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                PartyMiniSlots(
                    vm = vm,
                    peers = peers,
                    slotModifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    } else {
        Column(modifier = modifier) {
            Box(
                modifier = Modifier
                    .weight(3f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                MyCanvasContent(vm = vm)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                PartyMiniSlots(
                    vm = vm,
                    peers = peers,
                    slotModifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

// PARTY_MINI_SLOTS 개 슬롯 — peers 가 차례로 채우고 빈 자리는 EmptyMiniSlot.
// 호스트 + 조인자 = 4명, 자기 제외하면 미니 최대 3 슬롯.
private const val PARTY_MINI_SLOTS = 3

@Composable
private fun PartyMiniSlots(
    vm: DrawingViewModel,
    peers: List<com.rts.rys.ryy.drawingtogether.session.RemotePeerInfo>,
    slotModifier: Modifier,
) {
    repeat(PARTY_MINI_SLOTS) { i ->
        val peer = peers.getOrNull(i)
        if (peer != null) {
            MiniCanvas(
                nick = peer.nick,
                state = vm.peerCanvases.getOrPut(peer.peerId) {
                    com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState()
                },
                modifier = slotModifier,
            )
        } else {
            EmptyMiniSlot(modifier = slotModifier)
        }
    }
}
