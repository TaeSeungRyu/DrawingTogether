# 와이어 프로토콜

## 1. 목표와 비목표

**목표**
- 두 피어 사이에 드로잉 이벤트를 낮은 지연으로 전달.
- 세션에 사진 배경이 있는 경우, 사진 한 장도 전송.
- 메시지 버전이 진화 가능.
- 새 피어가 중간에 연결되어도 현재 캔버스 + 사진 상태를 복원 가능.

**비목표 (초기 버전)**
- 다중 피어 — 1:1 고정.
- 비잔틴 안전성/암호화 — Nearby Connections가 BT/Wi-Fi 링크 계층 암호화 제공, 별도 페어링 신뢰로 충분.
- 손실 없는 재전송 — Nearby가 신뢰 채널 보장하므로 앱 레이어 재전송 없음.
- 여러 장의 사진 — 세션당 1장.

## 2. 전송과 프레이밍

전송 계층: **Google Nearby Connections** ([nearby-connections.md](nearby-connections.md) 참고).

Nearby의 `Payload`가 이미 메시지 단위 → **우리 쪽 프레이밍 불필요**. 길이-prefix, varint 같은 작업 모두 라이브러리 위임.

페이로드 종류 매핑:

| 메시지 종류 | Nearby Payload 타입 |
|---|---|
| HELLO/ACK, EVENT, SNAPSHOT, PHOTO_META, PHOTO_ACK, PING/PONG, BYE | `BYTES` (CBOR) |
| 사진 본체 (JPEG 바이트) | `FILE` |

## 3. 메시지 타입 (BYTES 채널)

```
HELLO         양쪽 동시 송신. 능력/버전/사진 사용 여부 교환. 연결 직후 1회.
HELLO_ACK     HELLO 수신 응답.
SNAPSHOT_REQ 현 캔버스 상태 일괄 전송 요청 (재연결/늦참가 시).
SNAPSHOT      모든 완료된 stroke 목록 + 진행 중 stroke + (있다면) 사진 메타.
EVENT         단일 DrawingEvent (StrokeStart/Append/End/Clear/Undo).
PHOTO_META    이어서 보낼 FILE 페이로드의 id, 크기, 가로/세로, 파일 포맷.
PHOTO_ACK     사진 수신 완료 알림.
PING / PONG   keepalive 및 RTT 측정. 5초 주기.
BYE           정상 종료 알림.
```

## 4. 인코딩 — CBOR

선택: **CBOR (kotlinx.serialization).** 바이너리, 작고 빠름, `@Serializable`로 자동 처리.

디버그 빌드에서는 `BuildConfig.DEBUG`일 때 송수신을 JSON으로 미러링 로그 → 사람이 읽으며 디버깅 가능.

```kotlin
@Serializable
sealed class Frame {
    @Serializable @SerialName("hello")
    data class Hello(
        val proto: Int,
        val peerId: String,
        val nick: String,
        val canvas: CanvasSpec,           // 종횡비
        val photoSession: Boolean,        // 사진 모드 여부
    ) : Frame()

    @Serializable @SerialName("event")
    data class Event(val e: DrawingEvent) : Frame()

    @Serializable @SerialName("snapshot")
    data class Snapshot(
        val strokes: List<Stroke>,
        val openStrokes: List<Stroke>,
        val photo: PhotoMeta?,            // 진행 중인 세션의 사진 메타 (null 가능)
    ) : Frame()

    @Serializable @SerialName("photo_meta")
    data class PhotoMeta(
        val payloadId: Long,              // FILE 페이로드와 매칭
        val byteSize: Long,
        val widthPx: Int,
        val heightPx: Int,
        val mime: String,                 // "image/jpeg" 등
    ) : Frame()

    @Serializable @SerialName("photo_ack")
    data class PhotoAck(val payloadId: Long) : Frame()

    // ... ping/pong/bye 등
}
```

## 5. 핸드셰이크

```
A → B: HELLO  { proto=1, peerId="A...", nick="ryu", canvas={ratio: 1.0}, photoSession=false }
B → A: HELLO  { proto=1, peerId="B...", nick="kim", canvas={ratio: 1.0}, photoSession=false }
A → B: HELLO_ACK
B → A: HELLO_ACK
```

- `proto` 불일치 → 양쪽 즉시 `BYE("incompatible")` 후 끊기.
- 캔버스 종횡비: 사진 세션이 아니라면 기본 1:1, 사진 세션이면 사진 비율로 협상.
- `peerId`: 설치당 1회 생성한 ULID. 디바이스 MAC 대신 사용 → 권한 의존 회피.

## 6. 사진 전송 흐름

사진을 가진 쪽이 송신자(A). 송신은 세션 시작 직후 또는 사용자가 사진을 새로 선택했을 때.

```
A → B: PHOTO_META { payloadId=12345, byteSize=2_400_000, widthPx=1920, heightPx=1280, mime="image/jpeg" }
A → B: FILE payload (payloadId=12345)            ← Nearby가 자동 chunking + 진행률 콜백
       (Nearby가 onPayloadReceived 콜백으로 B에 완성 파일 전달)
B → A: PHOTO_ACK { payloadId=12345 }
```

- `PHOTO_META`는 사진 도착 전 미리 알려 진행률 UI 가능.
- `payloadId`는 Nearby의 `Payload.getId()` — BYTES와 FILE을 매칭하기 위한 키.
- 사진 도착이 완료되기 전에도 EVENT는 정상 송수신 — 사진은 배경 레이어, stroke는 그 위 별도 레이어이므로 순서 의존 없음.
- 캔버스 종횡비가 바뀌면 양쪽 모두 재레이아웃.

## 7. 늦참가 / 재연결 동기화

```
B → A: SNAPSHOT_REQ
A → B: SNAPSHOT { strokes=[...], openStrokes=[...], photo=PhotoMeta? }
A → B: FILE (사진 페이로드, photo가 null 아닌 경우)
A → B: EVENT(...)   // 그 후 평상시 흐름
```

- `Snapshot.photo`가 null이면 사진 없는 세션 → FILE 전송 안 함.
- null 아니면 곧이어 FILE 페이로드 전송.

## 8. 순서/충돌

- 단일 연결, Nearby 의 BYTES 는 순서 보장 → 같은 작성자의 이벤트 순서 자동 보장.
- 각 stroke 는 고유 `StrokeId` 로 식별 — 겹쳐 그려도 둘 다 살아남음.
- **멀티(1:1) "함께 그리기" 모드 (현재 동작)**: `Clear` 와 `Undo` 는 양쪽 모두에 적용, `authorId` 로 거르지 않음. 한쪽이 "전체 지우기" 하면 양쪽 캔버스 모두 빈다. 지우개도 자기/상대 stroke 가리지 않고 삭제 가능.
- 인바운드 EVENT 의 `authorId` 는 송신 측 VM 의 `PeerId.Local` 그대로 도착 — 수신 측에서도 Local 로 박힘. 1:1 에선 라우팅 안 해도 됨.

### 다중(1:N) 모드 — Phase 4 예정

- Strategy `P2P_STAR`: 호스트 + 조인자 최대 3. 조인자끼리 직접 연결 없음.
- **송신 시 `DrawingEvent.authorId` 를 실제 peerId (SessionManager.peerId, 설치당 UUID) 로 박아 보냄.** 1:1 에서 `PeerId.Local` 그대로 보내던 것과 다름 — 수신 측이 어느 미니 캔버스에 적용할지 알기 위함.
- **호스트 relay**: 호스트가 조인자 A 로부터 `Frame.Event` 받으면 → 조인자 B, C 에게 그대로 재전송 (source endpointId 제외). 양쪽이 동일 이벤트를 본인 화면이 아닌 *그 조인자의 미니 뷰*에 반영.
- 자기 캔버스에는 자기가 그린 stroke 만 들어감. 다른 사람들 stroke 은 미니 캔버스 (peer 별 `CanvasState`) 에만 들어감.
- `Clear`/`Undo`/지우개는 자기 캔버스만 영향. 미니 뷰는 read-only.
- "동기화" 는 타겟 peerId 와 함께 보내는 `SnapshotReq` — 호스트 relay 거쳐 그 피어가 응답. 응답이 도착하면 *내 메인 캔버스* 만 덮어씀.
- 끊김: 한 조인자 disconnect 는 그 미니 뷰 사라짐 + 세션 유지. 호스트 disconnect 는 전체 세션 종료.

## 9. 키프알라이브와 종료

- 5초마다 `PING`, 10초 응답 없으면 끊김 판정 → Nearby `disconnectFromEndpoint` 호출.
- 명시적 종료: `BYE` 후 disconnect. 수신측은 `BYE` 받으면 UI를 `Disconnected`로 전이.

## 10. 버전 호환성

- `proto` 정수 — 호환 불가 변경 시 증가.
- 새 메시지 타입 추가는 호환됨(`@SerialName`이 달라 무시).
- 새 필드는 nullable + 기본값으로 추가.

## 11. 보안 메모

- Nearby Connections는 BT/Wi-Fi 링크 계층 암호화 제공.
- 연결 수락 단계의 **인증 토큰 표시**(authentication digits) UI로 중간자 공격 방지 가능. Phase 2에서 옵션으로 노출.
- 앱 레이어 추가 암호화는 도입하지 않음 (1차 버전).
