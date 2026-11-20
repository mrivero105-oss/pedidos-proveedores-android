package com.pedidos.proveedores.domain.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR 100% offline para imágenes (JPG, PNG, WebP) usando Google ML Kit Text Recognition,
 * además de utilidades de copiado, redimensionado seguro y generación de miniaturas.
 */
object MlKitImageOcrExtractor {

  data class SavedImagePair(
    val fullImageFile: File,
    val thumbFile: File,
    val width: Int,
    val height: Int,
  )

  suspend fun recognizePage(
    imageFile: File,
    thumbFile: File,
    index1Based: Int,
    displayName: String,
  ): ImageOcrPage = withContext(Dispatchers.IO) {
    val bitmap = decodeScaledBitmap(imageFile, maxDimension = 1600)
      ?: error("No se pudo decodificar la imagen $displayName.")

    val width = bitmap.width.toFloat()
    val height = bitmap.height.toFloat()

    val recognizedText = try {
      runMlKitTextRecognition(InputImage.fromBitmap(bitmap, 0))
    } finally {
      bitmap.recycle()
    }

    val spans = mutableListOf<PdfSpan>()
    val plainBuilder = StringBuilder()

    for (block in recognizedText.textBlocks) {
      for (line in block.lines) {
        val lineText = line.text.trim()
        if (lineText.isNotEmpty()) {
          plainBuilder.appendLine(lineText)
        }
        for (element in line.elements) {
          val box = element.boundingBox ?: continue
          val elText = element.text.trim()
          if (elText.isEmpty()) continue
          val boxH = box.height().toFloat().coerceAtLeast(1f)
          spans.add(
            PdfSpan(
              x = box.left.toFloat(),
              y = box.top.toFloat(),
              width = box.width().toFloat().coerceAtLeast(1f),
              height = boxH,
              // Aproximación proporcional al tamaño de letra visual para detectar títulos grandes
              fontSize = boxH * 0.92f,
              text = elText,
            )
          )
        }
      }
    }

    ImageOcrPage(
      index1Based = index1Based,
      displayName = displayName,
      width = width,
      height = height,
      spans = spans,
      plainText = plainBuilder.toString(),
      thumbPath = thumbFile.absolutePath,
    )
  }

  /**
   * Copia una imagen desde ContentResolver optimizada a JPEG (máx. 1600px)
   * y genera además una miniatura liviana (360px) para listas y pedidos.
   */
  suspend fun saveImageAndThumbnail(
    context: Context,
    uri: Uri,
    targetDir: File,
    thumbsDir: File,
    filePrefix: String,
  ): SavedImagePair = withContext(Dispatchers.IO) {
    targetDir.mkdirs()
    thumbsDir.mkdirs()

    val tempRaw = File(targetDir, "${filePrefix}_raw.tmp")
    context.contentResolver.openInputStream(uri)?.use { input ->
      FileOutputStream(tempRaw).use { output -> input.copyTo(output) }
    } ?: error("No se pudo abrir la imagen seleccionada.")

    val fullBitmap = decodeScaledBitmap(tempRaw, maxDimension = 1600)
    tempRaw.delete()
    if (fullBitmap == null) {
      error("El archivo seleccionado no es una imagen válida.")
    }

    val fullFile = File(targetDir, "$filePrefix.jpg")
    FileOutputStream(fullFile).use { fos ->
      fullBitmap.compress(Bitmap.CompressFormat.JPEG, 86, fos)
    }

    val width = fullBitmap.width
    val height = fullBitmap.height

    val thumbFile = File(thumbsDir, "${filePrefix}_thumb.jpg")
    val thumbW = 360
    val thumbH = (height * (thumbW.toFloat() / width.coerceAtLeast(1))).toInt().coerceAtLeast(1)
    val thumbBitmap = if (width > thumbW) {
      Bitmap.createScaledBitmap(fullBitmap, thumbW, thumbH, true)
    } else {
      fullBitmap
    }
    FileOutputStream(thumbFile).use { fos ->
      thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 82, fos)
    }
    if (thumbBitmap !== fullBitmap) thumbBitmap.recycle()
    fullBitmap.recycle()

    SavedImagePair(
      fullImageFile = fullFile,
      thumbFile = thumbFile,
      width = width,
      height = height,
    )
  }

  fun decodeScaledBitmap(file: File, maxDimension: Int): Bitmap? {
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
    val rawW = boundsOpts.outWidth
    val rawH = boundsOpts.outHeight
    if (rawW <= 0 || rawH <= 0) return null

    var sampleSize = 1
    while ((rawW / sampleSize) > maxDimension * 2 || (rawH / sampleSize) > maxDimension * 2) {
      sampleSize *= 2
    }

    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
    val maxSide = maxOf(decoded.width, decoded.height)
    if (maxSide <= maxDimension) return decoded

    val scale = maxDimension.toFloat() / maxSide
    val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
    val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
    if (scaled !== decoded) decoded.recycle()
    return scaled
  }

  private suspend fun runMlKitTextRecognition(image: InputImage): Text =
    suspendCancellableCoroutine { cont ->
      val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
      recognizer.process(image)
        .addOnSuccessListener { result ->
          recognizer.close()
          if (cont.isActive) cont.resume(result)
        }
        .addOnFailureListener { ex ->
          recognizer.close()
          if (cont.isActive) cont.resumeWithException(ex)
        }
      cont.invokeOnCancellation {
        runCatching { recognizer.close() }
      }
    }
}
