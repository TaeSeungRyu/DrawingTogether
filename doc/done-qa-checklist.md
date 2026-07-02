# QA 체크리스트 — 버그 수정 검증 (2026-07-01 리포트 대응)

`doc/done-bug-hunt-2026-07-01.md`의 확정 17건을 수정할 때 사용하는 검증 가이드.
번호(#N)는 리포트의 항목 번호와 일치.

## 사전 준비

- **기기**: 멀티 검증은 에뮬레이터 불가(Nearby + Google Play Services 필요, CLAUDE.md 참조).
  - 함께(Duo) 검증: 물리 기기 **2대**
  - 모임/교실 mesh 검증(#4·#5): 물리 기기 **3대 이상**(호스트 1 + 조인자 2) — 2대로는 joiner→joiner relay 경로가 안 타서 버그가 안 드러남
- **빌드/설치**: `.\gradlew.bat installDebug` (각 기기에)

## 매 작업(수정 1건) 후 공통 게이트

수정 커밋 전에 항상:

```powershell
.\gradlew.bat test          # 유닛 테스트 (해당 수정의 red→green 테스트 포함)
.\gradlew.bat lintDebug     # 정적 검사
```

- 엔진/코덱 레벨 수정은 **유닛 테스트를 같은 커밋에** 넣어 자동 검증(아래 각 항목의 테스트 코드 참고).
- 그다음 해당 항목의 **기기 QA 스텝**을 수행.
- 통과 확인 후 다음 작업으로.

---

## 진행 현황 (2026-07-02 — 전부 완료·검증 통과)

**확정 17건 + 신규 3건(Duo 하트비트, 모임 mesh 늦은참여 자동채움, broadcast endpoint별 전송) 코드 완료 및 검증 통과.** 각 항목 별도 커밋. 이 버그 수정 이니셔티브 종료.

| 항목 | 커밋 | 코드 | 검증 |
|---|---|---|---|
| #1 선 굵기 정규화 | `6b9d8ec` | ✅ | 유닛 + 기기 1대 QA ✅ |
| #16 타임랩스 굵기 | `2e428d1` | ✅ | 유닛 + 기기 1대 QA ✅ |
| #8 applySnapshot undo 순서 | `1110ed8` | ✅ | 유닛 ✅ |
| #6 openStroke 누수 | `a92dc5e` | ✅ | 유닛 + 기기 QA ✅ |
| #7 Duo 재연결 교착 | `f8f5b18` | ✅ | 기기 2대 QA ✅ |
| Duo 하트비트 (신규) | `6ddd7a8` | ✅ | 기기 2대 QA ✅ |
| #2 maxJoiners 우회 | `212a30a` | ✅ | 기기 QA ✅ (문제없음) |
| #3 동시 pending 유실 | `212a30a` | ✅ | 기기 QA ✅ (문제없음) |
| #11 인코더 누수 | `1b08410` | ✅ | 코드리뷰 + 기기 ✅ |
| #10 공유 컬렉션 | `d2655ae` | ✅ | 코드리뷰 + 기기 ✅ |
| #14 orphan png | `bf4f8bb` | ✅ | 유닛 ✅ |
| #15 동시 save 이름 | `bf4f8bb` | ✅ | 코드리뷰 ✅ |
| #17 EdgeDetector recycle | `2d2f0f1` | ✅ | 코드리뷰 ✅ |
| #12 스티커 핸들 no-op | `3449204` | ✅ | 기기 QA ✅ |
| #13 조인자 재검색 | `3d801c8` | ✅ | 기기 QA ✅ |
| #4 배경색/비율 발신자 | `c996921` | ✅ | 코덱 유닛 ✅ + 3대 QA ✅ |
| #5 스냅샷 발신자 | `c996921` | ✅ | 코덱 유닛 ✅ + 3대 QA ✅ |
| #9 에어브러시 등 질감 | (#1 헬퍼) | ✅ | export 관점 #1로 해소 |
| 모임 mesh 늦은참여 자동채움 (신규) | `2f9c787` | ✅ | 3대 QA ✅ |
| 모임 broadcast endpoint별 전송 (신규) | `57942da` | ✅ | 3대 QA ✅ |

> 아래 각 섹션의 상세 QA 스텝은 회귀 검증 시 재사용 가능.

---

## A. 유닛 테스트로 검증 (기기 불필요)

### #8 — applySnapshot undo 순서
- **파일**: `drawing/engine/CanvasState.kt:116-120`
- **전제**: 이 수정은 순서 정보가 필요함. 두 방향 중 택1 →
  (a) `Stroke`/`Sticker`/`TextElement`에 단조 증가 `seq` 추가 후 병합 정렬,
  (b) `CanvasSnapshot`에 undo 순서열(`List<UndoItem>` 상당)을 실어 그대로 복원.
- **테스트(수정과 함께 `CanvasStateTest.kt`에 추가)** — 시간순 interleave 시나리오:

```kotlin
@Test
fun `applySnapshot restores undo order chronologically, not by type`() {
    // 실제 추가 순서: stroke(s1) → sticker(sk2) → stroke(s3) → text(t4)
    val src = CanvasState()
    src.apply(DrawingEvent.StrokeStart(next(), local, StrokeId("s1"), pen, Point(0f, 0f)))
    src.apply(DrawingEvent.StrokeEnd(next(), local, StrokeId("s1")))
    src.apply(place(StickerId("sk2")))
    src.apply(DrawingEvent.StrokeStart(next(), local, StrokeId("s3"), pen, Point(0.1f, 0.1f)))
    src.apply(DrawingEvent.StrokeEnd(next(), local, StrokeId("s3")))
    src.apply(placeText(TextId("t4")))

    // 스냅샷 → 새 상태로 복원 (수정된 applySnapshot 시그니처에 맞춰 호출)
    val fresh = CanvasState()
    fresh.applySnapshot(
        strokes = src.strokes.toList(),
        stickers = src.stickers.toList(),
        texts = src.texts.toList(),
        // (b)안이면 여기에 undo 순서열 전달
    )

    // 되돌리기는 실제 시간역순으로: t4 → s3 → sk2 → s1
    assertEquals(UndoItem.TextRef(TextId("t4")), fresh.lastUndoable())
    fresh.apply(DrawingEvent.RemoveText(next(), local, TextId("t4")))
    assertEquals(UndoItem.StrokeRef(StrokeId("s3")), fresh.lastUndoable())
    fresh.apply(DrawingEvent.Undo(next(), local, StrokeId("s3")))
    assertEquals(UndoItem.StickerRef(StickerId("sk2")), fresh.lastUndoable())
}
```

- **기기 확인(선택)**: 멀티에서 상대가 stroke→스티커→stroke→텍스트 순으로 그린 캔버스를 "가져오기" 후 되돌리기 연타 → 마지막에 그린 것부터 지워지는지.

### #1 — 선 굵기 정규화
- **파일**: `ui/canvas/StrokeRenderer.kt:370-371` (`strokeWidthPxFor`)
- **전제**: `strokeWidthPxFor`를 canvasSize(짧은 변) 비례로 바꾸거나, PngComposer가 export 해상도 비율로 density를 스케일해 전달.
- **테스트**: `strokeWidthPxFor`는 순수 함수라 JVM 테스트 가능. 수정 후 시그니처에 맞춰 "캔버스가 2배 크면 굵기도 2배"를 단언:

```kotlin
// StrokeRendererTest.kt (신규)
@Test
fun `stroke width scales with canvas short side`() {
    val tool = ToolSettings(ToolKind.Pen, 0xFF000000.toInt(), 4f)
    val small = strokeWidthPxFor(tool, density = 1f, shortSidePx = 1000) // 수정 후 시그니처
    val large = strokeWidthPxFor(tool, density = 1f, shortSidePx = 2000)
    assertEquals(small * 2f, large, 0.01f)
}
```

- **기기 확인(권장, 1대)**: 아래 B의 #1 참조 (시각 확인이 최종 판정).

### #4·#5 — 프레임 발신자 필드 라운드트립
- **파일**: `transport/Frame.kt`, `transport/codec/FrameCodec.kt`
- **전제**: `CanvasAspectFrame`/`BackgroundColorFrame`/`Snapshot`/`PhotoMeta`에 발신자 peerId 필드 추가.
- **테스트(`FrameCodecTest.kt`에 추가)**: 인코드→디코드 후 발신자 필드가 보존되는지 라운드트립. 필드 추가 후:

```kotlin
@Test
fun `aspect frame preserves senderPeerId across codec`() {
    val frame = Frame.CanvasAspectFrame(aspect = CanvasAspect.Portrait9_16, senderPeerId = "peer-a")
    val decoded = FrameCodec.decode(FrameCodec.encode(frame))
    assertEquals("peer-a", (decoded as Frame.CanvasAspectFrame).senderPeerId)
}
```

- (라우팅 로직 자체는 `SessionManager`가 Nearby에 묶여 순수 유닛 테스트가 어려움 → 실제 라우팅은 아래 C의 mesh 기기 QA로 검증.)

---

## B. 기기 1대로 시각/동작 확인

### #1 — 선 굵기 (저장 WYSIWYG)
1. 고해상도 사진을 배경으로 불러오기.
2. 굵은 펜(예 12dp)으로 선 몇 개 + 스티커 1개 + 텍스트 1개 그리기.
3. 저장 → 갤러리/미리보기에서 PNG 확인.
- **기대**: PNG의 선 굵기가 화면에서 본 굵기와 (스티커·텍스트 대비) 같은 비율. 선이 실낱같이 얇아지지 않음.

### #9 — 에어브러시/네온/블러 질감
1. 에어브러시로 사진 배경 위에 칠하기 → 저장.
- **기대**: PNG의 점 밀도/분사 폭이 화면과 동일한 질감. (네온 glow·블러 번짐도 동일.)

### #16 — 타임랩스 영상 굵기
1. 그림을 그려 타임랩스 기록 → 영상 export → 재생.
- **기대**: 영상 속 선 두께 비율이 화면에서 본 것과 일치(캔버스 폭 400dp 가정으로 어긋나지 않음).

### #6 — 그리는 중 툴 변경 (openStroke 누수) — 멀티터치 필요
1. 한 손가락으로 캔버스에 선을 그리는 중(손 떼지 않음).
2. 다른 손으로 툴바에서 펜→지우개(또는 스티커/텍스트)로 전환.
3. 손 떼기.
- **기대**: 미완료 선이 화면에 영구히 남지 않음. 되돌리기로 정리되거나 애초에 남지 않음. (멀티라면 상대 화면에도 열린 선이 안 남음 — C에서 재확인.)

### #12 — 스티커 핸들 탭 (no-op 이벤트) — 멀티에서 관측
- 1대에선 육안 확인 어려움. C의 멀티 QA에서 "핸들 탭만 했을 때 상대 화면 변화 없음"으로 확인.

---

## C. 멀티 기기 QA (에뮬레이터 불가)

### 2대 (함께/Duo)

#### #7 — Duo 재연결 교착
1. 2대로 함께 모드 연결 후 그리기.
2. 한 기기의 Wi-Fi를 끄거나 멀리 떨어뜨려 연결 끊기.
3. **양쪽** 모두 재연결 스낵바 확인 → 양쪽 다 "재연결" 탭.
- **기대(수정 후)**: 자동으로 다시 연결됨. (수정 전엔 둘 다 "방 여는 중"에서 멈춤 → 이게 재현되면 버그 존재 확인.)

#### #6 — openStroke 누수 (멀티 관점)
1. 함께 모드에서 A가 그리는 중 툴 변경(위 B #6 절차).
- **기대**: B 화면에도 A의 미완료 선이 영구히 남지 않음.

#### #8 — 가져오기 후 되돌리기 (멀티 관점)
1. A가 stroke→스티커→stroke→텍스트 순으로 그림.
2. B가 "동기화/가져오기"로 A 캔버스를 받음 → 되돌리기 연타.
- **기대**: 마지막에 그린 것부터 순서대로 제거.

### 3~4대 (모임 mesh — ★ 3대 이상 필수)

#### #4 — 배경색/비율 발신자 오인식
1. 호스트 + 조인자 A + 조인자 B로 모임 연결.
2. **조인자 A**가 배경색(또는 캔버스 비율) 변경.
3. **조인자 B** 화면에서 A의 미니뷰를 확인.
- **기대(수정 후)**: B가 보는 **A 미니뷰**의 배경색/비율이 갱신됨. B가 보는 **호스트 미니뷰**는 영향 없음. (수정 전엔 A 미니뷰 불변 + 호스트 미니뷰가 A 색으로 오염.)

#### #5 — 가져오기 스냅샷 발신자 오인식
1. 호스트 + 조인자 A + 조인자 B 연결.
2. 조인자 A가 "가져오기"로 자기 캔버스를 갱신(호스트 내용 반영 등).
3. 조인자 B 화면에서 A 미니뷰 vs 호스트 미니뷰 확인.
- **기대(수정 후)**: A의 스냅샷이 B의 **A 미니뷰**에 반영. 호스트 미니뷰가 A 그림으로 덮이지 않음.

#### #2 — 호스트 인원 제한 (레이스, 재현 어려움)
1. 모임(정원 3) 호스트에 조인자 4명이 거의 동시에 접속 시도.
- **기대**: 최대 3명까지만 연결, 4번째는 거부. (레이스라 수동 재현 난이도 높음 → 코드 리뷰 병행.)

#### #3 — 동시 접속 pending 유실 (레이스)
1. 두 조인자가 거의 동시에 호스트에 접속 시도.
- **기대**: 두 명 모두 토큰 컨펌 다이얼로그가 순차로 떠 연결 성공. 한 명이 "연결 안 됨"으로 누락되지 않음.

#### #13 — 조인자 대기 중 호스트 이탈
1. 조인자가 접속해 "호스트가 시작하기를 기다리는 중" 상태.
2. 호스트가 방을 떠남/연결 끊김.
- **기대**: 조인자가 뒤로가기 없이 재검색 가능(다시 검색 버튼 또는 자동 재검색).

#### #12 — 스티커 핸들 탭
1. 스티커를 배치하고 선택 → 크기/회전 핸들을 **드래그 없이 짧게 탭만**.
- **기대(수정 후)**: 상대 화면에 스티커 변화 없음(불필요한 TransformSticker 미전송).

---

## D. 코드 리뷰 중심 (수동 재현 난망 — 실패 경로/레이스)

기기 QA로는 재현이 거의 불가능. 수정 diff의 코드 검토 + (가능하면) 계측 테스트로 확인.

- **#10** Nearby 콜백/코루틴 공유 컬렉션 → `outgoingFilePayloads`/`nickByEndpoint`가 `ConcurrentHashMap` 계열로 바뀌었는지 확인. 장시간 파일(사진) 전송 반복 시 크래시/누락 없는지 스트레스.
- **#11** MediaCodec/Muxer 누수 → try/finally로 감쌌는지 코드 확인. 타임랩스 export를 연속 10회+ 반복해도 인코더 고갈로 실패하지 않는지.
- **#14** orphan png → meta 쓰기 실패 롤백 또는 초기화 스윕 추가 확인. (재현: 저장 도중 앱 강제종료 후 재실행 → 목록/디스크 정합성.)
- **#15** 동시 save 이름 중복 → `Mutex` 직렬화 확인. (저장→그리기→같은 이름 재저장 빠르게 반복 시 라벨 중복 없는지.)
- **#17** EdgeDetector copy recycle → copy 분기에서 recycle 호출 추가 확인. (원본 `src`는 recycle 금지 — 호출자 소유.)

---

## 진행 흐름 (per-task)

각 수정 1건마다:
1. 수정 + (해당되면) 유닛 테스트를 같은 커밋에.
2. `.\gradlew.bat test` + `lintDebug` 통과.
3. 위 해당 항목의 기기 QA 스텝 수행 → 기대결과 확인.
4. 통과 시 커밋 → 다음 작업.

> 작업이 끝날 때마다 어떤 QA(유닛/1대/2대/3대+/코드리뷰)를 돌려야 하는지 그 항목 기준으로 안내함.
