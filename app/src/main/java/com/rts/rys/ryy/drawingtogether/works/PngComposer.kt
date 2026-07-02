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
import kotlin.math.roundToInt

// CanvasState를 단일 비트맵으로 합성. 화면에 보이는 것과 같은 stroke 렌더링 함수를 재사용해
// "보이는 것 = 저장되는 것"을 보장.
// 진행 중 stroke와 커서 인디케이터는 포함하지 않음 — 저장 시점엔 손가락이 떨어진 상태로 가정.
object PngComposer {

    private const val DEFAULT_BLANK_SIZE = 1080

    // screenCanvasShortDp: 화면에 그려진 캔버스의 짧은변(dp). >0 이면 stroke 굵기를 export 해상도 대비
    // 화면과 같은 비율로 산출(#1). 0f(미상)면 device density 로 기존 동작 유지.
    // forceWidthPx/forceHeightPx: 둘 다 >0 이면 출력 치수를 이 값으로 강제(사진/aspect 무시).
    // 나눠 그리기 합성에서 각 슬롯을 슬롯 비율 그대로 렌더해 왜곡 없이 배치하려고 사용.
    fun compose(
        state: CanvasState,
        density: Float,
        includeBackground: Boolean = true,
        screenCanvasShortDp: Float = 0f,
        forceWidthPx: Int = 0,
        forceHeightPx: Int = 0,
    ): Bitmap {
        val bg = state.background
        // 사진 있으면 사진 치수(비율). 없으면 선택한 캔버스 비율로 치수 산출(자유=정사각).
        // stroke 는 정규화 좌표라 저장 치수의 비율이 화면 캔버스 비율과 같아야 모양이 맞음.
        val (w, h) = when {
            forceWidthPx > 0 && forceHeightPx > 0 -> forceWidthPx to forceHeightPx
            bg != null -> bg.widthPx to bg.heightPx
            else -> {
                val r = state.aspect.ratio
                when {
                    r == null -> DEFAULT_BLANK_SIZE to DEFAULT_BLANK_SIZE
                    r >= 1f -> DEFAULT_BLANK_SIZE to (DEFAULT_BLANK_SIZE / r).roundToInt()
                    else -> (DEFAULT_BLANK_SIZE * r).roundToInt() to DEFAULT_BLANK_SIZE
                }
            }
        }

        val image = ImageBitmap(w, h)
        val canvas = Canvas(image)
        val drawScope = CanvasDrawScope()
        val sizeFloat = Size(w.toFloat(), h.toFloat())
        val sizeInt = IntSize(w, h)
        // stroke 전용 density — 화면에서 본 굵기 비율을 export 해상도에 재현(#1). 배경/스티커/텍스트엔 무관.
        val strokeDensity = exportStrokeDensity(w, h, screenCanvasShortDp, fallbackDensity = density)

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
                drawStroke(stroke, sizeInt, strokeDensity)
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
