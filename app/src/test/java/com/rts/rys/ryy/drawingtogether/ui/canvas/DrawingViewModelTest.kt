package com.rts.rys.ryy.drawingtogether.ui.canvas

import com.rts.rys.ryy.drawingtogether.drawing.model.Point
import com.rts.rys.ryy.drawingtogether.drawing.model.StrokeId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// #6 — 그리는 중 툴 변경/컴포저블 이탈로 제스처가 취소될 때 openStroke 이 누수되지 않아야 한다.
// DrawingCanvas 의 try/finally 는 취소 시 strokeCancel 을 부르고, strokeCancel 은 현재 tool 이
// 아니라 "열린 stroke 존재 여부" 로 판정해 확실히 정리한다.
class DrawingViewModelTest {

    @Test
    fun `strokeCancel cleans up open stroke even after switching to eraser mid-draw`() {
        val vm = DrawingViewModel()
        val id = StrokeId("s1")
        vm.strokeStart(id, Point(0.1f, 0.1f))
        vm.strokeAppend(id, listOf(Point(0.2f, 0.2f)))
        assertTrue(vm.canvas.openStrokes.containsKey(id))

        // 그리는 도중 펜 → 지우개 전환 (pointerInput 재시작으로 취소되는 상황을 모사).
        vm.toggleEraser()
        vm.strokeCancel(id)

        // 미완료 stroke 이 완료/열림 어디에도 남지 않아야.
        assertTrue(vm.canvas.openStrokes.isEmpty())
        assertTrue(vm.canvas.strokes.isEmpty())
        assertFalse(vm.canvas.canUndo)
    }

    @Test
    fun `strokeCancel is a no-op when no open stroke exists`() {
        val vm = DrawingViewModel()
        // 열린 stroke 이 없을 때 취소 호출 — 아무 일도 없어야(예: 지우개라 애초에 시작 안 함).
        vm.strokeCancel(StrokeId("ghost"))

        assertTrue(vm.canvas.openStrokes.isEmpty())
        assertTrue(vm.canvas.strokes.isEmpty())
        assertFalse(vm.canvas.canUndo)
    }

    @Test
    fun `strokeCancel removes the just-started stroke without leaving it finished`() {
        val vm = DrawingViewModel()
        val id = StrokeId("s1")
        vm.strokeStart(id, Point(0f, 0f))
        vm.strokeCancel(id)

        assertTrue(vm.canvas.openStrokes.isEmpty())
        assertTrue(vm.canvas.strokes.isEmpty())
    }
}
