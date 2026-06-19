package com.rts.rys.ryy.drawingtogether.drawing.model

data class Stroke(
    val id: StrokeId,
    val authorId: PeerId,
    val tool: ToolSettings,
    val points: List<Point>,
)
