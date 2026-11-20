package com.pedidos.proveedores.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AiSettings(
  val geminiApiKey: String = "",
  val geminiModel: String = DEFAULT_MODEL,
) {
  val isConfigured: Boolean
    get() = geminiApiKey.isNotBlank()

  companion object {
    const val DEFAULT_MODEL = "gemini-2.5-flash"
    val AVAILABLE_MODELS = listOf(
      "gemini-2.5-flash" to "Gemini 2.5 Flash (Máxima precisión · gratis)",
      "gemini-2.5-flash-lite" to "Gemini 2.5 Flash-Lite (Más rápido · mayor cuota gratis)",
    )
  }
}

/**
 * Almacena la clave API gratuita de Google AI Studio únicamente de forma local
 * en las preferencias privadas del teléfono. Nunca se incluye en el código fuente.
 */
class AiSettingsStore(context: Context) {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _settings = MutableStateFlow(load())
  val settings: StateFlow<AiSettings> = _settings.asStateFlow()

  private fun load(): AiSettings = AiSettings(
    geminiApiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
    geminiModel = prefs.getString(KEY_MODEL, AiSettings.DEFAULT_MODEL) ?: AiSettings.DEFAULT_MODEL,
  )

  fun save(apiKey: String, model: String) {
    val cleanKey = apiKey.trim()
    val cleanModel = model.trim().ifBlank { AiSettings.DEFAULT_MODEL }
    prefs.edit()
      .putString(KEY_API_KEY, cleanKey)
      .putString(KEY_MODEL, cleanModel)
      .apply()
    _settings.value = AiSettings(geminiApiKey = cleanKey, geminiModel = cleanModel)
  }

  fun clearKey() {
    prefs.edit().remove(KEY_API_KEY).apply()
    _settings.value = _settings.value.copy(geminiApiKey = "")
  }

  companion object {
    private const val PREFS_NAME = "pedidos_ai_settings"
    private const val KEY_API_KEY = "gemini_api_key"
    private const val KEY_MODEL = "gemini_model"
  }
}
