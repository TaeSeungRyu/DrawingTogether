package com.rts.rys.ryy.drawingtogether.session

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import com.rts.rys.ryy.drawingtogether.drawing.model.Stroke
import com.rts.rys.ryy.drawingtogether.transport.FileTransferEvent
import com.rts.rys.ryy.drawingtogether.transport.Frame
import com.rts.rys.ryy.drawingtogether.transport.PROTO_VERSION
import com.rts.rys.ryy.drawingtogether.transport.Transport
import com.rts.rys.ryy.drawingtogether.transport.TransportState
import com.rts.rys.ryy.drawingtogether.transport.nearby.NearbyTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID

// 세션 상태 머신 + 핸드셰이크. doc/architecture.md §2-3, doc/protocol.md §5.
// Idle → Pairing → Handshaking → Connected (혹은 Failed/Disconnected)
sealed class SessionState {
    data object Idle : SessionState()
    data object Pairing : SessionState()      // 페어링 화면 활성, 광고/검색 또는 토큰 대기
    data object Handshaking : SessionState()  // 전송 연결됨, HELLO 교환 중
    data class Connected(val remoteNick: String) : SessionState()
    data class Failed(val reason: String) : SessionState()
}

// 원격에서 도착한 배경 변경. Photo = 새 사진 적용, Remove = 배경 제거.
sealed class BackgroundChange {
    data class Photo(val uri: Uri) : BackgroundChange()
    data object Remove : BackgroundChange()
}

// 프로세스 전역 싱글톤. WorkStore와 동일한 패턴.
class SessionManager private constructor(
    val transport: Transport,
    private val prefs: SharedPreferences,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _nick = MutableStateFlow(prefs.getString(KEY_NICK, null) ?: "")
    val nick: StateFlow<String> = _nick.asStateFlow()

    // 원격 작성자가 보낸 DrawingEvent. Frame.Event 만 추려서 노출.
    // DrawingScreen 이 collect 해서 DrawingViewModel.applyRemoteEvent 에 전달한다.
    private val _incomingDrawing = MutableSharedFlow<DrawingEvent>(extraBufferCapacity = 256)
    val incomingDrawing: SharedFlow<DrawingEvent> = _incomingDrawing.asSharedFlow()

    // 원격에서 도착한 배경 변경 (사진 적용 or 제거).
    // PhotoMeta + FILE 페이로드 매칭이 끝난 시점에만 Photo 가 발행된다.
    private val _incomingBackground = MutableSharedFlow<BackgroundChange>(extraBufferCapacity = 4)
    val incomingBackground: SharedFlow<BackgroundChange> = _incomingBackground.asSharedFlow()

    // 원격에서 도착한 "저장 시 배경 합치기" 토글 값.
    private val _incomingMergeToggle = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val incomingMergeToggle: SharedFlow<Boolean> = _incomingMergeToggle.asSharedFlow()

    // 사진 송수신 진행률. transport.fileTransfers 패스스루 — 현재 프로토콜에서 FILE = 사진.
    val photoTransfers: SharedFlow<FileTransferEvent> get() = transport.fileTransfers

    // "동기화" — 상대가 내 캔버스 상태를 요청. DrawingScreen 이 canvas 의 strokes/photo 로 응답.
    private val _snapshotRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val snapshotRequests: SharedFlow<Unit> = _snapshotRequests.asSharedFlow()

    // 내가 SnapshotReq 를 보냈고, 상대가 Snapshot 으로 응답한 stroke 목록.
    private val _incomingSnapshot = MutableSharedFlow<List<Stroke>>(extraBufferCapacity = 4)
    val incomingSnapshot: SharedFlow<List<Stroke>> = _incomingSnapshot.asSharedFlow()

    // PhotoMeta 와 FILE 페이로드는 도착 순서가 달라질 수 있으니 양방향 버퍼.
    private val pendingPhotoMeta = mutableMapOf<Long, Frame.PhotoMeta>()
    private val pendingPhotoFiles = mutableMapOf<Long, Uri>()

    val peerId: String = prefs.getString(KEY_PEER_ID, null) ?: run {
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_PEER_ID, newId).apply()
        newId
    }

    private var remoteHelloReceived: Frame.Hello? = null
    private var localAckSent: Boolean = false
    private var remoteAckReceived: Boolean = false

    init {
        // transport 상태 → 세션 상태 + 핸드셰이크 트리거
        transport.state
            .onEach { handleTransportState(it) }
            .launchIn(scope)
        transport.incoming
            .onEach { handleIncoming(it) }
            .launchIn(scope)
        // FILE 페이로드 도착 → 대응 PhotoMeta 매칭 시도
        transport.incomingFiles
            .onEach { file ->
                val meta = pendingPhotoMeta.remove(file.payloadId)
                if (meta != null) {
                    _incomingBackground.tryEmit(BackgroundChange.Photo(file.uri))
                } else {
                    pendingPhotoFiles[file.payloadId] = file.uri
                }
            }
            .launchIn(scope)
    }

    fun setNick(value: String) {
        val trimmed = value.trim().take(20).ifBlank { "User" }
        _nick.value = trimmed
        prefs.edit().putString(KEY_NICK, trimmed).apply()
        transport.setLocalNick(trimmed)
    }

    fun enterPairing() {
        if (_state.value !is SessionState.Connected) {
            _state.value = SessionState.Pairing
            transport.setLocalNick(_nick.value.ifBlank { "User" })
        }
    }

    fun disconnect() {
        scope.launch {
            if (_state.value is SessionState.Connected) {
                runCatching { transport.send(Frame.Bye("user-quit")) }
            }
            // transport.stop() 가 onDisconnected 콜백을 비동기로 발화시키고, 그 결과
            // handleTransportState 가 Connected→Failed("disconnected") 로 잠깐 뒤집을 수 있다.
            // 사용자 의도 disconnect 는 Failed 알림을 띄우면 안 되므로 먼저 Idle 로 박는다.
            _state.value = SessionState.Idle
            transport.stop()
            resetHandshake()
        }
    }

    private fun handleTransportState(s: TransportState) {
        when (s) {
            is TransportState.Connected -> {
                // 양쪽 모두 핸드셰이크로 진입. HELLO를 송신.
                _state.value = SessionState.Handshaking
                resetHandshake()
                scope.launch {
                    transport.send(
                        Frame.Hello(
                            proto = PROTO_VERSION,
                            peerId = peerId,
                            nick = _nick.value.ifBlank { "User" },
                        )
                    )
                }
            }
            is TransportState.Failed -> {
                _state.value = SessionState.Failed(s.reason)
            }
            TransportState.Idle -> {
                // 연결됐다가 끊긴 경우만 Disconnected 알림. Pairing 진행 중 Idle은 무시.
                if (_state.value is SessionState.Connected ||
                    _state.value is SessionState.Handshaking) {
                    _state.value = SessionState.Failed("disconnected")
                    resetHandshake()
                }
            }
            else -> Unit
        }
    }

    private fun handleIncoming(frame: Frame) {
        when (frame) {
            is Frame.Event -> {
                // 원격 DrawingEvent → DrawingScreen 으로 흘려보냄.
                // 핸드셰이크 완료(Connected) 후에만 의미 있는 이벤트이지만,
                // 안전상 그 외 상태에서도 들어오면 일단 broadcast 만 하고 적용은 소비자가 결정.
                _incomingDrawing.tryEmit(frame.e)
            }
            is Frame.PhotoMeta -> {
                // 짝이 되는 FILE 페이로드가 이미 도착해 있는지 확인.
                val fileUri = pendingPhotoFiles.remove(frame.payloadId)
                if (fileUri != null) {
                    _incomingBackground.tryEmit(BackgroundChange.Photo(fileUri))
                } else {
                    pendingPhotoMeta[frame.payloadId] = frame
                }
            }
            is Frame.PhotoRemove -> {
                _incomingBackground.tryEmit(BackgroundChange.Remove)
            }
            is Frame.MergeBackground -> {
                _incomingMergeToggle.tryEmit(frame.enabled)
            }
            is Frame.SnapshotReq -> {
                // 상대가 내 캔버스 상태를 요청. DrawingScreen 이 canvas 의 strokes/photo 로 응답.
                _snapshotRequests.tryEmit(Unit)
            }
            is Frame.Snapshot -> {
                // 내가 보낸 SnapshotReq 의 응답 — 받은 strokes 로 내 캔버스 덮어쓸 신호.
                // 사진은 별도 PhotoMeta + FILE (또는 PhotoRemove) 으로 따라옴 — 기존 경로 활용.
                _incomingSnapshot.tryEmit(frame.strokes)
            }
            is Frame.Hello -> {
                if (frame.proto != PROTO_VERSION) {
                    scope.launch {
                        transport.send(Frame.Bye("incompatible-proto"))
                        transport.stop()
                    }
                    _state.value = SessionState.Failed("proto v${frame.proto} 호환 안 됨")
                    return
                }
                remoteHelloReceived = frame
                scope.launch {
                    transport.send(Frame.HelloAck(peerId = peerId))
                    localAckSent = true
                    maybeFinishHandshake()
                }
            }
            is Frame.HelloAck -> {
                remoteAckReceived = true
                maybeFinishHandshake()
            }
            is Frame.Bye -> {
                _state.value = SessionState.Failed("peer-bye: ${frame.reason}")
                transport.stop()
                resetHandshake()
            }
            is Frame.Ping -> {
                scope.launch { transport.send(Frame.Pong(ts = frame.ts)) }
            }
            is Frame.Pong -> Unit
        }
    }

    private fun maybeFinishHandshake() {
        val remote = remoteHelloReceived ?: return
        if (localAckSent && remoteAckReceived) {
            _state.value = SessionState.Connected(remoteNick = remote.nick)
        }
    }

    private fun resetHandshake() {
        remoteHelloReceived = null
        localAckSent = false
        remoteAckReceived = false
    }

    companion object {
        private const val PREFS_NAME = "session_prefs"
        private const val KEY_NICK = "nick"
        private const val KEY_PEER_ID = "peer_id"

        @Volatile private var instance: SessionManager? = null

        fun get(context: Context): SessionManager = instance ?: synchronized(this) {
            instance ?: run {
                val app = context.applicationContext
                val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val transport = NearbyTransport(app)
                SessionManager(transport, prefs).also { instance = it }
            }
        }
    }
}
