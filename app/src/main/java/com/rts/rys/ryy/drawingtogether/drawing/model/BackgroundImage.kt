package com.rts.rys.ryy.drawingtogether.drawing.model

import androidx.compose.ui.graphics.ImageBitmap

// 캔버스에 깔리는 사진 한 장. stroke 아래에 그려지고, 캔버스 종횡비를 사진 비율로 맞춤.
// Phase 1.5: 갤러리/카메라 소스. Phase 3에서 Remote(피어 수신) 추가.
data class BackgroundImage(
    val bitmap: ImageBitmap,
    val widthPx: Int,
    val heightPx: Int,
    val source: Source,
) {
    val aspectRatio: Float
        get() = widthPx.toFloat() / heightPx.toFloat().coerceAtLeast(1f)

    enum class Source { Gallery, Camera, Remote }
}
