package com.rts.rys.ryy.drawingtogether.drawing.model

import kotlinx.serialization.Serializable

// 캔버스에 배치된 텍스트 한 개. stroke·스티커와 동일한 정규화 좌표계를 쓴다.
// 입력 종료 시 굳어지는 불변 요소 — 배치 후 내용/위치/크기 수정 불가, 삭제만 가능(설계 결정).
// cx, cy: 중심 위치(0..1). sizeFrac: 글자 크기 = 캔버스 짧은 변 대비 비율. colorArgb: 글자색.
// 픽셀이 아니라 문자열 + 메타만 보관 — 렌더 시 TextRenderer 가 기기 기본 폰트로 그린다.
@Serializable
data class TextElement(
    val id: TextId,
    val authorId: PeerId,
    val text: String,
    val cx: Float,
    val cy: Float,
    val sizeFrac: Float,
    val colorArgb: Int,
    // 배치 시점의 단조 증가 순번 — 스냅샷 복원 시 통합 undo 순서 재구성용(#8). 0L = 미부여(구 데이터).
    val seq: Long = 0L,
)
