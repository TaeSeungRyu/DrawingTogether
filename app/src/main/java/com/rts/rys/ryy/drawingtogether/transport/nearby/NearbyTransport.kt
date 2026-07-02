package com.rts.rys.ryy.drawingtogether.transport.nearby

import android.content.Context
import android.net.Uri
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.rts.rys.ryy.drawingtogether.transport.ConnectedPeer
import com.rts.rys.ryy.drawingtogether.transport.DiscoveredPeer
import com.rts.rys.ryy.drawingtogether.transport.FileTransferDirection
import com.rts.rys.ryy.drawingtogether.transport.FileTransferEvent
import com.rts.rys.ryy.drawingtogether.transport.FileTransferStatus
import com.rts.rys.ryy.drawingtogether.transport.Frame
import com.rts.rys.ryy.drawingtogether.transport.InboundFrame
import com.rts.rys.ryy.drawingtogether.transport.IncomingFile
import com.rts.rys.ryy.drawingtogether.transport.PendingConnection
import com.rts.rys.ryy.drawingtogether.transport.Role
import com.rts.rys.ryy.drawingtogether.transport.Transport
import com.rts.rys.ryy.drawingtogether.transport.TransportState
import com.rts.rys.ryy.drawingtogether.transport.codec.FrameCodec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// Phase 4-A: 각 모드를 SERVICE_ID + Strategy 로 격리. 같은 모드끼리만 발견된다.
// Duo       = 1:1 함께 모드 (P2P_POINT_TO_POINT)
// Party     = 1:N 모임 모드 (P2P_STAR, 호스트 + 최대 3 조인자, mesh 가시성)
// Classroom = 1:N 교실 모드 (P2P_STAR, 호스트 중심 — 조인자끼리 안 보임). serviceId 가 달라
//             교실 모드 기기끼리만 발견·연결된다(모임/함께 기기는 미발견). doc/classroom-mode.md.
// 호스트가 받을 수 있는 최대 조인자 수. maxJoiners 로 모드별 지정.
// 주의: Nearby P2P_STAR 는 소규모 그룹용 — 대역폭·안정성상 수십 명 동시 연결은 비현실적.
// 교실 모드도 "현실적인 수"(host + 9 = 10명)로 잡는다. 더 키우면 연결 실패/끊김이 잦아진다.
enum class TransportMode(
    val serviceId: String,
    val strategy: Strategy,
    val maxJoiners: Int,
) {
    Duo(
        serviceId = "com.rts.rys.ryy.drawingtogether.duo",
        strategy = Strategy.P2P_POINT_TO_POINT,
        maxJoiners = 1,
    ),
    Party(
        serviceId = "com.rts.rys.ryy.drawingtogether.party",
        strategy = Strategy.P2P_STAR,
        maxJoiners = 3,
    ),
    Classroom(
        serviceId = "com.rts.rys.ryy.drawingtogether.classroom",
        strategy = Strategy.P2P_STAR,
        maxJoiners = 9,
    );

    // 호스트–조인자(스타) 모드인지. 호스트 인원 제한·광고 유지·PartyStart 지각입장 등
    // 스타 공통 메커니즘은 Party/Classroom 둘 다에 적용된다.
    val isStar: Boolean get() = strategy == Strategy.P2P_STAR
}

// Nearby Connections 기반 Transport 구현.
// doc/nearby-connections.md §4 흐름 그대로.
// mode 디폴트 = Duo 라 기존 함께 모드 호출부 (SessionManager) 는 변경 없이 동작.
class NearbyTransport(
    context: Context,
    private val mode: TransportMode = TransportMode.Duo,
) : Transport {

    private val appContext = context.applicationContext
    private val client: ConnectionsClient = Nearby.getConnectionsClient(appContext)

    private val _state = MutableStateFlow<TransportState>(TransportState.Idle)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    override val discovered: StateFlow<List<DiscoveredPeer>> = _discovered.asStateFlow()

    private val _pending = MutableStateFlow<PendingConnection?>(null)
    override val pending: StateFlow<PendingConnection?> = _pending.asStateFlow()

    // 동시 연결 요청 레이스 방지:
    // - #3: pending 을 큐로 관리 — 거의 동시에 붙은 조인자들이 서로 덮어써 유실되지 않게. _pending 은 head 를 노출.
    // - #2: "수락했지만 STATUS_OK 아직" 인 endpoint 를 예약 집합에 담아 정원(연결됨+예약) 초과를 차단.
    // 콜백(GMS 스레드) 과 accept/reject(Main) 이 함께 접근하므로 pendingLock 으로 보호.
    private val pendingLock = Any()
    private val pendingQueue = ArrayDeque<PendingConnection>()
    private val acceptedEndpoints = mutableSetOf<String>()

    private val _connectedPeers = MutableStateFlow<List<ConnectedPeer>>(emptyList())
    override val connectedPeers: StateFlow<List<ConnectedPeer>> = _connectedPeers.asStateFlow()

    private val _incoming = MutableSharedFlow<InboundFrame>(extraBufferCapacity = 64)
    override val incoming: SharedFlow<InboundFrame> = _incoming.asSharedFlow()

    private val _incomingFiles = MutableSharedFlow<IncomingFile>(extraBufferCapacity = 8)
    override val incomingFiles: SharedFlow<IncomingFile> = _incomingFiles.asSharedFlow()

    private val _fileTransfers = MutableSharedFlow<FileTransferEvent>(extraBufferCapacity = 64)
    override val fileTransfers: SharedFlow<FileTransferEvent> = _fileTransfers.asSharedFlow()

    // 우리가 보낸 / 받은 FILE payload id 추적 — 진행률 업데이트 방향 분류용.
    // outgoing 은 Main 코루틴(sendFile add)과 GMS 콜백 스레드(onPayloadTransferUpdate read/remove)가
    // 함께 접근하므로 동시성 안전 컬렉션(#10). incoming 계열은 단일 PayloadCallback executor 만 접근.
    private val outgoingFilePayloads: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val incomingFilePayloads = mutableSetOf<Long>()
    // 인바운드 FILE 의 (endpointId, uri) 보관. onPayloadReceived 시점에 등록하고
    // onPayloadTransferUpdate SUCCESS 시점에 emit — 그 시점에야 파일이 완전 채워진다.
    // 너무 일찍 emit 하면 PhotoLoader 가 부분 파일을 디코드해 사진이 절반 짤린다.
    private val pendingIncomingFiles = mutableMapOf<Long, Pair<String, Uri>>()

    private var localNick: String = "User"
    // GMS 콜백 스레드(onConnectionInitiated write / onConnectionResult read)와 Main(stop clear)이
    // 함께 접근 → 동시성 안전 맵(#10).
    private val nickByEndpoint = ConcurrentHashMap<String, String>()
    // Phase 4-D: 호스트/조인자 구분. Party 호스트는 첫 연결 후에도 광고 유지 (최대 3명 더 수용).
    // Transport 인터페이스로 노출 — UI 에서 호스트 전용 분기(예: "방 열기" 버튼) 에 사용.
    override var localRole: Role? = null
        private set

    override fun setLocalNick(nick: String) {
        localNick = nick.ifBlank { "User" }
    }

    override suspend fun startAdvertising() {
        // 광고/검색만 정리 — 기존 연결 (connectedPeers) 과 localRole 유지. "방 열기" 처럼
        // Connected 상태에서 광고를 재개해야 하는 케이스에서 기존 조인자가 끊기지 않아야 한다.
        resetAdvertiseDiscovery()
        localRole = Role.Host
        _state.value = TransportState.Advertising
        val opts = AdvertisingOptions.Builder().setStrategy(mode.strategy).build()
        val deferred = CompletableDeferred<Unit>()
        client.startAdvertising(localNick, mode.serviceId, lifecycleCallback, opts)
            .addOnSuccessListener { deferred.complete(Unit) }
            .addOnFailureListener {
                _state.value = TransportState.Failed("advertise: ${it.message}")
                deferred.complete(Unit)
            }
        deferred.await()
    }

    override suspend fun startDiscovery() {
        resetAdvertiseDiscovery()
        localRole = Role.Joiner
        _state.value = TransportState.Discovering
        val opts = DiscoveryOptions.Builder().setStrategy(mode.strategy).build()
        val deferred = CompletableDeferred<Unit>()
        client.startDiscovery(mode.serviceId, endpointDiscoveryCallback, opts)
            .addOnSuccessListener { deferred.complete(Unit) }
            .addOnFailureListener {
                _state.value = TransportState.Failed("discover: ${it.message}")
                deferred.complete(Unit)
            }
        deferred.await()
    }

    override suspend fun requestConnection(endpointId: String) {
        _state.value = TransportState.Connecting(endpointId)
        val deferred = CompletableDeferred<Unit>()
        client.requestConnection(localNick, endpointId, lifecycleCallback)
            .addOnSuccessListener { deferred.complete(Unit) }
            .addOnFailureListener {
                _state.value = TransportState.Failed("request: ${it.message}")
                deferred.complete(Unit)
            }
        deferred.await()
    }

    override suspend fun acceptPending() {
        val p = synchronized(pendingLock) { pendingQueue.firstOrNull() } ?: return
        // 수락 직전 재검사(#2) — 큐에 있는 동안 다른 조인자가 먼저 채워 정원이 찼으면 accept 대신 reject.
        if (isStarHostFull()) {
            synchronized(pendingLock) { pendingQueue.removeFirstOrNull() }
            client.rejectConnection(p.endpointId)
            publishHead()
            return
        }
        synchronized(pendingLock) {
            pendingQueue.removeFirstOrNull()
            acceptedEndpoints.add(p.endpointId)
        }
        client.acceptConnection(p.endpointId, payloadCallback)
        publishHead()
    }

    override suspend fun rejectPending() {
        val p = synchronized(pendingLock) { pendingQueue.removeFirstOrNull() } ?: return
        client.rejectConnection(p.endpointId)
        publishHead()
    }

    // 스타 호스트 정원(연결됨 + 수락대기 예약) 이 찼는지. Duo/조인자는 항상 false.
    private fun isStarHostFull(): Boolean {
        if (!(mode.isStar && localRole == Role.Host)) return false
        val reserved = synchronized(pendingLock) { acceptedEndpoints.size }
        return _connectedPeers.value.size + reserved >= mode.maxJoiners
    }

    // 큐 head 를 _pending 으로 노출 — UI 는 한 번에 한 다이얼로그만 보고 순차 처리.
    private fun publishHead() {
        _pending.value = synchronized(pendingLock) { pendingQueue.firstOrNull() }
    }

    override suspend fun send(frame: Frame) {
        val targets = _connectedPeers.value
        if (targets.isEmpty()) return
        val bytes = FrameCodec.encode(frame)
        targets.forEach { peer ->
            client.sendPayload(peer.endpointId, Payload.fromBytes(bytes))
        }
    }

    override suspend fun sendTo(endpointId: String, frame: Frame) {
        if (_connectedPeers.value.none { it.endpointId == endpointId }) return
        val bytes = FrameCodec.encode(frame)
        client.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    override suspend fun sendFile(uri: Uri): Long {
        val targets = _connectedPeers.value
        if (targets.isEmpty()) error("not connected")
        val pfd = withContext(Dispatchers.IO) {
            appContext.contentResolver.openFileDescriptor(uri, "r")
        } ?: error("cannot open uri: $uri")
        val payload = Payload.fromFile(pfd)
        outgoingFilePayloads.add(payload.id)
        // Nearby 는 한 Payload 객체를 여러 endpoint 에 sendPayload 해도 안전 — 동일 payload.id 재사용.
        targets.forEach { peer ->
            client.sendPayload(peer.endpointId, payload)
        }
        return payload.id
    }

    override suspend fun sendFileTo(endpointId: String, uri: Uri): Long {
        if (_connectedPeers.value.none { it.endpointId == endpointId }) error("not connected: $endpointId")
        val pfd = withContext(Dispatchers.IO) {
            appContext.contentResolver.openFileDescriptor(uri, "r")
        } ?: error("cannot open uri: $uri")
        val payload = Payload.fromFile(pfd)
        outgoingFilePayloads.add(payload.id)
        client.sendPayload(endpointId, payload)
        return payload.id
    }

    override fun stopAdvertising() {
        // 광고만 중단. 연결/검색 상태와 _state 는 그대로 — 호출자(예: PartyPairingScreen
        // "그리기 시작" 버튼)가 후속 흐름을 결정.
        client.stopAdvertising()
    }

    override fun stop() {
        resetAdvertiseDiscovery()
        client.stopAllEndpoints()
        localRole = null
        _connectedPeers.value = emptyList()
        nickByEndpoint.clear()
        synchronized(pendingLock) { acceptedEndpoints.clear() }
        _state.value = TransportState.Idle
    }

    // 광고/검색만 멈추고 발견 캐시·pending 만 정리. 기존 연결과 localRole 은 그대로 — 광고를
    // 재시작하는 케이스 ("방 열기") 에서 기존 조인자가 끊기지 않아야 한다.
    // 완전 종료는 stop() 가 담당 (stopAllEndpoints + connectedPeers 비우기 + localRole reset).
    private fun resetAdvertiseDiscovery() {
        client.stopAdvertising()
        client.stopDiscovery()
        _discovered.value = emptyList()
        synchronized(pendingLock) { pendingQueue.clear() }
        _pending.value = null
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // 스타(Party/Classroom) 호스트가 정원(연결됨 + 수락대기 예약)을 채웠으면 자동 reject —
            // 다이얼로그도 안 띄움. 예약까지 세므로 동시 초기화 레이스에서도 초과되지 않음(#2).
            // Duo / 조인자는 영향 없음.
            if (isStarHostFull()) {
                client.rejectConnection(endpointId)
                return
            }
            nickByEndpoint[endpointId] = info.endpointName
            val pc = PendingConnection(
                endpointId = endpointId,
                remoteNick = info.endpointName,
                token = info.authenticationDigits,
            )
            // 큐로 관리(#3) — 거의 동시에 붙은 두 번째 조인자가 첫 번째를 덮어써 유실되지 않게.
            synchronized(pendingLock) {
                if (pendingQueue.none { it.endpointId == endpointId }) pendingQueue.addLast(pc)
            }
            publishHead()
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            // 예약 해제 — 수락 후 결과 도착(성공/거부/실패 모두).
            synchronized(pendingLock) { acceptedEndpoints.remove(endpointId) }
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    // 최종 방어(#2) — 예약을 넘어선 잔여 레이스로 정원을 넘겼으면 이 연결을 끊는다(스타 호스트).
                    if (mode.isStar && localRole == Role.Host &&
                        _connectedPeers.value.size >= mode.maxJoiners
                    ) {
                        disconnectFromEndpoint(endpointId)
                        return
                    }
                    val nick = nickByEndpoint[endpointId] ?: "peer"
                    _connectedPeers.value = _connectedPeers.value
                        .filter { it.endpointId != endpointId } +
                        ConnectedPeer(endpointId = endpointId, nick = nick)
                    // 스타(Party/Classroom) 호스트는 광고 유지 — 추가 조인자 (최대 3명) 수용.
                    // Duo 호스트 / 모든 조인자 / 스타 조인자는 첫 연결 성립 시 광고·검색 중단.
                    val keepAdvertising = mode.isStar && localRole == Role.Host
                    if (!keepAdvertising) client.stopAdvertising()
                    client.stopDiscovery()
                    _discovered.value = emptyList()
                    _state.value = TransportState.Connected
                }
                else -> {
                    // 스타 호스트는 특정 조인자의 거부/실패(정원 초과 reject 포함)로 세션 전체를 실패시키지
                    // 않는다 — 방을 유지하고 다른 조인자를 계속 받는다. Duo/조인자만 Failed 로 표면화.
                    val reason =
                        if (result.status.statusCode == ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED) {
                            "rejected"
                        } else {
                            "conn: ${result.status.statusCode}"
                        }
                    if (!(mode.isStar && localRole == Role.Host)) {
                        _state.value = TransportState.Failed(reason)
                    }
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            handleEndpointGone(endpointId)
        }
    }

    // endpoint 소실 정리 — Nearby onDisconnected 콜백과 하트비트 강제 해제가 공유.
    // connectedPeers 에서 제거, 마지막 하나였고 연결 상태였으면 Idle 로. localRole 은 건드리지 않음.
    private fun handleEndpointGone(endpointId: String) {
        _connectedPeers.value = _connectedPeers.value.filter { it.endpointId != endpointId }
        // 수락 대기 중이던 endpoint 가 STATUS_OK 전에 끊기면 예약도 해제(정원 카운트 누수 방지).
        synchronized(pendingLock) { acceptedEndpoints.remove(endpointId) }
        if (_connectedPeers.value.isEmpty() && _state.value is TransportState.Connected) {
            _state.value = TransportState.Idle
        }
    }

    override fun disconnectFromEndpoint(endpointId: String) {
        // Nearby 에 로컬 해제 요청 후, 콜백이 안 올 수 있으니 즉시 동일 정리(idempotent).
        runCatching { client.disconnectFromEndpoint(endpointId) }
        handleEndpointGone(endpointId)
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != mode.serviceId) return
            val peer = DiscoveredPeer(endpointId = endpointId, nick = info.endpointName)
            // 같은 endpointId 와 같은 nick 둘 다 제거하고 새 entry 만 남긴다.
            // 한 기기가 광고를 재시작하면 Nearby 가 새 endpointId 를 부여해서 같은 기기가
            // 두 카드로 보이는 현상이 있는데(onEndpointLost 가 늦게 옴), nick 기반 dedupe 로 해결.
            // 트레이드오프: 두 단말이 같은 닉네임을 쓰면 하나만 보임 — 1:1 쓰임새에서 수용 가능.
            _discovered.value = _discovered.value
                .filter { it.endpointId != endpointId && it.nick != info.endpointName } + peer
        }

        override fun onEndpointLost(endpointId: String) {
            _discovered.value = _discovered.value.filter { it.endpointId != endpointId }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val frame = FrameCodec.tryDecode(bytes) ?: return
                    _incoming.tryEmit(InboundFrame(endpointId = endpointId, frame = frame))
                }
                Payload.Type.FILE -> {
                    val uri = payload.asFile()?.asUri() ?: return
                    incomingFilePayloads.add(payload.id)
                    // Nearby 의 FILE 은 onPayloadReceived 시점엔 아직 채워지는 중. SUCCESS
                    // 까지 보관만 하고 emit 은 그 때.
                    pendingIncomingFiles[payload.id] = endpointId to uri
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val direction = when (update.payloadId) {
                in outgoingFilePayloads -> FileTransferDirection.Outgoing
                in incomingFilePayloads -> FileTransferDirection.Incoming
                else -> return  // BYTES 페이로드 업데이트나 아직 분류 안 된 건 무시
            }
            val status = when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> FileTransferStatus.InProgress
                PayloadTransferUpdate.Status.SUCCESS -> FileTransferStatus.Success
                else -> FileTransferStatus.Failure  // FAILURE, CANCELED 등 모두 실패로 통합
            }
            _fileTransfers.tryEmit(
                FileTransferEvent(
                    payloadId = update.payloadId,
                    direction = direction,
                    bytesTransferred = update.bytesTransferred,
                    totalBytes = update.totalBytes,
                    status = status,
                )
            )
            // 인바운드 FILE 이 완전 채워진 시점 → 그 때 emit. PhotoLoader 가 부분 파일을 읽지
            // 않게 한다.
            if (status == FileTransferStatus.Success &&
                direction == FileTransferDirection.Incoming
            ) {
                val pending = pendingIncomingFiles.remove(update.payloadId)
                if (pending != null) {
                    _incomingFiles.tryEmit(
                        IncomingFile(
                            endpointId = pending.first,
                            payloadId = update.payloadId,
                            uri = pending.second,
                        )
                    )
                }
            }
            // 종료 상태면 추적 set 에서 제거.
            if (status != FileTransferStatus.InProgress) {
                outgoingFilePayloads.remove(update.payloadId)
                incomingFilePayloads.remove(update.payloadId)
                if (status == FileTransferStatus.Failure) {
                    pendingIncomingFiles.remove(update.payloadId)
                }
            }
        }
    }
}
