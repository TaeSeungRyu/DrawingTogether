package com.rts.rys.ryy.drawingtogether.ui.pairing

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    onBack: () -> Unit,
    onConnected: () -> Unit,
    autoStartAsHost: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val session = remember { SessionManager.get(context) }
    val scope = rememberCoroutineScope()

    val sessionState by session.state.collectAsState()
    val transportState by session.transport.state.collectAsState()
    val discovered by session.transport.discovered.collectAsState()
    val pending by session.transport.pending.collectAsState()
    val nick by session.nick.collectAsState()

    var permissionsGranted by remember { mutableStateOf(NearbyPermissions.allGranted(context)) }
    var permissionDenied by remember { mutableStateOf(false) }

    val requestPermissions = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val ok = result.values.all { it }
        permissionsGranted = ok
        permissionDenied = !ok
    }

    // 페어링 진입 — 세션 상태 초기화 + 권한 요청
    LaunchedEffect(Unit) {
        session.enterPairing()
        if (!NearbyPermissions.allGranted(context)) {
            requestPermissions.launch(NearbyPermissions.required())
        } else {
            permissionsGranted = true
        }
    }

    // 권한 OK 되면 자동 시작 — 기본은 discovery, 재연결 시(autoStartAsHost=true) 는 100ms 후
    // 자동 advertising. 100ms 지연은 disconnect 직후 transport.stop() 의 onDisconnected 콜백
    // 사이클이 끝날 시간을 보장 (state 가 stale Idle/Failed 였다 곧 정리되는 케이스 회피).
    LaunchedEffect(permissionsGranted, autoStartAsHost) {
        if (!permissionsGranted) return@LaunchedEffect
        if (autoStartAsHost) {
            kotlinx.coroutines.delay(100)
            val s = session.transport.state.value
            if (s !is TransportState.Advertising && s !is TransportState.Connected) {
                session.transport.startAdvertising()
            }
        } else if (session.transport.state.value == TransportState.Idle) {
            session.transport.startDiscovery()
        }
    }

    // 연결 성립 → draw로 이동
    LaunchedEffect(sessionState) {
        if (sessionState is SessionState.Connected) {
            onConnected()
        }
    }

    // 실패 시 토스트만 — 사용자는 다시 시도 가능.
    // drop(1) 로 StateFlow 의 초기값(이전 Draw 화면에서 끊긴 채 남은 Failed 등) 은 무시하고
    // PairingScreen 진입 후의 새 전이만 토스트. "재연결" 직후 자동 토스트 방지.
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
            title = { Text("함께 모드") },
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

            val isAdvertising = transportState is TransportState.Advertising
            Button(
                onClick = {
                    scope.launch {
                        if (isAdvertising) {
                            session.transport.startDiscovery()
                        } else {
                            session.transport.startAdvertising()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = permissionsGranted && nick.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAdvertising)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(if (isAdvertising) "광고 중 — 탭하면 검색으로" else "호스트로 광고시작")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 핵심 안내 — 비대칭(한 대만 호스트) 규칙을 항상 노출.
            Text(
                text = if (isAdvertising)
                    "💡 이제 상대 기기 화면에서 내 이름 카드가 보일 거예요.\n   상대가 탭하면 양쪽에 인증 토큰이 뜹니다."
                else
                    "💡 두 기기 중 1대만 위 버튼을 누르세요! \n" +
                            "    꼭 1대만 눌러야 합니다! \n    다른 1대는 그대로 두면 아래에 카드가 나타납니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isAdvertising) "내가 호스트 — 상대가 탭하기를 기다리는 중" else "주변에서 발견된 기기",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // verticalScroll 안이라 weight 대신 최소 높이. 발견 리스트는 소수라 LazyColumn 대신
            // 일반 Column(중첩 스크롤 회피).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
            ) {
                when {
                    !permissionsGranted -> PermissionNotice(
                        denied = permissionDenied,
                        onRetry = { requestPermissions.launch(NearbyPermissions.required()) },
                    )
                    isAdvertising -> AdvertisingHint()
                    discovered.isEmpty() -> EmptyDiscoveryHint(transportState)
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        discovered.forEach { peer ->
                            DiscoveredCard(
                                nick = peer.nick,
                                onClick = {
                                    scope.launch {
                                        session.transport.requestConnection(peer.endpointId)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Text(
                text = "ⓘ Nearby가 BT/Wi-Fi를 자동 선택합니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }

    // 연결 요청 ~ 인증 토큰 다이얼로그 도착 사이 로딩.
    // requestConnection() 직후 state=Connecting 으로 전이되지만 onConnectionInitiated 가
    // 올 때까지 짧게는 수백 ms, BT 환경에 따라 수 초 비어있다 — 사용자가 "탭이 먹혔나?" 헷갈리지
    // 않게 차단형 로딩. acceptPending() 직후 onConnectionResult 사이도 같은 조건이라
    // 그대로 재사용됨.
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

    // 인증 토큰 다이얼로그
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
private fun DiscoveredCard(nick: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
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
                text = "🟦  $nick",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

// Phase 4-D: PartyPairingScreen 에서도 공유 — internal 로 노출.
@Composable
internal fun PermissionNotice(denied: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (denied)
                "권한이 거부되었어요. 함께 모드를 쓰려면 BT/Wi-Fi 권한이 필요합니다."
            else
                "권한을 요청 중입니다...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (denied) {
            Button(onClick = onRetry) { Text("다시 요청") }
        }
    }
}

@Composable
private fun AdvertisingHint() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "내가 호스트가 됐어요.\n상대 기기 화면에 내 이름이 곧 보일 거예요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
internal fun EmptyDiscoveryHint(state: TransportState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is TransportState.Discovering -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "검색 중...\n상대 기기에서 \"호스트로 광고시작\"을 누를 때까지 기다리는 중",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            is TransportState.Connecting -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text("연결 중...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                Text(
                    text = "아직 검색이 시작되지 않았어요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
