package com.rts.rys.ryy.drawingtogether.drawing.model

import kotlinx.serialization.Serializable

// 붓의 시각적 특성을 한 단위로 묶은 프리셋.
// ToolKind(펜/지우개)와 직교 — 지우개에도 임의의 BrushType이 적용될 수 있음.
//
// alpha:       0..1. Highlighter처럼 겹쳐 그렸을 때 색이 진해지는 효과를 만든다.
// widthScale:  슬라이더로 정한 strokeWidthDp에 곱해진다. 같은 굵기 설정에서도
//              연필은 가늘게, 형광펜은 굵게 보이게 함.
// capStyle:    선의 끝/꺾임 모양. Round = 둥근, Square = 각진.
// fixedWidthDp: non-null 이면 슬라이더 굵기를 무시하고 이 dp 로 고정 — "세밀붓" 처럼 굵기 선택
//              불가한 붓용. 이때 widthScale 은 실제 그리기엔 안 쓰이고 미리보기 두께에만 영향.
@Serializable
enum class BrushType(
    val displayName: String,
    val description: String,
    val capStyle: BrushCapStyle,
    val alpha: Float,
    val widthScale: Float,
    val fixedWidthDp: Float? = null,
) {
    Pen(
        displayName = "펜",
        description = "균일하고 또렷한 기본 선",
        capStyle = BrushCapStyle.Round,
        alpha = 1.0f,
        widthScale = 1.0f,
    ),
    Pencil(
        displayName = "연필",
        description = "가늘고 흐릿한 스케치",
        capStyle = BrushCapStyle.Round,
        alpha = 0.85f,
        widthScale = 0.6f,
    ),
    Fine(
        displayName = "세밀붓",
        description = "굵기 고정된 아주 가는 선",
        capStyle = BrushCapStyle.Round,
        alpha = 1.0f,
        widthScale = 0.35f,
        fixedWidthDp = 1.2f,
    ),
    Ink(
        displayName = "잉크펜",
        description = "굵고 진한 잉크",
        capStyle = BrushCapStyle.Round,
        alpha = 1.0f,
        widthScale = 1.4f,
    ),
    Marker(
        displayName = "마커",
        description = "각진 끝의 마커펜",
        capStyle = BrushCapStyle.Square,
        alpha = 1.0f,
        widthScale = 1.1f,
    ),
    Highlighter(
        displayName = "형광펜",
        description = "겹칠수록 진해지는 반투명",
        capStyle = BrushCapStyle.Square,
        alpha = 0.3f,
        widthScale = 1.6f,
    ),
    Crayon(
        displayName = "크레용",
        description = "부드럽고 흐릿한 질감",
        capStyle = BrushCapStyle.Round,
        alpha = 0.55f,
        widthScale = 1.0f,
    ),
    Airbrush(
        displayName = "에어브러시",
        description = "흩뿌리는 스프레이",
        capStyle = BrushCapStyle.Round,
        // alpha = 점 농도(겹칠수록 진해짐), widthScale = 분사 반경 스케일.
        alpha = 0.5f,
        widthScale = 1.8f,
    ),
    Blur(
        displayName = "번짐",
        description = "가장자리가 번지는 수채 느낌",
        capStyle = BrushCapStyle.Round,
        alpha = 0.7f,
        widthScale = 1.3f,
    ),
    Neon(
        displayName = "네온",
        description = "어두운 배경에서 빛나는 발광 선",
        capStyle = BrushCapStyle.Round,
        alpha = 1.0f,
        widthScale = 1.1f,
    ),
    Dash(
        displayName = "점선",
        description = "끊어지는 파선",
        capStyle = BrushCapStyle.Round,
        alpha = 1.0f,
        widthScale = 1.0f,
    ),
    Rainbow(
        displayName = "무지개",
        description = "진행에 따라 색이 변하는 선",
        capStyle = BrushCapStyle.Round,
        // 색은 렌더 시 결정론적으로 생성 — colorArgb 무시.
        alpha = 1.0f,
        widthScale = 1.2f,
    ),
    Calligraphy(
        displayName = "붓펜",
        description = "속도에 따라 굵기가 변하는 붓",
        capStyle = BrushCapStyle.Round,
        alpha = 1.0f,
        widthScale = 1.4f,
    ),
}

enum class BrushCapStyle { Round, Square }
