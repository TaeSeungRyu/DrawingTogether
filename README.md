# DrawingTogether

블루투스로 연결된 두 안드로이드 기기 사이에서 **실시간으로 그림을 함께 그리는** 앱. 혼자서도 캔버스로 사용할 수 있습니다.

> 현재 상태: Compose 스캐폴드 단계. 캔버스/블루투스/프로토콜은 [`doc/`](doc/)에 설계되어 있고, 구현은 [`doc/roadmap.md`](doc/roadmap.md)의 단계에 따라 진행합니다.

## 컨셉

- **솔로 모드** — 혼자 자유롭게 그림. 네트워크/권한 요구 없음.
- **1:1 협업 모드** — 블루투스 페어링된 두 기기가 실시간으로 획·색·지우개 동작을 주고받음.
- 두 모드는 같은 캔버스 엔진을 공유합니다. 차이는 "이벤트가 로컬에서 오는가, 원격에서도 오는가"뿐.

## 기능 (목표)

| | 솔로 | 1:1 협업 |
|---|---|---|
| 펜/지우개, 색·굵기 변경 | ✓ | ✓ |
| 자기 획 되돌리기 | ✓ | ✓ |
| 전체 지우기 | ✓ | ✓ (양쪽 동기화) |
| 늦참가/재연결 시 캔버스 동기화 | — | ✓ |
| PNG 내보내기 | 추후 | 추후 |

## 기술 스택

- **Kotlin 1.9** + **Jetpack Compose** (Material 3, BOM `2024.04.01`)
- **단일 액티비티**, Navigation Compose
- **Bluetooth Classic / RFCOMM(SPP)** — 1:1 양방향 스트림
- **kotlinx.serialization (CBOR)** — 와이어 포맷
- **Coroutines + Flow** — I/O 및 상태 전파
- **minSdk 26 / targetSdk 34**

선택 근거는 [`doc/architecture.md`](doc/architecture.md), [`doc/bluetooth.md`](doc/bluetooth.md), [`doc/protocol.md`](doc/protocol.md) 참고.

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

블루투스 기능 검증은 **에뮬레이터로 불가** — 실제 안드로이드 기기 2대가 필요합니다.

## 프로젝트 구조 (목표)

```
app/src/main/java/com/rts/rys/ryy/drawingtogether
├── ui/         Compose 화면 (home / pairing / canvas)
├── drawing/    캔버스 엔진 — UI·네트워크 무관
├── bluetooth/  RFCOMM 전송 + CBOR 직렬화
├── session/    연결/모드 상태 머신
└── MainActivity.kt
```

레이어 의존 방향과 분리 원칙은 [`doc/architecture.md`](doc/architecture.md).

## 문서

| 문서 | 내용 |
|---|---|
| [`doc/architecture.md`](doc/architecture.md) | 레이어 구조, 상태 관리, 동시성, 테스트 전략 |
| [`doc/bluetooth.md`](doc/bluetooth.md) | Classic vs BLE 선택, 권한, 페어링/소켓 I/O |
| [`doc/drawing-engine.md`](doc/drawing-engine.md) | 데이터 모델, Compose Canvas 렌더링, 입력 처리 |
| [`doc/protocol.md`](doc/protocol.md) | 와이어 포맷, 핸드셰이크, 늦참가 동기화 |
| [`doc/roadmap.md`](doc/roadmap.md) | 단계별 구현 계획과 보류 항목 |
| [`CLAUDE.md`](CLAUDE.md) | Claude Code 작업 시 참고용 명령/스택 요약 |

## 권한 (런타임)

협업 모드 진입 시점에만 요청. 솔로 모드는 권한 요구 없음.

- API 31+: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, (호스트일 때) `BLUETOOTH_ADVERTISE`
- API ≤30: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` (스캔용)

자세한 매니페스트 스니펫은 [`doc/bluetooth.md`](doc/bluetooth.md) §2.
