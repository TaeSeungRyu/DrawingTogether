package com.rts.rys.ryy.drawingtogether.works

import com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState
import kotlin.math.roundToInt

// 스포이드 — 캔버스에 보이는 색을 정규화 좌표(0..1)로 집어낸다.
// PngComposer 로 사진 배경 + stroke + 스티커를 합성한 비트맵을 만들어 그 픽셀을 읽으므로
// "보이는 색 = 집히는 색" (저장 결과와도 동일). 반투명 픽셀이라도 합성 후라 단색이며,
// 펜 색으로 쓰기 위해 alpha 는 불투명(0xFF)으로 강제한다.
object CanvasColorSampler {

    fun sampleColor(state: CanvasState, density: Float, nx: Float, ny: Float): Int {
        val bmp = PngComposer.compose(state, density, includeBackground = true)
        try {
            val px = (nx * (bmp.width - 1)).roundToInt().coerceIn(0, bmp.width - 1)
            val py = (ny * (bmp.height - 1)).roundToInt().coerceIn(0, bmp.height - 1)
            return 0xFF000000.toInt() or (bmp.getPixel(px, py) and 0x00FFFFFF)
        } finally {
            bmp.recycle()
        }
    }
}
