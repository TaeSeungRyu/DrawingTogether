package com.rts.rys.ryy.drawingtogether.transport.codec

import com.rts.rys.ryy.drawingtogether.drawing.model.BrushType
import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import com.rts.rys.ryy.drawingtogether.drawing.model.PeerId
import com.rts.rys.ryy.drawingtogether.drawing.model.Point
import com.rts.rys.ryy.drawingtogether.drawing.model.ShapeMode
import com.rts.rys.ryy.drawingtogether.drawing.model.StrokeId
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolKind
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolSettings
import com.rts.rys.ryy.drawingtogether.transport.Frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameCodecTest {

    @Test
    fun helloRoundtrip() {
        val src = Frame.Hello(proto = 1, peerId = "01H...", nick = "ryu")
        val bytes = FrameCodec.encode(src)
        val back = FrameCodec.decode(bytes)
        assertEquals(src, back)
    }

    @Test
    fun helloAckRoundtrip() {
        val src = Frame.HelloAck(peerId = "abc")
        assertEquals(src, FrameCodec.decode(FrameCodec.encode(src)))
    }

    @Test
    fun pingPongRoundtrip() {
        val ping = Frame.Ping(ts = 1_234_567L)
        val pong = Frame.Pong(ts = 9_876_543L)
        assertEquals(ping, FrameCodec.decode(FrameCodec.encode(ping)))
        assertEquals(pong, FrameCodec.decode(FrameCodec.encode(pong)))
    }

    @Test
    fun byeRoundtrip() {
        val src = Frame.Bye(reason = "user-quit")
        assertEquals(src, FrameCodec.decode(FrameCodec.encode(src)))
    }

    @Test
    fun garbageBytesReturnsNullViaTryDecode() {
        val garbage = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte())
        assertNull(FrameCodec.tryDecode(garbage))
    }

    @Test
    fun eventStrokeStartRoundtrip() {
        val tool = ToolSettings(
            kind = ToolKind.Pen,
            colorArgb = 0xFFE53935.toInt(),
            strokeWidthDp = 6f,
            brush = BrushType.Marker,
            shape = ShapeMode.Circle,
        )
        val src = Frame.Event(
            DrawingEvent.StrokeStart(
                seq = 1L,
                authorId = PeerId("peer-a"),
                strokeId = StrokeId("s1"),
                tool = tool,
                point = Point(0.1f, 0.2f),
            )
        )
        assertEquals(src, FrameCodec.decode(FrameCodec.encode(src)))
    }

    @Test
    fun eventStrokeAppendRoundtrip() {
        val src = Frame.Event(
            DrawingEvent.StrokeAppend(
                seq = 2L,
                authorId = PeerId.Local,
                strokeId = StrokeId("s1"),
                points = listOf(Point(0.2f, 0.3f), Point(0.4f, 0.5f)),
            )
        )
        assertEquals(src, FrameCodec.decode(FrameCodec.encode(src)))
    }

    @Test
    fun eventStrokeEndRoundtrip() {
        val src = Frame.Event(
            DrawingEvent.StrokeEnd(seq = 3L, authorId = PeerId.Local, strokeId = StrokeId("s1"))
        )
        assertEquals(src, FrameCodec.decode(FrameCodec.encode(src)))
    }

    @Test
    fun eventClearRoundtrip() {
        val src = Frame.Event(DrawingEvent.Clear(seq = 4L, authorId = PeerId("peer-b")))
        assertEquals(src, FrameCodec.decode(FrameCodec.encode(src)))
    }

    @Test
    fun eventUndoRoundtrip() {
        val src = Frame.Event(
            DrawingEvent.Undo(seq = 5L, authorId = PeerId.Local, strokeId = StrokeId("s1"))
        )
        assertEquals(src, FrameCodec.decode(FrameCodec.encode(src)))
    }

    @Test
    fun photoMetaRoundtrip() {
        val src = Frame.PhotoMeta(
            payloadId = 12345L,
            byteSize = 2_400_000L,
            widthPx = 1920,
            heightPx = 1280,
            mime = "image/jpeg",
        )
        assertEquals(src, FrameCodec.decode(FrameCodec.encode(src)))
    }

    @Test
    fun photoRemoveRoundtrip() {
        val src: Frame = Frame.PhotoRemove
        assertEquals(src, FrameCodec.decode(FrameCodec.encode(src)))
    }

    @Test
    fun mergeBackgroundRoundtrip() {
        listOf(Frame.MergeBackground(true), Frame.MergeBackground(false)).forEach { src ->
            assertEquals(src, FrameCodec.decode(FrameCodec.encode(src)))
        }
    }
}
