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
  val notas: String = "",
)

data class LocalProviderPhoneUpdate(
  val proveedorId: Long,
  val telefonoWhatsApp: String,
)

data class RemoteProductInsert(
  val providerName: String,
  val posProduct: IsaacPosProducto,
)

data class IsaacPosSyncPlan(
  val localProductUpdates: List<LocalProductPosUpdate>,
  val providersToCreate: List<RemoteProviderSeed>,
  val providersToUpdatePhone: List<LocalProviderPhoneUpdate> = emptyList(),
  val productsToInsertByProviderName: List<RemoteProductInsert>,
  val summary: IsaacPosSyncSummary,
)

/**
 * Motor puro (testeable en JVM) 100% enfocado en proveedores de Isaac POS:
 * 1. Sincroniza todos los proveedores activos de `cloud_proveedores` (incluso los que aún no tienen productos).
 * 2. Procesa únicamente productos asociados a un proveedor real (ignora productos sueltos de categorías).
 * 3. Empareja productos locales existentes por `isaacPosId`, `codigo_barra` o nombre normalizado.
 * 4. Inserta los productos faltantes dentro de su proveedor correspondiente sin cargar categorías.
 */
object IsaacPosSyncMatcher {

  fun computeSyncPlan(
    remoteProducts: List<IsaacPosProducto>,
    localProveedores: List<Proveedor>,
    localProductos: List<Producto>,
    remoteProviders: List<RemoteProviderSeed> = emptyList(),
  ): IsaacPosSyncPlan {
    val existingProvByKey = localProveedores.associateBy { normalizeKey(it.nombre) }
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
    val providersToUpdatePhone = linkedMapOf<Long, LocalProviderPhoneUpdate>()
    val productsToInsert = mutableListOf<RemoteProductInsert>()
    val seenPosIdsForLowStock = mutableSetOf<String>()

    // 1. Sembrar todos los proveedores activos recibidos de cloud_proveedores
    for (seed in remoteProviders) {
      val cleanName = seed.nombre.trim()
      if (cleanName.isBlank()) continue
      val key = normalizeKey(cleanName)
      val existing = existingProvByKey[key]
      if (existing == null) {
        if (!providersToCreate.containsKey(key)) {
          providersToCreate[key] = seed.copy(
            nombre = cleanName,
            telefonoWhatsApp = seed.telefonoWhatsApp.trim(),
            notas = seed.notas.trim(),
          )
        }
      } else if (existing.telefonoWhatsApp.isBlank() && seed.telefonoWhatsApp.isNotBlank()) {
        providersToUpdatePhone[existing.id] = LocalProviderPhoneUpdate(
          proveedorId = existing.id,
          telefonoWhatsApp = seed.telefonoWhatsApp.trim(),
        )
      }
    }

    // 2. Procesar únicamente productos pertenecientes a un proveedor de Isaac POS
    val providerOnlyProducts = remoteProducts.filter { it.proveedorNombreRemoto.isNotBlank() }
    var linkedByBarcodeCount = 0
    val ignoredWithoutProviderCount = remoteProducts.size - providerOnlyProducts.size
    var lowStockCount = 0

    for (pos in providerOnlyProducts) {
      if (seenPosIdsForLowStock.add(pos.productoId) && pos.esBajoStock) {
        lowStockCount++
      }

      val remoteProvName = pos.proveedorNombreRemoto.trim()
      val provKey = normalizeKey(remoteProvName)
      val existingProv = existingProvByKey[provKey]
      if (existingProv == null && !providersToCreate.containsKey(provKey)) {
        providersToCreate[provKey] = RemoteProviderSeed(
          nombre = remoteProvName,
          telefonoWhatsApp = pos.proveedorTelefonoRemoto.trim(),
        )
      } else if (existingProv != null && existingProv.telefonoWhatsApp.isBlank() && pos.proveedorTelefonoRemoto.isNotBlank()) {
        providersToUpdatePhone.putIfAbsent(
          existingProv.id,
          LocalProviderPhoneUpdate(existingProv.id, pos.proveedorTelefonoRemoto.trim()),
        )
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

      productsToInsert.add(RemoteProductInsert(providerName = remoteProvName, posProduct = pos))
    }

    return IsaacPosSyncPlan(
      localProductUpdates = updates,
      providersToCreate = providersToCreate.values.toList(),
      providersToUpdatePhone = providersToUpdatePhone.values.toList(),
      productsToInsertByProviderName = productsToInsert,
      summary = IsaacPosSyncSummary(
        totalDescargados = providerOnlyProducts.size,
        productosActualizadosLocales = updates.size,
        vinculadosPorCodigoBarras = linkedByBarcodeCount,
        proveedoresNuevosDesdeSupabase = providersToCreate.size,
        productosNuevosDesdeSupabase = productsToInsert.size,
        sinProveedorAsignado = ignoredWithoutProviderCount,
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
