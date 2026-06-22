package com.rts.rys.ryy.drawingtogether.drawing.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 캔버스에서 발생하는 단일 의도 단위.
// 로컬 입력과 (Phase 3+) 원격 인바운드 모두 동일한 타입으로 들어와 CanvasState.apply()에서 적용됨.
// seq는 작성자별 단조 증가. 전역 순서는 보장하지 않음.
@Serializable
sealed interface DrawingEvent {
    val seq: Long
    val authorId: PeerId

    @Serializable
    @SerialName("stroke_start")
    data class StrokeStart(
        override val seq: Long,
        override val authorId: PeerId,
        val strokeId: StrokeId,
        val tool: ToolSettings,
        val point: Point,
    ) : DrawingEvent

    @Serializable
    @SerialName("stroke_append")
    data class StrokeAppend(
        override val seq: Long,
        override val authorId: PeerId,
        val strokeId: StrokeId,
        val points: List<Point>,
    ) : DrawingEvent

    @Serializable
    @SerialName("stroke_end")
    data class StrokeEnd(
        override val seq: Long,
        override val authorId: PeerId,
        val strokeId: StrokeId,
    ) : DrawingEvent

    @Serializable
    @SerialName("clear")
    data class Clear(
        override val seq: Long,
        override val authorId: PeerId,
    ) : DrawingEvent

    @Serializable
    @SerialName("undo")
    data class Undo(
        override val seq: Long,
        override val authorId: PeerId,
        val strokeId: StrokeId,
    ) : DrawingEvent
}
