# 스티커 기능 계획 — 자체 벡터 세트 + 이동/크기/회전/삭제 + 통합 undo

> 상태: **계획만 수립, 미구현.** 그리기 강화 백로그([drawing-ideas.md](drawing-ideas.md))의
> 마지막·최대 항목. 진행 시 이 문서를 단계 가이드로 사용.

## 배경 / 설계 원칙

스티커는 stroke 가 아닌 **새 요소 타입**이라 모델·이벤트·렌더·편집 UX·동기화·undo
전반에 신규 경로가 필요하다.

핵심 ("기호로 관리 + 렌더 시 치환"):
- 스티커는 픽셀이 아니라 **`{key + 변환}` 메타**로만 보관. 렌더(화면·PNG·미니뷰)가
  `key` → 자체 벡터로 치환. 동기화는 메타만(가벼움, 기존 `DrawingEvent` 경로).
- 좌표는 stroke 와 동일하게 **정규화(cx,cy ∈ 0..1)** + scale(캔버스 짧은변 대비 비율)
  + rotationDeg.
- **undo 통합**: 되돌리기 버튼이 stroke·스티커 구분 없이 최근 *추가* 동작 취소.
  (스티커 이동/크기/회전 변형은 undo 대상 아님 — 라이브 편집. 삭제는 X 핸들로.)
- 에셋: **자체 벡터 세트 10~20종** (앱 번들, Candy Pop 테마 일관, 의존성 없음).
  양 단말에 번들돼 있어 `key` 만 전송 → 각자 렌더.

## 단계별 구현

### A. 모델 (`drawing/model/`)
- `Identifiers.kt`: `StickerId` value class (StrokeId 패턴) 추가.
- `Sticker.kt` (신규): `Sticker(id, authorId, key: StickerKey, cx, cy, scale, rotationDeg)`.
- `StickerKey.kt` (신규): enum 10~20종 (Heart, Star, Smile, Flower, ...). 벡터 그리기는
  렌더러에서(C). `displayName`.
- `DrawingEvent.kt`: 3종 추가 —
  - `PlaceSticker(seq, authorId, stickerId, key, cx, cy, scale, rotationDeg)`
  - `TransformSticker(seq, authorId, stickerId, cx, cy, scale, rotationDeg)` (이동/크기/회전 공용)
  - `RemoveSticker(seq, authorId, stickerId)`

### B. CanvasState (`drawing/engine/CanvasState.kt`)
- `_stickers = mutableStateListOf<Sticker>()` + `val stickers`.
- **통합 undo**: 현재 `_undoStack: List<StrokeId>` → `sealed UndoItem { StrokeRef(StrokeId);
  StickerRef(StickerId) }` 의 리스트로 교체. `lastFinishedStrokeId()` → `lastUndoable(): UndoItem?`.
- `apply()` 분기 추가:
  - PlaceSticker → `_stickers.add` + `undoStack.add(StickerRef)`
  - TransformSticker → 해당 sticker `copy(cx,cy,scale,rotationDeg)` 로 교체 (undo 스택 불변)
  - RemoveSticker → `_stickers` 제거 + undoStack 에서 StickerRef 제거
  - 기존 StrokeEnd → `undoStack.add(StrokeRef)`, Undo/Clear 도 UndoItem 기준으로
- `applySnapshot(strokes)` → `applySnapshot(strokes, stickers)` 로 확장.

### C. 렌더 (`ui/canvas/`)
- `StickerRenderer.kt` (신규): `DrawScope.drawSticker(sticker, canvasSize, density)` —
  `withTransform { translate(cx*w, cy*h); rotate(deg); scale(s) }` 안에서 `key` 별 벡터
  path 그림. 도형 path 빌더(`ShapeDropdownButton.kt#buildStarPath/buildHeartPath/
  buildRegularPolygonPath`) 재사용 + 추가 일러스트.
- `DrawingCanvas.kt`: stroke 렌더 다음에 `state.stickers.forEach { drawSticker(...) }`.
  안내선/커서보다 아래.
- `PngComposer.kt`: `state.stickers.forEach { drawSticker(...) }` 추가 (화면=저장).
- `MiniCanvas.kt`: stickers 도 렌더 (모임 미니뷰 일관).

### D. 도구 선택 (`drawing/model/ToolSettings.kt` + `ui/canvas/`)
- `ToolKind` 에 `Sticker` 추가 + `ToolSettings.stickerKey: StickerKey?`.
- `StickerPickerSheet.kt` (신규): `BrushSelectorSheet` 패턴. 자체 벡터 미리보기 그리드에서
  키 선택 → `ToolKind.Sticker` + stickerKey 설정.
- `Toolbar.kt`: 도구 행에 스티커 트리거 버튼(브러시/도형/지우개/안내선 옆).

### E. 입력 / 편집 제스처 (`ui/canvas/DrawingCanvas.kt`)
- `ToolKind.Sticker` 모드 분기 (기존 stroke `awaitEachGesture` 와 별도):
  - 빈 곳 탭 → 그 위치에 `onPlaceSticker(key, cx, cy)` (기본 scale/rotation 0).
  - 배치된 스티커 탭(hit-test) → 선택 상태(`selectedStickerId`, 로컬 state).
  - 선택된 스티커 본체 드래그 → 이동: 드래그 중 `onTransformStickerLocal`(로컬만),
    `onDragEnd` 에 `onCommitStickerTransform`(전송).
  - 선택 시 **핸들 오버레이**: 바운딩 박스 + 우하단 핸들(드래그 = 중심 기준 거리→scale,
    각도→rotation 동시; 동일하게 중엔 local, end 에 commit) + 우상단 X(삭제 `onRemoveSticker`).
- hit-test: 스티커 바운딩(중심±scale 반경) 역회전 좌표로 판정.

### F. ViewModel (`ui/canvas/DrawingViewModel.kt`)
- `placeSticker(key, cx, cy)` / `removeSticker(id)` — `emit(DrawingEvent...)` (canvas.apply + outbound).
- **변형은 commit-on-end**: 드래그/핀치 *중* 에는 로컬 캔버스만 갱신(자기 화면 실시간),
  전송은 **제스처 종료 시 최종 상태 1회**.
  - `transformStickerLocal(id, cx, cy, scale, rot)`: `canvas.apply(TransformSticker)` 만 — outbound 없음.
  - `commitStickerTransform(id, cx, cy, scale, rot)`: `emit(TransformSticker)` — outbound 포함, 제스처 종료(`onDragEnd`)에서 1회.
  - 이유: 스티커 변형은 *결과*만 보면 충분(stroke 그리기와 다름). 실시간 스트리밍 대비
    트래픽 대폭 절감(모임 모드 호스트 relay 면 N배). `OutboundCoalescer` 불필요.
  - 트레이드오프: 상대는 변형 *과정* 은 못 보고 최종 상태만(수용 가능).
- `undoLastLocal()`: `canvas.lastUndoable()` 분기 — StrokeRef→`Undo`, StickerRef→`RemoveSticker` emit.

### G. 동기화 — Snapshot 확장 (`transport/codec/FrameCodec.kt` + `session/`)
- DrawingEvent 3종은 CBOR 자동 직렬화 → 실시간 배치/변형/삭제는 자동 전파.
- "동기화"(Snapshot) 는 현재 strokes 만 FILE 로 보냄 → **strokes + stickers 묶음**으로 확장:
  - `FrameCodec.encodeStrokes/decodeStrokes` → `encodeCanvas/decodeCanvas`(`CanvasSnapshot(strokes, stickers)` data class).
  - `Frame.Snapshot` 은 payloadId 그대로(메타 불변). `handleSnapshotFile` 이 둘 다 디코드.
  - `respondToSnapshotRequest` / `applyRemoteSnapshot` / `broadcastMyCanvasAsPeer` 에 stickers 동반.
  - 모임 미니뷰 cascade(`peerCanvases.applySnapshot`)도 stickers 포함.

### H. 빌드/검증
- `./gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest assembleDebug` → `installDebug`.

## 재사용 자산
- `ShapeDropdownButton.kt` 의 `buildStarPath/buildHeartPath/buildRegularPolygonPath` — 벡터 일부 재사용.
- `BrushSelectorSheet.kt` — 스티커 피커 시트 패턴.
- `StrokeRenderer` 의 `drawStroke` 공유 패턴 — 화면/PNG/미니뷰 동일 렌더.
- 정규화 좌표(0..1) — 기존 stroke 와 동일 좌표계.
  (변형은 commit-on-end 라 `OutboundCoalescer` 불필요.)

## 위험 / 주의
- **undo 스택 타입 교체**(StrokeId → UndoItem): `CanvasState`/`DrawingViewModel`/테스트
  (`CanvasStateTest`)에 파급. 가장 침습적. 먼저 A·B 끝내고 단위 테스트로 고정.
- **Snapshot 와이어 확장**: 구버전과 호환 안 됨(같은 빌드끼리만). CanvasSnapshot 으로 캡슐화.
- **편집 제스처 복잡도**(E): 핸들 hit-test + 회전 좌표 변환이 손이 많이 감. 단계 E를
  "배치+선택+이동" → "크기/회전 핸들" → "삭제" 로 잘게 진행.
- 성능: 스티커는 stroke 보다 가벼움(개수 적음). blur/에어브러시만큼 부담 없음.

## 검증 시나리오
1. 스티커 배치 → 이동/핀치 크기·회전/삭제, 되돌리기로 최근 stroke·스티커 통합 취소.
2. 저장 PNG 에 스티커 합성(변환 반영).
3. 함께/모임 2대: 배치·변형·삭제 실시간 전파, 미니뷰 표시.
4. "동기화" 로 상대 캔버스의 stroke+스티커 통째 가져오기.
5. 단위 테스트: CanvasState apply(PlaceSticker/Transform/Remove) + 통합 undo.
