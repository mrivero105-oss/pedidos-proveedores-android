package com.pedidos.proveedores.data.local

import android.content.Context
import com.pedidos.proveedores.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OtaSettings(
  val repoSlug: String = BuildConfig.DEFAULT_OTA_REPO,
  val autoCheckOnLaunch: Boolean = true,
)

class OtaSettingsStore(context: Context) {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _settings = MutableStateFlow(load())
  val settings: StateFlow<OtaSettings> = _settings.asStateFlow()

  private fun load(): OtaSettings = OtaSettings(
    repoSlug = prefs.getString(KEY_REPO_SLUG, BuildConfig.DEFAULT_OTA_REPO)
      ?.takeIf { it.isNotBlank() }
      ?: BuildConfig.DEFAULT_OTA_REPO,
    autoCheckOnLaunch = prefs.getBoolean(KEY_AUTO_CHECK, true),
  )

  fun save(repoSlug: String, autoCheckOnLaunch: Boolean) {
    val cleanSlug = repoSlug.trim().ifBlank { BuildConfig.DEFAULT_OTA_REPO }
    prefs.edit()
      .putString(KEY_REPO_SLUG, cleanSlug)
      .putBoolean(KEY_AUTO_CHECK, autoCheckOnLaunch)
      .apply()
    _settings.value = OtaSettings(repoSlug = cleanSlug, autoCheckOnLaunch = autoCheckOnLaunch)
  }

  companion object {
    private const val PREFS_NAME = "pedidos_ota_settings"
    private const val KEY_REPO_SLUG = "github_repo_slug"
    private const val KEY_AUTO_CHECK = "auto_check_on_launch"
  }
}
