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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Size
import com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushCapStyle
import com.rts.rys.ryy.drawingtogether.drawing.model.Point as DrawPoint
import com.rts.rys.ryy.drawingtogether.drawing.model.StrokeId
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolSettings

// 완료된 획 + 진행 중 획을 매 프레임 다시 그린다.
// 실제 stroke 렌더링 함수는 StrokeRenderer.kt — 같은 코드가 PNG 합성에서도 사용됨.
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
        // 1. 사진 배경 (있으면) — 캔버스 종횡비는 외부에서 사진에 맞춰 잡혀있어 늘림 없이 채워짐
        state.background?.bitmap?.let { bg ->
            drawImage(
                image = bg,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bg.width, bg.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(canvasSize.width, canvasSize.height),
            )
        }
        // 2. 완료된 stroke
        state.strokes.forEach { drawStroke(it, canvasSize, density) }
        // 3. 진행 중 stroke
        state.openStrokes.values.forEach { drawStroke(it, canvasSize, density) }
        // 4. 커서 인디케이터
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

private fun Offset.toNormalized(canvasSize: IntSize): DrawPoint {
    val w = canvasSize.width.coerceAtLeast(1).toFloat()
    val h = canvasSize.height.coerceAtLeast(1).toFloat()
    return DrawPoint(
        x = (x / w).coerceIn(0f, 1f),
        y = (y / h).coerceIn(0f, 1f),
    )
}
