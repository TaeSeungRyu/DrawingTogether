package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushShape
import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import com.rts.rys.ryy.drawingtogether.drawing.model.PeerId
import com.rts.rys.ryy.drawingtogether.drawing.model.Point
import com.rts.rys.ryy.drawingtogether.drawing.model.StrokeId
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolKind
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolSettings

// Phase 1: 단일 작성자. Phase 3에서 BT 인바운드 이벤트도 같은 apply() 루프로 흘려보낼 예정.
class DrawingViewModel : ViewModel() {
    val canvas = CanvasState()

    private val author = PeerId.Local
    private var seq: Long = 0L

    var tool by mutableStateOf(ToolSettings.defaultPen())
        private set

    fun selectColor(argb: Int) {
        tool = tool.copy(kind = ToolKind.Pen, colorArgb = argb)
    }

    fun selectEraser() {
        tool = tool.copy(kind = ToolKind.Eraser)
    }

    fun setStrokeWidth(dp: Float) {
        tool = tool.copy(strokeWidthDp = dp)
    }

    fun setBrushShape(shape: BrushShape) {
        tool = tool.copy(shape = shape)
    }

    fun strokeStart(strokeId: StrokeId, point: Point) {
        emit(DrawingEvent.StrokeStart(nextSeq(), author, strokeId, tool, point))
    }

    fun strokeAppend(strokeId: StrokeId, points: List<Point>) {
        if (points.isEmpty()) return
        emit(DrawingEvent.StrokeAppend(nextSeq(), author, strokeId, points))
    }

    fun strokeEnd(strokeId: StrokeId) {
        emit(DrawingEvent.StrokeEnd(nextSeq(), author, strokeId))
    }

    fun undoLastLocal() {
        val id = canvas.lastLocalStrokeId() ?: return
        emit(DrawingEvent.Undo(nextSeq(), author, id))
    }

    fun clearAll() {
        emit(DrawingEvent.Clear(nextSeq(), author))
    }

    private fun emit(event: DrawingEvent) {
        canvas.apply(event)
    }

    private fun nextSeq(): Long {
        seq += 1
        return seq
    }
}
