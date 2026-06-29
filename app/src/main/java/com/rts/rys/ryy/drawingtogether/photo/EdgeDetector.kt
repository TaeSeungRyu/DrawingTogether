package com.rts.rys.ryy.drawingtogether.photo

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs

// 트레이싱 보조 — 사진에서 외곽선만 추출해 오버레이로 쓸 투명 비트맵을 만든다.
// Sobel 연산자로 명도(grayscale) 기울기를 구하고, 기울기가 큰 픽셀만 검은 선으로 남긴다.
// 로컬 표시 전용 — DrawingEvent/CanvasState/저장과 무관. CPU 비용이 커서 백그라운드에서 한 번만 계산.
object EdgeDetector {

    // 기울기 크기가 이 값을 넘는 픽셀만 외곽선으로 본다(약한 노이즈 억제). 0..1020 범위의 |gx|+|gy|.
    private const val THRESHOLD = 48

    // 검출된 외곽선을 그릴 색(검정). 흰/배경색 캔버스 위에 또렷한 라인아트가 된다.
    private const val EDGE_RGB = 0x000000

    fun detect(source: ImageBitmap): ImageBitmap {
        // 하드웨어 비트맵 등 getPixels 불가 구성을 대비해 소프트 ARGB_8888 로 복사.
        val src = source.asAndroidBitmap()
        val bmp = if (src.config == Bitmap.Config.ARGB_8888) src
        else src.copy(Bitmap.Config.ARGB_8888, false)

        val w = bmp.width
        val h = bmp.height
        if (w < 3 || h < 3) return source

        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        // 명도(luma) 선계산 — 정수 가중치(0.299/0.587/0.114).
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000
        }

        val out = IntArray(w * h) // 기본 0 = 완전 투명. 가장자리(1px 테두리)는 투명 유지.
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                val tl = gray[idx - w - 1]; val tc = gray[idx - w]; val tr = gray[idx - w + 1]
                val ml = gray[idx - 1]; /*           */ val mr = gray[idx + 1]
                val bl = gray[idx + w - 1]; val bc = gray[idx + w]; val br = gray[idx + w + 1]

                val gx = (tr + 2 * mr + br) - (tl + 2 * ml + bl)
                val gy = (bl + 2 * bc + br) - (tl + 2 * tc + tr)
                val mag = abs(gx) + abs(gy)

                if (mag > THRESHOLD) {
                    // 기울기가 셀수록 진하게 — 부드러운 라인. 알파 0..255.
                    val alpha = (mag / 4).coerceIn(0, 255)
                    out[idx] = (alpha shl 24) or EDGE_RGB
                }
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result.asImageBitmap()
    }
}
