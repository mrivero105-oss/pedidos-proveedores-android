package com.pedidos.proveedores.domain.parser

import com.pedidos.proveedores.domain.model.ProductoImportado
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Parser CSV/TSV robusto para catálogos de proveedores:
 * - Soporta UTF-8 (con o sin BOM) y hace fallback automático a Windows-1252 / ISO-8859-1.
 * - Autodetecta delimitador (',', ';', '\t').
 * - Respeta comillas y saltos de línea dentro de celdas según RFC 4180.
 */
object CsvCatalogParser {

  fun parse(inputStream: InputStream): List<ProductoImportado> {
    val bytes = inputStream.readBytes()
    val text = decodeText(bytes)
    return parseText(text)
  }

  fun parseText(rawText: String): List<ProductoImportado> {
    val cleaned = rawText.removePrefix("\uFEFF")
    if (cleaned.isBlank()) return emptyList()

    val delimiter = detectDelimiter(cleaned)
    val rows = parseDelimited(cleaned, delimiter)
    return TabularCatalogMapper.mapRows(rows)
  }

  private fun decodeText(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    // Detectar BOM UTF-8
    val startOffset = if (bytes.size >= 3 &&
      bytes[0] == 0xEF.toByte() &&
      bytes[1] == 0xBB.toByte() &&
      bytes[2] == 0xBF.toByte()
    ) 3 else 0

    return try {
      val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
      decoder.decode(ByteBuffer.wrap(bytes, startOffset, bytes.size - startOffset)).toString()
    } catch (_: CharacterCodingException) {
      // Excel en español suele exportar CSV en Windows-1252 / ISO-8859-1
      String(bytes, Charset.forName("windows-1252"))
    }
  }

  internal fun detectDelimiter(text: String): Char {
    val sampleLines = text.lineSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .take(10)
      .toList()
    if (sampleLines.isEmpty()) return ','

    val candidates = listOf(';', ',', '\t')
    return candidates.maxByOrNull { delim ->
      val counts = sampleLines.map { line -> countUnquoted(line, delim) }
      val nonZero = counts.count { it > 0 }
      val total = counts.sum()
      // Premia delimitadores consistentes en la mayoría de las líneas
      nonZero * 100 + total
    } ?: ','
  }

  private fun countUnquoted(line: String, delimiter: Char): Int {
    var inQuotes = false
    var count = 0
    for (ch in line) {
      if (ch == '"') inQuotes = !inQuotes
      else if (ch == delimiter && !inQuotes) count++
    }
    return count
  }

  internal fun parseDelimited(text: String, delimiter: Char): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val currentRow = mutableListOf<String>()
    val currentCell = StringBuilder()
    var inQuotes = false
    var i = 0

    while (i < text.length) {
      val ch = text[i]
      when {
        inQuotes -> {
          if (ch == '"') {
            if (i + 1 < text.length && text[i + 1] == '"') {
              currentCell.append('"')
              i++
            } else {
              inQuotes = false
            }
          } else {
            currentCell.append(ch)
          }
        }
        ch == '"' -> {
          inQuotes = true
        }
        ch == delimiter -> {
          currentRow.add(currentCell.toString().trim())
          currentCell.clear()
        }
        ch == '\r' -> {
          if (i + 1 < text.length && text[i + 1] == '\n') i++
          currentRow.add(currentCell.toString().trim())
          currentCell.clear()
          if (currentRow.any { it.isNotBlank() }) rows.add(currentRow.toList())
          currentRow.clear()
        }
        ch == '\n' -> {
          currentRow.add(currentCell.toString().trim())
          currentCell.clear()
          if (currentRow.any { it.isNotBlank() }) rows.add(currentRow.toList())
          currentRow.clear()
        }
        else -> currentCell.append(ch)
      }
      i++
    }

    if (currentCell.isNotEmpty() || currentRow.isNotEmpty()) {
      currentRow.add(currentCell.toString().trim())
      if (currentRow.any { it.isNotBlank() }) rows.add(currentRow.toList())
    }

    return rows
  }
}
