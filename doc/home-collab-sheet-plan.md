# 홈 화면 재구성 — "같이 그리기" 통합 + 바텀시트

> 2026-07. 홈 버튼이 많아져(7개) 협업 모드 4개를 하나로 묶는 UI 개선. 사용자 요청.

## 목표
홈 버튼 7개(싱글·함께·모임·교실·나눠그리기·최근작업·타임랩스) → **4개**로 정리:
- **싱글모드** (그대로, primary)
- **같이 그리기** (신규 통합 버튼, secondary) — 부제 "함께 · 모임 · 교실 · 나눠 그리기"
- **최근 작업** (그대로)
- **타임랩스** (그대로)

"같이 그리기" 탭 → **바텀시트** "같이 그릴 방식을 골라요"에 4개 모드 카드(함께/모임/교실/나눠 그리기).
각 카드 = 기존 제목·부제·색 그대로. 카드 탭 → 시트 닫고 해당 모드로 이동.

## UI
- 바텀시트는 기존 `RecentWorksModal` 커스텀 Box 오버레이 패턴 재사용 — Material3 `ModalBottomSheet`가
  가로 모드에서 우측 여백을 남기는 문제 때문에 이 앱은 이미 Box 오버레이 시트를 씀(가로 풀폭 +
  드래그/백드롭/뒤로가기 닫기). 같은 방식으로 `CollabModeSheet`.
- 4개 카드는 현재 각 모드 버튼의 색/문구 이식(함께=secondary, 모임=secondaryContainer,
  교실=tertiaryContainer, 나눠=secondaryContainer).
- 가로/세로 대응(시트 내부 스크롤 가능).

## 구현 범위
- `ui/home/HomeScreen.kt`(핵심): `modeButtons`에서 함께/모임/교실/나눠그리기 4버튼 → "같이 그리기"
  1개로 교체(`onClick = { collabSheetOpen = true }`). `collabSheetOpen` 상태 + 시트 렌더 블록 추가.
- `ui/home/CollabModeSheet.kt`(신규): 바텀시트 오버레이 + 4개 카드. 각 카드가
  `onDuoMode/onPartyMode/onClassroomMode/onSplitMode` 호출 후 시트 닫기.
- HomeScreen 시그니처(콜백 4개)·`AppNavGraph`는 변경 없음 — 라우팅 그대로.

## 결정
- "같이 그리기" 버튼 색 = secondary(민트) — 협업 대표 CTA.
- 시트 상태는 모드 선택 시 닫힘, 뒤로가기 홈 복귀 시 닫힌 상태(단순 remember).

## 검증
- `assembleDebug` + `lintDebug`.
- 에뮬/기기: 홈 4버튼 → "같이 그리기" 탭 → 시트 4개 → 각 모드 정상 이동 → 뒤로가기/백드롭 닫힘 →
  가로 정상. 실제 연결은 기존과 동일(회귀 없음).
