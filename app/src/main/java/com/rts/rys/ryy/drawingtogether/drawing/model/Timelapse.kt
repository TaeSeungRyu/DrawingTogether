package com.rts.rys.ryy.drawingtogether.drawing.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 타임랩스 = 그리는 과정의 이벤트 로그. 빈 CanvasState 에 시간순으로 다시 apply 하면 과정이 재현됨.
// 배경 사진 비트맵은 로그에 넣지 않고 ref(파일명) 로만 참조 — 비트맵은 TimelapseStore 가 별도 파일로 보관.

// 로그 한 줄이 담는 동작. DrawingEvent(stroke/스티커) + 배경 변경 마커.
@Serializable
sealed interface TimelapseOp {
    @Serializable
    @SerialName("draw")
    data class Draw(val event: DrawingEvent) : TimelapseOp

    @Serializable
    @SerialName("bg_color")
    data class BackgroundColor(val argb: Int) : TimelapseOp

    // ref = "bg-<n>" (파일 매칭), null = 사진 제거.
    @Serializable
    @SerialName("bg_photo")
    data class BackgroundPhoto(val ref: String?) : TimelapseOp

    // 기록 시작 시점에 이미 그려져 있던 내용(기록 버튼 전 작업)을 초기 상태로 심는다.
    // 재생 시 applySnapshot 으로 한 번에 복원. 보통 로그 맨 앞(atMs=0)에 1회.
    @Serializable
    @SerialName("snapshot")
    data class Snapshot(
        val strokes: List<Stroke>,
        val stickers: List<Sticker>,
        val texts: List<TextElement> = emptyList(),
    ) : TimelapseOp
}

@Serializable
data class TimelapseEntry(
    val atMs: Long,        // 녹화 시작 기준 경과 ms
    val op: TimelapseOp,
)

@Serializable
data class Timelapse(
    val version: Int = 1,
    val id: String,
    val createdAtEpochMs: Long,
    val durationMs: Long,
    val entries: List<TimelapseEntry>,
)
