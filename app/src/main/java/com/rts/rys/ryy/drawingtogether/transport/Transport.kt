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

// 연결 완료된 원격 단말. 1:1 함께 모드에선 최대 1명, 1:N 모임 모드에선 호스트 기준 최대 3명.
data class ConnectedPeer(
    val endpointId: String,
    val nick: String,
)

// 인바운드 BYTES 프레임 + source endpointId. 모임 모드에서 발신자 식별/relay 라우팅에 사용.
// 1:1 에서는 endpointId 단일이라 무시해도 무방.
data class InboundFrame(
    val endpointId: String,
    val frame: Frame,
)

sealed class TransportState {
    data object Idle : TransportState()
    data object Advertising : TransportState()
    data object Discovering : TransportState()
    data class Connecting(val endpointId: String) : TransportState()
    // 1명 이상 연결된 상태. 실제 피어 목록은 connectedPeers 로 노출.
    // 1:1 / 1:N 양쪽을 동일하게 처리하기 위해 endpoint 정보는 빼고 단순화.
    data object Connected : TransportState()
    data class Failed(val reason: String) : TransportState()
}

enum class Role { Host, Joiner }

// 인바운드 FILE 페이로드 1건. payloadId 로 Frame.PhotoMeta / Frame.Snapshot 와 매칭.
// endpointId 는 모임 모드에서 발신자 식별용 — 1:1 에서는 단일이라 무시.
data class IncomingFile(
    val endpointId: String,
    val payloadId: Long,
    val uri: Uri,
)

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
//
// 다중 endpoint 지원 (Phase 4-A):
// - connectedPeers 로 현재 연결된 피어 명단 노출
// - send/sendFile 은 모든 연결된 피어에게 broadcast (1:1 에선 자동으로 1명)
// - sendTo/sendFileTo 는 특정 endpointId 에만 보내는 unicast
interface Transport {
    val state: StateFlow<TransportState>
    val discovered: StateFlow<List<DiscoveredPeer>>
    val pending: StateFlow<PendingConnection?>
    val connectedPeers: StateFlow<List<ConnectedPeer>>
    val incoming: SharedFlow<InboundFrame>
    val incomingFiles: SharedFlow<IncomingFile>
    val fileTransfers: SharedFlow<FileTransferEvent>

    // 광고/검색을 시작한 단말의 역할. startAdvertising → Host, startDiscovery → Joiner.
    // stop() 시 null 로 reset. 모임 모드에서 호스트 전용 UI (예: "방 열기" 버튼) 분기에 사용.
    val localRole: Role?

    fun setLocalNick(nick: String)

    suspend fun startAdvertising()
    suspend fun startDiscovery()
    // 광고만 중단. 기존 연결과 discovery 는 유지. 1:N 모임 모드 호스트가
    // "그리기 시작" 시점에 더 이상 새 조인자를 안 받기 위해 호출.
    fun stopAdvertising()
    suspend fun requestConnection(endpointId: String)
    suspend fun acceptPending()
    suspend fun rejectPending()

    // 연결된 모든 피어에게 송신. 1:1 함께 모드에선 자동으로 1명에게만 간다.
    suspend fun send(frame: Frame)

    // 특정 endpointId 에게만 송신. 모임 모드 호스트 relay / 동기화 응답 등 unicast 케이스용.
    suspend fun sendTo(endpointId: String, frame: Frame)

    // 사진 파일을 BYTES 가 아닌 FILE 페이로드로 모든 피어에게 송신. 반환값은 Nearby payload id —
    // 별도로 Frame.PhotoMeta 를 송신해 수신측이 매칭할 수 있도록 한다.
    suspend fun sendFile(uri: Uri): Long

    // 특정 endpointId 에게만 FILE 송신. 동기화 응답 (snapshot strokes / 사진) 에 사용.
    suspend fun sendFileTo(endpointId: String, uri: Uri): Long

    fun stop()
}
