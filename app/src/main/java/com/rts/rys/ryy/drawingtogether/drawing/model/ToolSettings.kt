package com.rts.rys.ryy.drawingtogether.drawing.model

enum class ToolKind { Pen, Eraser }

data class ToolSettings(
    val kind: ToolKind,
    val colorArgb: Int,
    val strokeWidthDp: Float,
    // BrushType은 default를 두어 기존 호출자/테스트가 그대로 동작하게 함.
    val brush: BrushType = BrushType.Pen,
) {
    companion object {
        fun defaultPen(): ToolSettings = ToolSettings(
            kind = ToolKind.Pen,
            colorArgb = 0xFF000000.toInt(),
            strokeWidthDp = 4f,
            brush = BrushType.Pen,
        )
    }
}
