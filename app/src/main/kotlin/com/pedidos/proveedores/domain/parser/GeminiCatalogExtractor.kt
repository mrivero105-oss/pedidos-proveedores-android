package com.pedidos.proveedores.domain.parser

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.pedidos.proveedores.data.local.AiSettings
import com.pedidos.proveedores.domain.model.ProductoImportado
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Extrae productos de catálogos PDF e imágenes (JPG/PNG/WebP) con máxima precisión visual
 * enviando imágenes optimizadas a la API oficial de Gemini (compatible con la capa gratuita de Google AI Studio).
 */
class GeminiCatalogExtractor {

  data class GeminiExtractResult(
    val productos: List<ProductoImportado>,
    val paginasProcesadas: Int,
    val advertencia: String? = null,
  )

  private val fallbackModels = listOf(
    "gemini-flash-lite-latest",
    "gemini-3.5-flash-lite",
    "gemini-flash-latest",
  )

  private fun candidateModelsFor(requestedModel: String): List<String> {
    val primary = AiSettings.migrateLegacyModel(requestedModel)
    return (listOf(primary) + fallbackModels).distinct()
  }

  suspend fun testConnection(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
      val cleanKey = apiKey.trim()
      require(cleanKey.isNotEmpty()) { "Ingresa una clave API de Gemini." }

      val body = JSONObject().apply {
        put(
          "contents",
          JSONArray().put(
            JSONObject().put(
              "parts",
              JSONArray().put(JSONObject().put("text", "Responde únicamente: OK"))
            )
          )
        )
      }

      val candidates = candidateModelsFor(model)
      var lastError: Throwable? = null
      for ((idx, candidate) in candidates.withIndex()) {
        try {
          val text = postGenerateContent(cleanKey, candidate, body)
          if (text.isBlank()) error("Respuesta vacía de Gemini.")
          return@runCatching if (idx == 0) {
            "Conexión exitosa con $candidate"
          } else {
            "Conexión exitosa con $candidate (respaldo automático por saturación temporal de ${candidates.first()})"
          }
        } catch (e: ModelUnavailableException) {
          lastError = e
        } catch (e: TransientGeminiException) {
          lastError = e
        }
      }
      throw (lastError ?: IllegalStateException("No se pudo conectar con ningún modelo Gemini disponible."))
    }
  }

  suspend fun extractFromPdf(
    pdfFile: File,
    apiKey: String,
    model: String,
    onProgress: (currentPage: Int, totalPages: Int, statusText: String) -> Unit = { _, _, _ -> },
  ): GeminiExtractResult = withContext(Dispatchers.IO) {
    val cleanKey = apiKey.trim()
    require(cleanKey.isNotEmpty()) { "Configura tu clave API gratuita de Gemini en Ajustes de IA." }

    val allProducts = mutableListOf<ProductoImportado>()
    val seenKeys = mutableSetOf<String>()
    var nextTempId = 1
    var totalPages = 0
    val failedBatches = mutableListOf<String>()

    ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
      PdfRenderer(pfd).use { renderer ->
        totalPages = renderer.pageCount
        val batchSize = 3
        var pageIdx = 0

        while (pageIdx < totalPages) {
          val endExclusive = minOf(pageIdx + batchSize, totalPages)
          val pageNumbers = (pageIdx + 1)..endExclusive
          onProgress(
            pageIdx + 1,
            totalPages,
            "Analizando páginas ${pageNumbers.first}–${pageNumbers.last} de $totalPages con IA...",
          )

          val batchImages = mutableListOf<Pair<Int, String>>()
          for (idx in pageIdx until endExclusive) {
            renderer.openPage(idx).use { page ->
              val targetW = 1100
              val targetH = (page.height * (targetW.toFloat() / page.width)).toInt().coerceAtLeast(1)
              val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
              bmp.eraseColor(Color.WHITE)
              page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

              val baos = ByteArrayOutputStream()
              bmp.compress(Bitmap.CompressFormat.JPEG, 78, baos)
              bmp.recycle()
              val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
              batchImages.add((idx + 1) to b64)
            }
          }

          val batchResult = callGeminiWithRetry(
            apiKey = cleanKey,
            model = model,
            pageImages = batchImages,
          )

          batchResult.onSuccess { modelText ->
            val parsed = GeminiResponseParser.parseProductsJson(
              rawModelText = modelText,
              defaultPage = pageNumbers.first,
              startTempId = nextTempId,
            )
            for (item in parsed) {
              val key = "${item.codigo.lowercase()}|${item.nombre.lowercase()}|${item.unidad.lowercase()}"
              if (seenKeys.add(key)) {
                allProducts.add(item.copy(tempId = nextTempId++))
              }
            }
          }.onFailure { err ->
            failedBatches.add("Págs. ${pageNumbers.first}-${pageNumbers.last}: ${err.message}")
          }

          pageIdx = endExclusive
          if (pageIdx < totalPages) {
            delay(1200L)
          }
        }
      }
    }

    val warning = when {
      failedBatches.isNotEmpty() && allProducts.isNotEmpty() ->
        "Algunos lotes fallaron (${failedBatches.joinToString("; ")}). Revisa los productos detectados."
      failedBatches.isNotEmpty() ->
        "No se pudo completar la extracción con IA: ${failedBatches.first()}"
      allProducts.isEmpty() ->
        "La IA analizó las $totalPages páginas y no encontró productos."
      else -> null
    }

    GeminiExtractResult(
      productos = allProducts,
      paginasProcesadas = totalPages,
      advertencia = warning,
    )
  }

  suspend fun extractFromImageFiles(
    imageFiles: List<File>,
    apiKey: String,
    model: String,
    onProgress: (current: Int, total: Int, statusText: String) -> Unit = { _, _, _ -> },
  ): GeminiExtractResult = withContext(Dispatchers.IO) {
    val cleanKey = apiKey.trim()
    require(cleanKey.isNotEmpty()) { "Configura tu clave API gratuita de Gemini en Ajustes de IA." }
    require(imageFiles.isNotEmpty()) { "No hay imágenes para analizar." }

    val total = imageFiles.size
    val allProducts = mutableListOf<ProductoImportado>()
    val seenKeys = mutableSetOf<String>()
    var nextTempId = 1
    val failedBatches = mutableListOf<String>()

    val batchSize = 3
    var idx = 0
    while (idx < total) {
      val endExclusive = minOf(idx + batchSize, total)
      onProgress(
        idx + 1,
        total,
        if (total == 1) "Analizando imagen con IA Gemini..."
        else "Analizando imágenes ${idx + 1}–$endExclusive de $total con IA...",
      )

      val batchImages = mutableListOf<Pair<Int, String>>()
      for (i in idx until endExclusive) {
        val bmp = MlKitImageOcrExtractor.decodeScaledBitmap(imageFiles[i], maxDimension = 1200) ?: continue
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        bmp.recycle()
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        batchImages.add((i + 1) to b64)
      }

      if (batchImages.isNotEmpty()) {
        val batchResult = callGeminiWithRetry(
          apiKey = cleanKey,
          model = model,
          pageImages = batchImages,
        )
        batchResult.onSuccess { modelText ->
          val parsed = GeminiResponseParser.parseProductsJson(
            rawModelText = modelText,
            defaultPage = idx + 1,
            startTempId = nextTempId,
          )
          for (item in parsed) {
            val key = "${item.codigo.lowercase()}|${item.nombre.lowercase()}|${item.unidad.lowercase()}"
            if (seenKeys.add(key)) {
              allProducts.add(item.copy(tempId = nextTempId++))
            }
          }
        }.onFailure { err ->
          failedBatches.add("Imágenes ${idx + 1}-$endExclusive: ${err.message}")
        }
      }

      idx = endExclusive
      if (idx < total) {
        delay(1200L)
      }
    }

    val warning = when {
      failedBatches.isNotEmpty() && allProducts.isNotEmpty() ->
        "Algunas imágenes fallaron (${failedBatches.joinToString("; ")}). Revisa los productos detectados."
      failedBatches.isNotEmpty() ->
        "No se pudo completar la extracción con IA: ${failedBatches.first()}"
      allProducts.isEmpty() ->
        "La IA analizó $total imagen(es) y no encontró productos."
      else -> null
    }

    GeminiExtractResult(
      productos = allProducts,
      paginasProcesadas = total,
      advertencia = warning,
    )
  }

  private suspend fun callGeminiWithRetry(
    apiKey: String,
    model: String,
    pageImages: List<Pair<Int, String>>,
  ): Result<String> {
    val payload = buildBatchRequestPayload(pageImages)
    val candidates = candidateModelsFor(model)
    var lastError: Throwable? = null

    for (candidate in candidates) {
      for (attempt in 0..1) {
        try {
          val responseText = postGenerateContent(apiKey, candidate, payload)
          return Result.success(responseText)
        } catch (e: ModelUnavailableException) {
          lastError = e
          break // Pasar inmediatamente al siguiente modelo de respaldo
        } catch (e: TransientGeminiException) {
          lastError = e
          if (attempt == 0) {
            delay(3000L)
          }
        } catch (e: Exception) {
          return Result.failure(e)
        }
      }
    }
    return Result.failure(lastError ?: IllegalStateException("Límite o alta demanda temporal de Gemini alcanzada."))
  }

  private fun buildBatchRequestPayload(pageImages: List<Pair<Int, String>>): JSONObject {
    val parts = JSONArray()

    val prompt = """
      Eres un extractor de catálogos comerciales de proveedores con precisión quirúrgica.
      Analiza las imágenes adjuntas (pueden ser páginas de catálogo, capturas de listas de precios o fotos de productos) y devuelve ÚNICAMENTE un JSON válido con el campo "productos".

      REGLAS ESTRICTAS:
      1. Ignora portadas, índices o pantallas sin productos vendibles.
      2. Si una imagen muestra varios productos o varias presentaciones/gramajes/sabores, devuelve UN objeto por cada presentación/SKU.
      3. Si una imagen muestra la foto de un solo producto (por ejemplo un empaque, lata o botella), extrae ese producto leyendo la marca, nombre y peso/contenido neto visibles en la etiqueta.
      4. "nombre": nombre comercial completo y limpio incluyendo marca/variante y gramaje/volumen (ej. "Café Amanecer 200g", "Arroz Tradicional Amanecer 900g"). Corrige errores tipográficos evidentes.
      5. "codigo": código de barras (EAN/UPC) o SKU sin espacios (si tiene prefijo repetido como "759 7595461000032", devuelve los 13 dígitos reales "7595461000032"). Si no existe código, usa "".
      6. "unidad": empaque de pedido al proveedor. Si indica unidades por bulto/caja y peso del bulto, usa formato "bulto x 24 u (21.6 kg)". Si es por unidad/caja simple, indícalo claro.
      7. "precio": número decimal si la imagen muestra precio explícito; si NO hay precio visible, devuelve null.
      8. "categoria": rubro o línea del producto (ej. "Café", "Arroz", "Enlatados", "Lácteos", "Bebidas", etc.).
      9. "pagina": número entero exacto de la imagen/página donde aparece el producto (según la etiqueta "Página N" antes de cada imagen).
    """.trimIndent()

    parts.put(JSONObject().put("text", prompt))

    for ((pageNum, b64) in pageImages) {
      parts.put(JSONObject().put("text", "Página $pageNum:"))
      parts.put(
        JSONObject().put(
          "inline_data",
          JSONObject()
            .put("mime_type", "image/jpeg")
            .put("data", b64),
        )
      )
    }

    val itemProperties = JSONObject()
      .put("codigo", JSONObject().put("type", "STRING"))
      .put("nombre", JSONObject().put("type", "STRING"))
      .put("unidad", JSONObject().put("type", "STRING"))
      .put("precio", JSONObject().put("type", "NUMBER").put("nullable", true))
      .put("categoria", JSONObject().put("type", "STRING"))
      .put("pagina", JSONObject().put("type", "INTEGER"))

    val schema = JSONObject()
      .put("type", "OBJECT")
      .put(
        "properties",
        JSONObject().put(
          "productos",
          JSONObject()
            .put("type", "ARRAY")
            .put(
              "items",
              JSONObject()
                .put("type", "OBJECT")
                .put("properties", itemProperties)
                .put("required", JSONArray().put("nombre").put("codigo").put("unidad").put("categoria").put("pagina")),
            ),
        ),
      )
      .put("required", JSONArray().put("productos"))

    return JSONObject()
      .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
      .put(
        "generationConfig",
        JSONObject()
          .put("temperature", 0.0)
          .put("responseMimeType", "application/json")
          .put("responseSchema", schema),
      )
  }

  private fun postGenerateContent(apiKey: String, model: String, body: JSONObject): String {
    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      connectTimeout = 20_000
      readTimeout = 90_000
      doOutput = true
      setRequestProperty("Content-Type", "application/json; charset=UTF-8")
    }

    try {
      conn.outputStream.use { os ->
        os.write(body.toString().toByteArray(Charsets.UTF_8))
      }

      val status = conn.responseCode
      val stream = if (status in 200..299) conn.inputStream else conn.errorStream
      val rawResponse = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

      if (status == 429 || status == 503) {
        throw TransientGeminiException(
          if (status == 429) "Cuota temporal de Gemini alcanzada (HTTP 429)."
          else "Alta demanda temporal en $model (HTTP 503)."
        )
      }
      if (status == 404) {
        val apiError = runCatching {
          JSONObject(rawResponse).optJSONObject("error")?.optString("message")
        }.getOrNull()
        throw ModelUnavailableException(apiError?.ifBlank { null } ?: "Modelo $model no disponible (HTTP 404).")
      }
      if (status !in 200..299) {
        val apiError = runCatching {
          JSONObject(rawResponse).optJSONObject("error")?.optString("message")
        }.getOrNull()
        throw IllegalStateException(apiError?.ifBlank { null } ?: "Error HTTP $status de Gemini.")
      }

      val root = JSONObject(rawResponse)
      val candidates = root.optJSONArray("candidates") ?: return ""
      if (candidates.length() == 0) return ""
      val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return ""
      val sb = StringBuilder()
      for (i in 0 until parts.length()) {
        val t = parts.getJSONObject(i).optString("text", "")
        if (t.isNotEmpty()) sb.append(t)
      }
      return sb.toString()
    } finally {
      conn.disconnect()
    }
  }

  private class TransientGeminiException(message: String) : Exception(message)
  private class ModelUnavailableException(message: String) : Exception(message)
}
