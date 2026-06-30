package com.rts.rys.ryy.drawingtogether.ui.home

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.session.SessionManager
import com.rts.rys.ryy.drawingtogether.transport.nearby.TransportMode
import com.rts.rys.ryy.drawingtogether.ui.theme.PastelBlobBackground
import com.rts.rys.ryy.drawingtogether.works.WorkStore

@Composable
fun HomeScreen(
    onSingleMode: () -> Unit,
    onDuoMode: () -> Unit,
    onPartyMode: () -> Unit,
    onClassroomMode: () -> Unit,
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

    Box(modifier = modifier.fillMaxSize()) {
        PastelBlobBackground()
        // verticalScroll — 가로 모드처럼 높이가 부족하면 버튼들이 화면 밖으로 밀려 선택 안 되던
        // 문제 해결. 세로에서도 자연스럽게 위쪽 정렬 + 필요 시 스크롤.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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

        Spacer(modifier = Modifier.height(24.dp))

        // 싱글모드 — 코랄(primary). 가장 강한 CTA.
        Button(
            onClick = onSingleMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("싱글모드", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text("혼자 그리기", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 함께 모드 — 민트(secondary). 1:1 공유 캔버스.
        Button(
            onClick = onDuoMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
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

        Spacer(modifier = Modifier.height(12.dp))

        // 모임 모드 — secondary 의 컨테이너 톤으로 같은 계열이지만 위계 한 단계 낮춤.
        // 함께 모드와 같은 "연결" 군 이지만 좀 더 큰 규모.
        Button(
            onClick = onPartyMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
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

        Spacer(modifier = Modifier.height(12.dp))

        // 교실 모드 — 호스트 중심(교사–학생). 모임 모드와 같은 "연결" 군이지만 호스트만 전체를 봄.
        Button(
            onClick = onClassroomMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("교실 모드", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text("최대 4명, 방장 중심 (교사–학생)", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 최근 작업 — 라벤더(tertiary). 보조 액션이지만 같은 시각 무게.
        Button(
            onClick = { modalOpen = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
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

        Spacer(modifier = Modifier.height(12.dp))

        // 타임랩스 — 라벤더 컨테이너 톤. 그리기 과정 재생 갤러리.
        Button(
            onClick = onTimelapses,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
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
            Spacer(modifier = Modifier.height(32.dp))
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
