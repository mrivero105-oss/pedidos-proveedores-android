package com.pedidos.proveedores.domain.parser

import com.pedidos.proveedores.domain.model.ProductoImportado
import java.text.Normalizer
import kotlin.math.abs

data class PdfSpan(
  val x: Float,
  val y: Float,
  val width: Float,
  val height: Float,
  val fontSize: Float,
  val text: String,
)

data class PdfPageLayout(
  val pageNumber: Int,
  val width: Float,
  val height: Float,
  val spans: List<PdfSpan>,
)

/**
 * Extractor espacial para catálogos comerciales en PDF con fichas de producto por página
 * (como catálogos de distribuidoras / fuerza de ventas con presentaciones, unidades por bulto,
 * pesos y códigos de barras).
 *
 * Lógica pura en Kotlin (100% testeable en JVM sin dependencias de Android).
 */
object PdfProductSheetParser {

  private data class BulkEntry(
    val label: String,
    val gramaje: String?,
    val unitsPerBulk: Int,
  )

  private data class BarcodeRowAnchor(
    val y: Float,
    val barcode: String,
  )

  private data class TitleExtraction(
    val title: String,
    val titleSpans: Set<PdfSpan>,
  )

  // Gramaje individual limpio o con erratas OCR
  private val gramajeRegex = Regex(
    """(?i)\b(\d+(?:[.,]\d{1,2})?)\s*(kg|kgr|gr|g|ml|lt|l)\b"""
  )

  // Peso de bulto (ej. "5kg", ".5kg", "4.8kg", "21.6kg", "2.04kg")
  private val bulkWeightRegex = Regex(
    """(?i)(?:^|[\s.])(\.?\d+(?:[.,]\d+)?)\s*(kg|kgr|g|gr|lt|l)\b"""
  )

  // Código de barras EAN/UPC: 11-16 dígitos con posibles espacios internos
  private val barcodeCandidateRegex = Regex("""\b(\d[\d\s]{10,18}\d)\b""")

  // Permiso sanitario (ej. "A-159.570") por si cae en el borde izquierdo de la columna 2
  private val sanitaryPermitRegex = Regex("""(?i)\b[A-Z]-\d{2,4}\.\d{2,4}\b""")

  private val brandStopWords = setOf(
    "grupo", "amanecer", "cafe", "alimentos", "dellanonna", "favorito", "aurora",
    "arroz", "leche", "sardinas", "manufactura", "itc", "lata"
  )

  fun extractFromPages(pages: List<PdfPageLayout>): List<ProductoImportado> {
    val result = mutableListOf<ProductoImportado>()
    var nextId = 1

    for (page in pages) {
      val pageItems = parseProductSheetPage(page, startTempId = nextId)
      if (pageItems.isNotEmpty()) {
        result.addAll(pageItems)
        nextId += pageItems.size
      }
    }
    return result
  }

  private fun parseProductSheetPage(
    page: PdfPageLayout,
    startTempId: Int,
  ): List<ProductoImportado> {
    if (page.spans.isEmpty() || page.width <= 0f || page.height <= 0f) return emptyList()

    val normalizedSpans = page.spans
      .map { it.copy(text = normalizeOcrChars(it.text).trim()) }
      .filter { it.text.isNotEmpty() }

    val fullTextNorm = normalizedSpans.joinToString(" ") { normalizeLookup(it.text) }

    // Verificar si la página es una ficha técnica de producto (descarta portadas / introducciones)
    val hasSheetMarkers =
      (fullTextNorm.contains("codigo") && fullTextNorm.contains("barras")) ||
        (fullTextNorm.contains("pesos") && fullTextNorm.contains("gramajes")) ||
        fullTextNorm.contains("unidades por bul") ||
        fullTextNorm.contains("unidades por caja") ||
        fullTextNorm.contains("permisos sanitarios")

    if (!hasSheetMarkers) return emptyList()

    // 1. Localizar cabecera de la tabla inferior, corte de columna 1 y pie de página
    val tableHeaderY = findTableHeaderY(normalizedSpans, page.height) ?: (page.height * 0.72f)
    val footerY = findFooterY(normalizedSpans, page.height, tableHeaderY)
    val col1MaxX = findColumn1MaxX(normalizedSpans, page.width, tableHeaderY)

    // 2. Extraer el título principal de la ficha usando tamaño de fuente + coordenadas
    val titleExtraction = extractMainTitle(normalizedSpans, page.width, page.height, tableHeaderY)
    val otherPageSpans = normalizedSpans.filter { it !in titleExtraction.titleSpans }
    val title = cleanProductTitle(titleExtraction.title, otherPageSpans)

    // 3. Extraer las entradas del bloque "Cantidad de unidades por bulto / caja"
    val bulkEntries = extractBulkEntries(normalizedSpans, page.width, page.height, tableHeaderY)

    // 4. Extraer anclas de filas a partir de los códigos de barras dentro de la tabla inferior
    val tableSpans = normalizedSpans.filter { it.y > tableHeaderY + 6f && it.y < footerY }
    val barcodeAnchors = extractBarcodeAnchors(tableSpans, page.width)

    if (barcodeAnchors.isEmpty()) return emptyList()

    // 5. Combinar cada fila con su presentación, peso de bulto, unidades por bulto y código de barras
    val category = inferCategory(title)
    val productos = mutableListOf<ProductoImportado>()
    var tempId = startTempId

    barcodeAnchors.forEachIndexed { idx, anchor ->
      val prevMidY = if (idx == 0) tableHeaderY + 4f else (barcodeAnchors[idx - 1].y + anchor.y) / 2f
      val nextMidY = if (idx == barcodeAnchors.lastIndex) footerY else (anchor.y + barcodeAnchors[idx + 1].y) / 2f

      val minBandY = maxOf(prevMidY, anchor.y - 16f)
      val maxBandY = minOf(nextMidY, anchor.y + 18f)

      val leftRowSpans = tableSpans
        .filter { it.y in minBandY..maxBandY && it.x < col1MaxX }
        .sortedWith(compareBy<PdfSpan> { Math.round(it.y / 6f) }.thenBy { it.x })

      // Corregir OCR y quitar posibles permisos sanitarios ANTES de separar gramaje y peso de bulto
      val leftTextFixed = fixGramajeOcr(
        joinSpansCleanly(leftRowSpans).replace(sanitaryPermitRegex, "").trim()
      )
      val (presentationPart, bulkWeight) = splitPresentationAndBulkWeight(leftTextFixed)

      val itemGramaje = extractSingleGramaje(presentationPart)
      val matchedBulk = matchBulkEntry(
        rowIndex = idx,
        totalRows = barcodeAnchors.size,
        sheetTitle = title,
        presentation = presentationPart,
        gramaje = itemGramaje,
        bulkEntries = bulkEntries,
      )

      val finalName = composeProductName(
        sheetTitle = title,
        rowPresentation = presentationPart,
        gramaje = itemGramaje ?: matchedBulk?.gramaje,
      )

      val finalUnit = buildBulkUnitLabel(
        unitsPerBulk = matchedBulk?.unitsPerBulk,
        bulkWeight = bulkWeight,
      )

      if (finalName.isNotBlank()) {
        productos.add(
          ProductoImportado(
            tempId = tempId++,
            codigo = anchor.barcode,
            nombre = finalName,
            unidad = finalUnit,
            precio = null,
            categoria = category,
            paginaPdf = page.pageNumber,
            seleccionado = true,
          )
        )
      }
    }

    return productos
  }

  private fun findTableHeaderY(spans: List<PdfSpan>, pageHeight: Float): Float? {
    val candidates = spans.filter { span ->
      val norm = normalizeLookup(span.text)
      span.y > pageHeight * 0.55f && span.y < pageHeight * 0.88f && (
        norm.contains("gramajes") ||
          norm.contains("sanitarios") ||
          norm == "barras" ||
          norm.contains("de barras") ||
          norm == "cpe"
        )
    }
    return candidates.maxOfOrNull { it.y }
  }

  private fun findColumn1MaxX(spans: List<PdfSpan>, pageWidth: Float, tableHeaderY: Float): Float {
    val permisoHeader = spans.firstOrNull { span ->
      val norm = normalizeLookup(span.text)
      abs(span.y - tableHeaderY) <= 24f && (norm.contains("permiso") || norm.contains("sanitario"))
    }
    return if (permisoHeader != null && permisoHeader.x > pageWidth * 0.30f) {
      permisoHeader.x - 8f
    } else {
      pageWidth * 0.37f
    }
  }

  private fun findFooterY(spans: List<PdfSpan>, pageHeight: Float, tableHeaderY: Float): Float {
    val footerCandidates = spans.filter { span ->
      val norm = normalizeLookup(span.text)
      span.y > tableHeaderY + 25f && (
        norm.startsWith("j-") ||
          norm == "grupo" ||
          norm == "itc" ||
          norm.contains("manufactura") ||
          norm.contains("del cafe")
        )
    }
    val detected = footerCandidates.minOfOrNull { it.y }
    return if (detected != null) detected - 4f else pageHeight * 0.91f
  }

  private fun extractMainTitle(
    spans: List<PdfSpan>,
    pageWidth: Float,
    pageHeight: Float,
    tableHeaderY: Float,
  ): TitleExtraction {
    val candidateSpans = spans.filter { span ->
      span.y in (pageHeight * 0.16f)..(minOf(pageHeight * 0.52f, tableHeaderY - 40f)) &&
        span.fontSize >= 22f &&
        span.text.any { it.isLetter() }
    }
    if (candidateSpans.isEmpty()) return TitleExtraction("", emptySet())

    val lines = groupIntoVisualLines(candidateSpans, yTolerance = 12f)
    if (lines.isEmpty()) return TitleExtraction("", emptySet())

    val blocks = mutableListOf<MutableList<List<PdfSpan>>>()
    for (line in lines) {
      val lineMinX = line.minOf { it.x }
      val lineY = line.map { it.y }.average().toFloat()
      val lineMaxFont = line.maxOf { it.fontSize }

      val matchingBlock = blocks.lastOrNull { block ->
        val prevLine = block.last()
        val prevMinX = prevLine.minOf { it.x }
        val prevY = prevLine.map { it.y }.average().toFloat()
        val prevMaxFont = prevLine.maxOf { it.fontSize }
        abs(lineMinX - prevMinX) <= pageWidth * 0.14f &&
          (lineY - prevY) in 12f..55f &&
          abs(lineMaxFont - prevMaxFont) <= 10f
      }
      if (matchingBlock != null) {
        matchingBlock.add(line)
      } else {
        blocks.add(mutableListOf(line))
      }
    }

    val bestBlock = blocks.maxByOrNull { block ->
      val avgFont = block.flatten().map { it.fontSize }.average()
      val topY = block.first().minOf { it.y }
      val wordsCount = block.flatten().size
      val xStart = block.first().minOf { it.x }
      val text = block.flatten().joinToString(" ") { it.text }
      val allCapsPenalty = if (text.length >= 4 && text == text.uppercase()) -15.0 else 0.0
      val rightHalfBonus = if (xStart >= pageWidth * 0.48f) 18.0 else 0.0
      val upperBonus = if (topY <= pageHeight * 0.34f) 15.0 else 0.0
      avgFont * 1.6 + minOf(wordsCount, 5) * 6.0 + rightHalfBonus + upperBonus + allCapsPenalty
    } ?: return TitleExtraction("", emptySet())

    val text = bestBlock
      .joinToString(" ") { line -> line.sortedBy { it.x }.joinToString(" ") { it.text } }
      .replace(Regex("\\s+"), " ")
      .trim()
    return TitleExtraction(title = text, titleSpans = bestBlock.flatten().toSet())
  }

  private fun cleanProductTitle(rawTitle: String, otherPageSpans: List<PdfSpan>): String {
    var title = rawTitle
      .replace(Regex("\\s+"), " ")
      .trim()
    if (title.isEmpty()) return ""

    title = title.replace(Regex("""(?i)\bvacio\b"""), "vacío")

    // Palabras presentes en el resto de la página (cuerpo, cuadro de bultos, etiquetas) para reparar erratas OCR de 1 carácter en el título
    val bodyWords = otherPageSpans
      .flatMap { it.text.split(Regex("[^\\p{L}]+")) }
      .filter { it.length >= 4 }
    val bodyWordFreq = bodyWords.groupingBy { normalizeLookup(it) }.eachCount()

    val correctedWords = title.split(" ").map { word ->
      val norm = normalizeLookup(word)
      if (norm.length >= 5 && (bodyWordFreq[norm] ?: 0) == 0) {
        // Buscar si existe en el cuerpo una palabra casi idéntica (distancia 1, ej. "Intensal" -> "Intensa") con mayor respaldo
        val candidate = bodyWords
          .filter { bw ->
            val nbw = normalizeLookup(bw)
            nbw.length >= 4 && isNearDuplicateWord(norm, nbw) && (bodyWordFreq[nbw] ?: 0) >= 1
          }
          .maxByOrNull { bodyWordFreq[normalizeLookup(it)] ?: 0 }

        if (candidate != null) {
          preserveCapitalization(source = word, replacement = candidate)
        } else {
          word
        }
      } else {
        word
      }
    }

    return correctedWords.joinToString(" ").trim()
  }

  private fun preserveCapitalization(source: String, replacement: String): String {
    if (source.isEmpty() || replacement.isEmpty()) return replacement
    return if (source[0].isUpperCase()) {
      replacement.replaceFirstChar { it.uppercase() }
    } else {
      replacement.lowercase()
    }
  }

  private fun extractBulkEntries(
    spans: List<PdfSpan>,
    pageWidth: Float,
    pageHeight: Float,
    tableHeaderY: Float,
  ): List<BulkEntry> {
    val anchor = spans.firstOrNull { span ->
      val norm = normalizeLookup(span.text)
      span.y in (pageHeight * 0.50f)..tableHeaderY && (
        norm.contains("unidades") || norm.contains("bulto") || norm.contains("por bul")
        )
    } ?: return emptyList()

    val minY = anchor.y - 22f
    val maxY = tableHeaderY - 6f
    val boxSpans = spans.filter { span ->
      span.y in minY..maxY &&
        span.x >= pageWidth * 0.45f &&
        !isBulkHeaderNoise(span.text)
    }

    val visualLines = groupIntoVisualLines(boxSpans, yTolerance = 9f)
    val entries = mutableListOf<BulkEntry>()

    for (line in visualLines) {
      val text = joinSpansCleanly(line)
      val fixed = fixGramajeOcr(text)
      val trailingInt = Regex("""(\d{1,3})\s*$""").find(fixed) ?: continue
      val qty = trailingInt.groupValues[1].toIntOrNull() ?: continue
      if (qty !in 1..500) continue

      val beforeQty = fixed.substring(0, trailingInt.range.first).trim().trimEnd('.', ':', '-', ' ')
      if (beforeQty.isEmpty()) continue

      val gramaje = extractSingleGramaje(beforeQty)
      if (gramaje != null || beforeQty.any { it.isLetter() }) {
        entries.add(BulkEntry(label = beforeQty, gramaje = gramaje, unitsPerBulk = qty))
      }
    }

    // Fallback por si el gramaje y su cantidad por bulto quedaron en líneas visuales distintas
    if (entries.isEmpty()) {
      val tokens = boxSpans
        .sortedWith(compareBy<PdfSpan> { it.y }.thenBy { it.x })
        .map { fixGramajeOcr(it.text) }
      var pendingLabel: String? = null
      var pendingGram: String? = null
      for (tok in tokens) {
        val gram = extractSingleGramaje(tok)
        val pureInt = Regex("""^\d{1,3}$""").matchEntire(tok.trim('.', ' '))?.value?.toIntOrNull()
        when {
          gram != null -> {
            pendingLabel = tok
            pendingGram = gram
          }
          pureInt != null && pureInt in 1..500 && pendingGram != null -> {
            entries.add(BulkEntry(label = pendingLabel.orEmpty(), gramaje = pendingGram, unitsPerBulk = pureInt))
            pendingLabel = null
            pendingGram = null
          }
        }
      }
    }

    return entries
  }

  private fun isBulkHeaderNoise(text: String): Boolean {
    val norm = normalizeLookup(text)
    return norm in setOf(
      "cantidad", "de", "unidades", "por", "bulto", "bul",
      "de unidades", "por bulto", "de unidades por bulto", "de unidades por bul"
    )
  }

  private fun extractBarcodeAnchors(
    tableSpans: List<PdfSpan>,
    pageWidth: Float,
  ): List<BarcodeRowAnchor> {
    val rightSpans = tableSpans.filter { it.x >= pageWidth * 0.64f }
    val rightLines = groupIntoVisualLines(rightSpans, yTolerance = 9f)

    val anchors = mutableListOf<BarcodeRowAnchor>()
    for (line in rightLines) {
      val joined = line.sortedBy { it.x }.joinToString(" ") { it.text }
      val match = barcodeCandidateRegex.find(joined) ?: continue
      val cleanedBarcode = normalizeBarcode(match.groupValues[1]) ?: continue
      val avgY = line.map { it.y }.average().toFloat()
      anchors.add(BarcodeRowAnchor(y = avgY, barcode = cleanedBarcode))
    }

    return anchors.sortedBy { it.y }
  }

  internal fun normalizeBarcode(raw: String): String? {
    val digits = raw.filter { it.isDigit() }
    if (digits.length < 11) return null
    if (digits.length == 16 && digits.substring(0, 3) == digits.substring(3, 6)) {
      return digits.substring(3)
    }
    return digits
  }

  private fun splitPresentationAndBulkWeight(rawLeft: String): Pair<String, String?> {
    val cleaned = rawLeft.replace(Regex("""\.{2,}"""), " ").replace(Regex("\\s+"), " ").trim()
    if (cleaned.isEmpty()) return "" to null

    val matches = bulkWeightRegex.findAll(cleaned).toList()
    if (matches.size >= 2) {
      val last = matches.last()
      val weightNum = normalizeBulkWeightNumber(last.groupValues[1])
      val weightUnit = last.groupValues[2].lowercase()
      val before = cleaned.substring(0, last.range.first).trim().trimEnd('.', '-', ' ')
      return before to "$weightNum$weightUnit"
    }

    return cleaned to null
  }

  private fun normalizeBulkWeightNumber(rawNum: String): String {
    val trimmed = rawNum.replace(',', '.').trim()
    return if (trimmed.startsWith(".")) {
      trimmed.trimStart('.')
    } else {
      trimmed
    }
  }

  private fun matchBulkEntry(
    rowIndex: Int,
    totalRows: Int,
    sheetTitle: String,
    presentation: String,
    gramaje: String?,
    bulkEntries: List<BulkEntry>,
  ): BulkEntry? {
    if (bulkEntries.isEmpty()) return null
    if (bulkEntries.size == 1) return bulkEntries[0]

    val titleWords = normalizeLookup(sheetTitle).split(" ").filter { it.length >= 3 }.toSet()
    val normPres = normalizeLookup(presentation)

    // 1. Filtrar primero por gramaje exacto cuando esté disponible
    val sameGramaje = if (gramaje != null) {
      bulkEntries.filter { it.gramaje.equals(gramaje, ignoreCase = true) }
    } else {
      emptyList()
    }

    if (sameGramaje.size == 1) {
      return sameGramaje[0]
    }

    // 2. Si varias entradas comparten el mismo gramaje (ej. Sardinas 170g al natural / aceite / tomate), desempatar por palabras distintivas
    val pool = if (sameGramaje.isNotEmpty()) sameGramaje else bulkEntries
    val byDistinctiveKeyword = pool.firstOrNull { entry ->
      val distinctiveWords = normalizeLookup(entry.label)
        .split(" ")
        .filter { it.length >= 4 && !it.any(Char::isDigit) && it !in brandStopWords && it !in titleWords }
      distinctiveWords.isNotEmpty() && distinctiveWords.any { normPres.contains(it) }
    }
    if (byDistinctiveKeyword != null) return byDistinctiveKeyword

    // 3. Fallback posicional cuando el número de entradas coincide con el número de filas
    if (bulkEntries.size == totalRows && rowIndex in bulkEntries.indices) {
      return bulkEntries[rowIndex]
    }
    return sameGramaje.firstOrNull()
  }

  private fun composeProductName(
    sheetTitle: String,
    rowPresentation: String,
    gramaje: String?,
  ): String {
    val cleanTitle = sheetTitle.trim()
    val cleanRow = rowPresentation
      .replace(Regex("""\.{2,}"""), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
      .trimEnd('.', '-', ' ')

    if (cleanTitle.isEmpty()) return cleanRow
    if (cleanRow.isEmpty()) return listOfNotNull(cleanTitle, gramaje).joinToString(" ")

    val rowWithoutGram = if (gramaje != null) {
      cleanRow.replace(Regex("""(?i)\b${Regex.escape(gramaje)}\b"""), "").replace(Regex("\\s+"), " ").trim()
    } else {
      cleanRow
    }

    val titleNormWords = normalizeLookup(cleanTitle).split(" ").filter { it.length >= 3 }.toSet()
    val rowWords = rowWithoutGram.split(" ").filter { it.isNotBlank() && it != "." }

    val extraVariantWords = rowWords.filter { word ->
      val nw = normalizeLookup(word)
      nw.isNotEmpty() && titleNormWords.none { tw -> tw == nw || isNearDuplicateWord(tw, nw) }
    }

    val variantSuffix = if (
      extraVariantWords.size >= 2 ||
      (extraVariantWords.size == 1 && extraVariantWords[0].length >= 4 && normalizeLookup(extraVariantWords[0]) !in brandStopWords)
    ) {
      extraVariantWords.joinToString(" ")
    } else {
      ""
    }

    val titleAlreadyHasGram = gramaje != null && normalizeLookup(cleanTitle).contains(normalizeLookup(gramaje))
    return buildList {
      add(cleanTitle)
      if (variantSuffix.isNotBlank()) add(variantSuffix)
      if (gramaje != null && !titleAlreadyHasGram) add(gramaje)
    }.joinToString(" ").replace(Regex("\\s+"), " ").trim()
  }

  private fun isNearDuplicateWord(a: String, b: String): Boolean {
    if (abs(a.length - b.length) > 1 || minOf(a.length, b.length) < 4) return false
    var diffs = 0
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
      if (a[i] == b[j]) {
        i++
        j++
      } else {
        diffs++
        if (diffs > 1) return false
        when {
          a.length > b.length -> i++
          b.length > a.length -> j++
          else -> {
            i++
            j++
          }
        }
      }
    }
    return diffs + (a.length - i) + (b.length - j) <= 1
  }

  private fun buildBulkUnitLabel(unitsPerBulk: Int?, bulkWeight: String?): String {
    return when {
      unitsPerBulk != null && !bulkWeight.isNullOrBlank() -> "bulto x $unitsPerBulk u ($bulkWeight)"
      unitsPerBulk != null -> "bulto x $unitsPerBulk u"
      !bulkWeight.isNullOrBlank() -> "bulto ($bulkWeight)"
      else -> "bulto"
    }
  }

  private fun inferCategory(title: String): String {
    val norm = normalizeLookup(title)
    return when {
      norm.contains("cafe") -> "Café"
      norm.contains("arroz") -> "Arroz"
      norm.contains("sardina") || norm.contains("atun") -> "Enlatados"
      norm.contains("leche") -> "Lácteos"
      norm.contains("harina") -> "Harinas"
      norm.contains("aceite") -> "Aceites"
      norm.contains("azucar") -> "Azúcar"
      norm.contains("pasta") || norm.contains("espagueti") -> "Pastas"
      else -> title.split(" ").firstOrNull { it.length >= 3 }.orEmpty()
    }
  }

  internal fun fixGramajeOcr(text: String): String {
    var fixed = normalizeOcrChars(text)
    // 1. Caso de 3+ dígitos ya completos terminados en 0 seguidos de 'o' sobrante (ej. "250og" -> "250g", "100og" -> "100g")
    fixed = fixed.replace(Regex("""(?i)\b(\d{2,}0)[oO]\s*(g|gr|kg|ml|lt)\b"""), "$1$2")
    // 2. Caso de 1-2 dígitos seguidos de 1-2 'o'/'O' donde OCR leyó '0' como 'o' (ej. "20og" -> "200g", "50og" -> "500g", "9oog" -> "900g", "17og" -> "170g", "40og" -> "400g")
    fixed = fixed.replace(Regex("""(?i)\b(\d{1,2})([oO]{1,2})\s*(g|gr|kg|ml|lt)\b""")) { m ->
      val zeros = "0".repeat(m.groupValues[2].length)
      "${m.groupValues[1]}$zeros${m.groupValues[3].lowercase()}"
    }
    // 3. Normalizar "1Kg" -> "1kg", "900 gr" -> "900g"
    fixed = fixed.replace(Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s*kg\b"""), "$1kg")
    fixed = fixed.replace(Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s*gr?\b"""), "$1g")
    return fixed
  }

  private fun extractSingleGramaje(text: String): String? {
    val match = gramajeRegex.find(text) ?: return null
    val num = match.groupValues[1].replace(',', '.')
    val unit = when (val u = match.groupValues[2].lowercase()) {
      "gr" -> "g"
      "kgr" -> "kg"
      else -> u
    }
    return "$num$unit"
  }

  internal fun normalizeOcrChars(raw: String): String {
    return raw
      .replace('\u00A0', ' ')
      .replace('\u0410', 'A').replace('\u0430', 'a')
      .replace('\u0421', 'C').replace('\u0441', 'c')
      .replace('\u0415', 'E').replace('\u0435', 'e')
      .replace('\u041E', 'O').replace('\u043E', 'o')
      .replace('\u0420', 'P').replace('\u0440', 'p')
      .replace('\u0422', 'T')
      .replace('\u041C', 'M')
      .replace('\u041D', 'H')
      .replace('\u0443', 'y')
  }

  private fun normalizeLookup(raw: String): String {
    val decomposed = Normalizer.normalize(normalizeOcrChars(raw).lowercase(), Normalizer.Form.NFD)
    return decomposed
      .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
      .replace(Regex("[^a-z0-9.\\- ]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  private fun groupIntoVisualLines(
    spans: List<PdfSpan>,
    yTolerance: Float,
  ): List<List<PdfSpan>> {
    val sorted = spans.sortedWith(compareBy<PdfSpan> { it.y }.thenBy { it.x })
    val lines = mutableListOf<MutableList<PdfSpan>>()
    for (span in sorted) {
      val target = lines.firstOrNull { line ->
        abs(line.first().y - span.y) <= yTolerance
      }
      if (target != null) {
        target.add(span)
      } else {
        lines.add(mutableListOf(span))
      }
    }
    return lines.map { line -> line.sortedBy { it.x } }.sortedBy { it.first().y }
  }

  private fun joinSpansCleanly(spans: List<PdfSpan>): String {
    if (spans.isEmpty()) return ""
    return spans.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").trim()
  }
}
