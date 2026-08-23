package com.unop2p.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// UNO card colors, reused across the game table.
val UnoRed = Color(0xFFD32F2F)
val UnoYellow = Color(0xFFF9A825)
val UnoGreen = Color(0xFF2E7D32)
val UnoBlue = Color(0xFF1565C0)
val UnoWild = Color(0xFF212121)

private val DarkColors = darkColorScheme(
    primary = UnoRed,
    secondary = UnoBlue,
    tertiary = UnoYellow,
    background = Color(0xFF0E1116),
    surface = Color(0xFF161B22),
    onPrimary = Color.White,
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
)

private val LightColors = lightColorScheme(
    primary = UnoRed,
    secondary = UnoBlue,
    tertiary = UnoYellow,
)

@Composable
fun UnoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
