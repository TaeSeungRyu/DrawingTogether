package com.rts.rys.ryy.drawingtogether.transport

import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 와이어 메시지. doc/protocol.md §3 참고.
// Phase 3-A 에서 Event 추가. SNAPSHOT/PHOTO 는 Phase 3-B / 3-C.
@Serializable
sealed class Frame {

    @Serializable
    @SerialName("hello")
    data class Hello(
        val proto: Int,
        val peerId: String,
        val nick: String,
    ) : Frame()

    @Serializable
    @SerialName("hello_ack")
    data class HelloAck(val peerId: String) : Frame()

    @Serializable
    @SerialName("event")
    data class Event(val e: DrawingEvent) : Frame()

    // "동기화" 버튼 — 내 캔버스를 상대 캔버스로 덮어쓰기 위해 상대에게 현재 상태 요청.
    @Serializable
    @SerialName("snapshot_req")
    data object SnapshotReq : Frame()

    // SnapshotReq 응답. strokes 는 별도 FILE 페이로드로 송신 (BYTES 32KB 한도 회피).
    // strokesPayloadId 로 FILE 매칭 — 사진의 PhotoMeta 와 동일 패턴.
    // hasPhoto 가 true 면 별도로 Frame.PhotoMeta + FILE 가 따라오고, false 면 Frame.PhotoRemove.
    @Serializable
    @SerialName("snapshot")
    data class Snapshot(
        val strokesPayloadId: Long,
        val hasPhoto: Boolean,
    ) : Frame()

    // 사진 파일이 곧 도착함을 알리는 메타. payloadId 로 FILE 페이로드와 매칭.
    @Serializable
    @SerialName("photo_meta")
    data class PhotoMeta(
        val payloadId: Long,
        val byteSize: Long,
        val widthPx: Int,
        val heightPx: Int,
        val mime: String,
    ) : Frame()

    // 사진 배경 제거 요청 — 양쪽에 적용.
    @Serializable
    @SerialName("photo_remove")
    data object PhotoRemove : Frame()

    // "저장 시 배경 합치기" 토글 동기화.
    @Serializable
    @SerialName("merge_bg")
    data class MergeBackground(val enabled: Boolean) : Frame()

    @Serializable
    @SerialName("ping")
    data class Ping(val ts: Long) : Frame()

    @Serializable
    @SerialName("pong")
    data class Pong(val ts: Long) : Frame()

    @Serializable
    @SerialName("bye")
    data class Bye(val reason: String) : Frame()
}

const val PROTO_VERSION: Int = 1
