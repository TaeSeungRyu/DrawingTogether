# 진행 로드맵

진행 원칙: **연결을 만들기 전에 그림이 먼저 되어야 한다.** 싱글 모드를 완성한 뒤 사진 입력을 더하고, 그 위에 Nearby Connections 전송을 얹는 순서.

## Phase 0 — 토대 (완료)
- [x] Compose 스캐폴드, 테마, MainActivity
- [x] `gradle/libs.versions.toml`에 `navigation-compose`, `kotlinx-serialization-cbor`, `kotlinx-coroutines-android`, `kotlin-serialization` 플러그인 추가
- [x] AndroidX 라이브러리를 `compileSdk 34` / AGP 8.6.1 호환 버전으로 정렬 (`core-ktx 1.13.1`, `activity-compose 1.9.3`, `lifecycle-runtime-ktx 2.8.7`, `androidx.test.ext:junit 1.2.1`, `espresso-core 3.6.1`)
- [x] 패키지 구조 생성: `ui/`, `drawing/`, `bluetooth/`, `session/` (자세한 건 [architecture.md](architecture.md))
- [x] `gradlew assembleDebug` 통과 확인

## Phase 1 — 싱글 드로잉 MVP (완료)
목표: 네트워크 없이 혼자 그릴 수 있는 완성된 캔버스.
- [x] `DrawingEvent`, `Stroke`, `CanvasState` 정의 ([drawing-engine.md](drawing-engine.md))
- [x] `pointerInput` 기반 입력 수집 → 이벤트 스트림 (포인터 이벤트당 점 묶음 코얼레싱)
- [x] Compose Canvas 렌더링 — 완료된 획 + 진행 중 획. 비트맵 캐시는 Phase 4로 보류
- [x] 도구바: 펜/지우개, 색 팔레트(12색 + HSV 피커), 굵기 슬라이더(1–32dp), 자기 획 되돌리기, 전체 지우기
- [x] 브러시 6종: 펜/연필/잉크펜/마커/형광펜/크레용 (capStyle + alpha + widthScale 프리셋, 카드형 선택 UI)
- [x] 도형 모드 8종: 자유/동그라미/사각형/삼각형/오각형/육각형/별/하트 (드래그 바운딩 박스 외곽선)
- [x] 지우개 토글 동작 (재선택 시 펜으로 복귀)
- [x] 스플래시 + 홈(싱글/멀티 선택) + 페어링 자리표시 + 네비게이션
- [x] `CanvasStateTest` 단위 테스트 10개 + `assembleDebug` 통과

## Phase 1.5 — 사진 배경 (완료)
목표: 갤러리/카메라에서 가져온 사진 위에 그림을 그릴 수 있다.
- [x] `drawing/model/BackgroundImage` 모델 (bitmap + dimensions + Source enum)
- [x] `CanvasState.background` 필드 + `setBackground` setter
- [x] `photo/PhotoLoader.kt`: Uri → BackgroundImage (down-sample + EXIF rotation, `androidx.exifinterface`)
- [x] `photo/CameraCaptureFile.kt`: FileProvider 기반 임시 URI 생성
- [x] AndroidManifest FileProvider + `res/xml/file_paths.xml`
- [x] `DrawingScreen`에 `TopAppBar` 도입 — 뒤로 / 사진 / 촬영 / 제거 액션
- [x] PhotoPicker (`ActivityResultContracts.PickVisualMedia`) — 권한 불필요
- [x] 카메라 촬영 (`ActivityResultContracts.TakePicture`) — 매니페스트에 CAMERA 미선언으로 런타임 권한 불필요
- [x] 캔버스 렌더링 — 사진 → stroke 순서, letterbox Box로 사진 비율 보존
- [x] `PhotoLoaderTest` 8개 (sample size 계산 — 권한/EXIF 검증은 실기기에서)
- [x] `assembleDebug` + `testDebugUnitTest` 통과

**완료 기준**: 싱글 모드에서 사진을 띄우고 그 위에 그릴 수 있다. 회전된 사진도 올바른 방향으로 표시. (실기기 검증은 사용자가 직접 진행.)

## Phase 2 — Nearby Connections 페어링 & 연결
목표: 두 기기가 Nearby로 연결되어 "HELLO/HELLO_ACK" 핸드셰이크까지.
- [ ] `play-services-nearby` 의존성 추가
- [ ] 권한 요청 흐름 (API 33+ `NEARBY_WIFI_DEVICES`, API 31-32 BT 권한, API ≤30 위치 + BT, [nearby-connections.md](nearby-connections.md) §3)
- [ ] `pairing` 화면: 호스트는 `startAdvertising`, 조인은 `startDiscovery` + 발견된 디바이스 리스트
- [ ] `Transport` 인터페이스 + `NearbyTransport` 구현 (`P2P_POINT_TO_POINT` strategy)
- [ ] `Frame` 직렬화 (CBOR, [protocol.md](protocol.md))
- [ ] `session` 상태 머신: Idle → Discovering → Connecting → Connected
- [ ] 끊김 감지 + 사용자 알림

**완료 기준**: 두 기기에서 앱 켜고 한쪽 호스트, 한쪽 조인 → "연결됨" 표시. 그림은 아직 동기화 안 됨.

## Phase 3 — 이벤트 + 사진 동기화
목표: 1:1 실시간 그림 공유 + 같은 사진 위 작업.
- [ ] 로컬 입력 → `EVENT` 프레임 전송 (`Payload.Type.BYTES`)
- [ ] 인바운드 `EVENT` → 같은 `apply()` 루프
- [ ] 20–30ms 코얼레싱 (`StrokeAppend` 점 묶기)
- [ ] 원격 작성자 획은 시각적으로 구분 (예: 닉네임 라벨 또는 미세한 색 변조)
- [ ] PING/PONG, BYE
- [ ] 늦참가/재연결 시 `SNAPSHOT_REQ` → `SNAPSHOT`
- [ ] **사진 전송**: `PHOTO_META` + `Payload.Type.FILE` → 수신 측 `BackgroundImage` 설정
- [ ] 사진 수신 진행률 UI

**완료 기준**: 두 사람이 동시에 그려도 양쪽 캔버스가 일치. 한쪽이 사진을 보내면 양쪽 모두 같은 배경 위에 그림. 끊김/재연결 시 상태 복원.

## Phase 4 — 완성품 추출 & 다듬기
- [ ] PNG 내보내기 — 사진 배경 + 완료된 stroke 합성, `MediaStore.Images`로 저장
- [ ] 사용자 갤러리 진입 (Share intent로 다른 앱 전달)
- [ ] 완료된 획 비트맵 캐시 (실측 후 perf 이슈 있을 때)
- [ ] 닉네임 설정 화면
- [ ] 색 팔레트 커스터마이즈

## Phase 5 — 나중에 검토만
지금 결정하지 않을 것들:
- 다중 피어 메시 (3+명 동시 드로잉) — `P2P_CLUSTER` strategy로 전환
- 세션 기록(JSONL) → 리플레이 화면
- 클라우드 동기화/계정
- 벡터 export (SVG)
- 실시간 영상 스트리밍 (영상 전송이 필요해지면 WebRTC 또는 MediaCodec 파이프라인 도입 검토 — 사진 한 장으로 충분하면 미도입)

## 위험과 결정 보류 항목

- **사진 종횡비 처리**: 사진이 있으면 캔버스 비율을 사진에 맞춤. 사진 없는 자유 모드는 정사각 고정.
- **사진 크기와 메모리**: 대용량 사진(20MP+)은 디코딩 시 다운샘플링 필수. `PhotoLoader`에서 화면 해상도에 맞춰 inSampleSize 조정.
- **Nearby Connections 백그라운드 동작**: Android 13+ 백그라운드 BT/Wi-Fi 스캔 제한. 화면 켜진 동안만 동작 가정.
- **자동 재연결**: 의도적으로 미도입. 끊기면 사용자가 명시적으로 다시 연결.
- **Play Services 의존**: 한국 시장 가정 — 화웨이/de-Google ROM은 지원 범위 밖.

## 빌드/실행 명령

`CLAUDE.md` 참고.
