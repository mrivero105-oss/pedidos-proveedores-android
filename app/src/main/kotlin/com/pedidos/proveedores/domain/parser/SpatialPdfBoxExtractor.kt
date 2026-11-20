package com.pedidos.proveedores.domain.parser

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Extrae spans espaciales (x, y, tamaño de fuente y texto) página por página usando PDFBox-Android
 * y genera miniaturas JPG de las páginas de producto usando PdfRenderer nativo.
 */
object SpatialPdfBoxExtractor {

  data class PdfExtractionBundle(
    val pages: List<PdfPageLayout>,
    val plainText: String,
  )

  fun extractSpatialAndText(file: File): PdfExtractionBundle {
    PDDocument.load(file).use { document ->
      val totalPages = document.numberOfPages
      val pageLayouts = ArrayList<PdfPageLayout>(totalPages)
      val plainTextBuilder = StringBuilder()

      for (pageIdx in 1..totalPages) {
        val pdPage = document.getPage(pageIdx - 1)
        val mediaBox = pdPage.cropBox ?: pdPage.mediaBox
        val spans = mutableListOf<PdfSpan>()

        val stripper = object : PDFTextStripper() {
          init {
            startPage = pageIdx
            endPage = pageIdx
            sortByPosition = true
          }

          override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            if (textPositions.isNullOrEmpty()) return
            // Dividir por saltos horizontales para separar columnas y palabras con su tamaño de fuente exacto
            var wordBuf = StringBuilder()
            var startX = 0f
            var endX = 0f
            var ySum = 0f
            var sizeSum = 0f
            var maxH = 0f
            var count = 0
            var prevEndX = -1f
            var prevY = -1f
            var prevSpaceWidth = 4f

            fun flushWord() {
              val word = wordBuf.toString().trim()
              if (word.isNotEmpty() && count > 0) {
                spans.add(
                  PdfSpan(
                    x = startX,
                    y = ySum / count,
                    width = (endX - startX).coerceAtLeast(1f),
                    height = maxH.coerceAtLeast(1f),
                    fontSize = sizeSum / count,
                    text = word,
                  )
                )
              }
              wordBuf = StringBuilder()
              count = 0
              ySum = 0f
              sizeSum = 0f
              maxH = 0f
            }

            for (pos in textPositions) {
              val ch = pos.unicode ?: continue
              if (ch.isBlank()) {
                flushWord()
                prevEndX = pos.xDirAdj + pos.widthDirAdj
                continue
              }

              val x = pos.xDirAdj
              val y = pos.yDirAdj
              val w = pos.widthDirAdj
              val h = pos.heightDir
              val fontPt = pos.fontSizeInPt.takeIf { it > 0f } ?: pos.fontSize
              val spaceW = pos.widthOfSpace.takeIf { it > 0.5f } ?: prevSpaceWidth
              prevSpaceWidth = spaceW

              val bigGap = count > 0 && (
                (x - prevEndX) > maxOf(spaceW * 0.65f, 3.5f) ||
                  abs(y - prevY) > maxOf(fontPt * 0.6f, 6f)
                )
              if (bigGap) {
                flushWord()
              }

              if (count == 0) {
                startX = x
              }
              endX = maxOf(endX, x + w)
              ySum += y
              sizeSum += fontPt
              maxH = maxOf(maxH, h)
              count++
              prevEndX = x + w
              prevY = y
              wordBuf.append(ch)
            }
            flushWord()
          }
        }

        val pagePlain = stripper.getText(document)
        plainTextBuilder.appendLine(pagePlain)

        pageLayouts.add(
          PdfPageLayout(
            pageNumber = pageIdx,
            width = mediaBox.width,
            height = mediaBox.height,
            spans = spans,
          )
        )
      }

      return PdfExtractionBundle(
        pages = pageLayouts,
        plainText = plainTextBuilder.toString(),
      )
    }
  }

  /**
   * Genera miniaturas JPG livianas (ancho máx. 360px) de las páginas solicitadas (1-based)
   * usando PdfRenderer nativo para mostrar la foto del catálogo junto a cada producto.
   */
  fun renderPageThumbnails(
    pdfFile: File,
    thumbsDir: File,
    pages1Based: Set<Int>,
  ): Map<Int, String> {
    if (pages1Based.isEmpty() || !pdfFile.exists()) return emptyMap()
    thumbsDir.mkdirs()
    val result = mutableMapOf<Int, String>()

    runCatching {
      ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          val total = renderer.pageCount
          for (pageNum in pages1Based.sorted()) {
            val idx = pageNum - 1
            if (idx !in 0 until total) continue
            renderer.openPage(idx).use { page ->
              val targetW = 360
              val targetH = (page.height * (targetW.toFloat() / page.width)).toInt().coerceAtLeast(1)
              val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
              bmp.eraseColor(Color.WHITE)
              page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

              val outFile = File(thumbsDir, "page_${pageNum}.jpg")
              FileOutputStream(outFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 82, fos)
              }
              bmp.recycle()
              result[pageNum] = outFile.absolutePath
            }
          }
        }
      }
    }

    return result
  }
}
