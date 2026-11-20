package com.pedidos.proveedores.domain.parser

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object PriceParser {

  private val currencyPrefixRegex = Regex(
    """(?i)^(usd|us\${'$'}|u\${'$'}s|ars|mxn|cop|clp|pen|uyu|bs\.?|s/|eur|€|\${'$'}|£)\s*"""
  )
  private val currencySuffixRegex = Regex(
    """(?i)\s*(usd|ars|mxn|cop|clp|pen|uyu|bs\.?|eur|€|\${'$'}|£)$"""
  )
  private val numericCandidateRegex = Regex("""^\d{1,3}([.,]\d{3})*([.,]\d{1,4})?$|^\d+([.,]\d{1,4})?$""")

  /**
   * Parsea precios en formatos habituales de América Latina, España y EE. UU.:
   * - "$1.250,50" -> 1250.50
   * - "$1,250.50" -> 1250.50
   * - "18.500" -> 18500.0
   * - "12,50" -> 12.50
   * - "USD 45.90" -> 45.90
   */
  fun parse(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    var cleaned = raw.trim()
      .replace("\u00A0", " ")
      .replace(currencyPrefixRegex, "")
      .replace(currencySuffixRegex, "")
      .replace(" ", "")
      .trim()

    if (cleaned.isEmpty()) return null
    if (!numericCandidateRegex.matches(cleaned)) return null

    val lastDot = cleaned.lastIndexOf('.')
    val lastComma = cleaned.lastIndexOf(',')

    val normalized = when {
      // Ambos separadores presentes: el último es decimal
      lastDot >= 0 && lastComma >= 0 -> {
        if (lastComma > lastDot) {
          cleaned.replace(".", "").replace(",", ".")
        } else {
          cleaned.replace(",", "")
        }
      }
      // Solo comas
      lastComma >= 0 -> {
        val commaCount = cleaned.count { it == ',' }
        val digitsAfter = cleaned.length - lastComma - 1
        if (commaCount == 1 && digitsAfter in 1..2) {
          cleaned.replace(",", ".")
        } else if (digitsAfter == 3 && cleaned.substring(0, lastComma).length in 1..3) {
          // Ej: 1,500 o 12,500 -> miles
          cleaned.replace(",", "")
        } else if (commaCount > 1) {
          cleaned.replace(",", "")
        } else {
          cleaned.replace(",", ".")
        }
      }
      // Solo puntos
      lastDot >= 0 -> {
        val dotCount = cleaned.count { it == '.' }
        val digitsAfter = cleaned.length - lastDot - 1
        if (dotCount > 1) {
          // Ej: 1.250.000 -> miles
          cleaned.replace(".", "")
        } else if (digitsAfter == 3 && cleaned.substring(0, lastDot).length in 1..3 && cleaned.substring(0, lastDot) != "0") {
          // Ej: 18.500 -> miles (muy frecuente en listas de proveedores en español)
          cleaned.replace(".", "")
        } else {
          cleaned
        }
      }
      else -> cleaned
    }

    val value = normalized.toDoubleOrNull() ?: return null
    return if (value >= 0.0 && value.isFinite()) value else null
  }

  fun formatCurrency(amount: Double): String {
    val symbols = DecimalFormatSymbols(Locale.forLanguageTag("es-ES")).apply {
      groupingSeparator = '.'
      decimalSeparator = ','
    }
    val formatter = DecimalFormat("#,##0.00", symbols)
    return "$${formatter.format(amount)}"
  }

  fun formatQuantity(quantity: Double): String {
    return if (quantity % 1.0 == 0.0) {
      quantity.toLong().toString()
    } else {
      val symbols = DecimalFormatSymbols(Locale.forLanguageTag("es-ES")).apply {
        decimalSeparator = ','
      }
      DecimalFormat("0.##", symbols).format(quantity)
    }
  }
}
