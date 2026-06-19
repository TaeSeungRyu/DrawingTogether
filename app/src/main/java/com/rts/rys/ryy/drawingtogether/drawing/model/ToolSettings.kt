package com.rts.rys.ryy.drawingtogether.drawing.model

enum class ToolKind { Pen, Eraser }

data class ToolSettings(
    val kind: ToolKind,
    val colorArgb: Int,
    val strokeWidthDp: Float,
    // BrushShape는 default를 두어 기존 호출자/테스트가 그대로 동작하게 함.
    val shape: BrushShape = BrushShape.Round,
) {
    companion object {
        fun defaultPen(): ToolSettings = ToolSettings(
            kind = ToolKind.Pen,
            colorArgb = 0xFF000000.toInt(),
            strokeWidthDp = 4f,
            shape = BrushShape.Round,
        )
    }
}
