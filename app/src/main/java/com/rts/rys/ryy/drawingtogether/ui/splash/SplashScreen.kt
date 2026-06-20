package com.rts.rys.ryy.drawingtogether.ui.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rts.rys.ryy.drawingtogether.ui.theme.EnglishFontFamily
import com.rts.rys.ryy.drawingtogether.ui.theme.PastelBlobBackground
import kotlinx.coroutines.delay

private const val SPLASH_DELAY_MS = 1200L

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        delay(SPLASH_DELAY_MS)
        onFinished()
    }

    Box(modifier = modifier.fillMaxSize()) {
        PastelBlobBackground()
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "DrawingTogether",
                    style = MaterialTheme.typography.headlineLarge.copy(fontFamily = EnglishFontFamily),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "함께 그리는 시간",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
