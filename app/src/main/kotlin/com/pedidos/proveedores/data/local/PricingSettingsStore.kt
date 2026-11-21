package com.pedidos.proveedores.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PricingSettings(
  val tasaBsPorUsd: Double = 0.0,
  val tasaUpdatedAtMillis: Long? = null,
  val tasaSourceLabel: String = "",
  val targetMarkupPercent: Double = 30.0,
  val criticalMarkupPercent: Double = 15.0,
  val defaultIvaPercent: Double = 16.0,
  val minOptimalSavingsPercent: Double = 3.0,
) {
  val hasValidExchangeRate: Boolean
    get() = tasaBsPorUsd > 0.0 && tasaBsPorUsd.isFinite()
}

/**
 * Almacena la configuración local de inteligencia de compras:
 * tasa Bs/USD (BCV o manual), meta de recargo sobre costo (%), umbral crítico,
 * IVA por defecto y ahorro mínimo para cambiar de proveedor en Pedido Óptimo.
 */
class PricingSettingsStore(context: Context) {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _settings = MutableStateFlow(load())
  val settings: StateFlow<PricingSettings> = _settings.asStateFlow()

  private fun load(): PricingSettings {
    val updatedAt = prefs.getLong(KEY_TASA_UPDATED_AT, -1L).takeIf { it > 0L }
    return PricingSettings(
      tasaBsPorUsd = prefs.getFloat(KEY_TASA_BS, 0f).toDouble(),
      tasaUpdatedAtMillis = updatedAt,
      tasaSourceLabel = prefs.getString(KEY_TASA_SOURCE, "").orEmpty(),
      targetMarkupPercent = prefs.getFloat(KEY_TARGET_MARKUP, 30f).toDouble().coerceIn(1.0, 500.0),
      criticalMarkupPercent = prefs.getFloat(KEY_CRITICAL_MARKUP, 15f).toDouble().coerceIn(0.0, 200.0),
      defaultIvaPercent = prefs.getFloat(KEY_DEFAULT_IVA, 16f).toDouble().coerceIn(0.0, 50.0),
      minOptimalSavingsPercent = prefs.getFloat(KEY_MIN_SAVINGS, 3f).toDouble().coerceIn(0.0, 50.0),
    )
  }

  fun updateExchangeRate(rate: Double, sourceLabel: String) {
    if (!rate.isFinite() || rate <= 0.0) return
    val now = System.currentTimeMillis()
    val cleanSource = sourceLabel.trim().ifBlank { "Manual" }
    prefs.edit()
      .putFloat(KEY_TASA_BS, rate.toFloat())
      .putLong(KEY_TASA_UPDATED_AT, now)
      .putString(KEY_TASA_SOURCE, cleanSource)
      .apply()
    _settings.value = _settings.value.copy(
      tasaBsPorUsd = rate,
      tasaUpdatedAtMillis = now,
      tasaSourceLabel = cleanSource,
    )
  }

  fun saveSettings(
    tasaBsPorUsd: Double,
    tasaSourceLabel: String,
    targetMarkupPercent: Double,
    criticalMarkupPercent: Double,
    defaultIvaPercent: Double = 16.0,
    minOptimalSavingsPercent: Double = 3.0,
  ) {
    val safeRate = if (tasaBsPorUsd.isFinite() && tasaBsPorUsd >= 0.0) tasaBsPorUsd else _settings.value.tasaBsPorUsd
    val safeTarget = targetMarkupPercent.coerceIn(1.0, 500.0)
    val safeCritical = criticalMarkupPercent.coerceIn(0.0, safeTarget)
    val safeIva = defaultIvaPercent.coerceIn(0.0, 50.0)
    val safeSavings = minOptimalSavingsPercent.coerceIn(0.0, 50.0)
    val rateChanged = kotlin.math.abs(safeRate - _settings.value.tasaBsPorUsd) > 0.0001
    val now = if (rateChanged && safeRate > 0.0) System.currentTimeMillis() else _settings.value.tasaUpdatedAtMillis
    val source = if (rateChanged && safeRate > 0.0) {
      tasaSourceLabel.trim().ifBlank { "Manual" }
    } else {
      _settings.value.tasaSourceLabel
    }

    prefs.edit().apply {
      putFloat(KEY_TASA_BS, safeRate.toFloat())
      if (now != null) putLong(KEY_TASA_UPDATED_AT, now) else remove(KEY_TASA_UPDATED_AT)
      putString(KEY_TASA_SOURCE, source)
      putFloat(KEY_TARGET_MARKUP, safeTarget.toFloat())
      putFloat(KEY_CRITICAL_MARKUP, safeCritical.toFloat())
      putFloat(KEY_DEFAULT_IVA, safeIva.toFloat())
      putFloat(KEY_MIN_SAVINGS, safeSavings.toFloat())
    }.apply()

    _settings.value = PricingSettings(
      tasaBsPorUsd = safeRate,
      tasaUpdatedAtMillis = now,
      tasaSourceLabel = source,
      targetMarkupPercent = safeTarget,
      criticalMarkupPercent = safeCritical,
      defaultIvaPercent = safeIva,
      minOptimalSavingsPercent = safeSavings,
    )
  }

  fun updateMinOptimalSavingsPercent(percent: Double) {
    val safe = percent.coerceIn(0.0, 50.0)
    prefs.edit().putFloat(KEY_MIN_SAVINGS, safe.toFloat()).apply()
    _settings.value = _settings.value.copy(minOptimalSavingsPercent = safe)
  }

  companion object {
    private const val PREFS_NAME = "pedidos_pricing_settings"
    private const val KEY_TASA_BS = "tasa_bs_usd"
    private const val KEY_TASA_UPDATED_AT = "tasa_updated_at"
    private const val KEY_TASA_SOURCE = "tasa_source"
    private const val KEY_TARGET_MARKUP = "target_markup_pct"
    private const val KEY_CRITICAL_MARKUP = "critical_markup_pct"
    private const val KEY_DEFAULT_IVA = "default_iva_pct"
    private const val KEY_MIN_SAVINGS = "min_optimal_savings_pct"
  }
}
