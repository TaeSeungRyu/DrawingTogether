package com.rts.rys.ryy.drawingtogether.drawing.engine

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import com.rts.rys.ryy.drawingtogether.drawing.model.PeerId
import com.rts.rys.ryy.drawingtogether.drawing.model.Stroke
import com.rts.rys.ryy.drawingtogether.drawing.model.StrokeId

// 모든 드로잉 이벤트(로컬 + 향후 원격)가 apply()를 통해 적용되는 캔버스 상태 컨테이너.
// SnapshotState를 사용해 Compose가 변경을 자동 추적.
class CanvasState {
    private val _strokes = mutableStateListOf<Stroke>()
    val strokes: List<Stroke> get() = _strokes

    private val _openStrokes = mutableStateMapOf<StrokeId, Stroke>()
    val openStrokes: Map<StrokeId, Stroke> get() = _openStrokes

    // 로컬 작성자(PeerId.Local) 획만 보관. 원격 획은 되돌리기 후보가 아님.
    private val _undoStack = mutableStateListOf<StrokeId>()

    val canUndo: Boolean get() = _undoStack.isNotEmpty()

    fun lastLocalStrokeId(): StrokeId? = _undoStack.lastOrNull()

    fun apply(event: DrawingEvent) {
        when (event) {
            is DrawingEvent.StrokeStart -> {
                _openStrokes[event.strokeId] = Stroke(
                    id = event.strokeId,
                    authorId = event.authorId,
                    tool = event.tool,
                    points = listOf(event.point),
                )
            }
            is DrawingEvent.StrokeAppend -> {
                val open = _openStrokes[event.strokeId] ?: return
                _openStrokes[event.strokeId] = open.copy(points = open.points + event.points)
            }
            is DrawingEvent.StrokeEnd -> {
                val finished = _openStrokes.remove(event.strokeId) ?: return
                _strokes.add(finished)
                if (finished.authorId == PeerId.Local) {
                    _undoStack.add(finished.id)
                }
            }
            is DrawingEvent.Clear -> {
                _strokes.clear()
                _openStrokes.clear()
                _undoStack.clear()
            }
            is DrawingEvent.Undo -> {
                val index = _strokes.indexOfFirst { it.id == event.strokeId }
                if (index >= 0) {
                    _strokes.removeAt(index)
                    _undoStack.remove(event.strokeId)
                }
            }
        }
    }
}
