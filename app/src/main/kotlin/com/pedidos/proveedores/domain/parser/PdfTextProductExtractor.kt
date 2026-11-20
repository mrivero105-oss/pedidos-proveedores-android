package com.pedidos.proveedores.domain.parser

import com.pedidos.proveedores.domain.model.ProductoImportado

/**
 * Extrae productos estructurados a partir del texto obtenido de un catálogo PDF.
 * Lógica pura en Kotlin (testeable en JVM) que reconoce líneas de listas de precios
 * con código/SKU opcional, nombre del producto, unidad opcional y precio al final.
 */
object PdfTextProductExtractor {

  // Precio al final de línea: "$1.250,50", "USD 45.00", "18.500", "12,50"
  private val trailingPriceRegex = Regex(
    """(?i)(?:^|\s)((?:usd|us\${'$'}|u\${'$'}s|ars|mxn|cop|clp|pen|uyu|bs\.?|s/|eur|€|\${'$'}|£)?\s*\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2}))\s*$"""
  )

  // Código/SKU típico al inicio: "ART-001", "1045", "P_204", "SKU123"
  private val leadingCodeRegex = Regex(
    """^([A-Za-z]{0,5}[-_.]?\d{2,10}[A-Za-z0-9\-_.]*)\s+(.+)$"""
  )

  // Separador tabular dentro de PDFs (2+ espacios o tab)
  private val columnSplitRegex = Regex("""\s{2,}|\t+""")

  private val noiseLineRegex = Regex(
    """(?i)^(p[aá]gina\s*\d+|page\s*\d+|tel[eé]fono|whatsapp|email|correo|www\.|http|direcci[oó]n|lista\s+de\s+precios|cat[aá]logo|precios\s+sujetos|vigencia|subtotal|total\s+general|iva\s+incluido).*"""
  )

  private val headerOnlyWords = setOf(
    "codigo", "sku", "producto", "descripcion", "detalle", "articulo", "precio", "unidad", "lista", "presentacion", "importe", "valor"
  )

  fun extractFromText(rawText: String): List<ProductoImportado> {
    if (rawText.isBlank()) return emptyList()

    val rawLines = rawText.lineSequence()
      .map { it.replace('\u00A0', ' ').trim() }
      .filter { it.length >= 3 }
      .toList()

    val productos = mutableListOf<ProductoImportado>()
    val seenKeys = mutableSetOf<String>()
    var tempId = 1

    for (line in rawLines) {
      if (isNoiseLine(line)) continue

      val parsed = parseTabularLine(line) ?: parseFreeformLine(line) ?: continue
      val cleanName = parsed.nombre.trim().trim('-', '|', '•', '*', '.')
      if (cleanName.length < 2 || !cleanName.any { it.isLetter() }) continue

      val dedupKey = "${parsed.codigo.lowercase()}|${cleanName.lowercase()}|${parsed.precio}"
      if (!seenKeys.add(dedupKey)) continue

      productos.add(
        ProductoImportado(
          tempId = tempId++,
          codigo = parsed.codigo.trim(),
          nombre = cleanName,
          unidad = parsed.unidad.ifBlank { TabularCatalogMapper.inferUnitFromName(cleanName) ?: "u" },
          precio = parsed.precio,
          categoria = "",
          seleccionado = true,
        )
      )
    }

    return productos
  }

  private data class ParsedRow(
    val codigo: String,
    val nombre: String,
    val unidad: String,
    val precio: Double?,
  )

  private fun parseTabularLine(line: String): ParsedRow? {
    val parts = line.split(columnSplitRegex).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return null

    // Si casi todos los tokens son palabras de encabezado de tabla, descartar
    val headerMatches = parts.count { TabularCatalogMapper.normalizeHeader(it) in headerOnlyWords }
    if (headerMatches >= 2) return null

    // Comprobar si la última columna es precio
    val lastPrice = PriceParser.parse(parts.last())
    val remaining = if (lastPrice != null) parts.dropLast(1) else parts
    if (remaining.isEmpty()) return null

    return when (remaining.size) {
      1 -> {
        val only = remaining[0]
        if (lastPrice == null) return null
        val (code, name) = splitLeadingCode(only)
        ParsedRow(codigo = code, nombre = name, unidad = "", precio = lastPrice)
      }
      2 -> {
        val first = remaining[0]
        val second = remaining[1]
        if (looksLikeCode(first) && second.any { it.isLetter() }) {
          ParsedRow(codigo = first, nombre = second, unidad = "", precio = lastPrice)
        } else if (looksLikeUnit(second) && first.any { it.isLetter() }) {
          val (code, name) = splitLeadingCode(first)
          ParsedRow(codigo = code, nombre = name, unidad = second, precio = lastPrice)
        } else if (lastPrice != null) {
          val (code, name) = splitLeadingCode("$first $second")
          ParsedRow(codigo = code, nombre = name, unidad = "", precio = lastPrice)
        } else {
          null
        }
      }
      else -> {
        val first = remaining.first()
        val last = remaining.last()
        val hasCode = looksLikeCode(first)
        val hasUnit = looksLikeUnit(last)
        val startIdx = if (hasCode) 1 else 0
        val endIdx = if (hasUnit && remaining.size - startIdx >= 2) remaining.size - 1 else remaining.size
        val name = remaining.subList(startIdx, endIdx).joinToString(" ")
        if (name.isBlank()) return null
        ParsedRow(
          codigo = if (hasCode) first else "",
          nombre = name,
          unidad = if (hasUnit && endIdx < remaining.size) last else "",
          precio = lastPrice,
        )
      }
    }
  }

  private fun parseFreeformLine(line: String): ParsedRow? {
    val priceMatch = trailingPriceRegex.find(line)
    if (priceMatch != null) {
      val priceToken = priceMatch.groupValues[1]
      val parsedPrice = PriceParser.parse(priceToken)
      if (parsedPrice != null) {
        val beforePrice = line.substring(0, priceMatch.range.first).trim().trimEnd('-', '.', ':', '|')
        if (beforePrice.length >= 2 && beforePrice.any { it.isLetter() }) {
          val (code, name) = splitLeadingCode(beforePrice)
          return ParsedRow(codigo = code, nombre = name, unidad = "", precio = parsedPrice)
        }
      }
    }

    // Fallback sin precio: solo aceptar si tiene código claro al inicio + descripción
    val codeMatch = leadingCodeRegex.matchEntire(line)
    if (codeMatch != null) {
      val code = codeMatch.groupValues[1]
      val rest = codeMatch.groupValues[2].trim()
      if (rest.length >= 4 && rest.count { it.isLetter() } >= 3 && !isDateOrPhone(code, rest)) {
        return ParsedRow(codigo = code, nombre = rest, unidad = "", precio = null)
      }
    }

    return null
  }

  private fun splitLeadingCode(text: String): Pair<String, String> {
    val match = leadingCodeRegex.matchEntire(text.trim())
    if (match != null) {
      val candidateCode = match.groupValues[1]
      val rest = match.groupValues[2].trim()
      // Evitar tomar cantidades como "10 kg de harina" como si "10" fuera un código
      val startsWithUnit = Regex("""(?i)^(kg|g|gr|l|lt|ml|cc|cm|mm|m|x|un|u|u\.)\b""").containsMatchIn(rest)
      if (!startsWithUnit && rest.any { it.isLetter() } && candidateCode.length >= 3) {
        return candidateCode to rest
      }
    }
    return "" to text.trim()
  }

  private fun looksLikeCode(token: String): Boolean {
    if (token.length !in 2..18 || token.contains(' ')) return false
    return token.any { it.isDigit() } && token.all { it.isLetterOrDigit() || it in "-_./" }
  }

  private fun looksLikeUnit(token: String): Boolean {
    if (token.length > 14) return false
    return Regex(
      """(?i)^(u|un|und|unid|unidad|unidades|kg|kilo|kilos|g|gr|l|lt|lts|litro|litros|ml|caja|cajas|caj|pack|paq|paquete|bulto|bolsa|doc|docena|fardo|botella|lata|m|mt|mts|x\d+)$"""
    ).matches(token.trim())
  }

  private fun isNoiseLine(line: String): Boolean {
    if (noiseLineRegex.matches(line)) return true
    if (line.all { !it.isLetterOrDigit() }) return true
    val words = line.split(Regex("\\s+")).map { TabularCatalogMapper.normalizeHeader(it) }
    if (words.size in 2..6 && words.count { it in headerOnlyWords } >= 2 && words.none { it.any(Char::isDigit) }) {
      return true
    }
    return false
  }

  private fun isDateOrPhone(code: String, rest: String): Boolean {
    if (Regex("""^\d{1,2}/\d{1,2}/\d{2,4}$""").matches(code)) return true
    if (rest.startsWith("-") && rest.drop(1).trim().all { it.isDigit() || it == '-' || it == ' ' }) return true
    return false
  }
}
