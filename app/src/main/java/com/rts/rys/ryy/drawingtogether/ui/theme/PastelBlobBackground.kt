package com.rts.rys.ryy.drawingtogether.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// 파스텔 팝 시그니처 배경 — coral / mint / lavender 세 개 원을 radial gradient로 부드럽게.
// drawCircle radius와 동일한 gradient radius라 edge alpha=0 → 자연스러운 falloff (cream 배경에 녹아듦).
// 스플래시와 홈이 같은 레이아웃을 공유해 시각적 연결성을 만듦.
@Composable
fun PastelBlobBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 좌상단 코랄
        blob(Offset(w * 0.15f, h * 0.18f), w * 0.80f, CandyCoral, 0.32f)
        // 우중단 민트 (화면 밖에서 살짝 들어옴 — 끝자락만 보임)
        blob(Offset(w * 1.05f, h * 0.45f), w * 0.65f, CandyMint, 0.26f)
        // 우하단 라벤더
        blob(Offset(w * 0.85f, h * 0.95f), w * 0.80f, CandyLavender, 0.28f)
    }
}

private fun DrawScope.blob(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
