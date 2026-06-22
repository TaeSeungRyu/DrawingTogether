package com.rts.rys.ryy.drawingtogether.drawing.model

import kotlinx.serialization.Serializable

@Serializable
data class Stroke(
    val id: StrokeId,
    val authorId: PeerId,
    val tool: ToolSettings,
    val points: List<Point>,
)
