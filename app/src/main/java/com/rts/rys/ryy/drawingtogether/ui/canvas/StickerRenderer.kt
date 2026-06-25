package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import com.rts.rys.ryy.drawingtogether.drawing.model.Sticker
import com.rts.rys.ryy.drawingtogether.drawing.model.StickerKey
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// 스티커 한 개를 그린다. 화면(DrawingCanvas) · PNG 합성(PngComposer) · 미니뷰(MiniCanvas)
// 모두 같은 함수를 사용 — 보이는 것 = 저장되는 것 = 동기화되는 것.
// 색은 key 마다 고정(Candy Pop 테마). scale 은 캔버스 짧은 변 대비 비율, rotationDeg 는 시계방향.
internal fun DrawScope.drawSticker(sticker: Sticker, canvasSize: IntSize) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
    val shortSide = min(canvasSize.width, canvasSize.height).toFloat()
    val sizePx = (sticker.scale * shortSide).coerceAtLeast(1f)
    val half = sizePx / 2f
    val cx = sticker.cx * canvasSize.width
    val cy = sticker.cy * canvasSize.height

    withTransform({
        translate(cx, cy)
        rotate(degrees = sticker.rotationDeg, pivot = Offset.Zero)
    }) {
        drawStickerVector(sticker.key, half)
    }
}

// 로컬 좌표계: 중심 (0,0), 박스 [-half, half]. 디테일 선 두께는 size 비례라 해상도 독립.
private fun DrawScope.drawStickerVector(key: StickerKey, half: Float) {
    val size = half * 2f
    val left = -half
    val top = -half
    val detail = (size * 0.06f).coerceAtLeast(1f)
    when (key) {
        StickerKey.Heart ->
            drawPath(buildHeartPath(left, top, size, size), color = Color(0xFFFF5C8A))

        StickerKey.Star ->
            drawPath(buildStarPath(left, top, size, size), color = Color(0xFFFFC83D))

        StickerKey.Smile -> {
            drawCircle(Color(0xFFFFD54F), radius = half * 0.92f, center = Offset.Zero)
            val eyeR = half * 0.12f
            val eyeY = -half * 0.22f
            drawCircle(Color(0xFF4E342E), radius = eyeR, center = Offset(-half * 0.32f, eyeY))
            drawCircle(Color(0xFF4E342E), radius = eyeR, center = Offset(half * 0.32f, eyeY))
            val mouth = Path().apply {
                moveTo(-half * 0.40f, half * 0.18f)
                quadraticBezierTo(0f, half * 0.62f, half * 0.40f, half * 0.18f)
            }
            drawPath(
                mouth,
                color = Color(0xFF4E342E),
                style = Stroke(width = detail * 1.4f, cap = StrokeCap.Round),
            )
        }

        StickerKey.Flower -> {
            val petalR = half * 0.42f
            val ring = half * 0.52f
            for (i in 0 until 6) {
                val a = i * Math.PI.toFloat() / 3f
                drawCircle(
                    Color(0xFFFF8FB1),
                    radius = petalR,
                    center = Offset(ring * cos(a), ring * sin(a)),
                )
            }
            drawCircle(Color(0xFFFFE082), radius = half * 0.40f, center = Offset.Zero)
        }

        StickerKey.Cloud -> {
            val c = Color(0xFF90CAF9)
            drawCircle(c, radius = half * 0.42f, center = Offset(-half * 0.40f, half * 0.10f))
            drawCircle(c, radius = half * 0.55f, center = Offset(0f, -half * 0.10f))
            drawCircle(c, radius = half * 0.40f, center = Offset(half * 0.45f, half * 0.12f))
            drawRect(
                c,
                topLeft = Offset(-half * 0.78f, half * 0.10f),
                size = Size(half * 1.56f, half * 0.46f),
            )
        }

        StickerKey.Sun -> {
            for (i in 0 until 12) {
                val a = i * Math.PI.toFloat() / 6f
                drawLine(
                    Color(0xFFFFB300),
                    start = Offset(half * 0.62f * cos(a), half * 0.62f * sin(a)),
                    end = Offset(half * 0.95f * cos(a), half * 0.95f * sin(a)),
                    strokeWidth = detail * 1.6f,
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(Color(0xFFFFCA28), radius = half * 0.55f, center = Offset.Zero)
        }

        StickerKey.Moon -> {
            val outer = Path().apply {
                addOval(Rect(left, top, left + size, top + size))
            }
            val cut = size * 0.30f
            val inner = Path().apply {
                addOval(Rect(left + cut, top - size * 0.04f, left + cut + size, top - size * 0.04f + size))
            }
            val crescent = Path().apply { op(outer, inner, PathOperation.Difference) }
            drawPath(crescent, color = Color(0xFFFFE082))
        }

        StickerKey.Rainbow -> {
            val colors = listOf(
                Color(0xFFFF5252), Color(0xFFFFB300),
                Color(0xFFFFEB3B), Color(0xFF66BB6A),
                Color(0xFF42A5F5), Color(0xFFAB47BC),
            )
            val band = half * 0.14f
            colors.forEachIndexed { i, color ->
                val r = half * 0.92f - i * band
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(-r, -r * 0.6f),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = band, cap = StrokeCap.Round),
                )
            }
        }

        StickerKey.Drop -> {
            val drop = Path().apply {
                moveTo(0f, -half * 0.92f)
                cubicTo(half * 0.85f, -half * 0.05f, half * 0.55f, half * 0.85f, 0f, half * 0.85f)
                cubicTo(-half * 0.55f, half * 0.85f, -half * 0.85f, -half * 0.05f, 0f, -half * 0.92f)
                close()
            }
            drawPath(drop, color = Color(0xFF4FC3F7))
        }

        StickerKey.Lightning -> {
            val bolt = Path().apply {
                moveTo(half * 0.18f, -half * 0.92f)
                lineTo(-half * 0.50f, half * 0.12f)
                lineTo(-half * 0.04f, half * 0.12f)
                lineTo(-half * 0.20f, half * 0.92f)
                lineTo(half * 0.52f, -half * 0.18f)
                lineTo(half * 0.06f, -half * 0.18f)
                close()
            }
            drawPath(bolt, color = Color(0xFFFFD600))
        }

        StickerKey.Diamond -> {
            val gem = Path().apply {
                moveTo(0f, -half * 0.85f)
                lineTo(half * 0.85f, -half * 0.20f)
                lineTo(0f, half * 0.85f)
                lineTo(-half * 0.85f, -half * 0.20f)
                close()
            }
            drawPath(gem, color = Color(0xFF4DD0E1))
            // 윗면 패싯 라인.
            drawLine(
                Color(0xFFB2EBF2),
                start = Offset(-half * 0.45f, -half * 0.20f),
                end = Offset(half * 0.45f, -half * 0.20f),
                strokeWidth = detail,
            )
        }

        StickerKey.Sparkle ->
            drawPath(build4PointSparklePath(half), color = Color(0xFFFFF176))
    }
}

// 4갈래 반짝이 — 바깥 4점(상하좌우) + 안쪽 4점으로 오목한 별.
private fun build4PointSparklePath(half: Float): Path {
    val outer = half * 0.95f
    val inner = half * 0.18f
    return Path().apply {
        for (i in 0 until 8) {
            val a = i * Math.PI.toFloat() / 4f - Math.PI.toFloat() / 2f
            val r = if (i % 2 == 0) outer else inner
            val x = r * cos(a)
            val y = r * sin(a)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
