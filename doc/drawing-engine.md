# 드로잉 엔진

## 1. 데이터 모델

```kotlin
data class Point(val x: Float, val y: Float)              // 캔버스 정규화 좌표 (0..1)

enum class ToolKind { Pen, Eraser, Sticker, Eyedropper }
// Sticker = 스티커 배치/편집 모드, Eyedropper = 스포이드(색 추출) 모드
enum class BrushCapStyle { Round, Square }

enum class BrushType(
    val displayName: String,
    val description: String,
    val capStyle: BrushCapStyle,
    val alpha: Float,
    val widthScale: Float,
) {
    Pen, Pencil, Ink, Marker, Highlighter, Crayon,   // 기본 6종
    Airbrush, Blur,                                  // 분사 / 번짐 (특수 렌더)
    Neon, Dash, Rainbow, Calligraphy                 // 발광 / 점선 / 무지개 / 속도-굵기
    // 각 항목이 cap, 알파, 굵기 배수를 들고 있어 색·굵기와 직교.
    // Airbrush/Blur/Neon/Rainbow/Calligraphy 는 StrokeRenderer 에서 전용 렌더 분기.
}

enum class ShapeMode(val displayName: String) {
    None, Circle, Rect, Triangle, Pentagon, Hexagon, Star, Heart
    // None = 자유 곡선(중간점 베지어). 그 외 = 첫·마지막 점을 바운딩 박스로 도형 외곽선 1개.
}

data class ToolSettings(
    val kind: ToolKind,
    val colorArgb: Int,
    val strokeWidthDp: Float,
    val brush: BrushType = BrushType.Pen,
    val shape: ShapeMode = ShapeMode.None,
    val fill: Boolean = false,            // 도형 채우기 — true 면 외곽선 대신 색 채움(shape != None)
    val stickerKey: StickerKey? = null,   // ToolKind.Sticker 일 때 배치할 스티커
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
    // PlaceSticker / TransformSticker / RemoveSticker
}

// 스티커 — stroke 아닌 새 요소 타입. 픽셀이 아니라 {key + 변환} 메타로만 보관,
// 렌더 시 StickerRenderer 가 key → 자체 벡터로 치환. 색은 key 마다 고정(Candy Pop 테마).
enum class StickerKey(val displayName: String) {
    Heart, Star, Smile, Flower, Cloud, Sun, Moon, Rainbow, Drop, Lightning, Diamond, Sparkle
}

data class Sticker(
    val id: StickerId,
    val authorId: PeerId,
    val key: StickerKey,
    val cx: Float, val cy: Float,      // 중심 정규화 좌표 (0..1)
    val scale: Float,                  // 캔버스 짧은 변 대비 크기 비율
    val rotationDeg: Float,            // 시계방향 회전
)

// 동기화 응답 시 stroke + 스티커를 한 묶음으로 캡슐화 (FILE 페이로드 CBOR).
data class CanvasSnapshot(val strokes: List<Stroke>, val stickers: List<Sticker> = emptyList())

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
// stroke·스티커를 시간순 한 스택에 섞는 통합 undo 항목.
sealed interface UndoItem {
    data class StrokeRef(val id: StrokeId) : UndoItem
    data class StickerRef(val id: StickerId) : UndoItem
}

class CanvasState {
    val strokes: SnapshotStateList<Stroke> = mutableStateListOf()
    val openStrokes: SnapshotStateMap<StrokeId, Stroke> = mutableStateMapOf()
    val stickers: SnapshotStateList<Sticker> = mutableStateListOf()
    private val undoStack: SnapshotStateList<UndoItem> = mutableStateListOf()  // stroke+스티커 통합

    var background: BackgroundImage? by mutableStateOf(null)
        private set
    var backgroundColor: Int by mutableStateOf(WHITE)   // 사진 없을 때 바탕색. 캔버스 속성.
        private set

    fun apply(event: DrawingEvent) { /* Stroke* / Clear / Undo / *Sticker dispatch */ }
    fun applySnapshot(strokes: List<Stroke>, stickers: List<Sticker> = emptyList()) { /* 전체 교체 */ }
    fun setBackground(image: BackgroundImage?) { background = image }
    fun setBackgroundColor(argb: Int) { backgroundColor = argb }
}
```

- `apply`는 **순수에 가깝게**. 로컬 입력과 원격 인바운드 이벤트가 모두 여기로 들어옴 → 모드 분기 없음.
- **스티커**: `PlaceSticker` → 추가 + undoStack push, `TransformSticker`(이동/크기/회전 공용) → 교체(undo 불변), `RemoveSticker` → 제거 + undo 항목 제거.
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

손떨림 보정(입력 단계):
- `DrawingViewModel.Smoothing`(끔/약/강, alpha 1.0/0.5/0.28). `DrawingCanvas` 입력에서 점에
  지수이동평균(EMA)을 적용한 **보정된 점만** 저장·전송 → 동기화·저장·undo 자동. 렌더 곡선 평활화와 별개.

스포이드(`ToolKind.Eyedropper`):
- 누른 채 드래그하면 조준 십자가 따라오고 떼는 순간 그 지점 색을 추출. `CanvasColorSampler` 가
  `PngComposer` 합성 비트맵의 픽셀을 읽어("보이는 색=집히는 색") `selectColor` 로 펜 색에 적용.

## 4. Compose 렌더링

레이어 순서 (아래가 먼저, 위가 나중):

```
0. backgroundColor          (바탕색 채움. 기본 흰색 — 사진이 그 위에 깔림)
1. background?.bitmap        (사진. ContentScale.Fit으로 캔버스에 맞춤, 트레이싱 표시 알파 적용)
2. state.strokes             (완료된 획)
3. state.openStrokes.values  (진행 중 획)
4. state.stickers            (스티커 — StickerRenderer.drawSticker)
5. 안내선(가이드라인)         (격자/십자선 — 로컬 전용)
6. 스티커 선택 핸들           (스티커 모드 + 선택됨 — 바운딩 박스 + 크기·회전/삭제 핸들)
7. brushIndicator            (커서 위치 펜 발자국 — 그리기 모드)
8. 스포이드 조준 십자          (스포이드 모드 + 누르는 중 — drawEyedropperCursor)
```

- **완료된 획 비트맵 캐시**는 Phase 5(다듬기)에서. 현재 트래픽 수준에선 Compose RenderNode 캐싱만으로 충분.
- 도형 모드(`ShapeMode != None`)면 첫·마지막 점을 바운딩 박스로 도형 하나 — `tool.fill` 이면 색 채움(`Fill`), 아니면 외곽선(`Stroke`). 자유 곡선은 직선 폴리라인이 아니라 **인접 점의 중간점을 잇는 2차 베지어**(각 점이 control)로 그려 꺾임을 둥글게 한다(`buildFreehandPath`). 무지개·붓펜은 색·굵기가 구간마다 달라 한 Path 로 못 그리므로 `forEachSmoothPiece` 로 조각별 베지어를 그린다.
- 브러시는 `BrushType`의 `capStyle`/`alpha`/`widthScale`을 cap/색알파/굵기에 곱해 적용.
- 스티커는 `withTransform { translate(중심); rotate(deg) }` 안에서 key 별 벡터를 그림. `drawStroke` 와 마찬가지로 화면·PNG·미니뷰가 같은 `drawSticker` 함수 공유.

## 5. 사진 입력 (Phase 1.5)

세 진입 경로:

| 경로 | 구현 |
|---|---|
| **사진 선택** | Android `PhotoPicker` API (`ActivityResultContracts.PickVisualMedia`) — 권한 불필요 |
| **사진 촬영** | `ActivityResultContracts.TakePicture` (시스템 카메라 앱) — **CAMERA 권한 미선언**, 런타임 프롬프트 없음 |
| **원격 수신** | Nearby Connections `Payload.Type.FILE` → `onPayloadTransferUpdate` SUCCESS 시점에 디코딩 |

세 경로 모두 결과는 `BackgroundImage(bitmap, widthPx, heightPx, source)`를 만들어 `canvas.setBackground(image)` 호출.

사진 적용 시:
- 캔버스 종횡비를 사진 비율로 변경
- 기존 stroke는 정규화 좌표라 자동 리스케일

## 6. 되돌리기

- **함께 그리기 단일 모드 (현재)**: `Undo`/`Clear`/지우개가 `authorId` 로 거르지 않음 — 함께 모드에선 자기·상대 stroke 모두 되돌리기/삭제 가능(공유 캔버스, collaborative undo). 모임 모드는 자기 캔버스만 영향(미니 뷰는 read-only). (author-locked "따라 그리기" 옵션은 Phase 6 보류.)
- **통합 undo**: undoStack 은 `UndoItem`(StrokeRef/StickerRef) 을 시간순으로 담아 "되돌리기" 버튼이 stroke·스티커 구분 없이 최근 *추가* 동작을 취소. StrokeRef → `Undo`, StickerRef → `RemoveSticker` emit.
- **스티커 변형(이동/크기/회전)은 undo 대상 아님** — 라이브 편집(commit-on-end). 배치/삭제만 undo 스택에 반영. 삭제는 선택 시 X 핸들로도 가능.
- `Undo` 이벤트는 `strokeId` 지정 방식. 시퀀스 기반 "마지막 N개"는 협업에서 의미 흔들리므로 피함.
- 다시 실행(redo)은 미지원 (필요해지면 별도 스택).
- 사진 자체는 되돌리기 대상 아님 — 별도 "사진 제거" 액션.

## 7. 캔버스 크기/DPI

- 좌표는 정규화 (위 참고).
- `strokeWidthDp`는 dp 단위로 송신. 수신 측에서 자기 `density`로 px 환산.
- 색은 ARGB Int로 그대로 전송 (32-bit 고정 폭).

## 8. PNG 내보내기 (구현 완료 — `works/PngComposer`)

```kotlin
fun exportPng(state: CanvasState, density: Float): Bitmap {
    val (w, h) = state.background?.let { it.widthPx to it.heightPx }
                 ?: defaultCanvasSize()                              // 정사각 또는 화면 비율
    val bitmap = createBitmap(w, h)
    val canvas = Canvas(bitmap)
    // 1. 배경: 사진이 있고 합치기 모드면 사진, 아니면 backgroundColor(기본 흰색)로 채움
    state.background?.bitmap?.let { canvas.drawImage(it, ...) } ?: canvas.drawColor(state.backgroundColor)
    // 2. 완료된 stroke들을 같은 drawStroke() 로직으로 합성
    state.strokes.forEach { drawStroke(it, ..., canvas) }
    // 3. 스티커를 stroke 위에 합성 (같은 drawSticker() 로직)
    state.stickers.forEach { drawSticker(it, ..., canvas) }
    return bitmap
}
```

- 렌더링은 화면용과 동일한 `StrokeRenderer.drawStroke` + `StickerRenderer.drawSticker` 재사용 (`CanvasDrawScope` + `ImageBitmap`). 완료된 stroke + 스티커를 합성 (진행 중/커서/선택 핸들 제외).
- 앱 내부 저장: `WorkStore.save` → `filesDir/works/<id>.png` + `.meta`. "최근 작업" 모달에 노출, 최대 100개.
- 외부 갤러리: `WorkStore.exportToGallery` → `MediaStore.Images` (`Pictures/DrawingTogether`). `IS_PENDING` 패턴 + `MediaScannerConnection.scanFile` 로 갤러리 즉시 반영. 미리보기 화면의 "저장"/"공유" 액션에서 호출.
- 진행 중 stroke는 export에 포함 안 됨 (사용자가 export 누르는 시점엔 들고 있던 손가락 떼는 것이 자연).
