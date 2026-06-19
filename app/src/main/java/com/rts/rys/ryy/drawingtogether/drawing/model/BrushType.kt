package com.rts.rys.ryy.drawingtogether.drawing.model

// 붓의 시각적 특성을 한 단위로 묶은 프리셋.
// ToolKind(펜/지우개)와 직교 — 지우개에도 임의의 BrushType이 적용될 수 있음.
//
// alpha:       0..1. Highlighter처럼 겹쳐 그렸을 때 색이 진해지는 효과를 만든다.
// widthScale:  슬라이더로 정한 strokeWidthDp에 곱해진다. 같은 굵기 설정에서도
//              연필은 가늘게, 형광펜은 굵게 보이게 함.
// capStyle:    선의 끝/꺾임 모양. Round = 둥근, Square = 각진.
enum class BrushType(
    val displayName: String,
    val description: String,
    val capStyle: BrushCapStyle,
    val alpha: Float,
    val widthScale: Float,
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
}

enum class BrushCapStyle { Round, Square }
