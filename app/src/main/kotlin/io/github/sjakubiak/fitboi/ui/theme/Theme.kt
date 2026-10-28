package io.github.sjakubiak.fitboi.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Fixed accent colors per health area, used for rings and charts in both themes. */
object HealthColors {
    val Steps = Color(0xFF1A73E8)
    val ActiveZone = Color(0xFF00A383)
    val Calories = Color(0xFFF29900)
    val Distance = Color(0xFF12B5CB)
    val Sleep = Color(0xFF6E5DE7)
    val Heart = Color(0xFFE8453C)
    val Hrv = Color(0xFFD01884)
    val Readiness = Color(0xFF1E8E3E)

    val Deep = Color(0xFF3C2CB4)
    val Light = Color(0xFF8A7CF0)
    val Rem = Color(0xFF4FB3F6)
    val Awake = Color(0xFFF28B82)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),
    secondary = Color(0xFF00A383),
    background = Color(0xFFF8F9FC),
    surface = Color(0xFFF8F9FC),
    surfaceContainer = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFF5BD5B5),
)

private val FitboiTypography = Typography().run {
    copy(
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Medium),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Medium),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun FitboiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = FitboiTypography, content = content)
}
