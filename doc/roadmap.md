# 진행 로드맵

진행 원칙: **연결을 만들기 전에 그림이 먼저 되어야 한다.** 솔로 모드 캔버스를 끝내고, 그 위에 BT 전송을 얹는 순서.

## Phase 0 — 토대 (완료)
- [x] Compose 스캐폴드, 테마, MainActivity
- [x] `gradle/libs.versions.toml`에 `navigation-compose`, `kotlinx-serialization-cbor`, `kotlinx-coroutines-android`, `kotlin-serialization` 플러그인 추가
- [x] AndroidX 라이브러리들을 `compileSdk 34` / AGP 8.6.1과 호환되는 버전으로 다운그레이드 (`core-ktx 1.13.1`, `activity-compose 1.9.3`, `lifecycle-runtime-ktx 2.8.7`, `androidx.test.ext:junit 1.2.1`, `espresso-core 3.6.1`)
- [x] 패키지 구조 생성: `ui/`, `drawing/`, `bluetooth/`, `session/` — 각 패키지에 `package.kt` 마커 파일 (자세한 건 [architecture.md](architecture.md))
- [x] `gradlew assembleDebug` 통과 확인

## Phase 1 — 솔로 드로잉 MVP (완료)
목표: BT 없이 혼자 그릴 수 있는 완성된 캔버스.
- [x] `DrawingEvent`, `Stroke`, `CanvasState` 정의 ([drawing-engine.md](drawing-engine.md))
- [x] `pointerInput` 기반 입력 수집 → 이벤트 스트림 (포인터 이벤트당 점 묶음으로 `StrokeAppend` 코얼레싱)
- [x] Compose Canvas 렌더링 — 완료된 획 + 진행 중 획을 매 프레임 다시 그림. 비트맵 캐시는 Phase 4로 보류 (RenderNode 캐싱만으로 Phase 1 트래픽엔 충분, 실측 후 도입)
- [x] 도구바: 펜/지우개, 색 팔레트(6색), 굵기 슬라이더(1–32dp)
- [x] 자기 획 되돌리기 (로컬 작성자 한정), 전체 지우기
- [x] `CanvasStateTest` 단위 테스트 10개 — start/append/end/undo/clear/원격 작성자/no-op 시나리오 커버
- [x] `assembleDebug` + `testDebugUnitTest` 통과

**완료 기준**: 한 기기에서 안정적으로 그릴 수 있다. 캔버스가 60fps 근처로 동작.

> 실기기 동작 검증은 보류 — APK 빌드는 통과했지만 60fps 여부는 기기에서 확인 필요. Phase 2 진입 전에 한 번 실행해 보는 것을 권장.

## Phase 2 — 블루투스 페어링 & 연결
목표: 두 기기가 RFCOMM 소켓으로 연결되어 "HELLO/HELLO_ACK" 핸드셰이크까지.
- [ ] 권한 요청 흐름 (API 31± 분기, [bluetooth.md](bluetooth.md))
- [ ] `pairing` 화면: 페어된 디바이스 + 검색 결과 리스트
- [ ] `BluetoothTransport` (소켓 → 코루틴 read/write 루프 + 프레이밍)
- [ ] `Frame` 직렬화 (CBOR, [protocol.md](protocol.md))
- [ ] `session` 상태 머신: Idle → Discovering → Connecting → Connected
- [ ] 끊김 감지 + 사용자 알림

**완료 기준**: 두 기기에서 앱 켜고 한쪽이 호스트, 다른 쪽이 조인 → "연결됨" 상태 표시. 그림은 아직 동기화 안 됨.

## Phase 3 — 이벤트 동기화
목표: 1:1 실시간 그림 공유.
- [ ] 로컬 입력 → `EVENT` 프레임 전송
- [ ] 인바운드 `EVENT` → 같은 `apply()` 루프
- [ ] 20–30ms 코얼레싱(`StrokeAppend` 점들 묶기)
- [ ] 원격 작성자의 획은 시각적으로 구분(테두리 색 살짝, 또는 작성자 닉네임 라벨)
- [ ] PING/PONG, BYE
- [ ] 늦참가/재연결 시 `SNAPSHOT_REQ` → `SNAPSHOT`

**완료 기준**: 두 사람이 동시에 그려도 양쪽 캔버스가 일치한다. 한쪽이 떠나거나 재연결해도 상태가 보존된다.

## Phase 4 — 다듬기
- [ ] PNG 내보내기 (스토리지 권한 흐름 포함)
- [ ] 세션 기록(JSONL) → 리플레이 화면
- [ ] 색 팔레트 커스터마이즈
- [ ] 닉네임 설정 화면

## Phase 5 — 나중에 검토만
지금 결정하지 않을 것들. 적어두기만:
- BLE로 전환(다중 피어, 백그라운드 광고가 필요해질 때)
- 다중 피어 메시(3+명 동시 드로잉)
- 클라우드 동기화/계정
- 벡터 export (SVG)

## 위험과 결정 보류 항목

- **두 기기 화면 종횡비 차이**: 정사각 캔버스 고정으로 단순화(Phase 1에서 결정 고정). 자유 종횡비는 Phase 4+.
- **제조사 펌웨어별 RFCOMM 이슈**: 발견되면 reflection fallback을 그때 도입. 선제적으로 넣지 않음 ([bluetooth.md](bluetooth.md) §6 참고).
- **자동 재연결**: 의도적으로 도입 안 함. 끊기면 사용자가 명시적으로 다시 연결.

## 빌드/실행 명령

`CLAUDE.md` 참고.
