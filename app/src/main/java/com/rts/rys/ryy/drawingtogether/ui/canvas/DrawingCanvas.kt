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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
    // 손떨림 보정 계수(지수이동평균). 1f = 보정 없음(원본). 작을수록 더 매끄럽게.
    smoothingAlpha: Float = 1f,
    // 트레이싱 보조 — 사진 배경 표시 알파(1f=원본). 저장/동기화엔 미반영, 화면 표시만.
    backgroundAlpha: Float = 1f,
    // 스포이드 — 탭한 정규화 좌표의 색을 집는다. 스포이드 모드에서만 호출.
    onPickColor: (Float, Float) -> Unit = { _, _ -> },
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
    // 스포이드 조준 위치 — 누르는 동안만 non-null. 십자 커서를 그 위치에 그린다.
    var eyedropperPos by remember { mutableStateOf<Offset?>(null) }
    val selectionColor = MaterialTheme.colorScheme.primary
    val isSticker = tool.kind == ToolKind.Sticker
    val isEyedropper = tool.kind == ToolKind.Eyedropper

    // 완료된 stroke 비트맵 캐시 — contentRevision(완료 stroke 추가/제거/Clear/snapshot) 또는 캔버스
    // 크기가 바뀔 때만 다시 렌더. 색·도구 변경이나 진행 중 stroke 프레임에선 캐시를 그대로 재사용해
    // 매번 전체 벡터 재그리기를 피한다. 배경은 캐시하지 않음(트레이싱 알파가 표시마다 달라질 수 있어 라이브).
    val committedStrokes: ImageBitmap? = remember(state.contentRevision, canvasSize, density) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) {
            null
        } else {
            renderCommittedStrokes(state, canvasSize, density)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(tool.kind) {
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
                } else if (isEyedropper) {
                    // 스포이드 — 누른 채 드래그하면 조준 십자가 따라오고, 떼는 순간 그 지점 색을 집는다.
                    // 손가락에 가린 지점을 십자로 확인하며 정확히 조준 가능. selectColor 가 펜으로 전환.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        eyedropperPos = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            var pressed = false
                            event.changes.forEach { c ->
                                if (c.pressed) pressed = true
                                if (c.positionChanged()) {
                                    eyedropperPos = c.position
                                    c.consume()
                                }
                            }
                            if (!pressed) break
                        }
                        eyedropperPos?.let { p ->
                            val n = p.toNormalized(size)
                            onPickColor(n.x, n.y)
                        }
                        eyedropperPos = null
                    }
                } else {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        val strokeId = StrokeId.random()
                        onStrokeStart(strokeId, first.position.toNormalized(size))
                        cursor = first.position
                        first.consume()

                        // 지수이동평균(EMA) 상태 — 첫 점에서 시작. alpha=1f 면 원본 그대로.
                        val alpha = smoothingAlpha.coerceIn(0.05f, 1f)
                        var smoothed = first.position

                        val pending = mutableListOf<DrawPoint>()
                        while (true) {
                            val event = awaitPointerEvent()
                            var anyPressed = false
                            event.changes.forEach { change: PointerInputChange ->
                                if (change.pressed) anyPressed = true
                                if (change.positionChanged()) {
                                    smoothed += (change.position - smoothed) * alpha
                                    pending.add(smoothed.toNormalized(size))
                                    cursor = smoothed
                                    change.consume()
                                }
                            }
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
        // 0. 캔버스 배경색 (기본 흰색). 사진이 있으면 그 아래 깔린다.
        drawRect(color = Color(state.backgroundColor))
        // 1. 사진 배경 (있으면). 트레이싱 보조 알파 적용 — 표시만, 저장 PNG 엔 영향 없음. (라이브, 캐시 안 함)
        state.background?.bitmap?.let { bg ->
            drawImage(
                image = bg,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bg.width, bg.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(canvasSize.width, canvasSize.height),
                alpha = backgroundAlpha,
            )
        }
        // 2. 완료된 stroke — 캐시 비트맵으로 한 번에. (없으면 첫 프레임 등 — 폴백 직접 그리기)
        val cached = committedStrokes
        if (cached != null) {
            drawImage(cached)
        } else {
            state.strokes.forEach { drawStroke(it, canvasSize, density) }
        }
        // 3. 진행 중 stroke (라이브)
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
        // 8. 스포이드 조준 십자 (스포이드 모드 + 누르는 중)
        eyedropperPos?.let { pos ->
            drawEyedropperCursor(center = pos, density = density)
        }
    }
}

// 스포이드 조준 십자 — 중앙에 빈 틈을 둔 십자 + 링. 어떤 배경에서도 보이게 흰 외곽선 위에
// 검은 선을 겹쳐 그린다. 중앙 틈으로 정확히 어느 픽셀을 집는지 가려지지 않게 한다.
private fun DrawScope.drawEyedropperCursor(center: Offset, density: Float) {
    val arm = 14f * density
    val gap = 5f * density
    val ring = 9f * density

    fun cross(color: Color, w: Float) {
        drawLine(color, Offset(center.x - arm, center.y), Offset(center.x - gap, center.y), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(center.x + gap, center.y), Offset(center.x + arm, center.y), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(center.x, center.y - arm), Offset(center.x, center.y - gap), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(center.x, center.y + gap), Offset(center.x, center.y + arm), strokeWidth = w, cap = StrokeCap.Round)
        drawCircle(color = color, radius = ring, center = center, style = DrawStroke(width = w))
    }
    cross(Color.White.copy(alpha = 0.9f), 3f * density)
    cross(Color.Black.copy(alpha = 0.85f), 1.5f * density)
}

// 완료된 stroke 만 투명 배경 비트맵에 렌더 — DrawingCanvas 의 캐시 레이어. 배경(사진/색)은
// 포함하지 않는다(라이브로 그려야 트레이싱 알파가 반영됨). contentRevision/크기 변경 시에만 호출.
private fun renderCommittedStrokes(
    state: CanvasState,
    canvasSize: IntSize,
    density: Float,
): ImageBitmap {
    val image = ImageBitmap(canvasSize.width, canvasSize.height)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    CanvasDrawScope().draw(
        density = Density(density),
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
    ) {
        state.strokes.forEach { drawStroke(it, canvasSize, density) }
    }
    return image
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
