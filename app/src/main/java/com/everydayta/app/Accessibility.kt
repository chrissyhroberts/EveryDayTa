package com.everydayta.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** App text scaling multiplies Android's own accessibility font scale. */
@Stable
class AccessibilityPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("accessibility", Context.MODE_PRIVATE)
    var textScale by mutableFloatStateOf(prefs.getFloat("text_scale", 1f).coerceIn(.85f, 1.6f))
        private set
    var preferAccessiblePalette by mutableStateOf(prefs.getBoolean("accessible_palette", false))
        private set

    fun updateTextScale(value: Float) {
        val v = value.coerceIn(.85f, 1.6f)
        textScale = v
        prefs.edit().putFloat("text_scale", v).apply()
    }
    fun setAccessiblePalette(value: Boolean) {
        preferAccessiblePalette = value
        prefs.edit().putBoolean("accessible_palette", value).apply()
        Palettes.useAccessibleGlobally = value
        Thread { GridWidget.refresh(context.applicationContext) }.start()
    }
}

val LocalAccessibilityPreferences = staticCompositionLocalOf<AccessibilityPreferences> {
    error("Accessibility settings must be provided by EveryDayTaTheme")
}
