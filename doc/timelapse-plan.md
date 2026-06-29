# 타임랩스 재생·공유 계획 — 이벤트 로그 기록 → 재생 → 영상 내보내기

> 상태: **계획(미착수)**. P3 백로그. 상세 아이디어·우선순위는 [drawing-ideas.md](drawing-ideas.md),
> 단계 표기는 [roadmap.md](roadmap.md).
> 핵심 결정 사항(§6)은 착수 전 합의 권장.

## 배경 / 설계 원칙

타임랩스는 "프레임을 캡처하는 영상"이 아니라, **이미 한 곳을 지나는 `DrawingEvent` 스트림을
기록했다가 다시 재생**하는 기능이다. 이 앱의 이벤트 소싱 구조와 시너지가 가장 큰 후보.

핵심 원칙:
- **1차 산출물은 영상이 아니라 이벤트 로그.** 빈 `CanvasState` 에 로그를 시간순으로 다시
  `apply()` 하면 화면용과 같은 렌더러로 과정이 그대로 재현된다(보이는 것 = 저장되는 것 = 재생).
- **단일 캡처 지점 재사용.** 모든 캔버스 변경은 로컬 `DrawingViewModel.emit()`(= `canvas.apply`
  + outbound), 원격 `applyRemoteEvent()` 를 지난다. 여기에 로그 append 만 추가 — 입력 경로 신설 없음.
- **렌더 재사용.** 재생은 `DrawingCanvas` / `StrokeRenderer` / `StickerRenderer` 를 read-only 로
  재사용. 새 그리기 코드 없음.
- **저장은 가벼운 로그 먼저, 영상은 나중.** 타임랩스는 **작품(PNG)과 독립된 자체 엔티티**로
  보관(인앱 재생), MP4/GIF 내보내기는 별도 단계.

## 기능 정의 (확정)

사용자 확정 사항. 아래 단계 계획은 이 정의를 따른다.

1. **명시적 기록/종료.** 그리기 화면에 **기록 시작 / 종료** 버튼. 종료를 누르면 타임랩스 저장.
   **뒤로가기로 화면을 떠날 때도(기록 중이면) 저장**한다(자동 폐기 아님). → 녹화는 자동 시작이
   아니라 사용자가 켜는 명시적 동작.
2. **보기는 홈에서 별도 진입.** 홈 화면에서 **타임랩스 갤러리**로 들어가 목록에서 선택해 재생.
   그 갤러리(또는 재생 화면)에서 **삭제**도 가능. (작품 PNG 갤러리와 별개 목록.)
3. **사진 배경 처리.** 사진 위에 그린 경우 재생에도 그 사진이 깔려야 의미가 있음 → 배경 사진을
   타임랩스에 함께 보관하고 재생 시 적용. 상세 방안은 §"사진 배경 처리".

## 1. 데이터 모델 (Phase 1 에서 확정)

```kotlin
// 한 항목 = 경과 시간(ms) + 그 시점의 이벤트. 좌표 등은 DrawingEvent 가 이미 정규화 보유.
@Serializable
data class TimelapseEntry(
    val atMs: Long,            // 녹화 시작 기준 경과 ms
    val event: TimelapseOp,    // DrawingEvent + 배경 변경 마커를 함께 담는 sealed
)

// DrawingEvent 는 stroke/스티커만 다룸 → 배경(사진·배경색)은 이벤트가 아니므로 마커로 보강.
@Serializable
sealed interface TimelapseOp {
    data class Draw(val event: DrawingEvent) : TimelapseOp          // 기존 이벤트 그대로
    data class BackgroundColor(val argb: Int) : TimelapseOp         // setBackgroundColor
    data class BackgroundPhoto(val ref: String?) : TimelapseOp      // 사진 설정/제거(참조, §6 참고)
}

@Serializable
data class Timelapse(
    val version: Int = 1,
    val id: String,            // 타임랩스 엔티티 id (작품 PNG 와 독립)
    val createdAt: Long,       // 저장 시각 (목록 정렬)
    val durationMs: Long,
    val entries: List<TimelapseEntry>,
)
```

- `DrawingEvent` 는 이미 `@Serializable` (CBOR codec 존재) → 로그 직렬화는 거의 공짜.
- 배경 사진 비트맵은 로그(CBOR)에 직접 넣지 않고 **별도 파일로 보관**하고 `ref` 로 참조(§"사진 배경 처리").

### 저장 구조 — `TimelapseStore` (작품 PNG 와 독립)

`WorkStore`(`filesDir/works/`) 와 **별개**인 `filesDir/timelapses/<id>/` 디렉터리:

```
filesDir/timelapses/<id>/
├── log.timelapse      # Timelapse (CBOR)
├── thumb.png          # 목록용 썸네일 (재생 종료 상태 = PngComposer 로 1장)
└── bg-<n>.png         # 사진 배경 스냅샷(있을 때만, ref 로 매칭)
```

- `TimelapseStore` 는 `WorkStore` 처럼 앱 싱글톤 + `StateFlow<List<TimelapseMeta>>`(id·createdAt·
  thumb·길이). 저장/삭제 후 디스크에서 재로드.
- 작품 PNG 저장과 **무관하게** 독립 저장(타임랩스만 남길 수도, 둘 다 남길 수도).

## 2. Phase 1 — 기록 (record) + 저장

> 목표: 명시적 버튼으로 녹화하고, 종료/뒤로가기 시 `TimelapseStore` 에 저장.

### 1-A 수집 엔진
- [ ] `TimelapseRecorder` (ui/canvas 또는 works) — `start()/stop()/discard()` + `entries` 보유.
  시작 기준 `SystemClock.elapsedRealtime()` 로 `atMs` 계산.
- [ ] `DrawingViewModel` 배선: `emit(event)` 와 `applyRemoteEvent(event)` 에서 녹화 중이면
  `recorder.add(Draw(event))`. `setBackground`/`setBackgroundColor` 에서 배경 마커 add(§사진 배경 처리).
- [ ] 메모리 정책(§6) — 1단계는 인메모리. 상한 도달 시 동작 정의(경고/스풀).

### 1-B 기록 컨트롤 (UI)
- [ ] 그리기 화면에 **기록 시작 / 종료** 토글 버튼(녹화 중 인디케이터 표시, 예: ● REC).
  위치: TopAppBar 또는 액션 줄. 자동 시작 아님 — 사용자가 켠다.
- [ ] **종료 버튼** → `recorder.stop()` → `TimelapseStore.save(...)` → Toast.
- [ ] **뒤로가기 저장**: `BackHandler` 로, 기록 중이면 떠나기 전에 저장(폐기 아님).
  멀티 모드의 기존 `onDispose(disconnect)` 와 순서 충돌 없게 저장 먼저.
- [ ] (선택) 기록 중 "취소/폐기" — 저장 없이 버리는 명시적 경로.

### 1-C 저장 (TimelapseStore)
- [ ] `TimelapseStore.save` — `filesDir/timelapses/<id>/` 에 `log.timelapse`(CBOR) +
  `thumb.png`(종료 상태를 `PngComposer` 로 1장) + 배경 스냅샷 `bg-<n>.png`(있으면).
- [ ] `StateFlow<List<TimelapseMeta>>` 노출(저장 후 재로드).
- **산출물**: 저장된 타임랩스 엔티티(작품 PNG 와 독립).
- **검증**: 단위 테스트 — 이벤트 시퀀스 → `entries` 순서/`atMs` 단조 증가, 저장→로드 라운드트립.
- **위험**: 낮~중. append 비용 + 종료/뒤로가기 저장 타이밍.

## 3. Phase 2 — 인앱 재생 (view) + 갤러리 + 삭제

> 목표: 홈에서 별도 타임랩스 갤러리로 들어가 선택·재생·삭제.

### 2-A 재생 엔진
- [ ] `TimelapsePlayer` — 빈 `CanvasState` 에 `entries` 를 코루틴 스케줄러로 재적용.
  `Draw` → `canvas.apply(event)`, 배경 마커 → `setBackground*`(ref 비트맵 로드). 다음 항목까지 `atMs` 차이만큼 delay.
- [ ] 컨트롤: 재생/일시정지, 스크럽(특정 시점으로 = 0부터 그 지점까지 즉시 재적용), 속도(1x/2x/4x),
  **긴 정지 구간 압축**(gap 상한, 예: 1s) — "타임랩스" 다운 속도감.

### 2-B 홈 진입 + 갤러리 화면
- [ ] **홈 화면에 "타임랩스" 진입점** 추가(최근 작품 갤러리와 별도 섹션/버튼).
- [ ] 새 라우트 `timelapses` — `TimelapseStore` 목록을 썸네일 그리드로. 탭 → 재생.
  **삭제**: 항목 길게 누르기 또는 삭제 버튼 → `TimelapseStore.delete(id)`(디렉터리 삭제).
- [ ] 새 라우트 `timelapse/{id}` — 재생 화면(`DrawingCanvas` read-only + 재생 컨트롤).
  여기에서도 삭제 가능.
- **산출물**: 홈 → 갤러리 → 재생/삭제 동선.
- **검증**: 재생 후 최종 캔버스 == `thumb.png`(같은 이벤트·렌더러라 일치). 삭제 후 목록 갱신. 실기기 체감.
- **위험**: 중. 스케줄러 정확도·스크럽 재적용 비용·배경 복원.

## 4. Phase 3 — 영상/GIF 내보내기 (share)

> 목표: 인앱 밖으로 공유. 무겁고 분리 가능 — Phase 2 완성 후.

- [ ] 재생하며 프레임 캡처(오프스크린 `ImageBitmap` 렌더, `PngComposer` 류 재사용) → 일정 fps 샘플.
- [ ] 인코딩: MP4(`MediaCodec` + `MediaMuxer`) 또는 GIF. `MediaStore.Video`(`Movies/DrawingTogether`)
  저장 + 공유 인텐트. PNG 내보내기(`WorkStore.exportToGallery`) 패턴 재사용.
- [ ] 진행률 오버레이(인코딩은 시간 걸림).
- **산출물**: 갤러리 영상 + 공유.
- **위험**: 중상. 인코딩 호환성·해상도/용량·시간.

## 5. 사진 배경 처리 (기능 정의 3)

사진 위에 그린 타임랩스는 재생에도 그 사진이 깔려야 의미가 있다. 사진은 `DrawingEvent` 가
아니라 캔버스 상태(`CanvasState.background`)이고 비트맵이 커서 로그(CBOR)에 못 넣는다 →
**비트맵은 파일로 따로 보관하고 로그엔 참조만** 둔다.

설계:
- 기록 중 사진이 **설정/변경/제거**될 때마다 마커를 남긴다:
  - 설정/변경 → 그 비트맵을 `timelapses/<id>/bg-<n>.png` 로 저장 + `BackgroundPhoto(ref="bg-<n>")`.
  - 제거 → `BackgroundPhoto(ref=null)`.
  - 배경색 변경 → `BackgroundColor(argb)`(작아서 로그에 직접).
- 재생 시 마커를 만나면 `ref` 비트맵을 로드해 `canvas.setBackground`(없으면 배경색만).
- **트레이싱 표시 알파**는 재생엔 미반영(원본 농도로 재생) — 표시 보조일 뿐이므로.

단계적 범위:
- **MVP**: 사진은 **세션 동안 1장**(시작/도중 1회 설정) 가정 → `bg-0.png` 하나. 가장 흔한
  "사진 깔고 그리기" 케이스 커버. 도중 교체/제거는 마커 구조는 두되 우선 단순화.
- **확장**: 교체·제거 여러 번을 `bg-<n>` 다중 보관으로 충실 재현.

트레이드오프: 사진이 타임랩스 디렉터리에 복제돼 저장 용량이 늘어남(작품 PNG 와 별개). 타임랩스는
사용자가 명시적으로 남기는 것이라 수용 가능. 추후 동일 사진 dedupe 는 최적화 과제.

## 6. 멀티 모드

- **함께(1:1)**: 양쪽 이벤트가 공유 캔버스로 흐르니 로그가 **합작 과정**을 그대로 담음. 각자
  자기 단말 기준으로 녹화(각자 `apply` 스트림).
- **모임(1:N)**: Phase 1~2 는 **자기 메인 캔버스만** 기록. 피어 미니 뷰 동시 기록은 범위 밖.

## 7. 결정 사항 / 남은 포인트

확정(기능 정의):
- **녹화 시작**: 자동 아님 — **명시적 기록/종료 버튼**. 종료·뒤로가기 시 저장.
- **보기**: 홈에서 **별도 타임랩스 갤러리**로 진입, 거기서 재생·삭제.
- **사진 배경**: 비트맵 별도 파일 보관 + 로그 참조(§5).

착수 전 마저 정할 것:
| 항목 | 선택지 | 1차 권장 |
|---|---|---|
| 로그 보관 | 인메모리 / 디스크 스풀 | 인메모리(세션). 상한 도달 시 경고 |
| 길이 압축 | 실시간 / gap 상한 / 균일 배속 | gap 상한 + 배속 선택 |
| 스크럽 구현 | 역재생 / 0부터 재적용 | **0부터 재적용**(역적용 복잡) |
| 기록 버튼 위치 | TopAppBar / 액션 줄 | 실기기에서 잘 보이는 쪽 |

## 8. 보류 / 비범위

- 역재생(되감기) 애니메이션, 구간 편집/트리밍 — 후속.
- `Undo`/`Clear` 는 이벤트라 재생에 자연히 포함(그렸다 사라지는 과정도 보임) — 별도 처리 없음.
- 협업 redo(별도 [보류 항목](drawing-ideas.md)) 와는 무관.
