package net.bryanhogan.compass.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val NeedleRed = Color(0xFFE53935)
private val DialDark = Color(0xFF10151D)
private val DialLight = Color(0xFFF4F6F9)

private val DarkColors = darkColorScheme(
    primary = NeedleRed,
    secondary = Color(0xFF80CBC4),
    background = DialDark,
    surface = Color(0xFF1B2836)
)

private val LightColors = lightColorScheme(
    primary = NeedleRed,
    secondary = Color(0xFF00695C),
    background = DialLight,
    surface = Color.White
)

@Composable
fun CompassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CompassTypography,
        content = content
    )
}
