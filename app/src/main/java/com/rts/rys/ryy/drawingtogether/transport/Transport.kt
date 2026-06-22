package com.rts.rys.ryy.drawingtogether.transport

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

// 발견된 (아직 연결되지 않은) 원격 단말.
data class DiscoveredPeer(
    val endpointId: String,
    val nick: String,
)

// 연결 수락 단계의 인증 토큰 다이얼로그용 정보.
// Nearby의 onConnectionInitiated가 주는 authenticationDigits.
data class PendingConnection(
    val endpointId: String,
    val remoteNick: String,
    val token: String,
)

sealed class TransportState {
    data object Idle : TransportState()
    data object Advertising : TransportState()
    data object Discovering : TransportState()
    data class Connecting(val endpointId: String) : TransportState()
    data class Connected(val endpointId: String, val remoteNick: String) : TransportState()
    data class Failed(val reason: String) : TransportState()
}

enum class Role { Host, Joiner }

// transport 추상화. Nearby 외 다른 구현(예: 페이크/Wi-Fi Direct)을 끼울 여지를 둔다.
// doc/nearby-connections.md §8.
interface Transport {
    val state: StateFlow<TransportState>
    val discovered: StateFlow<List<DiscoveredPeer>>
    val pending: StateFlow<PendingConnection?>
    val incoming: SharedFlow<Frame>

    fun setLocalNick(nick: String)

    suspend fun startAdvertising()
    suspend fun startDiscovery()
    suspend fun requestConnection(endpointId: String)
    suspend fun acceptPending()
    suspend fun rejectPending()
    suspend fun send(frame: Frame)
    fun stop()
}
