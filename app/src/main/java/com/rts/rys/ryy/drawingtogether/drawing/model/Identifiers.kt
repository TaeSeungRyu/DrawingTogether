package com.rts.rys.ryy.drawingtogether.drawing.model

import kotlinx.serialization.Serializable
import java.util.UUID

@JvmInline
@Serializable
value class StrokeId(val value: String) {
    companion object {
        fun random(): StrokeId = StrokeId(UUID.randomUUID().toString())
    }
}

@JvmInline
@Serializable
value class PeerId(val value: String) {
    companion object {
        // Phase 1: 로컬 단일 작성자 플레이스홀더. Phase 2에서 설치당 UUID(SessionManager.peerId) 로 교체 예정.
        val Local: PeerId = PeerId("local")
    }
}
