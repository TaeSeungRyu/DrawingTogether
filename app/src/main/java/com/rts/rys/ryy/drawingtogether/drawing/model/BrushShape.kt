package com.rts.rys.ryy.drawingtogether.drawing.model

// 붓 종류. ToolKind(펜/지우개)와 직교 — 지우개도 임의의 모양을 가질 수 있음.
enum class BrushShape {
    Round,       // 펜 — 둥근 캡/조인, 불투명
    Square,      // 마커 — 사각 캡/Miter 조인, 불투명
    Highlighter, // 형광펜 — 둥근 캡 + 반투명
}
