# 드로잉 엔진

## 1. 데이터 모델

```kotlin
data class Point(val x: Float, val y: Float)              // 캔버스 정규화 좌표 (0..1)

enum class ToolKind { Pen, Eraser, Sticker, Eyedropper, Text }
// Sticker = 스티커 배치/편집 모드, Eyedropper = 스포이드(색 추출) 모드, Text = 텍스트 배치 모드
enum class BrushCapStyle { Round, Square }

enum class BrushType(
    val displayName: String,
    val description: String,
    val capStyle: BrushCapStyle,
    val alpha: Float,
    val widthScale: Float,
    val fixedWidthDp: Float? = null,   // non-null 이면 슬라이더 무시하고 이 굵기로 고정
) {
    Pen, Pencil, Fine, Ink, Marker, Highlighter, Crayon,   // 기본 + 세밀붓
    Airbrush, Blur,                                        // 분사 / 번짐 (특수 렌더)
    Neon, Dash, Rainbow, Calligraphy                       // 발광 / 점선 / 무지개 / 속도-굵기
    // 각 항목이 cap, 알파, 굵기 배수를 들고 있어 색·굵기와 직교.
    // Fine(세밀붓) = fixedWidthDp=0.8f 로 굵기 고정(슬라이더 숨김) — 세밀 묘사용.
    // Airbrush/Blur/Neon/Rainbow/Calligraphy 는 StrokeRenderer 에서 전용 렌더 분기.
}

// 빈 캔버스(사진 없음)의 가로세로 비율 프리셋. 사진이 있으면 사진 비율 우선.
enum class CanvasAspect(val label: String, val ratio: Float?) {
    Free, Square, Landscape4_3, Portrait3_4, Landscape16_9, Portrait9_16
    // ratio = width/height (Free=null → 화면 채움). 캔버스 속성(배경색과 동급).
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
    // PlaceText / RemoveText
    // (CanvasAspect 는 이벤트가 아니라 별도 Frame.CanvasAspectFrame 로 동기화 — 캔버스 속성)
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

// 텍스트 — 스티커처럼 배치형 요소. 불변·삭제전용(입력 종료 후 수정/이동 불가).
data class TextElement(
    val id: TextId,
    val authorId: PeerId,
    val text: String,
    val cx: Float, val cy: Float,      // 중심 정규화 좌표 (0..1)
    val sizeFrac: Float,               // 캔버스 짧은 변 대비 글자 크기 비율
    val colorArgb: Int,
)

// 동기화 응답 시 stroke + 스티커 + 텍스트 + 캔버스 비율 + 배경색을 한 묶음으로 캡슐화 (FILE 페이로드 CBOR).
data class CanvasSnapshot(
    val strokes: List<Stroke>,
    val stickers: List<Sticker> = emptyList(),
    val texts: List<TextElement> = emptyList(),
    val aspect: CanvasAspect = CanvasAspect.Free,
    val backgroundColor: Int = WHITE,
)

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
- **좌표는 정규화(0..1)**. 사진 배경이 있으면 사진 종횡비, 없으면 선택한 `CanvasAspect`(기본 자유=화면 채움)를 따른다.
- **획 단위가 아니라 이벤트 단위로 전송**. `StrokeStart` → `StrokeAppend` × N → `StrokeEnd`. 사용자가 손가락 떼기 전에도 원격에 즉시 보이도록.
- **`seq`는 작성자별 단조 증가**. 전역 순서는 보장하지 않음.
- **사진은 캔버스 상태의 옵션 필드** — 자기가 선택/촬영했든 원격에서 받았든 같은 setter로 진입.

## 2. 상태 머신

```kotlin
// stroke·스티커·텍스트를 시간순 한 스택에 섞는 통합 undo 항목.
sealed interface UndoItem {
    data class StrokeRef(val id: StrokeId) : UndoItem
    data class StickerRef(val id: StickerId) : UndoItem
    data class TextRef(val id: TextId) : UndoItem
}

class CanvasState {
    val strokes: SnapshotStateList<Stroke> = mutableStateListOf()
    val openStrokes: SnapshotStateMap<StrokeId, Stroke> = mutableStateMapOf()
    val stickers: SnapshotStateList<Sticker> = mutableStateListOf()
    val texts: SnapshotStateList<TextElement> = mutableStateListOf()
    private val undoStack: SnapshotStateList<UndoItem> = mutableStateListOf()  // stroke+스티커+텍스트 통합

    var background: BackgroundImage? by mutableStateOf(null)
        private set
    var backgroundColor: Int by mutableStateOf(WHITE)   // 사진 없을 때 바탕색. 캔버스 속성.
        private set
    var aspect: CanvasAspect by mutableStateOf(CanvasAspect.Free)  // 빈 캔버스 비율. 캔버스 속성.
        private set
    var contentRevision: Int by mutableStateOf(0)       // 완료 stroke/배경 변경 시 ++ — 비트맵 캐시 무효화
        private set

    fun apply(event: DrawingEvent) { /* Stroke* / Clear / Undo / *Sticker / *Text dispatch (+완료 stroke 변경 시 bumpRevision) */ }
    fun applySnapshot(strokes, stickers, texts, aspect, backgroundColor) { /* 전체 교체 + bump */ }
    fun reset() { /* 전부 비움(배경·비율 포함) — 타임랩스 재생 rebuild 용 */ }
    fun setBackground(image: BackgroundImage?) { background = image; /* bump */ }
    fun setBackgroundColor(argb: Int) { backgroundColor = argb; /* bump */ }
    fun setCanvasAspect(value: CanvasAspect) { aspect = value }  // 크기 변경으로 캐시 자동 재계산 → bump 불필요
}
```

- `apply`는 **순수에 가깝게**. 로컬 입력과 원격 인바운드 이벤트가 모두 여기로 들어옴 → 모드 분기 없음.
- **스티커**: `PlaceSticker` → 추가 + undoStack push, `TransformSticker`(이동/크기/회전 공용) → 교체(undo 불변), `RemoveSticker` → 제거 + undo 항목 제거.
- **텍스트**: `PlaceText` → 추가 + undoStack push, `RemoveText` → 제거 + undo 항목 제거. 변형 없음(불변·삭제전용).
- 사진·배경색·비율은 `apply` 밖의 별도 setter — 이벤트가 아니라 캔버스 속성이기 때문. `Clear`/`applySnapshot` 도 배경색·비율은 건드리지 않음(`reset` 만 초기화).
- `mutableStateListOf` + `mutableStateMapOf`로 Compose가 변경된 부분만 다시 그리도록.
- **타임랩스**: 모든 변경이 이 이벤트 스트림을 지나므로(`DrawingViewModel.emit`/`applyRemoteEvent` + 배경 setter), 기록기가 `(atMs, op)` 로그만 모았다가 빈 `CanvasState` 에 다시 재생/렌더한다. 상세: [timelapse-plan.md](done-timelapse-plan.md).

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

텍스트(`ToolKind.Text`):
- 빈 곳 탭 → 바텀시트(`TextInputSheet`, IME + 크기 프리셋)에서 입력·확인 시 그 위치에 `PlaceText`.
  입력 종료 후엔 **수정·이동 불가, 삭제만**(탭 선택 후 X 핸들 또는 통합 undo). 색 = 현재 펜 색.

줌(핀치/이동) — **로컬 표시 전용**:
- 뷰포트 = `scale`(1..5) + `offset`(px 이동). 순수 함수 `Viewport.kt`(`screenToContent`/`zoomAround`/`clampOffset`).
- 두 손가락 = 핀치 확대(centroid 고정) + 드래그 이동, 한 손가락 = 그리기. 그리는 중 두 번째 손가락이
  닿으면 진행 stroke 취소(`strokeCancel`: end 후 Undo — 피어·타임랩스 정리) 후 줌/이동으로 전환.
- **좌표 변환**: 포인터 화면좌표를 `screenToContent(scale, offset)` 로 되돌린 뒤 정규화 → 저장 데이터는
  줌과 무관하게 항상 콘텐츠 좌표(0..1). 스포이드/스티커/텍스트 제스처도 동일 변환.
- 동기화/저장/타임랩스/미니뷰엔 미반영(트레이싱 알파와 같은 로컬 뷰 속성).

## 4. Compose 렌더링

레이어 순서 (아래가 먼저, 위가 나중):

아래 0~6·9 는 **뷰포트 변환(`withTransform{ translate(offset); scale(scale) }`) 안**에서 그린다(확대/이동 일괄 적용). 커서/스포이드 십자(7·8)만 변환 밖(화면좌표, 일정 크기).

```
0. backgroundColor          (바탕색 채움. 기본 흰색 — 사진이 그 위에 깔림)
1. background?.bitmap        (사진. ContentScale.Fit으로 캔버스에 맞춤, 트레이싱 표시 알파 적용)
2. state.strokes             (완료된 획 — 1배율=비트맵 캐시, 확대 중=벡터 직접(또렷))
3. state.openStrokes.values  (진행 중 획)
4. state.stickers            (스티커 — StickerRenderer.drawSticker)
4.5 state.texts              (텍스트 — TextRenderer.drawText)
5. 안내선(가이드라인)         (격자/십자선 — 로컬 전용)
6. 스티커/텍스트 선택 핸들     (해당 모드 + 선택됨 — 핸들 크기는 1/scale 로 화면상 일정)
9. 텍스트 배치 마커           (입력 시트 열린 동안 I-beam 캐럿)
────────────── (아래는 변환 밖, 화면좌표) ──────────────
7. brushIndicator            (커서 위치 펜 발자국 — 그리기 모드, 반경 ×scale)
8. 스포이드 조준 십자          (스포이드 모드 + 누르는 중 — drawEyedropperCursor)
```

- **완료된 획 비트맵 캐시**(구현됨): 완료 stroke 를 투명 `ImageBitmap` 에 렌더해두고(`remember(contentRevision, size, density)`, `renderCommittedStrokes`) 매 프레임 `drawImage(cached)` 로 재사용. 색·도구 변경·진행 중 stroke 프레임에 전체 벡터 재그리기를 피함. **확대 중(scale≠1)엔 캐시 대신 벡터를 직접 그려 또렷하게.** 배경은 캐시 안 함(트레이싱 알파 라이브). 진행 중 stroke·스티커·커서는 캐시 위에 라이브.
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
- 캔버스 종횡비를 사진 비율로 변경 (사진 비율이 `CanvasAspect` 선택보다 우선)
- 기존 stroke는 정규화 좌표라 자동 리스케일
- 사진이 없을 때만 `CanvasAspect`(자유/1:1/4:3/3:4/16:9/9:16) 선택이 캔버스 모양을 결정. TopAppBar "비율" 버튼(사진 없을 때만 노출).

## 6. 되돌리기

- **함께 그리기 단일 모드 (현재)**: `Undo`/`Clear`/지우개가 `authorId` 로 거르지 않음 — 함께 모드에선 자기·상대 stroke 모두 되돌리기/삭제 가능(공유 캔버스, collaborative undo). 모임 모드는 자기 캔버스만 영향(미니 뷰는 read-only). (author-locked "따라 그리기" 옵션은 Phase 6 보류.)
- **통합 undo**: undoStack 은 `UndoItem`(StrokeRef/StickerRef) 을 시간순으로 담아 "되돌리기" 버튼이 stroke·스티커 구분 없이 최근 *추가* 동작을 취소. StrokeRef → `Undo`, StickerRef → `RemoveSticker` emit.
- **스티커 변형(이동/크기/회전)은 undo 대상 아님** — 라이브 편집(commit-on-end). 배치/삭제만 undo 스택에 반영. 삭제는 선택 시 X 핸들로도 가능.
- `Undo` 이벤트는 `strokeId` 지정 방식. 시퀀스 기반 "마지막 N개"는 협업에서 의미 흔들리므로 피함.
- 다시 실행(redo)은 미지원 — **보류 결정**. 로컬은 별도 스택으로 쉬우나 협업(공유 undo·지우개 공유·Clear/동기화·와이어) 의미 합의가 필요. 상세: [drawing-ideas.md](drawing-ideas.md) 보류 섹션.
- 사진 자체는 되돌리기 대상 아님 — 별도 "사진 제거" 액션.

## 7. 캔버스 크기/DPI

- 좌표는 정규화 (위 참고).
- `strokeWidthDp`는 dp 단위로 송신. 수신 측에서 자기 `density`로 px 환산.
- 색은 ARGB Int로 그대로 전송 (32-bit 고정 폭).
- **캔버스 비율(`CanvasAspect`)·배경색(`backgroundColor`)**: 캔버스 속성. 비율은 사진 없을 때 모양을 결정
  (자유=화면 채움), 배경색은 사진 아래 바탕. 둘 다 `Frame.CanvasAspectFrame`/`Frame.BackgroundColorFrame`
  로 세 모드 동기화(함께=공유 캔버스, 모임/교실=발신자 미니뷰), 스냅샷에도 `CanvasSnapshot.aspect`/
  `backgroundColor` 로 포함. 정규화 좌표라 비율을 바꾸면 기존 요소가 새 모양에 맞춰 리플로우.
- **줌(뷰포트)**: `scale`/`offset` 은 로컬 화면 표시 전용 — 정규화 데이터·동기화·저장엔 영향 없음(§3 줌 참고).

## 8. PNG 내보내기 (구현 완료 — `works/PngComposer`)

```kotlin
fun exportPng(state: CanvasState, density: Float): Bitmap {
    val (w, h) = state.background?.let { it.widthPx to it.heightPx }
                 ?: sizeForAspect(state.aspect)                     // 자유=1080² / 비율 프리셋은 그에 맞춘 치수
    val bitmap = createBitmap(w, h)
    val canvas = Canvas(bitmap)
    // 1. 배경: 사진이 있고 합치기 모드면 사진, 아니면 backgroundColor(기본 흰색)로 채움
    state.background?.bitmap?.let { canvas.drawImage(it, ...) } ?: canvas.drawColor(state.backgroundColor)
    // 2. 완료된 stroke들을 같은 drawStroke() 로직으로 합성
    state.strokes.forEach { drawStroke(it, ..., canvas) }
    // 3. 스티커 → 4. 텍스트를 stroke 위에 합성 (같은 drawSticker()/drawText() 로직)
    state.stickers.forEach { drawSticker(it, ..., canvas) }
    state.texts.forEach { drawText(it, ..., canvas) }
    return bitmap
}
```

- 렌더링은 화면용과 동일한 `StrokeRenderer.drawStroke` + `StickerRenderer.drawSticker` + `TextRenderer.drawText` 재사용 (`CanvasDrawScope` + `ImageBitmap`). 완료된 stroke + 스티커 + 텍스트를 합성 (진행 중/커서/선택 핸들 제외).
- **저장 치수는 `CanvasAspect` 반영**(사진 없을 때): 자유=1080×1080, 비율 프리셋은 긴 변 1080 기준. **줌은 무관**(항상 전체 해상도).
- 앱 내부 저장: `WorkStore.save` → `filesDir/works/<id>.png` + `.meta`. "최근 작업" 모달에 노출, 최대 100개.
- 외부 갤러리: `WorkStore.exportToGallery` → `MediaStore.Images` (`Pictures/DrawingTogether`). `IS_PENDING` 패턴 + `MediaScannerConnection.scanFile` 로 갤러리 즉시 반영. 미리보기 화면의 "저장"/"공유" 액션에서 호출.
- 진행 중 stroke는 export에 포함 안 됨 (사용자가 export 누르는 시점엔 들고 있던 손가락 떼는 것이 자연).
