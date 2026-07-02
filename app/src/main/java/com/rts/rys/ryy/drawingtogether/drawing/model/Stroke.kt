package com.rts.rys.ryy.drawingtogether.drawing.model

import kotlinx.serialization.Serializable

@Serializable
data class Stroke(
    val id: StrokeId,
    val authorId: PeerId,
    val tool: ToolSettings,
    val points: List<Point>,
    // 생성(완료) 시점의 단조 증가 순번. 스냅샷 복원 시 stroke·스티커·텍스트를 시간순으로
    // 병합정렬해 통합 undo 순서를 재구성하는 데 쓴다(#8). 0L = 미부여(구 데이터·미완료).
    val seq: Long = 0L,
)
