package com.pedidos.proveedores.domain.isaacpos

import com.pedidos.proveedores.domain.model.IsaacPosProducto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente REST de solo lectura hacia Supabase (PostgREST) para Isaac POS.
 * Enfoque 100% proveedores:
 * - Consulta `cloud_proveedores` (activos) para traer todos los proveedores de Isaac POS.
 * - Consulta `v_isaac_pos_productos_por_proveedor` filtrando `proveedor_nombre=not.is.null`
 *   para traer únicamente productos asociados a proveedores (sin categorías ni productos sueltos).
 * - Soporta tanto claves modernas `sb_publishable_...` como claves legacy `anon` JWT.
 */
class IsaacPosClient {

  data class FetchResult(
    val productos: List<IsaacPosProducto>,
    val proveedores: List<RemoteProviderSeed> = emptyList(),
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

      val provResp = httpGet(
        url = "$baseUrl/rest/v1/cloud_proveedores?select=id&activo=eq.true&limit=1",
        apiKey = key,
      )
      val viewResp = httpGet(
        url = "$baseUrl/rest/v1/v_isaac_pos_productos_por_proveedor?select=producto_id&proveedor_nombre=not.is.null&limit=1",
        apiKey = key,
      )
      if (provResp.status in 200..299 && viewResp.status in 200..299) {
        return@runCatching "Conexión exitosa (solo proveedores de Isaac POS activos)"
      }

      val errResp = if (provResp.status !in 200..299) provResp else viewResp
      error(buildHttpErrorMessage(errResp.status, errResp.body))
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

    // 1. Traer todos los proveedores activos de Isaac POS (incluso los que aún no tienen productos)
    onProgress(0, "Descargando proveedores de Isaac POS...")
    val providersResp = httpGet(
      url = "$baseUrl/rest/v1/cloud_proveedores?select=nombre,telefono_whatsapp,notas,activo&activo=eq.true&order=nombre.asc",
      apiKey = key,
    )
    if (providersResp.status !in 200..299 && providersResp.status != 404) {
      error(buildHttpErrorMessage(providersResp.status, providersResp.body))
    }
    val remoteProviders = if (providersResp.status in 200..299) {
      IsaacPosJsonParser.parseProvidersArray(providersResp.body)
    } else {
      emptyList()
    }

    // 2. Traer únicamente los productos vinculados a un proveedor real (sin categorías ni sueltos)
    onProgress(0, "Descargando productos de ${remoteProviders.size} proveedor(es)...")
    val providerProducts = fetchPaginated(
      baseUrl = baseUrl,
      resourcePath = "v_isaac_pos_productos_por_proveedor?select=*&proveedor_nombre=not.is.null&order=proveedor_nombre.asc,producto_nombre.asc",
      apiKey = key,
      defaultLowStock = defaultLowStock,
      onProgress = onProgress,
    )?.filter { it.proveedorNombreRemoto.isNotBlank() }

    if (providerProducts != null) {
      return@withContext FetchResult(
        productos = providerProducts,
        proveedores = remoteProviders,
        sourceLabel = "cloud_proveedores + v_isaac_pos_productos_por_proveedor",
        usedProviderView = true,
      )
    }

    error("No se encontró la vista de proveedores en Supabase. Aplica el script SQL de proveedores de Isaac POS.")
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
