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
- 사진 배경 정책 (1:1 과 다름 — **구현 중 사용자 피드백으로 반전됨**, 아래가 최종):
  - **자기 캔버스에는 사진 OK** — 사진/촬영/제거/배경합치기 버튼 그대로 노출. 1:1 함께 모드와 동일한 UX.
  - **자기 사진은 다른 사람들이 보는 내 미니 뷰에 표시** — `PhotoMeta`/`PhotoRemove` 를 `targetPeerId=""` broadcast. 받는 쪽은 `senderPeerId` 로 매칭해 `peerCanvases[sender].background` 에 적용. **상대 메인 캔버스엔 영향 없음** (그 사람의 미니 뷰에만). 호스트는 다른 조인자에게 relay.
    - (초기 설계는 "자기 사진은 자기만, 미니 뷰 미표시" 였으나 사용자 요구로 위와 같이 변경.)
  - **`MergeBackground` 토글은 broadcast 안 함** — 저장 옵션이라 자기에게만.
  - **미니 뷰는 그 peer 의 사진 + stroke 둘 다 렌더** (`MiniCanvas` 가 배경 직접 그림).
  - **"동기화" 시 사진 동반** — 선택한 피어 사진이 있으면 응답에 `Frame.Snapshot(hasPhoto=true)` + `PhotoMeta` + FILE 동반. `targetPeerId=self` 면 자기 메인에 적용 (`senderPeerId=null` 로 구분). 적용 후 자기 캔버스를 다시 broadcast 해 다른 사람의 내 미니 뷰까지 갱신.
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
- [x] **4-C**: `DrawingViewModel.peerCanvases: SnapshotStateMap<PeerId, CanvasState>` + `CanvasRouting { Shared, PerPeer }` enum. 기본 Shared (싱글 + 1:1 함께 모드), 모임 모드 진입점에서 `setRouting(PerPeer)` 호출. `applyRemoteEvent` 가 routing 에 따라 내 canvas vs `peerCanvases[authorId]` 로 분기. SnapshotStateMap 이라 새 peer 등장 시 미니 뷰 (4-E) 가 자동 등장.
- [x] **4-D**: `DrawMode { Single, Duo, Party }` enum + path-param 라우트 `draw/{mode}`. `SessionManager.get(context, mode)` 모드별 싱글톤 (prefs 만 공유). NearbyTransport 가 Party 호스트 (Role.Host) 일 때 `STATUS_OK` 후에도 광고 유지, `stopAdvertising()` public 메서드 추가. HomeScreen 4번째 버튼 "모임 모드" + 높이 88→72dp 조정. `PartyPairingScreen` 신규 — 호스트/조인 명시적 RolePicker → 호스트는 다중 accept + "그리기 시작" 버튼으로 명시 종료, 조인자는 1:1 흐름과 동일. DrawingScreen 에 mode 인자 — Party 면 `vm.setRouting(PerPeer)`. (호스트 3명 reject + Draw 진입 후 추가 조인 흡수는 4-H.)
- [x] **4-E**: `DrawingScreen` 반응형 레이아웃. `SessionManager.remotePeers: StateFlow<List<RemotePeerInfo>>` 노출 (peerId + nick + endpointId) — `handshakes` 갱신 시점에 publishRemotePeers. `MiniCanvas` 신규 — read-only, 사진 배경 무시, 닉네임 라벨, 빈 상태 placeholder. `DrawingScreen` mode == Party 분기:
  - 세로: `Column` weight 3f/1f, 미니 뷰는 `Row` 가로 분할
  - 가로: `Row` weight 3f/1f, 미니 뷰는 `Column` 세로 스택
  - peers.isEmpty(): 캔버스가 영역 전체 차지 + 우상단 "참가자를 기다리는 중..." 작은 안내
  - 방향: `LocalConfiguration.current.orientation`
  - 분할은 `peers.forEach { MiniCanvas(weight(1f)) }` 한 줄로 1/2/3명 자동 처리
- [x] **4-F**: 호스트 relay 도입. `relayIfHost(sourceEndpointId, frame)` 헬퍼 — `Frame.Event` 만 source 제외 다른 조인자에게 sendTo 재송신. **사진 관련(`PhotoMeta`/`PhotoRemove`/`MergeBackground`) 은 호출 안 함** + DrawingScreen 에서 Party 모드일 때 broadcast 자체 차단 (4-D 정책 마무리). `Frame.PeerJoined`/`PeerLeft` 신규 frame — `maybeFinishHandshake` 에서 호스트가 양방향 PeerJoined (새 조인자에 기존 멤버, 기존 멤버에 새 조인자) 송신. 끊김 시 `syncHandshakesWithPeers`/Bye 분기에서 호스트가 PeerLeft broadcast. SessionManager 에 `indirectPeers: Map<PeerId, IndirectPeerInfo>` — 조인자가 호스트 relay 로 알게 된 다른 조인자. `publishRemotePeers` 가 direct + indirect 합쳐 발행 → 미니 뷰가 다른 조인자도 표시.
- [x] **4-G**: 동기화 다이얼로그 + 타겟 라우팅. Frame `SnapshotReq`/`Snapshot`/`PhotoMeta`/`PhotoRemove` 에 `targetPeerId` 필드 (빈 문자열 = Duo broadcast). SessionManager 의 `shouldRelay`/`endpointForPeerId`/`forwardFile` 헬퍼로 호스트 relay 도입 — Snapshot/PhotoMeta FILE 도착 시 `sendFileTo` 로 forward 하면서 새 payloadId 박은 frame 도 함께 sendTo (target 측 매칭 보존). `SnapshotRequest(requesterPeerId)` SharedFlow shape 변경 — 응답 시 requester 를 target 으로 박음. DrawingScreen 동기화 다이얼로그 `SyncStep` sealed (None / DuoConfirm / PartyPicker / PartyConfirm). Duo 는 1단계, Party 는 피어 피커 → 컨펌 (사진 안내 문구 포함). 컨펌 시 `SnapshotReq(target=peer, requester=self)` 송신 → 호스트 relay → target 응답 → 호스트 relay → 자기 캔버스 덮어쓰기.
- [~] **4-H**: 끊김 처리·4명 초과 reject·도구바 가시성 + 비대칭 UX 정리.
  - **비대칭 UX 해결**: `Frame.PartyStart` 신규 (data object). `SessionManager.partyStart: SharedFlow<Unit>`. 호스트 "그리기 시작" 누름 → `stopAdvertising` + `PartyStart` broadcast → 조인자 전부 같이 Draw 진입. 조인자는 핸드셰이크 완료만으론 페어링 화면 머무름 ("호스트가 시작하기를 기다리는 중" 안내 + 스피너).
  - **4명 초과 reject**: `NearbyTransport.onConnectionInitiated` 에서 Party 호스트가 `connectedPeers.size >= 3` 이면 자동 `rejectConnection` (토큰 다이얼로그도 안 뜸). `PARTY_MAX_JOINERS = 3`.
  - **조인자 끊김**: 4-F의 `Frame.PeerLeft` + `syncHandshakesWithPeers` 흐름이 자동 처리. 그 미니 슬롯만 `EmptyMiniSlot` 으로 복귀.
  - **도구바 가시성**: 4-D/F 단계에서 이미 정리됨 (사진/제거/합치기 broadcast 차단, 동기화/방 열기 가드).
  - 호스트 끊김 = 세션 종료 처리는 사용자 결정으로 보류.

**완료 기준** (구현 후 갱신):
- 4명까지 같은 세션에 참여 가능 (호스트 + 조인자 3, `PARTY_MAX_JOINERS=3` 초과 시 자동 reject)
- 내가 그리면 다른 참가자의 메인 캔버스가 아닌 *각자 화면의 내 미니 뷰*에 표시됨 (거꾸로도). 호스트 relay 로 조인자↔조인자도 전달
- 미니 뷰는 read-only, **그 peer 의 사진 + stroke 둘 다 표시**
- 자기 캔버스에 사진 추가 가능, 자기 사진은 다른 사람의 *내 미니 뷰*에 표시 (상대 메인엔 영향 없음)
- 동기화 다이얼로그에서 누구 캔버스든 가져올 수 있고, 선택된 사람 사진도 함께 가져옴. 가져온 뒤 자기 미니 뷰도 다른 사람에게 갱신
- 조인자 1명 끊김은 세션 유지 (그 미니 슬롯만 비워짐), 호스트는 "방 열기" 로 재모집·재합류 자동 진입
- **호스트 끊김 = 조인자 모임 종료**: 조인자의 연결이 끊기면(호스트가 유일 직접 연결) "방장이 나가 모임이 종료됐어요" 토스트 + 홈 복귀(`onExitToHome`). 재연결 대신. 홈 복귀로 `peerCanvases`/`indirectPeers` 자동 정리. `disconnect()` 는 `Bye` 송신 후 150ms 대기해 전송 보장(백업: 상대 `onDisconnected`).

## Phase 5 — 완성품 추출 & 다듬기
- [x] PNG 내보내기 — `works/PngComposer` (화면용 `StrokeRenderer.drawStroke` 재사용). 앱 내부 `filesDir/works/` 저장(`WorkStore.save`, 최대 100개) + 외부 갤러리 `MediaStore.Images`(`Pictures/DrawingTogether`, `IS_PENDING` + `MediaScannerConnection`) 로 내보내기(`WorkStore.exportToGallery`). 미리보기 화면의 "갤러리로 보내기".
- [x] 사용자 갤러리 진입 — 미리보기 "공유" (`Intent.ACTION_SEND` + FileProvider URI + `createChooser`).
- [ ] 완료된 획 비트맵 캐시 — 여전히 보류. 에어브러시/번짐 도입으로 필요성 커짐 (아래 Phase 5.5 성능 노트).
- [x] 닉네임 설정 화면 — 홈 화면 인라인 닉네임 + 편집 다이얼로그. Duo/Party 인스턴스 양쪽 `setNick`.
- [x] 색 팔레트 커스터마이즈 — 7색 기본(검정/흰색/빨강/파랑/초록/노랑/갈색) + 슬롯별 ColorPickerSheet 편집 + 리셋(`UserPaletteRepo`, prefs 보관).

## Phase 5.5 — 그리기 강화 & 반응형 UI (완료)
> Phase 4/5 이후 사용자 피드백 기반 개선. 상세 아이디어는 [drawing-ideas.md](drawing-ideas.md).

**그리기 강화 (`BrushType` + `StrokeRenderer` 확장 — 화면·PNG·멀티 동기화 자동 공유)**
- [x] **안내선(가이드라인)** — 중앙 십자선 + 격자 6×6/18×18. 로컬 전용(동기화·저장 미포함). `DrawingViewModel.guideCross/guideGrid`, `DrawingCanvas.drawGuides`, `GuideDropdownButton`.
- [x] **에어브러시(분사)** — `BrushType.Airbrush`. `drawAirbrush` 경로 보간 + 결정론 seed(`stroke.id`)로 점 분사 → 매 프레임·양 단말 동일. 분사점 미저장(StrokeId 만 와이어).
- [x] **번짐(수채/스머지)** — `BrushType.Blur`. native `Paint` + `BlurMaskFilter` via `drawIntoCanvas{nativeCanvas}`. PNG 합성에서도 동작.
- [x] **스티커** — 자체 벡터 12종 배치/이동/크기·회전/삭제 + 통합 undo. stroke 아닌 새 요소 타입 (`Sticker`/`StickerKey`/`StickerId`, `DrawingEvent` 3종 Place/Transform/Remove, `CanvasState._stickers` + `UndoItem` 통합 undo, `StickerRenderer.drawSticker` 화면·PNG·미니뷰 공유, Snapshot `CanvasSnapshot`(strokes+stickers) 확장). 변형은 commit-on-end. 상세: [sticker-plan.md](sticker-plan.md).
- [x] **브러시 변형 4종(네온/점선/무지개/붓펜)** — `BrushType` 4개 추가 + `StrokeRenderer` 분기. 네온=발광 후광(BlurMaskFilter)+밝은 코어, 점선=`dashPathEffect`, 무지개=누적 길이→hue 회전(결정론 phase=`stroke.id`), 붓펜=점 간 거리(속도)→구간별 굵기 변조. 모두 점 좌표에서 유도 → 와이어 변경 없음, 화면·PNG·멀티 자동 공유. `PenIllustration`/`BrushPreview` 도 4종 추가.
- [x] **손떨림 보정(스트로크 안정화)** — 입력 점에 지수이동평균(EMA) 적용. 끔/약/강 3단(`Smoothing` enum, alpha 1.0/0.5/0.28). `DrawingCanvas` 입력 단계에서 보정 → 보정된 점만 stroke 에 저장·전송하므로 동기화·저장·undo 자동. 도구바 굵기 줄 "보정" 토글 칩(`SmoothingChip`). 로컬 설정.
- [x] **선 곡선 평활화(렌더)** — 자유 곡선을 직선 폴리라인이 아니라 인접 점 중간점-2차 베지어로 그려 꺾임을 둥글게(`buildFreehandPath`). 색·굵기가 구간별인 무지개·붓펜은 `forEachSmoothPiece`+`drawSmoothPiece`. 화면·PNG 공유. 손떨림 보정(입력)과 별개 단계.
- [x] **스포이드(색 추출)** — `ToolKind.Eyedropper` + 색 팔레트 스포이드 버튼(`EyedropperButton`). 누른 채 드래그하면 조준 십자(`drawEyedropperCursor`)가 따라오고 떼는 순간 그 지점 색을 집음(손가락 가림 보정). `PngComposer` 로 사진+stroke+스티커 합성 비트맵을 만들어 픽셀을 읽음(`CanvasColorSampler`, "보이는 색=집히는 색", alpha 불투명 강제). 집으면 `selectColor` 로 펜 복귀. 로컬 설정.
- [x] **최근 색 / 색 팔레트 저장** — 색을 쓸 때마다 `UserPaletteRepo.addRecent`(최신 앞, 중복 제거, 최대 8개, prefs 영속). 색 팔레트 줄에 프리셋과 구분선으로 나눠 표시(`ColorDot`/`PaletteDivider`, 프리셋 중복 제외). 프리셋/커스텀/스포이드 모든 선택 경로가 기록됨. 로컬 설정.
- [x] **트레이싱 보조 — 반투명** — 사진 배경 표시 알파 순환(원본/연하게/아주 연하게, `TraceOpacity`). TopAppBar 트레이싱 버튼(`TraceGlyph`, 사진 있을 때만). `DrawingCanvas` 가 `drawImage(alpha=)` 로 표시만 적용 — 저장(`mergeBackgroundOnSave`)·동기화엔 미반영(직교). 엣지 검출 오버레이는 P3 잔여.
- [x] **도형 채우기 토글** — `ToolSettings.fill`. `StrokeRenderer.drawShapeForm` 이 fill 이면 `Fill` DrawStyle, 아니면 외곽선 `Stroke`. 화면·PNG·동기화 자동 공유. 도형 드롭다운(`ShapeDropdownButton`) 하단에 "채우기" 토글.
- [x] **배경색 선택** — `CanvasState.backgroundColor`(사진처럼 캔버스 속성, 기본 흰색). `DrawingCanvas` 가 맨 아래 바탕으로 칠하고 `PngComposer` 가 사진 없을 때 흰색 대신 저장. TopAppBar "배경색" 버튼(`BgColorGlyph`) → `ColorPickerSheet`. 현재 로컬 전용(멀티 동기화 미포함 — 와이어 추가는 후속).
- [x] **대칭(미러) 그리기** — `SymmetryMode`(끔/좌우/상하/4분할). `DrawingViewModel` 이 입력 stroke 의 미러 좌표 stroke 를 독립 `StrokeId` 로 함께 emit(정규화 좌표 반사) → 동기화·저장 자동, 도형·브러시도 그대로 미러. 도구바 "보조" 드롭다운(`GuideDropdownButton`, 안내선+대칭 섹션)에 추가. 한 제스처가 N개 stroke 라 undo 는 미러별 1회씩(향후 묶기 가능).
- [x] **타임랩스 Phase 1 (기록+저장)** — 이벤트 로그를 메모리에 기록(`TimelapseRecorder`, `emit`/`applyRemoteEvent`/배경 setter 배선), TopAppBar 기록/종료 버튼 + ● REC + 뒤로가기 저장/폐기 확인. 종료 시 `TimelapseStore`(`filesDir/timelapses/<id>/`, 작품과 독립)에 CBOR 로그+썸네일+배경 저장. 메모리 임시 보관 — 저장 전 앱 종료 시 소실. 상세: [timelapse-plan.md](timelapse-plan.md).
- [x] **타임랩스 Phase 2 (재생+갤러리+삭제)** — `TimelapsePlayer`(빈 `CanvasState` 에 로그 재적용, gap 압축+배속, seek=`rebuildTo`, `CanvasState.reset()` 추가). 홈 "타임랩스" 버튼 → `TimelapseGalleryScreen`(썸네일 그리드, 길게눌러 삭제) → `TimelapsePlayerScreen`(read-only `ReplayCanvas` + 재생/일시정지/seek/배속/삭제). 라우트 `timelapses`·`timelapse/{id}`. 영상 내보내기(Phase 3) 미착수.
- [x] **완료 stroke 비트맵 캐시(성능)** — `CanvasState.contentRevision`(완료 stroke 추가/제거/Clear/snapshot/배경 변경 시 증가) + `DrawingCanvas` 가 `remember(contentRevision, size, density)` 로 완료 stroke 를 투명 비트맵에 캐시(`renderCommittedStrokes`). 색·도구 변경·진행 중 stroke 프레임엔 `drawImage(cached)` 재사용 → recompose 마다 전체 벡터 재그리던 멈칫 해소. 배경은 라이브(트레이싱 알파).

**반응형 가로 모드 + 회전**
- [x] 홈/페어링(함께·모임) 화면 `verticalScroll` — 가로에서 버튼/요소가 화면 밖으로 밀려 선택 안 되던 문제. 발견 리스트는 `heightIn` + `Column forEach`.
- [x] 그리기 화면 가로 레이아웃 — 세로: 캔버스(위)+도구바(아래). 가로: `Row[ 캔버스(좌) | 도구바(우 300dp 패널) ]`, 도구바 `fillHeight`+`SpaceEvenly` 로 여백 없이 분산. 모임 모드는 캔버스 영역 내부 가로 분기 유지.
- [x] 회전 시 연결 유지 — `MainActivity` `configChanges`(orientation 등)로 Activity 재생성 방지 → `DrawingScreen.onDispose(disconnect)` 안 불려 연결 유지, Compose 가 레이아웃 자동 전환.
- [x] 최근 작업 모달 커스텀 bottom 시트 — Material3 `ModalBottomSheet` 가 가로에서 우측 여백 남기는 문제(파라미터로 못 품) 때문에 Box 오버레이로 재구현. 가로 풀폭 + 드래그/백드롭/뒤로가기 닫기, 가로 5열·세로 3열, 회전 자동 재배치, hoisted `gridState` 로 스크롤 위치 보존.

**도구바/연결 표시 다듬기 (스티커 이후)**
- [x] 도구 줄을 라벨 달린 아이콘 5개(붓/도형/스티커/지우개/안내선) `weight` 균등 배치로 — 가로 스크롤 제거, 항상 전부 노출. 세부는 기존 시트/드롭다운 재사용. `ToolIconButton` + `EraserGlyph`/`GuideGlyph`.
- [x] 하단 액션 줄(동기화·방 열기·되돌리기·전체 지우기)을 `Box(CenterEnd)+horizontalScroll` 로 — 다 들어오면 우측 정렬, 넘치면 가로 스크롤. 줄바꿈으로 캔버스 높이 먹던 문제 해결.
- [x] 연결 상태 표시를 상단 peer indicator → **캔버스 우측 상단 내 닉네임 반투명 칩**으로 변경(`MyCanvasContent.selfNick`). `pointerInput` 없어 그리기 무영향. `PeerIndicator.kt` 제거.

**브랜딩**
- [x] 앱 아이콘 리디자인 — Candy Pop 코랄 배경 + 코랄/민트/라벤더 세 물감 방울 클러스터(adaptive + monochrome).
- [x] 런처 이름 한글화 "드로잉 투게더" (앱 실행 중 화면은 영문 "Drawing Together" 유지).

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
- **렌더 성능**: 에어브러시(매 프레임 분사 재계산) / 번짐(BlurMaskFilter) stroke 가 많이 쌓이면 프레임 드랍 가능 → "완료 stroke 비트맵 캐시"(Phase 5 보류) 의 필요성 커짐. 실측 후 도입.
- **PING/PONG keepalive**: `Frame.Ping/Pong` 타입만 있고 주기 송신 미구현. 끊김은 Nearby `onDisconnected` 로 감지(현재 충분).
- **수익화**: 횟수 제한 IAP 검토 → 보류. 상세는 [BILLING.md](BILLING.md).

## 빌드/실행 명령

`CLAUDE.md` 참고.
