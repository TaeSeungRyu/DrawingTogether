package com.rts.rys.ryy.drawingtogether.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.rts.rys.ryy.drawingtogether.R

// 기본 폰트: 한국어 normal.ttf / bold.ttf. 영어 글리프는 이 폰트에 포함된 Latin이 사용됨.
// Compose는 같은 weight 중 첫 폰트만 선택하므로, 영어 전용 폰트를 같은 family에 섞으면
// 한국어 폰트가 선택되지 않는다. 영어 전용 디자인이 필요한 자리에서만 EnglishFontFamily를 직접 지정.
val AppFontFamily = FontFamily(
    Font(resId = R.font.normal, weight = FontWeight.Normal),
    Font(resId = R.font.bold, weight = FontWeight.Bold),
)

val EnglishFontFamily = FontFamily(
    Font(resId = R.font.english, weight = FontWeight.Normal),
    Font(resId = R.font.english, weight = FontWeight.Bold),
)

private val Default = Typography()

val Typography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = AppFontFamily),
    displayMedium = Default.displayMedium.copy(fontFamily = AppFontFamily),
    displaySmall = Default.displaySmall.copy(fontFamily = AppFontFamily),
    headlineLarge = Default.headlineLarge.copy(fontFamily = AppFontFamily),
    headlineMedium = Default.headlineMedium.copy(fontFamily = AppFontFamily),
    headlineSmall = Default.headlineSmall.copy(fontFamily = AppFontFamily),
    titleLarge = Default.titleLarge.copy(fontFamily = AppFontFamily),
    titleMedium = Default.titleMedium.copy(fontFamily = AppFontFamily),
    titleSmall = Default.titleSmall.copy(fontFamily = AppFontFamily),
    bodyLarge = Default.bodyLarge.copy(fontFamily = AppFontFamily),
    bodyMedium = Default.bodyMedium.copy(fontFamily = AppFontFamily),
    bodySmall = Default.bodySmall.copy(fontFamily = AppFontFamily),
    labelLarge = Default.labelLarge.copy(fontFamily = AppFontFamily),
    labelMedium = Default.labelMedium.copy(fontFamily = AppFontFamily),
    labelSmall = Default.labelSmall.copy(fontFamily = AppFontFamily),
)
