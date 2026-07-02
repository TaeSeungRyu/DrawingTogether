package com.rts.rys.ryy.drawingtogether.ui.home

import android.app.Activity
import android.content.res.Configuration
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.session.SessionManager
import com.rts.rys.ryy.drawingtogether.transport.nearby.TransportMode
import com.rts.rys.ryy.drawingtogether.ui.theme.PastelBlobBackground
import com.rts.rys.ryy.drawingtogether.works.WorkStore

// 넓은 화면(태블릿/가로)에서 버튼 영역이 끝까지 늘어나지 않도록 하는 최대 폭. 이보다 좁은 화면
// (대부분의 폰 세로)에선 발동하지 않아 기존과 동일.
private val MAX_CONTENT_WIDTH = 480.dp

@Composable
fun HomeScreen(
    onSingleMode: () -> Unit,
    onDuoMode: () -> Unit,
    onPartyMode: () -> Unit,
    onClassroomMode: () -> Unit,
    onSplitMode: () -> Unit = {},
    onWorkClick: (String) -> Unit,
    onTimelapses: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { WorkStore.get(context) }
    val works by store.works.collectAsState()
    var modalOpen by rememberSaveable { mutableStateOf(false) }
    // modal 의 LazyGrid 스크롤 위치 — HomeScreen 에서 hoist. modal close → preview → 뒤로가기로
    // modal 재오픈해도 그대로. rememberLazyGridState 가 내부적으로 rememberSaveable 사용해
    // HomeScreen 의 NavBackStackEntry 에 보존.
    val recentWorksGridState = rememberLazyGridState()

    // 닉네임 — Duo 인스턴스의 prefs 가 모든 모드 공유. 변경 시 Party 인스턴스에도 setNick
    // 호출해 in-memory StateFlow 까지 동기화 (안 하면 Party 진입 시 이전 값 노출).
    val duoSession = remember { SessionManager.get(context, TransportMode.Duo) }
    val nick by duoSession.nick.collectAsState()
    var nickDialogOpen by rememberSaveable { mutableStateOf(false) }

    // 홈 화면에서 뒤로가기: 첫 번째는 안내 토스트, 2초 안에 두 번째 누르면 종료.
    var lastBackPressMs by remember { mutableStateOf(0L) }
    BackHandler {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPressMs < 2_000L) {
            (context as? Activity)?.finish()
        } else {
            lastBackPressMs = now
            Toast.makeText(context, "한 번 더 누르면 앱을 종료해요", Toast.LENGTH_SHORT).show()
        }
    }

    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 모드 버튼 6개 — 세로/가로 배치에서 공용. 버튼 사이 간격은 부모 Column 의 verticalArrangement 가 처리
    // (여기선 개별 Spacer 를 두지 않는다).
    val modeButtons: @Composable ColumnScope.() -> Unit = {
        // 싱글모드 — 코랄(primary). 가장 강한 CTA.
        Button(
            onClick = onSingleMode,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("싱글모드", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text("혼자 그리기", style = MaterialTheme.typography.bodySmall)
            }
        }
        // 함께 모드 — 민트(secondary). 1:1 공유 캔버스.
        Button(
            onClick = onDuoMode,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("함께 모드", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text("둘이서 한 캔버스에 같이 그리기", style = MaterialTheme.typography.bodySmall)
            }
        }
        // 모임 모드 — secondaryContainer. mesh(모두가 모두를 봄).
        Button(
            onClick = onPartyMode,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("모임 모드", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text("최대 4명, 각자 캔버스 + 미니 뷰", style = MaterialTheme.typography.bodySmall)
            }
        }
        // 교실 모드 — 호스트 중심(교사–학생).
        Button(
            onClick = onClassroomMode,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("교실 모드", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text("최대 10명, 방장 중심 (교사–학생)", style = MaterialTheme.typography.bodySmall)
            }
        }
        // 나눠 그리기 — mesh(모임 재사용), 레이아웃으로 나눠 각자 구역만 그림.
        Button(
            onClick = onSplitMode,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("나눠 그리기", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text("2~4명, 레이아웃 나눠 각자 구역 그리기", style = MaterialTheme.typography.bodySmall)
            }
        }
        // 최근 작업 — 라벤더(tertiary).
        Button(
            onClick = { modalOpen = true },
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("최근 작업", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (works.isEmpty()) "아직 저장된 작품이 없어요"
                           else "${works.size}개 저장됨",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        // 타임랩스 — 라벤더 컨테이너 톤.
        Button(
            onClick = onTimelapses,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("타임랩스", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text("그리는 과정 다시 보기", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PastelBlobBackground()
        // 제목 영역 + 버튼 영역 분리. 버튼 영역이 제목 아래 남은 공간을 모두 차지하므로 outer Column
        // 은 스크롤하지 않는다(스크롤은 세로에선 불필요, 가로에선 버튼 영역 안쪽에서만 처리).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Drawing Together",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "모드를 선택해 주세요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (nick.isBlank()) "내 이름을 정해주세요 ✏️" else "내 이름: $nick ✏️",
            style = MaterialTheme.typography.bodyMedium,
            color = if (nick.isBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable { nickDialogOpen = true }
                .padding(vertical = 4.dp, horizontal = 8.dp),
        )

        // 버튼 영역 — 제목 아래 남은 공간을 모두 차지. 폭은 MAX_CONTENT_WIDTH 로 제한해 넓은 화면
        // (태블릿/가로)에서 버튼이 끝까지 늘어나지 않고 가운데 모이게 한다(폰 세로에선 제한 미발동).
        //  - 세로: 균등 간격(SpaceEvenly) 으로 영역 전체에 고르게 분산.
        //  - 가로: 높이가 부족하므로 안쪽에서 스크롤 + 고정 간격.
        if (isLandscape) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = modeButtons,
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = MAX_CONTENT_WIDTH),
                verticalArrangement = Arrangement.SpaceEvenly,
                content = modeButtons,
            )
        }
        }

        if (modalOpen) {
            RecentWorksModal(
                works = works,
                // modalOpen 은 그대로 두고 nav.navigate 만. PreviewScreen 진입 시 HomeScreen 이
                // backstack 으로 가면서 RecentWorksModal 컴포저블이 자동 dispose 되어 sheet 가
                // 사라지고, 뒤로가기로 돌아오면 modalOpen=true 상태가 rememberSaveable 에 보존돼
                // 모달이 자동 재표시.
                onWorkClick = onWorkClick,
                onDismiss = { modalOpen = false },
                gridState = recentWorksGridState,
            )
        }

        if (nickDialogOpen) {
            var input by rememberSaveable(nickDialogOpen) { mutableStateOf(nick) }
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            val submit = submit@{
                val trimmed = input.trim()
                if (trimmed.isEmpty()) return@submit
                // Duo + Party + Classroom 인스턴스 모두 setNick — prefs 는 공유되지만 in-memory
                // StateFlow 는 인스턴스별 분리. 다음 진입 시 stale 값 회피.
                duoSession.setNick(trimmed)
                SessionManager.get(context, TransportMode.Party).setNick(trimmed)
                SessionManager.get(context, TransportMode.Classroom).setNick(trimmed)
                nickDialogOpen = false
            }
            AlertDialog(
                onDismissRequest = { nickDialogOpen = false },
                title = { Text("내 이름") },
                text = {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.take(20) },
                        singleLine = true,
                        label = { Text("이름") },
                        placeholder = { Text("예) ryu") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.focusRequester(focus),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { submit() },
                        enabled = input.trim().isNotEmpty(),
                    ) { Text("저장") }
                },
                dismissButton = {
                    TextButton(onClick = { nickDialogOpen = false }) { Text("취소") }
                },
            )
        }
    }
}
