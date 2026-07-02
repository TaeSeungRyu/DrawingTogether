package com.rts.rys.ryy.drawingtogether.drawing.model

import kotlinx.serialization.Serializable

// 캔버스에 배치된 스티커 한 개. stroke 와 동일하게 정규화 좌표계를 쓴다.
// cx, cy: 중심 위치 (0..1). scale: 캔버스 짧은 변 대비 크기 비율. rotationDeg: 시계방향 회전 각도.
// 픽셀 데이터가 아니라 key + 변환 메타만 보관 — 렌더 시 StickerRenderer 가 벡터로 치환.
@Serializable
data class Sticker(
    val id: StickerId,
    val authorId: PeerId,
    val key: StickerKey,
    val cx: Float,
    val cy: Float,
    val scale: Float,
    val rotationDeg: Float,
    // 배치 시점의 단조 증가 순번 — 스냅샷 복원 시 통합 undo 순서 재구성용(#8). 0L = 미부여(구 데이터).
    val seq: Long = 0L,
)
