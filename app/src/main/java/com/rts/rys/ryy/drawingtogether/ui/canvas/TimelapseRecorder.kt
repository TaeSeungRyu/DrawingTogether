package com.rts.rys.ryy.drawingtogether.ui.canvas

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import com.rts.rys.ryy.drawingtogether.drawing.model.Sticker
import com.rts.rys.ryy.drawingtogether.drawing.model.Stroke
import com.rts.rys.ryy.drawingtogether.drawing.model.TextElement
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

    // 기록 시작. 시작 시점의 캔버스(기록 버튼 전 작업)를 atMs=0 초기 상태로 심어 재생이 빈
    // 캔버스가 아니라 "이미 그려진 것"부터 시작하게 한다.
    fun start(
        initialStrokes: List<Stroke> = emptyList(),
        initialStickers: List<Sticker> = emptyList(),
        initialTexts: List<TextElement> = emptyList(),
        initialBgColor: Int = 0xFFFFFFFF.toInt(),
        initialBgPhoto: ImageBitmap? = null,
    ) {
        entries.clear()
        backgrounds.clear()
        startMs = SystemClock.elapsedRealtime()
        isRecording = true
        // 초기 상태 시딩(atMs=0). 배경색 → 배경사진 → 스냅샷 순.
        entries.add(TimelapseEntry(0L, TimelapseOp.BackgroundColor(initialBgColor)))
        if (initialBgPhoto != null) {
            backgrounds.add(initialBgPhoto)
            entries.add(TimelapseEntry(0L, TimelapseOp.BackgroundPhoto("bg-0")))
        }
        if (initialStrokes.isNotEmpty() || initialStickers.isNotEmpty() || initialTexts.isNotEmpty()) {
            entries.add(TimelapseEntry(0L, TimelapseOp.Snapshot(initialStrokes, initialStickers, initialTexts)))
        }
    }

    fun discard() {
        isRecording = false
        entries.clear()
        backgrounds.clear()
    }

    private fun now(): Long = SystemClock.elapsedRealtime() - startMs

    // 기록 시작 후 경과 ms (UI 타이머용). 비기록 시 0.
    fun elapsedMs(): Long = if (isRecording) SystemClock.elapsedRealtime() - startMs else 0L

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

    // 종료 — 기록 중 그린 게 없으면(시작 시점 시드만 있으면) null. 호출 후 녹화 off.
    fun stop(): RecordedTimelapse? {
        if (!isRecording) return null
        isRecording = false
        if (entries.none { it.op is TimelapseOp.Draw }) return null
        val duration = entries.last().atMs
        return RecordedTimelapse(
            entries = entries.toList(),
            durationMs = duration,
            backgrounds = backgrounds.toList(),
        )
    }
}
