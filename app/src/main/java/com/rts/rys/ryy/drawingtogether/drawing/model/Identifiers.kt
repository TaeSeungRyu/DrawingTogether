package com.rts.rys.ryy.drawingtogether.drawing.model

import java.util.UUID

@JvmInline
value class StrokeId(val value: String) {
    companion object {
        fun random(): StrokeId = StrokeId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class PeerId(val value: String) {
    companion object {
        // Phase 1: 로컬 단일 작성자 플레이스홀더. Phase 2에서 설치당 ULID로 교체.
        val Local: PeerId = PeerId("local")
    }
}
