package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.rts.rys.ryy.drawingtogether.drawing.model.TextElement
import com.rts.rys.ryy.drawingtogether.drawing.model.TextId
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
    // 트레이싱 외곽선 모드 — non-null 이면 사진 대신 추출한 외곽선 오버레이를 그린다. 로컬 표시만.
    edgeOverlay: ImageBitmap? = null,
    // 스포이드 — 탭한 정규화 좌표의 색을 집는다. 스포이드 모드에서만 호출.
    onPickColor: (Float, Float) -> Unit = { _, _ -> },
    // 스티커 편집 콜백 — 스티커 모드에서만 호출.
    onPlaceSticker: (Float, Float) -> StickerId? = { _, _ -> null },
    onTransformStickerLocal: (StickerId, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    onCommitStickerTransform: (StickerId, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    onRemoveSticker: (StickerId) -> Unit = {},
    // 텍스트 콜백 — 텍스트 모드에서만 호출. 빈 곳 탭 → 그 위치에 입력 시트 요청, X 핸들 → 삭제.
    onRequestText: (Float, Float) -> Unit = { _, _ -> },
    onRemoveText: (TextId) -> Unit = {},
    // 입력 시트가 열린 동안 텍스트가 들어갈 정규화 좌표(0..1). non-null 이면 그 위치에 캐럿 마커를 그린다.
    pendingTextPoint: Pair<Float, Float>? = null,
    // 줌 뷰포트 — 로컬 표시 전용. scale=1f/offset=Zero 면 기존과 동일(변화 없음).
    scale: Float = 1f,
    offset: Offset = Offset.Zero,
    onViewportChange: (Float, Offset) -> Unit = { _, _ -> },
    // 그리기 중 두 번째 손가락 감지 → 진행 stroke 취소(줌/이동으로 전환).
    onStrokeCancel: (StrokeId) -> Unit = {},
    // 캔버스 짧은변(dp)이 측정/변경될 때 알림 — 저장 시 굵기 정규화용(#1). 화면 표시엔 미사용.
    onCanvasShortDpChange: (Float) -> Unit = {},
) {
    val density = LocalDensity.current.density
    // pointerInput(tool.kind) 은 scale/offset 변화로 재시작하지 않으므로, 제스처 코루틴에서
    // 항상 최신 뷰포트를 읽도록 rememberUpdatedState 로 감싼다.
    val scaleState = rememberUpdatedState(scale)
    val offsetState = rememberUpdatedState(offset)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // 그리는 동안 현재 포인터 위치 — 펜/지우개 두께 미리보기용. 손가락 떼면 null.
    var cursor by remember { mutableStateOf<Offset?>(null) }
    // 선택된 스티커. 스티커 모드에서만 의미. 모드를 벗어나면 오버레이/핸들이 숨겨진다.
    var selectedStickerId by remember { mutableStateOf<StickerId?>(null) }
    // 스포이드 조준 위치 — 누르는 동안만 non-null. 십자 커서를 그 위치에 그린다.
    var eyedropperPos by remember { mutableStateOf<Offset?>(null) }
    // 선택된 텍스트. 텍스트 모드에서만 의미 — 선택 시 삭제(X) 핸들이 뜬다.
    var selectedTextId by remember { mutableStateOf<TextId?>(null) }
    val selectionColor = MaterialTheme.colorScheme.primary
    val isSticker = tool.kind == ToolKind.Sticker
    val isEyedropper = tool.kind == ToolKind.Eyedropper
    val isText = tool.kind == ToolKind.Text

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
            .onSizeChanged {
                canvasSize = it
                val shortPx = min(it.width, it.height)
                if (shortPx > 0 && density > 0f) onCanvasShortDpChange(shortPx / density)
            }
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
                        scale = { scaleState.value },
                        offset = { offsetState.value },
                    )
                } else if (isText) {
                    textGestures(
                        texts = { state.texts },
                        density = density,
                        selectedId = { selectedTextId },
                        setSelected = { selectedTextId = it },
                        onRequestText = onRequestText,
                        onRemove = onRemoveText,
                        scale = { scaleState.value },
                        offset = { offsetState.value },
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
                            // 화면좌표 → 콘텐츠좌표 후 정규화(줌 반영).
                            val n = p.screenToContent(scaleState.value, offsetState.value).toNormalized(size)
                            onPickColor(n.x, n.y)
                        }
                        eyedropperPos = null
                    }
                } else {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        first.consume()
                        val strokeId = StrokeId.random()
                        var drawing = true
                        onStrokeStart(
                            strokeId,
                            first.position.screenToContent(scaleState.value, offsetState.value).toNormalized(size),
                        )
                        cursor = first.position
                        try {
                            // 지수이동평균(EMA) 상태 — 첫 점에서 시작(화면좌표). alpha=1f 면 원본 그대로.
                            val alpha = smoothingAlpha.coerceIn(0.05f, 1f)
                            var smoothed = first.position

                            val pending = mutableListOf<DrawPoint>()
                            while (true) {
                                val event = awaitPointerEvent()

                                // 두 번째 손가락 감지 → 진행 stroke 취소하고 줌/이동(pan/zoom)으로 전환.
                                if (drawing && event.changes.count { it.pressed } >= 2) {
                                    onStrokeCancel(strokeId)
                                    cursor = null
                                    pending.clear()
                                    drawing = false
                                }

                                if (drawing) {
                                    var anyPressed = false
                                    event.changes.forEach { change: PointerInputChange ->
                                        if (change.pressed) anyPressed = true
                                        if (change.positionChanged()) {
                                            smoothed += (change.position - smoothed) * alpha
                                            pending.add(
                                                smoothed.screenToContent(scaleState.value, offsetState.value)
                                                    .toNormalized(size),
                                            )
                                            cursor = smoothed
                                            change.consume()
                                        }
                                    }
                                    if (pending.isNotEmpty()) {
                                        onStrokeAppend(strokeId, pending.toList())
                                        pending.clear()
                                    }
                                    if (!anyPressed) break
                                } else {
                                    // 줌/이동 모드 — 두 손가락 핀치=확대(centroid 고정), 드래그=이동.
                                    val zoom = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    if (centroid != Offset.Unspecified && (zoom != 1f || panChange != Offset.Zero)) {
                                        val (ns, no) = zoomAround(
                                            scaleState.value, offsetState.value, centroid, zoom, panChange, size,
                                        )
                                        onViewportChange(ns, no)
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    if (event.changes.none { it.pressed }) break
                                }
                            }

                            if (drawing) {
                                onStrokeEnd(strokeId)
                                cursor = null
                                drawing = false
                            }
                        } finally {
                            // 툴 변경(pointerInput 재시작)·컴포저블 이탈로 제스처가 취소되면 정상 종료 코드가
                            // 실행되지 않아 openStroke 이 영구 잔존한다(#6). 취소 시에도 여기서 정리.
                            if (drawing) {
                                onStrokeCancel(strokeId)
                                cursor = null
                            }
                        }
                    }
                }
            }
    ) {
        // 월드 레이어(배경~선택 핸들·마커)는 뷰포트 변환 안에서 그린다 — 확대/이동이 한 번에 적용됨.
        // 커서/스포이드 십자만 변환 밖(화면좌표)에서 일정 크기로 그린다.
        withTransform({
            translate(offset.x, offset.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            // 0. 캔버스 배경색 (기본 흰색). 사진이 있으면 그 아래 깔린다.
            drawRect(color = Color(state.backgroundColor))
            // 1. 사진 배경 (있으면). 트레이싱 보조 알파 적용 — 표시만, 저장 PNG 엔 영향 없음. (라이브, 캐시 안 함)
            //    외곽선 모드(edgeOverlay non-null)면 사진 대신 추출한 라인만 그린다. 계산 전이면 사진을 alpha 로 표시.
            state.background?.bitmap?.let { bg ->
                drawImage(
                    image = edgeOverlay ?: bg,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize((edgeOverlay ?: bg).width, (edgeOverlay ?: bg).height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(canvasSize.width, canvasSize.height),
                    alpha = if (edgeOverlay != null) 1f else backgroundAlpha,
                )
            }
            // 2. 완료된 stroke — 1배율이면 캐시 비트맵, 확대 중이면 벡터 직접 렌더(또렷하게).
            val cached = committedStrokes
            if (cached != null && scale == 1f) {
                drawImage(cached)
            } else {
                state.strokes.forEach { drawStroke(it, canvasSize, density) }
            }
            // 3. 진행 중 stroke (라이브)
            state.openStrokes.values.forEach { drawStroke(it, canvasSize, density) }
            // 4. 스티커 (stroke 위)
            state.stickers.forEach { drawSticker(it, canvasSize) }
            // 4.5 텍스트 (스티커 위)
            state.texts.forEach { drawText(it, canvasSize) }
            // 5. 안내선 (stroke/스티커 위, 커서 아래)
            drawGuides(canvasSize, density, guideCross, guideGridCells)
            // 6. 스티커 선택 핸들 (스티커 모드 + 선택됨) — 핸들 크기는 viewScale 로 나눠 화면상 일정.
            if (isSticker) {
                val sel = state.stickers.firstOrNull { it.id == selectedStickerId }
                if (sel != null) drawStickerSelection(sel, canvasSize, density, selectionColor, scale)
            }
            // 6.5 텍스트 선택 핸들 (텍스트 모드 + 선택됨)
            if (isText) {
                val sel = state.texts.firstOrNull { it.id == selectedTextId }
                if (sel != null) drawTextSelection(sel, canvasSize, density, selectionColor, scale)
            }
            // 9. 텍스트 입력 위치 마커 (입력 시트가 열린 동안) — 텍스트가 들어갈 자리에 I-beam 캐럿.
            pendingTextPoint?.let { (nx, ny) ->
                drawTextPlacementMarker(
                    center = Offset(nx * canvasSize.width, ny * canvasSize.height),
                    canvasSize = canvasSize,
                    density = density,
                    color = selectionColor,
                    viewScale = scale,
                )
            }
        }
        // 7. 커서 인디케이터 (그리기 모드) — 화면좌표. 확대된 붓 footprint 를 보이려 반경에 scale 곱.
        cursor?.let { pos ->
            drawBrushIndicator(center = pos, tool = tool, density = density, viewScale = scale)
        }
        // 8. 스포이드 조준 십자 (스포이드 모드 + 누르는 중) — 화면좌표, 일정 크기.
        eyedropperPos?.let { pos ->
            drawEyedropperCursor(center = pos, density = density)
        }
    }
}

// 텍스트 입력 위치 마커 — 중심(cx,cy)에 세로 I-beam 캐럿(위·아래 가로 세리프 포함).
// 텍스트가 중앙 정렬로 이 지점에 들어감을 알린다. 어떤 배경에서도 보이게 흰 외곽선 위 색 캐럿.
private fun DrawScope.drawTextPlacementMarker(
    center: Offset,
    canvasSize: IntSize,
    density: Float,
    color: Color,
    viewScale: Float = 1f,
) {
    val shortSide = min(canvasSize.width, canvasSize.height).toFloat()
    // 기본 텍스트 크기(보통, sizeFrac=0.06) 높이에 맞춘 캐럿. 세리프·선폭은 viewScale 로 나눠 화면상 일정.
    val half = (shortSide * 0.06f).coerceAtLeast(8f * density) / 2f
    val serif = 6f * density / viewScale
    val top = center.y - half
    val bottom = center.y + half

    fun ibeam(c: Color, w: Float) {
        drawLine(c, Offset(center.x, top), Offset(center.x, bottom), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(c, Offset(center.x - serif, top), Offset(center.x + serif, top), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(c, Offset(center.x - serif, bottom), Offset(center.x + serif, bottom), strokeWidth = w, cap = StrokeCap.Round)
    }
    ibeam(Color.White.copy(alpha = 0.9f), 4f * density / viewScale)
    ibeam(color, 2f * density / viewScale)
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
    scale: () -> Float,
    offset: () -> Offset,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val w = size.width.toFloat().coerceAtLeast(1f)
        val h = size.height.toFloat().coerceAtLeast(1f)
        val shortSide = min(size.width, size.height).toFloat().coerceAtLeast(1f)
        val handleRadius = 18f * density
        val list = stickers()
        // 화면좌표 → 콘텐츠좌표(줌 반영). 스티커 모드에선 줌이 변하지 않아 시작 시 1회 읽으면 충분.
        val sc = scale()
        val off = offset()
        val downPos = down.position.screenToContent(sc, off)

        val selected = list.firstOrNull { it.id == selectedId() }

        // 1. 선택된 스티커의 핸들 우선 검사.
        if (selected != null) {
            val local = toLocal(selected, downPos, w, h)
            val halfPx = selected.scale * shortSide / 2f
            // 핸들 히트 반경은 화면 기준이므로 콘텐츠좌표에선 /sc.
            val hitR = handleRadius / sc
            if (hypot(local.x - halfPx, local.y + halfPx) <= hitR) {
                // X 핸들 (우상단) — 삭제.
                onRemove(selected.id)
                setSelected(null)
                drainGesture()
                return@awaitEachGesture
            }
            if (hypot(local.x - halfPx, local.y - halfPx) <= hitR) {
                // 크기/회전 핸들 (우하단).
                resizeRotateGesture(selected, w, h, shortSide, onTransformLocal, onCommit, sc, off)
                return@awaitEachGesture
            }
        }

        // 2. 본체 hit-test (위에 그려진 것 우선 = 역순).
        val hit = list.lastOrNull { s ->
            val local = toLocal(s, downPos, w, h)
            val halfPx = s.scale * shortSide / 2f
            kotlin.math.abs(local.x) <= halfPx && kotlin.math.abs(local.y) <= halfPx
        }

        val target: Sticker?
        if (hit != null) {
            setSelected(hit.id)
            target = hit
        } else {
            // 빈 곳 → 새 스티커 배치.
            val cx = (downPos.x / w).coerceIn(0f, 1f)
            val cy = (downPos.y / h).coerceIn(0f, 1f)
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
        moveGesture(target, downPos, w, h, onTransformLocal, onCommit, sc, off)
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
    scale: Float,
    offset: Offset,
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
        pos?.let { raw ->
            val p = raw.screenToContent(scale, offset) // 콘텐츠좌표(줌 반영).
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
    viewScale: Float,
    viewOffset: Offset,
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
        pos?.let { raw ->
            val p = raw.screenToContent(viewScale, viewOffset) // 콘텐츠좌표(줌 반영).
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
    viewScale: Float = 1f,
) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
    val shortSide = min(canvasSize.width, canvasSize.height).toFloat()
    val half = (sticker.scale * shortSide / 2f).coerceAtLeast(1f)
    val cx = sticker.cx * canvasSize.width
    val cy = sticker.cy * canvasSize.height
    // 핸들·선폭은 viewScale 로 나눠 확대해도 화면상 일정 크기 유지.
    val handleR = 12f * density / viewScale
    val line = 1.5f * density / viewScale

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

// 텍스트 편집 제스처 루프. 불변·삭제전용이라 이동/크기 변경 없음. 한 번의 down→up 마다:
//  - 선택된 텍스트의 X 핸들 → 삭제
//  - 다른 텍스트 본체 → 선택(X 핸들 표시)
//  - 빈 곳 → 그 위치에 입력 시트 요청 (실제 배치는 시트 확인 후)
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.textGestures(
    texts: () -> List<TextElement>,
    density: Float,
    selectedId: () -> TextId?,
    setSelected: (TextId?) -> Unit,
    onRequestText: (Float, Float) -> Unit,
    onRemove: (TextId) -> Unit,
    scale: () -> Float,
    offset: () -> Offset,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val w = size.width.toFloat().coerceAtLeast(1f)
        val h = size.height.toFloat().coerceAtLeast(1f)
        val sc = scale()
        val handleRadius = 18f * density / sc // 화면 기준 히트 반경을 콘텐츠좌표로.
        val list = texts()
        val downPos = down.position.screenToContent(sc, offset()) // 콘텐츠좌표(줌 반영).

        // 1. 선택된 텍스트의 X 핸들(우상단) 검사.
        val selected = list.firstOrNull { it.id == selectedId() }
        if (selected != null) {
            val (halfW, halfH) = textHalfSizePx(selected, size)
            val handleX = selected.cx * w + halfW
            val handleY = selected.cy * h - halfH
            if (hypot(downPos.x - handleX, downPos.y - handleY) <= handleRadius) {
                onRemove(selected.id)
                setSelected(null)
                drainGesture()
                return@awaitEachGesture
            }
        }

        // 2. 본체 hit-test (위에 그려진 것 우선 = 역순).
        val hit = list.lastOrNull { t ->
            val (halfW, halfH) = textHalfSizePx(t, size)
            val lx = downPos.x - t.cx * w
            val ly = downPos.y - t.cy * h
            kotlin.math.abs(lx) <= halfW && kotlin.math.abs(ly) <= halfH
        }
        if (hit != null) {
            setSelected(hit.id)
        } else {
            // 빈 곳 → 입력 시트 요청. 선택 해제.
            setSelected(null)
            val cx = (downPos.x / w).coerceIn(0f, 1f)
            val cy = (downPos.y / h).coerceIn(0f, 1f)
            onRequestText(cx, cy)
        }
        drainGesture()
    }
}

// 텍스트 선택 오버레이 — 바운딩 박스 + 우상단 삭제(X) 핸들. 회전 없음.
private fun DrawScope.drawTextSelection(
    text: TextElement,
    canvasSize: IntSize,
    density: Float,
    color: Color,
    viewScale: Float = 1f,
) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
    val (halfW, halfH) = textHalfSizePx(text, canvasSize)
    val cx = text.cx * canvasSize.width
    val cy = text.cy * canvasSize.height
    // 핸들·선폭·여백은 viewScale 로 나눠 확대해도 화면상 일정 크기 유지.
    val handleR = 12f * density / viewScale
    val line = 1.5f * density / viewScale
    val pad = 6f * density / viewScale

    drawRect(
        color = color,
        topLeft = Offset(cx - halfW - pad, cy - halfH - pad),
        size = Size((halfW + pad) * 2f, (halfH + pad) * 2f),
        style = DrawStroke(width = line),
    )
    // 삭제 핸들 (우상단) + ×.
    val hx = cx + halfW + pad
    val hy = cy - halfH - pad
    drawCircle(color = Color(0xFFE53935), radius = handleR, center = Offset(hx, hy))
    val d = handleR * 0.45f
    drawLine(Color.White, Offset(hx - d, hy - d), Offset(hx + d, hy + d), strokeWidth = line * 1.3f)
    drawLine(Color.White, Offset(hx - d, hy + d), Offset(hx + d, hy - d), strokeWidth = line * 1.3f)
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

private fun DrawScope.drawBrushIndicator(center: Offset, tool: ToolSettings, density: Float, viewScale: Float = 1f) {
    val sizePx = strokeWidthPxFor(tool, density) * viewScale
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
