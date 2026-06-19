package com.rts.rys.ryy.drawingtogether.drawing.model

enum class ToolKind { Pen, Eraser }

data class ToolSettings(
    val kind: ToolKind,
    val colorArgb: Int,
    val strokeWidthDp: Float,
) {
    companion object {
        fun defaultPen(): ToolSettings =
            ToolSettings(kind = ToolKind.Pen, colorArgb = 0xFF000000.toInt(), strokeWidthDp = 4f)
    }
}
