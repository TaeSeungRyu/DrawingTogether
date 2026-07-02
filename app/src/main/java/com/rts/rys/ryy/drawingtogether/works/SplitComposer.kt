package com.rts.rys.ryy.drawingtogether.works

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState
import com.rts.rys.ryy.drawingtogether.drawing.model.SplitLayout
import kotlin.math.roundToInt

// 나눠 그리기 합성 — 참가자별 CanvasState 를 레이아웃 슬롯 rect 에 배치해 한 장으로 만든다.
// 미리보기 모달과 저장이 공용으로 쓴다("미리보기 = 저장물"). 각 슬롯은 PngComposer.compose 로
// 그 캔버스를 자체 렌더한 뒤 슬롯 픽셀 rect 로 스케일 배치. 캔버스 비율 == 슬롯 비율이라 왜곡 없음.
object SplitComposer {

    // 합성물 짧은 변(정사각 기준). PngComposer.DEFAULT_BLANK_SIZE 와 동급.
    private const val COMPOSITE_SIZE = 1080

    // orderedCanvases[i] = 슬롯 i 담당자의 캔버스(없으면 null → 흰 슬롯).
    // drawDividers = true 면 슬롯 경계에 희미한 선을 그린다(미리보기 전용 — 저장물엔 미포함).
    fun compose(
        layout: SplitLayout,
        orderedCanvases: List<CanvasState?>,
        density: Float,
        screenCanvasShortDp: Float,
        drawDividers: Boolean = false,
    ): Bitmap {
        val w = COMPOSITE_SIZE
        val h = COMPOSITE_SIZE
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        val slotRects = layout.slots.map { slot ->
            Rect(
                (slot.left * w).roundToInt(),
                (slot.top * h).roundToInt(),
                (slot.right * w).roundToInt(),
                (slot.bottom * h).roundToInt(),
            )
        }
        slotRects.forEachIndexed { i, dst ->
            val state = orderedCanvases.getOrNull(i) ?: return@forEachIndexed
            // 슬롯을 슬롯 rect 치수 그대로(=슬롯 비율) 렌더 → dst 에 1:1 배치라 비균일 스케일 왜곡 없음.
            // (state.aspect 로 렌더 후 스케일하면 정사각 아닌 슬롯에서 선 두께·스티커가 찌그러짐.)
            val slotBmp = PngComposer.compose(
                state = state,
                density = density,
                includeBackground = true,
                screenCanvasShortDp = screenCanvasShortDp,
                forceWidthPx = dst.width(),
                forceHeightPx = dst.height(),
            )
            canvas.drawBitmap(slotBmp, null, dst, paint)
            slotBmp.recycle()
        }

        // 미리보기 — 슬롯 경계 희미한 선. 각 슬롯 rect 외곽선을 얇게 그리면 내부 구분선 + 옅은 테두리.
        if (drawDividers) {
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (w * 0.004f).coerceAtLeast(2f)
                color = 0x40000000 // 희미한 검정(약 25% 알파)
            }
            slotRects.forEach { canvas.drawRect(it, line) }
        }
        return out
    }
}
