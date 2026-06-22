package com.rts.rys.ryy.drawingtogether.drawing.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rts.rys.ryy.drawingtogether.drawing.model.BackgroundImage
import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import com.rts.rys.ryy.drawingtogether.drawing.model.Stroke
import com.rts.rys.ryy.drawingtogether.drawing.model.StrokeId

// 모든 드로잉 이벤트(로컬 + 향후 원격)가 apply()를 통해 적용되는 캔버스 상태 컨테이너.
// SnapshotState를 사용해 Compose가 변경을 자동 추적.
class CanvasState {
    private val _strokes = mutableStateListOf<Stroke>()
    val strokes: List<Stroke> get() = _strokes

    private val _openStrokes = mutableStateMapOf<StrokeId, Stroke>()
    val openStrokes: Map<StrokeId, Stroke> get() = _openStrokes

    // 완료된 stroke 의 시간순 스택. "되돌리기" 버튼이 마지막 항목을 pop.
    // "함께 그리기" 모드라 자기/상대 구분 없이 모두 들어감 (collaborative undo).
    private val _undoStack = mutableStateListOf<StrokeId>()

    val canUndo: Boolean get() = _undoStack.isNotEmpty()

    fun lastFinishedStrokeId(): StrokeId? = _undoStack.lastOrNull()

    // 사진 배경. 이벤트(apply)가 아닌 별도 상태 — 사진은 도메인 이벤트가 아니라 캔버스 속성.
    private var _background: BackgroundImage? by mutableStateOf(null)
    val background: BackgroundImage? get() = _background

    fun setBackground(image: BackgroundImage?) {
        _background = image
    }

    // 저장 시 사진 배경을 PNG에 합쳐 굽을지 여부. 토글을 사진 추가보다 먼저 켜둘 수 있도록
    // 배경 유무와 독립적으로 보관. 기본 true — 기존 동작과 호환.
    private var _mergeBackgroundOnSave: Boolean by mutableStateOf(true)
    val mergeBackgroundOnSave: Boolean get() = _mergeBackgroundOnSave

    fun setMergeBackgroundOnSave(value: Boolean) {
        _mergeBackgroundOnSave = value
    }

    // 외부에서 들어온 snapshot 으로 stroke 을 전부 교체. "동기화" 버튼 응답을 받을 때 사용.
    // 일반 DrawingEvent 흐름과 달리 out-of-band — outboundEvents 로 흘려보내지 않으므로
    // 받는 쪽 단방향 적용용. _strokes/_openStrokes/_undoStack 을 비우고 받은 strokes 로 재구성.
    fun applySnapshot(strokes: List<Stroke>) {
        _strokes.clear()
        _openStrokes.clear()
        _undoStack.clear()
        _strokes.addAll(strokes)
        // collaborative undo — 받은 stroke 들 시간순으로 undoStack 에 push.
        strokes.forEach { _undoStack.add(it.id) }
    }

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
                _undoStack.add(finished.id)
            }
            is DrawingEvent.Clear -> {
                // "함께 그리기" 단일 모드 — Clear 는 양쪽 모두에 적용.
                // 누군가가 "전체 지우기" 하면 양쪽 캔버스 모두 빈다.
                _strokes.clear()
                _openStrokes.clear()
                _undoStack.clear()
            }
            is DrawingEvent.Undo -> {
                // strokeId 만으로 매칭 — 자기 / 상대 누구 stroke 든 제거 가능.
                // (지우개도 이 이벤트를 사용)
                val index = _strokes.indexOfFirst { it.id == event.strokeId }
                if (index >= 0) {
                    _strokes.removeAt(index)
                    _undoStack.remove(event.strokeId)
                }
            }
        }
    }
}
