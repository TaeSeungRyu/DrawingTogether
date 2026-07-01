package com.rts.rys.ryy.drawingtogether.drawing.model

import kotlinx.serialization.Serializable

// 빈 캔버스(사진 없음)의 가로세로 비율 프리셋. 사진이 있으면 사진 비율이 우선하므로 이 값은 무시됨.
// ratio = width / height. Free 는 null — 화면 영역을 그대로 채운다(fillMaxSize, 현재 기본 동작).
// backgroundColor 처럼 "캔버스 속성"이라 DrawingEvent 가 아닌 CanvasState 의 별도 상태로 보관.
// @Serializable 은 향후 멀티 동기화 대비(현재는 로컬 전용).
@Serializable
enum class CanvasAspect(val label: String, val ratio: Float?) {
    Free("자유", null),
    Square("1:1", 1f),
    Landscape4_3("4:3", 4f / 3f),
    Portrait3_4("3:4", 3f / 4f),
    Landscape16_9("16:9", 16f / 9f),
    Portrait9_16("9:16", 9f / 16f),
}
