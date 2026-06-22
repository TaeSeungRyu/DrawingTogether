package com.rts.rys.ryy.drawingtogether.drawing.engine

import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import com.rts.rys.ryy.drawingtogether.drawing.model.PeerId
import com.rts.rys.ryy.drawingtogether.drawing.model.Point
import com.rts.rys.ryy.drawingtogether.drawing.model.StrokeId
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolKind
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasStateTest {

    private val pen = ToolSettings(ToolKind.Pen, 0xFF000000.toInt(), 4f)
    private val local = PeerId.Local
    private val remote = PeerId("peer-b")
    private var seq: Long = 0L
    private fun next(): Long { seq += 1; return seq }

    @Test
    fun `StrokeStart adds open stroke with initial point`() {
        val state = CanvasState()
        val id = StrokeId("s1")

        state.apply(DrawingEvent.StrokeStart(next(), local, id, pen, Point(0.1f, 0.1f)))

        assertEquals(1, state.openStrokes.size)
        assertEquals(0, state.strokes.size)
        assertEquals(1, state.openStrokes[id]?.points?.size)
    }

    @Test
    fun `StrokeAppend extends open stroke points`() {
        val state = CanvasState()
        val id = StrokeId("s1")
        state.apply(DrawingEvent.StrokeStart(next(), local, id, pen, Point(0f, 0f)))

        state.apply(DrawingEvent.StrokeAppend(next(), local, id, listOf(Point(0.2f, 0.2f), Point(0.3f, 0.3f))))

        assertEquals(3, state.openStrokes[id]?.points?.size)
    }

    @Test
    fun `StrokeEnd moves stroke from open to finished`() {
        val state = CanvasState()
        val id = StrokeId("s1")
        state.apply(DrawingEvent.StrokeStart(next(), local, id, pen, Point(0f, 0f)))

        state.apply(DrawingEvent.StrokeEnd(next(), local, id))

        assertTrue(state.openStrokes.isEmpty())
        assertEquals(1, state.strokes.size)
        assertEquals(id, state.strokes[0].id)
    }

    @Test
    fun `any stroke end pushes onto undo stack (collaborative)`() {
        val state = CanvasState()
        val l = StrokeId("L")
        val r = StrokeId("R")
        state.apply(DrawingEvent.StrokeStart(next(), local, l, pen, Point(0f, 0f)))
        state.apply(DrawingEvent.StrokeEnd(next(), local, l))
        state.apply(DrawingEvent.StrokeStart(next(), remote, r, pen, Point(0.5f, 0.5f)))
        state.apply(DrawingEvent.StrokeEnd(next(), remote, r))

        // 두 stroke 모두 시간순으로 undoStack 에 들어가 마지막은 remote 의 R.
        assertTrue(state.canUndo)
        assertEquals(r, state.lastFinishedStrokeId())
    }

    @Test
    fun `Undo removes finished stroke regardless of author`() {
        val state = CanvasState()
        val a = StrokeId("a")
        val b = StrokeId("b")
        // 둘 다 local 작성자로 만든 후 마지막 거 Undo.
        listOf(a, b).forEach { id ->
            state.apply(DrawingEvent.StrokeStart(next(), local, id, pen, Point(0f, 0f)))
            state.apply(DrawingEvent.StrokeEnd(next(), local, id))
        }

        state.apply(DrawingEvent.Undo(next(), local, b))

        assertEquals(1, state.strokes.size)
        assertEquals(a, state.strokes[0].id)
        assertEquals(a, state.lastFinishedStrokeId())
    }

    @Test
    fun `Undo by any author removes any matching stroke (eraser semantics)`() {
        val state = CanvasState()
        val remoteId = StrokeId("R")
        state.apply(DrawingEvent.StrokeStart(next(), remote, remoteId, pen, Point(0f, 0f)))
        state.apply(DrawingEvent.StrokeEnd(next(), remote, remoteId))

        // local 이 remote 의 stroke 을 Undo (지우개 시나리오) — 이제 허용됨.
        state.apply(DrawingEvent.Undo(next(), local, remoteId))

        assertTrue(state.strokes.isEmpty())
    }

    @Test
    fun `Clear wipes everything regardless of author`() {
        val state = CanvasState()
        val localId = StrokeId("L")
        val remoteId = StrokeId("R")
        state.apply(DrawingEvent.StrokeStart(next(), local, localId, pen, Point(0f, 0f)))
        state.apply(DrawingEvent.StrokeEnd(next(), local, localId))
        state.apply(DrawingEvent.StrokeStart(next(), remote, remoteId, pen, Point(0.5f, 0.5f)))
        state.apply(DrawingEvent.StrokeEnd(next(), remote, remoteId))

        // local 의 Clear 도 remote stroke 까지 비움 — "함께 그리기" 단일 모드.
        state.apply(DrawingEvent.Clear(next(), local))

        assertTrue(state.strokes.isEmpty())
        assertTrue(state.openStrokes.isEmpty())
        assertFalse(state.canUndo)
    }

    @Test
    fun `StrokeAppend for unknown stroke is no-op`() {
        val state = CanvasState()

        state.apply(DrawingEvent.StrokeAppend(next(), local, StrokeId("ghost"), listOf(Point(0.5f, 0.5f))))

        assertTrue(state.openStrokes.isEmpty())
        assertTrue(state.strokes.isEmpty())
    }

    @Test
    fun `Undo for unknown stroke is no-op`() {
        val state = CanvasState()
        val id = StrokeId("s1")
        state.apply(DrawingEvent.StrokeStart(next(), local, id, pen, Point(0f, 0f)))
        state.apply(DrawingEvent.StrokeEnd(next(), local, id))

        state.apply(DrawingEvent.Undo(next(), local, StrokeId("ghost")))

        assertEquals(1, state.strokes.size)
        assertTrue(state.canUndo)
    }
}
