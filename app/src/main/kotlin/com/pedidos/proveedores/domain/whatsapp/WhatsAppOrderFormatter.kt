package com.pedidos.proveedores.domain.whatsapp

import com.pedidos.proveedores.domain.model.Pedido
import com.pedidos.proveedores.domain.parser.PriceParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WhatsAppOrderFormatter {

  fun format(pedido: Pedido, incluirPrecios: Boolean = pedido.incluirPrecios): String {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-ES"))
    val fechaTexto = dateFormat.format(Date(pedido.creadoEn))

    return buildString {
      appendLine("*PEDIDO A PROVEEDOR*")
      appendLine("*Proveedor:* ${pedido.proveedorNombre}")
      appendLine("*Fecha:* $fechaTexto")
      appendLine()
      appendLine("*Detalle:*")

      pedido.items.forEachIndexed { index, item ->
        val codeSuffix = if (item.codigo.isNotBlank()) " [${item.codigo}]" else ""
        appendLine("${index + 1}. *${item.nombre}*$codeSuffix")
        val qtyStr = PriceParser.formatQuantity(item.cantidad)
        val unitStr = item.unidad.ifBlank { "u" }
        val pricePart = if (incluirPrecios && item.precioUnitario != null) {
          val unitPrice = PriceParser.formatCurrency(item.precioUnitario)
          val subtotal = PriceParser.formatCurrency(item.precioUnitario * item.cantidad)
          " × $unitPrice = $subtotal"
        } else {
          ""
        }
        appendLine("   Cant.: $qtyStr $unitStr$pricePart")
      }

      appendLine()
      val totalUnits = PriceParser.formatQuantity(pedido.totalCantidad)
      appendLine("*Ítems:* ${pedido.items.size} (Total cant.: $totalUnits)")

      if (incluirPrecios && pedido.totalEstimado != null) {
        appendLine("*Total estimado:* ${PriceParser.formatCurrency(pedido.totalEstimado!!)}")
      }

      if (pedido.notas.isNotBlank()) {
        appendLine()
        appendLine("*Notas:* ${pedido.notas.trim()}")
      }
    }.trimEnd()
  }

  private val veLocalWithZero = Regex("^0(412|414|416|424|426)\\d{7}$")
  private val veLocalWithoutZero = Regex("^(412|414|416|424|426)\\d{7}$")

  /**
   * Limpia un número telefónico dejando solo dígitos para la URL wa.me / api.whatsapp.com.
   * Normaliza automáticamente móviles venezolanos locales (0412/0414/0416/0424/0426 -> 58...).
   */
  fun sanitizePhone(rawPhone: String): String {
    val digits = rawPhone.filter { it.isDigit() }
    return when {
      veLocalWithZero.matches(digits) -> "58${digits.drop(1)}"
      veLocalWithoutZero.matches(digits) -> "58$digits"
      else -> digits
    }
  }
}
