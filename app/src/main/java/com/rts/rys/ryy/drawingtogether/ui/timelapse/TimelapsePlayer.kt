package com.rts.rys.ryy.drawingtogether.ui.timelapse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState
import com.rts.rys.ryy.drawingtogether.drawing.model.BackgroundImage
import com.rts.rys.ryy.drawingtogether.drawing.model.TimelapseEntry
import com.rts.rys.ryy.drawingtogether.drawing.model.TimelapseOp
import kotlinx.coroutines.delay

// 타임랩스 재생 엔진 — 빈 CanvasState 에 이벤트 로그를 시간순으로 다시 apply.
// 긴 정지 구간은 GAP_CAP_MS 로 압축, 배속 적용. seek 는 0부터 rebuild(역재생 안 함).
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
    suspend fun run() {
        while (isPlaying && index < entries.size) {
            val prevAt = if (index == 0) 0L else entries[index - 1].atMs
            val gap = (entries[index].atMs - prevAt).coerceAtLeast(0L)
            val wait = minOf(gap, GAP_CAP_MS)
            if (wait > 0) delay((wait / speed).toLong().coerceAtLeast(1L))
            if (!isPlaying) break
            applyOp(entries[index].op)
            positionMs = entries[index].atMs
            index++
        }
        if (index >= entries.size) {
            positionMs = durationMs
            isPlaying = false
        }
    }

    companion object {
        const val GAP_CAP_MS: Long = 800L
        val SPEEDS = listOf(1f, 2f, 4f)
    }
}
