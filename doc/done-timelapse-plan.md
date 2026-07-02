# 타임랩스 재생·공유 계획 — 이벤트 로그 기록 → 재생 → 영상 내보내기

> 상태: **완료 — Phase 1~3 + 다듬기** (기록·저장 / 재생·갤러리·삭제 / MP4 내보내기·공유).
> 실기기 검증 양호. 다듬기: 기록 타이머·15분 소프트 가드, 종료 저장 백그라운드화, 재생 실시간화,
> 비트맵 캐시, 내보내기 해상도/배속 옵션 + 비트레이트·사진 화질 개선.
> 상세·우선순위는 [drawing-ideas.md](drawing-ideas.md), 단계 표기는 [roadmap.md](roadmap.md). 결정 사항은 §7.

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

1. **명시적 기록/종료 + 메모리 임시 보관.** 그리기 화면 **TopAppBar** 에 **기록 시작 / 종료** 버튼.
   녹화는 자동 아님 — 사용자가 켠다. 기록은 **메모리에만 임시 보관**하고, **"저장(종료)" 을 눌러야
   디스크에 기록**된다. 저장 전에 **앱이 종료되면 기록은 소실**(증분 저장·복구 없음 — 단순화).
   - 기록 중 화면을 뒤로가기로 떠날 때는 **저장/폐기 확인**(소리 없이 잃지 않게).
2. **보기는 홈에서 별도 진입.** 홈 화면에서 **타임랩스 갤러리**로 들어가 목록에서 선택해 재생.
   그 갤러리(또는 재생 화면)에서 **삭제**도 가능. (작품 PNG 갤러리와 별개 목록.)
3. **사진 배경 처리.** 사진 위에 그린 경우 재생에도 그 사진이 깔려야 의미가 있음 → 저장 시 배경
   사진을 타임랩스에 함께 기록하고 재생 시 적용. 상세 방안은 §"사진 배경 처리".

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

## 2. Phase 1 — 기록 (record) + 저장 ✅ 구현됨

> 목표: 명시적 버튼으로 **메모리에 녹화**하고, **저장(종료) 시에만** `TimelapseStore` 에 디스크 기록.
> 저장 전 앱 종료 시 소실(증분 저장·복구 없음).

### 1-A 수집 엔진 (인메모리)
- [x] `TimelapseRecorder` (ui/canvas) — `start(초기 스냅샷)/stop()/discard()` + 인메모리 `entries`/`backgrounds`.
  시작 기준 `SystemClock.elapsedRealtime()` 로 `atMs`. `stop()` 은 `RecordedTimelapse`(로그+배경) 반환.
- [x] **기록 시작 시점 캔버스 시딩** — 기록 버튼 전 작업도 재생에 보이도록 시작 시 현재 strokes/스티커/
  배경을 atMs=0 의 `TimelapseOp.Snapshot`+배경 마커로 심음(`startRecording`). 기록 중 그린 게 없으면 저장 안 함.
- [x] `DrawingViewModel` 배선: `emit` 와 `applyRemoteEvent`(Shared 만) 에서 `recorder.recordEvent`,
  `setBackground`/`setBackgroundColor` 에서 배경 마커.
- [x] 기록 경과 시간(● REC m:ss) + **소프트 가드** — 인메모리라 무한정이면 앱 종료 시 소실·탐색 둔화
  위험. `MAX_RECORD_MS`(15분) 1분 전 경고 + 도달 시 자동 종료-저장(`DrawingScreen` LaunchedEffect).

### 1-B 기록 컨트롤 (UI)
- [x] **TopAppBar** 기록 시작 / 종료(저장) 토글(`RecordGlyph`/`StopGlyph`) + 캔버스 좌상단 ● REC 인디케이터.
- [x] **종료(저장)** → `vm.finishRecording()` → `PngComposer` 썸네일 → `TimelapseStore.save` → Toast.
- [x] **뒤로가기**(시스템 + ← 버튼): 기록 중이면 `BackHandler`/navigationIcon 가로채 **저장/폐기 확인 다이얼로그**.
- [x] 앱 백그라운드/종료: 별도 저장 안 함 — **소실 허용**(설계 결정).

### 1-C 저장 (TimelapseStore)
- [x] `TimelapseStore.save` — `filesDir/timelapses/<id>/` 에 `log.timelapse`(CBOR) + `meta`(목록용) +
  `thumb.png`(종료 상태 `PngComposer`) + `bg-<n>.png`(배경 있을 때).
- [x] `StateFlow<List<TimelapseMeta>>` + `delete(id)`/`loadLog(id)`(Phase 2 용) 노출.
- **검증**: `TimelapseSerializationTest` — `Timelapse`(`TimelapseOp.Draw` 안 sealed `DrawingEvent` 다형)
  CBOR 라운드트립 통과. 실기기 기록→저장 동작 확인 필요.
- **위험**: 낮. 인메모리 수집 + 저장 시 일괄 쓰기(기존 PNG 저장 패턴).

## 3. Phase 2 — 인앱 재생 (view) + 갤러리 + 삭제 ✅ 구현됨

> 목표: 홈에서 별도 타임랩스 갤러리로 들어가 선택·재생·삭제.

### 2-A 재생 엔진
- [x] `TimelapsePlayer` — **프레임 시계(`withFrameNanos`)로 positionMs 를 연속 전진**시키고, 그 시각을
  지난 이벤트를 apply. 정지 구간(색 고르기·가만히 있기)도 실시간 그대로 흐름 → 슬라이더가 멈추지 않음.
  `Draw` → `canvas.apply`, 배경 마커 → `setBackground*`(ref 비트맵 사전 디코드 맵).
- [x] 컨트롤: 재생/일시정지, **seek 슬라이더**(0부터 `rebuildTo` 재적용), 속도(1x/2x/4x), 처음으로.
  `CanvasState.reset()` 추가(재생 rebuild 용). seek 은 드래그 중이 아닌 **놓을 때(`onValueChangeFinished`)만**
  rebuild — 드래그마다 0부터 재구성하던 끊김 제거.
- **정지 구간 정책 변경**: 초기 "gap 상한 압축" → **실시간 보존 + 배속**으로 변경. 정지도 실제 시간만큼
  재생(슬라이더 연속 이동), 빠르게 보려면 1/2/4x. (사용자 피드백: 5초 정지는 5초로 보여야 함.)

### 2-B 홈 진입 + 갤러리 화면
- [x] **홈 "타임랩스" 버튼** 추가(`onTimelapses`). 최근 작품과 별도.
- [x] 라우트 `timelapses` — `TimelapseGalleryScreen`: `TimelapseStore.items` 썸네일 그리드(다운샘플 디코드),
  탭 → 재생, **길게 누르기 → 삭제 확인**(`store.delete`).
- [x] 라우트 `timelapse/{id}` — `TimelapsePlayerScreen`: read-only `ReplayCanvas`(DrawingCanvas 렌더러
  재사용, pointerInput 없음) + 컨트롤 + 상단 "삭제".
- **검증**: 컴파일 통과. 재생 후 최종 == `thumb.png` 여부·seek 체감·배경 복원은 실기기 확인 필요.
- **위험**: 중. 스크럽 시 매번 0부터 rebuild — 긴 로그에서 드래그 비용(후속 최적화 여지).

## 4. Phase 3 — 영상 내보내기 (share) ✅ 구현됨

> 목표: 인앱 밖으로 공유. MP4 로 결정(GIF 미사용).

- [x] `TimelapseVideoExporter` (works) — 헤드리스로 프레임 렌더(같은 렌더러 재사용) → MP4 인코딩.
  - 프레임: 콘텐츠 시계를 fps(12)로 스텝, 빈 `CanvasState` 에 이벤트 누적 적용 후 `renderFrame`.
    긴 녹화는 `frameStepMs` 를 늘려 가속(최대 ~1200 프레임)으로 영상 길이 제한.
  - 인코딩: `MediaCodec`(H.264, `COLOR_FormatYUV420Flexible`) + `getInputImage` 로 ARGB→YUV420
    (plane rowStride/pixelStride 따름 → planar/semiplanar 대응) + `MediaMuxer`(MP4, cache temp).
  - 저장: `MediaStore.Video`(`Movies/DrawingTogether`, IS_PENDING 패턴) → 공유 인텐트(`ACTION_SEND`).
  - 출력: 최대 변 480(짝수), 배경 비율 반영. 해상도별 stroke 굵기는 density=w/400 근사.
- [x] `TimelapsePlayerScreen` 상단 "내보내기" → **설정 다이얼로그(해상도 낮음/보통/높음=360/480/720,
  배속 1x/2x/4x)** → 진행률 오버레이(%) + 완료 시 갤러리 저장 Toast + 공유 시트.
  배속은 프레임당 콘텐츠 진행 시간을 늘려 영상 길이를 줄임(`encode(speed)`), 해상도는 출력 최대 변(`maxDim`).
- **위험**: 중상 — MediaCodec/색포맷·해상도 기기차. **에뮬레이터/CI 검증 불가, 실기기 필수.** (기본 검증 양호)
- **후속 여지**: 진행 중 취소, 매우 긴 녹화 메모리.

## 5. 사진 배경 처리 (기능 정의 3)

사진 위에 그린 타임랩스는 재생에도 그 사진이 깔려야 의미가 있다. 사진은 `DrawingEvent` 가
아니라 캔버스 상태(`CanvasState.background`)이고 비트맵이 커서 로그(CBOR)에 못 넣는다 →
**비트맵은 파일로 따로 보관하고 로그엔 참조만** 둔다.

설계 (인메모리 모델에 맞춤 — 디스크 쓰기는 저장 시점에만):
- 기록 중 사진이 **설정/변경/제거**될 때 마커를 메모리 로그에 남긴다:
  - 설정/변경 → `BackgroundPhoto(ref="bg-<n>")` + 그 `ImageBitmap` 을 recorder 가 메모리에 보유.
  - 제거 → `BackgroundPhoto(ref=null)`.
  - 배경색 변경 → `BackgroundColor(argb)`(작아서 로그에 직접).
- **저장(종료) 시** 보유 중인 배경 비트맵들을 `timelapses/<id>/bg-<n>.png` 로 일괄 기록.
- 재생 시 마커를 만나면 `ref` 비트맵을 로드해 `canvas.setBackground`(없으면 배경색만).
- **트레이싱 표시 알파**는 재생엔 미반영(원본 농도로 재생) — 표시 보조일 뿐이므로.

단계적 범위:
- **MVP**: 사진은 **세션 동안 1장**(시작/도중 1회 설정) 가정 → 저장 시 `bg-0.png` 하나. 가장 흔한
  "사진 깔고 그리기" 케이스 커버. 도중 교체/제거는 마커 구조는 두되 우선 단순화.
- **확장**: 교체·제거 여러 번을 `bg-<n>` 다중 보관으로 충실 재현.

트레이드오프: 사진이 타임랩스 디렉터리에 복제돼 저장 용량이 늘어남(작품 PNG 와 별개). 타임랩스는
사용자가 명시적으로 남기는 것이라 수용 가능. 추후 동일 사진 dedupe 는 최적화 과제.

## 6. 멀티 모드

- **함께(1:1)**: 양쪽 이벤트가 공유 캔버스로 흐르니 로그가 **합작 과정**을 그대로 담음. 각자
  자기 단말 기준으로 녹화(각자 `apply` 스트림).
- **모임(1:N)**: Phase 1~2 는 **자기 메인 캔버스만** 기록. 피어 미니 뷰 동시 기록은 범위 밖.

## 7. 결정 사항 (착수 전 확정 완료)

| 항목 | 결정 |
|---|---|
| 녹화 시작 | 자동 아님 — **명시적 기록/종료 버튼** |
| 기록 버튼 위치 | **TopAppBar** (+ 캔버스 ● REC 인디케이터) |
| 로그 보관 | **메모리 임시 보관**. 저장(종료) 눌러야 디스크 기록. 저장 전 앱 종료 시 **소실**(증분 저장·복구 없음) |
| 뒤로가기(기록 중) | **저장/폐기 확인 다이얼로그** |
| 보기 | 홈에서 **별도 타임랩스 갤러리** 진입 → 재생·삭제 |
| 사진 배경 | 메모리 보유 → 저장 시 `bg-<n>.png` 기록 + 로그 `ref`(§5). MVP 1장 |
| 타이밍/길이 | 원본 `atMs` 저장 → 재생은 **실시간 보존**(프레임 시계로 연속 전진) + 배속(1/2/4x). gap 압축은 폐기(정지도 실제 시간만큼 흐름) |
| 스크럽 | **0부터 재적용**(rebuild). 역재생 안 함 |
| 메모리 상한 | **확정·구현**: 시간 기반 소프트 가드 `MAX_RECORD_MS`=15분(1분 전 경고 + 자동 종료저장) |

## 8. 보류 / 비범위

- 역재생(되감기) 애니메이션, 구간 편집/트리밍 — 후속.
- `Undo`/`Clear` 는 이벤트라 재생에 자연히 포함(그렸다 사라지는 과정도 보임) — 별도 처리 없음.
- 협업 redo(별도 [보류 항목](drawing-ideas.md)) 와는 무관.
