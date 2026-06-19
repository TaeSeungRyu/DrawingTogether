# 와이어 프로토콜

## 1. 목표와 비목표

**목표**
- 두 피어 사이에 드로잉 이벤트를 낮은 지연으로 전달.
- 메시지 경계가 명확하고, 버전이 진화 가능해야 함.
- 새 피어가 중간에 연결되어도 현재 캔버스 상태를 복원 가능해야 함.

**비목표 (초기 버전)**
- 다중 피어. 1:1 고정.
- 비잔틴 안전성/암호화. BT 페어링이 신뢰 경계를 제공.
- 손실 없는 신뢰성. RFCOMM이 이미 신뢰 채널이므로 앱 레이어 재전송 없음.

## 2. 프레이밍

각 메시지는 다음 형식의 단일 프레임으로 송신:

```
+----------------+--------------------+
| varint length  | payload (length B) |
+----------------+--------------------+
```

- `varint`: 1–5바이트 LEB128 부호 없는 길이.
- payload는 바이너리 인코딩(아래 §4).

## 3. 메시지 타입

```
HELLO         양쪽 동시 송신, 능력/버전 교환. 연결 직후 1회.
HELLO_ACK     HELLO 수신 응답.
SNAPSHOT_REQ  현 캔버스 상태 일괄 전송 요청 (재연결/늦참가 시).
SNAPSHOT      모든 완료된 stroke 목록 + 진행 중 stroke (있다면).
EVENT         단일 DrawingEvent (StrokeStart/Append/End/Clear/Undo).
PING / PONG   keepalive 및 RTT 측정. 5초 주기.
BYE           정상 종료 알림.
```

## 4. 인코딩 — 처음엔 무엇을 쓸까

후보 둘:

| 포맷 | 장점 | 단점 |
|---|---|---|
| **CBOR** (kotlinx.serialization) | 바이너리, 작고 빠름, `@Serializable`로 자동 | 디버깅 시 사람이 못 읽음 |
| JSON Lines | 디버깅 쉬움, 누구나 파싱 가능 | 크고 느림 (드로잉 트래픽엔 중요) |

**선택: CBOR.** 단, 개발 빌드에선 `BuildConfig.DEBUG`일 때 JSON으로 미러링 로그를 남겨 디버깅 가능하게 함.

`kotlinx.serialization` 의존성 추가가 필요 (현재 카탈로그에 없음).

### 예시(개념)

```kotlin
@Serializable
sealed class Frame {
    @Serializable @SerialName("hello")
    data class Hello(val proto: Int, val peerId: String, val nick: String): Frame()

    @Serializable @SerialName("event")
    data class Event(val e: DrawingEvent): Frame()

    @Serializable @SerialName("snapshot")
    data class Snapshot(val strokes: List<Stroke>): Frame()
    // ...
}
```

## 5. 핸드셰이크

```
A → B: HELLO  { proto=1, peerId="A...", nick="ryu", canvas={w:1.0, h:1.0} }
B → A: HELLO  { proto=1, peerId="B...", nick="kim", canvas={w:1.0, h:1.0} }
A → B: HELLO_ACK
B → A: HELLO_ACK
```

- `proto` 불일치 → 양쪽 즉시 `BYE("incompatible")` 후 끊기.
- 캔버스 종횡비는 양쪽이 합의(둘 다 정사각형으로 고정해 시작 — 단순화).
- `peerId`는 설치당 1회 생성한 ULID. BT MAC 대신 사용 → 권한 의존 회피.

## 6. 늦참가 / 재연결 동기화

```
B → A: SNAPSHOT_REQ
A → B: SNAPSHOT { strokes=[s1, s2, ...], openStrokes=[...] }
A → B: EVENT(...)   // 그 후 평상시 흐름
```

- 스냅샷은 한 프레임에 다 담음(획 수 ~수천 단위까지는 문제 없음). 그 이상이면 chunked 도입.
- 스냅샷 전송 중 새 이벤트가 발생해도, 수신측은 `apply(snapshot)` 후 후속 `EVENT`를 그대로 적용 → 자연 수렴.

## 7. 순서/충돌

- 단일 채널이므로 같은 작성자의 이벤트는 **TCP/RFCOMM 순서 보장**으로 충분.
- 두 작성자의 이벤트는 서로 독립 객체(`StrokeId` 분리). 동시에 겹쳐 그려도 둘 다 살아남음.
- `Clear`는 양쪽 모두에 적용되고, 자기 `Clear`만 자기 `Undo`로 되돌릴 수 없음(되돌리기 범위는 stroke 단위로 한정).

## 8. 키프알라이브와 종료

- 5초마다 `PING`, 10초 응답 없으면 끊김 판정.
- 명시적 종료는 `BYE` 후 소켓 close. 수신측은 `BYE` 받으면 UI 상태를 `Disconnected`로 전이.

## 9. 버전 호환성

- `proto` 정수 필드는 호환 안 되는 변경 시 증가.
- 새 메시지 타입 추가는 호환됨(`@SerialName`이 달라 무시).
- 새 필드는 nullable + 기본값으로 추가.

## 10. 보안 메모

- BT 페어링이 1차 신뢰 경계. 페어된 디바이스만 RFCOMM 연결 가능.
- 앱 레이어에서 추가 인증은 도입하지 않음(초기 버전).
- BT는 페이로드 암호화를 링크 레이어에서 제공. 그 위에 또 한 겹 올리는 건 비용 대비 가치 낮음.
