package com.rts.rys.ryy.drawingtogether.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 상단: 타이틀 + 모드 버튼. 자연스러운 높이로 stack.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "DrawingTogether",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "모드를 선택해 주세요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onSingleMode,
                modifier = Modifier.fillMaxWidth().height(88.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("싱글모드", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("혼자 그리기", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedButton(
                onClick = onMultiMode,
                modifier = Modifier.fillMaxWidth().height(88.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("멀티모드", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("가까운 기기와 함께 그리기", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 중간 빈 공간 — 모드 버튼과 최근 작업 사이를 자동으로 벌림.
        // 결과: 최근 작업 섹션이 화면 하단에 붙음. 높이는 자체 콘텐츠(~130dp) 기준 ≈ 화면 20%.
        Spacer(modifier = Modifier.weight(0.5f))

        if (works.isNotEmpty()) {
            RecentWorksRow(
                works = works,
                onWorkClick = onWorkClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }
}
