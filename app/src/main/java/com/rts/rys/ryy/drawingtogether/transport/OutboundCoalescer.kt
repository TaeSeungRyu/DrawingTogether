package com.rts.rys.ryy.drawingtogether.transport

import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// outbound DrawingEvent 코얼레서.
// - 같은 strokeId 의 연속된 StrokeAppend 는 points 를 누적해 하나로 합침.
// - intervalMs (기본 25ms) 마다 누적된 Append 를 flush.
// - 비-Append 이벤트(Start/End/Clear/Undo) 가 오면 누적분 즉시 flush 후 그 이벤트도 즉시 전송.
//
// 효과:
// - 빠른 드래그 시 패킷 폭증 완화 — 60fps 입력이 ~40Hz 송신으로 줄어듦
// - 작은 점 묶음들이 큰 묶음 1개로 합쳐져 CBOR/Nearby 헤더 오버헤드 감소
// - 로컬 화면은 영향 없음 (canvas.apply 는 VM 안에서 즉시 발생) — 코얼레싱은 outbound 한정
suspend fun runOutboundCoalescer(
    input: Flow<DrawingEvent>,
    intervalMs: Long = 25L,
    send: suspend (DrawingEvent) -> Unit,
): Unit = coroutineScope {
    val mutex = Mutex()
    var pending: DrawingEvent.StrokeAppend? = null

    val flushJob = launch {
        while (isActive) {
            delay(intervalMs)
            mutex.withLock {
                pending?.let {
                    send(it)
                    pending = null
                }
            }
        }
    }

    try {
        input.collect { event ->
            mutex.withLock {
                if (event is DrawingEvent.StrokeAppend) {
                    val existing = pending
                    pending = if (existing != null && existing.strokeId == event.strokeId) {
                        existing.copy(
                            points = existing.points + event.points,
                            seq = event.seq,
                        )
                    } else {
                        existing?.let { send(it) }
                        event
                    }
                } else {
                    pending?.let { send(it) }
                    pending = null
                    send(event)
                }
            }
        }
    } finally {
        flushJob.cancel()
    }
}
