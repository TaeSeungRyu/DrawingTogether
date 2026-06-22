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
import com.rts.rys.ryy.drawingtogether.transport.DiscoveredPeer
import com.rts.rys.ryy.drawingtogether.transport.Frame
import com.rts.rys.ryy.drawingtogether.transport.IncomingFile
import com.rts.rys.ryy.drawingtogether.transport.PendingConnection
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

// Nearby Connections 기반 Transport 구현. P2P_POINT_TO_POINT.
// doc/nearby-connections.md §4 흐름 그대로.
class NearbyTransport(context: Context) : Transport {

    private val appContext = context.applicationContext
    private val client: ConnectionsClient = Nearby.getConnectionsClient(appContext)

    private val _state = MutableStateFlow<TransportState>(TransportState.Idle)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    override val discovered: StateFlow<List<DiscoveredPeer>> = _discovered.asStateFlow()

    private val _pending = MutableStateFlow<PendingConnection?>(null)
    override val pending: StateFlow<PendingConnection?> = _pending.asStateFlow()

    private val _incoming = MutableSharedFlow<Frame>(extraBufferCapacity = 64)
    override val incoming: SharedFlow<Frame> = _incoming.asSharedFlow()

    private val _incomingFiles = MutableSharedFlow<IncomingFile>(extraBufferCapacity = 8)
    override val incomingFiles: SharedFlow<IncomingFile> = _incomingFiles.asSharedFlow()

    private var localNick: String = "User"
    private var connectedEndpoint: String? = null
    private val nickByEndpoint = mutableMapOf<String, String>()

    override fun setLocalNick(nick: String) {
        localNick = nick.ifBlank { "User" }
    }

    override suspend fun startAdvertising() {
        stopInternal(updateState = false)
        _state.value = TransportState.Advertising
        val opts = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        val deferred = CompletableDeferred<Unit>()
        client.startAdvertising(localNick, SERVICE_ID, lifecycleCallback, opts)
            .addOnSuccessListener { deferred.complete(Unit) }
            .addOnFailureListener {
                _state.value = TransportState.Failed("advertise: ${it.message}")
                deferred.complete(Unit)
            }
        deferred.await()
    }

    override suspend fun startDiscovery() {
        stopInternal(updateState = false)
        _state.value = TransportState.Discovering
        val opts = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        val deferred = CompletableDeferred<Unit>()
        client.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, opts)
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
        val p = _pending.value ?: return
        _pending.value = null
        client.acceptConnection(p.endpointId, payloadCallback)
    }

    override suspend fun rejectPending() {
        val p = _pending.value ?: return
        _pending.value = null
        client.rejectConnection(p.endpointId)
    }

    override suspend fun send(frame: Frame) {
        val target = connectedEndpoint ?: return
        val bytes = FrameCodec.encode(frame)
        client.sendPayload(target, Payload.fromBytes(bytes))
    }

    override suspend fun sendFile(uri: Uri): Long {
        val target = connectedEndpoint ?: error("not connected")
        val pfd = withContext(Dispatchers.IO) {
            appContext.contentResolver.openFileDescriptor(uri, "r")
        } ?: error("cannot open uri: $uri")
        val payload = Payload.fromFile(pfd)
        client.sendPayload(target, payload)
        return payload.id
    }

    override fun stop() {
        stopInternal(updateState = true)
    }

    private fun stopInternal(updateState: Boolean) {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        connectedEndpoint = null
        nickByEndpoint.clear()
        _discovered.value = emptyList()
        _pending.value = null
        if (updateState) _state.value = TransportState.Idle
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            nickByEndpoint[endpointId] = info.endpointName
            _pending.value = PendingConnection(
                endpointId = endpointId,
                remoteNick = info.endpointName,
                token = info.authenticationDigits,
            )
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connectedEndpoint = endpointId
                    // 광고/검색은 연결 성립 시 중단 — 같은 단말이 또 발견되지 않도록.
                    client.stopAdvertising()
                    client.stopDiscovery()
                    _discovered.value = emptyList()
                    _state.value = TransportState.Connected(
                        endpointId = endpointId,
                        remoteNick = nickByEndpoint[endpointId] ?: "peer",
                    )
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    _state.value = TransportState.Failed("rejected")
                }
                else -> {
                    _state.value = TransportState.Failed("conn: ${result.status.statusCode}")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (connectedEndpoint == endpointId) {
                connectedEndpoint = null
                _state.value = TransportState.Idle
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            val peer = DiscoveredPeer(endpointId = endpointId, nick = info.endpointName)
            _discovered.value = (_discovered.value.filter { it.endpointId != endpointId } + peer)
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
                    _incoming.tryEmit(frame)
                }
                Payload.Type.FILE -> {
                    val uri = payload.asFile()?.asUri() ?: return
                    _incomingFiles.tryEmit(IncomingFile(payloadId = payload.id, uri = uri))
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // 진행률 UI 는 Phase 3-B 추가 작업. 현재는 무시.
        }
    }

    companion object {
        const val SERVICE_ID: String = "com.rts.rys.ryy.drawingtogether"
        private val STRATEGY: Strategy = Strategy.P2P_POINT_TO_POINT
    }
}
