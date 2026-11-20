package com.pedidos.proveedores.domain.parser

import com.pedidos.proveedores.domain.model.ProductoImportado
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lector nativo y liviano de libros Excel (.xlsx / OpenXML) sin dependencias pesadas.
 * Lee sharedStrings.xml, workbook.xml, relaciones y todas las hojas de cálculo,
 * respetando referencias de columnas (A..ZZ) para no desfasar celdas vacías.
 */
object XlsxCatalogParser {

  data class XlsxParseOutput(
    val productos: List<ProductoImportado>,
    val advertencia: String? = null,
  )

  private val ole2Header = byteArrayOf(
    0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte()
  )

  fun parse(inputStream: InputStream): XlsxParseOutput {
    val bytes = inputStream.readBytes()
    if (bytes.size >= 4 &&
      bytes[0] == ole2Header[0] &&
      bytes[1] == ole2Header[1] &&
      bytes[2] == ole2Header[2] &&
      bytes[3] == ole2Header[3]
    ) {
      return XlsxParseOutput(
        productos = emptyList(),
        advertencia = "Este archivo usa formato Excel antiguo (.xls). Guárdalo como Excel moderno (.xlsx) o CSV para importarlo automáticamente.",
      )
    }

    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        if (!entry.isDirectory) {
          val normalizedName = entry.name.replace("\\", "/").removePrefix("/")
          entries[normalizedName] = zis.readBytes()
        }
        zis.closeEntry()
        entry = zis.nextEntry
      }
    }

    if (entries.isEmpty()) {
      return XlsxParseOutput(
        productos = emptyList(),
        advertencia = "No se pudo leer el archivo Excel (.xlsx). Verifica que el archivo no esté dañado o protegido con contraseña.",
      )
    }

    val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
    val sheetInfos = resolveSheetEntries(entries)

    if (sheetInfos.isEmpty()) {
      return XlsxParseOutput(
        productos = emptyList(),
        advertencia = "El archivo Excel no contiene hojas de cálculo legibles.",
      )
    }

    val allProducts = mutableListOf<ProductoImportado>()
    var nextTempId = 1
    val useSheetAsCategory = sheetInfos.size > 1

    for ((sheetName, sheetPath) in sheetInfos) {
      val sheetBytes = entries[sheetPath] ?: continue
      val rows = parseWorksheet(sheetBytes, sharedStrings)
      val defaultCat = if (useSheetAsCategory && !isGenericSheetName(sheetName)) sheetName else ""
      val mapped = TabularCatalogMapper.mapRows(
        rows = rows,
        defaultCategory = defaultCat,
        startTempId = nextTempId,
      )
      allProducts.addAll(mapped)
      nextTempId += mapped.size
    }

    val warning = if (allProducts.isEmpty()) {
      "No se detectaron productos en las hojas del Excel. Verifica que tenga columnas con nombre de producto y precio."
    } else null

    return XlsxParseOutput(productos = allProducts, advertencia = warning)
  }

  private fun isGenericSheetName(name: String): Boolean {
    val lower = name.trim().lowercase()
    return Regex("^(hoja|sheet|pagina|page)\\s*\\d*$").matches(lower)
  }

  private fun createSecureDocumentBuilderFactory(): DocumentBuilderFactory {
    return DocumentBuilderFactory.newInstance().apply {
      isNamespaceAware = false
      try {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      } catch (_: Exception) {
        // Algunos parsers XML en Android no soportan todas las features opcionales
      }
      isExpandEntityReferences = false
    }
  }

  private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
    val doc = createSecureDocumentBuilderFactory()
      .newDocumentBuilder()
      .parse(ByteArrayInputStream(xmlBytes))
    val siNodes = doc.getElementsByTagName("si")
    val strings = ArrayList<String>(siNodes.length)
    for (i in 0 until siNodes.length) {
      val siElement = siNodes.item(i) as? Element ?: continue
      val tNodes = siElement.getElementsByTagName("t")
      val sb = StringBuilder()
      for (j in 0 until tNodes.length) {
        sb.append(tNodes.item(j).textContent.orEmpty())
      }
      strings.add(sb.toString())
    }
    return strings
  }

  private fun resolveSheetEntries(entries: Map<String, ByteArray>): List<Pair<String, String>> {
    val workbookBytes = entries["xl/workbook.xml"]
    val relsBytes = entries["xl/_rels/workbook.xml.rels"]

    if (workbookBytes != null && relsBytes != null) {
      val relMap = mutableMapOf<String, String>()
      val relDoc = createSecureDocumentBuilderFactory()
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(relsBytes))
      val relNodes = relDoc.getElementsByTagName("Relationship")
      for (i in 0 until relNodes.length) {
        val el = relNodes.item(i) as? Element ?: continue
        val id = el.getAttribute("Id")
        val target = el.getAttribute("Target").replace("\\", "/").removePrefix("/")
        val fullPath = if (target.startsWith("xl/")) target else "xl/$target"
        if (id.isNotBlank()) relMap[id] = fullPath
      }

      val wbDoc = createSecureDocumentBuilderFactory()
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(workbookBytes))
      val sheetNodes = wbDoc.getElementsByTagName("sheet")
      val result = mutableListOf<Pair<String, String>>()
      for (i in 0 until sheetNodes.length) {
        val el = sheetNodes.item(i) as? Element ?: continue
        val name = el.getAttribute("name").ifBlank { "Hoja ${i + 1}" }
        val rId = el.getAttribute("r:id").ifBlank { el.getAttribute("id") }
        val path = relMap[rId]
        if (path != null && entries.containsKey(path)) {
          result.add(name to path)
        }
      }
      if (result.isNotEmpty()) return result
    }

    // Fallback: cualquier hoja xl/worksheets/sheet*.xml ordenada alfabéticamente
    return entries.keys
      .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
      .sorted()
      .mapIndexed { idx, path -> "Hoja ${idx + 1}" to path }
  }

  private fun parseWorksheet(xmlBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
    val doc = createSecureDocumentBuilderFactory()
      .newDocumentBuilder()
      .parse(ByteArrayInputStream(xmlBytes))
    val rowNodes = doc.getElementsByTagName("row")
    val rows = mutableListOf<List<String>>()

    for (i in 0 until rowNodes.length) {
      val rowEl = rowNodes.item(i) as? Element ?: continue
      val cellNodes = rowEl.getElementsByTagName("c")
      if (cellNodes.length == 0) continue

      val cellsByCol = mutableMapOf<Int, String>()
      var maxCol = -1
      var fallbackCol = 0

      for (j in 0 until cellNodes.length) {
        val cellEl = cellNodes.item(j) as? Element ?: continue
        val ref = cellEl.getAttribute("r")
        val colIdx = if (ref.isNotBlank()) columnRefToIndex(ref) else fallbackCol
        fallbackCol = colIdx + 1
        if (colIdx > maxCol) maxCol = colIdx

        val value = extractCellValue(cellEl, sharedStrings).trim()
        if (value.isNotEmpty()) {
          cellsByCol[colIdx] = value
        }
      }

      if (maxCol >= 0 && cellsByCol.isNotEmpty()) {
        val rowList = (0..maxCol).map { col -> cellsByCol[col].orEmpty() }
        rows.add(rowList)
      }
    }

    return rows
  }

  private fun extractCellValue(cellEl: Element, sharedStrings: List<String>): String {
    val type = cellEl.getAttribute("t")
    return when (type) {
      "s" -> {
        val idx = firstChildText(cellEl, "v")?.trim()?.toIntOrNull()
        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
      }
      "inlineStr" -> {
        val tNodes = cellEl.getElementsByTagName("t")
        buildString {
          for (i in 0 until tNodes.length) {
            append(tNodes.item(i).textContent.orEmpty())
          }
        }
      }
      "b" -> if (firstChildText(cellEl, "v")?.trim() == "1") "Sí" else "No"
      else -> firstChildText(cellEl, "v").orEmpty()
    }
  }

  private fun firstChildText(parent: Element, tagName: String): String? {
    val nodes = parent.getElementsByTagName(tagName)
    return if (nodes.length > 0) nodes.item(0).textContent else null
  }

  internal fun columnRefToIndex(cellRef: String): Int {
    var col = 0
    for (ch in cellRef) {
      if (ch.isLetter()) {
        col = col * 26 + (ch.uppercaseChar() - 'A' + 1)
      } else {
        break
      }
    }
    return (col - 1).coerceAtLeast(0)
  }
}
