# 와이어 프로토콜

## 1. 목표와 비목표

**목표**
- 피어들 사이에 드로잉 이벤트를 낮은 지연으로 전달 (1:1 함께 모드 + 1:N 모임 모드).
- 세션에 사진 배경이 있는 경우, 사진 한 장도 전송.
- 메시지 버전이 진화 가능.
- "동기화" 요청 시 현재 캔버스(스트로크 + 스티커 + 사진) 상태를 통째로 복원.

**비목표 (현재 버전)**
- 자동 늦참가/재연결 동기화 — 의도적으로 미도입. 사용자가 "동기화" 버튼으로 명시 요청.
- 비잔틴 안전성/앱 레이어 암호화 — Nearby Connections가 BT/Wi-Fi 링크 계층 암호화 제공, 별도 페어링 신뢰로 충분.
- 손실 없는 재전송 — Nearby가 신뢰 채널 보장하므로 앱 레이어 재전송 없음.
- 여러 장의 사진 — 캔버스당 1장.
- keepalive PING/PONG — Frame 타입은 정의돼 있으나 주기 송신은 미구현 (끊김은 Nearby `onDisconnected` 로 감지).

## 2. 전송과 프레이밍

전송 계층: **Google Nearby Connections** ([nearby-connections.md](nearby-connections.md) 참고).

Nearby의 `Payload`가 이미 메시지 단위 → **우리 쪽 프레이밍 불필요**. 길이-prefix, varint 같은 작업 모두 라이브러리 위임.

페이로드 종류 매핑:

| 메시지 종류 | Nearby Payload 타입 |
|---|---|
| Hello/HelloAck, Event, Snapshot(메타), PhotoMeta, PhotoRemove, MergeBackground, CanvasAspectFrame, SnapshotReq, PeerJoined/PeerLeft, PartyStart, Ping/Pong, Bye | `BYTES` (CBOR) |
| 사진 본체 (JPEG 바이트), 스냅샷 캔버스(stroke + 스티커 + 텍스트 + 비율, `CanvasSnapshot` CBOR 바이트) | `FILE` |

> 스냅샷의 캔버스 콘텐츠(stroke + 스티커 + 텍스트)는 BYTES 32KB 한도를 넘기 쉬워(빽빽한 캔버스 80KB~500KB) **FILE 페이로드**(`CanvasSnapshot` CBOR)로 송신한다 (Phase 3.5-A). `Frame.Snapshot` 은 메타(payloadId)만 BYTES 로.

## 3. 메시지 타입 — 실제 `Frame.kt` (BYTES 채널)

```
Hello            연결 직후 송신. proto/peerId/nick 교환. (다중 모드는 신규 피어에게 unicast)
HelloAck         Hello 수신 응답.
Event            단일 DrawingEvent (StrokeStart/Append/End/Clear/Undo, PlaceSticker/TransformSticker/RemoveSticker, PlaceText/RemoveText). authorId 동반.
SnapshotReq      캔버스 상태 요청. targetPeerId(빈="" broadcast) + requesterPeerId + forLiveView(교실 방장 라이브뷰용).
Snapshot         SnapshotReq 응답 메타. strokesPayloadId(FILE=CanvasSnapshot 매칭) + hasPhoto + targetPeerId.
PhotoMeta        이어질 FILE(사진)의 payloadId/byteSize/width/height/mime + targetPeerId.
PhotoRemove      배경 제거. targetPeerId.
MergeBackground  "저장 시 배경 합치기" 토글 (enabled). 모임/교실 모드는 broadcast 안 함.
CanvasAspectFrame 캔버스 비율 변경 (aspect). 함께=공유 캔버스, 모임/교실=발신자 미니뷰에 적용.
PeerJoined       (모임) 호스트가 다른 조인자에게 새 멤버 알림. peerId/nick. (교실은 미송신 — 조인자 격리)
PeerLeft         (모임) 조인자 끊김 알림. peerId. (교실은 미송신)
PartyStart       (모임/교실) 호스트 "그리기 시작" 신호 → 조인자 함께 Draw 진입.
Ping / Pong      keepalive(ts). 타입만 정의, 주기 송신 미구현.
Bye              정상 종료 알림 (reason).
```

> `proto` 정수는 `PROTO_VERSION` 상수. `targetPeerId` 류 필드는 모두 기본값 `""` — 1:1 함께 모드는 비워서 broadcast 의미, 모임/교실 모드만 채워서 호스트 relay 라우팅.
> **교실 모드**(호스트 중심, 별도 serviceId)의 멤버십·relay 정책은 [classroom-mode.md](classroom-mode.md) 참고 — mesh 가시성(Event relay·PeerJoined/Left)은 모임 전용이라 교실 조인자끼리 자동 미표시.

## 4. 인코딩 — CBOR

선택: **CBOR (kotlinx.serialization).** 바이너리, 작고 빠름, `@Serializable`로 자동 처리.

디버그 빌드에서는 `BuildConfig.DEBUG`일 때 송수신을 JSON으로 미러링 로그 → 사람이 읽으며 디버깅 가능.

```kotlin
@Serializable
sealed class Frame {
    @Serializable @SerialName("hello")
    data class Hello(val proto: Int, val peerId: String, val nick: String) : Frame()

    @Serializable @SerialName("hello_ack")
    data class HelloAck(val peerId: String) : Frame()

    @Serializable @SerialName("event")
    data class Event(val e: DrawingEvent) : Frame()

    @Serializable @SerialName("snapshot_req")
    data class SnapshotReq(
        val targetPeerId: String = "",     // 빈="" = broadcast(Duo), 채움 = 호스트 relay 대상
        val requesterPeerId: String = "",  // 응답을 받을 사람 (응답 시 target 으로 사용)
        val forLiveView: Boolean = false,  // 교실 조인자가 방장 라이브뷰(peerCanvases[host]) 채우기용 pull
    ) : Frame()

    @Serializable @SerialName("snapshot")
    data class Snapshot(
        val strokesPayloadId: Long,        // stroke 목록 FILE 페이로드와 매칭
        val hasPhoto: Boolean,             // true 면 PhotoMeta+FILE, false 면 PhotoRemove 동반
        val targetPeerId: String = "",
    ) : Frame()

    @Serializable @SerialName("photo_meta")
    data class PhotoMeta(
        val payloadId: Long, val byteSize: Long,
        val widthPx: Int, val heightPx: Int, val mime: String,
        val targetPeerId: String = "",
    ) : Frame()

    @Serializable @SerialName("photo_remove")
    data class PhotoRemove(val targetPeerId: String = "") : Frame()

    @Serializable @SerialName("merge_bg")
    data class MergeBackground(val enabled: Boolean) : Frame()

    @Serializable @SerialName("canvas_aspect")
    data class CanvasAspectFrame(val aspect: CanvasAspect) : Frame()  // 캔버스 비율 동기화

    @Serializable @SerialName("peer_joined")
    data class PeerJoined(val peerId: String, val nick: String) : Frame()

    @Serializable @SerialName("peer_left")
    data class PeerLeft(val peerId: String) : Frame()

    @Serializable @SerialName("party_start")
    data object PartyStart : Frame()

    @Serializable @SerialName("ping") data class Ping(val ts: Long) : Frame()
    @Serializable @SerialName("pong") data class Pong(val ts: Long) : Frame()
    @Serializable @SerialName("bye") data class Bye(val reason: String) : Frame()
}
```

`FrameCodec` 은 `Cbor { ignoreUnknownKeys = true }` 로 인코딩/디코딩. 동기화 캔버스(stroke +
스티커 + 텍스트 + 비율)는 별도 `encodeCanvas`/`decodeCanvas`(`CanvasSnapshot`) 로 FILE 페이로드 콘텐츠를
만든다(한도 무제한). `encodeStrokes`/`decodeStrokes` 도 남아있으나 동기화 경로는 `CanvasSnapshot` 사용.

## 5. 핸드셰이크

```
A → B: Hello     { proto=1, peerId="A...", nick="ryu" }
B → A: Hello     { proto=1, peerId="B...", nick="kim" }
A → B: HelloAck  { peerId="A..." }
B → A: HelloAck  { peerId="B..." }
→ 양쪽 remoteHello 수신 + localAckSent + remoteAckReceived 충족 시 SessionState.Connected
```

- `proto` 불일치 → `Bye("incompatible-proto")` 후 끊기.
- 핸드셰이크에 종횡비 협상 필드는 없음. 대신 캔버스 비율은 **`Frame.CanvasAspectFrame` 로 변경 시 동기화**되고,
  스냅샷(`CanvasSnapshot.aspect`)에도 포함. 사진이 있으면 사진 비율이 우선(비율 필드는 표시에 무영향).
- `peerId`: 설치당 1회 생성한 UUID(`SessionManager.peerId`, prefs 보관). 디바이스 MAC 대신 사용 → 권한 의존 회피.
- **모임 모드**: 호스트는 `connectedPeers` flow 로 신규 피어 등장을 감지해 그 endpoint 에 `Hello` 를 **unicast**. 핸드셰이크는 피어별 `Map<endpointId, PeerHandshake>` 로 독립 추적.

## 6. 사진 전송 흐름

사진을 가진 쪽이 송신자(A). 송신은 세션 시작 직후 또는 사용자가 사진을 새로 선택했을 때.

```
A → B: PhotoMeta { payloadId=12345, byteSize=..., widthPx, heightPx, mime="image/jpeg" }
A → B: FILE payload (payloadId=12345)            ← Nearby가 자동 chunking + 진행률 콜백
       (수신측은 onPayloadReceived 가 아니라 onPayloadTransferUpdate SUCCESS 시점에 처리)
```

- `PhotoMeta`는 사진 도착 전 미리 알려 진행률 UI(`TransferLoadingOverlay`) 가능. PHOTO_ACK 은 없음 (Nearby 가 신뢰 채널).
- `payloadId`는 Nearby의 `Payload.getId()` — BYTES와 FILE을 매칭하는 키. 메타/FILE 도착 순서가 엇갈릴 수 있어 양방향 버퍼(`pendingPhotoMeta` / `pendingFiles`)로 매칭.
- **FILE 은 완전히 채워진 뒤(`SUCCESS`)에만 디코드.** `onPayloadReceived` 시점엔 아직 채워지는 중이라 그때 읽으면 사진이 절반 잘린다 — 간헐 버그였고 SUCCESS 시점 emit 으로 수정.
- 송신 시 원본 URI 가 아니라 `PhotoLoader` 가 다운샘플한 비트맵을 JPEG 로 cache 에 써서 보냄 — 대용량 원본이 Nearby FILE 전송 중 끊기는 현상 회피.
- **전송 단위 = 캔버스 한 장**: 함께(Duo)는 양쪽 메인에 적용, 모임(Party)은 발신자 `senderPeerId` 기준 미니 뷰(`peerCanvases[sender].background`)에 적용. 호스트는 다른 조인자에게 relay.

## 7. "동기화" — 캔버스 통째로 가져오기

사용자가 "동기화" 버튼을 눌러 명시 요청할 때만. (자동 늦참가/재연결 동기화는 미도입.)

```
B → A: SnapshotReq { targetPeerId="A", requesterPeerId="B" }   (모임 모드; Duo 는 빈 문자열)
A → B: FILE (CanvasSnapshot CBOR — stroke + 스티커)             ← 32KB 한도 회피 (3.5-A)
A → B: Snapshot { strokesPayloadId=N, hasPhoto=true, targetPeerId="B" }
A → B: PhotoMeta { ..., targetPeerId="B" } + FILE   (hasPhoto=true 인 경우)
   또는 A → B: PhotoRemove { targetPeerId="B" }      (사진 없으면 — 상대 배경도 비움)
```

- 캔버스 콘텐츠는 BYTES 가 아니라 **FILE 페이로드**(CBOR `encodeCanvas` → `CanvasSnapshot{strokes, stickers, texts, aspect}`). `Snapshot.strokesPayloadId` 로 매칭.
- 수신측은 받은 strokes + 스티커 + 텍스트 + 비율로 메인 캔버스를 **덮어씀**(`applySnapshot(strokes, stickers, texts, aspect)`).
- **교실 모드**: 조인자가 `SnapshotReq(forLiveView=true)` 로 방장 라이브뷰를 pull 하면 호스트가 `sendHostCanvasToJoiner` 로 응답(target="" → 조인자의 `peerCanvases[host]` 에 적용). "가져오기"(forLiveView=false)는 자기 메인에 덮어씀.
- **모임 모드 cascade**: A 가 B 의 캔버스를 가져와 자기 메인에 적용한 뒤, 자기 캔버스를 `targetPeerId=""` 로 다시 broadcast → 다른 참가자가 보는 *A 의 미니 뷰*도 갱신. 사진 적용이 끝난 후 broadcast 해야 빈 배경이 안 나간다.

## 8. 순서/충돌

- 단일 연결, Nearby 의 BYTES 는 순서 보장 → 같은 작성자의 이벤트 순서 자동 보장.
- 각 stroke 는 고유 `StrokeId` 로 식별 — 겹쳐 그려도 둘 다 살아남음.
- **멀티(1:1) "함께 그리기" 모드 (현재 동작)**: `Clear` 와 `Undo` 는 양쪽 모두에 적용, `authorId` 로 거르지 않음. 한쪽이 "전체 지우기" 하면 양쪽 캔버스 모두 빈다. 지우개도 자기/상대 stroke 가리지 않고 삭제 가능.
- 인바운드 EVENT 의 `authorId`: 함께(1:1)에선 라우팅 불필요(공유 캔버스). 모임(1:N)에선 발신자 식별에 사용.
- **스티커 이벤트**: `PlaceSticker`/`RemoveSticker` 는 즉시 전파. `TransformSticker`(이동/크기/회전) 는 **commit-on-end** — 드래그/핸들 조작 *중* 엔 로컬만 갱신, 제스처 종료 시 최종 상태 1회만 전송(트래픽 절감, 모임 모드 relay 시 N배 효과). 상대는 변형 과정이 아닌 결과만 봄.

### 다중(1:N) 모임 모드 — 구현 완료

- Strategy `P2P_STAR`: 호스트 + 조인자 최대 3 (`PARTY_MAX_JOINERS`, 초과 시 자동 reject). 조인자끼리 직접 연결 없음.
- **송신 시 `DrawingEvent.authorId` 를 실제 peerId (`SessionManager.peerId`, 설치당 UUID) 로 박아 보냄** — 수신 측이 어느 미니 캔버스에 적용할지 알기 위함. `DrawingViewModel.setAuthor()` 로 주입.
- **호스트 relay** (`relayIfHost`): 호스트가 조인자 A 의 `Frame.Event` 받으면 → 조인자 B, C 에게 source 제외 재전송. 받는 쪽은 발신자의 미니 뷰(`peerCanvases[authorId]`)에 반영. `DrawingViewModel.CanvasRouting.PerPeer`.
- 자기 캔버스엔 자기 stroke 만. 다른 사람 stroke 은 peer 별 `CanvasState`(`peerCanvases`)에. `Clear`/`Undo`/지우개는 자기 캔버스만. 미니 뷰는 read-only.
- **멤버십**: 호스트가 핸드셰이크 완료 시 `PeerJoined` 양방향 송신(새 조인자↔기존 멤버), 끊김 시 `PeerLeft` broadcast. 조인자는 `indirectPeers` 로 호스트 relay 통해 알게 된 다른 조인자 추적. `PartyStart` 로 "그리기 시작" 동기 진입(재합류 시 호스트가 unicast).
- **사진 정책** (초기 "자기만" 설계에서 사용자 피드백으로 반전):
  - 자기 캔버스에 사진 사용 OK — 함께 모드와 동일 UX.
  - `PhotoMeta`/`PhotoRemove` 를 `targetPeerId=""` 로 **broadcast** → 받는 쪽이 `senderPeerId` 매칭해 **그 발신자의 미니 뷰 배경**에 적용. **상대 메인 캔버스엔 영향 없음.** 호스트는 다른 조인자에게 relay.
  - `MergeBackground` 는 저장 옵션이라 broadcast 안 함.
  - 미니 뷰는 그 peer 의 **사진 + stroke 둘 다** 렌더.
- **동기화**: `SnapshotReq(targetPeerId, requesterPeerId)` — 호스트 relay 거쳐 그 피어가 응답. `targetPeerId==self`(=요청자) 면 자기 메인에 적용(`senderPeerId=null` 로 구분), `targetPeerId==""` broadcast 면 발신자 미니 뷰에 적용. §7 cascade 참고.
- 끊김: 조인자 disconnect 는 그 미니 뷰만 비고 세션 유지. **호스트 disconnect = 전체 종료는 미구현(보류)** — 현재는 조인자가 개별 Failed.

## 9. 키프알라이브와 종료

- `Ping`/`Pong` 타입은 정의돼 있으나 **주기 송신은 미구현**. 끊김은 Nearby `onDisconnected` 콜백으로 감지.
- 명시적 종료: `Bye` 후 disconnect. 수신측은 `Bye` 받으면 처리(모임 호스트는 그 피어만 정리, 그 외 Failed).

## 10. 버전 호환성

- `proto` 정수 — 호환 불가 변경 시 증가.
- 새 메시지 타입 추가는 호환됨(`@SerialName`이 달라 무시).
- 새 필드는 nullable + 기본값으로 추가.

## 11. 보안 메모

- Nearby Connections는 BT/Wi-Fi 링크 계층 암호화 제공.
- 연결 수락 단계의 **인증 토큰 표시**(authentication digits) — 양쪽 같은 토큰 확인 후 수락하는 다이얼로그로 구현됨 (`PendingConnection.token`).
- 앱 레이어 추가 암호화는 도입하지 않음 (1차 버전).
