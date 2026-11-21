package com.pedidos.proveedores.domain.pricing

import com.pedidos.proveedores.data.local.PricingSettings
import com.pedidos.proveedores.domain.model.CatalogoArchivo
import com.pedidos.proveedores.domain.model.HistorialPrecio
import com.pedidos.proveedores.domain.model.IsaacPosProducto
import com.pedidos.proveedores.domain.model.ModoIvaCatalogo
import com.pedidos.proveedores.domain.model.MonedaCatalogo
import com.pedidos.proveedores.domain.model.Pedido
import com.pedidos.proveedores.domain.model.PedidoEstado
import com.pedidos.proveedores.domain.model.PedidoItem
import com.pedidos.proveedores.domain.model.Producto
import com.pedidos.proveedores.domain.model.Proveedor
import java.text.Normalizer
import kotlin.math.abs

enum class PriceChangeOrigin(val etiqueta: String) {
  CATALOG_HISTORY("Lista anterior → actual"),
  CATALOG_VS_POS("Último costo → lista nueva"),
}

data class PackDetection(
  val units: Double,
  val isAutoDetected: Boolean,
  val label: String,
)

data class TaxDetection(
  val isTaxable: Boolean,
  val isAutoDetected: Boolean,
)

data class EvaluatedOffer(
  val producto: Producto,
  val proveedor: Proveedor,
  val catalogo: CatalogoArchivo?,
  val posProduct: IsaacPosProducto?,
  val canonicalKey: String,
  val displayName: String,
  val moneda: MonedaCatalogo,
  val modoIva: ModoIvaCatalogo,
  val tasaAplicadaBs: Double?,
  val missingExchangeRate: Boolean,
  val packDetection: PackDetection,
  val taxDetection: TaxDetection,
  val ivaApplied: Boolean,
  val ivaPercentApplied: Double,
  val rawPriceOriginal: Double?,
  val packCostUsd: Double?,
  val effectiveUnitCostUsd: Double?,
  val stockPos: Double?,
  val isPrimaryPosSupplier: Boolean,
  val updatedAtMillis: Long,
)

data class SupplierComparisonItem(
  val canonicalKey: String,
  val displayName: String,
  val codigoBarra: String,
  val stockPos: Double?,
  val offers: List<EvaluatedOffer>,
  val cheapestOffer: EvaluatedOffer,
  val mostExpensiveOffer: EvaluatedOffer,
  val savingsPerUnitUsd: Double,
  val savingsPercent: Double,
)

data class PriceChangeItem(
  val idKey: String,
  val proveedorId: Long,
  val proveedorNombre: String,
  val productName: String,
  val unidad: String,
  val previousUnitCostUsd: Double,
  val currentUnitCostUsd: Double,
  val deltaUsd: Double,
  val deltaPercent: Double,
  val previousDateMillis: Long?,
  val currentDateMillis: Long,
  val origin: PriceChangeOrigin,
) {
  val isIncrease: Boolean get() = deltaUsd > 0.0
}

data class OptimalOrderLine(
  val canonicalKey: String,
  val displayName: String,
  val codigo: String,
  val stockPos: Double?,
  val stockMinimo: Double,
  val allOffers: List<EvaluatedOffer>,
  val recommendedOffer: EvaluatedOffer,
  val selectedOffer: EvaluatedOffer,
  val referenceCostUsd: Double,
  val savingsPerUnitUsd: Double,
  val quantityPacks: Double,
  val estimatedLineTotalUsd: Double,
  val estimatedLineSavingsUsd: Double,
)

data class OptimalProviderBucket(
  val proveedor: Proveedor,
  val lines: List<OptimalOrderLine>,
  val totalEstimatedUsd: Double,
  val totalSavingsUsd: Double,
)

data class OptimalOrderPlan(
  val lines: List<OptimalOrderLine>,
  val buckets: List<OptimalProviderBucket>,
  val totalEstimatedUsd: Double,
  val totalSavingsUsd: Double,
)

data class PricingIntelligenceSummary(
  val allOffers: List<EvaluatedOffer>,
  val comparisons: List<SupplierComparisonItem>,
  val priceChanges: List<PriceChangeItem>,
  val priceIncreaseCount: Int,
  val missingRateOffersCount: Int,
)

/**
 * Motor puro de compras a proveedores:
 * - Normaliza listas de precios en USD o Bs. (tasa fija o BCV global) y regla de IVA (+16% solo (G) o todo).
 * - Normaliza empaques (bultos/cajas) a costo unitario en USD para comparar proveedores en igualdad de condiciones.
 * - Compara qué proveedor vende más barato cada producto, detecta subidas/bajadas de costo entre listas
 *   y arma Pedidos Óptimos multi-proveedor listos para enviar por WhatsApp.
 */
object PricingIntelligenceEngine {

  private val explicitPackRegex = Regex(
    """(?i)\b(?:bulto|bto|caja|cja|cj|pack|pk|display|ristra|fardo|paquete|paca|carton|bandeja|estuche|bolsa)\s*(?:de|x|×|\*)?\s*(\d{1,4})\b"""
  )
  private val leadingMultiplierRegex = Regex("""(?i)^\s*[x×*]\s*(\d{1,4})\b""")
  private val inlineUnitsRegex = Regex("""(?i)\b(\d{1,4})\s*(?:und|unid|unidades|u\.?|pzas|piezas|sobres|latas|botellas)\b""")
  private val namePackWithMeasureRegex = Regex("""(?i)\b(\d{1,3})\s*[x×*]\s*\d+(?:[.,]\d+)?\s*(?:ml|cc|grs?|g|kg|l|lt|lts|oz)\b""")
  private val nameTrailingMultiplierRegex = Regex("""(?i)\b[x×*]\s*(\d{1,3})\b""")
  private val nameParenthesizedCountRegex = Regex("""\((\d{1,3})\)""")
  private val taxableTokenRegex = Regex("""(?i)(?:\(\s*g\s*\)|\[\s*g\s*]|\+\s*iva\b|\bgravado\b)""")

  fun detectIsTaxable(producto: Producto, posProduct: IsaacPosProducto?): TaxDetection {
    if (producto.esGravado != null) {
      return TaxDetection(isTaxable = producto.esGravado, isAutoDetected = false)
    }
    val detected = taxableTokenRegex.containsMatchIn(producto.nombre) ||
      (posProduct != null && taxableTokenRegex.containsMatchIn(posProduct.nombre))
    return TaxDetection(isTaxable = detected, isAutoDetected = true)
  }

  fun extractPackUnitsFromUnitText(rawUnit: String?): Double? {
    if (rawUnit.isNullOrBlank()) return null
    val text = rawUnit.trim()
    val lower = text.lowercase()
    if (lower.contains("media docena") || lower.contains("1/2 docena")) return 6.0
    if (lower.contains("docena") && !lower.any { it.isDigit() }) return 12.0

    explicitPackRegex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { n ->
      if (n in 2.0..1000.0) return n
    }
    leadingMultiplierRegex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { n ->
      if (n in 2.0..1000.0) return n
    }
    inlineUnitsRegex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { n ->
      if (n in 2.0..1000.0) return n
    }
    return null
  }

  fun extractPackUnitsFromProductName(rawName: String?): Double? {
    if (rawName.isNullOrBlank()) return null
    val text = rawName.trim()
    explicitPackRegex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { n ->
      if (n in 2.0..500.0) return n
    }
    namePackWithMeasureRegex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { n ->
      if (n in 2.0..200.0) return n
    }
    nameTrailingMultiplierRegex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { n ->
      if (n in 2.0..200.0) return n
    }
    return null
  }

  /**
   * Determina cuántas unidades contiene el precio del proveedor.
   * Usa la referencia interna del producto sincronizado solo para evitar dividir por error
   * precios que el vendedor ya cotizó por unidad.
   */
  fun detectPackUnits(
    producto: Producto,
    posProduct: IsaacPosProducto?,
    packCostUsdPreview: Double?,
    unitMagnitudeRefUsd: Double?,
  ): PackDetection {
    val manual = producto.unidadesPorEmpaque
    if (manual != null && manual > 0.0 && manual.isFinite()) {
      val label = if (manual > 1.0) "Empaque x ${formatFactor(manual)} (manual)" else "Unidad (manual)"
      return PackDetection(units = manual, isAutoDetected = false, label = label)
    }

    val fromOwnUnit = extractPackUnitsFromUnitText(producto.unidad)
    if (fromOwnUnit != null && fromOwnUnit > 1.0) {
      val looksAlreadyUnit = packCostUsdPreview != null && unitMagnitudeRefUsd != null && unitMagnitudeRefUsd > 0.0 &&
        packCostUsdPreview <= unitMagnitudeRefUsd * 1.45 && (packCostUsdPreview / fromOwnUnit) < unitMagnitudeRefUsd * 0.25
      if (!looksAlreadyUnit) {
        return PackDetection(
          units = fromOwnUnit,
          isAutoDetected = true,
          label = "${producto.unidad.trim()} (÷${formatFactor(fromOwnUnit)})",
        )
      }
    }

    val looksLikeBulk = packCostUsdPreview != null && unitMagnitudeRefUsd != null && unitMagnitudeRefUsd > 0.0 &&
      packCostUsdPreview > unitMagnitudeRefUsd * 1.8

    val fromPosUnit = extractPackUnitsFromUnitText(posProduct?.unidadPedido)
    if (fromPosUnit != null && fromPosUnit > 1.0) {
      val unitSuggestsBulk = isBulkUnitKeyword(producto.unidad)
      if (looksLikeBulk || (unitSuggestsBulk && (unitMagnitudeRefUsd == null || packCostUsdPreview == null || packCostUsdPreview > unitMagnitudeRefUsd * 1.45))) {
        return PackDetection(
          units = fromPosUnit,
          isAutoDetected = true,
          label = "${posProduct?.unidadPedido?.trim()} (÷${formatFactor(fromPosUnit)})",
        )
      }
    }

    val fromName = extractPackUnitsFromProductName(producto.nombre)
      ?: extractPackUnitsFromProductName(posProduct?.nombre)
      ?: if (looksLikeBulk) {
        (nameParenthesizedCountRegex.find(producto.nombre)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
          ?: posProduct?.nombre?.let { nameParenthesizedCountRegex.find(it)?.groupValues?.getOrNull(1)?.toDoubleOrNull() })
          ?.takeIf { it in 2.0..200.0 }
      } else null

    if (fromName != null && fromName > 1.0) {
      val unitSuggestsBulk = isBulkUnitKeyword(producto.unidad)
      if (looksLikeBulk || (unitSuggestsBulk && (unitMagnitudeRefUsd == null || packCostUsdPreview == null || packCostUsdPreview > unitMagnitudeRefUsd * 1.45))) {
        return PackDetection(
          units = fromName,
          isAutoDetected = true,
          label = "Empaque x ${formatFactor(fromName)} (÷${formatFactor(fromName)})",
        )
      }
    }

    return PackDetection(units = 1.0, isAutoDetected = true, label = "Por unidad")
  }

  fun computeEffectiveUnitCostUsd(
    rawPrice: Double,
    producto: Producto,
    catalogo: CatalogoArchivo?,
    posProduct: IsaacPosProducto?,
    settings: PricingSettings,
  ): Pair<Double?, Double?> {
    if (!rawPrice.isFinite() || rawPrice <= 0.0) return null to null
    val moneda = catalogo?.moneda ?: MonedaCatalogo.USD
    val tasaAplicada = if (moneda == MonedaCatalogo.VES) {
      catalogo?.tasaFijaBs?.takeIf { it > 0.0 } ?: settings.tasaBsPorUsd.takeIf { it > 0.0 }
    } else {
      null
    }
    if (moneda == MonedaCatalogo.VES && tasaAplicada == null) {
      return null to null
    }
    val baseUsd = if (moneda == MonedaCatalogo.VES) rawPrice / tasaAplicada!! else rawPrice
    val modoIva = catalogo?.modoIva ?: ModoIvaCatalogo.INCLUIDO
    val ivaPct = catalogo?.porcentajeIva ?: settings.defaultIvaPercent
    val tax = detectIsTaxable(producto, posProduct)
    val applyIva = when (modoIva) {
      ModoIvaCatalogo.INCLUIDO -> false
      ModoIvaCatalogo.SOLO_GRAVADOS -> tax.isTaxable
      ModoIvaCatalogo.TODO_CATALOGO -> true
    }
    val withIvaUsd = if (applyIva) baseUsd * (1.0 + ivaPct / 100.0) else baseUsd
    val unitRef = posProduct?.precioVenta ?: producto.precioVentaPos
    val pack = detectPackUnits(producto, posProduct, withIvaUsd, unitRef)
    return (withIvaUsd / pack.units) to tasaAplicada
  }

  fun computePosBaselineUnitCostUsd(pos: IsaacPosProducto): Double? {
    val raw = pos.costoUnitario?.takeIf { it > 0.0 && it.isFinite() } ?: return null
    val posPack = extractPackUnitsFromUnitText(pos.unidadPedido) ?: 1.0
    if (posPack > 1.0) {
      val unitRef = pos.precioVenta
      val looksAlreadyUnit = unitRef != null && unitRef > 0.0 && raw <= unitRef * 1.35 && (raw / posPack) < unitRef * 0.25
      if (!looksAlreadyUnit) {
        return raw / posPack
      }
    }
    return raw
  }

  fun evaluateOffer(
    producto: Producto,
    proveedor: Proveedor,
    catalogo: CatalogoArchivo?,
    posProduct: IsaacPosProducto?,
    settings: PricingSettings,
  ): EvaluatedOffer {
    val moneda = catalogo?.moneda ?: MonedaCatalogo.USD
    val modoIva = catalogo?.modoIva ?: ModoIvaCatalogo.INCLUIDO
    val ivaPct = catalogo?.porcentajeIva ?: settings.defaultIvaPercent
    val tasaAplicada = if (moneda == MonedaCatalogo.VES) {
      catalogo?.tasaFijaBs?.takeIf { it > 0.0 } ?: settings.tasaBsPorUsd.takeIf { it > 0.0 }
    } else {
      null
    }
    val missingRate = moneda == MonedaCatalogo.VES && tasaAplicada == null && (producto.precio ?: 0.0) > 0.0
    val taxDetection = detectIsTaxable(producto, posProduct)
    val ivaApplied = when (modoIva) {
      ModoIvaCatalogo.INCLUIDO -> false
      ModoIvaCatalogo.SOLO_GRAVADOS -> taxDetection.isTaxable
      ModoIvaCatalogo.TODO_CATALOGO -> true
    }

    val rawPrice = producto.precio?.takeIf { it > 0.0 && it.isFinite() }
    val packUsd = if (rawPrice != null && !missingRate) {
      val converted = if (moneda == MonedaCatalogo.VES) rawPrice / tasaAplicada!! else rawPrice
      if (ivaApplied) converted * (1.0 + ivaPct / 100.0) else converted
    } else {
      null
    }

    val unitRef = posProduct?.precioVenta?.takeIf { it > 0.0 } ?: producto.precioVentaPos?.takeIf { it > 0.0 }
    val packDetection = detectPackUnits(producto, posProduct, packUsd, unitRef)
    val unitCostUsd = packUsd?.let { it / packDetection.units }

    val canonicalKey = canonicalKeyFor(producto, posProduct)
    val isPrimary = posProduct != null &&
      posProduct.proveedorNombreRemoto.isNotBlank() &&
      normalizeKey(posProduct.proveedorNombreRemoto) == normalizeKey(proveedor.nombre)

    return EvaluatedOffer(
      producto = producto,
      proveedor = proveedor,
      catalogo = catalogo,
      posProduct = posProduct,
      canonicalKey = canonicalKey,
      displayName = posProduct?.nombre?.ifBlank { producto.nombre } ?: producto.nombre,
      moneda = moneda,
      modoIva = modoIva,
      tasaAplicadaBs = tasaAplicada,
      missingExchangeRate = missingRate,
      packDetection = packDetection,
      taxDetection = taxDetection,
      ivaApplied = ivaApplied,
      ivaPercentApplied = if (ivaApplied) ivaPct else 0.0,
      rawPriceOriginal = rawPrice,
      packCostUsd = packUsd,
      effectiveUnitCostUsd = unitCostUsd,
      stockPos = posProduct?.stockActual ?: producto.stockPos,
      isPrimaryPosSupplier = isPrimary,
      updatedAtMillis = catalogo?.importadoEn ?: proveedor.creadoEn,
    )
  }

  fun analyze(
    proveedores: List<Proveedor>,
    productos: List<Producto>,
    catalogos: List<CatalogoArchivo>,
    posProducts: List<IsaacPosProducto>,
    historialPrecios: List<HistorialPrecio>,
    settings: PricingSettings,
  ): PricingIntelligenceSummary {
    val proveedorById = proveedores.associateBy { it.id }
    val catalogoById = catalogos.associateBy { it.id }
    val posById = posProducts.associateBy { it.productoId }

    val allOffers = productos.mapNotNull { prod ->
      val prov = proveedorById[prod.proveedorId] ?: return@mapNotNull null
      val cat = prod.catalogoId?.let { catalogoById[it] }
      val pos = prod.isaacPosId?.let { posById[it] }
      evaluateOffer(
        producto = prod,
        proveedor = prov,
        catalogo = cat,
        posProduct = pos,
        settings = settings,
      )
    }

    val pricedOffers = allOffers.filter { it.effectiveUnitCostUsd != null && it.effectiveUnitCostUsd > 0.0 }

    val latestOfferPerKeyAndProvider = pricedOffers
      .groupBy { it.canonicalKey to it.proveedor.id }
      .mapValues { (_, list) ->
        list.maxWith(compareBy<EvaluatedOffer> { it.updatedAtMillis }.thenBy { it.producto.id })
      }
      .values
      .toList()

    val offersByCanonicalKey = latestOfferPerKeyAndProvider.groupBy { it.canonicalKey }

    // 1. Comparador multi-proveedor (productos presentes en >= 2 proveedores)
    val comparisons = offersByCanonicalKey.mapNotNull { (key, offers) ->
      if (offers.size < 2) return@mapNotNull null
      val sorted = offers.sortedBy { it.effectiveUnitCostUsd!! }
      val cheapest = sorted.first()
      val expensive = sorted.last()
      val diffUsd = (expensive.effectiveUnitCostUsd!! - cheapest.effectiveUnitCostUsd!!).coerceAtLeast(0.0)
      val diffPct = if (expensive.effectiveUnitCostUsd > 0.0) {
        (diffUsd / expensive.effectiveUnitCostUsd) * 100.0
      } else 0.0
      SupplierComparisonItem(
        canonicalKey = key,
        displayName = cheapest.displayName,
        codigoBarra = cheapest.posProduct?.codigoBarra?.ifBlank { cheapest.producto.codigo } ?: cheapest.producto.codigo,
        stockPos = cheapest.stockPos,
        offers = sorted,
        cheapestOffer = cheapest,
        mostExpensiveOffer = expensive,
        savingsPerUnitUsd = diffUsd,
        savingsPercent = diffPct,
      )
    }.sortedWith(compareByDescending<SupplierComparisonItem> { it.savingsPercent }.thenByDescending { it.savingsPerUnitUsd })

    // 2. Variaciones de costo de proveedores
    val priceChanges = buildPriceChanges(
      historialPrecios = historialPrecios,
      latestOffers = latestOfferPerKeyAndProvider,
      proveedorById = proveedorById,
    )

    return PricingIntelligenceSummary(
      allOffers = allOffers,
      comparisons = comparisons,
      priceChanges = priceChanges,
      priceIncreaseCount = priceChanges.count { it.isIncrease },
      missingRateOffersCount = allOffers.count { it.missingExchangeRate },
    )
  }

  private fun buildPriceChanges(
    historialPrecios: List<HistorialPrecio>,
    latestOffers: List<EvaluatedOffer>,
    proveedorById: Map<Long, Proveedor>,
  ): List<PriceChangeItem> {
    val changes = mutableListOf<PriceChangeItem>()
    val coveredKeys = mutableSetOf<String>()

    val groupedHistory = historialPrecios
      .filter { it.costoUnitarioUsd > 0.0 }
      .groupBy { h ->
        val prodKey = h.isaacPosId?.takeIf { it.isNotBlank() }?.let { "pos:$it" }
          ?: h.productoId?.let { "prod:$it" }
          ?: "name:${normalizeKey(h.nombreProducto)}"
        "${h.proveedorId}|$prodKey"
      }

    for ((groupKey, entries) in groupedHistory) {
      if (entries.size < 2) continue
      val ordered = entries.sortedWith(compareByDescending<HistorialPrecio> { it.registradoEn }.thenByDescending { it.id })
      val current = ordered.first()
      val previous = ordered.drop(1).firstOrNull { prev ->
        abs(current.costoUnitarioUsd - prev.costoUnitarioUsd) >= 0.005
      } ?: continue

      val deltaUsd = current.costoUnitarioUsd - previous.costoUnitarioUsd
      val deltaPct = (deltaUsd / previous.costoUnitarioUsd) * 100.0
      if (abs(deltaPct) < 0.5 && abs(deltaUsd) < 0.01) continue

      val provName = proveedorById[current.proveedorId]?.nombre ?: "Proveedor #${current.proveedorId}"
      changes.add(
        PriceChangeItem(
          idKey = "hist:$groupKey",
          proveedorId = current.proveedorId,
          proveedorNombre = provName,
          productName = current.nombreProducto,
          unidad = current.unidad,
          previousUnitCostUsd = previous.costoUnitarioUsd,
          currentUnitCostUsd = current.costoUnitarioUsd,
          deltaUsd = deltaUsd,
          deltaPercent = deltaPct,
          previousDateMillis = previous.registradoEn,
          currentDateMillis = current.registradoEn,
          origin = PriceChangeOrigin.CATALOG_HISTORY,
        )
      )
      coveredKeys.add(groupKey)
    }

    for (offer in latestOffers) {
      val pos = offer.posProduct ?: continue
      val currentCost = offer.effectiveUnitCostUsd ?: continue
      if (offer.catalogo == null) continue
      val groupKey = "${offer.proveedor.id}|pos:${pos.productoId}"
      if (groupKey in coveredKeys) continue

      val posUnitCost = computePosBaselineUnitCostUsd(pos) ?: continue
      val deltaUsd = currentCost - posUnitCost
      val deltaPct = (deltaUsd / posUnitCost) * 100.0
      if (abs(deltaPct) < 1.0 && abs(deltaUsd) < 0.01) continue

      changes.add(
        PriceChangeItem(
          idKey = "posdiff:$groupKey",
          proveedorId = offer.proveedor.id,
          proveedorNombre = offer.proveedor.nombre,
          productName = offer.displayName,
          unidad = offer.producto.unidad,
          previousUnitCostUsd = posUnitCost,
          currentUnitCostUsd = currentCost,
          deltaUsd = deltaUsd,
          deltaPercent = deltaPct,
          previousDateMillis = pos.sincronizadoEn,
          currentDateMillis = offer.updatedAtMillis,
          origin = PriceChangeOrigin.CATALOG_VS_POS,
        )
      )
    }

    return changes.sortedWith(
      compareByDescending<PriceChangeItem> { it.isIncrease }
        .thenByDescending { abs(it.deltaPercent) }
        .thenByDescending { it.currentDateMillis }
    )
  }

  fun buildOptimalOrderPlan(
    allOffers: List<EvaluatedOffer>,
    lowStockThreshold: Double,
    onlyLowStock: Boolean = true,
    minSavingsPercent: Double = 3.0,
    manualProviderOverrides: Map<String, Long> = emptyMap(),
    quantitiesByKey: Map<String, Double> = emptyMap(),
    excludedKeys: Set<String> = emptySet(),
  ): OptimalOrderPlan {
    val latestPerKeyAndProv = allOffers
      .filter { it.effectiveUnitCostUsd != null && it.effectiveUnitCostUsd > 0.0 }
      .groupBy { it.canonicalKey to it.proveedor.id }
      .mapValues { (_, list) ->
        list.maxWith(compareBy<EvaluatedOffer> { it.updatedAtMillis }.thenBy { it.producto.id })
      }
      .values
      .groupBy { it.canonicalKey }

    val lines = mutableListOf<OptimalOrderLine>()

    for ((key, offers) in latestPerKeyAndProv) {
      if (key in excludedKeys) continue
      val sorted = offers.sortedBy { it.effectiveUnitCostUsd!! }
      val cheapest = sorted.first()
      val stock = cheapest.stockPos
      val stockMin = cheapest.posProduct?.stockMinimo ?: lowStockThreshold
      val effectiveThreshold = maxOf(lowStockThreshold, stockMin)

      if (onlyLowStock) {
        if (stock == null || stock > effectiveThreshold) continue
      }

      val primaryPosOffer = sorted.firstOrNull { it.isPrimaryPosSupplier }
      val recommended = if (primaryPosOffer != null && primaryPosOffer.proveedor.id != cheapest.proveedor.id) {
        val savePct = ((primaryPosOffer.effectiveUnitCostUsd!! - cheapest.effectiveUnitCostUsd!!) /
          primaryPosOffer.effectiveUnitCostUsd) * 100.0
        if (savePct >= minSavingsPercent) cheapest else primaryPosOffer
      } else {
        cheapest
      }

      val overrideProvId = manualProviderOverrides[key]
      val selected = overrideProvId?.let { id -> sorted.firstOrNull { it.proveedor.id == id } } ?: recommended

      val referenceCost = primaryPosOffer?.effectiveUnitCostUsd
        ?: sorted.last().effectiveUnitCostUsd!!
      val savingsPerUnit = (referenceCost - selected.effectiveUnitCostUsd!!).coerceAtLeast(0.0)
      val qtyPacks = (quantitiesByKey[key] ?: 1.0).coerceAtLeast(0.0)
      if (qtyPacks <= 0.0) continue

      val packCost = selected.packCostUsd ?: selected.effectiveUnitCostUsd
      val totalLineUsd = packCost * qtyPacks
      val totalLineSavings = savingsPerUnit * selected.packDetection.units * qtyPacks

      lines.add(
        OptimalOrderLine(
          canonicalKey = key,
          displayName = selected.displayName,
          codigo = selected.posProduct?.codigoBarra?.ifBlank { selected.producto.codigo } ?: selected.producto.codigo,
          stockPos = stock,
          stockMinimo = stockMin,
          allOffers = sorted,
          recommendedOffer = recommended,
          selectedOffer = selected,
          referenceCostUsd = referenceCost,
          savingsPerUnitUsd = savingsPerUnit,
          quantityPacks = qtyPacks,
          estimatedLineTotalUsd = totalLineUsd,
          estimatedLineSavingsUsd = totalLineSavings,
        )
      )
    }

    val orderedLines = lines.sortedWith(
      compareByDescending<OptimalOrderLine> { it.allOffers.size > 1 }
        .thenByDescending { it.estimatedLineSavingsUsd }
        .thenBy { it.stockPos ?: Double.MAX_VALUE }
        .thenBy { it.displayName.lowercase() }
    )

    val buckets = orderedLines
      .groupBy { it.selectedOffer.proveedor.id }
      .values
      .map { bucketLines ->
        val prov = bucketLines.first().selectedOffer.proveedor
        OptimalProviderBucket(
          proveedor = prov,
          lines = bucketLines,
          totalEstimatedUsd = bucketLines.sumOf { it.estimatedLineTotalUsd },
          totalSavingsUsd = bucketLines.sumOf { it.estimatedLineSavingsUsd },
        )
      }
      .sortedByDescending { it.lines.size }

    return OptimalOrderPlan(
      lines = orderedLines,
      buckets = buckets,
      totalEstimatedUsd = buckets.sumOf { it.totalEstimatedUsd },
      totalSavingsUsd = buckets.sumOf { it.totalSavingsUsd },
    )
  }

  fun buildDraftPedidosFromPlan(
    plan: OptimalOrderPlan,
    nowMillis: Long = System.currentTimeMillis(),
  ): List<Pedido> {
    return plan.buckets.mapNotNull { bucket ->
      if (bucket.lines.isEmpty()) return@mapNotNull null
      val items = bucket.lines.map { line ->
        val prod = line.selectedOffer.producto
        PedidoItem(
          productoId = prod.id,
          codigo = prod.codigo,
          nombre = prod.nombre,
          unidad = prod.unidad,
          precioUnitario = line.selectedOffer.packCostUsd ?: prod.precio,
          cantidad = line.quantityPacks,
        )
      }
      Pedido(
        proveedorId = bucket.proveedor.id,
        proveedorNombre = bucket.proveedor.nombre,
        proveedorTelefono = bucket.proveedor.telefonoWhatsApp,
        estado = PedidoEstado.BORRADOR,
        notas = "Generado desde Pedido Óptimo (${items.size} productos)",
        incluirPrecios = false,
        creadoEn = nowMillis,
        items = items,
      )
    }
  }

  fun canonicalKeyFor(producto: Producto, posProduct: IsaacPosProducto?): String {
    val posId = posProduct?.productoId ?: producto.isaacPosId
    if (!posId.isNullOrBlank()) return "pos:$posId"
    val cleanCode = producto.codigo.filter { it.isLetterOrDigit() }.lowercase()
    if (cleanCode.length >= 6) return "code:$cleanCode"
    return "local:${producto.id}"
  }

  private fun isBulkUnitKeyword(rawUnit: String): Boolean {
    val lower = rawUnit.trim().lowercase()
    return lower.startsWith("bulto") ||
      lower.startsWith("bto") ||
      lower.startsWith("caja") ||
      lower.startsWith("cja") ||
      lower.startsWith("pack") ||
      lower.startsWith("display") ||
      lower.startsWith("ristra") ||
      lower.startsWith("fardo") ||
      lower.startsWith("paca") ||
      lower.startsWith("docena")
  }

  private fun formatFactor(valNum: Double): String =
    if (abs(valNum - valNum.toLong()) < 1e-6) valNum.toLong().toString() else "%.2f".format(valNum)

  private fun normalizeKey(raw: String): String {
    val decomposed = Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD)
    return decomposed
      .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
      .replace(Regex("[^a-z0-9]+"), " ")
      .trim()
  }
}
