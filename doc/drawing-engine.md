# 드로잉 엔진

## 1. 데이터 모델

```kotlin
data class Point(val x: Float, val y: Float)                  // 캔버스 정규화 좌표 (0..1)

enum class ToolKind { Pen, Eraser }

data class ToolSettings(
    val kind: ToolKind,
    val colorArgb: Int,    // Eraser면 무시
    val strokeWidthDp: Float
)

data class Stroke(
    val id: StrokeId,            // ULID/UUID — 협업 시 원격 ack/취소에 사용
    val authorId: PeerId,        // local 또는 remote
    val tool: ToolSettings,
    val points: List<Point>      // 시간 순서
)

sealed interface DrawingEvent {
    val seq: Long                // 작성자 로컬 단조 증가 시퀀스
    val authorId: PeerId

    data class StrokeStart(...): DrawingEvent
    data class StrokeAppend(val strokeId: StrokeId, val points: List<Point>, ...): DrawingEvent
    data class StrokeEnd(val strokeId: StrokeId, ...): DrawingEvent
    data class Clear(...): DrawingEvent
    data class Undo(val strokeId: StrokeId, ...): DrawingEvent
}
```

핵심:
- **좌표는 정규화(0..1)**. 두 기기 화면 크기가 다를 수 있음. 캔버스의 종횡비는 양쪽이 협상으로 맞춤(둘 다 1:1 사각형으로 고정하는 게 가장 단순). 자세한 협상은 [protocol.md](protocol.md) 참고.
- **획 단위가 아니라 이벤트 단위로 전송**. `StrokeStart` → `StrokeAppend` × N → `StrokeEnd`. 사용자가 손가락 떼기 전에도 원격에 즉시 보이도록.
- **`seq`는 작성자별 단조 증가**. 전역 순서는 보장하지 않음(필요할 때만 자기 획에 한해 순서 보장).

## 2. 상태 머신

```kotlin
class CanvasState {
    val strokes: SnapshotStateList<Stroke> = mutableStateListOf()
    private val openStrokes: MutableMap<StrokeId, Stroke> = mutableMapOf()
    private val undoStack: ArrayDeque<StrokeId> = ArrayDeque()  // 자기 author 한정

    fun apply(event: DrawingEvent) {
        when (event) {
            is StrokeStart -> openStrokes[event.strokeId] = Stroke(event)
            is StrokeAppend -> openStrokes[event.strokeId]?.let {
                openStrokes[event.strokeId] = it.copy(points = it.points + event.points)
            }
            is StrokeEnd -> openStrokes.remove(event.strokeId)?.let { strokes += it }
            is Clear -> { strokes.clear(); openStrokes.clear() }
            is Undo -> strokes.removeAll { it.id == event.strokeId }
        }
    }
}
```

- `apply`는 **순수 함수에 가깝게**. 입력 이벤트와 BT 인바운드 이벤트가 모두 여기로 들어옴 → 솔로/협업 분기 없음.
- `mutableStateListOf` + 항목별 `key`로 Compose가 변경된 획만 다시 그리도록.

## 3. 입력 처리

```kotlin
Canvas(
    Modifier.pointerInput(toolSettings) {
        awaitEachGesture {
            val first = awaitFirstDown()
            val strokeId = StrokeId.random()
            emit(StrokeStart(strokeId, first.position.normalize(), toolSettings))
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { change ->
                    if (change.positionChanged()) {
                        emit(StrokeAppend(strokeId, listOf(change.position.normalize())))
                        change.consume()
                    }
                }
            } while (event.changes.any { it.pressed })
            emit(StrokeEnd(strokeId))
        }
    }
) { /* draw strokes */ }
```

성능 메모:
- 포인터 이벤트는 16ms마다 들어오지만 BT로는 그렇게 자주 보낼 필요 없음. **20–30ms 단위로 배치**해서 `StrokeAppend`에 묶어 전송(코얼레싱).
- 멀리 떨어진 점 두 개는 베지어/카트멀-롬으로 보간해서 그림. 보간은 **렌더링 단계에서만** 수행 — 데이터엔 원본 점만 저장(원격에서 자유롭게 보간 가능).

## 4. Compose 렌더링

`Canvas` 컴포저블 하나에서 모든 획을 그리되, 성능 함정 회피:

- **잉크/완료된 획**: 자주 안 변하므로 `graphicsLayer { compositingStrategy = Offscreen }` + 별도 `ImageBitmap` 레이어에 그려두고 캔버스에 비트맵만 draw. 새 획 완료될 때만 비트맵 갱신.
- **현재 그리는 중인 획**: 매 프레임 다시 그림. 점이 많아져도 비싸지 않음(수백 점 수준).
- 색·붓 굵기는 `Stroke.tool`에 들어있어 캔버스 상태 외부 의존 없음.

작은 코드 형태:

```kotlin
@Composable
fun DrawingCanvas(state: CanvasState, ...) {
    val finishedBitmap = remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(state.strokes.size) { /* repaint finished strokes onto bitmap */ }

    Canvas(Modifier.fillMaxSize()) {
        finishedBitmap.value?.let { drawImage(it) }
        state.openStrokes.values.forEach { drawStroke(it) }
    }
}
```

## 5. 되돌리기

- 사용자가 자기 획만 되돌릴 수 있도록. 상대 획은 못 건드림 → 분쟁 회피.
- `Undo` 이벤트는 `strokeId` 지정 방식. 시퀀스 기반 "마지막 N개 되돌리기"는 협업에서 의미가 흔들리므로 피함.
- 다시 실행(redo)은 초기 버전에선 미지원. 필요해지면 별도 스택.

## 6. 캔버스 크기/DPI

- 좌표는 정규화 사용 (위 참고).
- `strokeWidthDp`는 dp 단위로 송신. 수신 측에서 자기 `density`로 px 환산.
- 색은 ARGB Int로 그대로 전송 (32-bit 고정 폭).

## 7. 저장(나중에)

초기엔 메모리에만. 나중에 PNG로 내보내거나 세션 재생을 원하면:

- PNG 내보내기: `finishedBitmap` 그대로 `Bitmap.compress`.
- 세션 재생: `DrawingEvent` 시퀀스를 JSONL로 저장 → 같은 `apply` 루프로 리플레이.
