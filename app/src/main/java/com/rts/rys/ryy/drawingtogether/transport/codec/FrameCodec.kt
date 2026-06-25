package com.rts.rys.ryy.drawingtogether.transport.codec

import com.rts.rys.ryy.drawingtogether.drawing.model.CanvasSnapshot
import com.rts.rys.ryy.drawingtogether.drawing.model.Stroke
import com.rts.rys.ryy.drawingtogether.transport.Frame
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

// Frame <-> CBOR 바이트. Nearby Payload BYTES 에 그대로 실어 보낼 용도.
// Stroke 리스트 직렬화도 같이 — Snapshot 응답이 BYTES 32KB 한도를 넘기 위해
// FILE 페이로드로 전송할 때 사용 (Phase 3.5-A).
@OptIn(ExperimentalSerializationApi::class)
object FrameCodec {
    private val cbor: Cbor = Cbor {
        ignoreUnknownKeys = true
    }

    fun encode(frame: Frame): ByteArray = cbor.encodeToByteArray(frame)

    fun decode(bytes: ByteArray): Frame = cbor.decodeFromByteArray(bytes)

    fun tryDecode(bytes: ByteArray): Frame? =
        runCatching { decode(bytes) }.getOrNull()

    // Snapshot strokes — FILE 페이로드 콘텐츠로 사용. 한도 무제한.
    fun encodeStrokes(strokes: List<Stroke>): ByteArray = cbor.encodeToByteArray(strokes)

    fun decodeStrokes(bytes: ByteArray): List<Stroke> = cbor.decodeFromByteArray(bytes)

    // Snapshot 캔버스 통째 (strokes + 스티커) — FILE 페이로드 콘텐츠. 스티커 도입 후 동기화 경로.
    fun encodeCanvas(snapshot: CanvasSnapshot): ByteArray = cbor.encodeToByteArray(snapshot)

    fun decodeCanvas(bytes: ByteArray): CanvasSnapshot = cbor.decodeFromByteArray(bytes)
}
