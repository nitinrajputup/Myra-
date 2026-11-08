package com.myra.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MyraBlack = Color(0xFF000000)
val MyraPurple = Color(0xFF8B5CF6)
val MyraBlue = Color(0xFF3B82F6)
val MyraCard = Color(0x22FFFFFF)

fun accentFor(name: String): Color = when (name) {
    "Blue" -> MyraBlue
    "Cyan" -> Color(0xFF22D3EE)
    "Pink" -> Color(0xFFEC4899)
    else -> MyraPurple
}

@Composable
fun MyraTheme(accent: Color = MyraPurple, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            secondary = MyraBlue,
            background = MyraBlack,
            surface = Color(0xFF0B0B12),
            onBackground = Color.White,
            onSurface = Color.White,
            onPrimary = Color.White
        ),
        content = content
    )
}
