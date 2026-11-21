package com.pedidos.proveedores.domain.pricing

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.pedidos.proveedores.domain.parser.PriceParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formatea y exporta la lista de productos que necesitan ajuste de PVP en Isaac POS
 * (texto listo para copiar/WhatsApp y archivo CSV UTF-8 compatible con Excel).
 */
object PvpAdjustmentExporter {

  fun formatWhatsAppText(
    alerts: List<MarginAlertItem>,
    targetMarkupPercent: Double,
    nowMillis: Long = System.currentTimeMillis(),
  ): String {
    val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("es-ES")).format(Date(nowMillis))
    val sb = StringBuilder()
    sb.appendLine("*Ajustes de PVP sugeridos — Isaac POS* ($dateStr)")
    sb.appendLine("Meta de ganancia: +${PriceParser.formatQuantity(targetMarkupPercent)}% s/costo")
    sb.appendLine()

    alerts.forEachIndexed { idx, alert ->
      val o = alert.primaryOffer
      val tag = when (o.health) {
        MarginHealth.LOSS -> "[PÉRDIDA]"
        MarginHealth.CRITICAL -> "[CRÍTICO]"
        MarginHealth.BELOW_TARGET -> "[BAJO META]"
        else -> "[OK]"
      }
      val costStr = o.effectiveUnitCostUsd?.let { PriceParser.formatCurrency(it) } ?: "-"
      val pvpStr = o.posPvpUsd?.let { PriceParser.formatCurrency(it) } ?: "-"
      val sugStr = o.suggestedPvpUsd?.let { PriceParser.formatCurrency(it) } ?: "-"
      val markupStr = o.markupPercent?.let { "${"%.1f".format(Locale.US, it)}%" } ?: "-"
      val marginStr = o.grossMarginPercent?.let { "${"%.1f".format(Locale.US, it)}%" } ?: "-"

      sb.appendLine("${idx + 1}. $tag ${o.displayName}")
      if (o.posProduct?.codigoBarra?.isNotBlank() == true) {
        sb.appendLine("   Cód: ${o.posProduct.codigoBarra}")
      }
      sb.appendLine("   Costo u.: $costStr (${o.proveedor.nombre}) | PVP actual: $pvpStr ($markupStr s/costo · $marginStr s/PVP)")
      sb.appendLine("   -> PVP sugerido: *$sugStr*")
      alert.cheaperAlternative?.let { alt ->
        val altCost = alt.effectiveUnitCostUsd?.let { PriceParser.formatCurrency(it) } ?: "-"
        sb.appendLine("   Tip: ${alt.proveedor.nombre} lo ofrece a $altCost")
      }
    }
    return sb.toString().trimEnd()
  }

  fun formatCsv(
    alerts: List<MarginAlertItem>,
    targetMarkupPercent: Double,
  ): String {
    val sb = StringBuilder()
    // BOM UTF-8 para compatibilidad inmediata con Excel en Windows
    sb.append('\uFEFF')
    sb.appendLine("Estado,Codigo,Producto,Proveedor,Costo_Unitario_USD,PVP_Actual_USD,Ganancia_Sobre_Costo_Pct,Margen_Sobre_PVP_Pct,PVP_Sugerido_USD,Meta_Pct,Alternativa_Mas_Barata,Costo_Alternativa_USD")

    for (alert in alerts) {
      val o = alert.primaryOffer
      val code = o.posProduct?.codigoBarra?.ifBlank { o.producto.codigo } ?: o.producto.codigo
      val alt = alert.cheaperAlternative
      val fields = listOf(
        o.health.etiqueta,
        code,
        o.displayName,
        o.proveedor.nombre,
        o.effectiveUnitCostUsd?.let { "%.4f".format(Locale.US, it) } ?: "",
        o.posPvpUsd?.let { "%.2f".format(Locale.US, it) } ?: "",
        o.markupPercent?.let { "%.1f".format(Locale.US, it) } ?: "",
        o.grossMarginPercent?.let { "%.1f".format(Locale.US, it) } ?: "",
        o.suggestedPvpUsd?.let { "%.2f".format(Locale.US, it) } ?: "",
        "%.1f".format(Locale.US, targetMarkupPercent),
        alt?.proveedor?.nombre.orEmpty(),
        alt?.effectiveUnitCostUsd?.let { "%.4f".format(Locale.US, it) }.orEmpty(),
      )
      sb.appendLine(fields.joinToString(",") { escapeCsv(it) })
    }
    return sb.toString()
  }

  fun shareCsvFile(
    context: Context,
    alerts: List<MarginAlertItem>,
    targetMarkupPercent: Double,
  ): Boolean {
    return runCatching {
      val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
      val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
      val csvFile = File(exportDir, "ajustes_pvp_isaac_pos_$stamp.csv")
      csvFile.writeText(formatCsv(alerts, targetMarkupPercent), Charsets.UTF_8)

      val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        csvFile,
      )
      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Ajustes de PVP sugeridos — Isaac POS")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val chooser = Intent.createChooser(shareIntent, "Exportar ajustes de PVP (CSV)").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(chooser)
      true
    }.getOrDefault(false)
  }

  private fun escapeCsv(value: String): String {
    val clean = value.replace("\r", " ").replace("\n", " ")
    return if (clean.contains(',') || clean.contains('"')) {
      "\"${clean.replace("\"", "\"\"")}\""
    } else {
      clean
    }
  }
}
