package com.rts.rys.ryy.drawingtogether.ui.pairing

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rts.rys.ryy.drawingtogether.session.SessionManager
import com.rts.rys.ryy.drawingtogether.session.SessionState
import com.rts.rys.ryy.drawingtogether.transport.TransportState
import com.rts.rys.ryy.drawingtogether.transport.nearby.TransportMode
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

// Phase 4-D: 모임 모드 (1:N) 페어링.
// - 호스트/조인 명시적 선택 — Duo 와 달리 자동 추정 불가 (호스트는 다중 accept 흐름).
// - 호스트: 광고 켜고 조인자들 토큰 컨펌 → 1명 이상 모이면 "그리기 시작" 버튼 활성화.
// - 조인자: 호스트 발견 카드 탭 → 토큰 컨펌 → 즉시 onStart() (1:1 과 동일).
//
// 4-D 시점 한계 (4-H 에서 정교화):
// - 호스트 최대 3명 reject 정책 미구현
// - "그리기 시작" 누른 뒤 추가 조인자가 들어오는 케이스 미처리
private enum class PartyRole { Host, Joiner, NotPicked }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyPairingScreen(
    onBack: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    // 스타(1:N) 페어링 공용 화면. 모임(Party)·교실(Classroom) 둘 다 이걸 쓴다.
    // 기본값 Party 라 기존 호출부(AppNavGraph 의 모임 모드)는 변경 없이 동작.
    mode: TransportMode = TransportMode.Party,
) {
    val context = LocalContext.current
    val session = remember(mode) { SessionManager.get(context, mode) }
    val modeLabel = if (mode == TransportMode.Classroom) "교실" else "모임"
    val scope = rememberCoroutineScope()

    val sessionState by session.state.collectAsState()
    val transportState by session.transport.state.collectAsState()
    val discovered by session.transport.discovered.collectAsState()
    val connectedPeers by session.transport.connectedPeers.collectAsState()
    val pending by session.transport.pending.collectAsState()
    val nick by session.nick.collectAsState()

    var permissionsGranted by remember { mutableStateOf(NearbyPermissions.allGranted(context)) }
    var permissionDenied by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf(PartyRole.NotPicked) }

    val requestPermissions = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val ok = result.values.all { it }
        permissionsGranted = ok
        permissionDenied = !ok
    }

    LaunchedEffect(Unit) {
        session.enterPairing()
        if (!NearbyPermissions.allGranted(context)) {
            requestPermissions.launch(NearbyPermissions.required())
        } else {
            permissionsGranted = true
        }
    }

    // 역할 선택 + 권한 OK 시 광고/검색 시작.
    LaunchedEffect(role, permissionsGranted) {
        if (!permissionsGranted) return@LaunchedEffect
        when (role) {
            PartyRole.Host -> session.transport.startAdvertising()
            PartyRole.Joiner -> session.transport.startDiscovery()
            PartyRole.NotPicked -> Unit
        }
    }

    // Phase 4-H: 조인자는 핸드셰이크 완료만으론 페어링 화면에 머무름. 호스트가 "그리기 시작"
    // 누르고 PartyStart 신호를 broadcast 하면 그 때 함께 진입 → 비대칭 UX 해결.
    LaunchedEffect(role) {
        if (role == PartyRole.Joiner) {
            session.partyStart.collect { onStart() }
        }
    }

    // 실패 토스트 — drop(1) 로 진입 시 초기값(이전 화면의 stale Failed) 무시.
    LaunchedEffect(Unit) {
        session.state
            .drop(1)
            .collect { s ->
                if (s is SessionState.Failed) {
                    Toast.makeText(context, "연결 실패: ${s.reason}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("$modeLabel 모드") },
            navigationIcon = {
                IconButton(onClick = {
                    session.disconnect()
                    onBack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로",
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = nick,
                onValueChange = { session.setNick(it) },
                label = { Text("내 이름") },
                placeholder = { Text("예) ryu") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (role) {
                PartyRole.NotPicked -> RolePicker(
                    enabled = permissionsGranted && nick.isNotBlank(),
                    modeLabel = modeLabel,
                    maxJoiners = mode.maxJoiners,
                    onHost = { role = PartyRole.Host },
                    onJoiner = { role = PartyRole.Joiner },
                )
                PartyRole.Host -> HostBody(
                    connectedCount = connectedPeers.size,
                    connectedNicks = connectedPeers.map { it.nick },
                    canStart = connectedPeers.isNotEmpty(),
                    onStartClick = {
                        // 새 조인자 더 안 받기 + 기존 조인자들에게 PartyStart 신호 → 동기 진입.
                        // broadcastPartyStart 가 partyStarted 플래그도 박아둠 — 그 후 "방 열기"
                        // 로 들어오는 새 조인자도 자동으로 PartyStart unicast 받는다.
                        session.transport.stopAdvertising()
                        session.broadcastPartyStart()
                        onStart()
                    },
                )
                PartyRole.Joiner -> JoinerBody(
                    permissionsGranted = permissionsGranted,
                    permissionDenied = permissionDenied,
                    transportState = transportState,
                    discoveredNicks = discovered,
                    waitingForHostStart = sessionState is SessionState.Connected,
                    onRetryPermission = { requestPermissions.launch(NearbyPermissions.required()) },
                    onPickPeer = { endpointId ->
                        scope.launch { session.transport.requestConnection(endpointId) }
                    },
                )
            }
        }
    }

    // 연결 요청 ~ 인증 토큰 다이얼로그 도착 사이 로딩. 호스트는 onConnectionInitiated 가
    // 외부 요청으로 트리거되니 항상 안 뜨고, 조인자만 본다.
    val p = pending
    val connecting = transportState
    if (p == null && connecting is TransportState.Connecting) {
        Dialog(
            onDismissRequest = { /* 차단 */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "연결 중...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "잠시만 기다려 주세요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // 인증 토큰 다이얼로그 — 호스트는 조인자마다 토큰 컨펌 반복.
    if (p != null) {
        AlertDialog(
            onDismissRequest = {
                scope.launch { session.transport.rejectPending() }
            },
            title = { Text("${p.remoteNick}과 연결할까요?") },
            text = {
                Column {
                    Text("양쪽 기기에 같은 토큰이 보이면 안전한 연결입니다.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = p.token,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { session.transport.acceptPending() }
                }) { Text("연결") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { session.transport.rejectPending() }
                }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun ColumnScope.RolePicker(
    enabled: Boolean,
    modeLabel: String,
    maxJoiners: Int,
    onHost: () -> Unit,
    onJoiner: () -> Unit,
) {
    Text(
        text = "어느 역할로 참여할까요?",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = onHost,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("호스트 (내가 $modeLabel 시작)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(2.dp))
            Text("최대 ${maxJoiners}명까지 받기", style = MaterialTheme.typography.bodySmall)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = onJoiner,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("조인 (다른 $modeLabel 참여)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(2.dp))
            Text("호스트 검색해서 들어가기", style = MaterialTheme.typography.bodySmall)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "💡 $modeLabel 내 1대는 반드시 호스트여야 합니다. 나머지는 조인.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ColumnScope.HostBody(
    connectedCount: Int,
    connectedNicks: List<String>,
    canStart: Boolean,
    onStartClick: () -> Unit,
) {
    Text(
        text = "내가 호스트 — 조인자 기다리는 중 ($connectedCount/3)",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp),
    ) {
        if (connectedNicks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "다른 기기에서 \"조인\"을 누르고\n내 카드를 탭하면 토큰이 떠요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                connectedNicks.forEach { nick ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "🟢  $nick",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    Button(
        onClick = onStartClick,
        enabled = canStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(
            text = if (canStart) "그리기 시작 (${connectedCount}명)" else "조인자 1명 이상 기다려요",
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun ColumnScope.JoinerBody(
    permissionsGranted: Boolean,
    permissionDenied: Boolean,
    transportState: TransportState,
    discoveredNicks: List<com.rts.rys.ryy.drawingtogether.transport.DiscoveredPeer>,
    waitingForHostStart: Boolean,
    onRetryPermission: () -> Unit,
    onPickPeer: (endpointId: String) -> Unit,
) {
    Text(
        text = if (waitingForHostStart) "조인 — 호스트가 시작하기를 기다리는 중"
               else "조인 — 주변 호스트 검색 중",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp),
    ) {
        when {
            !permissionsGranted -> PermissionNotice(
                denied = permissionDenied,
                onRetry = onRetryPermission,
            )
            waitingForHostStart -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "연결됐어요!\n호스트가 \"그리기 시작\"을 누르면 함께 진입해요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            discoveredNicks.isEmpty() -> EmptyDiscoveryHint(transportState)
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                discoveredNicks.forEach { peer ->
                    Card(
                        onClick = { onPickPeer(peer.endpointId) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "🟦  ${peer.nick}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
