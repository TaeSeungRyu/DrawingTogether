package com.rts.rys.ryy.drawingtogether.session

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import com.rts.rys.ryy.drawingtogether.drawing.model.PeerId
import com.rts.rys.ryy.drawingtogether.drawing.model.Sticker
import com.rts.rys.ryy.drawingtogether.drawing.model.Stroke
import com.rts.rys.ryy.drawingtogether.drawing.model.TextElement
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
// senderPeerId: 누가 보냈는지. 모임 모드에서 DrawingScreen 이 발신자의 peerCanvases.background
// 에 적용 (자기 메인 캔버스 X). 함께 모드(Duo) 는 1:1 공유 캔버스라 sender 무시하고 메인에 적용.
// null 인 경우는 sender 매칭 실패 — 안전망 차원, 자기 메인 캔버스에 적용 fallback.
sealed class BackgroundChange {
    abstract val senderPeerId: com.rts.rys.ryy.drawingtogether.drawing.model.PeerId?

    data class Photo(
        val uri: Uri,
        override val senderPeerId: com.rts.rys.ryy.drawingtogether.drawing.model.PeerId? = null,
    ) : BackgroundChange()

    data class Remove(
        override val senderPeerId: com.rts.rys.ryy.drawingtogether.drawing.model.PeerId? = null,
    ) : BackgroundChange()
}

// 핸드셰이크가 완료된 원격 피어. 모임 모드의 미니 뷰 라벨 / 동기화 다이얼로그용.
// peerId = Frame.Hello.peerId (설치당 UUID), nick = Hello.nick.
// vm.peerCanvases 의 키와 peerId 가 일치해야 미니 뷰 라벨 매칭이 된다.
data class RemotePeerInfo(
    val endpointId: String,
    val peerId: PeerId,
    val nick: String,
)

// 원격에서 도착한 stroke 통째 (Snapshot 응답 또는 모임 모드 peer 캔버스 broadcast).
// senderPeerId = null: 동기화 응답 (자기 메인 캔버스에 덮어쓰기).
// senderPeerId 박힘: broadcast (모임 모드 peer 의 미니뷰 동기화 — vm.peerCanvases[sender] 에 적용).
data class IncomingSnapshotEvent(
    val senderPeerId: PeerId?,
    val strokes: List<Stroke>,
    val stickers: List<Sticker> = emptyList(),
    val texts: List<TextElement> = emptyList(),
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
    // Phase 4-G: requesterPeerId 동반 — 모임 모드 응답 시 target 박아 호스트 relay 거치게 한다.
    // Duo (1:1) 는 requester 무시하고 broadcast 응답.
    // forLiveView=true: 교실 조인자가 방장 라이브뷰 채우기용으로 요청(응답은 그 조인자의
    // peerCanvases[host] 로). false: "가져오기"(응답을 요청자 메인에 덮어쓰기).
    data class SnapshotRequest(val requesterPeerId: PeerId, val forLiveView: Boolean = false)
    private val _snapshotRequests = MutableSharedFlow<SnapshotRequest>(extraBufferCapacity = 4)
    val snapshotRequests: SharedFlow<SnapshotRequest> = _snapshotRequests.asSharedFlow()

    // 원격에서 도착한 strokes — 동기화 응답 (target=self) 또는 모임 모드 peer 캔버스 broadcast.
    private val _incomingSnapshot = MutableSharedFlow<IncomingSnapshotEvent>(extraBufferCapacity = 4)
    val incomingSnapshot: SharedFlow<IncomingSnapshotEvent> = _incomingSnapshot.asSharedFlow()

    // Phase 4-H: 호스트가 "그리기 시작" 누르면 broadcast 되는 신호. 조인자가 페어링 화면에서
    // 대기하다가 이 신호 받고 Draw 진입.
    private val _partyStart = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val partyStart: SharedFlow<Unit> = _partyStart.asSharedFlow()

    // 호스트가 PartyStart 를 이미 송신했는지. true 라면 그 후 "방 열기" 로 새로 참여한 조인자
    // 의 핸드셰이크 완료 시점에 그 조인자에게 PartyStart unicast 해 자동으로 Draw 진입시킨다.
    // 안 그러면 새 조인자가 "호스트가 시작하기를 기다리는 중" 화면에서 영원히 대기.
    private var partyStarted: Boolean = false

    // FILE 페이로드와 짝이 되는 메타는 PhotoMeta(사진) 또는 Snapshot(strokes) 두 종류.
    // 도착 순서가 달라질 수 있어서 양방향 버퍼.
    //   pendingPhotoMeta:    PhotoMeta 가 먼저 도착해 FILE 을 기다리는 상태
    //   pendingSnapshotMeta: Frame.Snapshot 이 먼저 도착해 FILE 을 기다리는 상태
    //   pendingFiles:        FILE 이 먼저 도착해 메타를 기다리는 상태 (어느 종류일지는 메타 도착 시 결정)
    private val pendingPhotoMeta = mutableMapOf<Long, Frame.PhotoMeta>()
    // pendingPhotoMeta 와 짝 — 그 PhotoMeta 를 보낸 사람의 PeerId. 모임 모드에서 미니뷰 라우팅에 필요.
    private val pendingPhotoMetaSender = mutableMapOf<Long, PeerId?>()
    private val pendingSnapshotMeta = mutableMapOf<Long, Frame.Snapshot>()
    // pendingSnapshotMeta 와 짝 — null = 동기화 응답 (자기 메인), 박힘 = broadcast (peerCanvases).
    private val pendingSnapshotMetaSender = mutableMapOf<Long, PeerId?>()
    private val pendingFiles = mutableMapOf<Long, Uri>()

    // Phase 4-G/4-H: 호스트 relay 보관소. target!=self 인 Snapshot/PhotoMeta 가 도착하면 frame
    // 을 보관하고, 짝 FILE 도착 시 각 endpoint 로 sendFileTo → 새 payloadId 받아 frame 갱신 후
    // sendTo. key = 원본 payloadId.
    // targetEndpointIds 가 List 인 이유: 모임 모드의 사진 broadcast 는 source 제외 다른 조인자
    // 전체에게 relay (4-H 정책 반전).
    private data class PendingRelay(
        val targetEndpointIds: List<String>,
        val frame: Frame,  // Snapshot 또는 PhotoMeta. payloadId 필드를 갱신해 forward.
    )
    private val pendingRelays = mutableMapOf<Long, PendingRelay>()

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

    // Phase 4-F: 호스트 relay 로 알게 된 다른 조인자들. 조인자 입장에서 자기와 직접 연결 안 됐지만
    // 호스트가 PeerJoined 로 알려준 피어. 호스트 측은 비어있음 (호스트는 모두 직접 연결).
    private data class IndirectPeerInfo(val nick: String)
    private val indirectPeers = mutableMapOf<PeerId, IndirectPeerInfo>()

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
        // FILE 페이로드 도착 → PhotoMeta 또는 Snapshot 메타 매칭 시도.
        // Phase 4-G/4-H: relay 와 자기 처리는 동시에 가능 (모임 모드 사진 broadcast — 호스트가
        // 다른 조인자에게 relay 하면서 자기 캔버스에도 적용). 따라서 relay 처리 후 fall-through
        // 해서 photoMeta/snapshotMeta 매칭도 검사.
        transport.incomingFiles
            .onEach { file ->
                val relay = pendingRelays.remove(file.payloadId)
                if (relay != null) {
                    forwardFile(relay, file.uri)
                }
                val photoMeta = pendingPhotoMeta.remove(file.payloadId)
                val photoMetaSender = pendingPhotoMetaSender.remove(file.payloadId)
                val snapshotMeta = pendingSnapshotMeta.remove(file.payloadId)
                val snapshotSender = pendingSnapshotMetaSender.remove(file.payloadId)
                when {
                    photoMeta != null -> {
                        _incomingBackground.tryEmit(
                            BackgroundChange.Photo(uri = file.uri, senderPeerId = photoMetaSender)
                        )
                    }
                    snapshotMeta != null -> {
                        handleSnapshotFile(file.uri, snapshotSender)
                    }
                    relay != null -> Unit  // unicast relay (Snapshot 응답): 자기 처리 없음.
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

    // 호스트가 PartyPairingScreen 의 "그리기 시작" 을 누른 시점에 호출. 기존 조인자들에게
    // broadcast 송신 + partyStarted 플래그 박음. 그 후 "방 열기" 로 들어오는 새 조인자에게는
    // maybeFinishHandshake 가 자동으로 unicast 한다.
    fun broadcastPartyStart() {
        if (!mode.isStar) return
        partyStarted = true
        scope.launch {
            runCatching { transport.send(Frame.PartyStart) }
        }
    }

    fun disconnect() {
        scope.launch {
            if (_state.value is SessionState.Connected) {
                runCatching { transport.send(Frame.Bye("user-quit")) }
                // Bye 는 Nearby 가 fire-and-forget 으로 큐잉 — 바로 stop() 하면 전송 전에 끊겨
                // 상대(특히 모임 조인자)가 "방장 이탈"을 늦게 감지. 짧게 대기해 전송 시간 확보.
                // (못 받아도 상대의 onDisconnected 가 백업으로 끊김을 감지.)
                kotlinx.coroutines.delay(150)
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
                // 스타(모임/교실) 호스트는 마지막 조인자가 끊겨도 sessionState 유지 — 방을 지키는
                // 자연스러운 상태. ("방 열기" / 동기화 버튼 유지)
                val partyHost = mode.isStar &&
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
        // 끊긴 피어 추출. Phase 4-F: 호스트면 그 피어의 PeerLeft 를 다른 조인자에게 broadcast.
        val removedPeerIds = mutableListOf<String>()
        handshakes.keys.toList().forEach { id ->
            if (id !in activeIds) {
                val removed = handshakes.remove(id)
                removed?.remoteHello?.peerId?.let { removedPeerIds.add(it) }
            }
        }
        if (mode == TransportMode.Party && transport.localRole == Role.Host && removedPeerIds.isNotEmpty()) {
            val survivors = handshakes.keys.toList()
            scope.launch {
                removedPeerIds.forEach { leftPeerId ->
                    survivors.forEach { survivor ->
                        runCatching {
                            transport.sendTo(survivor, Frame.PeerLeft(peerId = leftPeerId))
                        }
                    }
                }
            }
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

    // remotePeers 를 handshakes 의 완료된 항목 + indirectPeers (호스트 relay 로 알게 된 피어) 로
    // 다시 발행. 핸드셰이크가 진행 중인 (아직 Hello 수신 전) 피어는 nick 을 모르니 제외.
    // 조인자 입장: direct = [호스트] + indirect = [다른 조인자들]. 같은 PeerId 가 양쪽에 있을
    // 일은 없음 (직접 연결되었으면 PeerJoined 송신 안 함).
    private fun publishRemotePeers() {
        val direct = handshakes.entries.mapNotNull { (endpointId, h) ->
            val hello = h.remoteHello ?: return@mapNotNull null
            RemotePeerInfo(
                endpointId = endpointId,
                peerId = PeerId(hello.peerId),
                nick = hello.nick,
            )
        }
        val directIds = direct.map { it.peerId }.toSet()
        val indirect = indirectPeers
            .filterKeys { it !in directIds }
            .map { (peerId, info) ->
                RemotePeerInfo(endpointId = "", peerId = peerId, nick = info.nick)
            }
        _remotePeers.value = direct + indirect
    }

    private fun handleIncoming(endpointId: String, frame: Frame) {
        when (frame) {
            is Frame.Event -> {
                // 원격 DrawingEvent → DrawingScreen 으로 흘려보냄.
                // 핸드셰이크 완료(Connected) 후에만 의미 있는 이벤트이지만,
                // 안전상 그 외 상태에서도 들어오면 일단 broadcast 만 하고 적용은 소비자가 결정.
                _incomingDrawing.tryEmit(frame.e)
                // 호스트 relay — source 제외 다른 조인자에게 같은 Event 재송신.
                // 사진 관련 frame (PhotoMeta/PhotoRemove/MergeBackground) 은 의도적으로 relay 안 함
                // (자기 사진은 자기만 보기, 4-D 정책).
                relayIfHost(endpointId, frame)
            }
            is Frame.PeerJoined -> {
                // 조인자 측에서 호스트 relay 로 다른 조인자의 합류 알림 수신.
                indirectPeers[PeerId(frame.peerId)] = IndirectPeerInfo(nick = frame.nick)
                publishRemotePeers()
            }
            is Frame.PeerLeft -> {
                indirectPeers.remove(PeerId(frame.peerId))
                publishRemotePeers()
            }
            is Frame.PartyStart -> {
                _partyStart.tryEmit(Unit)
            }
            is Frame.PhotoMeta -> {
                // target != self → unicast relay (Snapshot 응답 동반 사진 — 호스트 거쳐 가는 케이스).
                if (shouldRelay(frame.targetPeerId)) {
                    pendingRelays[frame.payloadId] = PendingRelay(
                        targetEndpointIds = listOf(
                            endpointForPeerId(frame.targetPeerId) ?: return,
                        ),
                        frame = frame,
                    )
                    return
                }
                // 모임 모드 broadcast (target="") + 호스트 → 다른 조인자에게 broadcast relay
                // + 자기 처리 fall-through.
                if (frame.targetPeerId.isEmpty()) {
                    val others = broadcastRelayTargets(endpointId)
                    if (others.isNotEmpty()) {
                        pendingRelays[frame.payloadId] = PendingRelay(
                            targetEndpointIds = others,
                            frame = frame,
                        )
                    }
                }
                // senderPeerId 라우팅 분기:
                //  - target == self (동기화 응답): 자기 메인 캔버스에 적용 → senderPeerId = null.
                //  - target == "" (broadcast): 발신자의 peerCanvases.background → sender 박음.
                val isSnapshotResponse = frame.targetPeerId.isNotEmpty() &&
                    frame.targetPeerId == peerId
                val senderPeerId = if (isSnapshotResponse) {
                    null
                } else {
                    handshakes[endpointId]?.remoteHello?.peerId?.let(::PeerId)
                }
                val fileUri = pendingFiles.remove(frame.payloadId)
                if (fileUri != null) {
                    _incomingBackground.tryEmit(
                        BackgroundChange.Photo(uri = fileUri, senderPeerId = senderPeerId)
                    )
                } else {
                    pendingPhotoMeta[frame.payloadId] = frame
                    pendingPhotoMetaSender[frame.payloadId] = senderPeerId
                }
            }
            is Frame.PhotoRemove -> {
                // target != self → unicast forward (Snapshot 응답 동반).
                if (shouldRelay(frame.targetPeerId)) {
                    val targetEndpoint = endpointForPeerId(frame.targetPeerId) ?: return
                    scope.launch {
                        runCatching { transport.sendTo(targetEndpoint, frame) }
                    }
                    return
                }
                // 모임 모드 broadcast (target="") + 호스트 → broadcast relay + 자기 적용.
                if (frame.targetPeerId.isEmpty()) {
                    relayIfHost(endpointId, frame)
                }
                // 동기화 응답 (target=self) 면 자기 메인 제거 → sender=null. broadcast 면 sender 박음.
                val isSnapshotResponse = frame.targetPeerId.isNotEmpty() &&
                    frame.targetPeerId == peerId
                val senderPeerId = if (isSnapshotResponse) {
                    null
                } else {
                    handshakes[endpointId]?.remoteHello?.peerId?.let(::PeerId)
                }
                _incomingBackground.tryEmit(BackgroundChange.Remove(senderPeerId = senderPeerId))
            }
            is Frame.MergeBackground -> {
                _incomingMergeToggle.tryEmit(frame.enabled)
            }
            is Frame.SnapshotReq -> {
                // Phase 4-G: target!=self 면 호스트 relay. target 비/self 면 자기가 응답.
                if (shouldRelay(frame.targetPeerId)) {
                    val targetEndpoint = endpointForPeerId(frame.targetPeerId) ?: return
                    scope.launch {
                        runCatching { transport.sendTo(targetEndpoint, frame) }
                    }
                    return
                }
                // requesterPeerId 가 없으면 (Duo broadcast) 그냥 broadcast 요청. 모임 모드는
                // requester 박혀서 응답 시 target 으로 사용.
                val requester = PeerId(frame.requesterPeerId)
                _snapshotRequests.tryEmit(
                    SnapshotRequest(requesterPeerId = requester, forLiveView = frame.forLiveView)
                )
            }
            is Frame.Snapshot -> {
                // target!=self → unicast relay (호스트 거쳐 가는 동기화 응답).
                if (shouldRelay(frame.targetPeerId)) {
                    pendingRelays[frame.strokesPayloadId] = PendingRelay(
                        targetEndpointIds = listOf(
                            endpointForPeerId(frame.targetPeerId) ?: return,
                        ),
                        frame = frame,
                    )
                    return
                }
                // broadcast (target="") + 호스트 → 다른 조인자에게 relay + 자기 처리 fall-through.
                if (frame.targetPeerId.isEmpty()) {
                    val others = broadcastRelayTargets(endpointId)
                    if (others.isNotEmpty()) {
                        pendingRelays[frame.strokesPayloadId] = PendingRelay(
                            targetEndpointIds = others,
                            frame = frame,
                        )
                    }
                }
                // sender 라우팅:
                //  - target == self (동기화 응답): 자기 메인 캔버스 덮어쓰기 → sender=null
                //  - target == "" (broadcast): 발신자 peerCanvases 갱신 → sender 박음
                val isSnapshotResponse = frame.targetPeerId.isNotEmpty() &&
                    frame.targetPeerId == peerId
                val senderPeerId = if (isSnapshotResponse) {
                    null
                } else {
                    handshakes[endpointId]?.remoteHello?.peerId?.let(::PeerId)
                }
                val fileUri = pendingFiles.remove(frame.strokesPayloadId)
                if (fileUri != null) {
                    handleSnapshotFile(fileUri, senderPeerId)
                } else {
                    pendingSnapshotMeta[frame.strokesPayloadId] = frame
                    pendingSnapshotMetaSender[frame.strokesPayloadId] = senderPeerId
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
                val leftHello = handshakes[endpointId]?.remoteHello
                handshakes.remove(endpointId)
                publishRemotePeers()
                // PeerLeft 알림은 "모두가 모두를 보는" Party(mesh) 전용 — 교실은 조인자끼리 안 보임.
                // onDisconnected 가 곧 자동 발화하지만 그땐 handshakes 가 이미 비어있어
                // syncHandshakesWithPeers 가 PeerLeft 를 송신하지 않으므로 여기서 명시 송신.
                if (mode == TransportMode.Party && transport.localRole == Role.Host && leftHello != null) {
                    val survivors = handshakes.keys.toList()
                    scope.launch {
                        survivors.forEach { survivor ->
                            runCatching {
                                transport.sendTo(survivor, Frame.PeerLeft(peerId = leftHello.peerId))
                            }
                        }
                    }
                }
                if (handshakes.isEmpty()) {
                    // 마지막 피어 BYE. 스타(모임/교실) 호스트는 "혼자 방을 지키는" 상태로 두어야
                    // "방 열기" 와 호스트 UI 가 유지된다. 그 외(Duo / 스타 조인자)는 진짜 끊긴 거라 Failed.
                    val isStarHost = mode.isStar && transport.localRole == Role.Host
                    if (!isStarHost) {
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

    // Snapshot FILE 페이로드 (CBOR-encoded CanvasSnapshot) 를 읽어 incomingSnapshot 으로 발행.
    // senderPeerId = null: 동기화 응답. !=null: broadcast (peer 미니뷰 동기화).
    private fun handleSnapshotFile(uri: Uri, senderPeerId: PeerId?) {
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@launch
                val snapshot = FrameCodec.decodeCanvas(bytes)
                _incomingSnapshot.tryEmit(
                    IncomingSnapshotEvent(senderPeerId, snapshot.strokes, snapshot.stickers, snapshot.texts)
                )
            }
        }
    }

    // 특정 피어의 핸드셰이크가 완료되면 첫 한 명 기준으로 SessionState.Connected 전이.
    // 1:N 에서는 두 번째/세 번째 피어가 추가로 완료돼도 이미 Connected 상태라 nick 갱신 안 함 —
    // 다중 피어 UI 는 4-E 에서 connectedPeers flow 를 직접 collect 하는 방향으로 정리.
    // Phase 4-F: 호스트면 새 조인자에게 기존 조인자들 정보, 기존 조인자들에게 새 조인자 정보를
    // 양방향 PeerJoined 로 알림.
    private fun maybeFinishHandshake(endpointId: String) {
        val h = handshakes[endpointId] ?: return
        if (!h.done) return
        val finishedHello = h.remoteHello ?: return
        if (_state.value !is SessionState.Connected) {
            _state.value = SessionState.Connected(remoteNick = finishedHello.nick)
        }
        if (mode.isStar && transport.localRole == Role.Host) {
            // 멤버십 양방향 알림(PeerJoined)은 "모두가 모두를 보는" Party 전용.
            // 교실 모드(Classroom)는 조인자끼리 안 보이므로 호출하지 않는다 — 호스트만 목록을 가진다.
            if (mode == TransportMode.Party) {
                announcePeerJoined(newlyJoinedEndpoint = endpointId, newlyJoinedHello = finishedHello)
            }
            // 호스트가 이미 Draw 진입한 상태에서 새 조인자 합류 ("방 열기" 케이스) — 그 조인자
            // 만 PartyStart 못 받아 대기 화면 멈춤. unicast 로 즉시 진입시킨다. (스타 공통)
            if (partyStarted) {
                scope.launch {
                    runCatching { transport.sendTo(endpointId, Frame.PartyStart) }
                }
            }
        }
    }

    // 호스트가 새 조인자의 핸드셰이크 완료 시 호출. 양방향 멤버십 동기화:
    //   1) 새 조인자 (endpointId) 에게 기존 다른 조인자들의 PeerJoined 송신
    //   2) 기존 다른 조인자들에게 새 조인자의 PeerJoined 송신
    private fun announcePeerJoined(newlyJoinedEndpoint: String, newlyJoinedHello: Frame.Hello) {
        val others = handshakes.entries.mapNotNull { (otherId, otherH) ->
            if (otherId == newlyJoinedEndpoint) return@mapNotNull null
            val otherHello = otherH.remoteHello ?: return@mapNotNull null
            otherId to otherHello
        }
        scope.launch {
            others.forEach { (otherId, otherHello) ->
                // 1) 새 조인자에게 기존 피어 알림
                runCatching {
                    transport.sendTo(
                        newlyJoinedEndpoint,
                        Frame.PeerJoined(peerId = otherHello.peerId, nick = otherHello.nick),
                    )
                }
                // 2) 기존 피어에게 새 조인자 알림
                runCatching {
                    transport.sendTo(
                        otherId,
                        Frame.PeerJoined(
                            peerId = newlyJoinedHello.peerId,
                            nick = newlyJoinedHello.nick,
                        ),
                    )
                }
            }
        }
    }

    // Phase 4-G: target 이 자기가 아니라 다른 peerId 면 호스트가 relay 해야 한다.
    // - targetPeerId 빈 문자열 → broadcast 의미 (Duo 호환) → relay 아님
    // - target == 자기 peerId → 자기 처리 → relay 아님
    // - 그 외 → relay (단, 호스트만 가능. 조인자는 무시)
    private fun shouldRelay(targetPeerId: String): Boolean {
        if (targetPeerId.isEmpty() || targetPeerId == peerId) return false
        return mode == TransportMode.Party && transport.localRole == Role.Host
    }

    // 모임 모드 호스트가 target peerId 로 송신할 때 매칭되는 endpointId 찾기.
    // handshakes 안에서 remoteHello.peerId == target 인 항목 검색.
    private fun endpointForPeerId(targetPeerId: String): String? {
        return handshakes.entries.firstOrNull { (_, h) ->
            h.remoteHello?.peerId == targetPeerId
        }?.key
    }

    // pendingRelays 의 항목 — FILE 도착 시 호출. 각 target endpoint 별로 sendFileTo →
    // 새 payloadId 받고, 보관해둔 frame (Snapshot/PhotoMeta) 의 payloadId 를 갱신해 sendTo.
    // multi-target (모임 모드 사진 broadcast) 와 single-target (동기화 응답) 양쪽 케이스 모두.
    private fun forwardFile(relay: PendingRelay, uri: Uri) {
        scope.launch {
            runCatching {
                relay.targetEndpointIds.forEach { target ->
                    val newPayloadId = transport.sendFileTo(target, uri)
                    val updated: Frame = when (val f = relay.frame) {
                        is Frame.Snapshot -> f.copy(strokesPayloadId = newPayloadId)
                        is Frame.PhotoMeta -> f.copy(payloadId = newPayloadId)
                        else -> f
                    }
                    transport.sendTo(target, updated)
                }
            }
        }
    }

    // 호스트가 인바운드 Frame.Event 를 source 제외 다른 조인자에게 재송신. P2P_STAR 토폴로지에서
    // 조인자끼리 직접 안 보이니 이걸로 다대다 동기화.
    private fun relayIfHost(sourceEndpointId: String, frame: Frame) {
        if (mode != TransportMode.Party) return
        if (transport.localRole != Role.Host) return
        val targets = broadcastRelayTargets(sourceEndpointId)
        if (targets.isEmpty()) return
        scope.launch {
            targets.forEach { targetEndpointId ->
                runCatching { transport.sendTo(targetEndpointId, frame) }
            }
        }
    }

    // 호스트의 connectedPeers 중 source 제외한 endpoint 목록.
    private fun broadcastRelayTargets(sourceEndpointId: String): List<String> {
        if (mode != TransportMode.Party || transport.localRole != Role.Host) return emptyList()
        return transport.connectedPeers.value
            .map { it.endpointId }
            .filter { it != sourceEndpointId }
    }

    private fun resetHandshake() {
        handshakes.clear()
        indirectPeers.clear()
        partyStarted = false
        // 진행 중이던 FILE/메타 매칭 상태를 비운다. 안 비우면 끊겼다 재연결한 뒤 "가져오기"
        // 응답(Snapshot/PhotoMeta + FILE)이 직전 세션의 잔여 pending 과 엉켜 적용이 누락될 수 있다.
        pendingPhotoMeta.clear()
        pendingPhotoMetaSender.clear()
        pendingSnapshotMeta.clear()
        pendingSnapshotMetaSender.clear()
        pendingFiles.clear()
        pendingRelays.clear()
        publishRemotePeers()
    }

    companion object {
        private const val PREFS_NAME = "session_prefs"
        private const val KEY_NICK = "nick"
        private const val KEY_PEER_ID = "peer_id"

        // Phase 4-D: 모드별 별도 싱글톤. 인스턴스들은 prefs (peerId/nick) 만 공유하고
        // transport 와 핸드셰이크 상태는 완전 격리. 사용자 요구 "1:1 은 1:1 만, 1:N 은 1:N 만".
        // 교실 모드(Classroom)도 모임 모드(Party)와 별개 인스턴스 — 세션 상태가 섞이지 않는다.
        @Volatile private var duoInstance: SessionManager? = null
        @Volatile private var partyInstance: SessionManager? = null
        @Volatile private var classroomInstance: SessionManager? = null

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
            TransportMode.Classroom -> classroomInstance ?: synchronized(this) {
                classroomInstance ?: build(context, mode).also { classroomInstance = it }
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
