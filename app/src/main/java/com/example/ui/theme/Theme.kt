package com.example.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CharcoalPrimary,
    onPrimary = Color.White,
    primaryContainer = CharcoalPrimaryContainer,
    onPrimaryContainer = OnCharcoalPrimaryContainer,
    secondary = SlateGreySecondary,
    onSecondary = Color.White,
    secondaryContainer = SlateGreyContainer,
    onSecondaryContainer = CharcoalInk,
    tertiary = ActiveSlate,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6E8EC),
    onTertiaryContainer = CharcoalInk,
    background = LightBackground,
    onBackground = CharcoalInk,
    surface = LightSurface,
    onSurface = CharcoalInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = MutedGrey,
    outline = LightOutline,
    error = AlertCharcoalRed,
    onError = Color.White,
    errorContainer = AlertContainerLight,
    onErrorContainer = Color(0xFF4A1515),
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimaryWhite,
    onPrimary = DarkOnPrimaryCharcoal,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondarySilver,
    onSecondary = DarkOnPrimaryCharcoal,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnSurface,
    tertiary = DarkSecondarySilver,
    onTertiary = DarkOnPrimaryCharcoal,
    tertiaryContainer = DarkPrimaryContainer,
    onTertiaryContainer = DarkOnSurface,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = AlertCharcoalRedDark,
    onError = Color(0xFF3A1212),
    errorContainer = AlertContainerDark,
    onErrorContainer = Color(0xFFF6D5D5),
)

private val ChambersShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun DilawarSaqlainTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ChambersShapes,
        content = content,
    )
}

object ThemePreferenceStore {
    private const val PREFS_NAME = "dilawar_saqlain_theme_prefs"
    private const val KEY_IS_DARK = "is_dark_mode"

    fun hasSavedPreference(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).contains(KEY_IS_DARK)

    fun isDarkMode(context: Context, fallbackSystemDark: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_IS_DARK)) {
            prefs.getBoolean(KEY_IS_DARK, fallbackSystemDark)
        } else {
            fallbackSystemDark
        }
    }

    fun setDarkMode(context: Context, isDark: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_DARK, isDark)
            .apply()
    }
}
