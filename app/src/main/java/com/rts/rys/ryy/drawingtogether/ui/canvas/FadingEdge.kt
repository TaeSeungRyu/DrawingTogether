package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 가로 스크롤 가능 컨테이너에 양 끝 fade — "더 있다" 시각 신호.
// scrollState 의 canScrollBackward / canScrollForward 에 따라 left/right fade 토글.
// graphicsLayer 의 Offscreen 합성이 필수 — 안 그러면 DstIn blend 가 전체 캔버스에 적용된다.
fun Modifier.fadingEdgeHorizontal(
    leftFade: Boolean,
    rightFade: Boolean,
    fadeWidth: Dp = 24.dp,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fadePx = fadeWidth.toPx()
        if (fadePx <= 0f) return@drawWithContent
        if (leftFade) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = fadePx,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (rightFade) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - fadePx,
                    endX = size.width,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
