package com.rts.rys.ryy.drawingtogether.session

import android.content.Context
import android.content.SharedPreferences
import com.rts.rys.ryy.drawingtogether.transport.Frame
import com.rts.rys.ryy.drawingtogether.transport.PROTO_VERSION
import com.rts.rys.ryy.drawingtogether.transport.Transport
import com.rts.rys.ryy.drawingtogether.transport.TransportState
import com.rts.rys.ryy.drawingtogether.transport.nearby.NearbyTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
