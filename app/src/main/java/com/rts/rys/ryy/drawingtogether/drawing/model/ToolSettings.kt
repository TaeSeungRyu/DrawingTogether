package com.rts.rys.ryy.drawingtogether.drawing.model

enum class ToolKind { Pen, Eraser }

data class ToolSettings(
    val kind: ToolKind,
    val colorArgb: Int,
    val strokeWidthDp: Float,
    val brush: BrushType = BrushType.Pen,
    val shape: ShapeMode = ShapeMode.None,
) {
    companion object {
        fun defaultPen(): ToolSettings = ToolSettings(
            kind = ToolKind.Pen,
            colorArgb = 0xFF000000.toInt(),
            strokeWidthDp = 4f,
            brush = BrushType.Pen,
            shape = ShapeMode.None,
        )
    }
}
