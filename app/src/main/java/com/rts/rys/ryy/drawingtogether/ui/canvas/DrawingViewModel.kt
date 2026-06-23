package com.rts.rys.ryy.drawingtogether.ui.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
//
// Phase 4-C: 인바운드 라우팅 분기 도입.
// - Shared (싱글 + 1:1 함께 모드): 인바운드도 내 canvas 에 적용 — 캔버스를 공유하는 협업.
// - PerPeer (1:N 모임 모드): 인바운드를 발신자 authorId 별 peerCanvases 로 라우팅 —
//   각자 자기 영역, 내 canvas 는 내 stroke 만.
enum class CanvasRouting {
    Shared,
    PerPeer,
}

class DrawingViewModel : ViewModel() {
    val canvas = CanvasState()

    // Phase 4-C: 모임 모드에서 peer 별로 받는 인바운드 stroke 을 담는 캔버스들.
    // SnapshotStateMap 이라 새 peerId 가 등장하면 미니 뷰 (4-E) 가 자동으로 등장.
    // Shared 라우팅에서는 사용하지 않는다.
    val peerCanvases: SnapshotStateMap<PeerId, CanvasState> = mutableStateMapOf()

    private var routing: CanvasRouting = CanvasRouting.Shared

    fun setRouting(value: CanvasRouting) {
        routing = value
    }

    // Phase 4-B: 멀티 모드에서는 실제 SessionManager.peerId 로 교체된다.
    // DrawingScreen 진입 시 setAuthor() 호출 — 싱글 모드는 PeerId.Local 그대로 유지.
    private var author: PeerId = PeerId.Local
    private var seq: Long = 0L

    fun setAuthor(peerId: PeerId) {
        author = peerId
    }

    var tool by mutableStateOf(ToolSettings.defaultPen())
        private set

    // 로컬에서 발생한 모든 DrawingEvent. DrawingScreen 이 collect 해서
    // 함께 모드 연결 중이면 Frame.Event 로 송신. 함께 모드 아니면 그냥 흘려보냄.
    private val _outboundEvents = MutableSharedFlow<DrawingEvent>(extraBufferCapacity = 256)
    val outboundEvents: SharedFlow<DrawingEvent> = _outboundEvents.asSharedFlow()

    // 원격에서 받은 이벤트를 캔버스에 반영. outbound 로 재발행하지 않음(루프 방지).
    // routing 에 따라 분기:
    //   Shared  → 내 canvas 에 그대로 적용 (1:1 함께 모드 = 공유 캔버스)
    //   PerPeer → 발신자 authorId 별 peerCanvases 에 라우팅 (1:N 모임 모드)
    fun applyRemoteEvent(event: DrawingEvent) {
        when (routing) {
            CanvasRouting.Shared -> canvas.apply(event)
            CanvasRouting.PerPeer -> {
                val target = peerCanvases.getOrPut(event.authorId) { CanvasState() }
                target.apply(event)
            }
        }
    }

    // "동기화" — 상대 캔버스 snapshot 으로 내 stroke 을 전부 교체. 사진 배경은 별도 경로.
    fun applyRemoteSnapshot(strokes: List<Stroke>) {
        canvas.applySnapshot(strokes)
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

    // 지우개 한 점이 stroke 과 충돌하면 그 stroke 에 대해 Undo 이벤트 발행.
    // "함께 그리기" 단일 모드 — 자기/상대 누구 stroke 든 지울 수 있다.
    // hit radius 는 tool.strokeWidthDp 에 비례하는 정규화 좌표(0..1). 캔버스 크기 없이도 동작.
    private fun eraseAt(p: Point) {
        val threshold = (tool.strokeWidthDp * 0.005f).coerceAtLeast(0.015f)
        val thresholdSq = threshold * threshold
        val hits = canvas.strokes
            .filter { strokeHitsPoint(it, p, thresholdSq) }
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
        val id = canvas.lastFinishedStrokeId() ?: return
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
