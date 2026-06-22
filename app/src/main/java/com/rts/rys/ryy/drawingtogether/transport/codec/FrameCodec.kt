package com.rts.rys.ryy.drawingtogether.transport.codec

import com.rts.rys.ryy.drawingtogether.transport.Frame
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

// Frame <-> CBOR 바이트. Nearby Payload BYTES에 그대로 실어 보낼 용도.
@OptIn(ExperimentalSerializationApi::class)
object FrameCodec {
    private val cbor: Cbor = Cbor {
        ignoreUnknownKeys = true
    }

    fun encode(frame: Frame): ByteArray = cbor.encodeToByteArray(frame)

    fun decode(bytes: ByteArray): Frame = cbor.decodeFromByteArray(bytes)

    fun tryDecode(bytes: ByteArray): Frame? =
        runCatching { decode(bytes) }.getOrNull()
}
