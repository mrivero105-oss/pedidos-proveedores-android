package com.pedidos.proveedores.domain.parser

import com.pedidos.proveedores.domain.isaacpos.IsaacPosSyncMatcher
import com.pedidos.proveedores.domain.model.ModoIvaCatalogo
import com.pedidos.proveedores.domain.model.MonedaCatalogo
import com.pedidos.proveedores.domain.model.ProductoImportado
import com.pedidos.proveedores.domain.model.Proveedor

data class WhatsAppTextParseResult(
  val productos: List<ProductoImportado>,
  val monedaSugerida: MonedaCatalogo,
  val modoIvaSugerido: ModoIvaCatalogo,
)

/**
 * Parser puro (offline, JVM-testeable) para listas de precios enviadas por vendedores en mensajes
 * de texto de WhatsApp (con asteriscos, emojis, bultos, Bs. o USD y líneas de ruido).
 */
object WhatsAppCatalogTextParser {

  private val whatsappMarkdownRegex = Regex("[*_~`]+")
  private val leadingBulletRegex = Regex("""^[\s•●▪▫▸▹►▶➤➢\-–—+>✅✔☑📦🛒🔥💥⭐🌟💲💵💰📌📍👉]+""")
  private val emojiRegex = Regex("""[\p{So}\p{Cn}]+""")

  // Líneas informativas típicas de mensajes de WhatsApp que NO son productos
  private val noiseLineRegex = Regex(
    """(?i)^(buenos?\s+d[ií]as|buenas?\s+tardes|buenas?\s+noches|hola\b|saludos|lista\s+de\s+precios|cat[aá]logo\s+de|precios?\s+sujetos|precios?\s+(en|actualizados|vigentes)|tasa\s*(bcv|del\s+d[ií]a)?|pago\s*m[oó]vil|zelle|binance|banco\b|banesco|mercantil|provincial|venezuela\b|bdv\b|cuenta\b|rif\b|c\.?i\.?\b|tel[eé]fono|whatsapp|contacto|direcci[oó]n|horario|despacho|delivery|env[ií]os|pedido\s+m[ií]nimo|cr[eé]dito|nota\s*:|ojo\s*:|disponible|promoci[oó]n\s+v[aá]lida|gracias\b|atentamente)"""
  )
  private val bankAccountDigitsRegex = Regex("""\b\d{4}[-\s]?\d{4}[-\s]?\d{2}[-\s]?\d{10}\b""")
  private val phoneOnlyRegex = Regex("""^(?:\+?\d[\d\s\-().]{7,})$""")
  private val dateOnlyRegex = Regex("""^\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}$""")

  // Precio al final de la línea o separado por ":", "-", "->", "=", "/", "a"
  private val trailingPriceRegex = Regex(
    """(?i)(?:^|[\s:;\-–—=>/·|])(?:(us\${'$'}|usd|\${'$'}|bs\.?s?|ves|€|eur)\s*)?(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})|\d+(?:[.,]\d{1,2})?)\s*(usd|us\${'$'}|\${'$'}|bs\.?s?|ves|€|eur)?(?:\s*(?:\+\s*iva|sin\s+iva|iva\s+inc\.?|c/u|cada\s+uno|x\s*u|neto))?\s*$"""
  )
  private val leadingPriceRegex = Regex(
    """(?i)^\s*(us\${'$'}|usd|\${'$'}|bs\.?s?|ves)\s*(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)\s*[-–—:·|]\s*(.+)$"""
  )
  private val leadingSkuRegex = Regex("""^(?:cod\.?\s*:?\s*)?(\d{6,14}|[A-Z]{2,5}[-_]?\d{3,8})\s*[-–—:|]?\s+(.+)$""", RegexOption.IGNORE_CASE)
  private val packUnitRegex = Regex(
    """(?i)\b((?:bulto|bto|caja|cja|cj|pack|pk|display|ristra|fardo|paquete|paca|carton|bandeja|docena)\s*(?:de|x|×|\*)?\s*\d{1,4}|\b[x×*]\s*\d{1,4}\s*(?:u|und|unid|unidades)?)\b"""
  )

  private val vesIndicatorRegex = Regex("""(?i)\b(bs\.?s?|bol[ií]vares|ves)\b""")
  private val usdIndicatorRegex = Regex("""(?i)(us\${'$'}|\busd\b|\${'$'}|\bd[oó]lares?\b)""")
  private val plusIvaIndicatorRegex = Regex("""(?i)(\+\s*iva|\bsin\s+iva\b|\bno\s+incluye\s+iva\b|\bm[aá]s\s+iva\b)""")

  fun parse(rawText: String, startTempId: Int = 1): WhatsAppTextParseResult {
    if (rawText.isBlank()) {
      return WhatsAppTextParseResult(
        productos = emptyList(),
        monedaSugerida = MonedaCatalogo.USD,
        modoIvaSugerido = ModoIvaCatalogo.INCLUIDO,
      )
    }

    val lines = rawText.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val result = mutableListOf<ProductoImportado>()
    val seenKeys = mutableSetOf<String>()
    var nextId = startTempId

    var vesHits = 0
    var usdHits = 0
    var hasPlusIvaHint = plusIvaIndicatorRegex.containsMatchIn(rawText)

    for (rawLine in lines) {
      val cleanLine = cleanLineText(rawLine)
      if (cleanLine.length < 3) continue
      if (noiseLineRegex.containsMatchIn(cleanLine)) {
        if (vesIndicatorRegex.containsMatchIn(cleanLine)) vesHits++
        if (usdIndicatorRegex.containsMatchIn(cleanLine)) usdHits++
        if (plusIvaIndicatorRegex.containsMatchIn(cleanLine)) hasPlusIvaHint = true
        continue
      }
      if (bankAccountDigitsRegex.containsMatchIn(cleanLine)) continue
      if (phoneOnlyRegex.matches(cleanLine) || dateOnlyRegex.matches(cleanLine)) continue

      // Ignorar líneas que son únicamente un encabezado en mayúsculas sin números (ej. "VIVERES", "BEBIDAS")
      val hasAnyDigit = cleanLine.any { it.isDigit() }
      if (!hasAnyDigit) continue

      val parsedLine = parseSingleProductLine(cleanLine) ?: continue
      if (parsedLine.currencyHint == MonedaCatalogo.VES) vesHits++
      if (parsedLine.currencyHint == MonedaCatalogo.USD) usdHits++

      val key = "${parsedLine.codigo.lowercase()}|${parsedLine.nombre.lowercase()}|${parsedLine.unidad.lowercase()}"
      if (seenKeys.add(key)) {
        result.add(
          ProductoImportado(
            tempId = nextId++,
            codigo = parsedLine.codigo,
            nombre = parsedLine.nombre,
            unidad = parsedLine.unidad,
            precio = parsedLine.precio,
            categoria = "",
            seleccionado = true,
          )
        )
      }
    }

    val monedaSugerida = if (vesHits > usdHits && vesHits > 0) {
      MonedaCatalogo.VES
    } else if (vesHits == 0 && usdHits == 0 && vesIndicatorRegex.containsMatchIn(rawText) && !usdIndicatorRegex.containsMatchIn(rawText)) {
      MonedaCatalogo.VES
    } else {
      MonedaCatalogo.USD
    }

    val modoIvaSugerido = if (hasPlusIvaHint) {
      ModoIvaCatalogo.SOLO_GRAVADOS
    } else {
      ModoIvaCatalogo.INCLUIDO
    }

    return WhatsAppTextParseResult(
      productos = result,
      monedaSugerida = monedaSugerida,
      modoIvaSugerido = modoIvaSugerido,
    )
  }

  private data class ParsedLine(
    val codigo: String,
    val nombre: String,
    val unidad: String,
    val precio: Double,
    val currencyHint: MonedaCatalogo?,
  )

  private fun parseSingleProductLine(line: String): ParsedLine? {
    // 1. Formato: "$18.50 - Harina PAN 1kg x 20" o "Bs 1200 - Aceite 1L"
    leadingPriceRegex.find(line)?.let { m ->
      val cur = detectCurrency(m.groupValues[1])
      val price = PriceParser.parse(m.groupValues[2])
      val rest = m.groupValues[3].trim()
      if (price != null && price > 0.0 && rest.length >= 2 && rest.any { it.isLetter() }) {
        return finalizeParsedLine(rest, price, cur)
      }
    }

    // 2. Formato más común en WhatsApp: "HARINA PAN 1KG BULTO X 20 ... $18.50" / "Refresco Pepsi 1L 5,00$" / "Azúcar 1kg: 1.25"
    val match = trailingPriceRegex.find(line) ?: return null
    val prefixCur = match.groupValues[1]
    val numRaw = match.groupValues[2]
    val suffixCur = match.groupValues[3]
    val hasExplicitCurrency = prefixCur.isNotBlank() || suffixCur.isNotBlank()

    // Evitar confundir medidas finales ("500g", "1kg", "200ml", "x 24") con un precio cuando no hay moneda
    val beforeMatch = line.substring(0, match.range.first).trim()
    if (beforeMatch.length < 2 || !beforeMatch.any { it.isLetter() }) return null
    if (!hasExplicitCurrency) {
      // Si el número estaba justo después de "x" (ej. "Harina 1kg x 20"), NO es un precio
      if (Regex("""(?i)(?:^|\s)[x×*]\s*$""").containsMatchIn(beforeMatch)) return null
      // Si no tiene separador decimal ni separador claro (: - = > / ...), requerir moneda o decimal para no capturar años o tallas
      val separatorSlice = line.substring(match.range.first, match.range.last + 1)
      val hasSeparator = separatorSlice.any { it in ":=>/-–—·|" } || numRaw.contains('.') || numRaw.contains(',')
      if (!hasSeparator) return null
    }

    val price = PriceParser.parse(numRaw) ?: return null
    if (price <= 0.0 || price > 100_000_000.0) return null

    val currency = detectCurrency(prefixCur.ifBlank { suffixCur })
    return finalizeParsedLine(beforeMatch, price, currency)
  }

  private fun finalizeParsedLine(rawDesc: String, price: Double, currency: MonedaCatalogo?): ParsedLine? {
    var desc = rawDesc
      .trim()
      .trimEnd('.', ':', '-', '–', '—', '=', '>', '/', '|', '·', ',')
      .trim()

    var sku = ""
    leadingSkuRegex.find(desc)?.let { m ->
      sku = m.groupValues[1].trim()
      desc = m.groupValues[2].trim()
    }

    if (desc.length < 2 || !desc.any { it.isLetter() }) return null

    // Extraer unidad/empaque si aparece en la línea (conservando el nombre limpio)
    val unitMatch = packUnitRegex.find(desc)
    val detectedUnit = unitMatch?.groupValues?.getOrNull(1)?.trim()
      ?.replace(Regex("\\s+"), " ")
      ?: "u"

    return ParsedLine(
      codigo = sku,
      nombre = desc,
      unidad = detectedUnit,
      precio = price,
      currencyHint = currency,
    )
  }

  private fun detectCurrency(token: String): MonedaCatalogo? {
    val t = token.trim().lowercase()
    return when {
      t.isEmpty() -> null
      t.startsWith("bs") || t == "ves" -> MonedaCatalogo.VES
      t.contains("$") || t.contains("usd") -> MonedaCatalogo.USD
      else -> null
    }
  }

  private fun cleanLineText(raw: String): String {
    return raw
      .replace('\u00A0', ' ')
      .replace(whatsappMarkdownRegex, " ")
      .replace(leadingBulletRegex, "")
      .replace(emojiRegex, " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }

  /**
   * Sugiere automáticamente un proveedor local si su nombre (o palabras distintivas del mismo)
   * aparece dentro del nombre del archivo o del texto compartido desde WhatsApp.
   */
  fun suggestProveedorId(
    fileNameOrHint: String,
    proveedores: List<Proveedor>,
  ): Long? {
    if (fileNameOrHint.isBlank() || proveedores.isEmpty()) return null
    val normHint = " ${IsaacPosSyncMatcher.normalizeKey(fileNameOrHint)} "
    if (normHint.isBlank()) return null

    val genericWords = setOf(
      "distribuidora", "comercial", "inversiones", "representaciones", "importadora",
      "alimentos", "productos", "servicios", "grupo", "corporacion", "compania",
      "ca", "sa", "srl", "de", "del", "la", "las", "los", "el", "y", "and",
      "barcelona", "oriente", "venezuela", "lista", "precios", "catalogo",
    )

    var bestId: Long? = null
    var bestScore = 0

    for (prov in proveedores) {
      val normProv = IsaacPosSyncMatcher.normalizeKey(prov.nombre)
      if (normProv.length < 3) continue

      if (normHint.contains(" $normProv ")) {
        return prov.id
      }

      val tokens = normProv.split(' ').filter { it.length >= 4 && it !in genericWords }
      var score = 0
      for (tk in tokens) {
        if (normHint.contains(" $tk ") || normHint.contains(tk)) {
          score += tk.length
        }
      }
      if (score > bestScore && score >= 5) {
        bestScore = score
        bestId = prov.id
      }
    }

    return bestId
  }
}
