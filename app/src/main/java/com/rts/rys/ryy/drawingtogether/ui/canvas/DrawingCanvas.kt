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
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushCapStyle
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushType
import com.rts.rys.ryy.drawingtogether.drawing.model.Point as DrawPoint
import com.rts.rys.ryy.drawingtogether.drawing.model.ShapeMode
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
    val sizePx = strokeWidthPxFor(tool, density)
    val color = Color.Black.copy(alpha = 0.45f)
    val style = DrawStroke(width = 1.5f * density)
    when (tool.brush.capStyle) {
        BrushCapStyle.Square -> {
            val half = sizePx / 2f
            drawRect(
                color = color,
                topLeft = Offset(center.x - half, center.y - half),
                size = Size(sizePx, sizePx),
                style = style,
            )
        }
        BrushCapStyle.Round -> {
            drawCircle(color = color, center = center, radius = sizePx / 2f, style = style)
        }
    }
}

private fun DrawScope.drawStroke(stroke: Stroke, canvasSize: IntSize, density: Float) {
    if (stroke.points.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return
    when (stroke.tool.shape) {
        ShapeMode.None -> drawFreehand(stroke, canvasSize, density)
        else -> drawShapeForm(stroke, canvasSize, density)
    }
}

private fun DrawScope.drawFreehand(stroke: Stroke, canvasSize: IntSize, density: Float) {
    val w = canvasSize.width.toFloat()
    val h = canvasSize.height.toFloat()

    val path = Path()
    val first = stroke.points.first()
    path.moveTo(first.x * w, first.y * h)
    for (i in 1 until stroke.points.size) {
        val p = stroke.points[i]
        path.lineTo(p.x * w, p.y * h)
    }

    val (cap, join) = capJoinFor(stroke.tool.brush)
    drawPath(
        path = path,
        color = colorFor(stroke.tool),
        style = DrawStroke(
            width = strokeWidthPxFor(stroke.tool, density),
            cap = cap,
            join = join,
        ),
    )
}

// 첫 점과 마지막 점을 바운딩 박스로 삼아 정해진 도형 하나를 외곽선으로 그린다.
private fun DrawScope.drawShapeForm(stroke: Stroke, canvasSize: IntSize, density: Float) {
    val first = stroke.points.first()
    val last = stroke.points.last()
    val w = canvasSize.width.toFloat()
    val h = canvasSize.height.toFloat()

    val x1 = first.x * w
    val y1 = first.y * h
    val x2 = last.x * w
    val y2 = last.y * h
    val left = minOf(x1, x2)
    val top = minOf(y1, y2)
    val width = (maxOf(x1, x2) - left).coerceAtLeast(0f)
    val height = (maxOf(y1, y2) - top).coerceAtLeast(0f)
    if (width <= 0f && height <= 0f) return

    val color = colorFor(stroke.tool)
    val (cap, join) = capJoinFor(stroke.tool.brush)
    val style = DrawStroke(
        width = strokeWidthPxFor(stroke.tool, density),
        cap = cap,
        join = join,
    )

    when (stroke.tool.shape) {
        ShapeMode.Circle -> drawOval(
            color = color,
            topLeft = Offset(left, top),
            size = Size(width, height),
            style = style,
        )
        ShapeMode.Rect -> drawRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(width, height),
            style = style,
        )
        ShapeMode.Triangle -> drawPath(
            path = buildRegularPolygonPath(left, top, width, height, sides = 3),
            color = color,
            style = style,
        )
        ShapeMode.Pentagon -> drawPath(
            path = buildRegularPolygonPath(left, top, width, height, sides = 5),
            color = color,
            style = style,
        )
        ShapeMode.Hexagon -> drawPath(
            path = buildRegularPolygonPath(left, top, width, height, sides = 6),
            color = color,
            style = style,
        )
        ShapeMode.Star -> drawPath(
            path = buildStarPath(left, top, width, height),
            color = color,
            style = style,
        )
        ShapeMode.Heart -> drawPath(
            path = buildHeartPath(left, top, width, height),
            color = color,
            style = style,
        )
        ShapeMode.None -> Unit
    }
}

// 지우개 = 배경색(흰색)으로 덮어쓰기. Phase 1 단순화 — 실제 픽셀 삭제 아님.
// 지우개 자체는 BrushType의 alpha 무시 (항상 불투명).
private fun colorFor(tool: ToolSettings): Color {
    if (tool.kind == ToolKind.Eraser) return Color.White
    return Color(tool.colorArgb).copy(alpha = tool.brush.alpha)
}

private fun strokeWidthPxFor(tool: ToolSettings, density: Float): Float =
    tool.strokeWidthDp * tool.brush.widthScale * density

private fun capJoinFor(brush: BrushType): Pair<StrokeCap, StrokeJoin> = when (brush.capStyle) {
    BrushCapStyle.Square -> StrokeCap.Square to StrokeJoin.Miter
    BrushCapStyle.Round -> StrokeCap.Round to StrokeJoin.Round
}

private fun Offset.toNormalized(canvasSize: IntSize): DrawPoint {
    val w = canvasSize.width.coerceAtLeast(1).toFloat()
    val h = canvasSize.height.coerceAtLeast(1).toFloat()
    return DrawPoint(
        x = (x / w).coerceIn(0f, 1f),
        y = (y / h).coerceIn(0f, 1f),
    )
}
