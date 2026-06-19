# 블루투스 전송 계층

## 1. Classic vs BLE — 무엇을 쓸 것인가

| 기준 | Bluetooth Classic (RFCOMM/SPP) | BLE (GATT) |
|---|---|---|
| 처리량 | 수백 kbps ~ 1 Mbps | 수십 kbps (MTU 협상 후 더 나아짐) |
| 지연 | 낮음, 스트림 친화 | notify 간격에 의존 |
| 페어링 UX | 시스템 페어링 UI 사용 | 보통 페어링 없이 직접 연결 |
| API 모델 | 소켓 (`InputStream`/`OutputStream`) | 서비스/특성/notify |
| 두 기기 1:1 | 자연스러움 | 가능하지만 GATT 서버 직접 구현 필요 |

**선택: Bluetooth Classic + RFCOMM(SPP).** 이유:

- 1:1, 양방향, 실시간 스트리밍이라는 요구에 가장 자연스러운 모델.
- 드로잉 이벤트는 작지만 자주 흐름 → 스트림 추상화가 메시지 큐보다 단순.
- API 26(minSdk) 이상에서 안정적.

BLE는 추후 다중 피어나 백그라운드 광고가 필요해질 때 재검토.

## 2. 권한 (Android 버전별)

런타임 권한이 SDK 31에서 크게 바뀐 점이 핵심입니다.

### `AndroidManifest.xml`

```xml
<!-- Android 12+ (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" /> <!-- 호스트가 listen할 때 -->

<!-- Android 11 이하 (API 30 이하) -->
<uses-permission android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />

<!-- Android 11 이하에서 디바이스 스캔에 필요 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"
    android:maxSdkVersion="30" />

<uses-feature android:name="android.hardware.bluetooth" android:required="true" />
```

### 런타임 권한 요청 순서

1. API 31+: `BLUETOOTH_SCAN` → `BLUETOOTH_CONNECT` (필요 시 `BLUETOOTH_ADVERTISE`).
2. API ≤30: `ACCESS_FINE_LOCATION` (스캔 시).
3. 어댑터 활성 상태 확인 — 꺼져 있으면 `ACTION_REQUEST_ENABLE` 인텐트.

권한 요청은 `pairing` 화면 진입 시점에만. 솔로 모드에서는 BT 권한을 요구하지 않음 → 사용자 신뢰 측면에서 중요.

## 3. 연결 모델

```
[Host]                                    [Client]
listenUsingRfcommWithServiceRecord(UUID)
        │
        │  ── accept() (blocking, IO 코루틴) ──
        │                                      createRfcommSocketToServiceRecord(UUID)
        │                                              │
        ▼                                              ▼
  BluetoothSocket  ◄────── 연결 수립 ──────►   BluetoothSocket
        │                                              │
        └──── 양쪽 모두 read/write 코루틴 시작 ─────────┘
```

- **고정 UUID**: 앱 전용 UUID 하나를 상수로 정의. RFCOMM 서비스 이름은 `DrawingTogether`.
- **역할 비대칭이지만, 연결 후엔 대칭**: 누가 호스트인지는 협상용일 뿐. 드로잉 권한은 양쪽 동등.
- **재연결**: 소켓 끊기면 자동 재시도 안 함. 사용자에게 "연결 끊김" 표시 후 명시적 재연결. 자동 재시도는 안 보이게 상태가 꼬일 위험이 더 큼.

## 4. 소켓 I/O 패턴

```kotlin
// pseudo-code
class BluetoothTransport(private val socket: BluetoothSocket) {
    private val incoming = MutableSharedFlow<ByteArray>()
    private val outgoing = Channel<ByteArray>(Channel.BUFFERED)

    suspend fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { readLoop() }
        scope.launch(Dispatchers.IO) { writeLoop() }
    }

    private suspend fun readLoop() {
        val input = socket.inputStream
        // 프레이밍 — 각 메시지 앞에 길이(varint) 붙여서 경계 식별
        while (currentCoroutineContext().isActive) {
            val len = readVarInt(input) ?: break
            val buf = ByteArray(len)
            input.readFully(buf)
            incoming.emit(buf)
        }
    }
    // ...
}
```

핵심:
- **프레이밍 필수**. RFCOMM은 바이트 스트림이라 메시지 경계를 직접 표시해야 함. varint 길이 prefix 사용.
- **읽기/쓰기 코루틴 분리**. 한 스레드에서 둘 다 하면 deadlock 위험.
- **소켓 닫기 = 코루틴 취소 트리거**. `socket.close()`가 `read()`에 `IOException`을 던짐 → 정상 종료 경로.

## 5. 발견(Discovery) UX

- `BluetoothAdapter.bondedDevices`로 이미 페어링된 디바이스 먼저 보여줌.
- `startDiscovery()` 결과는 `BroadcastReceiver`로 수신. 12초 동안 동작 → 끝나면 종료 알림.
- 사용자가 디바이스를 선택하면 페어링 안 돼있는 경우 시스템이 페어링 다이얼로그를 띄움. 우리는 그 결과를 `ACTION_BOND_STATE_CHANGED`로 관찰만.

## 6. 알려진 함정

- `BluetoothAdapter` 메서드 다수가 API 31에서 `BLUETOOTH_CONNECT` 권한 체크를 강화 → `@SuppressLint("MissingPermission")` 남발 말고 권한 보유를 보장하는 진입점에서만 호출.
- 일부 제조사 펌웨어는 SDP 캐시 문제로 `createRfcommSocketToServiceRecord`가 실패함. fallback으로 reflection으로 `createRfcommSocket(channel=1)`을 시도하는 패턴이 알려져 있으나, **처음엔 도입하지 말 것**. 진짜 문제가 보이면 그때.
- `discovery` 중에는 연결 throughput이 급락. 연결 직전 `cancelDiscovery()` 호출 필수.

## 7. 테스트

- 단위 테스트에서 실제 `BluetoothSocket`은 사용 불가 → `interface Transport { val incoming: Flow<ByteArray>; suspend fun send(b: ByteArray) }`로 추상화하고 페이크 구현으로 테스트.
- 두 기기 손 검증이 1차. 두 에뮬레이터 사이 BT 페어링은 일반적으로 지원 안 됨.
