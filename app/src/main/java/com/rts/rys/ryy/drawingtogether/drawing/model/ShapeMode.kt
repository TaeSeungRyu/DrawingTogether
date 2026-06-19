package com.rts.rys.ryy.drawingtogether.drawing.model

// 그리기 모드. None = 자유 곡선(폴리라인), 그 외 = 첫 점과 마지막 점을
// 바운딩 박스로 삼아 정해진 도형 하나를 그린다.
enum class ShapeMode(val displayName: String) {
    None("자유"),
    Circle("동그라미"),
    Rect("사각형"),
    Triangle("삼각형"),
    Pentagon("오각형"),
    Hexagon("육각형"),
    Star("별"),
    Heart("하트"),
}
