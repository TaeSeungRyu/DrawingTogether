package com.rts.rys.ryy.drawingtogether.session

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import com.rts.rys.ryy.drawingtogether.drawing.model.PeerId
import com.rts.rys.ryy.drawingtogether.drawing.model.Stroke
import com.rts.rys.ryy.drawingtogether.transport.ConnectedPeer
import com.rts.rys.ryy.drawingtogether.transport.FileTransferEvent
import com.rts.rys.ryy.drawingtogether.transport.Frame
import com.rts.rys.ryy.drawingtogether.transport.PROTO_VERSION
import com.rts.rys.ryy.drawingtogether.transport.Role
import com.rts.rys.ryy.drawingtogether.transport.Transport
import com.rts.rys.ryy.drawingtogether.transport.TransportState
import com.rts.rys.ryy.drawingtogether.transport.codec.FrameCodec
import com.rts.rys.ryy.drawingtogether.transport.nearby.NearbyTransport
import com.rts.rys.ryy.drawingtogether.transport.nearby.TransportMode
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
import kotlinx.coroutines.withContext
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

// 핸드셰이크가 완료된 원격 피어. 모임 모드의 미니 뷰 라벨 / 동기화 다이얼로그용.
// peerId = Frame.Hello.peerId (설치당 UUID), nick = Hello.nick.
// vm.peerCanvases 의 키와 peerId 가 일치해야 미니 뷰 라벨 매칭이 된다.
data class RemotePeerInfo(
    val endpointId: String,
    val peerId: PeerId,
    val nick: String,
)

// 프로세스 전역 싱글톤. WorkStore와 동일한 패턴.
class SessionManager private constructor(
    val mode: TransportMode,
    val transport: Transport,
    private val prefs: SharedPreferences,
    private val appContext: Context,
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

    // FILE 페이로드와 짝이 되는 메타는 PhotoMeta(사진) 또는 Snapshot(strokes) 두 종류.
    // 도착 순서가 달라질 수 있어서 양방향 버퍼.
    //   pendingPhotoMeta:    PhotoMeta 가 먼저 도착해 FILE 을 기다리는 상태
    //   pendingSnapshotMeta: Frame.Snapshot 이 먼저 도착해 FILE 을 기다리는 상태
    //   pendingFiles:        FILE 이 먼저 도착해 메타를 기다리는 상태 (어느 종류일지는 메타 도착 시 결정)
    private val pendingPhotoMeta = mutableMapOf<Long, Frame.PhotoMeta>()
    private val pendingSnapshotMeta = mutableMapOf<Long, Frame.Snapshot>()
    private val pendingFiles = mutableMapOf<Long, Uri>()

    val peerId: String = prefs.getString(KEY_PEER_ID, null) ?: run {
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_PEER_ID, newId).apply()
        newId
    }

    // Phase 4-B: 피어별 핸드셰이크 상태. 1:1 함께 모드는 항상 size <= 1, 1:N 모임 모드는 최대 3.
    private class PeerHandshake {
        var remoteHello: Frame.Hello? = null
        var localAckSent: Boolean = false
        var remoteAckReceived: Boolean = false
        val done: Boolean
            get() = remoteHello != null && localAckSent && remoteAckReceived
    }
    private val handshakes = mutableMapOf<String, PeerHandshake>()

    // Phase 4-E: 핸드셰이크 완료된 원격 피어 명단. 모임 모드 미니 뷰 라벨 매칭 + 동기화
    // 다이얼로그용. handshakes 갱신 시점마다 동기화 (maybeFinishHandshake / syncHandshakesWithPeers).
    private val _remotePeers = MutableStateFlow<List<RemotePeerInfo>>(emptyList())
    val remotePeers: StateFlow<List<RemotePeerInfo>> = _remotePeers.asStateFlow()

    init {
        // transport 상태 → 세션 상태 전이
        transport.state
            .onEach { handleTransportState(it) }
            .launchIn(scope)
        // 새 피어 등장 → HELLO unicast / 끊긴 피어 → handshake 제거.
        // transport.state 의 Connected 전이와 별도 채널이지만 양쪽 핸들러가 idempotent 라
        // 도착 순서 무관 (handshakes 맵 키 가드 + _state 분기 가드).
        transport.connectedPeers
            .onEach { syncHandshakesWithPeers(it) }
            .launchIn(scope)
        transport.incoming
            .onEach { handleIncoming(it.endpointId, it.frame) }
            .launchIn(scope)
        // FILE 페이로드 도착 → PhotoMeta 또는 Snapshot 메타 매칭 시도
        transport.incomingFiles
            .onEach { file ->
                val photoMeta = pendingPhotoMeta.remove(file.payloadId)
                val snapshotMeta = pendingSnapshotMeta.remove(file.payloadId)
                when {
                    photoMeta != null -> {
                        _incomingBackground.tryEmit(BackgroundChange.Photo(file.uri))
                    }
                    snapshotMeta != null -> {
                        handleSnapshotFile(file.uri)
                    }
                    else -> {
                        // 메타가 아직 도착 안 함 — 일단 buffer, 곧 들어오면 매칭.
                        pendingFiles[file.payloadId] = file.uri
                    }
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
                // 첫 피어 연결 시 Handshaking 으로 전이. 실제 HELLO 송신은 syncHandshakesWithPeers 가
                // connectedPeers 변화를 보고 신규 피어에게 unicast.
                if (_state.value !is SessionState.Connected &&
                    _state.value !is SessionState.Handshaking
                ) {
                    _state.value = SessionState.Handshaking
                }
            }
            is TransportState.Failed -> {
                _state.value = SessionState.Failed(s.reason)
            }
            TransportState.Idle -> {
                // 연결됐다가 끊긴 경우만 Disconnected 알림. Pairing 진행 중 Idle은 무시.
                // 모임 모드 호스트는 마지막 조인자가 끊겨도 sessionState 유지 — 방을 지키는
                // 자연스러운 상태. ("방 열기" / 동기화 버튼 유지)
                val partyHost = mode == TransportMode.Party &&
                    transport.localRole == Role.Host
                if (!partyHost &&
                    (_state.value is SessionState.Connected ||
                        _state.value is SessionState.Handshaking)
                ) {
                    _state.value = SessionState.Failed("disconnected")
                    resetHandshake()
                }
            }
            else -> Unit
        }
    }

    // connectedPeers 변경에 맞춰 handshakes 맵 동기화 + 신규 피어에 HELLO unicast.
    // - 새 피어: handshakes 등록 후 sendTo(Hello) — broadcast 가 아니라 unicast 라
    //   기존 피어에게 HELLO 가 중복 전달되지 않는다.
    // - 사라진 피어: handshakes 에서 제거. 마지막 피어가 빠지면 transport.state 가 Idle 로 가서
    //   handleTransportState 가 Disconnected 처리.
    private fun syncHandshakesWithPeers(peers: List<ConnectedPeer>) {
        val activeIds = peers.map { it.endpointId }.toSet()
        // 끊긴 피어는 remotePeers 에서도 같이 제거 — handshakes 정리 후 publishRemotePeers().
        handshakes.keys.toList().forEach { id ->
            if (id !in activeIds) handshakes.remove(id)
        }
        peers.forEach { peer ->
            if (peer.endpointId !in handshakes.keys) {
                handshakes[peer.endpointId] = PeerHandshake()
                scope.launch {
                    transport.sendTo(
                        peer.endpointId,
                        Frame.Hello(
                            proto = PROTO_VERSION,
                            peerId = peerId,
                            nick = _nick.value.ifBlank { "User" },
                        ),
                    )
                }
            }
        }
        publishRemotePeers()
    }

    // remotePeers 를 handshakes 의 완료된 항목 기준으로 다시 발행. 핸드셰이크가 진행 중인
    // (아직 Hello 수신 전) 피어는 nick 을 모르니 제외.
    private fun publishRemotePeers() {
        _remotePeers.value = handshakes.entries.mapNotNull { (endpointId, h) ->
            val hello = h.remoteHello ?: return@mapNotNull null
            RemotePeerInfo(
                endpointId = endpointId,
                peerId = PeerId(hello.peerId),
                nick = hello.nick,
            )
        }
    }

    private fun handleIncoming(endpointId: String, frame: Frame) {
        when (frame) {
            is Frame.Event -> {
                // 원격 DrawingEvent → DrawingScreen 으로 흘려보냄.
                // 핸드셰이크 완료(Connected) 후에만 의미 있는 이벤트이지만,
                // 안전상 그 외 상태에서도 들어오면 일단 broadcast 만 하고 적용은 소비자가 결정.
                _incomingDrawing.tryEmit(frame.e)
            }
            is Frame.PhotoMeta -> {
                // 짝이 되는 FILE 페이로드가 이미 도착해 있는지 확인.
                val fileUri = pendingFiles.remove(frame.payloadId)
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
                // 1:N 에서는 source endpointId 에만 응답해야 하지만 그 라우팅은 4-G 에서 도입.
                _snapshotRequests.tryEmit(Unit)
            }
            is Frame.Snapshot -> {
                // 내가 보낸 SnapshotReq 의 응답 — strokes 는 FILE 페이로드로 따로 도착.
                // 짝이 되는 FILE 이 이미 와있으면 즉시 처리, 아니면 buffer.
                val fileUri = pendingFiles.remove(frame.strokesPayloadId)
                if (fileUri != null) {
                    handleSnapshotFile(fileUri)
                } else {
                    pendingSnapshotMeta[frame.strokesPayloadId] = frame
                }
                // 사진은 별도 PhotoMeta + FILE (또는 PhotoRemove) 으로 따라옴 — 기존 경로 활용.
            }
            is Frame.Hello -> {
                if (frame.proto != PROTO_VERSION) {
                    scope.launch {
                        transport.sendTo(endpointId, Frame.Bye("incompatible-proto"))
                        // 1:1 호환 위해 전체 stop. 1:N 에서 한 피어만 끊는 정교화는 4-H 에서.
                        transport.stop()
                    }
                    _state.value = SessionState.Failed("proto v${frame.proto} 호환 안 됨")
                    return
                }
                val h = handshakes.getOrPut(endpointId) { PeerHandshake() }
                h.remoteHello = frame
                publishRemotePeers()  // Hello 수신 시점에 nick 알게 됨 → remotePeers 갱신
                scope.launch {
                    transport.sendTo(endpointId, Frame.HelloAck(peerId = peerId))
                    h.localAckSent = true
                    maybeFinishHandshake(endpointId)
                }
            }
            is Frame.HelloAck -> {
                val h = handshakes[endpointId] ?: return
                h.remoteAckReceived = true
                maybeFinishHandshake(endpointId)
            }
            is Frame.Bye -> {
                // 그 endpoint 의 핸드셰이크만 정리 — 다른 피어가 살아있으면 sessionState 그대로.
                handshakes.remove(endpointId)
                publishRemotePeers()
                if (handshakes.isEmpty()) {
                    // 마지막 피어 BYE. 모임 모드 호스트는 "혼자 방을 지키는" 상태로 두어야
                    // "방 열기" 와 동기화 버튼 등 호스트 UI 가 유지된다. 그 외(Duo / Party 조인자)
                    // 는 진짜 끊긴 거라 Failed.
                    val partyHost = mode == TransportMode.Party &&
                        transport.localRole == Role.Host
                    if (!partyHost) {
                        _state.value = SessionState.Failed("peer-bye: ${frame.reason}")
                    }
                }
            }
            is Frame.Ping -> {
                // Pong 은 보낸 피어에게만 unicast — 1:N 에서 broadcast 면 불필요한 RTT 측정 노이즈.
                scope.launch { transport.sendTo(endpointId, Frame.Pong(ts = frame.ts)) }
            }
            is Frame.Pong -> Unit
        }
    }

    // Snapshot FILE 페이로드 (CBOR-encoded List<Stroke>) 를 읽어 incomingSnapshot 으로 발행.
    private fun handleSnapshotFile(uri: Uri) {
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@launch
                val strokes = FrameCodec.decodeStrokes(bytes)
                _incomingSnapshot.tryEmit(strokes)
            }
        }
    }

    // 특정 피어의 핸드셰이크가 완료되면 첫 한 명 기준으로 SessionState.Connected 전이.
    // 1:N 에서는 두 번째/세 번째 피어가 추가로 완료돼도 이미 Connected 상태라 nick 갱신 안 함 —
    // 다중 피어 UI 는 4-E 에서 connectedPeers flow 를 직접 collect 하는 방향으로 정리.
    private fun maybeFinishHandshake(endpointId: String) {
        val h = handshakes[endpointId] ?: return
        if (!h.done) return
        if (_state.value !is SessionState.Connected) {
            val nick = h.remoteHello?.nick ?: "peer"
            _state.value = SessionState.Connected(remoteNick = nick)
        }
    }

    private fun resetHandshake() {
        handshakes.clear()
        publishRemotePeers()
    }

    companion object {
        private const val PREFS_NAME = "session_prefs"
        private const val KEY_NICK = "nick"
        private const val KEY_PEER_ID = "peer_id"

        // Phase 4-D: 모드별 별도 싱글톤. 두 인스턴스는 prefs (peerId/nick) 만 공유하고
        // transport 와 핸드셰이크 상태는 완전 격리. 사용자 요구 "1:1 은 1:1 만, 1:N 은 1:N 만".
        @Volatile private var duoInstance: SessionManager? = null
        @Volatile private var partyInstance: SessionManager? = null

        fun get(
            context: Context,
            mode: TransportMode = TransportMode.Duo,
        ): SessionManager = when (mode) {
            TransportMode.Duo -> duoInstance ?: synchronized(this) {
                duoInstance ?: build(context, mode).also { duoInstance = it }
            }
            TransportMode.Party -> partyInstance ?: synchronized(this) {
                partyInstance ?: build(context, mode).also { partyInstance = it }
            }
        }

        private fun build(context: Context, mode: TransportMode): SessionManager {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val transport = NearbyTransport(app, mode)
            return SessionManager(mode, transport, prefs, app)
        }
    }
}
