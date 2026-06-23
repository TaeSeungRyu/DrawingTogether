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
- [x] Compose Canvas 렌더링 — 완료된 획 + 진행 중 획. 비트맵 캐시는 Phase 5로 보류
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

## Phase 1.6 — 작품 저장 & 갤러리 (완료)
목표: 그린 작품을 저장하고 홈 화면에서 다시 볼 수 있다.
- [x] `ui/canvas/StrokeRenderer.kt` — DrawScope 렌더 함수 추출 (화면 + PNG 합성 공유)
- [x] `works/Work.kt` — 메타데이터 (id/시각/크기/사진 유무), 파이프 구분 텍스트 시리얼라이즈
- [x] `works/WorkStore.kt` — `filesDir/works/`에 PNG + `.meta` 저장, `StateFlow<List<Work>>` 인덱스, 싱글톤
- [x] `works/PngComposer.kt` — `CanvasDrawScope` + `ImageBitmap`으로 합성, finished strokes만 (진행 중·커서 미저장)
- [x] `DrawingScreen` TopAppBar에 "저장" 버튼 (secondaryContainer 토널) + Toast
- [x] `ui/home/RecentWorksRow.kt` — LazyRow 썸네일 (sample-size 다운샘플 + `produceState`로 IO 디코딩), 빈 상태 안내
- [x] `HomeScreen`에 행 통합 — `WorkStore.works.collectAsState()`로 자동 갱신
- [x] `ui/preview/PreviewScreen.kt` — 풀사이즈 PNG 표시, 뒤로 가기
- [x] `AppNavGraph`에 `preview/{workId}` 라우트
- [x] `assembleDebug` + `testDebugUnitTest` 통과

**완료 기준**: 캔버스에서 "저장" → 홈으로 돌아오면 썸네일 등장 → 탭하면 풀사이즈 보임. 외부 저장소 권한 불필요(앱 내부 `filesDir` 사용).

> 추후: 삭제, 공유, 재편집(stroke 보존), 그리드 뷰

## Phase 2 — Nearby Connections 페어링 & 연결
목표: 두 기기가 Nearby로 연결되어 "HELLO/HELLO_ACK" 핸드셰이크까지.
- [x] `play-services-nearby` 의존성 추가
- [x] 권한 요청 흐름 (API 33+ `NEARBY_WIFI_DEVICES`, API 31-32 BT 권한, API ≤30 위치 + BT, [nearby-connections.md](nearby-connections.md) §3)
- [x] `pairing` 화면: 호스트는 `startAdvertising`, 조인은 `startDiscovery` + 발견된 디바이스 리스트
- [x] `Transport` 인터페이스 + `NearbyTransport` 구현 (`P2P_POINT_TO_POINT` strategy)
- [x] `Frame` 직렬화 (CBOR, [protocol.md](protocol.md))
- [x] `session` 상태 머신: Idle → Discovering → Connecting → Connected
- [x] 끊김 감지 + 사용자 알림

**완료 기준**: 두 기기에서 앱 켜고 한쪽 호스트, 한쪽 조인 → "연결됨" 표시. 그림은 아직 동기화 안 됨.

## Phase 3 — 이벤트 + 사진 동기화
목표: 1:1 실시간 그림 공유 + 같은 사진 위 작업.
- [x] 로컬 입력 → `EVENT` 프레임 전송 (`Payload.Type.BYTES`) — `DrawingViewModel.outboundEvents` → `Frame.Event`
- [x] 인바운드 `EVENT` → 같은 `apply()` 루프 — `SessionManager.incomingDrawing` → `vm.applyRemoteEvent`
- [x] 20–30ms 코얼레싱 (`StrokeAppend` 점 묶기) — `transport/OutboundCoalescer.kt`, 25ms 주기
- [~] 원격 작성자 획 시각 구분 — "함께 그리기" 단일 모드 확정으로 보류. 자기/상대 stroke 동등 가시. Phase 4 "따라 그리기" 옵션 도입 시 알파 감쇠 재추가 검토
- [ ] PING/PONG (BYE는 Phase 2에서 구현됨)
- [ ] 늦참가/재연결 시 `SNAPSHOT_REQ` → `SNAPSHOT`
- [x] **사진 전송**: `PHOTO_META` + `Payload.Type.FILE` → 수신 측 `BackgroundImage` 설정. `PhotoRemove` 도 같이. "배경 합치기" 토글(`MergeBackground`) 도 양방향 동기화
- [x] 사진 송수신 진행률 UI — `Transport.fileTransfers` + `TransferLoadingOverlay`. 최대 120초 타임아웃(Phase 3.5-B), 실패/타임아웃 시 토스트

**완료 기준**: 두 사람이 동시에 그려도 양쪽 캔버스가 일치. 한쪽이 사진을 보내면 양쪽 모두 같은 배경 위에 그림. 끊김/재연결 시 상태 복원.

## Phase 3.5 — Snapshot 전송 안정화 (Phase 4 사전 작업)

**문제**: Nearby Connections 의 `Payload.Type.BYTES` 한도는 **32KB**. 현재 `Frame.Snapshot(strokes)` 는 단일 BYTES payload 로 송신.

- 한 stroke ≈ id + authorId + tool + points 100개 ≈ 1.6KB
- 빽빽한 캔버스(50~300 stroke) = **80KB ~ 500KB → 한도 초과**
- BYTES 한도 초과 시 Nearby 는 **조용히 실패** (명시적 에러 없음) — 사용자는 "동기화 눌렀는데 아무 일 없네?" 만 봄

**왜 지금 처리하는가**
- 1:1 함께 모드 현재 sync 의 잠재 버그 (작은 캔버스에서만 우연히 안 터졌을 뿐)
- Phase 4 모임 모드는 호스트 relay 까지 추가 → 같은 문제가 N 배 확대됨
- Phase 4 시작 전에 안정화 후 진입하는 게 안전

**작업**
- [x] **3.5-A**: Snapshot 송수신을 BYTES → FILE payload 로 전환 — `Frame.Snapshot(strokesPayloadId, hasPhoto)`, `FrameCodec.encodeStrokes/decodeStrokes`, `SessionManager.handleSnapshotFile`. 사진 sync 와 같은 pending FILE/META 매칭 메커니즘 재사용.
- [x] **3.5-B**: Sync 로딩 UI 의 타임아웃 60초 → **120초** 로 확대 — `DrawingScreen.kt` 의 `LaunchedEffect(transferLabel)` 의 `delay(120_000L)`. 토스트 문구도 "2분 넘겨 중단" 으로 갱신.

**완료 기준**: 200+ stroke 빽빽한 캔버스에서 sync 정상 동작. 사진까지 동반된 sync 도 안정. Phase 4 진입 준비 완료.

**예상 작업량**: 1~2 시간 (FILE 메커니즘 재사용).

## Phase 4 — 모임 모드 (1:N, 최대 4인)
> Phase 3.5 (Snapshot 안정화) 선행 권장.
목표: 기존 **"함께 모드" (1:1)** 와 별개로 **"모임 모드" (1:N, 최대 4인)** 추가. 한 명이 자기 캔버스에 그리면서 다른 3명이 무엇을 그리는지 미니 뷰로 보고, 필요하면 누구 한 명의 캔버스를 그대로 자기 캔버스로 가져옴.

**모델 결정**
- Strategy: **`P2P_STAR`** — 호스트 1 + 조인자 최대 3 (Nearby 의 1:N 스타 토폴로지). 조인자끼리는 직접 연결되지 않으므로 **호스트가 EVENT 를 다른 조인자에게 재전송 (app-level relay)**.
- 사진 배경 정책 (1:1 과 다름):
  - **자기 캔버스에는 사진 OK** — 사진/촬영/제거/배경합치기 버튼 그대로 노출. 1:1 함께 모드와 동일한 UX.
  - **다른 사람에게 자동 broadcast 안 함** — `PhotoMeta`/`PhotoRemove`/`MergeBackground` 자동 송신 차단. 자기 사진은 자기만 본다.
  - **미니 뷰에는 사진 미표시** — `stroke` 만 렌더 (배경 null 로 그림). 미니 뷰 크기 + 4-way 사진 전송 복잡도 회피.
  - **"동기화" 시에만 사진 동반** — 선택한 피어가 사진을 갖고 있으면 동기화 응답에 `Frame.Snapshot(hasPhoto=true)` + `PhotoMeta` + FILE 페이로드가 따라옴. 현재 1:1 sync-with-photo 흐름 재사용.
- 함께 모드(1:1) 코드는 그대로 유지. 모임 모드는 별도 진입점·별도 흐름.

**UI 레이아웃**
```
┌──────────────────────────────────────┐
│ TopAppBar [뒤로] [저장]              │
├──────────────────────────────────────┤
│                                      │
│  [내 캔버스 — 크게]                  │ ← 그리기·지우기·undo 가능
│                                      │
├──────────┬──────────┬────────────────┤
│ Peer A   │ Peer B   │ Peer C         │ ← 미니 뷰 (read-only)
│ stroke만 │ stroke만 │ stroke만       │   탭 불가, 동기화 선택 시에만 사용
└──────────┴──────────┴────────────────┘
│ 도구바 [색/붓/굵기/undo/clear/동기화]│
└──────────────────────────────────────┘
```

**"동기화" 동작** (함께 모드와 다름)
- 함께 모드(1:1): 즉시 그 한 명에게 `SnapshotReq` 송신
- 모임 모드: 다이얼로그 → 연결된 피어 리스트 → 한 명 선택 → 컨펌 (사진 안내 포함) → 그 사람에게 타겟 `SnapshotReq` (호스트 relay 거침)
- 모임 모드 컨펌 문구 추가: "상대방 사진이 있는 경우 사진 정보도 가져옵니다"

**구현 항목 — 8단계로 분할**
- [x] **4-A**: `Transport` 다중 endpoint 지원. `connectedEndpoint: String?` → `connectedPeers: StateFlow<List<ConnectedPeer>>`. `send(frame)` = 브로드캐스트, `sendTo(endpointId, frame)` = 유니캐스트. `sendFile`/`sendFileTo` 분리. `incoming` 이 `InboundFrame(endpointId, frame)`, `IncomingFile` 에 endpointId 동반. `TransportState.Connected` → data object. `TransportMode { Duo, Party }` 로 모드별 SERVICE_ID + Strategy 격리 (cross-mode 발견 차단).
- [x] **4-B**: `SessionManager` 다중 피어 상태 머신. `PeerHandshake` 내부 클래스 + `Map<endpointId, PeerHandshake>` 로 피어별 HELLO/ACK 추적. `transport.connectedPeers` flow 가 신규 피어에 `sendTo(Hello)` unicast. HELLO/HelloAck/Pong/proto-Bye 모두 source endpointId 에 unicast. `DrawingViewModel.setAuthor()` 로 `DrawingEvent.authorId` 를 실제 `session.peerId` 로 박음 (`DrawingScreen` 진입 시 주입).
- [ ] **4-C**: `DrawingViewModel.peerCanvases: Map<PeerId, CanvasState>`. 인바운드 이벤트를 발신자 peerId 기준으로 해당 캔버스에 라우팅.
- [ ] **4-D**: Home 화면에 "모임 모드" 버튼 추가. 모임 모드 페어링 화면 — 호스트/조인 명시적 선택, 호스트는 최대 3명 까지 accept.
- [ ] **4-E**: `DrawingScreen` 반응형 레이아웃 (방향 × 참가자 수). 미니 캔버스는 input 비활성 + 미니 뷰 렌더 시 사진 배경 무시(strokes 만). 자기 캔버스의 사진 관련 액션은 유지.
  - **세로**: 캔버스 위, 미니 뷰 N개를 캔버스 아래 가로 분할 (`Row` + `Modifier.weight(1f)`)
  - **가로**: 캔버스 좌측, 미니 뷰 N개를 우측 세로 스택 (`Column` + `Modifier.weight(1f)`)
  - N=0: 미니 영역 숨김 + "참가자 대기 중" 안내
  - N=1/2/3: 같은 코드, `forEach` 로 분할 자동 (자세한 매트릭스 + ASCII 는 `doc/ui-layout.md §5`)
  - 방향 감지: `LocalConfiguration.current.orientation`
- [ ] **4-F**: 호스트 relay — 인바운드 `Frame.Event` 를 source 제외 다른 조인자에게 재송신. **`PhotoMeta`/`PhotoRemove`/`MergeBackground` 는 relay 안 함** (자기 사진은 자기만). `Frame.PeerJoined`/`PeerLeft` 로 조인자 목록 변동 알림.
- [ ] **4-G**: "동기화" 선택 다이얼로그. 피어 리스트 → 선택 → 컨펌 (사진 안내 문구 포함) → 타겟 SnapshotReq (호스트 relay). 응답에 사진 동반되면 자동 적용.
- [ ] **4-H**: 끊김 처리 (조인자 1명 빠져도 세션 유지, 호스트 빠지면 모두 종료), 4명 초과 시 reject, 도구바 액션 가시성 정리.

**완료 기준**:
- 4명까지 같은 세션에 참여 가능
- 내가 그리면 다른 3명의 메인 캔버스가 아닌 *각자 화면의 내 미니 뷰*에 stroke 만 표시됨 (마찬가지 거꾸로도)
- 미니 뷰는 항상 read-only, 사진 배경 표시 안 함
- 자기 캔버스에는 사진 추가 가능, 다른 사람에게 자동 broadcast 안 됨
- 동기화 선택 다이얼로그에서 누구 캔버스든 가져올 수 있고, 선택된 사람의 사진이 있다면 함께 가져옴
- 한 명 끊김은 세션 종료 아님 (호스트 끊김은 종료)

## Phase 5 — 완성품 추출 & 다듬기
- [ ] PNG 내보내기 — 사진 배경 + 완료된 stroke 합성, `MediaStore.Images`로 저장
- [ ] 사용자 갤러리 진입 (Share intent로 다른 앱 전달)
- [ ] 완료된 획 비트맵 캐시 (실측 후 perf 이슈 있을 때)
- [ ] 닉네임 설정 화면
- [ ] 색 팔레트 커스터마이즈

## Phase 6 — 나중에 검토만
지금 결정하지 않을 것들:
- **따라 그리기 모드 옵션** — 학습 시나리오용. Clear/Undo/지우개를 자기 stroke 만, 상대 stroke 은 알파 감쇠. 페어링 시 양쪽 합의로 모드 선택. 사용자 요구 있을 때 도입. 알파 감쇠와 author 필터 분기 코드는 Phase 3-A 중 작성했다가 단일 모드 확정으로 제거됨 — 도입 시 git 히스토리 참고.
- 다중 피어 메시 (P2P_CLUSTER) — Phase 4 모임 모드는 P2P_STAR(호스트+조인) 인데, 호스트 의존 없이 완전 메시가 필요하면 그때.
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
