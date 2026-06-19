package com.rts.rys.ryy.drawingtogether.drawing.model

// 캔버스에서 발생하는 단일 의도 단위.
// 로컬 입력과 (Phase 2+) 원격 인바운드 모두 동일한 타입으로 들어와 CanvasState.apply()에서 적용됨.
// seq는 작성자별 단조 증가. 전역 순서는 보장하지 않음.
sealed interface DrawingEvent {
    val seq: Long
    val authorId: PeerId

    data class StrokeStart(
        override val seq: Long,
        override val authorId: PeerId,
        val strokeId: StrokeId,
        val tool: ToolSettings,
        val point: Point,
    ) : DrawingEvent

    data class StrokeAppend(
        override val seq: Long,
        override val authorId: PeerId,
        val strokeId: StrokeId,
        val points: List<Point>,
    ) : DrawingEvent

    data class StrokeEnd(
        override val seq: Long,
        override val authorId: PeerId,
        val strokeId: StrokeId,
    ) : DrawingEvent

    data class Clear(
        override val seq: Long,
        override val authorId: PeerId,
    ) : DrawingEvent

    data class Undo(
        override val seq: Long,
        override val authorId: PeerId,
        val strokeId: StrokeId,
    ) : DrawingEvent
}
