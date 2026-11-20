package com.pedidos.proveedores.domain.isaacpos

import com.pedidos.proveedores.domain.model.IsaacPosProducto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente REST de solo lectura hacia Supabase (PostgREST) para Isaac POS.
 * - Soporta tanto claves modernas `sb_publishable_...` como claves legacy `anon` JWT.
 * - Consulta primero la vista `v_isaac_pos_productos_por_proveedor` (con proveedores y costos).
 * - Si dicha vista aún no existe en Supabase (HTTP 404), hace fallback transparente a `cloud_productos`.
 * - Pagina automáticamente con header `Range` para traer más de 1,000 productos sin truncar.
 */
class IsaacPosClient {

  data class FetchResult(
    val productos: List<IsaacPosProducto>,
    val sourceLabel: String,
    val usedProviderView: Boolean,
  )

  suspend fun testConnection(
    supabaseUrl: String,
    apiKey: String,
  ): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val baseUrl = validateBaseUrl(supabaseUrl)
      val key = apiKey.trim()
      require(key.isNotEmpty()) { "Ingresa la clave Publishable o Anon de Supabase." }

      val viewResp = httpGet(
        url = "$baseUrl/rest/v1/v_isaac_pos_productos_por_proveedor?select=producto_id&limit=1",
        apiKey = key,
      )
      if (viewResp.status in 200..299) {
        return@runCatching "Conexión exitosa (vista con proveedores activa)"
      }

      val baseResp = httpGet(
        url = "$baseUrl/rest/v1/cloud_productos?select=id&limit=1",
        apiKey = key,
      )
      if (baseResp.status in 200..299) {
        return@runCatching "Conexión exitosa con cloud_productos (modo base sin vista de proveedores)"
      }

      error(buildHttpErrorMessage(baseResp.status, baseResp.body))
    }
  }

  suspend fun fetchAllProducts(
    supabaseUrl: String,
    apiKey: String,
    defaultLowStock: Double = 5.0,
    onProgress: (fetchedCount: Int, statusText: String) -> Unit = { _, _ -> },
  ): FetchResult = withContext(Dispatchers.IO) {
    val baseUrl = validateBaseUrl(supabaseUrl)
    val key = apiKey.trim()
    require(key.isNotEmpty()) { "Configura tu clave Publishable o Anon de Supabase." }

    // 1. Intentar la vista enriquecida ordenada por proveedor
    onProgress(0, "Conectando con Isaac POS en Supabase...")
    val viewProbe = fetchPaginated(
      baseUrl = baseUrl,
      resourcePath = "v_isaac_pos_productos_por_proveedor?select=*&order=proveedor_nombre.asc.nullslast,producto_nombre.asc",
      apiKey = key,
      defaultLowStock = defaultLowStock,
      onProgress = onProgress,
    )

    if (viewProbe != null) {
      return@withContext FetchResult(
        productos = viewProbe,
        sourceLabel = "v_isaac_pos_productos_por_proveedor",
        usedProviderView = true,
      )
    }

    // 2. Fallback automático a cloud_productos si el script SQL de proveedores aún no se aplicó
    onProgress(0, "Leyendo cloud_productos de Isaac POS...")
    val baseProducts = fetchPaginated(
      baseUrl = baseUrl,
      resourcePath = "cloud_productos?select=*&order=categoria.asc,nombre.asc",
      apiKey = key,
      defaultLowStock = defaultLowStock,
      onProgress = onProgress,
    ) ?: error("No se encontró la tabla cloud_productos en Supabase.")

    FetchResult(
      productos = baseProducts,
      sourceLabel = "cloud_productos",
      usedProviderView = false,
    )
  }

  /**
   * Devuelve null únicamente si el recurso no existe (404). Para errores de autenticación/red lanza excepción.
   */
  private fun fetchPaginated(
    baseUrl: String,
    resourcePath: String,
    apiKey: String,
    defaultLowStock: Double,
    onProgress: (fetchedCount: Int, statusText: String) -> Unit,
  ): List<IsaacPosProducto>? {
    val pageSize = 500
    var offset = 0
    val all = mutableListOf<IsaacPosProducto>()
    val syncNow = System.currentTimeMillis()

    while (offset < 25_000) {
      val rangeHeader = "$offset-${offset + pageSize - 1}"
      val resp = httpGet(
        url = "$baseUrl/rest/v1/$resourcePath",
        apiKey = apiKey,
        rangeHeader = rangeHeader,
      )

      if (resp.status == 404 && offset == 0) {
        return null
      }
      if (resp.status == 416) {
        // Range Not Satisfiable = ya llegamos al final exacto de los registros
        break
      }
      if (resp.status !in 200..299) {
        error(buildHttpErrorMessage(resp.status, resp.body))
      }

      val batch = IsaacPosJsonParser.parseProductsArray(
        rawJson = resp.body,
        defaultLowStock = defaultLowStock,
        syncTimestamp = syncNow,
      )
      all.addAll(batch)
      onProgress(all.size, "Descargados ${all.size} productos de Isaac POS...")

      if (batch.size < pageSize) break
      offset += pageSize
    }

    return all
  }

  private data class HttpResp(val status: Int, val body: String)

  private fun httpGet(
    url: String,
    apiKey: String,
    rangeHeader: String? = null,
  ): HttpResp {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = 15_000
      readTimeout = 30_000
      setRequestProperty("apikey", apiKey)
      if (apiKey.startsWith("eyJ")) {
        setRequestProperty("Authorization", "Bearer $apiKey")
      }
      setRequestProperty("Accept", "application/json")
      setRequestProperty("Range-Unit", "items")
      if (rangeHeader != null) {
        setRequestProperty("Range", rangeHeader)
      }
    }
    return try {
      val status = conn.responseCode
      val stream = if (status in 200..299) conn.inputStream else conn.errorStream
      val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
      HttpResp(status = status, body = body)
    } finally {
      conn.disconnect()
    }
  }

  private fun validateBaseUrl(raw: String): String {
    val clean = raw.trim().trimEnd('/')
    require(clean.startsWith("https://")) { "La URL de Supabase debe comenzar con https://" }
    return clean
  }

  private fun buildHttpErrorMessage(status: Int, body: String): String {
    return when (status) {
      401, 403 -> "Acceso denegado (HTTP $status). Verifica la clave Publishable o Anon de Supabase y las políticas RLS de lectura."
      else -> "Error HTTP $status al conectar con Supabase${if (body.isNotBlank()) ": ${body.take(140)}" else "."}"
    }
  }
}
