# 아키텍처

## 1. 큰 그림

`DrawingTogether`는 두 가지 모드를 같은 UI로 지원합니다.

- **솔로 모드**: 한 기기에서 혼자 그림. 네트워킹 비활성.
- **1:1 협업 모드**: 블루투스로 연결된 두 기기 사이에서 드로잉 이벤트(획, 색 변경, 지우개 등)를 실시간으로 주고받음.

핵심 결정: **두 모드의 차이는 "이벤트가 어디서 오는가"뿐**. 로컬 입력이든 블루투스로 받은 원격 이벤트든, 같은 `DrawingEvent` 스트림으로 정규화해서 캔버스에 적용합니다. 이렇게 하면 드로잉 엔진은 네트워크를 모른 채 동작하고, 테스트도 쉬워집니다.

```
[Touch input] ──┐
                ├──► DrawingEvent stream ──► CanvasState ──► Compose Canvas
[BT inbound]  ──┘                  │
                                   └──► BT outbound (협업 모드일 때만)
```

## 2. 레이어 구성

단일 모듈(`:app`) 안에서 패키지로만 레이어를 나눕니다. 멀티모듈은 아직 과합니다.

```
com.rts.rys.ryy.drawingtogether
├── ui/                  Compose 화면 + 테마
│   ├── AppNavGraph.kt   NavHost — splash → home → (draw | pairing)
│   ├── splash/          앱 진입 시 잠깐 보이는 스플래시
│   ├── home/            모드 선택 (싱글 / 멀티)
│   ├── pairing/         BT 디바이스 검색·페어링 화면 (Phase 2부터 구현)
│   ├── canvas/          드로잉 화면 (붓·색·지우개 도구)
│   └── theme/           (기존)
├── drawing/             드로잉 도메인 — UI/네트워크 무관
│   ├── model/           Stroke, Point, ToolSettings, DrawingEvent
│   ├── engine/          CanvasState, 이벤트 적용기, 되돌리기 스택
│   └── render/          Compose Canvas 어댑터
├── bluetooth/           BT 연결 + 직렬화
│   ├── transport/       BluetoothSocket I/O (server/client)
│   ├── discovery/       디바이스 스캔, 페어링 상태
│   └── codec/           DrawingEvent ↔ 바이트 인코딩
├── session/             세션 상태 머신 (Idle → Pairing → Connected → Drawing)
└── MainActivity.kt
```

네비게이션 흐름:

```
splash ──auto──► home ──싱글모드──► draw   (DrawingScreen)
                  └────멀티모드──► pairing (Phase 2)
```

`splash`에서 `home`으로 진입할 때는 `popUpTo(Splash) { inclusive = true }`로 백스택에서 제거 — 뒤로 가기로 스플래시에 돌아가지 않음.

레이어 의존 방향은 **단방향**:

```
ui ──► session ──► drawing
              └──► bluetooth ──► drawing(.model)
```

`drawing`은 누구도 의존하지 않습니다(가장 안쪽). `bluetooth`는 `drawing.model`의 이벤트 타입만 알고, UI/세션은 모릅니다.

## 3. 상태 관리

- **단일 액티비티 + Compose** 구조 유지 (`MainActivity` 하나).
- 화면 간 이동은 **Navigation Compose** 사용. (`androidx.navigation:navigation-compose` 추가 필요 — 카탈로그에 등록.)
- 화면별 상태는 **`ViewModel` + `StateFlow`** 패턴. Compose는 `collectAsStateWithLifecycle`로 구독.
- 드로잉 캔버스의 핵심 상태(`CanvasState`)는 `DrawingViewModel`이 보유. 입력 이벤트와 BT 인바운드 이벤트가 동일한 `Channel<DrawingEvent>`로 들어와 순서대로 적용됨 → 동시성/순서 버그 회피.
- DI는 도입하지 않음. 생성자 주입 + `viewModelFactory`로 충분. Hilt는 모듈이 늘어나면 그때 고민.

## 4. 동시성

- BT I/O는 `Dispatchers.IO` 코루틴. 읽기/쓰기 각각 별도 코루틴, 종료는 `Job` 취소로.
- 캔버스 상태 변경은 **메인 스레드**(`Dispatchers.Main`)에서만. BT에서 받은 이벤트는 `Channel`로 메인 스레드 적용 루프에 넘김.
- Compose 재구성은 `derivedStateOf`/스냅샷으로 최소화 — 획 추가마다 전체 리컴포지션 일어나면 60fps 못 지킴 (자세한 건 [drawing-engine.md](drawing-engine.md) 참고).

## 5. 모듈 분리는 언제

지금은 단일 모듈. 다음 중 하나가 성립하면 그때 분리:

- `bluetooth`를 다른 앱이 재사용 (현재로선 없음)
- 빌드 시간이 30초를 넘기 시작
- 테스트에서 BT 의존성 격리가 어려워질 때

## 6. 테스트 전략

- `drawing`: 순수 Kotlin 단위 테스트. 이벤트 시퀀스 → 캔버스 상태 검증.
- `bluetooth.codec`: 라운드트립 테스트(`encode(decode(x)) == x`).
- `bluetooth.transport`: 페어 디바이스가 필요하므로 수동 검증. CI에선 모의 `BluetoothSocket` 인터페이스로 대체.
- UI: Compose 테스트로 도구바·색 선택 등 인터랙션만. 캔버스 픽셀 검증은 스크린샷 테스트가 필요한데, 초기엔 도입하지 않음.

## 관련 문서

- [bluetooth.md](bluetooth.md) — 전송 계층
- [drawing-engine.md](drawing-engine.md) — 캔버스/렌더링
- [protocol.md](protocol.md) — 이벤트 직렬화 포맷
- [roadmap.md](roadmap.md) — 단계별 진행 계획
