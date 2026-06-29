package com.rts.rys.ryy.drawingtogether.ui.timelapse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState
import com.rts.rys.ryy.drawingtogether.drawing.model.BackgroundImage
import com.rts.rys.ryy.drawingtogether.drawing.model.TimelapseEntry
import com.rts.rys.ryy.drawingtogether.drawing.model.TimelapseOp

// 타임랩스 재생 엔진 — 프레임 시계로 재생 위치(positionMs)를 연속 전진시키며, 그 시각을 지난
// 이벤트를 apply. 정지 구간도 실시간 그대로 흐름(슬라이더가 멈추지 않음). 빠르게 보려면 배속.
// seek 는 0부터 rebuild(역재생 안 함).
class TimelapsePlayer(
    private val canvas: CanvasState,
    private val entries: List<TimelapseEntry>,
    val durationMs: Long,
    private val loadBackground: (ref: String) -> BackgroundImage?,
) {
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var speed by mutableStateOf(1f)

    private var index = 0   // 다음에 적용할 entry

    private fun applyOp(op: TimelapseOp) {
        when (op) {
            is TimelapseOp.Draw -> canvas.apply(op.event)
            is TimelapseOp.BackgroundColor -> canvas.setBackgroundColor(op.argb)
            is TimelapseOp.BackgroundPhoto -> canvas.setBackground(op.ref?.let(loadBackground))
            is TimelapseOp.Snapshot -> canvas.applySnapshot(op.strokes, op.stickers)
        }
    }

    // 0부터 targetMs 까지 즉시(delay 없이) 재적용. seek/초기화 공용.
    fun rebuildTo(targetMs: Long) {
        canvas.reset()
        var i = 0
        while (i < entries.size && entries[i].atMs <= targetMs) {
            applyOp(entries[i].op)
            i++
        }
        index = i
        positionMs = targetMs.coerceIn(0L, durationMs)
    }

    fun play() {
        if (index >= entries.size) rebuildTo(0L)   // 끝에서 다시 누르면 처음부터
        isPlaying = true
    }

    fun pause() { isPlaying = false }

    fun seekTo(ms: Long) {
        isPlaying = false
        rebuildTo(ms)
    }

    // 재생 루프 — `LaunchedEffect(isPlaying)` 안에서 isPlaying 이 true 가 되면 호출.
    // 프레임마다 경과 시간(×배속)만큼 positionMs 를 전진시키고, 그 시각을 지난 이벤트를 적용.
    // → 정지 구간에도 슬라이더가 실시간으로 흐른다.
    suspend fun run() {
        var lastNanos = 0L
        while (isPlaying && positionMs < durationMs) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dtMs = (now - lastNanos) / 1_000_000f * speed
                    val next = (positionMs + dtMs.toLong()).coerceIn(0L, durationMs)
                    while (index < entries.size && entries[index].atMs <= next) {
                        applyOp(entries[index].op)
                        index++
                    }
                    positionMs = next
                }
                lastNanos = now
            }
        }
        if (positionMs >= durationMs) isPlaying = false
    }

    companion object {
        val SPEEDS = listOf(1f, 2f, 4f)
    }
}
