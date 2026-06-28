package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushCapStyle
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushType
import com.rts.rys.ryy.drawingtogether.drawing.model.ShapeMode
import com.rts.rys.ryy.drawingtogether.drawing.model.Stroke
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolSettings

// stroke 한 개를 그린다. 화면 렌더링과 PNG 합성 양쪽에서 같은 함수를 사용 — 보이는 것 = 저장되는 것.
// shape가 None이면 폴리라인, 그 외엔 첫·마지막 점 바운딩 박스 도형.
// "함께 그리기" 단일 모드 — 자기/상대 stroke 동등 가시.
internal fun DrawScope.drawStroke(stroke: Stroke, canvasSize: IntSize, density: Float) {
    if (stroke.points.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return
    when (stroke.tool.shape) {
        ShapeMode.None -> when (stroke.tool.brush) {
            BrushType.Airbrush -> drawAirbrush(stroke, canvasSize, density)
            BrushType.Blur -> drawBlurred(stroke, canvasSize, density)
            BrushType.Neon -> drawNeon(stroke, canvasSize, density)
            BrushType.Dash -> drawDashed(stroke, canvasSize, density)
            BrushType.Rainbow -> drawRainbow(stroke, canvasSize, density)
            BrushType.Calligraphy -> drawCalligraphy(stroke, canvasSize, density)
            else -> drawFreehand(stroke, canvasSize, density)
        }
        else -> drawShapeForm(stroke, canvasSize, density)
    }
}

// 부드러운 Path 빌드 — drawFreehand / drawBlurred / drawNeon / drawDashed 공유.
// 직선 lineTo 폴리라인 대신 인접 점의 중간점을 잇는 2차 베지어로 그려 꺾이는 부분을 둥글게 한다
// (각 점은 control point, 중간점이 곡선 끝). 점이 1~2개면 점/직선으로 폴백.
private fun buildFreehandPath(stroke: Stroke, canvasSize: IntSize): Path {
    val w = canvasSize.width.toFloat()
    val h = canvasSize.height.toFloat()
    val pts = stroke.points
    return Path().apply {
        val first = pts.first()
        moveTo(first.x * w, first.y * h)
        when (pts.size) {
            1 -> lineTo(first.x * w, first.y * h)
            2 -> lineTo(pts[1].x * w, pts[1].y * h)
            else -> {
                for (i in 1 until pts.size - 1) {
                    val c = pts[i]
                    val next = pts[i + 1]
                    val midX = (c.x + next.x) / 2f * w
                    val midY = (c.y + next.y) / 2f * h
                    quadraticBezierTo(c.x * w, c.y * h, midX, midY)
                }
                val last = pts.last()
                lineTo(last.x * w, last.y * h)
            }
        }
    }
}

private fun DrawScope.drawFreehand(stroke: Stroke, canvasSize: IntSize, density: Float) {
    val path = buildFreehandPath(stroke, canvasSize)
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

// 번짐 — Android BlurMaskFilter 로 가장자리를 부드럽게. Compose drawScope 에서 native
// canvas 접근(drawIntoCanvas + nativeCanvas). PngComposer 의 ImageBitmap canvas 에서도 동작.
private fun DrawScope.drawBlurred(stroke: Stroke, canvasSize: IntSize, density: Float) {
    val androidPath = buildFreehandPath(stroke, canvasSize).asAndroidPath()
    val widthPx = strokeWidthPxFor(stroke.tool, density)
    val blurRadius = (widthPx * 0.5f).coerceAtLeast(1f)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = widthPx
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        color = colorFor(stroke.tool).toArgb()
        maskFilter = android.graphics.BlurMaskFilter(
            blurRadius,
            android.graphics.BlurMaskFilter.Blur.NORMAL,
        )
    }
    drawIntoCanvas { it.nativeCanvas.drawPath(androidPath, paint) }
}

// 에어브러시 — 경로를 따라 점을 흩뿌린다. 결정론적: seed = stroke.id 라 매 프레임/양 단말
// 동일하게 재현(난수가 흔들리지 않음). 분사점 좌표를 stroke 에 저장하지 않고 렌더 시 생성.
private fun DrawScope.drawAirbrush(stroke: Stroke, canvasSize: IntSize, density: Float) {
    val w = canvasSize.width.toFloat()
    val h = canvasSize.height.toFloat()
    val radius = strokeWidthPxFor(stroke.tool, density) / 2f
    if (radius <= 0f) return
    val color = colorFor(stroke.tool)
    val dotRadius = (1.2f * density).coerceAtLeast(1f)
    val spacing = (radius * 0.5f).coerceAtLeast(2f)
    val perStamp = 8
    val rng = kotlin.random.Random(stroke.id.value.hashCode())

    // 한 stamp 위치에서 반경 안에 perStamp 개 점 분사.
    fun spray(cx: Float, cy: Float) {
        for (k in 0 until perStamp) {
            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            // sqrt 로 반경 균일 분포 (중심 쏠림 방지).
            val dist = kotlin.math.sqrt(rng.nextFloat()) * radius
            val x = cx + dist * kotlin.math.cos(angle)
            val y = cy + dist * kotlin.math.sin(angle)
            drawCircle(color = color, radius = dotRadius, center = Offset(x, y))
        }
    }

    val pts = stroke.points
    if (pts.size == 1) {
        spray(pts[0].x * w, pts[0].y * h)
        return
    }
    // 인접 점 사이를 spacing 간격으로 보간하며 stamp.
    for (i in 0 until pts.size - 1) {
        val x1 = pts[i].x * w; val y1 = pts[i].y * h
        val x2 = pts[i + 1].x * w; val y2 = pts[i + 1].y * h
        val dx = x2 - x1; val dy = y2 - y1
        val segLen = kotlin.math.sqrt(dx * dx + dy * dy)
        val steps = (segLen / spacing).toInt().coerceAtLeast(1)
        for (s in 0 until steps) {
            val t = s.toFloat() / steps
            spray(x1 + dx * t, y1 + dy * t)
        }
    }
    // 마지막 점도 분사.
    spray(pts.last().x * w, pts.last().y * h)
}

// 네온 — 굵게 번진 발광 후광(native BlurMaskFilter) 위에 밝은 코어 선을 겹친다.
// 어두운 배경에서 빛나 보임. 후광·코어 모두 결정론적이라 PNG 합성에서도 동일.
private fun DrawScope.drawNeon(stroke: Stroke, canvasSize: IntSize, density: Float) {
    val composePath = buildFreehandPath(stroke, canvasSize)
    val androidPath = composePath.asAndroidPath()
    val widthPx = strokeWidthPxFor(stroke.tool, density)
    val base = Color(stroke.tool.colorArgb)

    fun glow(strokeW: Float, blur: Float, alpha: Float) {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            color = base.copy(alpha = alpha).toArgb()
            maskFilter = android.graphics.BlurMaskFilter(
                blur.coerceAtLeast(1f),
                android.graphics.BlurMaskFilter.Blur.NORMAL,
            )
        }
        drawIntoCanvas { it.nativeCanvas.drawPath(androidPath, paint) }
    }

    glow(widthPx * 2.4f, widthPx, 0.35f)
    glow(widthPx * 1.4f, widthPx * 0.5f, 0.5f)
    // 밝은 코어 — 색을 흰색 쪽으로 당겨 빛나는 심지.
    drawPath(
        path = composePath,
        color = lerp(base, Color.White, 0.6f),
        style = DrawStroke(
            width = (widthPx * 0.5f).coerceAtLeast(1f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

// 점선 — 일반 폴리라인에 dashPathEffect 적용. on/off 간격을 굵기에 비례시켜 굵을수록 성긴 파선.
private fun DrawScope.drawDashed(stroke: Stroke, canvasSize: IntSize, density: Float) {
    val path = buildFreehandPath(stroke, canvasSize)
    val widthPx = strokeWidthPxFor(stroke.tool, density)
    val dash = (widthPx * 2f).coerceAtLeast(4f)
    drawPath(
        path = path,
        color = colorFor(stroke.tool),
        style = DrawStroke(
            width = widthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), 0f),
        ),
    )
}

// 무지개 — 경로 누적 길이에 따라 색상(hue)을 회전시키며 구간별로 그린다.
// 구간마다 색이 달라 한 Path 로 못 그리므로, 각 조각을 중간점-2차 베지어로 그려 꺾임을 둥글게 한다.
// colorArgb 는 무시. 색 시작 위상만 stroke.id 로 결정론화 → 매 프레임·양 단말 동일.
private fun DrawScope.drawRainbow(stroke: Stroke, canvasSize: IntSize, density: Float) {
    val w = canvasSize.width.toFloat()
    val h = canvasSize.height.toFloat()
    val widthPx = strokeWidthPxFor(stroke.tool, density)
    val p = stroke.points.map { Offset(it.x * w, it.y * h) }
    val phase = (stroke.id.value.hashCode() % 360 + 360) % 360
    // hue 가 한 바퀴 도는 기준 길이(px) — 화면 폭의 절반 정도마다 한 바퀴.
    val cycle = (w * 0.5f).coerceAtLeast(1f)

    if (p.size == 1) {
        drawCircle(
            color = Color.hsv(phase.toFloat(), 1f, 1f),
            radius = (widthPx / 2f).coerceAtLeast(1f),
            center = p[0],
        )
        return
    }
    forEachSmoothPiece(p) { start, ctrl, end, lenAtCtrl ->
        val hue = (phase + lenAtCtrl / cycle * 360f) % 360f
        drawSmoothPiece(start, ctrl, end, Color.hsv(hue, 1f, 1f), widthPx)
    }
}

// 붓펜(속도 기반 굵기) — 인접 점 간 거리를 속도로 보고 조각별 굵기를 변조한다.
// 빠르면(거리 큼) 가늘게, 느리면 굵게. 조각은 중간점-2차 베지어로 그려 꺾임을 둥글게.
private fun DrawScope.drawCalligraphy(stroke: Stroke, canvasSize: IntSize, density: Float) {
    val w = canvasSize.width.toFloat()
    val h = canvasSize.height.toFloat()
    val color = colorFor(stroke.tool)
    val maxW = strokeWidthPxFor(stroke.tool, density)
    val minW = (maxW * 0.25f).coerceAtLeast(1f)
    val refDist = 40f * density // 이 거리 이상 빠르면 최소 굵기.
    val p = stroke.points.map { Offset(it.x * w, it.y * h) }

    if (p.size == 1) {
        drawCircle(color = color, radius = maxW / 2f, center = p[0])
        return
    }
    forEachSmoothPiece(p) { start, ctrl, end, _ ->
        // 조각 길이(start→ctrl→end)를 속도 대용으로 사용.
        val dist = kotlin.math.hypot(ctrl.x - start.x, ctrl.y - start.y) +
            kotlin.math.hypot(end.x - ctrl.x, end.y - ctrl.y)
        val speed = (dist / refDist).coerceIn(0f, 1f)
        drawSmoothPiece(start, ctrl, end, color, lerpFloat(maxW, minW, speed))
    }
}

// 중간점-2차 베지어로 경로를 조각낸다. 각 조각: 이전 중간점(start) → 점 p[i](control) → 다음 중간점(end).
// 양 끝은 실제 끝점으로 마감. block 에 누적 길이(control 점까지)를 함께 넘겨 색/굵기 변조에 쓰게 한다.
// 조각 단위라야 색·굵기를 구간마다 바꿀 수 있음(단색·균일 굵기는 buildFreehandPath 한 방으로 충분).
private inline fun forEachSmoothPiece(
    p: List<Offset>,
    block: (start: Offset, ctrl: Offset, end: Offset, lenAtCtrl: Float) -> Unit,
) {
    val n = p.size
    if (n < 2) return
    var prevMid = p[0]
    var acc = 0f
    for (i in 1 until n - 1) {
        acc += kotlin.math.hypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y)
        val mid = Offset((p[i].x + p[i + 1].x) / 2f, (p[i].y + p[i + 1].y) / 2f)
        block(prevMid, p[i], mid, acc)
        prevMid = mid
    }
    // 마지막 조각 — 마지막 중간점(또는 첫 점)에서 끝점까지. control = 끝점(직선처럼).
    acc += kotlin.math.hypot(p[n - 1].x - p[n - 2].x, p[n - 1].y - p[n - 2].y)
    block(prevMid, p[n - 1], p[n - 1], acc)
}

private fun DrawScope.drawSmoothPiece(start: Offset, ctrl: Offset, end: Offset, color: Color, width: Float) {
    val piece = Path().apply {
        moveTo(start.x, start.y)
        quadraticBezierTo(ctrl.x, ctrl.y, end.x, end.y)
    }
    drawPath(
        path = piece,
        color = color,
        style = DrawStroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

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
    // 채우기면 Fill, 아니면 외곽선 Stroke. 둘 다 DrawStyle 이라 같은 style 인자로 넘긴다.
    val style: DrawStyle = if (stroke.tool.fill) {
        Fill
    } else {
        DrawStroke(
            width = strokeWidthPxFor(stroke.tool, density),
            cap = cap,
            join = join,
        )
    }

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

internal fun colorFor(tool: ToolSettings): Color =
    Color(tool.colorArgb).copy(alpha = tool.brush.alpha)

internal fun strokeWidthPxFor(tool: ToolSettings, density: Float): Float =
    tool.strokeWidthDp * tool.brush.widthScale * density

internal fun capJoinFor(brush: BrushType): Pair<StrokeCap, StrokeJoin> = when (brush.capStyle) {
    BrushCapStyle.Square -> StrokeCap.Square to StrokeJoin.Miter
    BrushCapStyle.Round -> StrokeCap.Round to StrokeJoin.Round
}
