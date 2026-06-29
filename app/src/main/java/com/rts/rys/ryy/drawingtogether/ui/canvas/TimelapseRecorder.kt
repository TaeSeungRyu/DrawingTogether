package com.rts.rys.ryy.drawingtogether.ui.canvas

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import com.rts.rys.ryy.drawingtogether.drawing.model.TimelapseEntry
import com.rts.rys.ryy.drawingtogether.drawing.model.TimelapseOp

// 종료 시 반환되는 묶음 — 직렬화 로그(entries) + 배경 비트맵(파일로 저장될 것). 비직렬화 transient.
class RecordedTimelapse(
    val entries: List<TimelapseEntry>,
    val durationMs: Long,
    val backgrounds: List<ImageBitmap>,   // index = "bg-<index>" ref
)

// 그리는 과정을 메모리에만 임시 기록. 디스크 쓰기는 stop() 으로 묶음을 받아 TimelapseStore 가 한다.
// 저장 전 앱 종료 시 소실(설계 결정 — 증분 저장·복구 없음).
class TimelapseRecorder {

    var isRecording by mutableStateOf(false)
        private set

    private val entries = mutableListOf<TimelapseEntry>()
    private val backgrounds = mutableListOf<ImageBitmap>()
    private var startMs = 0L

    fun start() {
        entries.clear()
        backgrounds.clear()
        startMs = SystemClock.elapsedRealtime()
        isRecording = true
    }

    fun discard() {
        isRecording = false
        entries.clear()
        backgrounds.clear()
    }

    private fun now(): Long = SystemClock.elapsedRealtime() - startMs

    fun recordEvent(event: DrawingEvent) {
        if (!isRecording) return
        entries.add(TimelapseEntry(now(), TimelapseOp.Draw(event)))
    }

    fun recordBackgroundColor(argb: Int) {
        if (!isRecording) return
        entries.add(TimelapseEntry(now(), TimelapseOp.BackgroundColor(argb)))
    }

    // 사진 설정/변경 시 비트맵을 메모리에 보유하고 ref 마커를 남긴다. null = 제거.
    fun recordBackgroundPhoto(bitmap: ImageBitmap?) {
        if (!isRecording) return
        val ref = if (bitmap == null) {
            null
        } else {
            backgrounds.add(bitmap)
            "bg-${backgrounds.size - 1}"
        }
        entries.add(TimelapseEntry(now(), TimelapseOp.BackgroundPhoto(ref)))
    }

    // 종료 — 기록이 비어 있으면 null. 호출 후 녹화 off.
    fun stop(): RecordedTimelapse? {
        if (!isRecording) return null
        isRecording = false
        if (entries.isEmpty()) return null
        val duration = entries.last().atMs
        return RecordedTimelapse(
            entries = entries.toList(),
            durationMs = duration,
            backgrounds = backgrounds.toList(),
        )
    }
}
