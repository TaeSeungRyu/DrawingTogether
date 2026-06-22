package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.unit.IntSize
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushCapStyle
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushType
import com.rts.rys.ryy.drawingtogether.drawing.model.ShapeMode
import com.rts.rys.ryy.drawingtogether.drawing.model.Stroke
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolSettings

// stroke 한 개를 그린다. 화면 렌더링과 PNG 합성 양쪽에서 같은 함수를 사용 — 보이는 것 = 저장되는 것.
// shape가 None이면 폴리라인, 그 외엔 첫·마지막 점 바운딩 박스 도형.
//
// isRemote=true 면 알파를 추가로 감쇠해 "원격 작성자의 참고용 stroke" 처럼 보이게 함 (Phase 3-A).
// PngComposer 는 기본값(false) 그대로 호출 — 저장 PNG 는 모든 stroke 동일 가시.
internal fun DrawScope.drawStroke(
    stroke: Stroke,
    canvasSize: IntSize,
    density: Float,
    isRemote: Boolean = false,
) {
    if (stroke.points.isEmpty() || canvasSize.width <= 0 || canvasSize.height <= 0) return
    when (stroke.tool.shape) {
        ShapeMode.None -> drawFreehand(stroke, canvasSize, density, isRemote)
        else -> drawShapeForm(stroke, canvasSize, density, isRemote)
    }
}

private fun DrawScope.drawFreehand(stroke: Stroke, canvasSize: IntSize, density: Float, isRemote: Boolean) {
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
        color = colorFor(stroke.tool, isRemote),
        style = DrawStroke(
            width = strokeWidthPxFor(stroke.tool, density),
            cap = cap,
            join = join,
        ),
    )
}

// 첫 점과 마지막 점을 바운딩 박스로 삼아 정해진 도형 하나를 외곽선으로 그린다.
private fun DrawScope.drawShapeForm(stroke: Stroke, canvasSize: IntSize, density: Float, isRemote: Boolean) {
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

    val color = colorFor(stroke.tool, isRemote)
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

// 지우개는 더 이상 stroke을 만들지 않고 DrawingViewModel에서 자기 stroke을 삭제한다 —
// 렌더러는 펜 stroke만 그린다.
// isRemote: 원격 작성자 stroke 은 알파 0.65 곱셈으로 살짝 흐릿하게.
private const val REMOTE_ALPHA_MULTIPLIER = 0.65f
internal fun colorFor(tool: ToolSettings, isRemote: Boolean = false): Color {
    val alpha = tool.brush.alpha * if (isRemote) REMOTE_ALPHA_MULTIPLIER else 1f
    return Color(tool.colorArgb).copy(alpha = alpha)
}

internal fun strokeWidthPxFor(tool: ToolSettings, density: Float): Float =
    tool.strokeWidthDp * tool.brush.widthScale * density

internal fun capJoinFor(brush: BrushType): Pair<StrokeCap, StrokeJoin> = when (brush.capStyle) {
    BrushCapStyle.Square -> StrokeCap.Square to StrokeJoin.Miter
    BrushCapStyle.Round -> StrokeCap.Round to StrokeJoin.Round
}
