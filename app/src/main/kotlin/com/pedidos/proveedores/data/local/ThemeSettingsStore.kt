package com.pedidos.proveedores.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val etiqueta: String) {
  SYSTEM("Automático"),
  DARK("Oscuro POS"),
  LIGHT("Claro limpio");

  fun next(): AppThemeMode = when (this) {
    SYSTEM -> DARK
    DARK -> LIGHT
    LIGHT -> SYSTEM
  }

  companion object {
    fun fromPref(raw: String?): AppThemeMode =
      entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SYSTEM
  }
}

class ThemeSettingsStore(context: Context) {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _mode = MutableStateFlow(AppThemeMode.fromPref(prefs.getString(KEY_THEME_MODE, null)))
  val mode: StateFlow<AppThemeMode> = _mode.asStateFlow()

  fun setMode(mode: AppThemeMode) {
    prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    _mode.value = mode
  }

  fun cycleMode(): AppThemeMode {
    val next = _mode.value.next()
    setMode(next)
    return next
  }

  companion object {
    private const val PREFS_NAME = "pedidos_theme_settings"
    private const val KEY_THEME_MODE = "app_theme_mode"
  }
}
