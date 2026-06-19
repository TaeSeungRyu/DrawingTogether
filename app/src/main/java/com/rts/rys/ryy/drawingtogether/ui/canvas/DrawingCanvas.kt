package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Size
import com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushShape
import com.rts.rys.ryy.drawingtogether.drawing.model.Point as DrawPoint
import com.rts.rys.ryy.drawingtogether.drawing.model.Stroke
import com.rts.rys.ryy.drawingtogether.drawing.model.StrokeId
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolKind
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolSettings

// 완료된 획 + 진행 중 획을 매 프레임 다시 그린다.
// 완료된 획용 ImageBitmap 캐시는 Phase 4(다듬기)에서 도입 — Phase 1 수준의 트래픽에선
// Compose Canvas의 RenderNode 캐싱만으로 충분히 부드러움.
@Composable
fun DrawingCanvas(
    state: CanvasState,
    tool: ToolSettings,
    onStrokeStart: (StrokeId, DrawPoint) -> Unit,
    onStrokeAppend: (StrokeId, List<DrawPoint>) -> Unit,
    onStrokeEnd: (StrokeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // 그리는 동안 현재 포인터 위치 — 펜/지우개 두께 미리보기용. 손가락 떼면 null.
    var cursor by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val strokeId = StrokeId.random()
                    onStrokeStart(strokeId, first.position.toNormalized(size))
                    cursor = first.position
                    first.consume()

                    val pending = mutableListOf<DrawPoint>()
                    while (true) {
                        val event = awaitPointerEvent()
                        var anyPressed = false
                        var latestPos: Offset? = null
                        event.changes.forEach { change: PointerInputChange ->
                            if (change.pressed) {
                                anyPressed = true
                                latestPos = change.position
                            }
                            if (change.positionChanged()) {
                                pending.add(change.position.toNormalized(size))
                                change.consume()
                            }
                        }
                        if (latestPos != null) cursor = latestPos
                        if (pending.isNotEmpty()) {
                            onStrokeAppend(strokeId, pending.toList())
                            pending.clear()
                        }
                        if (!anyPressed) break
                    }

                    onStrokeEnd(strokeId)
                    cursor = null
                }
            }
    ) {
        state.strokes.forEach { drawStroke(it, canvasSize, density) }
        state.openStrokes.values.forEach { drawStroke(it, canvasSize, density) }
        cursor?.let { pos ->
            drawBrushIndicator(center = pos, tool = tool, density = density)
        }
    }
}

private fun DrawScope.drawBrushIndicator(center: Offset, tool: ToolSettings, density: Float) {
    val sizePx = tool.strokeWidthDp * density
    val color = Color.Black.copy(alpha = 0.45f)
    val style = DrawStroke(width = 1.5f * density)
    when (tool.shape) {
        BrushShape.Square -> {
            val half = sizePx / 2f
            drawRect(
                color = color,
                topLeft = Offset(center.x - half, center.y - half),
                size = Size(sizePx, sizePx),
                style = style,
            )
        }
        BrushShape.Round, BrushShape.Highlighter -> {
            drawCircle(color = color, center = center, radius = sizePx / 2f, style = style)
        }
    }
}

private fun DrawScope.drawStroke(stroke: Stroke, canvasSize: IntSize, density: Float) {
    if (stroke.points.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return

    val w = canvasSize.width.toFloat()
    val h = canvasSize.height.toFloat()

    val path = Path()
    val first = stroke.points.first()
    path.moveTo(first.x * w, first.y * h)
    for (i in 1 until stroke.points.size) {
        val p = stroke.points[i]
        path.lineTo(p.x * w, p.y * h)
    }

    val (cap, join) = capJoinFor(stroke.tool.shape)
    drawPath(
        path = path,
        color = colorFor(stroke.tool),
        style = DrawStroke(
            width = stroke.tool.strokeWidthDp * density,
            cap = cap,
            join = join,
        ),
    )
}

// 지우개 = 배경색(흰색)으로 덮어쓰기. Phase 1 단순화 — 실제 픽셀 삭제 아님.
// 형광펜은 알파를 낮춰 겹친 부분이 진해지는 마커 효과.
private fun colorFor(tool: ToolSettings): Color {
    if (tool.kind == ToolKind.Eraser) return Color.White
    val base = Color(tool.colorArgb)
    return if (tool.shape == BrushShape.Highlighter) base.copy(alpha = 0.3f) else base
}

private fun capJoinFor(shape: BrushShape): Pair<StrokeCap, StrokeJoin> = when (shape) {
    BrushShape.Square -> StrokeCap.Square to StrokeJoin.Miter
    BrushShape.Round, BrushShape.Highlighter -> StrokeCap.Round to StrokeJoin.Round
}

private fun Offset.toNormalized(canvasSize: IntSize): DrawPoint {
    val w = canvasSize.width.coerceAtLeast(1).toFloat()
    val h = canvasSize.height.coerceAtLeast(1).toFloat()
    return DrawPoint(
        x = (x / w).coerceIn(0f, 1f),
        y = (y / h).coerceIn(0f, 1f),
    )
}
