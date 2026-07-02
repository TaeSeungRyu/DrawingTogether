package com.rts.rys.ryy.drawingtogether.transport

import com.rts.rys.ryy.drawingtogether.drawing.model.CanvasAspect
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
    // Phase 4-G: targetPeerId 가 비어있으면 broadcast (Duo 1:1 호환). 명시되면 호스트가
    // 그 peerId 의 조인자에게 relay (sendTo). 응답도 같은 라우팅.
    // forLiveView=true: 교실 조인자가 방장 라이브뷰(peerCanvases[host])를 채우려는 요청.
    // 응답은 target="" 로 와서 받는 쪽이 sender=host 로 라우팅(메인 아님). false(기본)는 "가져오기"
    // (응답을 자기 메인에 덮어쓰기).
    @Serializable
    @SerialName("snapshot_req")
    data class SnapshotReq(
        val targetPeerId: String = "",
        val requesterPeerId: String = "",
        val forLiveView: Boolean = false,
    ) : Frame()

    // SnapshotReq 응답. strokes 는 별도 FILE 페이로드로 송신 (BYTES 32KB 한도 회피).
    // strokesPayloadId 로 FILE 매칭 — 사진의 PhotoMeta 와 동일 패턴.
    // hasPhoto 가 true 면 별도로 Frame.PhotoMeta + FILE 가 따라오고, false 면 Frame.PhotoRemove.
    // Phase 4-G: targetPeerId 는 응답 받을 사람 (요청자) — 호스트가 그쪽으로 relay.
    @Serializable
    @SerialName("snapshot")
    data class Snapshot(
        val strokesPayloadId: Long,
        val hasPhoto: Boolean,
        val targetPeerId: String = "",
        // broadcast(targetPeerId="") 시 원발신자 peerId. 호스트 relay 후에도 보존돼 수신 측이
        // relay 해준 endpoint(호스트)가 아닌 진짜 발신자로 라우팅한다(#5). 비면 endpoint 폴백.
        val originPeerId: String = "",
    ) : Frame()

    // 사진 파일이 곧 도착함을 알리는 메타. payloadId 로 FILE 페이로드와 매칭.
    // Phase 4-G: 모임 모드 Snapshot 응답 동반 시 targetPeerId 박힘 → 호스트가 그쪽으로 relay.
    // Duo 모드는 빈 문자열 → broadcast.
    @Serializable
    @SerialName("photo_meta")
    data class PhotoMeta(
        val payloadId: Long,
        val byteSize: Long,
        val widthPx: Int,
        val heightPx: Int,
        val mime: String,
        val targetPeerId: String = "",
        // broadcast 시 원발신자 peerId — relay 후에도 보존해 발신자 라우팅(#5). 비면 endpoint 폴백.
        val originPeerId: String = "",
    ) : Frame()

    // 사진 배경 제거 요청 — 양쪽에 적용.
    // Phase 4-G: Snapshot 응답에서 hasPhoto=false 면 동반 송신. targetPeerId 박힘.
    @Serializable
    @SerialName("photo_remove")
    data class PhotoRemove(
        val targetPeerId: String = "",
        // broadcast 시 원발신자 peerId — relay 후에도 보존해 발신자 라우팅(#5). 비면 endpoint 폴백.
        val originPeerId: String = "",
    ) : Frame()

    // "저장 시 배경 합치기" 토글 동기화.
    @Serializable
    @SerialName("merge_bg")
    data class MergeBackground(val enabled: Boolean) : Frame()

    // 캔버스 비율 변경 동기화. 함께=공유 캔버스에 적용, 모임/교실=발신자 peerCanvases 에 적용
    // (받는 쪽이 senderPeerId 로 라우팅). 사진 있으면 사진 비율 우선이라 표시엔 영향 없음.
    @Serializable
    @SerialName("canvas_aspect")
    data class CanvasAspectFrame(
        val aspect: CanvasAspect,
        // 발신자 peerId — 호스트 relay 후에도 보존해 수신 측이 relay endpoint(호스트)가 아닌
        // 진짜 발신자의 미니뷰에 적용하게 한다(#4). 비면 endpoint 폴백(Duo/구버전 호환).
        val senderPeerId: String = "",
    ) : Frame()

    // 캔버스 배경색 변경 동기화. 비율(CanvasAspectFrame)과 동일 라우팅 — 함께=공유 캔버스,
    // 모임/교실=발신자 peerCanvases(미니뷰). 사진이 있으면 사진이 위에 깔려 표시엔 영향 없음.
    @Serializable
    @SerialName("bg_color")
    data class BackgroundColorFrame(
        val argb: Int,
        // 발신자 peerId — relay 후에도 보존해 발신자 미니뷰에 적용(#4). 비면 endpoint 폴백.
        val senderPeerId: String = "",
    ) : Frame()

    @Serializable
    @SerialName("ping")
    data class Ping(val ts: Long) : Frame()

    @Serializable
    @SerialName("pong")
    data class Pong(val ts: Long) : Frame()

    @Serializable
    @SerialName("bye")
    data class Bye(val reason: String) : Frame()

    // Phase 4-F: 모임 모드 호스트가 다른 조인자에게 알리는 멤버십 변화.
    // - PeerJoined: 새 조인자 합류 시 (1) 기존 조인자들에게 새 조인자 정보, (2) 새 조인자에게
    //   기존 조인자들 정보 양방향 송신.
    // - PeerLeft: 조인자 끊김 시 다른 조인자들에게 알림. 받는 측은 미니 뷰에서 그 peerId 제거.
    @Serializable
    @SerialName("peer_joined")
    data class PeerJoined(val peerId: String, val nick: String) : Frame()

    @Serializable
    @SerialName("peer_left")
    data class PeerLeft(val peerId: String) : Frame()

    // Phase 4-H: 모임 모드 호스트가 "그리기 시작" 누른 시점에 broadcast. 조인자들은 핸드셰이크
    // 완료만으론 페어링 화면에서 대기하다가 이 신호 받으면 함께 Draw 화면으로 진입.
    // 핸드셰이크 완료 즉시 조인자가 자동 진입하던 4-D 의 비대칭 UX를 정리.
    @Serializable
    @SerialName("party_start")
    data object PartyStart : Frame()

    // 나눠 그리기 시작 — 호스트가 레이아웃 + 슬롯 순서(peerId 리스트)를 확정해 broadcast.
    // 조인자는 이 신호를 받고 draw 진입(모임의 PartyStart 지각입장과 동일 역할, 데이터 동반).
    // peerIds[i] = 슬롯 i 담당자(호스트가 0번, 조인 순으로). layoutId = SplitLayout.name.
    @Serializable
    @SerialName("split_start")
    data class SplitStart(
        val layoutId: String,
        val peerIds: List<String>,
    ) : Frame()
}

const val PROTO_VERSION: Int = 1
