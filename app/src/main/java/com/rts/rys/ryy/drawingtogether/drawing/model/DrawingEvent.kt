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

    // 스티커 배치 — 새 스티커 한 개를 캔버스에 추가. 통합 undo 스택에 push 된다.
    @Serializable
    @SerialName("place_sticker")
    data class PlaceSticker(
        override val seq: Long,
        override val authorId: PeerId,
        val stickerId: StickerId,
        val key: StickerKey,
        val cx: Float,
        val cy: Float,
        val scale: Float,
        val rotationDeg: Float,
    ) : DrawingEvent

    // 스티커 변형 — 이동/크기/회전 공용. commit-on-end 라 제스처 종료 시 최종 상태 1회만 전송.
    // undo 스택은 불변(라이브 편집은 되돌리기 대상 아님).
    @Serializable
    @SerialName("transform_sticker")
    data class TransformSticker(
        override val seq: Long,
        override val authorId: PeerId,
        val stickerId: StickerId,
        val cx: Float,
        val cy: Float,
        val scale: Float,
        val rotationDeg: Float,
    ) : DrawingEvent

    // 스티커 삭제 — X 핸들 또는 통합 undo(스티커가 마지막 추가물일 때)로 발생.
    @Serializable
    @SerialName("remove_sticker")
    data class RemoveSticker(
        override val seq: Long,
        override val authorId: PeerId,
        val stickerId: StickerId,
    ) : DrawingEvent
}
