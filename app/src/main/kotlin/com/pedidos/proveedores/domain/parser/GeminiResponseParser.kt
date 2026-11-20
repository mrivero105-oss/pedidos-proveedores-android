package com.pedidos.proveedores.domain.parser

import com.pedidos.proveedores.domain.model.ProductoImportado
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class GeminiCatalogBatchDto(
  val productos: List<GeminiProductDto> = emptyList(),
)

@Serializable
internal data class GeminiProductDto(
  val codigo: String? = null,
  val nombre: String = "",
  val unidad: String? = null,
  val precio: Double? = null,
  val precioTexto: String? = null,
  val categoria: String? = null,
  val pagina: Int? = null,
)

/**
 * Parsea la respuesta JSON estructurada devuelta por Gemini Vision API en una lista limpia
 * de ProductoImportado. Lógica pura en Kotlin (testeable en JVM).
 */
object GeminiResponseParser {

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
  }

  fun parseProductsJson(
    rawModelText: String,
    defaultPage: Int? = null,
    startTempId: Int = 1,
  ): List<ProductoImportado> {
    val cleanedJson = stripMarkdownCodeFences(rawModelText).trim()
    if (cleanedJson.isEmpty()) return emptyList()

    val itemsDto: List<GeminiProductDto> = runCatching {
      json.decodeFromString<GeminiCatalogBatchDto>(cleanedJson).productos
    }.getOrElse {
      // Fallback por si el modelo devolvió directamente un arreglo JSON [...]
      runCatching {
        json.decodeFromString<List<GeminiProductDto>>(cleanedJson)
      }.getOrDefault(emptyList())
    }

    var nextId = startTempId
    val result = mutableListOf<ProductoImportado>()
    val seen = mutableSetOf<String>()

    for (dto in itemsDto) {
      val name = dto.nombre.replace(Regex("\\s+"), " ").trim()
      if (name.length < 2) continue

      val rawCode = dto.codigo.orEmpty().trim()
      val cleanCode = if (rawCode.filter { it.isDigit() }.length >= 11) {
        PdfProductSheetParser.normalizeBarcode(rawCode) ?: rawCode
      } else {
        rawCode
      }

      val resolvedPrice = dto.precio?.takeIf { it >= 0.0 && it.isFinite() }
        ?: PriceParser.parse(dto.precioTexto)

      val unit = dto.unidad.orEmpty().trim().ifBlank { "u" }
      val category = dto.categoria.orEmpty().trim()
      val page = dto.pagina?.takeIf { it > 0 } ?: defaultPage

      val dedupKey = "${cleanCode.lowercase()}|${name.lowercase()}|${unit.lowercase()}"
      if (!seen.add(dedupKey)) continue

      result.add(
        ProductoImportado(
          tempId = nextId++,
          codigo = cleanCode,
          nombre = name,
          unidad = unit,
          precio = resolvedPrice,
          categoria = category,
          paginaPdf = page,
          seleccionado = true,
        )
      )
    }

    return result
  }

  internal fun stripMarkdownCodeFences(raw: String): String {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("```")) return trimmed
    val withoutStart = trimmed.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trimStart()
    return withoutStart.removeSuffix("```").trim()
  }
}
