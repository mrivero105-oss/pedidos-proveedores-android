package com.pedidos.proveedores.domain.parser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.pedidos.proveedores.data.local.AiSettings
import com.pedidos.proveedores.domain.model.CatalogoFormato
import com.pedidos.proveedores.domain.model.ResultadoImportacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class CatalogImporter(
  private val context: Context,
  private val geminiExtractor: GeminiCatalogExtractor = GeminiCatalogExtractor(),
) {

  suspend fun importFromUri(
    proveedorId: Long,
    uri: Uri,
    aiSettings: AiSettings = AiSettings(),
    onProgress: (current: Int, total: Int, statusText: String) -> Unit = { _, _, _ -> },
  ): ResultadoImportacion =
    withContext(Dispatchers.IO) {
      val displayName = resolveDisplayName(uri)
      val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
      val formato = detectFormat(displayName, mimeType)

      if (formato == CatalogoFormato.IMAGEN) {
        return@withContext importImagesFromUris(
          proveedorId = proveedorId,
          uris = listOf(uri),
          aiSettings = aiSettings,
          onProgress = onProgress,
        )
      }

      val dir = File(context.filesDir, "catalogos/prov_$proveedorId").apply { mkdirs() }
      val safeName = sanitizeFileName(displayName)
      val targetFile = File(dir, "${System.currentTimeMillis()}_$safeName")

      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(targetFile).use { output ->
          input.copyTo(output)
        }
      } ?: error("No se pudo abrir el archivo seleccionado.")

      when (formato) {
        CatalogoFormato.PDF -> parsePdfOffline(proveedorId, targetFile, displayName)
        CatalogoFormato.XLSX -> parseExcel(targetFile, displayName)
        CatalogoFormato.CSV -> parseCsv(targetFile, displayName)
        CatalogoFormato.IMAGEN -> error("Formato imagen gestionado arriba")
      }
    }

  suspend fun importImagesFromUris(
    proveedorId: Long,
    uris: List<Uri>,
    aiSettings: AiSettings = AiSettings(),
    onProgress: (current: Int, total: Int, statusText: String) -> Unit = { _, _, _ -> },
  ): ResultadoImportacion = withContext(Dispatchers.IO) {
    require(uris.isNotEmpty()) { "Selecciona al menos una imagen." }

    val batchStamp = System.currentTimeMillis()
    val imgDir = File(context.filesDir, "catalogos/prov_$proveedorId/imgs_$batchStamp")
    val thumbsDir = File(context.filesDir, "catalogos/prov_$proveedorId/thumbs_$batchStamp")

    val savedPairs = mutableListOf<Triple<String, MlKitImageOcrExtractor.SavedImagePair, Int>>()
    uris.forEachIndexed { idx, uri ->
      onProgress(idx + 1, uris.size, "Preparando imagen ${idx + 1} de ${uris.size}...")
      val name = resolveDisplayName(uri)
      val pair = MlKitImageOcrExtractor.saveImageAndThumbnail(
        context = context,
        uri = uri,
        targetDir = imgDir,
        thumbsDir = thumbsDir,
        filePrefix = "img_${idx + 1}",
      )
      savedPairs.add(Triple(name, pair, idx + 1))
    }

    val displayName = if (savedPairs.size == 1) {
      savedPairs.first().first
    } else {
      "Catálogo en imágenes (${savedPairs.size} fotos)"
    }
    val fullImagePaths = savedPairs.map { it.second.fullImageFile.absolutePath }
    val thumbByPage = savedPairs.associate { it.third to it.second.thumbFile.absolutePath }

    // Si la IA gratuita Gemini está configurada, priorizar lectura visual con Gemini (con fallback offline ML Kit si falla)
    if (aiSettings.isConfigured) {
      val aiAttempt = runCatching {
        geminiExtractor.extractFromImageFiles(
          imageFiles = savedPairs.map { it.second.fullImageFile },
          apiKey = aiSettings.geminiApiKey,
          model = aiSettings.geminiModel,
          onProgress = onProgress,
        )
      }
      if (aiAttempt.isSuccess) {
        val aiRes = aiAttempt.getOrThrow()
        if (aiRes.productos.isNotEmpty()) {
          val enriched = aiRes.productos.map { item ->
            val thumb = item.paginaPdf?.let { thumbByPage[it] } ?: thumbByPage[1]
            item.copy(imagenRuta = thumb)
          }
          return@withContext ResultadoImportacion(
            formato = CatalogoFormato.IMAGEN,
            nombreArchivo = displayName,
            rutaLocal = fullImagePaths.first(),
            rutasImagenesFuente = fullImagePaths,
            productos = enriched,
            advertencia = aiRes.advertencia,
            metodoExtraccion = "IA Gemini (${aiSettings.geminiModel})",
          )
        }
      }
    }

    // Modo offline con Google ML Kit Text Recognition (0 internet, 0 API key)
    val ocrPages = mutableListOf<ImageOcrPage>()
    savedPairs.forEach { (name, pair, pageIdx) ->
      onProgress(pageIdx, savedPairs.size, "Leyendo texto de imagen $pageIdx de ${savedPairs.size} (OCR offline)...")
      val ocrPage = MlKitImageOcrExtractor.recognizePage(
        imageFile = pair.fullImageFile,
        thumbFile = pair.thumbFile,
        index1Based = pageIdx,
        displayName = name,
      )
      ocrPages.add(ocrPage)
    }

    val productos = ImageCatalogTextMapper.mapOcrPages(ocrPages)
    val hint = if (!aiSettings.isConfigured) {
      "Extraído en modo OCR offline. Si alguna imagen tiene precios en etiquetas o diseño complejo, activa \"IA Gemini\" gratis para máxima precisión visual."
    } else {
      null
    }

    ResultadoImportacion(
      formato = CatalogoFormato.IMAGEN,
      nombreArchivo = displayName,
      rutaLocal = fullImagePaths.first(),
      rutasImagenesFuente = fullImagePaths,
      productos = productos,
      advertencia = hint,
      metodoExtraccion = "OCR Imágenes (offline)",
    )
  }

  suspend fun extractPdfWithGemini(
    proveedorId: Long,
    pdfFile: File,
    displayName: String,
    apiKey: String,
    model: String,
    onProgress: (currentPage: Int, totalPages: Int, statusText: String) -> Unit = { _, _, _ -> },
  ): ResultadoImportacion = withContext(Dispatchers.IO) {
    val result = geminiExtractor.extractFromPdf(
      pdfFile = pdfFile,
      apiKey = apiKey,
      model = model,
      onProgress = onProgress,
    )

    val thumbsDir = File(context.filesDir, "catalogos/prov_$proveedorId/thumbs_${pdfFile.nameWithoutExtension}")
    val pagesNeeded = result.productos.mapNotNull { it.paginaPdf }.toSet()
    val thumbsByPage = SpatialPdfBoxExtractor.renderPageThumbnails(pdfFile, thumbsDir, pagesNeeded)

    val enriched = result.productos.map { item ->
      val thumb = item.paginaPdf?.let { thumbsByPage[it] }
      item.copy(imagenRuta = thumb)
    }

    ResultadoImportacion(
      formato = CatalogoFormato.PDF,
      nombreArchivo = displayName,
      rutaLocal = pdfFile.absolutePath,
      productos = enriched,
      advertencia = result.advertencia,
      metodoExtraccion = "IA Gemini ($model)",
    )
  }

  suspend fun extractImagesWithGemini(
    imagePaths: List<String>,
    displayName: String,
    apiKey: String,
    model: String,
    onProgress: (current: Int, total: Int, statusText: String) -> Unit = { _, _, _ -> },
  ): ResultadoImportacion = withContext(Dispatchers.IO) {
    val files = imagePaths.map { File(it) }.filter { it.exists() }
    require(files.isNotEmpty()) { "Las imágenes temporales ya no están disponibles." }

    val result = geminiExtractor.extractFromImageFiles(
      imageFiles = files,
      apiKey = apiKey,
      model = model,
      onProgress = onProgress,
    )

    val enriched = result.productos.map { item ->
      val idx = ((item.paginaPdf ?: 1) - 1).coerceIn(0, files.lastIndex)
      val imgFile = files[idx]
      val siblingThumb = File(imgFile.parentFile?.parentFile, "thumbs_${imgFile.parentFile?.name?.removePrefix("imgs_")}/${imgFile.nameWithoutExtension}_thumb.jpg")
      val bestImgPath = if (siblingThumb.exists()) siblingThumb.absolutePath else imgFile.absolutePath
      item.copy(imagenRuta = bestImgPath)
    }

    ResultadoImportacion(
      formato = CatalogoFormato.IMAGEN,
      nombreArchivo = displayName,
      rutaLocal = files.first().absolutePath,
      rutasImagenesFuente = files.map { it.absolutePath },
      productos = enriched,
      advertencia = result.advertencia,
      metodoExtraccion = "IA Gemini ($model)",
    )
  }

  suspend fun saveSingleProductPhoto(proveedorId: Long, uri: Uri): String = withContext(Dispatchers.IO) {
    val dir = File(context.filesDir, "catalogos/prov_$proveedorId/manual_photos")
    val thumbsDir = File(context.filesDir, "catalogos/prov_$proveedorId/manual_thumbs")
    val pair = MlKitImageOcrExtractor.saveImageAndThumbnail(
      context = context,
      uri = uri,
      targetDir = dir,
      thumbsDir = thumbsDir,
      filePrefix = "prod_${System.currentTimeMillis()}",
    )
    pair.thumbFile.absolutePath
  }

  private fun parsePdfOffline(
    proveedorId: Long,
    file: File,
    displayName: String,
  ): ResultadoImportacion {
    val bundle = try {
      SpatialPdfBoxExtractor.extractSpatialAndText(file)
    } catch (e: Exception) {
      return ResultadoImportacion(
        formato = CatalogoFormato.PDF,
        nombreArchivo = displayName,
        rutaLocal = file.absolutePath,
        productos = emptyList(),
        advertencia = "No se pudo leer el texto del PDF (${e.localizedMessage ?: "archivo protegido o dañado"}). Prueba con \"Leer con IA\" o abre el visor PDF.",
      )
    }

    val sheetProducts = PdfProductSheetParser.extractFromPages(bundle.pages)
    val (productosBase, metodo) = if (sheetProducts.isNotEmpty()) {
      sheetProducts to "Espacial fichas PDF (offline)"
    } else {
      PdfTextProductExtractor.extractFromText(bundle.plainText) to "Tabla de precios PDF (offline)"
    }

    val thumbsDir = File(context.filesDir, "catalogos/prov_$proveedorId/thumbs_${file.nameWithoutExtension}")
    val pagesNeeded = productosBase.mapNotNull { it.paginaPdf }.toSet()
    val thumbsByPage = SpatialPdfBoxExtractor.renderPageThumbnails(file, thumbsDir, pagesNeeded)

    val productos = productosBase.map { item ->
      val thumb = item.paginaPdf?.let { thumbsByPage[it] }
      item.copy(imagenRuta = thumb)
    }

    val advertencia = when {
      bundle.plainText.isBlank() ->
        "Este PDF no tiene texto seleccionable (es puramente imagen/escaneado). Usa \"Leer con IA\" para leerlo visualmente o guárdalo para verlo en el visor PDF."
      productos.isEmpty() ->
        "No se reconocieron productos automáticamente en modo offline. Usa \"Leer con IA\" para analizar el diseño visual del catálogo o consulta el PDF en el visor."
      else -> null
    }

    return ResultadoImportacion(
      formato = CatalogoFormato.PDF,
      nombreArchivo = displayName,
      rutaLocal = file.absolutePath,
      productos = productos,
      advertencia = advertencia,
      metodoExtraccion = metodo,
    )
  }

  private fun parseExcel(file: File, displayName: String): ResultadoImportacion {
    val output = try {
      FileInputStream(file).use { XlsxCatalogParser.parse(it) }
    } catch (e: Exception) {
      XlsxCatalogParser.XlsxParseOutput(
        productos = emptyList(),
        advertencia = "Error al leer el archivo Excel: ${e.localizedMessage ?: "formato no compatible"}.",
      )
    }
    return ResultadoImportacion(
      formato = CatalogoFormato.XLSX,
      nombreArchivo = displayName,
      rutaLocal = file.absolutePath,
      productos = output.productos,
      advertencia = output.advertencia,
      metodoExtraccion = "Excel (.xlsx) offline",
    )
  }

  private fun parseCsv(file: File, displayName: String): ResultadoImportacion {
    val productos = try {
      FileInputStream(file).use { CsvCatalogParser.parse(it) }
    } catch (e: Exception) {
      return ResultadoImportacion(
        formato = CatalogoFormato.CSV,
        nombreArchivo = displayName,
        rutaLocal = file.absolutePath,
        productos = emptyList(),
        advertencia = "Error al leer el archivo CSV: ${e.localizedMessage ?: "archivo inválido"}.",
      )
    }

    val advertencia = if (productos.isEmpty()) {
      "No se detectaron productos en el CSV. Verifica que tenga al menos una columna con nombres de producto."
    } else null

    return ResultadoImportacion(
      formato = CatalogoFormato.CSV,
      nombreArchivo = displayName,
      rutaLocal = file.absolutePath,
      productos = productos,
      advertencia = advertencia,
      metodoExtraccion = "CSV offline",
    )
  }

  private fun resolveDisplayName(uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor ->
        if (cursor.moveToFirst()) {
          val name = cursor.getString(0)
          if (!name.isNullOrBlank()) return name
        }
      }
    return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "catalogo_importado"
  }

  private fun detectFormat(fileName: String, mimeType: String): CatalogoFormato {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
      ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif") || mimeType.startsWith("image/") ->
        CatalogoFormato.IMAGEN
      ext == "pdf" || mimeType.contains("pdf") -> CatalogoFormato.PDF
      ext == "xlsx" || ext == "xlsm" || ext == "xls" ||
        mimeType.contains("spreadsheet") || mimeType.contains("excel") -> CatalogoFormato.XLSX
      ext == "csv" || ext == "tsv" || ext == "txt" ||
        mimeType.contains("csv") || mimeType.contains("comma-separated") || mimeType.startsWith("text/") ->
        CatalogoFormato.CSV
      else -> CatalogoFormato.PDF
    }
  }

  private fun sanitizeFileName(name: String): String {
    return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80).ifBlank { "catalogo" }
  }
}
