package com.rts.rys.ryy.drawingtogether.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// 파스텔 팝 = 밝은 톤 전용. 시스템 다크모드/Material You는 의도적으로 무시 — 어떤 환경에서도 동일한 개성.
private val CandyPopColorScheme = lightColorScheme(
    primary = CandyCoral,
    onPrimary = PureWhite,
    primaryContainer = CandyCoralContainer,
    onPrimaryContainer = CandyCoralDeep,

    secondary = CandyMint,
    onSecondary = WarmDarkText,
    secondaryContainer = CandyMintContainer,
    onSecondaryContainer = CandyMintDeep,

    tertiary = CandyLavender,
    onTertiary = WarmDarkText,
    tertiaryContainer = CandyLavenderContainer,
    onTertiaryContainer = CandyLavenderDeep,

    background = CreamBackground,
    onBackground = WarmDarkText,
    surface = CreamBackground,
    onSurface = WarmDarkText,
    surfaceVariant = PeachSurfaceVariant,
    onSurfaceVariant = WarmSubText,
    surfaceTint = CandyCoral,

    outline = PastelOutline,
    outlineVariant = PastelOutlineVariant,

    error = PastelError,
    onError = PureWhite,
    errorContainer = PastelErrorContainer,
    onErrorContainer = PastelErrorDeep,
)

@Composable
fun DrawingTogetherTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CandyPopColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
