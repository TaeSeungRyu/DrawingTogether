# 교실 모드 (Classroom Mode) — 기능 정의 & 구현 계획

> 상태: **계획만 수립, 미구현.** 기존 모임 모드(Party)와 별개인 **신규 모드**.
> 이 문서는 기능 정의 + "공유 + 분기" 구현 접근을 정리한 스펙. 구현 시 코드와 [roadmap.md](roadmap.md) 갱신.

## 1. 개요

1:N "교사–학생" 형태의 **호스트 중심** 협업 모드. 기존 [모임 모드(Party)](#부록-모임-모드와의-차이)와 달리, 조인자끼리는 서로를 보지 못하고 호스트가 중심이 된다.

- 홈 화면에 **별도 메뉴 "교실 모드"**로 진입한다.
- 호스트 1 + 조인자 최대 3, `P2P_STAR` (모임 모드와 같은 스타 전송).
- **모든 참가자는 자기 캔버스에 그린다.**

## 2. 역할별 기능

### 방을 개설한 사람 (호스트 / 교사)
- 자기 그림이 **모든 조인자에게 라이브로 스트리밍**된다.
- **"참여자 보기" 버튼**을 누르면 **모달(다이얼로그)**이 열린다.
- 모달에서 참여한 인원의 **이름을 선택**하면 그 인원이 **지금 무엇을 그리는지 라이브로** 본다(한 번에 한 명, 온디맨드).

### 참여한 사람 (조인자 / 학생)
- 방을 개설한 사람(호스트)의 내용을 **항상 라이브로 본다**(보조 뷰). 보조 뷰가 작으므로 **탭하면 큰 모달**로 본다.
- 호스트의 내용을 자기 캔버스로 **가져올 수 있다**("가져오기" = 스냅샷 pull). 도구바 동기화 버튼은 없음.
- **다른 참여 인원의 내용(이름·그림)은 볼 수 없다.**

> 인원: 호스트 + 최대 9 = **10명**. Nearby P2P_STAR 안정성상 더 키우지 않음(수십 명은 비현실적).
> 호스트에는 "가져오기/동기화" 버튼이 없다(호스트가 중심이므로 가져올 필요 없음).

## 3. 참여 제한 — 교실 모드끼리만

**교실 모드 사용자만 같은 교실에 참여할 수 있다.** 모임 모드·함께 모드 사용자는 교실 방을 발견하지도, 들어오지도 못한다.

- 구현: `NearbyTransport`는 모드별 **serviceId + Strategy로 발견을 격리**한다("같은 모드끼리만 발견된다", 코드 주석 명시). 교실 모드에 **고유 serviceId**(예: `com.rts.rys.ryy.drawingtogether.classroom`)를 부여하면, 광고/검색이 그 serviceId로만 매칭되어 교실 기기끼리만 연결된다.
- 즉 모드 격리는 추가 인증 로직 없이 serviceId 분리로 달성된다(모임/함께 모드가 이미 이 방식).

## 4. 구현 접근 — "공유 + 분기"

스타 전송·세션·미니뷰 인프라를 **재사용**하되, 새 모드 값으로 **가시성·UI만 분기**한다. **모임 모드(Party) 코드 경로는 손대지 않는다.**

### 핵심 통찰
`SessionManager`의 "모두가 모두를 보는" mesh 가시성 코드(이벤트 중계 `relayIfHost`, 멤버십 broadcast `announcePeerJoined`/`PeerLeft`)는 **이미 `mode == TransportMode.Party`로 게이트**돼 있다. 따라서 새 `TransportMode.Classroom`은 그 코드를 **자동으로 건너뛰어** "조인자끼리 안 보임"이 별도 작업 없이 달성된다.

반대로 **스타 공통 메커니즘**(호스트 4인 제한, 시작 후 광고 유지, PartyStart 지각 입장)은 Party 전용으로 게이트돼 있어 **Classroom도 포함되도록 넓혀야** 한다(권장: `TransportMode.isStar = strategy == P2P_STAR` 헬퍼).

### 변경 범위 (전부 "분기 추가" — Party 동작 보존)
1. **모드 값**: `DrawMode.Classroom`(`ui/AppNavGraph.kt`), `TransportMode.Classroom`(고유 serviceId, `P2P_STAR`)(`transport/nearby/NearbyTransport.kt`).
2. **스타 공통 확장**: `NearbyTransport` 호스트 4인 제한·광고 유지, `SessionManager` PartyStart 지각 입장을 `Party || Classroom`(=`isStar`) 기준으로. `announcePeerJoined`(멤버십 알림)는 **Party 전용 유지**.
3. **세션 싱글톤**: `SessionManager`에 `classroomInstance` 추가(`duo/partyInstance` 패턴). 가시성 정책 코드는 **무변경**.
4. **페어링 재사용**: `PartyPairingScreen`에 `mode: TransportMode` 파라미터 추가 → 교실 페어링에서 `TransportMode.Classroom`로 호출. 호스트/조인자 역할·광고/검색 흐름 동일.
5. **홈/내비**: 홈에 "교실 모드" 버튼 + `classroom-pairing` 라우트 + `draw/Classroom`.
6. **DrawingScreen 분기**: Classroom→star 전송 매핑, `CanvasRouting.PerPeer` 포함, **신규 `ClassroomCanvasArea`**. 기존 `PartyCanvasArea`·`SyncStep.PartyPicker`는 무접촉. 교실 조인자 "가져오기"=`Frame.SnapshotReq(target=host)`→기존 `respondToSnapshotRequest` 응답.
   - **호스트**: 캔버스(전체) + **"참여자 보기" 버튼** → 모달(`AlertDialog`/`ModalBottomSheet`)에서 `remotePeers` 이름 목록 표시 → 선택 시 그 조인자의 `MiniCanvas(peerCanvases[selected])`를 모달에 라이브 표시(한 명씩). 상시 미니뷰 없음.
   - **조인자**: 내 캔버스 + 호스트 `MiniCanvas(peerCanvases[host])` + "가져오기" 버튼. 호스트 식별 = `remotePeers.first()`(조인자는 호스트와만 연결 + 멤버십 미수신 → remotePeers=[host]).

### 재사용 (그대로)
스타 전송·핸드셰이크·`peerCanvases`/`CanvasRouting.PerPeer`·`MiniCanvas`·스냅샷 pull(`respondToSnapshotRequest`/`IncomingSnapshotEvent`)·`remotePeers`·PartyStart 지각 입장.

## 5. 회귀 방지 원칙

공유 파일(`SessionManager`, `NearbyTransport`, `PartyPairingScreen`, `DrawingScreen`, `HomeScreen`, `AppNavGraph`)을 건드리되, **모든 변경은 "Classroom 분기 추가" 또는 "Party→isStar 확장"**이어야 하고 **Party 단독 경로 동작은 바이트 단위로 보존**한다. mesh 가시성 코드(Party 전용)는 절대 미변경.

## 6. 미해결 / 후속 (모임 모드와 동일 한계)

1. ~~**조인자 호스트뷰 초기 채움**~~ ✅ 해결 — 새 조인자 합류 시 호스트가 strokes-only 스냅샷을 그 조인자에게 unicast(`sendHostStrokesToJoiner`, target="" → 받는 쪽 `peerCanvases[host]` 에 적용)해 합류 전 호스트 그림까지 방장 라이브뷰에 표시. 사진은 비공개라 미포함.
2. **호스트 사진 배경 라이브 표시** — 사진은 자동 broadcast 안 함. 라이브 호스트뷰엔 stroke/스티커/텍스트만, 사진은 "가져오기" 시 동반(라이브 사진 스트리밍은 후속).
3. ~~**pull 후 호스트의 조인자뷰 staleness**~~ ✅ 해결 — 조인자가 가져오기를 적용한 직후(strokes+사진 모두 도착 시) 자기 캔버스를 호스트에게 다시 송신(`broadcastMyCanvasAsPeer`, 스타 구조상 호스트에게만)해 호스트의 조인자 라이브뷰를 갱신.

## 7. 검증

- 빌드/유닛: `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest` + `assembleDebug`.
- **실기 필수(물리 기기 2~4대, 에뮬레이터 불가 — Nearby + Play Services)**:
  - 교실 모드 기기끼리만 발견/연결(모임·함께 모드 기기는 안 보임).
  - 호스트 그림 → 전 조인자 호스트뷰 라이브 반영.
  - 조인자 A가 그려도 조인자 B 화면엔 안 보임(이름도 안 보임). 호스트는 이름 눌러 A 라이브 확인.
  - 조인자 "가져오기" → 호스트 현재 내용(사진 포함) 자기 캔버스에 적용.
  - **회귀**: 모임 모드(Party)가 기존과 100% 동일(모두-미니뷰, PartyPicker 동기화). 함께(Duo)·싱글 무영향.

## 8. 작업 단계 (구현 순서)

한 번에 다 하지 않고, **각 단계가 독립 빌드·1커밋·리뷰 가능**하도록 나눈다. 앞 단계는 보이지 않는 배관(회귀 위험 최소), 뒤로 갈수록 화면이 붙는다. 실기 테스트는 3단계부터 의미 있음.

- **1단계 — 전송·세션 기반 (보이지 않음)**: `TransportMode.Classroom`(고유 serviceId, P2P_STAR) + `isStar` 헬퍼. 스타 공통(호스트 4인 제한·광고 유지·PartyStart 지각입장)을 `isStar`로 확장(`announcePeerJoined`는 Party 전용 유지). `SessionManager.classroomInstance` 싱글톤. → 컴파일 OK, Party/Duo/싱글 무변화, 교실 진입 불가. 검증: 빌드.
- **2단계 — 페어링 재사용**: `PartyPairingScreen`에 `mode` 파라미터(기본 Party) → 기존 호출 무변경. 검증: 빌드.
- **3단계 — 진입점 + 최소 캔버스**: `DrawMode.Classroom`, 홈 "교실 모드" 버튼, `classroom-pairing` 라우트, `draw/Classroom`. `DrawingScreen` Classroom 분기(전송 매핑·PerPeer) + 최소 `ClassroomCanvasArea`(자기 캔버스만, 보조 패널 placeholder). 검증: 실기 — 교실끼리만 발견·연결·자기 그리기.
- **4단계 — 조인자 UI**: `ClassroomCanvasArea` 조인자 분기 — 호스트 라이브 뷰 + "가져오기"(`SnapshotReq` target=host). 검증: 실기 — 호스트 라이브 보기·가져오기.
- **5단계 — 호스트 UI**: `ClassroomCanvasArea` 호스트 분기 — "참여자 보기" 버튼 → 모달 이름 목록 → 선택 1명 라이브. 검증: 실기 — 호스트가 조인자 라이브 보기 + 조인자끼리 안 보임 + 모임 모드 회귀.

## 부록: 모임 모드와의 차이

| 항목 | 모임 모드 (Party, 기존) | 교실 모드 (Classroom, 신규) |
|---|---|---|
| 토폴로지 | 모두가 모두를 봄(mesh형) | 호스트 중심(교사–학생) |
| 조인자끼리 | 서로 그림·이름 보임 | 서로 안 보임 |
| 호스트가 조인자 보기 | 항상 켜진 3 미니뷰 | "참여자 보기" 버튼 → 모달에서 이름 선택 → 1명 라이브 |
| 조인자가 보는 것 | 모든 참가자 미니뷰 | 호스트만 |
| 동기화(가져오기) | 아무 참가자나 선택(PartyPicker) | 호스트 고정 |
| 전송 | P2P_STAR | P2P_STAR (별도 serviceId) |
