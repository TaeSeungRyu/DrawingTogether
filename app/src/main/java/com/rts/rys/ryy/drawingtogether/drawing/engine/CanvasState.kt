package com.rts.rys.ryy.drawingtogether.drawing.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rts.rys.ryy.drawingtogether.drawing.model.BackgroundImage
import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import com.rts.rys.ryy.drawingtogether.drawing.model.Sticker
import com.rts.rys.ryy.drawingtogether.drawing.model.StickerId
import com.rts.rys.ryy.drawingtogether.drawing.model.Stroke
import com.rts.rys.ryy.drawingtogether.drawing.model.StrokeId

// 통합 undo 스택 항목 — stroke 와 스티커를 시간순으로 한 스택에 섞어 담아 "되돌리기" 가
// 종류 구분 없이 최근 *추가* 동작을 취소하게 한다.
sealed interface UndoItem {
    data class StrokeRef(val id: StrokeId) : UndoItem
    data class StickerRef(val id: StickerId) : UndoItem
}

// 모든 드로잉 이벤트(로컬 + 향후 원격)가 apply()를 통해 적용되는 캔버스 상태 컨테이너.
// SnapshotState를 사용해 Compose가 변경을 자동 추적.
class CanvasState {
    private val _strokes = mutableStateListOf<Stroke>()
    val strokes: List<Stroke> get() = _strokes

    private val _openStrokes = mutableStateMapOf<StrokeId, Stroke>()
    val openStrokes: Map<StrokeId, Stroke> get() = _openStrokes

    // 배치된 스티커. stroke 와 별도 리스트지만 같은 정규화 좌표계를 쓴다.
    private val _stickers = mutableStateListOf<Sticker>()
    val stickers: List<Sticker> get() = _stickers

    // 완료된 *추가* 동작의 시간순 스택. "되돌리기" 버튼이 마지막 항목을 pop.
    // stroke·스티커를 섞어 담는 통합 undo — "함께 그리기" 모드라 자기/상대 구분 없이 모두 들어감.
    private val _undoStack = mutableStateListOf<UndoItem>()

    val canUndo: Boolean get() = _undoStack.isNotEmpty()

    fun lastUndoable(): UndoItem? = _undoStack.lastOrNull()

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

    // 외부에서 들어온 snapshot 으로 stroke + 스티커를 전부 교체. "동기화" 버튼 응답을 받을 때 사용.
    // 일반 DrawingEvent 흐름과 달리 out-of-band — outboundEvents 로 흘려보내지 않으므로
    // 받는 쪽 단방향 적용용. 모든 상태를 비우고 받은 데이터로 재구성.
    fun applySnapshot(strokes: List<Stroke>, stickers: List<Sticker> = emptyList()) {
        _strokes.clear()
        _openStrokes.clear()
        _stickers.clear()
        _undoStack.clear()
        _strokes.addAll(strokes)
        _stickers.addAll(stickers)
        // collaborative undo — 받은 stroke 들 시간순으로 undoStack 에 push.
        // 스티커는 추가 순서를 알 수 없으니 stroke 뒤에 이어 push (대략적 시간순).
        strokes.forEach { _undoStack.add(UndoItem.StrokeRef(it.id)) }
        stickers.forEach { _undoStack.add(UndoItem.StickerRef(it.id)) }
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
                _undoStack.add(UndoItem.StrokeRef(finished.id))
            }
            is DrawingEvent.Clear -> {
                // "함께 그리기" 단일 모드 — Clear 는 양쪽 모두에 적용.
                // 누군가가 "전체 지우기" 하면 양쪽 캔버스 모두 빈다 (stroke + 스티커).
                _strokes.clear()
                _openStrokes.clear()
                _stickers.clear()
                _undoStack.clear()
            }
            is DrawingEvent.Undo -> {
                // strokeId 만으로 매칭 — 자기 / 상대 누구 stroke 든 제거 가능.
                // (지우개도 이 이벤트를 사용)
                val index = _strokes.indexOfFirst { it.id == event.strokeId }
                if (index >= 0) {
                    _strokes.removeAt(index)
                    _undoStack.remove(UndoItem.StrokeRef(event.strokeId))
                }
            }
            is DrawingEvent.PlaceSticker -> {
                _stickers.add(
                    Sticker(
                        id = event.stickerId,
                        authorId = event.authorId,
                        key = event.key,
                        cx = event.cx,
                        cy = event.cy,
                        scale = event.scale,
                        rotationDeg = event.rotationDeg,
                    )
                )
                _undoStack.add(UndoItem.StickerRef(event.stickerId))
            }
            is DrawingEvent.TransformSticker -> {
                // 이동/크기/회전 공용 — 해당 스티커를 새 변환으로 교체. undo 스택 불변.
                val index = _stickers.indexOfFirst { it.id == event.stickerId }
                if (index >= 0) {
                    _stickers[index] = _stickers[index].copy(
                        cx = event.cx,
                        cy = event.cy,
                        scale = event.scale,
                        rotationDeg = event.rotationDeg,
                    )
                }
            }
            is DrawingEvent.RemoveSticker -> {
                val index = _stickers.indexOfFirst { it.id == event.stickerId }
                if (index >= 0) {
                    _stickers.removeAt(index)
                    _undoStack.remove(UndoItem.StickerRef(event.stickerId))
                }
            }
        }
    }
}
