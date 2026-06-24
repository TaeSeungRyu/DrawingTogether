# Nearby Connections 전송 계층

## 1. 왜 Nearby Connections인가

| 후보 | 스트로크 동기화 | 사진 1장(2–5MB) 전송 | 페어링 UX | API 복잡도 |
|---|---|---|---|---|
| Bluetooth Classic (RFCOMM) | ✓ | 16–40초 (답답) | 익숙 | 중간 |
| Wi-Fi Direct | ✓ | <1초 | 약간 복잡 | 중상 |
| **Nearby Connections** | ✓ | <1초 (자동) | 가장 단순 | 낮음 |

**선택: Google Nearby Connections (`com.google.android.gms:play-services-nearby`).** 이유:

- 두 모드(자유 그리기 / 사진 보고 그리기)를 같은 전송으로 묶으려면 사진 전송 UX가 결정타.
- Nearby가 BT/Wi-Fi/Wi-Fi Direct를 자동 선택 — 작은 메시지는 저전력, 큰 페이로드는 Wi-Fi.
- 메시지 경계, chunking, 발견-페어링 UI까지 라이브러리가 처리.
- 한국 시장만 대상이라 Google Play Services 의존은 사실상 비용 없음 (대부분 폰에 사전 설치).

## 2. Strategy

모드별로 다른 strategy 사용 (`TransportMode` enum). **serviceId 도 모드별로 분리**해 두 모드가 서로 발견되지 않도록 격리:

| 앱 모드 | `TransportMode` | Strategy | serviceId | 위상 |
|---|---|---|---|---|
| 함께 (1:1) | `Duo` | `P2P_POINT_TO_POINT` | `...drawingtogether.duo` | 1:1 양방향 |
| 모임 (1:N) | `Party` | `P2P_STAR` | `...drawingtogether.party` | 호스트 1 + 조인자 최대 3 |

`P2P_STAR` 의 비대칭:
- 광고자(호스트)는 여러 발견자(조인자) 와 연결 가능
- 발견자는 한 광고자에게만 연결 가능 — **조인자끼리는 직접 연결 안 됨**
- 따라서 조인자 → 다른 조인자 메시지는 **호스트 app-level relay** 필요 (`Frame.Event` 수신 시 source 제외 다른 조인자에게 재전송)

향후 4+명 또는 호스트 단일 장애점이 부담되면 `P2P_CLUSTER` 메시로 전환 검토 (`doc/roadmap.md` Phase 6 보류 항목).

## 3. 권한 (Android 버전별)

`minSdk = 31` 기준. Play Console 위치 권한 선언 폼을 피하기 위해 `ACCESS_FINE_LOCATION` 은 의도적으로 미선언. 트레이드오프: API 31-32 (Android 12/12L) 기기에서는 Wi-Fi Direct 없이 BT 위주로 동작 (사진 전송이 느려질 수 있음).

### `AndroidManifest.xml`

```xml
<!-- API 33+ Wi-Fi P2P 신권한 -->
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
    android:usesPermissionFlags="neverForLocation"
    tools:targetApi="33" />

<!-- API 31+ BT 신권한 -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />

<!-- Wi-Fi 상태 (일반 권한, 런타임 묻지 않음) -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

### 런타임 요청 순서

`pairing` 화면 진입 시 한 번에 묶어서 요청:

1. API 33+: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `NEARBY_WIFI_DEVICES`
2. API 31-32: 위와 동일하되 `NEARBY_WIFI_DEVICES` 제외

Android의 "Nearby devices" 권한 그룹 덕에 위 권한들이 단일 다이얼로그로 통합되어 표시됨.

솔로 모드에서는 권한 요구 없음 — 사용자 신뢰 측면에서 중요.

## 4. 연결 모델

```
[Host]                                            [Joiner]
ConnectionsClient.startAdvertising(
    name, serviceId, callback,
    AdvertisingOptions(P2P_POINT_TO_POINT))
        │
        │             ConnectionsClient.startDiscovery(
        │                 serviceId, callback,
        │                 DiscoveryOptions(P2P_POINT_TO_POINT))
        │                       │
        │                       ▼
        │             onEndpointFound(endpointId, info)
        │                       │
        │             requestConnection(name, endpointId, callback)
        │                       ▼
   onConnectionInitiated(endpointId, info)
        │
        │  양쪽 acceptConnection(endpointId, payloadCallback)
        │
   onConnectionResult(endpointId, OK)  ◄────►   onConnectionResult(endpointId, OK)
        │                                              │
        └──── 이제 sendPayload / onPayloadReceived 양방향 ────┘
```

- **serviceId**: 모드별 고정 문자열 (`...duo` / `...party`). 같은 ID끼리만 발견 → 함께/모임 모드 단말이 서로 안 잡힘.
- **name**: 사용자 닉네임 — 발견 시 표시용.
- **함께(Duo)**: 한 쪽만 호스트로 광고, 다른 쪽 검색. 연결 후 대칭(공유 캔버스).
- **모임(Party)**: 호스트는 첫 연결 후에도 광고 유지(최대 3명 더 수용), 도구바 "방 열기" 로 광고 재개 가능. `onConnectionInitiated` 에서 3명 초과면 자동 reject.
- **재연결**: 자동 시도 안 함. 끊기면 알리고 명시적 재연결. (함께 모드 "재연결" 은 진입 후 100ms 뒤 자동 호스트 광고.)

## 5. Payload 타입

Nearby는 3가지 페이로드 타입 제공 — 우리는 둘만 사용:

| 타입 | 최대 크기 | 우리 사용처 |
|---|---|---|
| `BYTES` | 32 KB | Hello, Event, Snapshot(메타), PhotoMeta, Bye 등 모든 CBOR Frame |
| `STREAM` | 무제한 | 사용 안 함 |
| `FILE` | 무제한 | 사진(JPEG) + **스냅샷 stroke 목록(CBOR)** 전송 |

> stroke 목록을 FILE 로 보내는 이유: 빽빽한 캔버스의 stroke 목록이 BYTES 32KB 한도를 넘으면 Nearby 가 조용히 실패하기 때문 (Phase 3.5-A). FILE 도착은 `onPayloadTransferUpdate` 의 `SUCCESS` 시점에 처리 — `onPayloadReceived` 시점엔 파일이 아직 안 채워져 부분 읽힘 발생.

### BYTES 전송 (CBOR 메시지)

```kotlin
val bytes = cbor.encodeToByteArray(frame)
client.sendPayload(endpointId, Payload.fromBytes(bytes))
// 수신측 PayloadCallback.onPayloadReceived(endpointId, payload):
//   val frame = cbor.decodeFromByteArray<Frame>(payload.asBytes()!!)
```

Nearby가 메시지 경계를 보장하므로 우리 쪽에서 길이-prefix 같은 프레이밍 직접 안 함. 자세한 메시지 포맷은 [protocol.md](protocol.md).

### FILE 전송 (사진)

```kotlin
val pfd = contentResolver.openFileDescriptor(photoUri, "r")
val payload = Payload.fromFile(pfd)
val photoId = payload.id  // 64-bit ID — 메타데이터(BYTES)에 함께 보냄
client.sendPayload(endpointId, payload)
```

수신측 `onPayloadTransferUpdate`로 진행률 모니터, `onPayloadReceived`에서 완성된 파일 핸들 받음. 자동 chunking.

## 6. 동시성

- Nearby 콜백은 메인 스레드에서 호출됨 — 무거운 작업은 즉시 `Dispatchers.IO`로 위임.
- BYTES 페이로드 디코딩(CBOR)도 짧지만 메인 스레드 막지 않도록 코루틴 전환.
- 캔버스 상태 변경은 메인 스레드에서만 — `Channel<DrawingEvent>`로 메인 적용 루프에 넘김.

## 7. 알려진 함정

- **권한 거부 → `startAdvertising`/`startDiscovery` 즉시 실패**. 권한 보유를 보장하는 진입점에서만 호출.
- **Discovery 발견까지 시간 걸림** (수 초). 사용자에게 진행 표시 필요.
- **PAYLOAD_TRANSFERRED 콜백 지연**이 일부 기기에서 관찰됨 — 큰 파일은 진행률 UI로 안심 유도.
- **백그라운드 제약**: Android 13+ 백그라운드 BT/Wi-Fi 스캔 제한. 사용자 화면이 켜져있는 동안만 동작 가정.
- **여러 앱이 같은 serviceId로 광고하지 않도록 주의** — 고유 reverse-domain 권장.

## 8. 추상화 인터페이스

테스트 가능성과 향후 전송 교체 여지를 위해 추상화 계층 유지:

```kotlin
interface Transport {
    val state: StateFlow<TransportState>
    val discovered: StateFlow<List<DiscoveredPeer>>
    val pending: StateFlow<PendingConnection?>       // 인증 토큰 컨펌 대기
    val connectedPeers: StateFlow<List<ConnectedPeer>>
    val incoming: SharedFlow<InboundFrame>           // (endpointId, frame)
    val incomingFiles: SharedFlow<IncomingFile>      // (endpointId, payloadId, uri)
    val fileTransfers: SharedFlow<FileTransferEvent> // 진행률
    val localRole: Role?                             // 호스트/조인자 — UI 분기용

    fun setLocalNick(nick: String)
    suspend fun startAdvertising()
    suspend fun startDiscovery()
    fun stopAdvertising()                            // 광고만 중단 (연결 유지) — "방 열기" 토글
    suspend fun requestConnection(endpointId: String)
    suspend fun acceptPending()
    suspend fun rejectPending()
    suspend fun send(frame: Frame)                   // 모든 연결 피어에 broadcast
    suspend fun sendTo(endpointId: String, frame: Frame)   // unicast (relay/동기화)
    suspend fun sendFile(uri: Uri): Long             // FILE broadcast
    suspend fun sendFileTo(endpointId: String, uri: Uri): Long  // FILE unicast
    fun stop()
}
```

- 구현: `NearbyTransport(context, mode)` — `TransportMode` 로 serviceId/Strategy 결정.
- `send`/`sendFile` 은 `connectedPeers` 전체에 broadcast (1:1 에선 자동으로 1명). `sendTo`/`sendFileTo` 는 호스트 relay·타겟 동기화용 unicast.
- 테스트: 페이크 `Transport`로 세션/프로토콜 로직 격리 가능.

## 9. 테스트

- 단위: `Transport` 추상화 + 페이크 구현으로 메시지 흐름 검증.
- 통합: 두 실기기 손 검증. 두 에뮬레이터 사이 Nearby Connections는 일반적으로 미동작.
- 사진 전송 시 진행률 UI, 끊김 복구 시나리오는 수동 체크리스트.

## 관련 문서

- [architecture.md](architecture.md) — 레이어 구조 (transport 패키지 위치)
- [protocol.md](protocol.md) — 와이어 메시지 포맷
- [drawing-engine.md](drawing-engine.md) — 캔버스/사진 합성
- [roadmap.md](roadmap.md) — Phase 2 진행 계획
