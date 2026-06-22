package com.rts.rys.ryy.drawingtogether.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.ui.theme.PastelBlobBackground
import com.rts.rys.ryy.drawingtogether.works.WorkStore

@Composable
fun HomeScreen(
    onSingleMode: () -> Unit,
    onMultiMode: () -> Unit,
    onWorkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { WorkStore.get(context) }
    val works by store.works.collectAsState()
    var modalOpen by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        PastelBlobBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(64.dp))
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

        // 위 spacer가 버튼 그룹을 수직 중앙쪽으로 밀어냄. 아래 spacer와 비율이 같으면 정확한 중앙.
        Spacer(modifier = Modifier.weight(1f))

        // 싱글모드 — 코랄(primary). 가장 강한 CTA.
        Button(
            onClick = onSingleMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("싱글모드", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text("혼자 그리기", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 멀티모드 — 민트(secondary). 같은 위계지만 색으로 구분.
        Button(
            onClick = onMultiMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("멀티모드", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text("가까운 기기와 함께 그리기", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 최근 작업 — 라벤더(tertiary). 보조 액션이지만 같은 시각 무게.
        Button(
            onClick = { modalOpen = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
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
            Spacer(modifier = Modifier.weight(1f))
        }

        if (modalOpen) {
            RecentWorksModal(
                works = works,
                onWorkClick = onWorkClick,
                onDismiss = { modalOpen = false },
            )
        }
    }
}
