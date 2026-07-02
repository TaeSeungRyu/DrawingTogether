package com.rts.rys.ryy.drawingtogether.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// "함께 그리기" 히어로 버튼 — 코랄→라벤더 그라데이션 + 👥 이모지 + 흰 글씨. 협업 대표 CTA.
// 파스텔 원색은 흰 글씨 대비가 약해, 흰 글씨가 또렷하도록 살짝 진한 톤의 코랄→퍼플 그라데이션을 쓴다.
@Composable
fun CollabHeroGradient(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.extraLarge
    val brush = Brush.horizontalGradient(
        listOf(Color(0xFFFF7A97), Color(0xFF9B86F5)),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(shape)
            .background(brush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("👥", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "함께 그리기",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "함께 · 모임 · 교실 · 나눠 그리기",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }
    }
}
