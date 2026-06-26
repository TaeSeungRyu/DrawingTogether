# DrawingTogether (드로잉 투게더)

같은 캔버스/사진 위에서 **여럿이 함께 그림을 그리는** 안드로이드 앱. 혼자서도 사진 위 낙서 도구로 사용 가능.

> 현재 상태: 싱글 / 함께(1:1) / 모임(1:N) 세 모드 동작. 작품 저장·갤러리 내보내기·공유까지 구현. 단계별 상세는 [`doc/roadmap.md`](doc/roadmap.md).

## 세 가지 모드

- **싱글 모드** — 혼자 자유 드로잉. 갤러리에서 사진을 불러오거나 카메라로 찍어 그 위에 그릴 수 있음. 네트워크/권한 요구 없음.
- **함께 모드 (1:1)** — Nearby Connections(`P2P_POINT_TO_POINT`)로 직접 연결된 두 기기가 하나의 캔버스를 실시간 공유. 한쪽이 사진을 보내면 양쪽 모두 같은 사진 위에 함께 그림. Clear/Undo/지우개도 양쪽에 적용.
- **모임 모드 (1:N, 최대 4인)** — 호스트 1 + 조인자 최대 3 (`P2P_STAR`). 자기 캔버스는 크게, 다른 참가자는 미니 뷰(read-only)로 봄. 조인자끼리는 호스트가 이벤트를 relay. "동기화"로 누구 한 명의 캔버스(그림 + 사진)를 통째로 가져올 수 있음. 자기 사진은 다른 사람이 보는 *내 미니 뷰*에만 표시됨(상대 메인엔 영향 없음).

세 모드는 같은 캔버스 엔진을 공유. 차이는 "이벤트가 로컬에서만 오는가, 원격에서도 오는가 / 한 캔버스인가 여러 캔버스인가"뿐.

## 기능

| | 싱글 | 함께(1:1) | 모임(1:N) |
|---|---|---|---|
| 브러시 12종 (펜·연필·잉크펜·마커·형광펜·크레용·에어브러시·번짐·네온·점선·무지개·붓펜) | ✓ | ✓ | ✓ |
| 도형 8종 (동그라미·사각형·삼각형·오각형·육각형·별·하트·자유) | ✓ | ✓ | ✓ |
| 스티커 12종 (배치·이동·크기·회전·삭제) | ✓ | ✓ | ✓ |
| 색 팔레트 (7색 + 사용자 정의 슬롯 편집 + HSV 피커) + 스포이드 + 최근 색 | ✓ | ✓ | ✓ |
| 안내선(십자·격자) / 손떨림 보정 (끔·약·강) | ✓ | ✓ | ✓ |
| 되돌리기 / 전체 지우기 | ✓ | ✓ (공유 캔버스) | ✓ (내 캔버스) |
| 사진 배경 (갤러리 / 카메라) | ✓ | ✓ (양쪽 동기화) | ✓ (내 미니 뷰에 표시) |
| "동기화" — 상대 캔버스 가져오기 | — | ✓ (즉시) | ✓ (피어 선택 → 사진 포함) |
| 작품 저장 (앱 내부) + 최근 작업 갤러리 | ✓ | ✓ | ✓ |
| 외부 갤러리 내보내기 (MediaStore) + 공유 | ✓ | ✓ | ✓ |
| 닉네임 설정 (홈 화면) | ✓ | ✓ | ✓ |

## 기술 스택

- **Kotlin 1.9** + **Jetpack Compose** (Material 3, BOM `2024.04.01`, Compose Compiler 1.5.1)
- **단일 액티비티** + Navigation Compose (`splash → home → draw/{mode} | pairing | party-pairing | preview/{workId}`)
- **Google Nearby Connections** (`play-services-nearby`) — `P2P_POINT_TO_POINT`(함께) / `P2P_STAR`(모임), BT/Wi-Fi 자동 선택, BYTES/FILE 페이로드 자동 chunking
- **kotlinx.serialization (CBOR)** — 메시지 와이어 포맷
- **Android PhotoPicker** + **`ActivityResultContracts.TakePicture`** (시스템 카메라 앱) — 사진 입력. **CAMERA 권한 미선언** (시스템 카메라 앱이 처리하므로 런타임 권한 프롬프트 없음)
- **MediaStore.Images** — 외부 갤러리(`Pictures/DrawingTogether`) 내보내기. scoped storage 라 저장소 권한 불필요
- **Coroutines + Flow** — I/O 및 상태 전파
- **minSdk 31 / target·compileSdk 34**, AGP 8.6.1, `jvmTarget` 1.8

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

함께/모임 모드 검증은 **에뮬레이터로 불가** — 실제 안드로이드 기기 2대 이상 필요 (모두 Google Play Services 탑재).

## 프로젝트 구조

```
app/src/main/java/com/rts/rys/ryy/drawingtogether
├── ui/         Compose 화면 (splash / home / pairing / party-pairing / canvas / preview)
├── drawing/    캔버스 엔진 (model + engine) — UI·네트워크 무관
├── photo/      사진 입력 (PhotoLoader, CameraCaptureFile)
├── works/      작품 저장/갤러리/PNG 합성 (Work, WorkStore, PngComposer)
├── transport/  Nearby Connections + CBOR 직렬화 (Transport, NearbyTransport, Frame, codec)
├── session/    연결/모드 상태 머신 (SessionManager)
└── MainActivity.kt
```

레이어 의존 방향(`ui → session/photo/works → drawing.engine → drawing.model`, `transport → drawing.model`)은 [`doc/architecture.md`](doc/architecture.md).

## 문서

| 문서 | 내용 |
|---|---|
| [`doc/architecture.md`](doc/architecture.md) | 레이어 구조, 상태 관리, 동시성, 테스트 전략 |
| [`doc/ui-layout.md`](doc/ui-layout.md) | 화면별 ASCII 목업, TopAppBar/도구바 배치, 모임 모드 반응형 레이아웃, peer indicator |
| [`doc/nearby-connections.md`](doc/nearby-connections.md) | Nearby Connections 선택 근거, 권한, 연결 흐름, 다중 endpoint, payload 타입 |
| [`doc/drawing-engine.md`](doc/drawing-engine.md) | 데이터 모델, Compose Canvas 렌더링, 입력 처리, 사진 배경, PNG export |
| [`doc/protocol.md`](doc/protocol.md) | 와이어 메시지(CBOR), 핸드셰이크, 사진/스냅샷 전송, 호스트 relay, 다중 피어 |
| [`doc/roadmap.md`](doc/roadmap.md) | 단계별 구현 계획과 보류 항목 |
| [`CLAUDE.md`](CLAUDE.md) | Claude Code 작업 시 참고용 명령/스택 요약 |

## 권한 (런타임)

함께/모임 모드 진입 시점에만 요청. 싱글 모드는 권한 요구 없음.

- API 33+: `NEARBY_WIFI_DEVICES`(`neverForLocation`), `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`
- API 31-32: 위와 동일하되 `NEARBY_WIFI_DEVICES` 제외
- 카메라 촬영: **권한 없음** — 시스템 카메라 앱(`TakePicture`)이 처리
- 갤러리 선택: 권한 없음 (Android PhotoPicker)
- 외부 갤러리 내보내기: 권한 없음 (MediaStore scoped storage)

minSdk 31 이라 API ≤30 의 위치/구 BT 권한 경로는 해당 없음. 자세한 매니페스트는 [`doc/nearby-connections.md`](doc/nearby-connections.md) §3.
