package com.rts.rys.ryy.drawingtogether.ui.canvas

import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState
import com.rts.rys.ryy.drawingtogether.works.CanvasColorSampler
import com.rts.rys.ryy.drawingtogether.works.PngComposer
import com.rts.rys.ryy.drawingtogether.works.TimelapseStore
import com.rts.rys.ryy.drawingtogether.works.WorkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ASPECT_TOAST_TEXT = "사진 비율로 화면을 맞췄어요"
// 저장 후 안내 — 길어서 토스트로는 잘림. Snackbar(여러 줄 + 닫기)로 표시.
private const val SAVED_MESSAGE =
    "저장됐어요. 휴대폰 갤러리로 보내려면 \"최근 작업\"에서 작품을 열고 \"갤러리로 보내기\"를 눌러주세요."

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

// "동기화" 응답용 — strokes + 스티커를 CanvasSnapshot 으로 묶어 CBOR 직렬화 후
// cache/snapshots/ 에 저장하고 URI 반환. BYTES 페이로드 32KB 한도를 회피하기 위해 FILE 로 송신.
private suspend fun canvasToCacheUri(
    context: android.content.Context,
    strokes: List<com.rts.rys.ryy.drawingtogether.drawing.model.Stroke>,
    stickers: List<com.rts.rys.ryy.drawingtogether.drawing.model.Sticker>,
    texts: List<com.rts.rys.ryy.drawingtogether.drawing.model.TextElement>,
): android.net.Uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val dir = java.io.File(context.cacheDir, "snapshots").apply { mkdirs() }
    val file = java.io.File(dir, "canvas_${System.currentTimeMillis()}.cbor")
    file.outputStream().use { out ->
        out.write(
            com.rts.rys.ryy.drawingtogether.transport.codec.FrameCodec.encodeCanvas(
                com.rts.rys.ryy.drawingtogether.drawing.model.CanvasSnapshot(strokes, stickers, texts)
            )
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
    stickers: List<com.rts.rys.ryy.drawingtogether.drawing.model.Sticker>,
    texts: List<com.rts.rys.ryy.drawingtogether.drawing.model.TextElement>,
    background: BackgroundImage?,
) {
    if (session.state.value !is SessionState.Connected) return
    runCatching {
        val strokesUri = canvasToCacheUri(context, strokes, stickers, texts)
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
    stickers: List<com.rts.rys.ryy.drawingtogether.drawing.model.Sticker>,
    texts: List<com.rts.rys.ryy.drawingtogether.drawing.model.TextElement>,
    background: BackgroundImage?,
) {
    if (session.state.value !is SessionState.Connected) return
    runCatching {
        // 1) strokes + 스티커 + 텍스트 → FILE + Snapshot 메타
        val strokesUri = canvasToCacheUri(context, strokes, stickers, texts)
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
    onExitToHome: () -> Unit = {},
    modifier: Modifier = Modifier,
    vm: DrawingViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val snackbarHostState = remember { SnackbarHostState() }
    var showSaveDialog by remember { mutableStateOf(false) }
    // 배경색 선택 다이얼로그 표시 여부.
    var showBgColorPicker by remember { mutableStateOf(false) }
    // 기록 중 뒤로가기 시 저장/폐기 확인.
    var showRecordBackConfirm by remember { mutableStateOf(false) }
    // 텍스트 입력 시트 — 캔버스 빈 곳 탭 시 그 정규화 좌표(nx,ny)를 담아 연다. null = 닫힘.
    var pendingTextPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    // 타임랩스 저장 — 인메모리 로그 + 종료 상태 썸네일을 TimelapseStore 에 기록.
    // 썸네일(PngComposer)·디스크 쓰기는 백그라운드에서 — 메인 스레드 프리징(종료 시 멈춤) 방지.
    // 현재 캔버스 내용을 메인에서 가볍게 복사해 넘겨 동시 수정 위험도 없앤다.
    val saveTimelapse: () -> Unit = saveTimelapse@{
        val recorded = vm.finishRecording() ?: return@saveTimelapse
        val strokesCopy = vm.canvas.strokes.toList()
        val stickersCopy = vm.canvas.stickers.toList()
        val textsCopy = vm.canvas.texts.toList()
        val bg = vm.canvas.background
        val bgColor = vm.canvas.backgroundColor
        scope.launch {
            val ok = runCatching {
                val thumb = withContext(Dispatchers.Default) {
                    val snap = CanvasState().apply {
                        applySnapshot(strokesCopy, stickersCopy, textsCopy)
                        setBackground(bg)
                        setBackgroundColor(bgColor)
                    }
                    PngComposer.compose(snap, density)
                }
                TimelapseStore.get(context)
                    .save(recorded.entries, recorded.durationMs, recorded.backgrounds, thumb)
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "타임랩스를 저장했어요" else "타임랩스 저장에 실패했어요",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // 기록 중 시스템 뒤로가기 → 바로 나가지 않고 저장/폐기 확인.
    BackHandler(enabled = vm.isRecording) { showRecordBackConfirm = true }

    // 소프트 가드 — 인메모리 기록이라 너무 길면 앱 종료 시 소실·탐색 둔화 위험. 1분 전 경고,
    // 최대(MAX_RECORD_MS) 도달 시 자동 종료-저장.
    LaunchedEffect(vm.isRecording) {
        if (!vm.isRecording) return@LaunchedEffect
        var warned = false
        while (vm.isRecording) {
            val e = vm.recordingElapsedMs()
            if (!warned && e >= MAX_RECORD_MS - 60_000L) {
                warned = true
                Toast.makeText(context, "곧 최대 기록 시간(${MAX_RECORD_MS / 60_000}분)에 도달해요", Toast.LENGTH_SHORT).show()
            }
            if (e >= MAX_RECORD_MS) {
                Toast.makeText(context, "최대 기록 시간에 도달해 저장했어요", Toast.LENGTH_LONG).show()
                saveTimelapse()
                break
            }
            kotlinx.coroutines.delay(1000)
        }
    }
    // Phase 4-G: 동기화 다이얼로그 단계 — Duo 는 컨펌 1단계, Party 는 피커 → 컨펌 2단계.
    var syncStep by remember { mutableStateOf<SyncStep>(SyncStep.None) }
    // 모임 모드 동기화 응답 후, 내 캔버스를 다른 참가자에게 broadcast 해 그들이 보는 내 미니뷰도
    // 갱신한다. 응답은 strokes(Snapshot) + 배경(PhotoMeta 또는 PhotoRemove) 두 이벤트로 오는데
    // 도착 순서가 엇갈릴 수 있으므로(특히 사진 없을 때 PhotoRemove 가 먼저 올 수 있음), 둘 다
    // 도착한 시점에만 broadcast. 한쪽만 트리거로 쓰면 누락된다.
    var syncGotStrokes by remember { mutableStateOf(false) }
    var syncGotBackground by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }

    // Phase 4-D: 모드별 SessionManager 인스턴스. Single 도 Duo 싱글톤을 쓰지만
    // Pairing 을 거치지 않으니 transport 가 idle 이라 무해.
    val transportMode = when (mode) {
        DrawMode.Party -> TransportMode.Party
        else -> TransportMode.Duo
    }
    val session = remember(transportMode) { SessionManager.get(context, transportMode) }
    val sessionState by session.state.collectAsState()
    // 연결되면 캔버스 우측 상단에 회색으로 표시할 내 닉네임. 미연결(싱글 등)이면 null.
    val myNick by session.nick.collectAsState()
    val canvasSelfNick = if (sessionState is SessionState.Connected) myNick.ifBlank { null } else null

    // "저장 시 배경 합치기" 토글 변경 — 저장 다이얼로그에서 호출. 함께 모드(Duo) 연결 중이면 동기화.
    val onMergeChange: (Boolean) -> Unit = { value ->
        vm.setMergeBackgroundOnSave(value)
        if (mode != DrawMode.Party && session.state.value is SessionState.Connected) {
            scope.launch {
                runCatching { session.transport.send(Frame.MergeBackground(value)) }
            }
        }
    }

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
            val role = session.transport.localRole
            val isPartyHost = mode == DrawMode.Party &&
                role == com.rts.rys.ryy.drawingtogether.transport.Role.Host
            // 모임 호스트: 마지막 조인자가 나가도 알림 없이 방 유지("방 열기"로 재모집).
            if (isPartyHost) return@LaunchedEffect

            // 모임 조인자: 유일한 연결(호스트)이 끊긴 것 = 모임 종료. 재연결 대신 홈 복귀.
            val isPartyJoiner = mode == DrawMode.Party &&
                role == com.rts.rys.ryy.drawingtogether.transport.Role.Joiner
            if (isPartyJoiner) {
                Toast.makeText(
                    context,
                    "방장이 나가 모임이 종료됐어요.",
                    Toast.LENGTH_LONG,
                ).show()
                onExitToHome()
                return@LaunchedEffect
            }

            // 함께 모드(1:1): 끊김 알림 + 재연결.
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
            // 모임 모드 동기화 응답의 배경 부분 도착. sender=null = 동기화 응답(자기 메인 적용).
            if (mode == DrawMode.Party && change.senderPeerId == null) {
                syncGotBackground = true
                if (syncGotStrokes) {
                    syncGotStrokes = false
                    syncGotBackground = false
                    broadcastMyCanvasAsPeer(
                        context = context,
                        session = session,
                        strokes = vm.canvas.strokes.toList(),
                        stickers = vm.canvas.stickers.toList(),
                        texts = vm.canvas.texts.toList(),
                        background = vm.canvas.background,
                    )
                }
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
                stickers = vm.canvas.stickers.toList(),
                texts = vm.canvas.texts.toList(),
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
                vm.applyRemoteSnapshot(event.strokes, event.stickers, event.texts)
                return@collect
            }
            val sender = event.senderPeerId
            if (sender == null) {
                // 동기화 응답의 strokes 부분. 배경까지 도착하면 broadcast.
                vm.applyRemoteSnapshot(event.strokes, event.stickers, event.texts)
                syncGotStrokes = true
                if (syncGotBackground) {
                    syncGotStrokes = false
                    syncGotBackground = false
                    broadcastMyCanvasAsPeer(
                        context = context,
                        session = session,
                        strokes = vm.canvas.strokes.toList(),
                        stickers = vm.canvas.stickers.toList(),
                        texts = vm.canvas.texts.toList(),
                        background = vm.canvas.background,
                    )
                }
            } else {
                val peerCanvas = vm.peerCanvases.getOrPut(sender) {
                    com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState()
                }
                peerCanvas.applySnapshot(event.strokes, event.stickers, event.texts)
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
                IconButton(onClick = { if (vm.isRecording) showRecordBackConfirm = true else onBack() }) {
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 배경 합치기 토글은 상단에서 빼서 저장 다이얼로그로 이동(저장 시에만 의미).
                    TopActionButton(
                        label = "사진",
                        onClick = {
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) { PhotoGlyph(modifier = Modifier.fillMaxSize()) }
                    TopActionButton(
                        label = "촬영",
                        onClick = {
                            val uri = CameraCaptureFile.create(context)
                            pendingCameraUri = uri
                            capturePhoto.launch(uri)
                        },
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) { CameraGlyph(modifier = Modifier.fillMaxSize()) }
                    // 버튼 자체가 현재 배경색 스와치 — 색에 따라 글자/아이콘 대비 자동.
                    val bgSwatch = Color(vm.canvas.backgroundColor)
                    val bgOn = if (bgSwatch.luminance() > 0.5f) Color.Black else Color.White
                    TopActionButton(
                        label = "배경색",
                        onClick = { showBgColorPicker = true },
                        container = bgSwatch,
                        content = bgOn,
                    ) { BgColorGlyph(modifier = Modifier.fillMaxSize()) }
                    if (vm.canvas.background != null) {
                        // 트레이싱 보조 — 사진 표시 농도 순환(원본→연하게→아주 연하게). 표시만, 저장 무관.
                        val tracing = vm.traceOpacity != TraceOpacity.Full
                        TopActionButton(
                            label = if (tracing) vm.traceOpacity.label else "트레이싱",
                            onClick = { vm.cycleTraceOpacity() },
                            container = if (tracing) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.tertiaryContainer,
                            content = if (tracing) MaterialTheme.colorScheme.onTertiary
                            else MaterialTheme.colorScheme.onTertiaryContainer,
                        ) { TraceGlyph(modifier = Modifier.fillMaxSize()) }
                        TopActionButton(
                            label = "제거",
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
                        ) { TrashGlyph(modifier = Modifier.fillMaxSize()) }
                    }
                    TopActionButton(
                        label = "저장",
                        onClick = {
                            nameInput = ""
                            showSaveDialog = true
                        },
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) { SaveGlyph(modifier = Modifier.fillMaxSize()) }
                    // 타임랩스 기록 토글 — 기록 중이면 종료(저장), 아니면 시작.
                    if (vm.isRecording) {
                        TopActionButton(
                            label = "종료",
                            onClick = saveTimelapse,
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        ) { StopGlyph(modifier = Modifier.fillMaxSize()) }
                    } else {
                        TopActionButton(
                            label = "기록",
                            onClick = { vm.startRecording() },
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) { RecordGlyph(modifier = Modifier.fillMaxSize()) }
                    }
                }
            },
        )

        // Phase 4-E + 가로 모드: 캔버스 영역과 도구바를 람다로 추출해
        //  - 세로: 캔버스(위) + 도구바(아래 전체폭)
        //  - 가로: 캔버스(좌) + 도구바(우측 고정폭 패널, 세로 스크롤)
        // 가로에서 큰 도구바가 캔버스를 다 먹던 문제 해결.
        val cfg = LocalConfiguration.current
        val isLandscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
        val peers by session.remotePeers.collectAsState()
        val isPartyHost = mode == DrawMode.Party &&
            session.transport.localRole == com.rts.rys.ryy.drawingtogether.transport.Role.Host

        val canvasArea: @Composable (Modifier) -> Unit = { m ->
            if (mode == DrawMode.Party) {
                PartyCanvasArea(
                    vm = vm,
                    peers = peers,
                    isLandscape = isLandscape,
                    selfNick = canvasSelfNick,
                    modifier = m.background(MaterialTheme.colorScheme.surfaceVariant),
                    onRequestText = { nx, ny -> pendingTextPoint = nx to ny },
                    pendingTextPoint = pendingTextPoint,
                )
            } else {
                Box(
                    modifier = m.background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    MyCanvasContent(
                        vm = vm,
                        selfNick = canvasSelfNick,
                        onRequestText = { nx, ny -> pendingTextPoint = nx to ny },
                        pendingTextPoint = pendingTextPoint,
                    )
                }
            }
        }

        val toolbar: @Composable (Modifier) -> Unit = { m ->
            Toolbar(
                tool = vm.tool,
                canUndo = vm.canvas.canUndo,
                onColor = { argb ->
                    UserPaletteRepo.get(context).addRecent(argb)
                    vm.selectColor(argb)
                },
                onEraser = vm::toggleEraser,
                onEyedropper = vm::toggleEyedropper,
                onBrush = vm::setBrush,
                onShape = vm::setShape,
                onToggleFill = vm::toggleFill,
                onSticker = vm::selectSticker,
                onText = vm::selectText,
                onPen = vm::selectPenFreehand,
                onStrokeWidth = vm::setStrokeWidth,
                onUndo = vm::undoLastLocal,
                onClear = vm::clearAll,
                guideCross = vm.guideCross,
                guideGrid = vm.guideGrid,
                onToggleGuideCross = vm::toggleGuideCross,
                onSelectGuideGrid = vm::selectGuideGrid,
                symmetry = vm.symmetry,
                onSelectSymmetry = vm::selectSymmetry,
                smoothing = vm.smoothing,
                onCycleSmoothing = vm::cycleSmoothing,
                // 동기화 버튼은 Connected 일 때만 노출. 모드별 다이얼로그 분기.
                onSync = if (sessionState is SessionState.Connected) {
                    {
                        syncStep = if (mode == DrawMode.Party) SyncStep.PartyPicker
                        else SyncStep.DuoConfirm
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
                fillHeight = isLandscape,
                modifier = m,
            )
        }

        if (isLandscape) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                canvasArea(Modifier.weight(1f).fillMaxHeight())
                // 도구바가 패널 높이를 꽉 채움(배경 여백 방지). 콘텐츠 넘침은 Toolbar 내부
                // Column 의 verticalScroll 이 처리.
                toolbar(Modifier.width(300.dp).fillMaxHeight())
            }
        } else {
            canvasArea(Modifier.weight(1f).fillMaxWidth())
            toolbar(Modifier.fillMaxWidth())
        }
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

    if (showBgColorPicker) {
        ColorPickerSheet(
            initialColor = vm.canvas.backgroundColor,
            onConfirm = { argb ->
                vm.setBackgroundColor(argb)
                showBgColorPicker = false
            },
            onDismiss = { showBgColorPicker = false },
        )
    }

    // 텍스트 입력 시트 — 확인 시 탭 위치에 현재 펜 색으로 텍스트를 굳힌다.
    pendingTextPoint?.let { (nx, ny) ->
        TextInputSheet(
            onConfirm = { text, sizeFrac ->
                vm.placeText(nx, ny, text, sizeFrac, vm.tool.colorArgb)
                pendingTextPoint = null
            },
            onDismiss = { pendingTextPoint = null },
        )
    }

    if (showRecordBackConfirm) {
        AlertDialog(
            onDismissRequest = { showRecordBackConfirm = false },
            title = { Text("타임랩스 기록 중") },
            text = { Text("기록을 저장하고 나갈까요? 폐기하면 기록은 사라집니다.") },
            confirmButton = {
                TextButton(onClick = {
                    saveTimelapse()
                    showRecordBackConfirm = false
                    onBack()
                }) { Text("저장하고 나가기") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.discardRecording()
                    showRecordBackConfirm = false
                    onBack()
                }) { Text("폐기하고 나가기") }
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
                snackbarHostState.showSnackbar(
                    message = SAVED_MESSAGE,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
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
                    // 사진 배경 포함 토글 — 사진이 있을 때만 활성. 없으면 흐리게 disabled.
                    Spacer(modifier = Modifier.height(12.dp))
                    MergeBackgroundToggle(
                        checked = vm.canvas.mergeBackgroundOnSave,
                        onCheckedChange = onMergeChange,
                        enabled = vm.canvas.background != null,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "💡 앱 안에 저장돼요. 휴대폰 갤러리로 보내려면 \"최근 작업\" " +
                            "에서 작품을 열어 \"갤러리로 보내기\" 를 눌러주세요.",
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
private fun MyCanvasContent(
    vm: DrawingViewModel,
    selfNick: String? = null,
    onRequestText: (Float, Float) -> Unit = { _, _ -> },
    pendingTextPoint: Pair<Float, Float>? = null,
) {
    val bg = vm.canvas.background
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val sizeModifier = if (bg != null) {
        Modifier.aspectRatio(bg.aspectRatio)
    } else {
        Modifier.fillMaxSize()
    }
    Box(modifier = sizeModifier.background(Color.White)) {
        DrawingCanvas(
            state = vm.canvas,
            tool = vm.tool,
            onStrokeStart = vm::strokeStart,
            onStrokeAppend = vm::strokeAppend,
            onStrokeEnd = vm::strokeEnd,
            modifier = Modifier.fillMaxSize(),
            guideCross = vm.guideCross,
            guideGridCells = vm.guideGrid.cells,
            smoothingAlpha = vm.smoothing.alpha,
            backgroundAlpha = vm.traceOpacity.alpha,
            edgeOverlay = if (vm.traceOpacity.edge) vm.edgeOverlay else null,
            onPickColor = { nx, ny ->
                val argb = CanvasColorSampler.sampleColor(vm.canvas, density, nx, ny)
                UserPaletteRepo.get(context).addRecent(argb)
                vm.selectColor(argb)
            },
            onPlaceSticker = vm::placeSticker,
            onTransformStickerLocal = vm::transformStickerLocal,
            onCommitStickerTransform = vm::commitStickerTransform,
            onRemoveSticker = vm::removeSticker,
            onRequestText = onRequestText,
            onRemoveText = vm::removeText,
            pendingTextPoint = pendingTextPoint,
        )
        // 내 닉네임 — 우측 상단 반투명 칩. 사진/그림 위에서도 읽히게 surface 배경 + 회색 글자.
        // pointerInput 없으므로 그리기 터치는 아래 캔버스가 받음.
        if (!selfNick.isNullOrBlank()) {
            Text(
                text = selfNick,
                color = Color.Gray,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(percent = 50),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        // 타임랩스 녹화 인디케이터 — 좌측 상단 ● REC mm:ss (0.5초마다 갱신).
        if (vm.isRecording) {
            var elapsedMs by remember { mutableStateOf(0L) }
            LaunchedEffect(Unit) {
                while (true) {
                    elapsedMs = vm.recordingElapsedMs()
                    kotlinx.coroutines.delay(500)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(percent = 50),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(color = Color(0xFFE53935), radius = size.minDimension / 2f)
                }
                Text(
                    text = "REC ${formatElapsed(elapsedMs)}",
                    color = Color(0xFFE53935),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// 타임랩스 기록 소프트 가드 — 인메모리라 무한정 두면 앱 종료 시 소실·탐색 둔화. 도달 시 자동 저장.
private const val MAX_RECORD_MS: Long = 15 * 60 * 1000L

// 녹화 경과 시간 mm:ss.
private fun formatElapsed(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
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
    selfNick: String? = null,
    modifier: Modifier = Modifier,
    onRequestText: (Float, Float) -> Unit = { _, _ -> },
    pendingTextPoint: Pair<Float, Float>? = null,
) {
    if (isLandscape) {
        Row(modifier = modifier) {
            Box(
                modifier = Modifier
                    .weight(3f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                MyCanvasContent(vm = vm, selfNick = selfNick, onRequestText = onRequestText, pendingTextPoint = pendingTextPoint)
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
                MyCanvasContent(vm = vm, selfNick = selfNick, onRequestText = onRequestText, pendingTextPoint = pendingTextPoint)
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
