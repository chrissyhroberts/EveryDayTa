package com.everydayta.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object DayStyle {
    val canvas = Color(0xFFF6F7F3)
    val surface = Color.White
    val ink = Color(0xFF172A27)
    val subdued = Color(0xFF687A75)
    val teal = Color(0xFF176B60)
    val paleTeal = Color(0xFFE4F1EC)
    val outline = Color(0xFFDEE7E1)
    val error = Color(0xFFB3261E)
}

private val dayColors = lightColorScheme(
    primary = DayStyle.teal,
    onPrimary = Color.White,
    primaryContainer = DayStyle.paleTeal,
    onPrimaryContainer = DayStyle.ink,
    secondary = Color(0xFF58756B),
    background = DayStyle.canvas,
    onBackground = DayStyle.ink,
    surface = DayStyle.surface,
    onSurface = DayStyle.ink,
    surfaceVariant = Color(0xFFF0F4F0),
    onSurfaceVariant = DayStyle.subdued,
    outline = DayStyle.outline,
    error = DayStyle.error
)

@Composable
fun EveryDayTaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val accessibility = remember(context) { AccessibilityPreferences(context) }
    val systemDensity = LocalDensity.current
    val adjustedDensity = remember(systemDensity, accessibility.textScale) {
        Density(systemDensity.density, systemDensity.fontScale * accessibility.textScale)
    }
    SideEffect { Palettes.useAccessibleGlobally = accessibility.preferAccessiblePalette }
    CompositionLocalProvider(
        LocalAccessibilityPreferences provides accessibility,
        LocalDensity provides adjustedDensity
    ) {
        MaterialTheme(colorScheme = dayColors, typography = Typography(), content = content)
    }
}

@Composable
fun DayPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DayStyle.surface),
        border = BorderStroke(1.dp, DayStyle.outline),
        content = content
    )
}
