# 그리기 모드 강화 아이디어

> 그리기 경험을 풍부하게 만들기 위한 기능 후보 목록. 사용자 제안 + 구현 관점 평가.
> 상태: 안내선부터 착수 예정, 나머지는 백로그.

## 평가 기준 (이 앱 특유)

- **동기화**: 모임/함께 모드에서 모든 그리기 요소는 `DrawingEvent` → `CanvasState.apply()`
  경로로 동기화돼야 함. 새 요소가 stroke 틀에 맞으면 거의 공짜, 아니면 와이어(`Frame`)
  까지 새로 만들어야 함.
- **렌더 공유**: `ui/canvas/StrokeRenderer.kt#drawStroke` 가 화면 + PNG 합성 공유. 여기에
  그리면 "보이는 것 = 저장되는 것" + 멀티 동기화가 자동.
- **정규화 좌표**: 모든 점은 0..1. 렌더 시 `canvasSize` 곱함.

## 후보

### 1. 상하 안내선 (가이드라인) — ✅ 구현 완료
- **개념**: 캔버스 위 보조선. 중앙 십자선 + 격자(6×6 / 18×18 택1).
- **동기화**: 없음 — 로컬 `mutableStateOf`. PNG 저장에도 미포함.
- **구현**: `DrawingViewModel.guideCross/guideGrid(GuideGrid)`, `DrawingCanvas.drawGuides`,
  `GuideDropdownButton`(도구바 2번째 행, 가로 스크롤+fade), 미니 뷰엔 미표시.

### 2. 에어브러시 (분사) — ✅ 구현 완료
- **개념**: 점을 흩뿌리는 스프레이 브러시. `BrushType.Airbrush`.
- **동기화 해법**: 분사점을 stroke 에 저장하지 않고, 렌더 시 `Random(stroke.id 해시)`
  로 결정론 생성 → 매 프레임·양 단말 동일. StrokeId 가 이미 와이어 전송돼 멀티에서도
  일치.
- **구현**: `StrokeRenderer.drawAirbrush`(경로 보간 + 극좌표 분사, sqrt 균일 분포),
  `BrushPreview`/`PenIllustration` 분사·스프레이캔 분기. PNG·동기화 자동.
- **남은 과제**: 분사 stroke 多 누적 시 매 프레임 재계산 비용 → 비트맵 캐시 검토.

### 3. 번지는 효과 (수채/스머지) — ✅ 구현 완료
- **개념**: 가장자리가 번지는 붓. `BrushType.Blur`.
- **구현**: `StrokeRenderer.drawBlurred` — `buildFreehandPath().asAndroidPath()` +
  native `Paint` 에 `BlurMaskFilter(NORMAL)`, `drawIntoCanvas { nativeCanvas.drawPath }`.
  `PngComposer` 의 ImageBitmap canvas 에서도 동작 → 화면=저장. 동기화 자동.
  `BrushPreview`/`PenIllustration`(물방울) 분기. 실기기에서 blur 정상 표시 확인됨.
- **남은 과제**: blur 렌더 비용 큼 → stroke 多 누적 시 비트맵 캐시 검토.

### 4. 스티커 — 📋 계획 수립 (미구현)
- **개념**: 자체 벡터 세트 스티커를 캔버스에 배치, 이동·크기·회전·삭제. undo 통합.
- **동기화**: stroke 아닌 **새 요소 타입** → `DrawingEvent`·`Frame`·`CanvasState`·
  렌더러·PNG 합성·undo 새 경로 전부 필요. "기호(key)로 관리 + 렌더 시 치환".
- **난이도**: 높음. 8단계(A~H) 분할.
- **상세 계획**: [sticker-plan.md](sticker-plan.md).

## 권장 진행 순서

안내선(빠른 win) → 에어브러시 → 번짐 → 스티커.
앞 셋은 기존 brush/오버레이 틀 재사용으로 가볍고, 스티커는 규모가 커서 마지막.
