# 버그 헌팅 리포트 — 2026-07-01

멀티 에이전트 워크플로로 코드베이스를 8개 영역(drawing engine·undo / 렌더링·PNG / transport·nearby / 세션 동기화 / 캔버스 입력 / 페어링 / works·타임랩스 / 사진)으로 나눠 병렬 탐색하고, 각 발견을 별도 검증 에이전트가 코드를 직접 읽어 적대적으로 반증했다.

- 탐색 발견: **24건**
- 검증 통과(확정): **17건**

두 개의 큰 테마:
1. **선 굵기 정규화 누락** (#1, #9, #16) — stroke 굵기만 `dp*density` 절대 px라 화면과 저장물/영상의 WYSIWYG가 깨진다.
2. **mesh 발신자 오인식** (#4, #5) — 일부 프레임에 발신자 필드가 없어 endpoint 기반 sender 계산과 `authorId` 라우팅이 불일치, relay 시 원발신자가 호스트로 뒤바뀐다.

---

## High (7)

### 1. 선 굵기가 캔버스 해상도에 무관한 절대 px — PNG/사진 배경에서 화면보다 얇게 나옴 (WYSIWYG 깨짐)
- **파일**: `ui/canvas/StrokeRenderer.kt:370-371`
- **확신도**: high
- **문제**: `strokeWidthPxFor(tool, density) = (fixedWidthDp ?: strokeWidthDp*widthScale) * density` 가 `canvasSize`와 무관한 절대 px 반환. 스티커(`StickerRenderer.kt:26 scale*shortSide`)·텍스트(`TextRenderer.kt:51 sizeFrac*shortSide`)는 짧은 변 비율로 정규화되는데 stroke 굵기만 정규화가 빠짐. 화면 캔버스(~1080px)와 PngComposer의 사진 네이티브 픽셀(최대 2048px)이 같은 density를 쓰므로, 큰 캔버스에서 선이 상대적으로 얇아지고 요소 간 굵기 밸런스도 깨짐.
- **재현**: 4dp 펜으로 고해상도 사진 위에 그린 뒤 저장 → 화면에서 굵던 선이 PNG에서 실낱같이 얇음.
- **수정 방향**: `strokeWidthPxFor`에 `canvasSize`를 넘겨 shortSide 대비 비율로 산출하도록 통일하거나, PngComposer에서 export 해상도 비율만큼 density를 스케일. 모든 브러시 렌더가 이 함수를 공유하므로 한 곳 수정으로 전 경로 반영.

### 2. 스타 모드 호스트 인원 제한이 동시 연결 요청 시 우회됨 (maxJoiners race)
- **파일**: `transport/nearby/NearbyTransport.kt:253-266, 268-282`
- **확신도**: medium
- **문제**: `partyHostFull` 판정을 `onConnectionInitiated`에서 `_connectedPeers.value.size >= mode.maxJoiners` 로만 하는데, 피어는 `onConnectionResult(STATUS_OK)`에서야 추가됨. `onConnectionResult`엔 인원 초과 방어가 없어(무조건 추가) stale 카운트로 통과한 연결이 정원을 초과할 수 있음. `acceptPending`도 재검사 없음.
- **수정 방향**: `onConnectionResult(STATUS_OK)` 진입 시 정원 초과면 `disconnectFromEndpoint`로 잘라내고, `acceptPending()` 직전에도 재검사. 근본적으로는 "수락했지만 STATUS_OK 안 온" pending을 포함하는 예약 카운터.

### 3. 동시 다중 연결 요청 시 `_pending`이 덮어써져 이전 pending 유실
- **파일**: `transport/nearby/NearbyTransport.kt:93-94, 261-265`
- **확신도**: high
- **문제**: `_pending`이 단일 `MutableStateFlow<PendingConnection?>`. 스타 호스트는 광고를 유지하며 여러 조인자를 받는데, 두 조인자가 거의 동시에 붙으면 두 번째 `onConnectionInitiated`가 첫 번째를 덮어씀. 첫 endpoint는 accept/reject가 절대 안 불려 인증 대기로 방치되다 타임아웃 → 조인자 "연결 안 됨".
- **수정 방향**: `_pending`을 큐(`List`/`ArrayDeque`)로 관리하고 accept/reject가 head를 소비, UI는 큐가 빌 때까지 순차 표시. 또는 `acceptPending(endpointId)` 시그니처로 endpoint별 처리.

### 4. mesh relay된 `CanvasAspectFrame`/`BackgroundColorFrame` 발신자가 호스트로 오인식 → 미니뷰 발산
- **파일**: `session/SessionManager.kt:492-502`
- **확신도**: high
- **문제**: 두 프레임에 발신자(peerId) 필드가 없음(`Frame.kt:84,90`). 수신 측은 sender를 `handshakes[endpointId]?.remoteHello?.peerId`(프레임을 물어다 준 endpoint)로 계산. 조인자 A가 배경색/비율 변경 → 호스트가 B·C에 relay → B는 "호스트 endpoint"에서 받아 sender=HOST로 오인식 → A의 변경이 `peerCanvases[HOST]`에 적용됨. 결과: B가 보는 A 미니뷰 미갱신 + 호스트 미니뷰 오염. `Frame.Event`는 `authorId`를 페이로드에 실어 relay 후에도 정확히 라우팅되는 것과 대조.
- **수정 방향**: 두 프레임에 `senderPeerId` 필드 추가. 발신 측이 자기 peerId를 박고, 수신 측은 endpoint 기반 lookup 대신 프레임의 `senderPeerId` 우선 사용(비면 Duo 호환 endpoint fallback).

### 5. mesh broadcast Snapshot/PhotoMeta relay 시에도 발신자가 호스트로 오인식
- **파일**: `session/SessionManager.kt:540-556`
- **확신도**: high
- **문제**: `Frame.Snapshot`/`Frame.PhotoMeta`(targetPeerId="")에도 원발신자 peerId 필드 없음. 조인자 A가 "가져오기" 후 자기 캔버스를 broadcast → 호스트가 다른 조인자에 relay → B는 sender=HOST로 계산 → A의 스냅샷이 `peerCanvases[HOST]`에 applySnapshot되어 호스트 미니뷰가 A 그림으로 덮이고 A 미니뷰는 미갱신. #4와 동일 근본 원인.
- **수정 방향**: broadcast(target="") 프레임에 `originPeerId` 필드 추가, 최초 발신자가 박고 relay 시 보존. 수신 측은 target="" 일 때 `originPeerId`로 sender 계산.

### 6. 툴 변경/컴포저블 이탈로 그리기 제스처가 취소되면 openStroke가 종료 안 됨 (상태 누수 + 멀티 발산)
- **파일**: `ui/canvas/DrawingCanvas.kt:179-245`
- **확신도**: high
- **문제**: `awaitEachGesture` 종료 로직(`onStrokeEnd` + `cursor=null`)이 finally가 아니라 정상 흐름 코드. `pointerInput(tool.kind)` key라 그리는 중 툴 변경 시 코루틴이 취소되며 `onStrokeEnd` 미호출 → `_openStrokes`에 StrokeId 영구 잔존. 로컬에선 미완료 stroke가 undo 불가로 화면에 영구 렌더, 멀티에선 StrokeStart/Append만 전송되고 End 미전송으로 피어·스냅샷·타임랩스 발산.
- **수정 방향**: `awaitEachGesture` 본문을 try/finally로 감싸 취소 시에도 `if (drawing) onStrokeCancel(strokeId)` 보장. `strokeCancel`이 이미 strokeEnd+Undo를 emit하므로 재사용. drawing 플래그로 중복 종료 가드.

### 7. Duo 재연결 시 양쪽 다 `autoHost=true`로 진입 → 둘 다 광고만, 재연결 교착
- **파일**: `ui/AppNavGraph.kt:90-99, 106-121` / `ui/pairing/PairingScreen.kt:101-112`
- **확신도**: high
- **문제**: Duo 끊김 시 양쪽에 대칭적으로 재연결 스낵바가 뜨고, `onReconnect`가 Duo일 때 무조건 `pairingRoute(autoHost=true)`로 이동. 두 기기 모두 `startAdvertising()`만 실행하고 아무도 `startDiscovery()`를 안 해 자동 재연결 불가(광고자끼리는 서로 발견 못 함). 사용자가 한쪽에서 수동으로 "탭하면 검색으로"를 눌러야 회복.
- **수정 방향**: (a) 원래 role을 넘겨 원 호스트만 `autoHost=true`, 원 조인자는 자동 `startDiscovery`. 또는 (b) 광고 진입 후 타임아웃 시 자동 discovery 폴백.

---

## Medium (4)

### 8. `applySnapshot` undo 스택이 시간순이 아니라 타입순으로 쌓임
- **파일**: `drawing/engine/CanvasState.kt:116-120`
- **확신도**: high
- **문제**: 스냅샷 재구성 시 strokes 전부 → stickers 전부 → texts 전부 순으로 push. "되돌리기가 종류 무관 최근 추가를 취소" 불변식과 어긋남. 원격 스냅샷 수신 후 되돌리기 동작이 로컬 그리기와 달라짐. (모델에 seq/timestamp가 없어 병합 정렬 불가한 것이 근본 제약.)
- **수정 방향**: Stroke/Sticker/TextElement에 단조 증가 seq 추가해 스냅샷 직렬화에 포함, applySnapshot에서 seq 기준 병합 정렬. 또는 CanvasSnapshot에 undo 순서열을 함께 실어 복원.

### 9. Airbrush/Neon/Blur 파라미터가 절대 px — 고해상도 저장 시 질감 불일치
- **파일**: `ui/canvas/StrokeRenderer.kt:116-152`
- **확신도**: high
- **문제**: `drawAirbrush`의 `dotRadius=1.2f*density`, `spacing`, `radius` 모두 절대 px(#1의 파생). 큰 캔버스에서 점이 상대적으로 작고 성기게 렌더돼 화면 질감이 PNG에서 재현 안 됨. Neon glow·Blur 반경도 동일.
- **수정 방향**: #1과 함께 canvasSize 비례로 리팩터.

### 10. Nearby 콜백 스레드와 코루틴이 공유하는 비동기 컬렉션
- **파일**: `transport/nearby/NearbyTransport.kt:109-117, 205, 219, 235, 260, 271 ...`
- **확신도**: medium
- **문제**: `outgoingFilePayloads`(Set), `nickByEndpoint`(Map)가 동기화되지 않은 컬렉션인데 GMS 콜백 스레드와 코루틴(메인)이 동시 접근. 가시성 문제로 갓 add된 payloadId를 못 봐 전송 업데이트 오분류/누락, 또는 구조 손상 가능. (`incomingFilePayloads`/`pendingIncomingFiles`는 단일 PayloadCallback executor라 실질 안전 — 주장 일부 과장.)
- **수정 방향**: `outgoingFilePayloads`/`nickByEndpoint`를 `ConcurrentHashMap` 기반으로 교체, 또는 콜백 접근을 단일 executor로 confine.

### 11. `MediaCodec`/`MediaMuxer` 초기화 실패 시 인코더 누수
- **파일**: `works/TimelapseVideoExporter.kt:112-157`
- **확신도**: high
- **문제**: `createEncoderByType`/`configure`/`start()`/`MediaMuxer(...)`가 해제 담당 try/finally 밖. configure/start 또는 muxer 생성이 던지면 이미 start된 codec이 release 없이 버려짐 → 하드웨어 인코더 고갈로 이후 내보내기 실패 가능.
- **수정 방향**: codec/muxer를 nullable로 선언 후 바깥 try/finally로 감싸 non-null일 때만 stop/release.

---

## Low (6)

### 12. 스티커 리사이즈/회전 핸들을 탭만 해도 no-op TransformSticker 전송
- **파일**: `ui/canvas/DrawingCanvas.kt:515-538`
- **확신도**: high
- **문제**: `resizeRotateGesture`가 위치 변화 검사 없이 무조건 `moved=true`(`moveGesture`는 `if (p != downPos)`로 실제 이동만 검사하는 것과 비대칭). 핸들 탭만 해도 멀티 모드에서 사실상 no-op 이벤트 1건 전송.
- **수정 방향**: down 지점과 비교하거나 scale/rot이 유의미하게 변했을 때만 `moved=true`.

### 13. 조인자 대기 중 연결 실패 시 재검색 경로 없음
- **파일**: `ui/pairing/PartyPairingScreen.kt:204-214, 461-474`
- **확신도**: medium
- **문제**: 조인자가 Connected 도달 시 `stopDiscovery`+발견목록 비움. 이후 호스트 이탈 시 Failed로 가지만 `LaunchedEffect(role, permissionsGranted)`가 재실행 안 돼 `startDiscovery` 재호출 없음 → 뒤로가기 외 재검색 불가. (pre-connect reject 경로는 검색 유지되어 안전.)
- **수정 방향**: Failed/Idle+Joiner일 때 "다시 검색" 버튼 노출, 또는 LaunchedEffect key에 sessionState 추가.

### 14. orphan `.png` 누적 — png 기록 후 meta 기록 실패 시 목록/prune에서 영구 누락
- **파일**: `works/WorkStore.kt:43-62, 142-147`
- **확신도**: high
- **문제**: `save()`가 PNG 먼저 쓰고 meta 나중. 사이에 크래시/IO 예외 시 `.png`만 남음. `loadAll()`은 `.meta`만 나열해 목록·prune 카운트에서 누락 → 내부 저장소에 영구 잔존.
- **수정 방향**: meta 쓰기 실패 시 png 롤백, 또는 write-then-rename, 또는 초기화 시 orphan png 정리 스윕.

### 15. 동시 `save()` 시 stale `_works`로 같은 이름 라벨 중복
- **파일**: `works/WorkStore.kt:33-48, 164-172`
- **확신도**: medium
- **문제**: `resolveUniqueName`이 lock 없이 `_works.value`(save 끝에서야 갱신)를 읽음. 거의 동시 save 두 건이 같은 이름 확정 가능. UUID 파일명은 달라 파일 손상은 없고 라벨만 중복.
- **수정 방향**: `Mutex`로 이름 확정~`_works` 갱신 구간 직렬화, 또는 in-flight 이름 Set 관리.

### 16. 타임랩스 영상 stroke 굵기가 `density=w/400` 고정 가정
- **파일**: `works/TimelapseVideoExporter.kt:131, 221-243`
- **확신도**: high
- **문제**: `renderFrame`이 `density=w/400f` 고정 → 영상은 "캔버스 폭 항상 400dp" 가정. 실제 화면 폭이 다르면 영상 속 선 두께 비율이 화면과 어긋남(#1과 같은 계통).
- **수정 방향**: 기록 시점 실제 canvasSize.width를 타임랩스 메타에 저장해 export 때 재사용, 또는 PngComposer와 같은 기준으로 통일.

### 17. `EdgeDetector.detect`: ARGB_8888 변환용 copy 비트맵 미recycle
- **파일**: `photo/EdgeDetector.kt:22-28, 64`
- **확신도**: medium
- **문제**: config가 ARGB_8888이 아닐 때 만든 copy를 recycle 안 함. 큰 이미지에서 순간 네이티브 메모리 압박. (PhotoLoader가 항상 ARGB_8888을 만들어 실제로는 copy 분기가 거의 안 타므로 low.)
- **수정 방향**: 복사 여부 플래그로 기억해 getPixels 직후(및 조기 리턴 전) copy본만 recycle. 원본 `src`는 호출자 소유이므로 recycle 금지.
