package com.pedidos.proveedores.domain.isaacpos

import com.pedidos.proveedores.domain.model.IsaacPosProducto
import com.pedidos.proveedores.domain.parser.PdfProductSheetParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Parsea respuestas JSON de PostgREST (Supabase) tanto de la vista enriquecida
 * `v_isaac_pos_productos_por_proveedor` como de la tabla base `cloud_productos`.
 */
object IsaacPosJsonParser {

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  fun parseProductsArray(
    rawJson: String,
    defaultLowStock: Double = 5.0,
    syncTimestamp: Long = System.currentTimeMillis(),
  ): List<IsaacPosProducto> {
    if (rawJson.isBlank()) return emptyList()
    val array = runCatching { json.parseToJsonElement(rawJson).jsonArray }.getOrNull() ?: return emptyList()

    val result = ArrayList<IsaacPosProducto>(array.size)
    for (element in array) {
      val obj = runCatching { element.jsonObject }.getOrNull() ?: continue
      val productoId = obj.str("producto_id").ifBlank { obj.str("id") }
      val nombre = obj.str("producto_nombre").ifBlank { obj.str("nombre") }
      if (productoId.isBlank() || nombre.isBlank()) continue

      val rawBarcode = obj.str("codigo_barra")
      val cleanBarcode = if (rawBarcode.filter { it.isDigit() }.length >= 11) {
        PdfProductSheetParser.normalizeBarcode(rawBarcode) ?: rawBarcode
      } else {
        rawBarcode
      }

      result.add(
        IsaacPosProducto(
          productoId = productoId,
          sucursalId = obj.str("sucursal_id").ifBlank { "principal" },
          codigoBarra = cleanBarcode,
          nombre = nombre,
          categoria = obj.str("categoria"),
          precioVenta = obj.dbl("precio_venta"),
          stockActual = obj.dbl("stock_actual") ?: 0.0,
          proveedorNombreRemoto = obj.str("proveedor_nombre"),
          proveedorTelefonoRemoto = obj.str("proveedor_telefono"),
          unidadPedido = obj.str("unidad_pedido").ifBlank { "u" },
          costoUnitario = obj.dbl("costo_unitario"),
          stockMinimo = obj.dbl("stock_minimo") ?: defaultLowStock,
          sincronizadoEn = syncTimestamp,
        )
      )
    }

    return result
  }

  private fun JsonObject.str(key: String): String {
    val el: JsonElement = this[key] ?: return ""
    return (el as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
  }

  private fun JsonObject.dbl(key: String): Double? {
    val el: JsonElement = this[key] ?: return null
    val content = (el as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
    if (content.isEmpty() || content.equals("null", ignoreCase = true)) return null
    return content.replace(',', '.').toDoubleOrNull()
  }
}
