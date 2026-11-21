package com.pedidos.proveedores.domain.pricing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL

data class BcvRateQuote(
  val rateBsPerUsd: Double,
  val sourceLabel: String,
  val dateLabel: String = "",
)

/**
 * Consulta la tasa oficial del Banco Central de Venezuela (Bs/USD) sin requerir clave API.
 * Usa `ve.dolarapi.com` como fuente primaria y `open.er-api.com` como respaldo automático.
 */
class BcvRateClient {

  suspend fun fetchOfficialRate(): Result<BcvRateQuote> = withContext(Dispatchers.IO) {
    var lastError: Throwable? = null

    try {
      val body = httpGet(PRIMARY_DOLAR_API_URL)
      val quote = parseDolarApiJson(body)
      if (quote != null) return@withContext Result.success(quote)
    } catch (e: Exception) {
      lastError = e
    }

    try {
      val body = httpGet(FALLBACK_EXCHANGE_RATE_URL)
      val quote = parseExchangeRateApiJson(body)
      if (quote != null) return@withContext Result.success(quote)
    } catch (e: Exception) {
      lastError = e
    }

    Result.failure(
      lastError ?: IllegalStateException("No se pudo obtener la tasa BCV oficial. Puedes ingresarla manualmente.")
    )
  }

  private fun httpGet(urlStr: String): String {
    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = 8000
      readTimeout = 8000
      setRequestProperty("Accept", "application/json")
      setRequestProperty("User-Agent", "PedidosProveedores/1.3")
    }
    return try {
      val code = conn.responseCode
      if (code !in 200..299) {
        throw IllegalStateException("HTTP $code al consultar tasa BCV")
      }
      conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
      conn.disconnect()
    }
  }

  companion object {
    private const val PRIMARY_DOLAR_API_URL = "https://ve.dolarapi.com/v1/dolares/oficial"
    private const val FALLBACK_EXCHANGE_RATE_URL = "https://open.er-api.com/v6/latest/USD"

    private val json = Json {
      ignoreUnknownKeys = true
      isLenient = true
    }

    fun parseDolarApiJson(rawJson: String): BcvRateQuote? {
      if (rawJson.isBlank()) return null
      val obj = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return null
      val rate = extractDouble(obj["promedio"])
        ?: extractDouble(obj["venta"])
        ?: extractDouble(obj["compra"])
        ?: return null
      if (rate <= 0.0 || !rate.isFinite()) return null
      val fecha = (obj["fechaActualizacion"] as? JsonPrimitive)?.contentOrNull.orEmpty().take(10)
      return BcvRateQuote(
        rateBsPerUsd = rate,
        sourceLabel = "BCV oficial",
        dateLabel = fecha,
      )
    }

    fun parseExchangeRateApiJson(rawJson: String): BcvRateQuote? {
      if (rawJson.isBlank()) return null
      val obj = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return null
      val rates = runCatching { obj["rates"]?.jsonObject }.getOrNull() ?: return null
      val rate = extractDouble(rates["VES"]) ?: return null
      if (rate <= 0.0 || !rate.isFinite()) return null
      val date = (obj["time_last_update_utc"] as? JsonPrimitive)?.contentOrNull.orEmpty().take(16)
      return BcvRateQuote(
        rateBsPerUsd = rate,
        sourceLabel = "BCV respaldo",
        dateLabel = date,
      )
    }

    private fun extractDouble(el: kotlinx.serialization.json.JsonElement?): Double? {
      val prim = el as? JsonPrimitive ?: return null
      val raw = prim.contentOrNull?.trim() ?: return null
      if (raw.isEmpty() || raw.equals("null", ignoreCase = true)) return null
      return raw.replace(",", ".").toDoubleOrNull()
    }
  }
}
