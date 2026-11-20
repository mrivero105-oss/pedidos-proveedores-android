package com.pedidos.proveedores.domain.isaacpos

import com.pedidos.proveedores.domain.model.IsaacPosProducto
import com.pedidos.proveedores.domain.model.Producto
import com.pedidos.proveedores.domain.model.Proveedor
import java.text.Normalizer

data class IsaacPosSyncSummary(
  val totalDescargados: Int,
  val productosActualizadosLocales: Int,
  val vinculadosPorCodigoBarras: Int,
  val proveedoresNuevosDesdeSupabase: Int,
  val productosNuevosDesdeSupabase: Int,
  val sinProveedorAsignado: Int,
  val enBajoStock: Int,
)

data class LocalProductPosUpdate(
  val localProductId: Long,
  val isaacPosId: String,
  val stockPos: Double,
  val precioVentaPos: Double?,
  val newCostIfMissing: Double?,
  val newUnitIfGeneric: String? = null,
)

data class RemoteProviderSeed(
  val nombre: String,
  val telefonoWhatsApp: String,
)

data class RemoteProductInsert(
  val providerName: String,
  val posProduct: IsaacPosProducto,
)

data class IsaacPosSyncPlan(
  val localProductUpdates: List<LocalProductPosUpdate>,
  val providersToCreate: List<RemoteProviderSeed>,
  val productsToInsertByProviderName: List<RemoteProductInsert>,
  val summary: IsaacPosSyncSummary,
)

/**
 * Motor puro (testeable en JVM) que cruza los productos descargados de Isaac POS (Supabase)
 * con los proveedores y catálogos ya cargados en el teléfono:
 * 1. Empareja primero por `isaacPosId` ya vinculado.
 * 2. Empareja automáticamente por `codigo_barra` (ej. catálogos PDF/Excel/Imágenes como Amanecer).
 * 3. Empareja por nombre normalizado exacto.
 * 4. Si en Supabase el producto ya tiene `proveedor_nombre` (desde `cloud_proveedores`),
 *    planifica la creación del proveedor y la inserción ordenada del producto.
 */
object IsaacPosSyncMatcher {

  fun computeSyncPlan(
    remoteProducts: List<IsaacPosProducto>,
    localProveedores: List<Proveedor>,
    localProductos: List<Producto>,
  ): IsaacPosSyncPlan {
    val existingProvLower = localProveedores.map { normalizeKey(it.nombre) }.toMutableSet()
    val localByPosId = localProductos
      .mapNotNull { p -> p.isaacPosId?.takeIf { it.isNotBlank() }?.let { it to p } }
      .groupBy({ it.first }, { it.second })

    val localByBarcode = localProductos
      .filter { it.codigo.isNotBlank() }
      .groupBy { normalizeBarcodeKey(it.codigo) }

    val localByName = localProductos
      .groupBy { normalizeKey(it.nombre) }

    val updates = mutableListOf<LocalProductPosUpdate>()
    val updatedLocalIds = mutableSetOf<Long>()
    val providersToCreate = linkedMapOf<String, RemoteProviderSeed>()
    val productsToInsert = mutableListOf<RemoteProductInsert>()
    val seenPosIdsForLowStock = mutableSetOf<String>()

    var linkedByBarcodeCount = 0
    var unassignedCount = 0
    var lowStockCount = 0

    for (pos in remoteProducts) {
      if (seenPosIdsForLowStock.add(pos.productoId) && pos.esBajoStock) {
        lowStockCount++
      }

      val byIdMatches = localByPosId[pos.productoId].orEmpty()
      val barcodeKey = normalizeBarcodeKey(pos.codigoBarra)
      val byBarcodeMatches = if (byIdMatches.isEmpty() && barcodeKey.length >= 6) {
        localByBarcode[barcodeKey].orEmpty()
      } else {
        emptyList()
      }
      val byNameMatches = if (byIdMatches.isEmpty() && byBarcodeMatches.isEmpty()) {
        localByName[normalizeKey(pos.nombre)].orEmpty()
      } else {
        emptyList()
      }

      val matchedLocals = when {
        byIdMatches.isNotEmpty() -> byIdMatches
        byBarcodeMatches.isNotEmpty() -> {
          linkedByBarcodeCount++
          byBarcodeMatches
        }
        else -> byNameMatches
      }

      if (matchedLocals.isNotEmpty()) {
        val remoteUnit = pos.unidadPedido.trim()
        for (local in matchedLocals) {
          if (updatedLocalIds.add(local.id)) {
            val shouldUpdateUnit = local.unidad.trim().equals("u", ignoreCase = true) &&
              remoteUnit.isNotBlank() &&
              !remoteUnit.equals("u", ignoreCase = true)
            updates.add(
              LocalProductPosUpdate(
                localProductId = local.id,
                isaacPosId = pos.productoId,
                stockPos = pos.stockActual,
                precioVentaPos = pos.precioVenta,
                newCostIfMissing = if (local.precio == null) pos.costoUnitario else null,
                newUnitIfGeneric = if (shouldUpdateUnit) remoteUnit else null,
              )
            )
          }
        }
        continue
      }

      // No estaba en catálogos locales: si viene con proveedor desde Supabase, crearlo/insertarlo ordenado
      val remoteProvName = pos.proveedorNombreRemoto.trim()
      if (remoteProvName.isNotBlank()) {
        val provKey = normalizeKey(remoteProvName)
        if (!existingProvLower.contains(provKey) && !providersToCreate.containsKey(provKey)) {
          providersToCreate[provKey] = RemoteProviderSeed(
            nombre = remoteProvName,
            telefonoWhatsApp = pos.proveedorTelefonoRemoto.trim(),
          )
        }
        productsToInsert.add(RemoteProductInsert(providerName = remoteProvName, posProduct = pos))
      } else {
        unassignedCount++
      }
    }

    return IsaacPosSyncPlan(
      localProductUpdates = updates,
      providersToCreate = providersToCreate.values.toList(),
      productsToInsertByProviderName = productsToInsert,
      summary = IsaacPosSyncSummary(
        totalDescargados = remoteProducts.size,
        productosActualizadosLocales = updates.size,
        vinculadosPorCodigoBarras = linkedByBarcodeCount,
        proveedoresNuevosDesdeSupabase = providersToCreate.size,
        productosNuevosDesdeSupabase = productsToInsert.size,
        sinProveedorAsignado = unassignedCount,
        enBajoStock = lowStockCount,
      ),
    )
  }

  internal fun normalizeBarcodeKey(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return if (digits.length >= 6) digits else raw.trim().lowercase()
  }

  internal fun normalizeKey(raw: String): String {
    val decomposed = Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD)
    return decomposed
      .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
      .replace(Regex("[^a-z0-9 ]"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }
}
