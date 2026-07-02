package com.rts.rys.ryy.drawingtogether.works

import kotlin.math.min

// 저장(PNG)·타임랩스 export 시 stroke 굵기 밀도(density).
//
// 화면에서 stroke 굵기는 `dp * deviceDensity` 절대 px라, 캔버스 짧은변 대비 비율은
// `dp / 화면캔버스_짧은변_dp` 로 deviceDensity 가 상쇄된다(스티커·텍스트는 짧은변 정규화라 이미 이 성질).
// export 해상도(더 큰 픽셀)에서 이 비율을 그대로 재현하려면 넘기는 density 를
//   export_짧은변_px / 화면캔버스_짧은변_dp
// 로 잡으면 된다. `strokeWidthPxFor(tool, density) = dp * density` 는 그대로 두고 이 density 만 바꿔
// 화면 렌더는 손대지 않는다(#1, #16).
//
// screenCanvasShortDp <= 0 (화면 크기 미상) 이면 스케일하지 않고 fallbackDensity 를 그대로 쓴다.
fun exportStrokeDensity(
    exportWidthPx: Int,
    exportHeightPx: Int,
    screenCanvasShortDp: Float,
    fallbackDensity: Float,
): Float {
    if (screenCanvasShortDp <= 0f) return fallbackDensity
    val exportShort = min(exportWidthPx, exportHeightPx).toFloat()
    return exportShort / screenCanvasShortDp
}
