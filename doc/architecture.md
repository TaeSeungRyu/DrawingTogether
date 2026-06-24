# 아키텍처

## 1. 큰 그림

`DrawingTogether`는 세 모드를 같은 UI로 지원합니다.

- **싱글 모드**: 한 기기에서 혼자 그림. 네트워크 비활성. 사진 배경(선택/촬영)도 지원.
- **함께 모드 (1:1)**: Nearby Connections (`P2P_POINT_TO_POINT`) 로 연결된 두 기기 사이 드로잉 이벤트 실시간 동기화. 한쪽이 사진을 보내면 양쪽 모두 같은 사진 위에 그림. "함께 그리기" 단일 모드 — 자기·상대 stroke 모두 편집·삭제 가능, 캔버스 공유.
- **모임 모드 (1:N)**: `P2P_STAR` 호스트 1 + 조인자 최대 3. 각 기기는 자기 캔버스를 크게, 다른 참가자는 미니 read-only 뷰로 봄. "동기화" 로 누구 한 명의 캔버스(stroke + 사진까지)를 가져옴. 자기 사진은 다른 사람이 보는 *내 미니 뷰*에 표시(상대 메인엔 영향 없음). 호스트가 조인자간 EVENT/사진/멤버십을 relay.

핵심 결정: **모드의 차이는 주로 "이벤트가 어디서 오는가, 어디로 가는가" 와 "캔버스가 몇 개인가"**. 로컬 입력이든 원격 인바운드든 동일한 `DrawingEvent` 스트림으로 정규화 — `DrawingViewModel.CanvasRouting` 이 `Shared`(싱글·함께, 단일 `CanvasState`) / `PerPeer`(모임, 본인 + 피어별 `peerCanvases: Map<PeerId, CanvasState>`) 분기. 사진도 동일하게 `BackgroundImage` 상태로 — 함께 모드는 양쪽 메인에, 모임 모드는 발신자의 미니 뷰 캔버스에 적용.

```
[Touch input]   ──┐
                  │
[Local photo]   ──┼──► CanvasState ──► Compose Canvas (사진 위 stroke 오버레이)
                  │
[Inbound event] ──┤
[Inbound photo] ──┘                          │
                                             └──► outbound (함께 모드일 때만)
```

## 2. 레이어 구성

단일 모듈(`:app`) 안에서 패키지로 레이어 분리.

```
com.rts.rys.ryy.drawingtogether
├── ui/                  Compose 화면 + 테마
│   ├── AppNavGraph.kt   NavHost + DrawMode{Single,Duo,Party} — splash → home → (draw/{mode} | pairing | party-pairing | preview/{workId})
│   ├── splash/          앱 진입 시 잠깐 보이는 스플래시
│   ├── home/            모드 선택 (싱글/함께/모임) + 최근 작업 모달 + 닉네임 설정
│   ├── pairing/         PairingScreen(1:1) + PartyPairingScreen(1:N)
│   ├── canvas/          드로잉 화면 (DrawingScreen/VM, DrawingCanvas, MiniCanvas, Toolbar, StrokeRenderer, 색 팔레트/피커)
│   ├── preview/         저장된 작품 풀사이즈 보기 + 갤러리 저장/공유
│   └── theme/           Candy Pop 팔레트 + 테마
├── drawing/             드로잉 도메인 — UI/네트워크 무관
│   ├── model/           Stroke, Point, ToolSettings, DrawingEvent, BackgroundImage, PeerId/StrokeId
│   └── engine/          CanvasState — apply(event) 리듀서 + 되돌리기 스택 + 배경 슬롯
├── photo/               사진 입력
│   ├── PhotoLoader      Uri → BackgroundImage (다운샘플 + EXIF 회전)
│   └── CameraCaptureFile  FileProvider 기반 촬영 임시 URI (TakePicture)
├── works/               저장된 작품 영속성
│   ├── Work             메타데이터 (id, 저장 시각, 크기, 사진 유무, 이름)
│   ├── WorkStore        filesDir/works/ PNG+.meta, StateFlow 인덱스, 갤러리 export, 100개 한도
│   └── PngComposer      CanvasState → ImageBitmap → PNG (StrokeRenderer 재사용)
├── transport/           Nearby Connections 연결 + 직렬화
│   ├── nearby/          NearbyTransport + TransportMode{Duo,Party} — 광고/검색/연결/relay
│   ├── codec/           FrameCodec — Frame ↔ CBOR 바이트
│   ├── Transport.kt     인터페이스 + ConnectedPeer/InboundFrame/TransportState
│   ├── Frame.kt         와이어 메시지 sealed class
│   └── OutboundCoalescer.kt  StrokeAppend 25ms 코얼레싱
├── session/             SessionManager — 다중 피어 핸드셰이크, 호스트 relay, 동기화 라우팅
└── MainActivity.kt
```

> 참고: StrokeRenderer 는 `ui/canvas/`(화면+PNG 공유), PngComposer 는 `works/` 에 있음. `drawing/render/` 패키지는 없음.

네비게이션 흐름:

```
splash ──auto──► home ──싱글──► draw/Single   (DrawingScreen, 네트워크 미사용)
                  ├──함께──► pairing/{autoHost} ──연결──► draw/Duo
                  ├──모임──► party-pairing ──그리기시작──► draw/Party
                  └──썸네일 탭──► preview/{workId}
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
- 캔버스 핵심 상태(`CanvasState`)는 `DrawingViewModel`이 보유. `CanvasState` 내부는 `mutableStateListOf`/`mutableStateMapOf`/`mutableStateOf` 라 Compose 가 직접 추적 — 별도 `StateFlow` 불필요. 모든 변경은 `CanvasState.apply(DrawingEvent)` 한 곳을 통과 (로컬 입력·원격 인바운드 동일 경로) → 순서/동시성 버그 회피.
- 사진(`BackgroundImage`)은 `CanvasState`의 또 다른 필드 — 로컬에서 선택했든 원격에서 받았든 같은 setter로 변경.
- 세션은 `SessionManager` 싱글톤이 모드별(`Duo`/`Party`)로 분리 보유. prefs(peerId/nick)만 공유.
- DI는 도입하지 않음. 생성자 주입 + `viewModel()` / `Context` 키 싱글톤으로 충분.

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
