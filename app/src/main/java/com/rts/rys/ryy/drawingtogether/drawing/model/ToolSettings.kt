package com.rts.rys.ryy.drawingtogether.drawing.model

import kotlinx.serialization.Serializable

@Serializable
enum class ToolKind { Pen, Eraser, Sticker, Eyedropper, Text }

@Serializable
data class ToolSettings(
    val kind: ToolKind,
    val colorArgb: Int,
    val strokeWidthDp: Float,
    val brush: BrushType = BrushType.Pen,
    val shape: ShapeMode = ShapeMode.None,
    // 도형 채우기 — true 면 외곽선 대신 색으로 채운다. shape == None(자유 곡선)에선 무시.
    val fill: Boolean = false,
    // ToolKind.Sticker 일 때 배치할 스티커 종류. 그 외 모드에선 무시.
    val stickerKey: StickerKey? = null,
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
