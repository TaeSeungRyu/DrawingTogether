package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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

        // S자 곡선 — 캡/조인/알파/굵기 특성 미리보기 공통 경로.
        fun sCurveAt(t: Float): Offset {
            // 곡선 위 t(0..1) 지점 근사 (3차 베지어 샘플).
            val p0 = Offset(w * 0.08f, midY + h * 0.25f)
            val p1 = Offset(w * 0.32f, midY - h * 0.40f)
            val p2 = Offset(w * 0.68f, midY + h * 0.40f)
            val p3 = Offset(w * 0.92f, midY - h * 0.25f)
            val u = 1 - t
            val x = u*u*u*p0.x + 3*u*u*t*p1.x + 3*u*t*t*p2.x + t*t*t*p3.x
            val y = u*u*u*p0.y + 3*u*u*t*p1.y + 3*u*t*t*p2.y + t*t*t*p3.y
            return Offset(x, y)
        }

        if (brush == BrushType.Airbrush) {
            // 분사 미리보기 — 곡선 따라 점 흩뿌림 (결정론 seed 고정).
            val radius = (h * 0.20f).coerceAtLeast(3f)
            val rng = kotlin.random.Random(42)
            val dot = (h * 0.04f).coerceAtLeast(1.2f)
            var t = 0f
            while (t <= 1f) {
                val c = sCurveAt(t)
                repeat(6) {
                    val a = rng.nextFloat() * 2f * Math.PI.toFloat()
                    val d = kotlin.math.sqrt(rng.nextFloat()) * radius
                    drawCircle(
                        color = color.copy(alpha = brush.alpha),
                        radius = dot,
                        center = Offset(
                            c.x + d * kotlin.math.cos(a),
                            c.y + d * kotlin.math.sin(a),
                        ),
                    )
                }
                t += 0.06f
            }
            return@Canvas
        }

        val path = Path().apply {
            moveTo(w * 0.08f, midY + h * 0.25f)
            cubicTo(
                w * 0.32f, midY - h * 0.40f,
                w * 0.68f, midY + h * 0.40f,
                w * 0.92f, midY - h * 0.25f,
            )
        }

        val widthPx = (h * 0.22f * brush.widthScale).coerceAtLeast(2f)

        if (brush == BrushType.Blur) {
            // 번짐 미리보기 — native Paint + BlurMaskFilter.
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = widthPx
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
                this.color = color.copy(alpha = brush.alpha).toArgb()
                maskFilter = android.graphics.BlurMaskFilter(
                    (widthPx * 0.5f).coerceAtLeast(1f),
                    android.graphics.BlurMaskFilter.Blur.NORMAL,
                )
            }
            drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
            return@Canvas
        }

        val cap = if (brush.capStyle == BrushCapStyle.Square) StrokeCap.Square else StrokeCap.Round
        val join = if (brush.capStyle == BrushCapStyle.Square) StrokeJoin.Miter else StrokeJoin.Round

        drawPath(
            path = path,
            color = color.copy(alpha = brush.alpha),
            style = Stroke(width = widthPx, cap = cap, join = join),
        )
    }
}
