package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushCapStyle
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushType

// 브러시 타입을 시각적으로 보여주는 작은 미리보기 — S자 곡선으로 캡/조인/알파/굵기 특성이 한눈에 보임.
// BrushSelectorSheet의 카드, Toolbar의 트리거 버튼 모두에서 사용.
@Composable
fun BrushPreview(
    brush: BrushType,
    color: Color = Color.Black,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val midY = h / 2f

        val path = Path().apply {
            moveTo(w * 0.08f, midY + h * 0.25f)
            cubicTo(
                w * 0.32f, midY - h * 0.40f,
                w * 0.68f, midY + h * 0.40f,
                w * 0.92f, midY - h * 0.25f,
            )
        }

        val widthPx = (h * 0.22f * brush.widthScale).coerceAtLeast(2f)
        val cap = if (brush.capStyle == BrushCapStyle.Square) StrokeCap.Square else StrokeCap.Round
        val join = if (brush.capStyle == BrushCapStyle.Square) StrokeJoin.Miter else StrokeJoin.Round

        drawPath(
            path = path,
            color = color.copy(alpha = brush.alpha),
            style = Stroke(width = widthPx, cap = cap, join = join),
        )
    }
}
