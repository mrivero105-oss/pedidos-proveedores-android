package com.pedidos.proveedores.domain.parser

import com.pedidos.proveedores.domain.model.ProductoImportado
import java.text.Normalizer

/**
 * Convierte tablas provenientes de Excel (.xlsx) o CSV/TSV en productos listos para revisar.
 * Detecta encabezados automáticamente (incluso si el archivo tiene filas de título arriba)
 * y cae a inferencia heurística si el catálogo viene sin encabezados.
 */
object TabularCatalogMapper {

  private data class ColumnMap(
    val codeIndex: Int?,
    val nameIndex: Int,
    val unitIndex: Int?,
    val priceIndex: Int?,
    val categoryIndex: Int?,
  )

  private val codeHeaders = setOf(
    "codigo", "cod", "cod.", "sku", "ref", "referencia", "clave", "id", "articulo", "item", "ean", "barcode", "codigo producto"
  )
  private val nameHeaders = setOf(
    "nombre", "producto", "descripcion", "detalle", "concepto", "articulo", "item", "mercaderia",
    "product", "name", "description", "title", "titulo", "denominacion"
  )
  private val unitHeaders = setOf(
    "unidad", "und", "um", "u.m.", "u/m", "presentacion", "formato", "empaque", "medida", "envase", "unit", "pack", "bulto"
  )
  private val priceHeaders = setOf(
    "precio", "precio unitario", "p. unitario", "p.u.", "pu", "valor", "costo", "pvp", "importe",
    "lista", "precio lista", "precio mayorista", "mayorista", "price", "cost", "unit price", "tarifa"
  )
  private val categoryHeaders = setOf(
    "categoria", "rubro", "linea", "familia", "marca", "grupo", "seccion", "category", "brand", "departamento"
  )

  private val summarySkipTerms = setOf(
    "total", "subtotal", "iva", "impuesto", "descuento", "observaciones", "condiciones", "pagina"
  )

  fun mapRows(
    rows: List<List<String>>,
    defaultCategory: String = "",
    startTempId: Int = 1,
  ): List<ProductoImportado> {
    val nonEmptyRows = rows
      .map { row -> row.map { it.trim().replace(Regex("\\s+"), " ") } }
      .filter { row -> row.any { it.isNotBlank() } }

    if (nonEmptyRows.isEmpty()) return emptyList()

    val headerDetection = detectHeaderRow(nonEmptyRows)
    val columnMap: ColumnMap
    val dataRows: List<List<String>>

    if (headerDetection != null) {
      val (headerRowIndex, detectedMap) = headerDetection
      columnMap = detectedMap
      dataRows = nonEmptyRows.drop(headerRowIndex + 1)
    } else {
      columnMap = inferColumnsWithoutHeader(nonEmptyRows) ?: return emptyList()
      dataRows = nonEmptyRows
    }

    var nextId = startTempId
    val result = mutableListOf<ProductoImportado>()

    for (row in dataRows) {
      val rawName = row.getOrNull(columnMap.nameIndex)?.trim().orEmpty()
      val rawCode = columnMap.codeIndex?.let { row.getOrNull(it)?.trim() }.orEmpty()
      val rawUnit = columnMap.unitIndex?.let { row.getOrNull(it)?.trim() }.orEmpty()
      val rawPrice = columnMap.priceIndex?.let { row.getOrNull(it)?.trim() }.orEmpty()
      val rawCategory = columnMap.categoryIndex?.let { row.getOrNull(it)?.trim() }.orEmpty()

      // Ignorar filas sin nombre útil o filas de resumen / encabezados repetidos
      if (rawName.length < 2) continue
      val normalizedName = normalizeHeader(rawName)
      if (normalizedName in nameHeaders || summarySkipTerms.any { normalizedName == it || normalizedName.startsWith("$it:") }) {
        continue
      }
      // Si el nombre es puramente numérico y no hay código, probablemente es basura
      if (rawName.all { it.isDigit() || it in ".,-$/ " } && rawCode.isEmpty()) continue

      val price = PriceParser.parse(rawPrice)
      val unit = rawUnit.ifBlank { inferUnitFromName(rawName) ?: "u" }
      val category = rawCategory.ifBlank { defaultCategory }

      result.add(
        ProductoImportado(
          tempId = nextId++,
          codigo = rawCode,
          nombre = rawName,
          unidad = unit,
          precio = price,
          categoria = category,
          seleccionado = true,
        )
      )
    }

    return result
  }

  private fun detectHeaderRow(rows: List<List<String>>): Pair<Int, ColumnMap>? {
    val maxRowsToInspect = minOf(rows.size, 8)
    for (rowIndex in 0 until maxRowsToInspect) {
      val row = rows[rowIndex]
      if (row.count { it.isNotBlank() } < 2) continue

      var codeIdx: Int? = null
      var nameIdx: Int? = null
      var unitIdx: Int? = null
      var priceIdx: Int? = null
      var catIdx: Int? = null
      var hits = 0

      row.forEachIndexed { idx, cell ->
        val norm = normalizeHeader(cell)
        if (norm.isEmpty()) return@forEachIndexed
        when {
          nameIdx == null && matchesAny(norm, nameHeaders) -> {
            // "articulo" o "item" puede ser nombre si ya existe código o si no hay otra columna de nombre
            nameIdx = idx
            hits++
          }
          priceIdx == null && matchesAny(norm, priceHeaders) -> {
            priceIdx = idx
            hits++
          }
          codeIdx == null && matchesAny(norm, codeHeaders) -> {
            codeIdx = idx
            hits++
          }
          unitIdx == null && matchesAny(norm, unitHeaders) -> {
            unitIdx = idx
            hits++
          }
          catIdx == null && matchesAny(norm, categoryHeaders) -> {
            catIdx = idx
            hits++
          }
        }
      }

      // Si "articulo" quedó como codeIdx pero no hubo nameIdx, promover o buscar columna más larga
      if (nameIdx != null && (hits >= 2 || (hits == 1 && row.count { it.isNotBlank() } <= 2))) {
        return rowIndex to ColumnMap(
          codeIndex = if (codeIdx == nameIdx) null else codeIdx,
          nameIndex = nameIdx,
          unitIndex = unitIdx,
          priceIndex = priceIdx,
          categoryIndex = catIdx,
        )
      }
    }
    return null
  }

  private fun inferColumnsWithoutHeader(rows: List<List<String>>): ColumnMap? {
    val sample = rows.take(25)
    val maxCols = sample.maxOfOrNull { it.size } ?: 0
    if (maxCols == 0) return null
    if (maxCols == 1) {
      return ColumnMap(codeIndex = null, nameIndex = 0, unitIndex = null, priceIndex = null, categoryIndex = null)
    }

    // Buscar columna con mayor tasa de precios válidos (preferir la más a la derecha si empatan)
    var priceIdx: Int? = null
    var bestPriceRatio = 0.0
    for (col in 0 until maxCols) {
      val values = sample.mapNotNull { it.getOrNull(col)?.takeIf(String::isNotBlank) }
      if (values.isEmpty()) continue
      val validPrices = values.count { PriceParser.parse(it) != null }
      val ratio = validPrices.toDouble() / values.size
      // Evitar confundir códigos puramente numéricos de la columna 0 con precios si hay otra columna numérica
      val looksLikeCodeCol = col == 0 && values.all { it.all(Char::isDigit) && !it.contains('.') && !it.contains(',') && !it.contains('$') }
      if (ratio >= 0.5 && !looksLikeCodeCol && ratio >= bestPriceRatio) {
        bestPriceRatio = ratio
        priceIdx = col
      }
    }

    // Columna de nombre: la que tiene texto descriptivo más largo (con letras), excluyendo priceIdx
    var nameIdx: Int? = null
    var bestNameScore = -1.0
    for (col in 0 until maxCols) {
      if (col == priceIdx) continue
      val values = sample.mapNotNull { it.getOrNull(col)?.takeIf(String::isNotBlank) }
      if (values.isEmpty()) continue
      val letterRows = values.count { v -> v.any { it.isLetter() } }
      val avgLen = values.map { it.length }.average()
      val score = (letterRows.toDouble() / values.size) * avgLen
      if (score > bestNameScore) {
        bestNameScore = score
        nameIdx = col
      }
    }
    if (nameIdx == null) return null

    // Columna de código: si hay una columna a la izquierda de nameIdx con tokens cortos sin espacios
    var codeIdx: Int? = null
    for (col in 0 until nameIdx) {
      if (col == priceIdx) continue
      val values = sample.mapNotNull { it.getOrNull(col)?.takeIf(String::isNotBlank) }
      if (values.isNotEmpty() && values.count { it.length <= 20 && !it.contains(' ') } >= values.size * 0.6) {
        codeIdx = col
        break
      }
    }

    // Columna de unidad: columna restante corta (<= 12 chars)
    var unitIdx: Int? = null
    for (col in 0 until maxCols) {
      if (col == nameIdx || col == priceIdx || col == codeIdx) continue
      val values = sample.mapNotNull { it.getOrNull(col)?.takeIf(String::isNotBlank) }
      if (values.isNotEmpty() && values.map { it.length }.average() <= 12.0 && values.any { it.any(Char::isLetter) }) {
        unitIdx = col
        break
      }
    }

    return ColumnMap(
      codeIndex = codeIdx,
      nameIndex = nameIdx,
      unitIndex = unitIdx,
      priceIndex = priceIdx,
      categoryIndex = null,
    )
  }

  private fun matchesAny(normalized: String, candidates: Set<String>): Boolean {
    if (normalized in candidates) return true
    return candidates.any { candidate ->
      candidate.length >= 4 && (normalized.startsWith(candidate) || normalized.contains(candidate))
    }
  }

  internal fun normalizeHeader(raw: String): String {
    val decomposed = Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD)
    return decomposed
      .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
      .replace(Regex("[^a-z0-9./ ]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  internal fun inferUnitFromName(name: String): String? {
    val unitMatch = Regex(
      """(?i)\b(caja|cajas|pack|paquete|paq|bolsa|bolsas|bulto|bultos|fardo|docena|doc|botella|lata|bandeja|bidon|galon|kg|kilo|lt|litro|unidad|und)\b"""
    ).find(name)
    return unitMatch?.value?.lowercase()
  }
}
