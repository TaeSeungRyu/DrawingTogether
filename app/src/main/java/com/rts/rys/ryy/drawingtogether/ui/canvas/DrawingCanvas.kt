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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.material3.MaterialTheme
import com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushCapStyle
import com.rts.rys.ryy.drawingtogether.drawing.model.Point as DrawPoint
import com.rts.rys.ryy.drawingtogether.drawing.model.Sticker
import com.rts.rys.ryy.drawingtogether.drawing.model.StickerId
import com.rts.rys.ryy.drawingtogether.drawing.model.StrokeId
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolKind
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolSettings
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private const val MIN_STICKER_SCALE = 0.04f
private const val MAX_STICKER_SCALE = 0.8f

// 완료된 획 + 진행 중 획 + 스티커를 매 프레임 다시 그린다.
// stroke 렌더는 StrokeRenderer.kt, 스티커는 StickerRenderer.kt — 같은 코드가 PNG 합성에서도 쓰임.
// 도구가 스티커 모드면 stroke 제스처 대신 배치/선택/이동/크기·회전/삭제 제스처가 동작한다.
@Composable
fun DrawingCanvas(
    state: CanvasState,
    tool: ToolSettings,
    onStrokeStart: (StrokeId, DrawPoint) -> Unit,
    onStrokeAppend: (StrokeId, List<DrawPoint>) -> Unit,
    onStrokeEnd: (StrokeId) -> Unit,
    modifier: Modifier = Modifier,
    guideCross: Boolean = false,
    guideGridCells: Int = 0,
    // 스티커 편집 콜백 — 스티커 모드에서만 호출.
    onPlaceSticker: (Float, Float) -> StickerId? = { _, _ -> null },
    onTransformStickerLocal: (StickerId, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    onCommitStickerTransform: (StickerId, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    onRemoveSticker: (StickerId) -> Unit = {},
) {
    val density = LocalDensity.current.density
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // 그리는 동안 현재 포인터 위치 — 펜/지우개 두께 미리보기용. 손가락 떼면 null.
    var cursor by remember { mutableStateOf<Offset?>(null) }
    // 선택된 스티커. 스티커 모드에서만 의미. 모드를 벗어나면 오버레이/핸들이 숨겨진다.
    var selectedStickerId by remember { mutableStateOf<StickerId?>(null) }
    val selectionColor = MaterialTheme.colorScheme.primary
    val isSticker = tool.kind == ToolKind.Sticker

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(isSticker) {
                if (isSticker) {
                    stickerGestures(
                        stickers = { state.stickers },
                        density = density,
                        selectedId = { selectedStickerId },
                        setSelected = { selectedStickerId = it },
                        onPlace = onPlaceSticker,
                        onTransformLocal = onTransformStickerLocal,
                        onCommit = onCommitStickerTransform,
                        onRemove = onRemoveSticker,
                    )
                } else {
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
            }
    ) {
        // 1. 사진 배경 (있으면)
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
        // 4. 스티커 (stroke 위)
        state.stickers.forEach { drawSticker(it, canvasSize) }
        // 5. 안내선 (stroke/스티커 위, 커서 아래)
        drawGuides(canvasSize, density, guideCross, guideGridCells)
        // 6. 스티커 선택 핸들 (스티커 모드 + 선택됨)
        if (isSticker) {
            val sel = state.stickers.firstOrNull { it.id == selectedStickerId }
            if (sel != null) drawStickerSelection(sel, canvasSize, density, selectionColor)
        }
        // 7. 커서 인디케이터 (그리기 모드)
        cursor?.let { pos ->
            drawBrushIndicator(center = pos, tool = tool, density = density)
        }
    }
}

// 스티커 편집 제스처 루프. 한 번의 down→up 마다:
//  - 선택된 스티커의 X 핸들 → 삭제
//  - 선택된 스티커의 크기/회전 핸들 → 드래그로 scale+rotation 동시 조절 (commit-on-end)
//  - 다른 스티커 본체 → 선택 + 드래그로 이동 (commit-on-end)
//  - 빈 곳 → 그 위치에 새 스티커 배치 + 선택 + 드래그로 이동
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.stickerGestures(
    stickers: () -> List<Sticker>,
    density: Float,
    selectedId: () -> StickerId?,
    setSelected: (StickerId?) -> Unit,
    onPlace: (Float, Float) -> StickerId?,
    onTransformLocal: (StickerId, Float, Float, Float, Float) -> Unit,
    onCommit: (StickerId, Float, Float, Float, Float) -> Unit,
    onRemove: (StickerId) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val w = size.width.toFloat().coerceAtLeast(1f)
        val h = size.height.toFloat().coerceAtLeast(1f)
        val shortSide = min(size.width, size.height).toFloat().coerceAtLeast(1f)
        val handleRadius = 18f * density
        val list = stickers()

        val selected = list.firstOrNull { it.id == selectedId() }

        // 1. 선택된 스티커의 핸들 우선 검사.
        if (selected != null) {
            val local = toLocal(selected, down.position, w, h)
            val halfPx = selected.scale * shortSide / 2f
            if (hypot(local.x - halfPx, local.y + halfPx) <= handleRadius) {
                // X 핸들 (우상단) — 삭제.
                onRemove(selected.id)
                setSelected(null)
                drainGesture()
                return@awaitEachGesture
            }
            if (hypot(local.x - halfPx, local.y - halfPx) <= handleRadius) {
                // 크기/회전 핸들 (우하단).
                resizeRotateGesture(selected, w, h, shortSide, onTransformLocal, onCommit)
                return@awaitEachGesture
            }
        }

        // 2. 본체 hit-test (위에 그려진 것 우선 = 역순).
        val hit = list.lastOrNull { s ->
            val local = toLocal(s, down.position, w, h)
            val halfPx = s.scale * shortSide / 2f
            kotlin.math.abs(local.x) <= halfPx && kotlin.math.abs(local.y) <= halfPx
        }

        val target: Sticker?
        if (hit != null) {
            setSelected(hit.id)
            target = hit
        } else {
            // 빈 곳 → 새 스티커 배치.
            val cx = (down.position.x / w).coerceIn(0f, 1f)
            val cy = (down.position.y / h).coerceIn(0f, 1f)
            val newId = onPlace(cx, cy)
            if (newId == null) {
                drainGesture()
                return@awaitEachGesture
            }
            setSelected(newId)
            target = stickers().firstOrNull { it.id == newId }
        }

        if (target == null) {
            drainGesture()
            return@awaitEachGesture
        }
        moveGesture(target, down.position, w, h, onTransformLocal, onCommit)
    }
}

// 본체 드래그 = 이동. down 대비 변위를 정규화 좌표에 더한다. commit-on-end.
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.moveGesture(
    sticker: Sticker,
    downPos: Offset,
    w: Float,
    h: Float,
    onTransformLocal: (StickerId, Float, Float, Float, Float) -> Unit,
    onCommit: (StickerId, Float, Float, Float, Float) -> Unit,
) {
    val startCx = sticker.cx
    val startCy = sticker.cy
    var cx = startCx
    var cy = startCy
    var moved = false
    while (true) {
        val event = awaitPointerEvent()
        var pressed = false
        var pos: Offset? = null
        event.changes.forEach { c ->
            if (c.pressed) { pressed = true; pos = c.position }
            if (c.positionChanged()) c.consume()
        }
        pos?.let { p ->
            cx = (startCx + (p.x - downPos.x) / w).coerceIn(0f, 1f)
            cy = (startCy + (p.y - downPos.y) / h).coerceIn(0f, 1f)
            if (p != downPos) moved = true
            onTransformLocal(sticker.id, cx, cy, sticker.scale, sticker.rotationDeg)
        }
        if (!pressed) break
    }
    if (moved) onCommit(sticker.id, cx, cy, sticker.scale, sticker.rotationDeg)
}

// 우하단 핸들 드래그 = 중심 기준 거리→scale, 각도→rotation 동시. commit-on-end.
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.resizeRotateGesture(
    sticker: Sticker,
    w: Float,
    h: Float,
    shortSide: Float,
    onTransformLocal: (StickerId, Float, Float, Float, Float) -> Unit,
    onCommit: (StickerId, Float, Float, Float, Float) -> Unit,
) {
    val centerX = sticker.cx * w
    val centerY = sticker.cy * h
    var scale = sticker.scale
    var rot = sticker.rotationDeg
    var moved = false
    while (true) {
        val event = awaitPointerEvent()
        var pressed = false
        var pos: Offset? = null
        event.changes.forEach { c ->
            if (c.pressed) { pressed = true; pos = c.position }
            if (c.positionChanged()) c.consume()
        }
        pos?.let { p ->
            val dx = p.x - centerX
            val dy = p.y - centerY
            val cornerDist = hypot(dx, dy)
            // 핸들은 로컬 (half, half) = 중심에서 half*√2 거리 → half = cornerDist/√2.
            val half = cornerDist / 1.41421356f
            scale = (half * 2f / shortSide).coerceIn(MIN_STICKER_SCALE, MAX_STICKER_SCALE)
            // 핸들 기준각 45° 를 빼서 손가락 방향이 그대로 회전각이 되게.
            rot = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() - 45f
            moved = true
            onTransformLocal(sticker.id, sticker.cx, sticker.cy, scale, rot)
        }
        if (!pressed) break
    }
    if (moved) onCommit(sticker.id, sticker.cx, sticker.cy, scale, rot)
}

// 남은 포인터 이벤트를 소비만 하고 흘려보냄 (배치/삭제 후 잔여 드래그 무시).
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.drainGesture() {
    while (true) {
        val event = awaitPointerEvent()
        var pressed = false
        event.changes.forEach { c ->
            if (c.pressed) pressed = true
            if (c.positionChanged()) c.consume()
        }
        if (!pressed) break
    }
}

// 캔버스 px 좌표를 스티커 로컬(중심 0,0, 회전 역적용) 좌표로 변환. hit-test/핸들 판정에 사용.
private fun toLocal(sticker: Sticker, p: Offset, w: Float, h: Float): Offset {
    val cx = sticker.cx * w
    val cy = sticker.cy * h
    val dx = p.x - cx
    val dy = p.y - cy
    val rad = Math.toRadians(-sticker.rotationDeg.toDouble())
    val cosA = cos(rad).toFloat()
    val sinA = sin(rad).toFloat()
    return Offset(dx * cosA - dy * sinA, dx * sinA + dy * cosA)
}

// 선택 오버레이 — 바운딩 박스 + 우하단 크기/회전 핸들 + 우상단 X 핸들. 스티커와 함께 회전.
private fun DrawScope.drawStickerSelection(
    sticker: Sticker,
    canvasSize: IntSize,
    density: Float,
    color: Color,
) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
    val shortSide = min(canvasSize.width, canvasSize.height).toFloat()
    val half = (sticker.scale * shortSide / 2f).coerceAtLeast(1f)
    val cx = sticker.cx * canvasSize.width
    val cy = sticker.cy * canvasSize.height
    val handleR = 12f * density
    val line = 1.5f * density

    withTransform({
        translate(cx, cy)
        rotate(degrees = sticker.rotationDeg, pivot = Offset.Zero)
    }) {
        drawRect(
            color = color,
            topLeft = Offset(-half, -half),
            size = Size(half * 2f, half * 2f),
            style = DrawStroke(width = line),
        )
        // 크기/회전 핸들 (우하단).
        drawCircle(color = color, radius = handleR, center = Offset(half, half))
        drawCircle(color = Color.White, radius = handleR * 0.45f, center = Offset(half, half))
        // 삭제 핸들 (우상단) + ×.
        drawCircle(color = Color(0xFFE53935), radius = handleR, center = Offset(half, -half))
        val x = half
        val y = -half
        val d = handleR * 0.45f
        drawLine(Color.White, Offset(x - d, y - d), Offset(x + d, y + d), strokeWidth = line * 1.3f)
        drawLine(Color.White, Offset(x - d, y + d), Offset(x + d, y - d), strokeWidth = line * 1.3f)
    }
}

// 안내선 오버레이 — 격자(gridCells×gridCells 칸) + 중앙 십자선. 독립 토글.
private fun DrawScope.drawGuides(
    canvasSize: IntSize,
    density: Float,
    cross: Boolean,
    gridCells: Int,
) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
    val w = canvasSize.width.toFloat()
    val h = canvasSize.height.toFloat()
    val thin = 1f * density
    val gridColor = Color.Gray.copy(alpha = 0.30f)
    val crossColor = Color.Gray.copy(alpha = 0.55f)

    if (gridCells > 1) {
        for (i in 1 until gridCells) {
            val x = w * i / gridCells
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = thin)
            val y = h * i / gridCells
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = thin)
        }
    }
    if (cross) {
        drawLine(crossColor, Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = thin)
        drawLine(crossColor, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = thin)
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
