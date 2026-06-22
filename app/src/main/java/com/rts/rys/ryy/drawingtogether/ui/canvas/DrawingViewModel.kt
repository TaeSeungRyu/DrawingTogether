package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.rts.rys.ryy.drawingtogether.drawing.engine.CanvasState
import com.rts.rys.ryy.drawingtogether.drawing.model.BackgroundImage
import com.rts.rys.ryy.drawingtogether.drawing.model.BrushType
import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import com.rts.rys.ryy.drawingtogether.drawing.model.PeerId
import com.rts.rys.ryy.drawingtogether.drawing.model.Point
import com.rts.rys.ryy.drawingtogether.drawing.model.ShapeMode
import com.rts.rys.ryy.drawingtogether.drawing.model.Stroke
import com.rts.rys.ryy.drawingtogether.drawing.model.StrokeId
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolKind
import com.rts.rys.ryy.drawingtogether.drawing.model.ToolSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Phase 3-A: 로컬 입력 + 원격 인바운드 이벤트 둘 다 같은 canvas.apply() 루프를 통과.
// outbound 는 SharedFlow 로 노출해 DrawingScreen 이 Connected 상태에서만 transport.send 로 흘려보낸다.
class DrawingViewModel : ViewModel() {
    val canvas = CanvasState()

    private val author = PeerId.Local
    private var seq: Long = 0L

    var tool by mutableStateOf(ToolSettings.defaultPen())
        private set

    // 로컬에서 발생한 모든 DrawingEvent. DrawingScreen 이 collect 해서
    // 멀티모드 연결 중이면 Frame.Event 로 송신. 멀티모드 아니면 그냥 흘려보냄.
    private val _outboundEvents = MutableSharedFlow<DrawingEvent>(extraBufferCapacity = 256)
    val outboundEvents: SharedFlow<DrawingEvent> = _outboundEvents.asSharedFlow()

    // 원격에서 받은 이벤트를 캔버스에 반영. outbound 로 재발행하지 않음(루프 방지).
    // 인바운드는 다른 작성자 ID 가 박힌 채로 도착하므로 canvas.apply() 의 author 분리 로직이 자동 적용됨.
    fun applyRemoteEvent(event: DrawingEvent) {
        canvas.apply(event)
    }

    fun selectColor(argb: Int) {
        tool = tool.copy(kind = ToolKind.Pen, colorArgb = argb)
    }

    // 지우개 버튼은 토글 — 지우개 상태에서 다시 누르면 펜으로 돌아온다.
    fun toggleEraser() {
        val nextKind = if (tool.kind == ToolKind.Eraser) ToolKind.Pen else ToolKind.Eraser
        tool = tool.copy(kind = nextKind)
    }

    fun setStrokeWidth(dp: Float) {
        tool = tool.copy(strokeWidthDp = dp)
    }

    fun setBrush(brush: BrushType) {
        tool = tool.copy(brush = brush)
    }

    fun setShape(shape: ShapeMode) {
        tool = tool.copy(shape = shape)
    }

    fun strokeStart(strokeId: StrokeId, point: Point) {
        if (tool.kind == ToolKind.Eraser) {
            eraseAt(point)
        } else {
            emit(DrawingEvent.StrokeStart(nextSeq(), author, strokeId, tool, point))
        }
    }

    fun strokeAppend(strokeId: StrokeId, points: List<Point>) {
        if (points.isEmpty()) return
        if (tool.kind == ToolKind.Eraser) {
            points.forEach { eraseAt(it) }
        } else {
            emit(DrawingEvent.StrokeAppend(nextSeq(), author, strokeId, points))
        }
    }

    fun strokeEnd(strokeId: StrokeId) {
        if (tool.kind == ToolKind.Eraser) {
            // 지우개는 진행 stroke을 만들지 않으므로 종료할 것도 없다.
            return
        }
        emit(DrawingEvent.StrokeEnd(nextSeq(), author, strokeId))
    }

    // 지우개 한 점이 자기 stroke과 충돌하면 그 stroke에 대해 Undo 이벤트 발행.
    // hit radius는 tool.strokeWidthDp에 비례하는 정규화 좌표(0..1). 캔버스 크기 없이도 동작.
    private fun eraseAt(p: Point) {
        val threshold = (tool.strokeWidthDp * 0.005f).coerceAtLeast(0.015f)
        val thresholdSq = threshold * threshold
        val hits = canvas.strokes
            .filter { it.authorId == author && strokeHitsPoint(it, p, thresholdSq) }
            .map { it.id }
        hits.forEach { id -> emit(DrawingEvent.Undo(nextSeq(), author, id)) }
    }

    private fun strokeHitsPoint(stroke: Stroke, p: Point, thresholdSq: Float): Boolean {
        val pts = stroke.points
        if (pts.isEmpty()) return false

        // 도형은 바운딩 박스 + 마진으로 히트 판정 (MVP 단순화).
        if (stroke.tool.shape != ShapeMode.None) {
            val a = pts.first()
            val b = pts.last()
            val margin = kotlin.math.sqrt(thresholdSq.toDouble()).toFloat()
            val minX = minOf(a.x, b.x) - margin
            val maxX = maxOf(a.x, b.x) + margin
            val minY = minOf(a.y, b.y) - margin
            val maxY = maxOf(a.y, b.y) + margin
            return p.x in minX..maxX && p.y in minY..maxY
        }

        // 자유 곡선: 폴리라인의 각 segment에 대한 점-선분 거리 검사.
        if (pts.size == 1) {
            val ex = p.x - pts[0].x
            val ey = p.y - pts[0].y
            return ex * ex + ey * ey < thresholdSq
        }
        for (i in 0 until pts.size - 1) {
            if (pointToSegmentDistanceSq(p, pts[i], pts[i + 1]) < thresholdSq) return true
        }
        return false
    }

    private fun pointToSegmentDistanceSq(p: Point, a: Point, b: Point): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) {
            val ex = p.x - a.x
            val ey = p.y - a.y
            return ex * ex + ey * ey
        }
        val t = (((p.x - a.x) * dx) + ((p.y - a.y) * dy)) / lenSq
        val tc = t.coerceIn(0f, 1f)
        val cx = a.x + tc * dx
        val cy = a.y + tc * dy
        val ex = p.x - cx
        val ey = p.y - cy
        return ex * ex + ey * ey
    }

    fun undoLastLocal() {
        val id = canvas.lastLocalStrokeId() ?: return
        emit(DrawingEvent.Undo(nextSeq(), author, id))
    }

    fun clearAll() {
        emit(DrawingEvent.Clear(nextSeq(), author))
    }

    fun setBackground(image: BackgroundImage?) {
        canvas.setBackground(image)
    }

    fun setMergeBackgroundOnSave(value: Boolean) {
        canvas.setMergeBackgroundOnSave(value)
    }

    private fun emit(event: DrawingEvent) {
        canvas.apply(event)
        _outboundEvents.tryEmit(event)
    }

    private fun nextSeq(): Long {
        seq += 1
        return seq
    }
}
