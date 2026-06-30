package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntSize
import com.rts.rys.ryy.drawingtogether.drawing.model.TextElement
import kotlin.math.min

// 텍스트 한 개를 그린다. 화면(DrawingCanvas) · PNG 합성(PngComposer) · 미니뷰(MiniCanvas) ·
// 타임랩스 재생/내보내기 모두 같은 함수를 사용 — 보이는 것 = 저장되는 것 = 동기화되는 것.
// 벡터 stroke·스티커와 달리 폰트 글리프라 기기 기본 폰트에 의존(동일 기기 내에선 일관).
// sizeFrac 은 캔버스 짧은 변 대비 글자 크기 비율, 중심(cx,cy) 기준 가로·세로 중앙 정렬, 줄바꿈(\n) 지원.
internal fun DrawScope.drawText(text: TextElement, canvasSize: IntSize) {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
    if (text.text.isEmpty()) return

    val paint = textPaint(text, canvasSize)
    val lines = text.text.split("\n")
    val fm = paint.fontMetrics
    val lineHeight = fm.descent - fm.ascent
    val totalHeight = lineHeight * lines.size
    val cx = text.cx * canvasSize.width
    val cy = text.cy * canvasSize.height
    // 첫 줄 baseline = 중앙 - 전체높이/2 - ascent(음수). 이후 줄은 lineHeight 만큼 내려간다.
    var baseline = cy - totalHeight / 2f - fm.ascent

    drawIntoCanvas { canvas ->
        lines.forEach { line ->
            canvas.nativeCanvas.drawText(line, cx, baseline, paint)
            baseline += lineHeight
        }
    }
}

// 텍스트 박스의 중심 기준 반치수(halfWidth, halfHeight) px — 히트 판정·선택 박스 공용.
internal fun textHalfSizePx(text: TextElement, canvasSize: IntSize): Pair<Float, Float> {
    val paint = textPaint(text, canvasSize)
    val lines = text.text.split("\n").ifEmpty { listOf("") }
    val fm = paint.fontMetrics
    val lineHeight = fm.descent - fm.ascent
    val totalHeight = lineHeight * lines.size
    val maxWidth = lines.maxOf { paint.measureText(it) }
    return (maxWidth / 2f) to (totalHeight / 2f)
}

private fun textPaint(text: TextElement, canvasSize: IntSize): android.graphics.Paint {
    val shortSide = min(canvasSize.width, canvasSize.height).toFloat()
    return android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = text.colorArgb
        textSize = (text.sizeFrac * shortSide).coerceAtLeast(1f)
        textAlign = android.graphics.Paint.Align.CENTER
    }
}
