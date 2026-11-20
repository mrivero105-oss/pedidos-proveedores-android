package com.pedidos.proveedores.domain.parser

import com.pedidos.proveedores.domain.model.ProductoImportado

data class ImageOcrPage(
  val index1Based: Int,
  val displayName: String,
  val width: Float,
  val height: Float,
  val spans: List<PdfSpan>,
  val plainText: String,
  val thumbPath: String,
)

/**
 * Convierte los resultados OCR (offline) de una o varias imágenes en productos importables.
 * - Si la imagen es una ficha técnica de catálogo (con unidades por bulto / códigos de barras),
 *   aplica PdfProductSheetParser.
 * - Si la imagen es una lista de precios, aplica PdfTextProductExtractor.
 * - Si la imagen es la foto de un solo producto sin formato de tabla, genera 1 ítem listo para
 *   revisar/editar con su foto ya asociada.
 */
object ImageCatalogTextMapper {

  private val codeOrBarcodeRegex = Regex("""\b(\d[\d\s]{10,16}\d)\b""")

  fun mapOcrPages(pages: List<ImageOcrPage>): List<ProductoImportado> {
    val allProducts = mutableListOf<ProductoImportado>()
    var nextTempId = 1

    for (page in pages) {
      val pageProducts = extractFromSingleImage(page, startTempId = nextTempId)
      allProducts.addAll(pageProducts)
      nextTempId += pageProducts.size
    }

    return allProducts
  }

  private fun extractFromSingleImage(
    page: ImageOcrPage,
    startTempId: Int,
  ): List<ProductoImportado> {
    // 1. Intentar extractor de fichas técnicas por coordenadas (normalizado a ancho 612pt)
    if (page.spans.isNotEmpty() && page.width > 0f && page.height > 0f) {
      val scale = 612f / page.width
      val scaledLayout = PdfPageLayout(
        pageNumber = page.index1Based,
        width = 612f,
        height = page.height * scale,
        spans = page.spans.map { s ->
          PdfSpan(
            x = s.x * scale,
            y = s.y * scale,
            width = s.width * scale,
            height = s.height * scale,
            fontSize = s.fontSize * scale,
            text = s.text,
          )
        },
      )
      val sheetItems = PdfProductSheetParser.extractFromPages(listOf(scaledLayout))
      if (sheetItems.isNotEmpty()) {
        return sheetItems.mapIndexed { idx, item ->
          item.copy(
            tempId = startTempId + idx,
            imagenRuta = page.thumbPath,
            paginaPdf = page.index1Based,
          )
        }
      }
    }

    // 2. Intentar extractor de listas de precios / filas con precio o código
    if (page.plainText.isNotBlank()) {
      val lineItems = PdfTextProductExtractor.extractFromText(page.plainText)
      if (lineItems.isNotEmpty()) {
        return lineItems.mapIndexed { idx, item ->
          item.copy(
            tempId = startTempId + idx,
            imagenRuta = page.thumbPath,
            paginaPdf = page.index1Based,
          )
        }
      }
    }

    // 3. Fallback inteligente: la imagen parece ser la foto individual de un producto
    return listOf(buildSinglePhotoDraftProduct(page, startTempId))
  }

  private fun buildSinglePhotoDraftProduct(
    page: ImageOcrPage,
    tempId: Int,
  ): ProductoImportado {
    val lines = page.plainText
      .lineSequence()
      .map { PdfProductSheetParser.fixGramajeOcr(it).trim() }
      .filter { it.length >= 2 }
      .toList()

    // Detectar posible precio suelto en la foto
    val detectedPrice = lines.firstNotNullOfOrNull { line ->
      val m = Regex("""(?i)(?:usd|us\${'$'}|ars|bs\.?|s/|€|\${'$'})?\s*\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})?""")
        .findAll(line)
        .mapNotNull { PriceParser.parse(it.value) }
        .firstOrNull { it > 0.0 && it < 10_000_000.0 }
      m
    }

    // Detectar posible código de barras
    val detectedBarcode = lines.firstNotNullOfOrNull { line ->
      codeOrBarcodeRegex.find(line)?.groupValues?.get(1)?.let { PdfProductSheetParser.normalizeBarcode(it) }
    }.orEmpty()

    // Mejor línea descriptiva (con letras) como nombre sugerido; si no hay texto, nombre limpio del archivo
    val suggestedName = lines
      .filter { ln -> ln.count { it.isLetter() } >= 3 && PriceParser.parse(ln) == null }
      .maxByOrNull { ln ->
        // Premiar líneas de longitud típica de nombre de producto (6..45 chars)
        if (ln.length in 6..45) ln.length + 30 else ln.length
      }
      ?.take(60)
      ?: cleanDisplayNameToProductTitle(page.displayName, page.index1Based)

    val unit = TabularCatalogMapper.inferUnitFromName(suggestedName) ?: "u"

    return ProductoImportado(
      tempId = tempId,
      codigo = detectedBarcode,
      nombre = suggestedName,
      unidad = unit,
      precio = detectedPrice,
      categoria = "",
      imagenRuta = page.thumbPath,
      paginaPdf = page.index1Based,
      seleccionado = true,
    )
  }

  private fun cleanDisplayNameToProductTitle(fileName: String, index: Int): String {
    val base = fileName.substringBeforeLast('.')
      .replace(Regex("[_-]+"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
    val looksGenericCameraName = Regex("(?i)^(img|pxl|dsc|photo|whatsapp image|screenshot|captura).*").matches(base)
    return if (base.length >= 3 && !looksGenericCameraName) {
      base.replaceFirstChar { it.uppercase() }
    } else {
      "Producto de imagen $index"
    }
  }
}
