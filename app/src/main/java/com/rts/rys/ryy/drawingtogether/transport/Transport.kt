package com.rts.rys.ryy.drawingtogether.transport

import android.net.Uri
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

// 인바운드 FILE 페이로드 1건. payloadId 로 Frame.PhotoMeta 와 매칭.
data class IncomingFile(val payloadId: Long, val uri: Uri)

enum class FileTransferDirection { Outgoing, Incoming }
enum class FileTransferStatus { InProgress, Success, Failure }

// FILE 페이로드 송수신 진행 상황. Nearby 의 PayloadTransferUpdate 를 우리 도메인 타입으로 정규화.
// fraction = bytesTransferred / totalBytes (totalBytes 0 이면 0f 로 보호).
data class FileTransferEvent(
    val payloadId: Long,
    val direction: FileTransferDirection,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val status: FileTransferStatus,
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f
                else (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

// transport 추상화. Nearby 외 다른 구현(예: 페이크/Wi-Fi Direct)을 끼울 여지를 둔다.
// doc/nearby-connections.md §8.
interface Transport {
    val state: StateFlow<TransportState>
    val discovered: StateFlow<List<DiscoveredPeer>>
    val pending: StateFlow<PendingConnection?>
    val incoming: SharedFlow<Frame>
    val incomingFiles: SharedFlow<IncomingFile>
    val fileTransfers: SharedFlow<FileTransferEvent>

    fun setLocalNick(nick: String)

    suspend fun startAdvertising()
    suspend fun startDiscovery()
    suspend fun requestConnection(endpointId: String)
    suspend fun acceptPending()
    suspend fun rejectPending()
    suspend fun send(frame: Frame)

    // 사진 파일을 BYTES 가 아닌 FILE 페이로드로 송신. 반환값은 Nearby payload id —
    // 별도로 Frame.PhotoMeta 를 송신해 수신측이 매칭할 수 있도록 한다.
    suspend fun sendFile(uri: Uri): Long

    fun stop()
}
