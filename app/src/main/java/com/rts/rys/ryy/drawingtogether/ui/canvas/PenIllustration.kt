package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushType

// 각 브러시 타입의 실제 펜 모양을 가로로 누운 실루엣으로 그린다.
// 모든 펜은 팁이 오른쪽을 향함 — 일관된 방향성으로 시각 비교 쉽게.
@Composable
fun PenIllustration(brush: BrushType, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        when (brush) {
            BrushType.Pen -> drawBallpoint(w, h)
            BrushType.Pencil -> drawPencil(w, h)
            BrushType.Fine -> drawFineliner(w, h)
            BrushType.Ink -> drawInkPen(w, h)
            BrushType.Marker -> drawMarker(w, h)
            BrushType.Highlighter -> drawHighlighter(w, h)
            BrushType.Crayon -> drawCrayon(w, h)
            BrushType.Airbrush -> drawSprayCan(w, h)
            BrushType.Blur -> drawWaterDrop(w, h)
            BrushType.Neon -> drawNeonSign(w, h)
            BrushType.Dash -> drawDashIcon(w, h)
            BrushType.Rainbow -> drawRainbowArc(w, h)
            BrushType.Calligraphy -> drawBrushPen(w, h)
        }
    }
}

// 네온 — 발광 후광을 겹친 밝은 번개선.
private fun DrawScope.drawNeonSign(w: Float, h: Float) {
    val pts = listOf(
        Offset(w * 0.12f, h * 0.30f),
        Offset(w * 0.42f, h * 0.58f),
        Offset(w * 0.30f, h * 0.58f),
        Offset(w * 0.62f, h * 0.78f),
        Offset(w * 0.88f, h * 0.34f),
    )
    val glow = Color(0xFF00E5FF)
    fun pass(width: Float, color: Color) {
        for (i in 0 until pts.size - 1) {
            drawLine(color, pts[i], pts[i + 1], strokeWidth = width, cap = StrokeCap.Round)
        }
    }
    pass(h * 0.34f, glow.copy(alpha = 0.18f))
    pass(h * 0.20f, glow.copy(alpha = 0.35f))
    pass(h * 0.06f, Color.White)
}

// 점선 — 가로 파선.
private fun DrawScope.drawDashIcon(w: Float, h: Float) {
    val y = h * 0.5f
    val seg = w * 0.16f
    val gap = w * 0.10f
    var x = w * 0.08f
    while (x + seg <= w * 0.94f) {
        drawLine(
            color = Color(0xFF5E35B1),
            start = Offset(x, y),
            end = Offset(x + seg, y),
            strokeWidth = h * 0.18f,
            cap = StrokeCap.Round,
        )
        x += seg + gap
    }
}

// 무지개 — 색 밴드 아크.
private fun DrawScope.drawRainbowArc(w: Float, h: Float) {
    val bands = 6
    val bandW = h * 0.10f
    val cx = w * 0.5f
    val cy = h * 0.92f
    val outer = minOf(w * 0.42f, h * 0.78f)
    for (i in 0 until bands) {
        val radius = outer - i * bandW
        if (radius <= 0f) break
        drawArc(
            color = Color.hsv(i * 50f, 0.9f, 1f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = bandW * 0.9f),
        )
    }
}

// 붓펜 — 손잡이 + 뾰족한 붓끝.
private fun DrawScope.drawBrushPen(w: Float, h: Float) {
    val bodyTop = h * 0.34f
    val bodyHeight = h * 0.32f
    val bodyEnd = w * 0.60f
    drawRoundRect(
        color = Color(0xFF37474F),
        topLeft = Offset(0f, bodyTop),
        size = Size(bodyEnd, bodyHeight),
        cornerRadius = CornerRadius(bodyHeight * 0.4f),
    )
    // 금속 페룰.
    drawRect(
        color = Color(0xFFBCAAA4),
        topLeft = Offset(bodyEnd - w * 0.04f, bodyTop),
        size = Size(w * 0.08f, bodyHeight),
    )
    // 붓끝 — 점점 뾰족.
    val tip = Path().apply {
        moveTo(bodyEnd + w * 0.02f, bodyTop)
        lineTo(w * 0.97f, h * 0.5f)
        lineTo(bodyEnd + w * 0.02f, bodyTop + bodyHeight)
        close()
    }
    drawPath(tip, Color(0xFF263238))
}

// 번짐 — 물방울 + 번진 후광.
private fun DrawScope.drawWaterDrop(w: Float, h: Float) {
    val cx = w * 0.5f
    val cy = h * 0.5f
    val r = minOf(w, h) * 0.22f
    // 번진 후광 (점점 옅게 겹친 원).
    for (i in 3 downTo 1) {
        drawCircle(
            color = Color(0xFF4FC3F7).copy(alpha = 0.12f),
            radius = r * (1f + i * 0.35f),
            center = Offset(cx, cy),
        )
    }
    drawCircle(color = Color(0xFF29B6F6), radius = r, center = Offset(cx, cy))
}

// 스프레이 캔 — 둥근 몸통 + 노즐 + 분사점.
private fun DrawScope.drawSprayCan(w: Float, h: Float) {
    val bodyTop = h * 0.30f
    val bodyHeight = h * 0.42f
    val bodyLeft = w * 0.10f
    val bodyWidth = w * 0.42f
    val cornerR = CornerRadius(bodyHeight * 0.25f)
    drawRoundRect(
        color = Color(0xFF26A69A),
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = cornerR,
    )
    // 노즐
    drawRoundRect(
        color = Color(0xFF455A64),
        topLeft = Offset(bodyLeft + bodyWidth * 0.3f, bodyTop - h * 0.12f),
        size = Size(bodyWidth * 0.4f, h * 0.14f),
        cornerRadius = CornerRadius(h * 0.03f),
    )
    // 분사점 — 노즐 우측으로 흩뿌림
    val sx = bodyLeft + bodyWidth + w * 0.04f
    val rng = kotlin.random.Random(7)
    repeat(14) {
        val px = sx + rng.nextFloat() * w * 0.30f
        val py = h * 0.5f + (rng.nextFloat() - 0.5f) * h * 0.5f
        drawCircle(
            color = Color(0xFF26A69A).copy(alpha = 0.6f),
            radius = (h * 0.035f).coerceAtLeast(1.2f),
            center = Offset(px, py),
        )
    }
}

private fun DrawScope.drawBallpoint(w: Float, h: Float) {
    val bodyTop = h * 0.32f
    val bodyHeight = h * 0.36f
    val capEnd = w * 0.15f
    val bodyEnd = w * 0.72f
    val cornerR = CornerRadius(bodyHeight * 0.4f)

    drawRoundRect(
        color = Color(0xFF1565C0),
        topLeft = Offset(0f, bodyTop),
        size = Size(capEnd, bodyHeight),
        cornerRadius = cornerR,
    )
    drawRoundRect(
        color = Color(0xFF42A5F5),
        topLeft = Offset(capEnd - bodyHeight * 0.2f, bodyTop),
        size = Size(bodyEnd - (capEnd - bodyHeight * 0.2f), bodyHeight),
        cornerRadius = cornerR,
    )
    val tipPath = Path().apply {
        moveTo(bodyEnd, bodyTop + bodyHeight * 0.1f)
        lineTo(w * 0.95f, h * 0.5f)
        lineTo(bodyEnd, bodyTop + bodyHeight * 0.9f)
        close()
    }
    drawPath(tipPath, Color(0xFF37474F))
    drawCircle(
        color = Color.Black,
        radius = h * 0.05f,
        center = Offset(w * 0.96f, h * 0.5f),
    )
}

private fun DrawScope.drawPencil(w: Float, h: Float) {
    val bodyTop = h * 0.28f
    val bodyHeight = h * 0.44f
    val eraserEnd = w * 0.10f
    val ferruleEnd = eraserEnd + w * 0.05f
    val bodyEnd = w * 0.72f
    val woodEnd = w * 0.90f

    drawRoundRect(
        color = Color(0xFFEF5350),
        topLeft = Offset(0f, bodyTop),
        size = Size(eraserEnd, bodyHeight),
        cornerRadius = CornerRadius(bodyHeight * 0.3f),
    )
    drawRect(
        color = Color(0xFFB0BEC5),
        topLeft = Offset(eraserEnd, bodyTop),
        size = Size(w * 0.05f, bodyHeight),
    )
    drawRect(
        color = Color(0xFF78909C),
        topLeft = Offset(eraserEnd, bodyTop + bodyHeight * 0.4f),
        size = Size(w * 0.05f, bodyHeight * 0.10f),
    )
    drawRect(
        color = Color(0xFFFBC02D),
        topLeft = Offset(ferruleEnd, bodyTop),
        size = Size(bodyEnd - ferruleEnd, bodyHeight),
    )
    val woodPath = Path().apply {
        moveTo(bodyEnd, bodyTop)
        lineTo(woodEnd, h * 0.42f)
        lineTo(woodEnd, h * 0.58f)
        lineTo(bodyEnd, bodyTop + bodyHeight)
        close()
    }
    drawPath(woodPath, Color(0xFFD7AB7A))
    val tipPath = Path().apply {
        moveTo(woodEnd, h * 0.42f)
        lineTo(w * 0.96f, h * 0.50f)
        lineTo(woodEnd, h * 0.58f)
        close()
    }
    drawPath(tipPath, Color(0xFF263238))
}

// 세밀붓 — 가늘고 긴 몸통 + 아주 뾰족한 바늘 팁(제도펜 느낌).
private fun DrawScope.drawFineliner(w: Float, h: Float) {
    val bodyTop = h * 0.40f
    val bodyHeight = h * 0.20f
    val capEnd = w * 0.16f
    val bodyEnd = w * 0.70f
    val tipEnd = w * 0.97f
    val cornerR = CornerRadius(bodyHeight * 0.5f)

    drawRoundRect(
        color = Color(0xFF455A64),
        topLeft = Offset(0f, bodyTop),
        size = Size(capEnd, bodyHeight),
        cornerRadius = cornerR,
    )
    drawRoundRect(
        color = Color(0xFF263238),
        topLeft = Offset(capEnd - bodyHeight * 0.2f, bodyTop),
        size = Size(bodyEnd - (capEnd - bodyHeight * 0.2f), bodyHeight),
        cornerRadius = cornerR,
    )
    // 원뿔형 팁 — 얇게 수렴.
    val cone = Path().apply {
        moveTo(bodyEnd, bodyTop)
        lineTo(w * 0.90f, h * 0.5f)
        lineTo(bodyEnd, bodyTop + bodyHeight)
        close()
    }
    drawPath(cone, Color(0xFF607D8B))
    // 바늘 팁 — 아주 가는 선.
    drawLine(
        color = Color(0xFF212121),
        start = Offset(w * 0.90f, h * 0.5f),
        end = Offset(tipEnd, h * 0.5f),
        strokeWidth = (h * 0.02f).coerceAtLeast(1.5f),
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawInkPen(w: Float, h: Float) {
    val bodyTop = h * 0.30f
    val bodyHeight = h * 0.40f
    val capEnd = w * 0.20f
    val bodyEnd = w * 0.68f
    val nibEnd = w * 0.94f
    val cornerR = CornerRadius(bodyHeight * 0.4f)

    drawRoundRect(
        color = Color(0xFFB7860B),
        topLeft = Offset(0f, bodyTop),
        size = Size(capEnd, bodyHeight),
        cornerRadius = cornerR,
    )
    drawRoundRect(
        color = Color(0xFF263238),
        topLeft = Offset(capEnd - bodyHeight * 0.2f, bodyTop),
        size = Size(bodyEnd - (capEnd - bodyHeight * 0.2f), bodyHeight),
        cornerRadius = cornerR,
    )
    val nibPath = Path().apply {
        moveTo(bodyEnd, bodyTop + bodyHeight * 0.15f)
        lineTo(nibEnd, h * 0.5f)
        lineTo(bodyEnd, bodyTop + bodyHeight * 0.85f)
        close()
    }
    drawPath(nibPath, Color(0xFF90A4AE))
    drawLine(
        color = Color(0xFF263238),
        start = Offset(bodyEnd + (nibEnd - bodyEnd) * 0.35f, h * 0.5f),
        end = Offset(nibEnd, h * 0.5f),
        strokeWidth = 1f,
    )
}

private fun DrawScope.drawMarker(w: Float, h: Float) {
    val bodyTop = h * 0.20f
    val bodyHeight = h * 0.60f
    val capEnd = w * 0.18f
    val bodyEnd = w * 0.75f
    val tipEnd = w * 0.92f
    val cornerR = CornerRadius(bodyHeight * 0.18f)

    drawRoundRect(
        color = Color(0xFF2E7D32),
        topLeft = Offset(0f, bodyTop),
        size = Size(capEnd, bodyHeight),
        cornerRadius = cornerR,
    )
    drawRoundRect(
        color = Color(0xFF66BB6A),
        topLeft = Offset(capEnd - bodyHeight * 0.1f, bodyTop),
        size = Size(bodyEnd - (capEnd - bodyHeight * 0.1f), bodyHeight),
        cornerRadius = cornerR,
    )
    drawRoundRect(
        color = Color(0xFF1B5E20),
        topLeft = Offset(bodyEnd, h * 0.36f),
        size = Size(tipEnd - bodyEnd, h * 0.28f),
        cornerRadius = CornerRadius(h * 0.04f),
    )
}

private fun DrawScope.drawHighlighter(w: Float, h: Float) {
    val bodyTop = h * 0.12f
    val bodyHeight = h * 0.76f
    val capEnd = w * 0.14f
    val bodyEnd = w * 0.72f
    val tipEnd = w * 0.96f
    val cornerR = CornerRadius(bodyHeight * 0.14f)

    drawRoundRect(
        color = Color(0xFFFBC02D),
        topLeft = Offset(0f, bodyTop),
        size = Size(capEnd, bodyHeight),
        cornerRadius = cornerR,
    )
    drawRoundRect(
        color = Color(0xFFFFEE58),
        topLeft = Offset(capEnd - bodyHeight * 0.08f, bodyTop),
        size = Size(bodyEnd - (capEnd - bodyHeight * 0.08f), bodyHeight),
        cornerRadius = cornerR,
    )
    val tipPath = Path().apply {
        moveTo(bodyEnd, bodyTop + bodyHeight * 0.18f)
        lineTo(tipEnd, h * 0.36f)
        lineTo(tipEnd, h * 0.64f)
        lineTo(bodyEnd, bodyTop + bodyHeight * 0.82f)
        close()
    }
    drawPath(tipPath, Color(0xFFF9A825))
}

private fun DrawScope.drawCrayon(w: Float, h: Float) {
    val bodyTop = h * 0.24f
    val bodyHeight = h * 0.52f
    val bodyEnd = w * 0.74f
    val cornerR = CornerRadius(bodyHeight * 0.12f)

    drawRoundRect(
        color = Color(0xFFE53935),
        topLeft = Offset(0f, bodyTop),
        size = Size(bodyEnd, bodyHeight),
        cornerRadius = cornerR,
    )
    // 종이 라벨
    drawRect(
        color = Color(0xFFFFFFFF),
        topLeft = Offset(w * 0.14f, bodyTop),
        size = Size(w * 0.48f, bodyHeight),
    )
    drawRect(
        color = Color(0xFFE53935),
        topLeft = Offset(w * 0.14f, bodyTop + bodyHeight * 0.42f),
        size = Size(w * 0.48f, bodyHeight * 0.16f),
    )
    // 둥근 팁 (반쪽 원)
    val tipPath = Path().apply {
        moveTo(bodyEnd, bodyTop)
        cubicTo(
            bodyEnd + w * 0.18f, bodyTop + bodyHeight * 0.05f,
            bodyEnd + w * 0.18f, bodyTop + bodyHeight * 0.95f,
            bodyEnd, bodyTop + bodyHeight,
        )
        close()
    }
    drawPath(tipPath, Color(0xFFB71C1C))
}
