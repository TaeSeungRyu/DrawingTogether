package com.rts.rys.ryy.drawingtogether.transport.codec

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
}
