package com.rts.rys.ryy.drawingtogether.works

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportScaleTest {

    // 화면 캔버스 짧은변(dp)이 같을 때, export 짧은변이 2배면 density(=굵기)도 2배 —
    // stroke 가 캔버스 짧은변에 비례해 커져야 화면 대비 굵기 비율이 보존된다(#1).
    @Test
    fun `stroke density scales with export short side`() {
        val screenShortDp = 400f
        val small = exportStrokeDensity(1000, 1500, screenShortDp, fallbackDensity = 1f)
        val large = exportStrokeDensity(2000, 3000, screenShortDp, fallbackDensity = 1f)
        assertEquals(small * 2f, large, 0.001f)
    }

    // 짧은변 기준 — 가로/세로 중 작은 쪽으로 산출(스티커·텍스트와 동일 기준).
    @Test
    fun `uses the shorter side of the export size`() {
        val d = exportStrokeDensity(3000, 1000, screenCanvasShortDp = 500f, fallbackDensity = 1f)
        assertEquals(1000f / 500f, d, 0.001f)
    }

    // 화면에서 본 굵기 px 비율이 export 에서 보존된다:
    // 화면 stroke 비율 = dp*deviceDensity / (shortDp*deviceDensity) = dp/shortDp,
    // export stroke px = dp * exportStrokeDensity → 비율 = dp/shortDp 로 동일.
    @Test
    fun `preserves on-screen stroke fraction at export resolution`() {
        val dp = 8f
        val shortDp = 360f
        val deviceDensity = 2.75f
        val screenShortPx = shortDp * deviceDensity
        val screenFraction = (dp * deviceDensity) / screenShortPx

        val exportShort = 2048
        val exportDensity = exportStrokeDensity(exportShort, 3072, shortDp, fallbackDensity = deviceDensity)
        val exportFraction = (dp * exportDensity) / exportShort

        assertEquals(screenFraction, exportFraction, 0.0001f)
    }

    // 화면 크기 미상(0)이면 스케일하지 않고 fallback density 를 그대로 사용.
    @Test
    fun `falls back to device density when screen size unknown`() {
        val d = exportStrokeDensity(2048, 2048, screenCanvasShortDp = 0f, fallbackDensity = 2.75f)
        assertEquals(2.75f, d, 0.0001f)
    }
}
