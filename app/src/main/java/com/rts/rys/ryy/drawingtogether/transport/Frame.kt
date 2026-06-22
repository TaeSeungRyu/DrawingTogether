package com.rts.rys.ryy.drawingtogether.transport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 와이어 메시지. doc/protocol.md §3 참고.
// Phase 2에서는 핸드셰이크/킵얼라이브/종료까지만. EVENT/SNAPSHOT/PHOTO는 Phase 3.
@Serializable
sealed class Frame {

    @Serializable
    @SerialName("hello")
    data class Hello(
        val proto: Int,
        val peerId: String,
        val nick: String,
    ) : Frame()

    @Serializable
    @SerialName("hello_ack")
    data class HelloAck(val peerId: String) : Frame()

    @Serializable
    @SerialName("ping")
    data class Ping(val ts: Long) : Frame()

    @Serializable
    @SerialName("pong")
    data class Pong(val ts: Long) : Frame()

    @Serializable
    @SerialName("bye")
    data class Bye(val reason: String) : Frame()
}

const val PROTO_VERSION: Int = 1
