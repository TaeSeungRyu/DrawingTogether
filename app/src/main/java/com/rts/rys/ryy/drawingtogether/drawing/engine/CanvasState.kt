package com.rts.rys.ryy.drawingtogether.drawing.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rts.rys.ryy.drawingtogether.drawing.model.BackgroundImage
import com.rts.rys.ryy.drawingtogether.drawing.model.CanvasAspect
import com.rts.rys.ryy.drawingtogether.drawing.model.DrawingEvent
import com.rts.rys.ryy.drawingtogether.drawing.model.Sticker
import com.rts.rys.ryy.drawingtogether.drawing.model.StickerId
import com.rts.rys.ryy.drawingtogether.drawing.model.Stroke
import com.rts.rys.ryy.drawingtogether.drawing.model.StrokeId
import com.rts.rys.ryy.drawingtogether.drawing.model.TextElement
import com.rts.rys.ryy.drawingtogether.drawing.model.TextId

// 통합 undo 스택 항목 — stroke·스티커·텍스트를 시간순으로 한 스택에 섞어 담아 "되돌리기" 가
// 종류 구분 없이 최근 *추가* 동작을 취소하게 한다.
sealed interface UndoItem {
    data class StrokeRef(val id: StrokeId) : UndoItem
    data class StickerRef(val id: StickerId) : UndoItem
    data class TextRef(val id: TextId) : UndoItem
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

    // 배치된 텍스트. 불변 요소 — 추가/삭제만 있고 변형 없음.
    private val _texts = mutableStateListOf<TextElement>()
    val texts: List<TextElement> get() = _texts

    // 완료된 *추가* 동작의 시간순 스택. "되돌리기" 버튼이 마지막 항목을 pop.
    // stroke·스티커를 섞어 담는 통합 undo — "함께 그리기" 모드라 자기/상대 구분 없이 모두 들어감.
    private val _undoStack = mutableStateListOf<UndoItem>()

    val canUndo: Boolean get() = _undoStack.isNotEmpty()

    fun lastUndoable(): UndoItem? = _undoStack.lastOrNull()

    // 요소(stroke/스티커/텍스트) 생성 순번 발급기. 세 종류가 하나의 카운터를 공유해 서로 섞인
    // 시간순을 갖는다 → 스냅샷 복원 시 seq 로 병합정렬하면 통합 undo 순서를 복원할 수 있다(#8).
    private var _elementSeq: Long = 0L
    private fun nextSeq(): Long = ++_elementSeq

    // 캐시 무효화용 리비전 — "완료된 stroke 비트맵 캐시"(DrawingCanvas)가 이 값이 바뀔 때만
    // 캐시를 다시 렌더한다. 완료 stroke 집합/배경/배경색이 바뀔 때만 증가(진행 중 stroke·스티커는
    // 라이브로 위에 그리므로 무관). Compose 가 추적하도록 mutableStateOf.
    private var _contentRevision: Int by mutableStateOf(0)
    val contentRevision: Int get() = _contentRevision
    private fun bumpRevision() { _contentRevision++ }

    // 사진 배경. 이벤트(apply)가 아닌 별도 상태 — 사진은 도메인 이벤트가 아니라 캔버스 속성.
    private var _background: BackgroundImage? by mutableStateOf(null)
    val background: BackgroundImage? get() = _background

    fun setBackground(image: BackgroundImage?) {
        _background = image
        bumpRevision()
    }

    // 캔버스 배경색 — 사진이 없을 때(또는 사진 미포함 저장 시) 흰색 대신 칠하는 바탕색.
    // 사진 배경과 같은 "캔버스 속성" 이라 apply() 가 아닌 별도 setter. 기본 흰색.
    private var _backgroundColor: Int by mutableStateOf(0xFFFFFFFF.toInt())
    val backgroundColor: Int get() = _backgroundColor

    fun setBackgroundColor(argb: Int) {
        _backgroundColor = argb
        bumpRevision()
    }

    // 빈 캔버스(사진 없음)의 가로세로 비율. 사진이 있으면 사진 비율이 우선(렌더 측에서 분기).
    // 배경색과 같은 캔버스 속성 — Clear 로 지워지지 않고, apply() 가 아닌 별도 setter. 기본 Free.
    // 비율 변경 시 캔버스 크기가 바뀌어 비트맵 캐시(remember(canvasSize))가 자동 재계산되므로 bumpRevision 불필요.
    private var _aspect: CanvasAspect by mutableStateOf(CanvasAspect.Free)
    val aspect: CanvasAspect get() = _aspect

    fun setCanvasAspect(value: CanvasAspect) {
        _aspect = value
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
    fun applySnapshot(
        strokes: List<Stroke>,
        stickers: List<Sticker> = emptyList(),
        texts: List<TextElement> = emptyList(),
        aspect: CanvasAspect = _aspect,
        backgroundColor: Int = _backgroundColor,
    ) {
        _strokes.clear()
        _openStrokes.clear()
        _stickers.clear()
        _texts.clear()
        _undoStack.clear()
        _strokes.addAll(strokes)
        _stickers.addAll(stickers)
        _texts.addAll(texts)
        _aspect = aspect
        _backgroundColor = backgroundColor
        // collaborative undo — 세 종류를 생성 순번(seq)으로 병합정렬해 실제 시간순으로 push(#8).
        // sortedBy 는 안정 정렬이라, seq 가 모두 0(구 데이터·미부여)이면 기존 동작(stroke→스티커→텍스트)으로 폴백.
        val ordered = buildList {
            strokes.forEach { add(it.seq to UndoItem.StrokeRef(it.id)) }
            stickers.forEach { add(it.seq to UndoItem.StickerRef(it.id)) }
            texts.forEach { add(it.seq to UndoItem.TextRef(it.id)) }
        }.sortedBy { it.first }
        ordered.forEach { _undoStack.add(it.second) }
        // 이후 로컬 추가가 가져온 요소들보다 뒤 순번을 갖도록 카운터를 최대값 이상으로.
        _elementSeq = maxOf(
            _elementSeq,
            strokes.maxOfOrNull { it.seq } ?: 0L,
            stickers.maxOfOrNull { it.seq } ?: 0L,
            texts.maxOfOrNull { it.seq } ?: 0L,
        )
        bumpRevision()
    }

    // 전체 초기화 — 타임랩스 재생에서 처음부터 다시 쌓을 때(seek/rebuild) 사용.
    // 일반 그리기 경로에선 쓰지 않는다(apply(Clear) 와 달리 배경까지 비움).
    fun reset() {
        _strokes.clear()
        _openStrokes.clear()
        _stickers.clear()
        _texts.clear()
        _undoStack.clear()
        _elementSeq = 0L
        _background = null
        _backgroundColor = 0xFFFFFFFF.toInt()
        _aspect = CanvasAspect.Free
        bumpRevision()
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
                _strokes.add(finished.copy(seq = nextSeq()))
                _undoStack.add(UndoItem.StrokeRef(finished.id))
                bumpRevision()
            }
            is DrawingEvent.Clear -> {
                // "함께 그리기" 단일 모드 — Clear 는 양쪽 모두에 적용.
                // 누군가가 "전체 지우기" 하면 양쪽 캔버스 모두 빈다 (stroke + 스티커).
                _strokes.clear()
                _openStrokes.clear()
                _stickers.clear()
                _texts.clear()
                _undoStack.clear()
                bumpRevision()
            }
            is DrawingEvent.Undo -> {
                // strokeId 만으로 매칭 — 자기 / 상대 누구 stroke 든 제거 가능.
                // (지우개도 이 이벤트를 사용)
                val index = _strokes.indexOfFirst { it.id == event.strokeId }
                if (index >= 0) {
                    _strokes.removeAt(index)
                    _undoStack.remove(UndoItem.StrokeRef(event.strokeId))
                    bumpRevision()
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
                        seq = nextSeq(),
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
            is DrawingEvent.PlaceText -> {
                _texts.add(
                    TextElement(
                        id = event.textId,
                        authorId = event.authorId,
                        text = event.text,
                        cx = event.cx,
                        cy = event.cy,
                        sizeFrac = event.sizeFrac,
                        colorArgb = event.colorArgb,
                        seq = nextSeq(),
                    )
                )
                _undoStack.add(UndoItem.TextRef(event.textId))
            }
            is DrawingEvent.RemoveText -> {
                val index = _texts.indexOfFirst { it.id == event.textId }
                if (index >= 0) {
                    _texts.removeAt(index)
                    _undoStack.remove(UndoItem.TextRef(event.textId))
                }
            }
        }
    }
}
