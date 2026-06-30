package com.rts.rys.ryy.drawingtogether.works

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState
import com.rts.rys.ryy.drawingtogether.ui.canvas.drawSticker
import com.rts.rys.ryy.drawingtogether.ui.canvas.drawStroke
import com.rts.rys.ryy.drawingtogether.ui.canvas.drawText

// CanvasState를 단일 비트맵으로 합성. 화면에 보이는 것과 같은 stroke 렌더링 함수를 재사용해
// "보이는 것 = 저장되는 것"을 보장.
// 진행 중 stroke와 커서 인디케이터는 포함하지 않음 — 저장 시점엔 손가락이 떨어진 상태로 가정.
object PngComposer {

    private const val DEFAULT_BLANK_SIZE = 1080

    fun compose(state: CanvasState, density: Float, includeBackground: Boolean = true): Bitmap {
        val bg = state.background
        // 사진 미포함 모드여도 캔버스 크기는 bg 비율을 유지해야 stroke(정규화 좌표) 비율이 맞음.
        val (w, h) = if (bg != null) bg.widthPx to bg.heightPx
                     else DEFAULT_BLANK_SIZE to DEFAULT_BLANK_SIZE

        val image = ImageBitmap(w, h)
        val canvas = Canvas(image)
        val drawScope = CanvasDrawScope()
        val sizeFloat = Size(w.toFloat(), h.toFloat())
        val sizeInt = IntSize(w, h)

        drawScope.draw(
            density = Density(density),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = sizeFloat,
        ) {
            // 배경: 사진이 있고 합치기 모드면 사진, 아니면 캔버스 배경색(기본 흰색).
            if (bg != null && includeBackground) {
                drawImage(
                    image = bg.bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bg.bitmap.width, bg.bitmap.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = sizeInt,
                )
            } else {
                drawRect(color = Color(state.backgroundColor))
            }
            // 완료된 stroke만.
            state.strokes.forEach { stroke ->
                drawStroke(stroke, sizeInt, density)
            }
            // 스티커는 stroke 위에 합성 (배치 순서대로).
            state.stickers.forEach { sticker ->
                drawSticker(sticker, sizeInt)
            }
            // 텍스트는 맨 위에 합성.
            state.texts.forEach { text ->
                drawText(text, sizeInt)
            }
        }

        return image.asAndroidBitmap()
    }
}
