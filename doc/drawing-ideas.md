# 그리기 모드 강화 아이디어

> 그리기 경험을 풍부하게 만들기 위한 기능 후보 목록. 사용자 제안 + 구현 관점 평가.
> 상태: 아래 "완료된 항목" 16건 완료(P1·P2·P3 전부 + 텍스트 넣기 + 캔버스 비율·세밀붓·줌). 나머지는 백로그(P4).

## 평가 기준 (이 앱 특유)

- **동기화**: 모임/함께 모드에서 모든 그리기 요소는 `DrawingEvent` → `CanvasState.apply()`
  경로로 동기화돼야 함. 새 요소가 stroke 틀에 맞으면 거의 공짜, 아니면 와이어(`Frame`)
  까지 새로 만들어야 함.
- **렌더 공유**: `ui/canvas/StrokeRenderer.kt#drawStroke` 가 화면 + PNG 합성 공유. 여기에
  그리면 "보이는 것 = 저장되는 것" + 멀티 동기화가 자동.
- **정규화 좌표**: 모든 점은 0..1. 렌더 시 `canvasSize` 곱함.

## 완료된 항목

구현 세부는 코드와 [roadmap.md](roadmap.md) Phase 5.5 참고. 여기선 요약만.

1. **안내선(가이드라인)** — 중앙 십자선 + 격자(6×6 / 18×18 택1). 로컬 전용(동기화·저장
   미포함). `DrawingViewModel.guideCross/guideGrid`, `DrawingCanvas.drawGuides`, 도구바 "보조" 드롭다운(안내선 섹션).
2. **에어브러시(분사)** — `BrushType.Airbrush`. 분사점을 stroke 에 저장하지 않고 렌더 시
   결정론 seed(`stroke.id` 해시)로 생성 → 매 프레임·양 단말 동일. `StrokeRenderer.drawAirbrush`.
3. **번짐(수채/스머지)** — `BrushType.Blur`. native `Paint` + `BlurMaskFilter` via
   `drawIntoCanvas { nativeCanvas.drawPath }`. PNG 합성에서도 동작. `StrokeRenderer.drawBlurred`.
4. **스티커** — 자체 벡터 12종(하트/별/스마일/꽃/구름/해/달/무지개/물방울/번개/보석/반짝이)
   배치·이동·크기·회전·삭제 + stroke/스티커 통합 undo(`UndoItem`). 메타(`key`+변환)만 보관·전송,
   렌더 시 벡터로 치환. 변형은 commit-on-end. 상세: [sticker-plan.md](sticker-plan.md).
5. **브러시 변형 4종(네온/점선/무지개/붓펜)** — `BrushType` 4개 + `StrokeRenderer` 분기.
   네온=발광 후광+밝은 코어, 점선=`dashPathEffect`, 무지개=누적 길이→hue 회전(결정론 phase),
   붓펜=점 간 거리(속도)→굵기 변조. 점 좌표에서 유도 → 와이어 변경 없음, 화면·PNG·멀티 자동 공유.
6. **손떨림 보정(스트로크 안정화)** — 입력 점에 지수이동평균(EMA) 적용. 끔/약/강 3단
   (`Smoothing`, alpha 1.0/0.5/0.28). `DrawingCanvas` 입력 단계에서 보정 → 보정된 점만
   저장·전송하므로 동기화·저장·undo 자동. 도구바 굵기 줄에 "보정" 토글 칩. 로컬 설정.
7. **스포이드(색 추출)** — `ToolKind.Eyedropper` + 색 팔레트의 스포이드 버튼. 누른 채
   드래그하면 조준 십자가 따라오고 떼는 순간 색을 집음(손가락 가림 보정). `PngComposer` 로
   사진+stroke+스티커 합성 비트맵을 만들어 픽셀을 읽음("보이는 색=집히는 색").
   `CanvasColorSampler.sampleColor`(alpha 불투명 강제). 집으면 `selectColor` 로 펜 복귀. 로컬 설정.
8. **최근 색 / 색 팔레트 저장** — 색을 쓸 때마다 `UserPaletteRepo.addRecent`(최신 앞, 중복 제거,
   최대 8개, prefs 영속). 색 팔레트 줄에 프리셋과 구분선으로 나눠 표시(프리셋에 이미 있는 색은
   제외). 프리셋/커스텀/스포이드 모든 선택 경로가 기록됨. 스포이드와 짝. 로컬 설정.
9. **트레이싱 보조 — 반투명 + 외곽선(엣지 검출)** — 사진 배경 표시 알파 순환(원본→연하게
   →아주 연하게→외곽선, `TraceOpacity`). TopAppBar 트레이싱 버튼(사진 있을 때만). `DrawingCanvas` 가
   `drawImage(alpha=)` 로 표시만 적용 — **저장(`mergeBackgroundOnSave`)·동기화엔 미반영(직교)**.
   외곽선 모드는 `EdgeDetector`(Sobel)로 사진에서 라인만 추출한 투명 오버레이를 사진 대신 표시 —
   또렷한 라인아트 가이드. 계산은 `DrawingViewModel.edgeOverlay`(viewModelScope + Dispatchers.Default,
   사진당 1회 캐시·사진 변경 시 무효화). 로컬 표시 전용.
10. **도형 채우기 토글** — `ToolSettings.fill`. `StrokeRenderer.drawShapeForm` 이 fill 이면 `Fill`,
    아니면 외곽선 `Stroke`. 화면·PNG·동기화 자동 공유. 도형 드롭다운 하단에 "채우기" 토글.
11. **배경색 선택** — `CanvasState.backgroundColor`(사진 배경처럼 캔버스 속성, 기본 흰색).
    `DrawingCanvas` 가 맨 아래 바탕으로 칠하고, `PngComposer` 가 사진 없을 때 흰색 대신 이 색으로 저장.
    TopAppBar "배경색" 버튼 → `ColorPickerSheet`. **현재 로컬 전용**(멀티 동기화 미포함 — 와이어 추가는 후속).
12. **대칭(미러) 그리기** — `SymmetryMode`(끔/좌우/상하/4분할). `DrawingViewModel` 이 입력 stroke 의
    미러 좌표 stroke 를 독립 `StrokeId` 로 함께 emit(정규화 좌표 반사) → 동기화·저장 자동. 도형·브러시도
    그대로 미러. "보조" 드롭다운(구 안내선)에 대칭 섹션. 한 제스처가 N개 stroke 라 undo 는 미러별 1회씩(향후 묶기 가능).
13. **텍스트 넣기(불변·삭제전용)** — 스티커처럼 배치형 요소(`TextElement`: 정규화 cx/cy + sizeFrac + 색).
    `ToolKind.Text` 모드 → 빈 곳 탭 → 바텀시트(`TextInputSheet`, IME + 크기 프리셋)에서 입력·확인하면
    그 위치에 굳음. **입력 종료 = 수정·이동 불가, 삭제만**(탭해 선택 후 X 핸들, 또는 통합 undo). 이벤트는
    `PlaceText`/`RemoveText` 2종뿐 → 멀티 동기화 자동. 렌더는 `TextRenderer`(네이티브 Paint, 줄바꿈·중앙정렬)
    공유 → 화면·PNG·미니뷰·타임랩스 동일. 색은 현재 펜 색. 폰트는 기기 기본(벡터 아님 — 기기 간 동일 보장은 약함).
14. **캔버스 비율(가로세로) 선택 + 동기화** — `CanvasAspect`(자유/1:1/4:3/3:4/16:9/9:16). 사진 없을 때만
    적용(사진 비율 우선). `CanvasState.aspect`(배경색과 동급 캔버스 속성 — `Clear` 로 안 지워짐, `reset` 만 초기화),
    TopAppBar "비율" 버튼(`AspectRatioSheet`). `PngComposer` 저장 치수도 반영(자유=1080²). **함께·모임·교실
    3모드 동기화** — `Frame.CanvasAspectFrame` + `CanvasSnapshot.aspect`. `MiniCanvas` 도 그 peer 비율 반영.
15. **세밀붓(Fine)** — 굵기 고정된 아주 가는 붓. `BrushType.fixedWidthDp`(non-null 이면 슬라이더 무시, 세밀붓=0.8dp).
    선택 시 도구바 굵기 슬라이더를 숨기고 "굵기 고정" 안내. `PenIllustration`/`BrushPreview` 추가. 세밀 묘사용.
16. **줌인/줌아웃(로컬 전용)** — 두 손가락 핀치 확대(centroid 고정)+드래그 이동, 한 손가락 그리기.
    뷰포트 `scale`(1..5)+`offset`, 순수함수 `Viewport.kt`. 그리는 중 두 번째 손가락 닿으면 진행 stroke
    취소(`strokeCancel`) 후 줌 전환. 확대 중엔 벡터 직접 렌더(또렷). 포인터를 콘텐츠 좌표로 변환해 정규화 →
    **저장 데이터·동기화·저장·타임랩스·미니뷰 무관**(로컬 뷰 속성). 리셋 칩(1:1) + 사진/비율 변경 시 자동 리셋.

> **선 곡선 평활화**(렌더): 자유 곡선은 직선 폴리라인이 아니라 인접 점 중간점-2차 베지어로
> 그려 꺾임을 둥글게(`buildFreehandPath`, 무지개·붓펜은 `forEachSmoothPiece`). 손떨림 보정(입력)과 별개.

> **성능 메모**: 에어브러시·번짐은 stroke 가 많이 쌓이면 매 프레임 재계산/blur 비용이 큼 →
> "완료 stroke 비트맵 캐시" 도입 완료(아래 P3). recompose 마다 전체 재그리던 멈칫 해소.

## 백로그 (우선순위순)

> **정렬 기준**: 우선순위 = (가치 × 구조 적합성) ÷ 노력.
> - **가치**: 어린이/일반 사용자 재미·실용. **구조 적합성**: stroke·렌더·오버레이 등 기존
>   틀에 얹히면 ↑, 와이어(`Frame`)·구조 변경/정책 위험이면 ↓. **노력**: 난이도.
> - 그래서 "기존 brush/오버레이 재사용 + 로컬 전용"이 위로, "구조 변경·정책 위험"이 아래로.
>
> | 항목 | 가치 | 구조 적합성 | 노력 | 티어 |
> |---|---|---|---|---|
> | ~~타임랩스 재생·내보내기~~ ✅ | 상(공유성) | 중 | 중 | 완료 |
> | ~~트레이싱(엣지검출)~~ ✅ | 중 | 무관(로컬) | 중 | 완료 |
> | ~~비트맵 캐시~~ ✅ | 성능 | — | 중 | 완료 |
> | ~~텍스트 넣기(불변·삭제전용)~~ ✅ | 상 | 중(스티커형) | 중 | 완료 |
> | 레이어 | 상 | 낮(큰 구조) | 상 | P4 |
> | 음향 드로잉 | 불확실 | 중 | 중상+정책 | P4 |
> | redo | 중 | 협업 위험 | 중 | **보류** |

> **P1·P2·P3 는 전부 완료** — 위 [완료된 항목](#완료된-항목) 참고. 남은 백로그는 P4.

## P3 — 큰/조건부 (구조 변경·성능) — 전부 완료

> **타임랩스 재생·내보내기 = 완료** — 기록→재생·갤러리→MP4 내보내기 + 다듬기까지. 상세: [timelapse-plan.md](timelapse-plan.md).
> **비트맵 캐시 = 완료**(아래). **트레이싱 엣지 검출 = 완료**(아래).

### 트레이싱 보조 — 엣지 검출 ✅ 완료
- 반투명 표시(9번)에 이어, 사진의 **외곽선만 추출**해 사진 대신 오버레이 → 또렷한 라인아트 가이드.
- **구현**: `photo/EdgeDetector.kt` Sobel(|gx|+|gy|, THRESHOLD=48) → 검은 라인·투명 배경 `ImageBitmap`.
  `TraceOpacity.Edge` 가 순환 4번째 상태. `DrawingViewModel.edgeOverlay` 가 viewModelScope +
  Dispatchers.Default 로 사진당 1회 계산·캐시(사진 변경 시 무효화). `DrawingCanvas` 가 edge 모드면
  사진 대신 오버레이를 그림. 로컬 전용·동기화/저장 무관.

### 완료 stroke 비트맵 캐시 ✅ 완료
- **계기**: 색 선택 등 recompose 마다 완료 stroke 전체를 벡터 재그리기 → 누적 시 멈칫(실측 확인).
- **구현**: `CanvasState.contentRevision`(완료 stroke 추가/제거/Clear/snapshot/배경 변경 시 증가).
  `DrawingCanvas` 가 `remember(contentRevision, canvasSize, density)` 로 완료 stroke 를 투명 비트맵에
  렌더(`renderCommittedStrokes`)해 캐시 → 색·도구 변경·진행 중 stroke 프레임엔 `drawImage(cached)` 재사용.
  배경(사진/색)은 캐시 안 함(트레이싱 알파 라이브). 진행 중 stroke·스티커·커서는 캐시 위에 라이브.

## P4 — 보류 / 실험 (규모 큼·불확실)

### 레이어
- 큰 작업, 보류급. 레이어는 렌더·합성·undo·동기화 전반에 영향.

> **텍스트 넣기 = 완료**(불변·삭제전용). 아래 [완료된 항목](#완료된-항목) 13번 참고.

### 음향 드로잉 / 비주얼라이저 — 실험 후보 (불확실)
- **개념**: 마이크/음악의 진폭·주파수(FFT)·박자를 받아 stroke 를 자동 생성. 음에 맞춰
  "자연스럽게" 그려지는 재미 요소. 함께 모드면 생성 stroke 가 `DrawingEvent` 로 양쪽 동기화.
- **결과물 성격**: 본질적으로 **추상 비주얼라이저/패턴**. 날것 매핑이면 파형 선 하나,
  매핑을 설계하면(음표→점/별, 음높이→위치·색, 박자→간격) 점묘화처럼 풍부해짐. 단
  "곡의 *의미*(가사/제목)를 형상화" 하는 건 불가 — 그건 AI 이미지 생성 영역.
- **구현**: `AudioRecord`/`Visualizer` 로 실시간 진폭·FFT → stroke 매핑. 매핑 디자인이
  핵심이자 불확실성(여러 번 튜닝 필요). 스티커 기능과 연계하면 음표→별 점묘 가능.
- **난이도**: 중상. + **출시 정책 주의**:
  - `RECORD_AUDIO` 권한 — 현재 앱의 "무권한" 정책이 깨짐(거부감).
  - **음향을 저장·전송하지 않고 실시간 시각화만** 하면 데이터 수집 최소 → 심사 단순.
    생성된 stroke 만 동기화, 오디오 자체는 버림.
  - Play Store: 마이크 권한 자체는 리젝 사유 아님. 단 **개인정보처리방침 + Data safety
    폼 + 권한–기능 일치 + 포그라운드 사용** 필수. 어린이 타겟(Families)이면 마이크 추가
    제약. 가장 흔한 함정은 기술이 아니라 정책 문서 누락.
- **권장**: "마이크 진폭 → 파형 선 하나" 작은 프로토타입으로 느낌부터 확인 후 판단.

## 보류 (결정상 미진행)

### redo (다시 실행)
- **결정**: 보류. undo 로 취소한 동작을 되살리는 기능.
- **로컬(싱글)은 저위험** — `UndoItem` 위에 redo 스택을 얹고, 취소된 stroke/스티커 *원본*을 보관,
  새 입력 시 redo 스택 클리어면 충분.
- **협업(함께/모임)이 위험** — 보류 사유:
  - undo 이벤트를 **지우개가 공유**(`DrawingEvent.Undo(strokeId)`) → redo 대상 구분 필요.
  - `Clear`/`applySnapshot`(동기화)이 undo 스택을 통째로 비움 → redo 스택 무효화·스냅샷 보관 필요.
  - undo 가 strokeId 기반으로 양 단말 동기화되는 공유 캔버스라, 재삽입 z-order·상대 새 입력과의
    충돌·새 `Frame` 와이어 추가(구버전 호환) 등 **의미 정의부터 합의 필요**.
- **재개 시 방향**: 싱글 한정 redo 먼저(멀티에선 버튼 숨김/비활성), 협업 redo 는 별도 과제.

## 권장 진행 순서

완료(P1·P2 전부): 안내선 → 에어브러시 → 번짐 → 스티커 → 브러시 변형 4종 → 손떨림 보정 → 스포이드 → 최근 색 → 트레이싱 반투명 → 도형 채우기 → 배경색 → 대칭.

남은 순서 (P4):
- **P3 전부 완료**: 트레이싱(엣지 검출)·타임랩스·비트맵 캐시
- **P4**: 레이어/텍스트 → 음향 드로잉 (규모 큼·정책 불확실)
- **보류**: redo (협업 의미 합의 필요 — 위 보류 섹션 참고)

원칙: 기존 brush/오버레이 재사용 + 로컬 전용은 위로, 와이어·구조 변경·정책 위험은 아래로.
