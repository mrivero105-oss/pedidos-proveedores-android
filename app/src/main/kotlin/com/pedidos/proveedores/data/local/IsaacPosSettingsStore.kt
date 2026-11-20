package com.pedidos.proveedores.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IsaacPosSettings(
  val supabaseUrl: String = DEFAULT_SUPABASE_URL,
  val publishableKey: String = "",
  val lowStockThreshold: Double = 5.0,
  val lastSyncMillis: Long? = null,
) {
  val isConfigured: Boolean
    get() = supabaseUrl.isNotBlank() && publishableKey.isNotBlank()

  companion object {
    const val DEFAULT_SUPABASE_URL = "https://puvqiipussyktfmgafgv.supabase.co"
  }
}

/**
 * Almacena la URL de Supabase y la clave publishable de solo lectura únicamente en las
 * preferencias privadas del teléfono del usuario. Nunca se incluye ninguna clave en el código fuente.
 */
class IsaacPosSettingsStore(context: Context) {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _settings = MutableStateFlow(load())
  val settings: StateFlow<IsaacPosSettings> = _settings.asStateFlow()

  private fun load(): IsaacPosSettings {
    val lastSync = prefs.getLong(KEY_LAST_SYNC, -1L).takeIf { it > 0L }
    return IsaacPosSettings(
      supabaseUrl = prefs.getString(KEY_URL, IsaacPosSettings.DEFAULT_SUPABASE_URL)
        ?.takeIf { it.isNotBlank() }
        ?: IsaacPosSettings.DEFAULT_SUPABASE_URL,
      publishableKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
      lowStockThreshold = prefs.getFloat(KEY_LOW_STOCK, 5f).toDouble(),
      lastSyncMillis = lastSync,
    )
  }

  fun save(supabaseUrl: String, publishableKey: String, lowStockThreshold: Double = 5.0) {
    val cleanUrl = supabaseUrl.trim().trimEnd('/').ifBlank { IsaacPosSettings.DEFAULT_SUPABASE_URL }
    val cleanKey = publishableKey.trim()
    val cleanThreshold = lowStockThreshold.coerceAtLeast(0.0)
    prefs.edit()
      .putString(KEY_URL, cleanUrl)
      .putString(KEY_API_KEY, cleanKey)
      .putFloat(KEY_LOW_STOCK, cleanThreshold.toFloat())
      .apply()
    _settings.value = _settings.value.copy(
      supabaseUrl = cleanUrl,
      publishableKey = cleanKey,
      lowStockThreshold = cleanThreshold,
    )
  }

  fun markSyncedNow() {
    val now = System.currentTimeMillis()
    prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
    _settings.value = _settings.value.copy(lastSyncMillis = now)
  }

  fun clearKey() {
    prefs.edit().remove(KEY_API_KEY).apply()
    _settings.value = _settings.value.copy(publishableKey = "")
  }

  companion object {
    private const val PREFS_NAME = "pedidos_isaac_pos_settings"
    private const val KEY_URL = "supabase_url"
    private const val KEY_API_KEY = "supabase_publishable_key"
    private const val KEY_LOW_STOCK = "low_stock_threshold"
    private const val KEY_LAST_SYNC = "last_sync_millis"
  }
}
