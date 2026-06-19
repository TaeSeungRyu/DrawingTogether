# DrawingTogether

같은 사진을 보며 **두 사람이 함께 그림을 그리는** 안드로이드 앱. 혼자서도 캔버스 / 사진 위 낙서 도구로 사용 가능.

> 현재 상태: 싱글 모드(Phase 1) 완료. 다음은 사진 배경(Phase 1.5) → Nearby Connections 연결(Phase 2) → 실시간 동기화(Phase 3) 순서. 자세한 단계는 [`doc/roadmap.md`](doc/roadmap.md).

## 컨셉

- **싱글 모드** — 혼자 자유 드로잉. 갤러리에서 사진을 불러오거나 카메라로 찍어 그 위에 그릴 수도 있음. 네트워크/권한 요구 없음.
- **멀티 모드 (1:1)** — Nearby Connections로 직접 연결된 두 기기가 실시간 동기화. 한쪽이 사진을 보내면 양쪽 모두 같은 사진 위에 함께 그림.
- 두 모드는 같은 캔버스 엔진을 공유. 차이는 "이벤트가 로컬에서만 오는가, 원격에서도 오는가"뿐.

## 기능 (목표)

| | 싱글 | 멀티(1:1) |
|---|---|---|
| 펜·연필·잉크펜·마커·형광펜·크레용 (6종) | ✓ | ✓ |
| 도형 모드 (동그라미·사각형·삼각형·오각형·육각형·별·하트) | ✓ | ✓ |
| 색 팔레트 + HSV 사용자 정의 | ✓ | ✓ |
| 자기 획 되돌리기, 전체 지우기 | ✓ | ✓ (양쪽 동기화) |
| **사진 배경** (갤러리 선택 / 카메라 촬영) | ✓ | ✓ (한쪽 보내면 양쪽 동기화) |
| 늦참가/재연결 시 캔버스+사진 동기화 | — | ✓ |
| PNG 내보내기 (사진 + 그림 합성) | 추후 | 추후 |

## 기술 스택

- **Kotlin 1.9** + **Jetpack Compose** (Material 3, BOM `2024.04.01`)
- **단일 액티비티** + Navigation Compose
- **Google Nearby Connections** (`play-services-nearby`) — 1:1 P2P, BT/Wi-Fi 자동 선택, 메시지/파일 페이로드 자동 chunking
- **kotlinx.serialization (CBOR)** — 메시지 와이어 포맷
- **CameraX** + **Android PhotoPicker** — 사진 입력
- **Coroutines + Flow** — I/O 및 상태 전파
- **minSdk 26 / targetSdk 34**

선택 근거는 [`doc/architecture.md`](doc/architecture.md), [`doc/nearby-connections.md`](doc/nearby-connections.md), [`doc/protocol.md`](doc/protocol.md) 참고.

## 빌드 & 실행

Windows:

```powershell
.\gradlew.bat assembleDebug          # APK 빌드
.\gradlew.bat installDebug           # 연결된 기기/에뮬레이터에 설치
.\gradlew.bat test                   # JVM 단위 테스트
.\gradlew.bat connectedAndroidTest   # 기기 필요
.\gradlew.bat lint
```

macOS/Linux는 `./gradlew` 사용. 자세한 명령은 [`CLAUDE.md`](CLAUDE.md).

멀티 모드 검증은 **에뮬레이터로 불가** — 실제 안드로이드 기기 2대 필요 (둘 다 Google Play Services 탑재).

## 프로젝트 구조 (목표)

```
app/src/main/java/com/rts/rys/ryy/drawingtogether
├── ui/         Compose 화면 (splash / home / pairing / canvas)
├── drawing/    캔버스 엔진 — UI·네트워크 무관
├── photo/      사진 입력 (Phase 1.5: PhotoPicker, CameraX)
├── transport/  Nearby Connections + CBOR 직렬화 (Phase 2+)
├── session/    연결/모드 상태 머신
└── MainActivity.kt
```

레이어 의존 방향과 분리 원칙은 [`doc/architecture.md`](doc/architecture.md).

## 문서

| 문서 | 내용 |
|---|---|
| [`doc/architecture.md`](doc/architecture.md) | 레이어 구조, 상태 관리, 동시성, 테스트 전략 |
| [`doc/ui-layout.md`](doc/ui-layout.md) | 화면별 ASCII 목업, TopAppBar/도구바 배치, peer indicator, 디자인 결정 |
| [`doc/nearby-connections.md`](doc/nearby-connections.md) | Nearby Connections 선택 근거, 권한, 연결 흐름, payload 타입 |
| [`doc/drawing-engine.md`](doc/drawing-engine.md) | 데이터 모델, Compose Canvas 렌더링, 입력 처리, 사진 배경, PNG export |
| [`doc/protocol.md`](doc/protocol.md) | 와이어 메시지(CBOR), 핸드셰이크, 사진 전송, 늦참가 동기화 |
| [`doc/roadmap.md`](doc/roadmap.md) | 단계별 구현 계획과 보류 항목 |
| [`CLAUDE.md`](CLAUDE.md) | Claude Code 작업 시 참고용 명령/스택 요약 |

## 권한 (런타임)

멀티 모드 진입 시점에만 요청. 싱글 모드는 카메라 촬영 시점에만 카메라 권한.

- API 33+: `NEARBY_WIFI_DEVICES`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`
- API 31-32: 위와 동일하되 `NEARBY_WIFI_DEVICES` 제외
- API ≤30: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION`
- 사진 촬영(싱글/멀티 공통): `CAMERA` — 처음 촬영 버튼 누를 때
- 갤러리 선택: 권한 불필요 (Android PhotoPicker)

자세한 매니페스트 스니펫은 [`doc/nearby-connections.md`](doc/nearby-connections.md) §3.
