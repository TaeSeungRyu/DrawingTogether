package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 가로 스크롤 가능 컨테이너에 양 끝 fade — "더 있다" 시각 신호.
// 콘텐츠 위에 fadeColor (컨테이너 배경색) 그라디언트를 overlay 해 콘텐츠가 배경에 흡수되어
// 잘리는 듯한 효과. alpha-only fade 보다 시인성 좋음.
// scrollState 의 canScrollBackward / canScrollForward 에 따라 left/right fade 토글.
fun Modifier.fadingEdgeHorizontal(
    leftFade: Boolean,
    rightFade: Boolean,
    fadeColor: Color,
    fadeWidth: Dp = 32.dp,
): Modifier = this.drawWithContent {
    drawContent()
    val fadePx = fadeWidth.toPx()
    if (fadePx <= 0f) return@drawWithContent
    if (leftFade) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(fadeColor, fadeColor.copy(alpha = 0f)),
                startX = 0f,
                endX = fadePx,
            ),
        )
    }
    if (rightFade) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(fadeColor.copy(alpha = 0f), fadeColor),
                startX = size.width - fadePx,
                endX = size.width,
            ),
        )
    }
}
