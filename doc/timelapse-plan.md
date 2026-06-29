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
- **저장은 가벼운 로그 먼저, 영상은 나중.** `.timelapse`(CBOR) 파일을 작품과 함께 보관(인앱 재생),
  MP4/GIF 내보내기는 별도 단계.

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
    val durationMs: Long,
    val entries: List<TimelapseEntry>,
)
```

- `DrawingEvent` 는 이미 `@Serializable` (CBOR codec 존재) → 로그 직렬화는 거의 공짜.
- 배경 사진 비트맵은 로그에 직접 넣지 않는다(큼). §6 결정 참고.

## 2. Phase 1 — 기록 (record)

> 목표: 그리는 동안 이벤트 로그를 모은다. **화면/저장/재생 변화 없음**(내부 수집만) — 가장 저위험.

- [ ] `TimelapseRecorder` (ui/canvas 또는 works) — `start()/stop()/clear()` + `entries` 보유.
  시작 시각 기준 `SystemClock.elapsedRealtime()` 로 `atMs` 계산.
- [ ] `DrawingViewModel` 배선: `emit(event)` 와 `applyRemoteEvent(event)` 에서 녹화 중이면
  `recorder.add(Draw(event))`. `setBackground`/`setBackgroundColor` 에서 배경 마커 add.
- [ ] 녹화 시작 시점 결정(§6) — 우선 **그리기 화면 진입 시 자동 시작**, 폐기/리셋 액션 제공.
- [ ] 메모리 정책(§6) — 1단계는 인메모리. 상한 도달 시 동작 정의(경고/스풀).
- **산출물**: 세션 종료 시 `Timelapse` 객체 1개.
- **검증**: 단위 테스트 — 이벤트 시퀀스 입력 → `entries` 순서/`atMs` 단조 증가 확인.
- **위험**: 낮음. append 비용만 추가(이벤트는 작음).

## 3. Phase 2 — 인앱 재생 (view) + 로그 저장

> 목표: 모은 로그를 다시 그려 "과정"을 본다. 작품과 함께 로그를 저장해 언제든 재생.

### 2-A 재생 엔진
- [ ] `TimelapsePlayer` — 빈 `CanvasState` 에 `entries` 를 코루틴 스케줄러로 재적용.
  `Draw` → `canvas.apply(event)`, 배경 마커 → `setBackground*`. 다음 항목까지 `atMs` 차이만큼 delay.
- [ ] 컨트롤: 재생/일시정지, 스크럽(특정 시점으로 = 0부터 그 지점까지 즉시 재적용), 속도(1x/2x/4x),
  **긴 정지 구간 압축**(gap 상한, 예: 1s) — "타임랩스" 다운 속도감.

### 2-B 재생 화면
- [ ] 새 라우트 `replay/{workId}` (`AppNavGraph`). 캔버스는 `DrawingCanvas` read-only 재사용
  (`pointerInput` 미부착) + 하단 재생 컨트롤.
- [ ] 진입점: `preview/{workId}` 에 "▶ 과정 재생" 버튼(해당 작품에 `.timelapse` 있을 때만).

### 2-C 저장
- [ ] `WorkStore` 확장: 작품 저장 시 `filesDir/works/<id>.timelapse`(CBOR) 동반 저장.
  `<id>.png` + `<id>.meta` 패턴에 한 파일 추가. `.meta` 에 타임랩스 유무 플래그.
- [ ] 로드: `Work` 에 `hasTimelapse: Boolean`. 없으면 재생 버튼 숨김.
- **산출물**: 작품별 인앱 재생 가능.
- **검증**: 재생 후 최종 캔버스 == 저장 PNG 상태(같은 이벤트·렌더러라 일치해야 함). 실기기 체감.
- **위험**: 중. 스케줄러 정확도·스크럽 시 재적용 비용·배경 복원.

## 4. Phase 3 — 영상/GIF 내보내기 (share)

> 목표: 인앱 밖으로 공유. 무겁고 분리 가능 — Phase 2 완성 후.

- [ ] 재생하며 프레임 캡처(오프스크린 `ImageBitmap` 렌더, `PngComposer` 류 재사용) → 일정 fps 샘플.
- [ ] 인코딩: MP4(`MediaCodec` + `MediaMuxer`) 또는 GIF. `MediaStore.Video`(`Movies/DrawingTogether`)
  저장 + 공유 인텐트. PNG 내보내기(`WorkStore.exportToGallery`) 패턴 재사용.
- [ ] 진행률 오버레이(인코딩은 시간 걸림).
- **산출물**: 갤러리 영상 + 공유.
- **위험**: 중상. 인코딩 호환성·해상도/용량·시간.

## 5. 멀티 모드

- **함께(1:1)**: 양쪽 이벤트가 공유 캔버스로 흐르니 로그가 **합작 과정**을 그대로 담음. 각자
  자기 단말 기준으로 녹화(각자 `apply` 스트림).
- **모임(1:N)**: Phase 1~2 는 **자기 메인 캔버스만** 기록. 피어 미니 뷰 동시 기록은 범위 밖.

## 6. 결정 필요 포인트 (착수 전)

| 항목 | 선택지 | 1차 권장 |
|---|---|---|
| 로그 보관 | 인메모리 / 디스크 스풀 | 인메모리(세션). 상한 도달 시 경고 |
| 사진 배경 | 시작 시 1장 참조 / 변경마다 비트맵 보관(큼) | **시작 시 1장 참조** (작품 PNG·원본 활용) |
| 녹화 시작 | 화면 진입 자동 / 명시적 토글 | **자동 + 폐기 버튼** |
| 길이 압축 | 실시간 / gap 상한 / 균일 배속 | gap 상한 + 배속 선택 |
| 스크럽 구현 | 역재생 / 0부터 재적용 | **0부터 재적용**(역적용 복잡) |

## 7. 보류 / 비범위

- 역재생(되감기) 애니메이션, 구간 편집/트리밍 — 후속.
- `Undo`/`Clear` 는 이벤트라 재생에 자연히 포함(그렸다 사라지는 과정도 보임) — 별도 처리 없음.
- 협업 redo(별도 [보류 항목](drawing-ideas.md)) 와는 무관.
