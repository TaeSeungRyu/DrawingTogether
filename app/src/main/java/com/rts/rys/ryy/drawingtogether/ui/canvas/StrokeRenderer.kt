package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
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
            else -> drawFreehand(stroke, canvasSize, density)
        }
        else -> drawShapeForm(stroke, canvasSize, density)
    }
}

// 폴리라인 Path 빌드 — drawFreehand / drawBlurred 공유.
// 점이 1개면 같은 좌표로 lineTo 해서 round cap 점(또는 blur 점)이 찍히게 한다.
private fun buildFreehandPath(stroke: Stroke, canvasSize: IntSize): Path {
    val w = canvasSize.width.toFloat()
    val h = canvasSize.height.toFloat()
    return Path().apply {
        val first = stroke.points.first()
        moveTo(first.x * w, first.y * h)
        if (stroke.points.size == 1) {
            lineTo(first.x * w, first.y * h)
        } else {
            for (i in 1 until stroke.points.size) {
                val p = stroke.points[i]
                lineTo(p.x * w, p.y * h)
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

internal fun colorFor(tool: ToolSettings): Color =
    Color(tool.colorArgb).copy(alpha = tool.brush.alpha)

internal fun strokeWidthPxFor(tool: ToolSettings, density: Float): Float =
    tool.strokeWidthDp * tool.brush.widthScale * density

internal fun capJoinFor(brush: BrushType): Pair<StrokeCap, StrokeJoin> = when (brush.capStyle) {
    BrushCapStyle.Square -> StrokeCap.Square to StrokeJoin.Miter
    BrushCapStyle.Round -> StrokeCap.Round to StrokeJoin.Round
}
