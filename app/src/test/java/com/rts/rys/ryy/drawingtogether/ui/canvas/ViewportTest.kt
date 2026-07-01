package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportTest {

    private val size = IntSize(1000, 800)

    @Test
    fun `screenToContent is inverse of content-to-screen`() {
        val scale = 2f
        val offset = Offset(-300f, -200f)
        val content = Offset(120f, 90f)
        // content -> screen
        val screen = Offset(content.x * scale + offset.x, content.y * scale + offset.y)
        val back = screen.screenToContent(scale, offset)
        assertEquals(content.x, back.x, 0.001f)
        assertEquals(content.y, back.y, 0.001f)
    }

    @Test
    fun `clampOffset returns zero at scale 1 or less`() {
        assertEquals(Offset.Zero, clampOffset(Offset(50f, 50f), 1f, size))
        assertEquals(Offset.Zero, clampOffset(Offset(50f, 50f), 0.5f, size))
    }

    @Test
    fun `clampOffset keeps content covering the view`() {
        val scale = 2f
        // 좌상단 한계: W - W*scale = -1000, H - H*scale = -800.
        // 양수 offset 은 0 으로, 과도한 음수는 한계로 클램프.
        assertEquals(Offset.Zero, clampOffset(Offset(100f, 100f), scale, size))
        val clamped = clampOffset(Offset(-5000f, -5000f), scale, size)
        assertEquals(-1000f, clamped.x, 0.001f)
        assertEquals(-800f, clamped.y, 0.001f)
    }

    @Test
    fun `zoomAround keeps the content point under the centroid fixed`() {
        val scale = 1f
        val offset = Offset.Zero
        val centroid = Offset(400f, 300f)
        // 확대 전 centroid 아래 콘텐츠 점.
        val contentBefore = centroid.screenToContent(scale, offset)
        val (newScale, newOffset) = zoomAround(scale, offset, centroid, 2f, Offset.Zero, size)
        assertEquals(2f, newScale, 0.001f)
        // 확대 후 같은 콘텐츠 점이 여전히 centroid 화면 위치에 오는지.
        val screenAfter = Offset(
            contentBefore.x * newScale + newOffset.x,
            contentBefore.y * newScale + newOffset.y,
        )
        assertEquals(centroid.x, screenAfter.x, 0.001f)
        assertEquals(centroid.y, screenAfter.y, 0.001f)
    }

    @Test
    fun `zoomAround clamps scale to max`() {
        val (newScale, _) = zoomAround(4f, Offset.Zero, Offset(500f, 400f), 10f, Offset.Zero, size)
        assertEquals(MAX_CANVAS_ZOOM, newScale, 0.001f)
    }

    @Test
    fun `zoomAround clamps scale to min 1`() {
        val (newScale, newOffset) = zoomAround(2f, Offset(-200f, -100f), Offset(500f, 400f), 0.1f, Offset.Zero, size)
        assertEquals(1f, newScale, 0.001f)
        assertEquals(Offset.Zero, newOffset)
    }
}
