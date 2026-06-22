package com.rts.rys.ryy.drawingtogether.drawing.engine

import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import com.rts.rys.ryy.drawingtogether.drawing.model.PeerId
import com.rts.rys.ryy.drawingtogether.drawing.model.Point
import com.rts.rys.ryy.drawingtogether.drawing.model.StrokeId
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolKind
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `local stroke end pushes onto undo stack`() {
        val state = CanvasState()
        val id = StrokeId("s1")
        state.apply(DrawingEvent.StrokeStart(next(), local, id, pen, Point(0f, 0f)))
        state.apply(DrawingEvent.StrokeEnd(next(), local, id))

        assertTrue(state.canUndo)
        assertEquals(id, state.lastLocalStrokeId())
    }

    @Test
    fun `remote stroke end does not affect undo stack`() {
        val state = CanvasState()
        val id = StrokeId("r1")
        state.apply(DrawingEvent.StrokeStart(next(), remote, id, pen, Point(0f, 0f)))
        state.apply(DrawingEvent.StrokeEnd(next(), remote, id))

        assertEquals(1, state.strokes.size)
        assertFalse(state.canUndo)
        assertNull(state.lastLocalStrokeId())
    }

    @Test
    fun `Undo removes finished stroke and pops undo stack`() {
        val state = CanvasState()
        val a = StrokeId("a")
        val b = StrokeId("b")
        listOf(a, b).forEach { id ->
            state.apply(DrawingEvent.StrokeStart(next(), local, id, pen, Point(0f, 0f)))
            state.apply(DrawingEvent.StrokeEnd(next(), local, id))
        }

        state.apply(DrawingEvent.Undo(next(), local, b))

        assertEquals(1, state.strokes.size)
        assertEquals(a, state.strokes[0].id)
        assertEquals(a, state.lastLocalStrokeId())
    }

    @Test
    fun `Clear removes only own-author strokes, peer strokes survive`() {
        val state = CanvasState()
        val localId = StrokeId("L")
        val remoteId = StrokeId("R")
        state.apply(DrawingEvent.StrokeStart(next(), local, localId, pen, Point(0f, 0f)))
        state.apply(DrawingEvent.StrokeEnd(next(), local, localId))
        state.apply(DrawingEvent.StrokeStart(next(), remote, remoteId, pen, Point(0.5f, 0.5f)))
        state.apply(DrawingEvent.StrokeEnd(next(), remote, remoteId))

        state.apply(DrawingEvent.Clear(next(), local))

        assertEquals(1, state.strokes.size)
        assertEquals(remoteId, state.strokes[0].id)
        assertTrue(state.openStrokes.isEmpty())
        assertFalse(state.canUndo)
    }

    @Test
    fun `Clear by remote author leaves local strokes intact`() {
        val state = CanvasState()
        val localId = StrokeId("L")
        val remoteId = StrokeId("R")
        state.apply(DrawingEvent.StrokeStart(next(), local, localId, pen, Point(0f, 0f)))
        state.apply(DrawingEvent.StrokeEnd(next(), local, localId))
        state.apply(DrawingEvent.StrokeStart(next(), remote, remoteId, pen, Point(0.5f, 0.5f)))
        state.apply(DrawingEvent.StrokeEnd(next(), remote, remoteId))

        state.apply(DrawingEvent.Clear(next(), remote))

        assertEquals(1, state.strokes.size)
        assertEquals(localId, state.strokes[0].id)
        assertTrue(state.canUndo)
    }

    @Test
    fun `Undo with cross-author strokeId is rejected`() {
        val state = CanvasState()
        val remoteId = StrokeId("R")
        state.apply(DrawingEvent.StrokeStart(next(), remote, remoteId, pen, Point(0f, 0f)))
        state.apply(DrawingEvent.StrokeEnd(next(), remote, remoteId))

        // local 이 remote 의 stroke 을 Undo 시도 — 무시되어야 한다.
        state.apply(DrawingEvent.Undo(next(), local, remoteId))

        assertEquals(1, state.strokes.size)
        assertEquals(remoteId, state.strokes[0].id)
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

    @Test
    fun `mixed local and remote strokes — only local survive on undo`() {
        val state = CanvasState()
        val localId = StrokeId("L")
        val remoteId = StrokeId("R")

        state.apply(DrawingEvent.StrokeStart(next(), local, localId, pen, Point(0f, 0f)))
        state.apply(DrawingEvent.StrokeEnd(next(), local, localId))
        state.apply(DrawingEvent.StrokeStart(next(), remote, remoteId, pen, Point(0.5f, 0.5f)))
        state.apply(DrawingEvent.StrokeEnd(next(), remote, remoteId))

        assertEquals(2, state.strokes.size)
        assertEquals(localId, state.lastLocalStrokeId())

        state.apply(DrawingEvent.Undo(next(), local, localId))

        assertEquals(1, state.strokes.size)
        assertEquals(remoteId, state.strokes[0].id)
        assertFalse(state.canUndo)
    }
}
