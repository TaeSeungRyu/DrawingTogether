# 아키텍처

## 1. 큰 그림

`DrawingTogether`는 세 모드를 같은 UI로 지원합니다.

- **싱글 모드**: 한 기기에서 혼자 그림. 네트워크 비활성. 사진 배경(선택/촬영)도 지원.
- **멀티 모드 (1:1)**: Nearby Connections (`P2P_POINT_TO_POINT`) 로 연결된 두 기기 사이 드로잉 이벤트 실시간 동기화. 한쪽이 사진을 보내면 양쪽 모두 같은 사진 위에 그림. "함께 그리기" 단일 모드 — 자기·상대 stroke 모두 편집·삭제 가능, 캔버스 공유.
- **다중 모드 (1:N, Phase 4 예정)**: `P2P_STAR` 호스트 1 + 조인자 최대 3. 각 기기는 자기 캔버스를 크게, 다른 3명은 미니 read-only 뷰로 봄. "동기화" 로 누구 한 명의 캔버스를 가져옴. 사진 비활성. 호스트가 조인자간 메시지 relay.

핵심 결정: **모드의 차이는 주로 "이벤트가 어디서 오는가, 어디로 가는가" 와 "캔버스가 몇 개인가"**. 로컬 입력이든 원격 인바운드든 동일한 `DrawingEvent` 스트림으로 정규화 — 1:1 은 단일 `CanvasState`, 1:N 은 본인용 + 피어별 미니용 `Map<PeerId, CanvasState>`. 사진도 동일 — 자기가 고른 것이든 상대가 보낸 것이든 같은 `BackgroundImage` 상태에 들어감(다중모드에선 사진 자체가 없음).

```
[Touch input]   ──┐
                  │
[Local photo]   ──┼──► CanvasState ──► Compose Canvas (사진 위 stroke 오버레이)
                  │
[Inbound event] ──┤
[Inbound photo] ──┘                          │
                                             └──► outbound (멀티 모드일 때만)
```

## 2. 레이어 구성

단일 모듈(`:app`) 안에서 패키지로 레이어 분리.

```
com.rts.rys.ryy.drawingtogether
├── ui/                  Compose 화면 + 테마
│   ├── AppNavGraph.kt   NavHost — splash → home → (draw | pairing)
│   ├── splash/          앱 진입 시 잠깐 보이는 스플래시
│   ├── home/            모드 선택 (싱글 / 멀티)
│   ├── pairing/         Nearby 디바이스 검색·연결 화면 (Phase 2부터 구현)
│   ├── canvas/          드로잉 화면 (붓·색·지우개·사진 도구 + StrokeRenderer)
│   ├── preview/         저장된 작품 풀사이즈 보기 (Phase 1.6)
│   └── theme/           (기존)
├── drawing/             드로잉 도메인 — UI/네트워크 무관
│   ├── model/           Stroke, Point, ToolSettings, DrawingEvent, BackgroundImage
│   ├── engine/          CanvasState, 이벤트 적용기, 되돌리기 스택
│   └── render/          Compose Canvas 어댑터, PNG 합성기
├── photo/               사진 입력 (Phase 1.5)
│   ├── PhotoPicker      갤러리에서 선택 (PhotoPicker API)
│   ├── PhotoCapture     카메라로 촬영 (CameraX 또는 ACTION_IMAGE_CAPTURE)
│   └── PhotoLoader      Uri → Bitmap + 메타데이터
├── works/               저장된 작품 영속성 (Phase 1.6)
│   ├── Work             메타데이터 (id, 저장 시각, 크기, 사진 유무)
│   ├── WorkStore        filesDir/works/ — PNG + .meta 파일, StateFlow 인덱스
│   └── PngComposer      CanvasState → ImageBitmap → PNG (StrokeRenderer 재사용)
├── transport/           Nearby Connections 연결 + 직렬화
│   ├── nearby/          NearbyTransport — 광고/검색/연결/페이로드 송수신
│   └── codec/           Frame ↔ CBOR 바이트
├── session/             세션 상태 머신 (Idle → Discovering → Connecting → Connected → Drawing)
└── MainActivity.kt
```

네비게이션 흐름:

```
splash ──auto──► home ──싱글모드──► draw   (DrawingScreen)
                  ├────멀티모드──► pairing (Phase 2)──► draw (멀티 모드 변형)
                  └────썸네일 탭──► preview/{workId} (Phase 1.6 PreviewScreen)
```

`splash`에서 `home`으로 진입할 때는 `popUpTo(Splash) { inclusive = true }`로 백스택에서 제거. 미리보기는 백스택에 쌓여 뒤로 가기로 홈 복귀.

레이어 의존 방향은 **단방향**:

```
ui ──► session ──► drawing
   │           └──► transport ──► drawing(.model)
   ├──► photo ──► drawing(.model)
   └──► works ──► drawing(.engine) (PngComposer가 CanvasState 읽음)
                  └──► ui.canvas.StrokeRenderer (화면과 동일 렌더 함수 재사용)
```

`drawing`은 누구에게도 의존하지 않습니다(가장 안쪽). `transport`/`photo`/`works`는 `drawing.model` 타입만 알고 UI/세션은 모름. 단 `works.PngComposer`는 화면용 stroke 렌더 함수를 재사용해 "보이는 것 = 저장되는 것"을 보장.

## 3. 상태 관리

- **단일 액티비티 + Compose** 구조 (`MainActivity` 하나).
- 화면 간 이동은 **Navigation Compose**.
- 화면별 상태는 **`ViewModel` + `StateFlow`** 패턴. Compose는 `collectAsStateWithLifecycle`로 구독.
- 캔버스 핵심 상태(`CanvasState`)는 `DrawingViewModel`이 보유. 입력 이벤트와 인바운드 이벤트가 동일한 `Channel<DrawingEvent>`로 들어와 순서대로 적용 → 동시성/순서 버그 회피.
- 사진(`BackgroundImage`)은 `CanvasState`의 또 다른 필드 — 로컬에서 선택했든 원격에서 받았든 같은 setter로 변경.
- DI는 도입하지 않음. 생성자 주입 + `viewModelFactory`로 충분.

## 4. 동시성

- 전송 I/O와 사진 디코딩은 `Dispatchers.IO` 코루틴.
- 캔버스 상태 변경은 **메인 스레드**(`Dispatchers.Main`)에서만. 원격 수신 이벤트는 `Channel`로 메인 적용 루프에 넘김.
- Compose 재구성은 `derivedStateOf`/스냅샷으로 최소화 — 획 추가마다 전체 리컴포지션 일어나면 60fps 못 지킴 ([drawing-engine.md](drawing-engine.md) 참고).

## 5. 모듈 분리는 언제

지금은 단일 모듈. 다음 중 하나가 성립하면 그때 분리:

- `transport`를 다른 앱이 재사용 (현재 없음)
- 빌드 시간이 30초를 넘기 시작
- 테스트에서 전송 의존성 격리가 어려워질 때

## 6. 테스트 전략

- `drawing`: 순수 Kotlin 단위 테스트. 이벤트 시퀀스 → 캔버스 상태 검증.
- `transport.codec`: 라운드트립 테스트(`encode(decode(x)) == x`).
- `transport.nearby`: 실기기 손 검증 — Nearby Connections는 에뮬레이터 페어링 미지원. CI에선 `Transport` 인터페이스 페이크.
- `photo`: 작은 비트맵으로 로딩/리사이즈 단위 테스트.
- UI: Compose 테스트로 도구바·색 선택·사진 버튼 등 인터랙션. 캔버스 픽셀 검증은 스크린샷 테스트, 초기엔 미도입.

## 관련 문서

- [ui-layout.md](ui-layout.md) — 화면별 목업, TopAppBar/도구바 배치, peer indicator
- [nearby-connections.md](nearby-connections.md) — 전송 계층
- [drawing-engine.md](drawing-engine.md) — 캔버스/사진/렌더링
- [protocol.md](protocol.md) — 이벤트·사진 직렬화 포맷
- [roadmap.md](roadmap.md) — 단계별 진행 계획
