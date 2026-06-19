# 드로잉 엔진

## 1. 데이터 모델

```kotlin
data class Point(val x: Float, val y: Float)              // 캔버스 정규화 좌표 (0..1)

enum class ToolKind { Pen, Eraser }
enum class BrushCapStyle { Round, Square }

enum class BrushType(
    val displayName: String,
    val description: String,
    val capStyle: BrushCapStyle,
    val alpha: Float,
    val widthScale: Float,
) {
    Pen, Pencil, Ink, Marker, Highlighter, Crayon
    // 각 항목이 cap, 알파, 굵기 배수를 들고 있어 색·굵기와 직교
}

enum class ShapeMode(val displayName: String) {
    None, Circle, Rect, Triangle, Pentagon, Hexagon, Star, Heart
    // None = 자유 폴리라인. 그 외 = 첫·마지막 점을 바운딩 박스로 도형 외곽선 1개.
}

data class ToolSettings(
    val kind: ToolKind,
    val colorArgb: Int,
    val strokeWidthDp: Float,
    val brush: BrushType = BrushType.Pen,
    val shape: ShapeMode = ShapeMode.None,
)

data class Stroke(
    val id: StrokeId,                 // ULID/UUID — 협업 시 원격 ack/취소에 사용
    val authorId: PeerId,             // local 또는 remote
    val tool: ToolSettings,           // stroke 생성 시점의 도구 스냅샷
    val points: List<Point>,          // 시간 순서
)

sealed interface DrawingEvent {
    val seq: Long                      // 작성자 로컬 단조 증가 시퀀스
    val authorId: PeerId
    // StrokeStart / StrokeAppend / StrokeEnd / Clear / Undo
}

// Phase 1.5+ — 사진 배경 (옵션)
data class BackgroundImage(
    val widthPx: Int,
    val heightPx: Int,
    val bitmap: ImageBitmap,          // 로컬 캐시; 원격에선 페이로드 수신 후 디코딩
    val source: Source,               // Gallery / Camera / Remote
) {
    enum class Source { Gallery, Camera, Remote }
}
```

핵심:
- **좌표는 정규화(0..1)**. 사진 배경이 없으면 캔버스는 1:1 사각형, 있으면 사진 종횡비를 따른다.
- **획 단위가 아니라 이벤트 단위로 전송**. `StrokeStart` → `StrokeAppend` × N → `StrokeEnd`. 사용자가 손가락 떼기 전에도 원격에 즉시 보이도록.
- **`seq`는 작성자별 단조 증가**. 전역 순서는 보장하지 않음.
- **사진은 캔버스 상태의 옵션 필드** — 자기가 선택/촬영했든 원격에서 받았든 같은 setter로 진입.

## 2. 상태 머신

```kotlin
class CanvasState {
    val strokes: SnapshotStateList<Stroke> = mutableStateListOf()
    val openStrokes: SnapshotStateMap<StrokeId, Stroke> = mutableStateMapOf()
    private val undoStack: SnapshotStateList<StrokeId> = mutableStateListOf()  // 자기 author 한정

    var background: BackgroundImage? by mutableStateOf(null)
        private set

    fun apply(event: DrawingEvent) { /* StrokeStart/Append/End/Clear/Undo dispatch */ }
    fun setBackground(image: BackgroundImage?) { background = image }
}
```

- `apply`는 **순수에 가깝게**. 로컬 입력과 원격 인바운드 이벤트가 모두 여기로 들어옴 → 모드 분기 없음.
- 사진은 `apply` 밖의 별도 setter — 사진은 이벤트가 아니라 상태이기 때문.
- `mutableStateListOf` + `mutableStateMapOf`로 Compose가 변경된 부분만 다시 그리도록.

## 3. 입력 처리

```kotlin
Canvas(
    Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val first = awaitFirstDown()
            val strokeId = StrokeId.random()
            onStrokeStart(strokeId, first.position.normalize())
            // 포인터 이벤트당 점들을 묶어 onStrokeAppend(strokeId, points) 로 코얼레싱
            // 손가락 떼면 onStrokeEnd(strokeId)
        }
    }
) { /* draw */ }
```

성능 메모:
- 포인터 이벤트는 ~16ms마다 들어오지만 원격으로는 그렇게 자주 보낼 필요 없음 — **20–30ms 단위 배치 코얼레싱**.
- 베지어/카트멀-롬 보간은 **렌더링 단계에서만** 수행. 데이터에는 원본 점만.

## 4. Compose 렌더링

레이어 순서 (아래가 먼저, 위가 나중):

```
1. background?.bitmap        (사진. ContentScale.Fit으로 캔버스에 맞춤)
2. state.strokes             (완료된 획)
3. state.openStrokes.values  (진행 중 획)
4. brushIndicator            (커서 위치 펜 발자국)
```

- **완료된 획 비트맵 캐시**는 Phase 4(다듬기)에서. 현재 트래픽 수준에선 Compose RenderNode 캐싱만으로 충분.
- 도형 모드(`ShapeMode != None`)면 첫·마지막 점을 바운딩 박스로 도형 외곽선 하나, 그 외엔 폴리라인.
- 브러시는 `BrushType`의 `capStyle`/`alpha`/`widthScale`을 cap/색알파/굵기에 곱해 적용.

## 5. 사진 입력 (Phase 1.5)

세 진입 경로:

| 경로 | 구현 |
|---|---|
| **사진 선택** | Android `PhotoPicker` API (`ActivityResultContracts.PickVisualMedia`) — 권한 불필요 |
| **사진 촬영** | `CameraX` 또는 `ActionImageCapture` 인텐트 — 카메라 권한 필요 |
| **원격 수신** (Phase 3) | Nearby Connections `Payload.Type.FILE` → 파일 경로 → 디코딩 |

세 경로 모두 결과는 `BackgroundImage(bitmap, widthPx, heightPx, source)`를 만들어 `canvas.setBackground(image)` 호출.

사진 적용 시:
- 캔버스 종횡비를 사진 비율로 변경
- 기존 stroke는 정규화 좌표라 자동 리스케일

## 6. 되돌리기

- 자기(local author) 획만 되돌리기 가능. 상대 획은 못 건드림 → 분쟁 회피.
- `Undo` 이벤트는 `strokeId` 지정 방식. 시퀀스 기반 "마지막 N개"는 협업에서 의미 흔들리므로 피함.
- 다시 실행(redo)은 미지원 (필요해지면 별도 스택).
- 사진 자체는 되돌리기 대상 아님 — 별도 "사진 제거" 액션.

## 7. 캔버스 크기/DPI

- 좌표는 정규화 (위 참고).
- `strokeWidthDp`는 dp 단위로 송신. 수신 측에서 자기 `density`로 px 환산.
- 색은 ARGB Int로 그대로 전송 (32-bit 고정 폭).

## 8. PNG 내보내기 (Phase 4)

```kotlin
fun exportPng(state: CanvasState, density: Float): Bitmap {
    val (w, h) = state.background?.let { it.widthPx to it.heightPx }
                 ?: defaultCanvasSize()                              // 정사각 또는 화면 비율
    val bitmap = createBitmap(w, h)
    val canvas = Canvas(bitmap)
    // 1. 배경: 사진이 있으면 그리고, 없으면 흰색으로 채움
    state.background?.bitmap?.let { canvas.drawImage(it, ...) } ?: canvas.drawColor(WHITE)
    // 2. 완료된 stroke들을 같은 drawStroke() 로직으로 합성
    state.strokes.forEach { drawStroke(it, ..., canvas) }
    return bitmap
}
```

- 렌더링 코드는 화면용과 동일 — 외부 `Canvas` 인자만 받게 살짝 일반화.
- `MediaStore.Images`로 저장 → 사용자 갤러리에 등장.
- 진행 중 stroke는 export에 포함 안 됨 (사용자가 export 누르는 시점엔 들고 있던 손가락 떼는 것이 자연).
