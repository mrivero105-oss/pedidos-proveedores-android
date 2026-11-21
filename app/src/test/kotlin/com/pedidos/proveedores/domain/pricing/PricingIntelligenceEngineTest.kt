package com.pedidos.proveedores.domain.pricing

import com.pedidos.proveedores.data.local.PricingSettings
import com.pedidos.proveedores.domain.model.CatalogoArchivo
import com.pedidos.proveedores.domain.model.CatalogoFormato
import com.pedidos.proveedores.domain.model.HistorialPrecio
import com.pedidos.proveedores.domain.model.IsaacPosProducto
import com.pedidos.proveedores.domain.model.ModoIvaCatalogo
import com.pedidos.proveedores.domain.model.MonedaCatalogo
import com.pedidos.proveedores.domain.model.Producto
import com.pedidos.proveedores.domain.model.Proveedor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PricingIntelligenceEngineTest {

  private val provServicpan = Proveedor(id = 1L, nombre = "Distribuidora SERVICPAN", telefonoWhatsApp = "04121141566")
  private val provTinito = Proveedor(id = 2L, nombre = "COMERCIAL TINITO BARCELONA", telefonoWhatsApp = "04248206369")
  private val defaultSettings = PricingSettings(
    tasaBsPorUsd = 900.0,
    tasaSourceLabel = "BCV oficial",
    defaultIvaPercent = 16.0,
    minOptimalSavingsPercent = 3.0,
  )

  @Test
  fun normalizesBulkPackToUnitAndAvoidsDividingAlreadyUnitPrices() {
    val posSedal = IsaacPosProducto(
      productoId = "pos-sedal",
      nombre = "SEDAL SHAMPOO CERANIDAS RISTRA 12X24ML (G)",
      precioVenta = 0.52,
      stockActual = 48.0,
      unidadPedido = "Bulto x 12",
      costoUnitario = 4.78,
      proveedorNombreRemoto = "COMERCIAL TINITO BARCELONA",
    )
    val prodBulto = Producto(
      id = 10L,
      proveedorId = 2L,
      nombre = "SEDAL SHAMPOO CERANIDAS RISTRA 12X24ML (G)",
      unidad = "Bulto x 12",
      precio = 4.78,
      isaacPosId = "pos-sedal",
    )

    val offerBulk = PricingIntelligenceEngine.evaluateOffer(
      producto = prodBulto,
      proveedor = provTinito,
      catalogo = null,
      posProduct = posSedal,
      settings = defaultSettings,
    )

    assertEquals(12.0, offerBulk.packDetection.units, 0.001)
    assertEquals(4.78 / 12.0, offerBulk.effectiveUnitCostUsd!!, 0.001)

    // Si el vendedor cotiza por unidad ($0.40) aunque la ficha diga Bulto x 12, no debe dividir entre 12
    val prodUnit = Producto(
      id = 11L,
      proveedorId = 1L,
      nombre = "SEDAL SHAMPOO CERANIDAS",
      unidad = "u",
      precio = 0.40,
      isaacPosId = "pos-sedal",
    )
    val offerUnit = PricingIntelligenceEngine.evaluateOffer(
      producto = prodUnit,
      proveedor = provServicpan,
      catalogo = null,
      posProduct = posSedal,
      settings = defaultSettings,
    )
    assertEquals(1.0, offerUnit.packDetection.units, 0.001)
    assertEquals(0.40, offerUnit.effectiveUnitCostUsd!!, 0.001)
  }

  @Test
  fun convertsVesCatalogAndAppliesIvaOnlyToTaxableProductsMarkedG() {
    val catBsPlusIva = CatalogoArchivo(
      id = 100L,
      proveedorId = 2L,
      nombreArchivo = "Lista_Bs_Sin_IVA.pdf",
      formato = CatalogoFormato.PDF,
      rutaLocal = "/tmp/a.pdf",
      productosImportados = 2,
      moneda = MonedaCatalogo.VES,
      tasaFijaBs = null,
      modoIva = ModoIvaCatalogo.SOLO_GRAVADOS,
      porcentajeIva = 16.0,
    )

    val posGravado = IsaacPosProducto(
      productoId = "pos-mermelada",
      nombre = "MERMELADA DE GUAYABA 250 GRS (12) (G)",
      precioVenta = 3.31,
      stockActual = 1.0,
      unidadPedido = "u",
    )
    val prodGravado = Producto(
      id = 20L,
      proveedorId = 2L,
      catalogoId = 100L,
      nombre = "Mermelada Guayaba 250g",
      unidad = "u",
      precio = 2250.0, // Bs 2250 / 900 = $2.50 -> +16% IVA = $2.90
      isaacPosId = "pos-mermelada",
    )

    val posExento = IsaacPosProducto(
      productoId = "pos-pasta",
      nombre = "Pasta Corta Primor 500 grs",
      precioVenta = 1.32,
      stockActual = 10.0,
      unidadPedido = "u",
    )
    val prodExento = Producto(
      id = 21L,
      proveedorId = 2L,
      catalogoId = 100L,
      nombre = "Pasta Corta Primor 500g",
      unidad = "u",
      precio = 900.0, // Bs 900 / 900 = $1.00 -> Exento = $1.00
      isaacPosId = "pos-pasta",
    )

    val evalGravado = PricingIntelligenceEngine.evaluateOffer(prodGravado, provTinito, catBsPlusIva, posGravado, defaultSettings)
    val evalExento = PricingIntelligenceEngine.evaluateOffer(prodExento, provTinito, catBsPlusIva, posExento, defaultSettings)

    assertTrue(evalGravado.taxDetection.isTaxable)
    assertTrue(evalGravado.ivaApplied)
    assertEquals(2.90, evalGravado.effectiveUnitCostUsd!!, 0.001)

    assertFalse(evalExento.taxDetection.isTaxable)
    assertFalse(evalExento.ivaApplied)
    assertEquals(1.00, evalExento.effectiveUnitCostUsd!!, 0.001)
  }

  @Test
  fun comparesMultipleSuppliersAcrossDifferentPacksAndCalculatesSavings() {
    val posPepsi = IsaacPosProducto(
      productoId = "pos-pepsi",
      nombre = "Refresco Pepsi 1L",
      precioVenta = 1.08,
      stockActual = 2.0,
      unidadPedido = "Bulto x 6",
      costoUnitario = 5.00,
      proveedorNombreRemoto = "Distribuidora SERVICPAN",
    )
    val catServicpan = CatalogoArchivo(
      id = 1L,
      proveedorId = 1L,
      nombreArchivo = "Servicpan_Nov.pdf",
      formato = CatalogoFormato.PDF,
      rutaLocal = "/tmp/s.pdf",
      productosImportados = 1,
      importadoEn = 2_000L,
    )
    val catTinito = CatalogoArchivo(
      id = 2L,
      proveedorId = 2L,
      nombreArchivo = "Tinito_Nov.pdf",
      formato = CatalogoFormato.PDF,
      rutaLocal = "/tmp/t.pdf",
      productosImportados = 1,
      importadoEn = 1_000L,
    )
    // SERVICPAN: Bulto x 6 a $6.00 -> $1.00/u
    val offerExpensive = Producto(
      id = 30L,
      proveedorId = 1L,
      catalogoId = 1L,
      nombre = "Refresco Pepsi 1L",
      unidad = "Bulto x 6",
      precio = 6.00,
      stockPos = 2.0,
      isaacPosId = "pos-pepsi",
    )
    // TINITO: Caja x 12 a $9.60 -> $0.80/u (ahorra $0.20/u = 20%)
    val offerCheap = Producto(
      id = 31L,
      proveedorId = 2L,
      catalogoId = 2L,
      nombre = "Pepsi 1L Caja",
      unidad = "Caja x 12",
      precio = 9.60,
      stockPos = 2.0,
      isaacPosId = "pos-pepsi",
    )

    val summary = PricingIntelligenceEngine.analyze(
      proveedores = listOf(provServicpan, provTinito),
      productos = listOf(offerExpensive, offerCheap),
      catalogos = listOf(catServicpan, catTinito),
      posProducts = listOf(posPepsi),
      historialPrecios = emptyList(),
      settings = defaultSettings,
    )

    assertEquals(1, summary.comparisons.size)
    val cmp = summary.comparisons.first()
    assertEquals(provTinito.id, cmp.cheapestOffer.proveedor.id)
    assertEquals(0.80, cmp.cheapestOffer.effectiveUnitCostUsd!!, 0.001)
    assertEquals(0.20, cmp.savingsPerUnitUsd, 0.001)
    assertEquals(20.0, cmp.savingsPercent, 0.1)
  }

  @Test
  fun detectsSupplierCostIncreasesAndBuildsOptimalDraftOrders() {
    val posPepsi = IsaacPosProducto(
      productoId = "pos-pepsi",
      nombre = "Refresco Pepsi 1L",
      precioVenta = 1.08,
      stockActual = 2.0,
      stockMinimo = 5.0,
      unidadPedido = "Bulto x 6",
      costoUnitario = 5.00,
      proveedorNombreRemoto = "Distribuidora SERVICPAN",
    )
    val posPasta = IsaacPosProducto(
      productoId = "pos-pasta",
      nombre = "Pasta Corta Primor 500 grs",
      precioVenta = 1.32,
      stockActual = 1.0,
      stockMinimo = 5.0,
      unidadPedido = "u",
      costoUnitario = 1.02,
      proveedorNombreRemoto = "Distribuidora SERVICPAN",
    )

    val productos = listOf(
      Producto(id = 40L, proveedorId = 1L, nombre = "Pepsi 1L", unidad = "Bulto x 6", precio = 6.00, stockPos = 2.0, isaacPosId = "pos-pepsi"),
      Producto(id = 41L, proveedorId = 2L, nombre = "Pepsi 1L", unidad = "Bulto x 6", precio = 4.80, stockPos = 2.0, isaacPosId = "pos-pepsi"),
      Producto(id = 42L, proveedorId = 1L, nombre = "Pasta Primor 500g", unidad = "u", precio = 1.00, stockPos = 1.0, isaacPosId = "pos-pasta"),
      // Tinito solo es 1% más barato en Pasta ($0.99 vs $1.00) -> con minSavings=3% mantiene SERVICPAN
      Producto(id = 43L, proveedorId = 2L, nombre = "Pasta Primor 500g", unidad = "u", precio = 0.99, stockPos = 1.0, isaacPosId = "pos-pasta"),
    )

    val history = listOf(
      HistorialPrecio(
        id = 1L,
        proveedorId = 1L,
        productoId = 40L,
        isaacPosId = "pos-pepsi",
        nombreProducto = "Pepsi 1L",
        unidad = "Bulto x 6",
        precioOriginal = 5.00,
        costoUnitarioUsd = 0.8333,
        registradoEn = 1_000L,
      ),
      HistorialPrecio(
        id = 2L,
        proveedorId = 1L,
        productoId = 40L,
        isaacPosId = "pos-pepsi",
        nombreProducto = "Pepsi 1L",
        unidad = "Bulto x 6",
        precioOriginal = 6.00,
        costoUnitarioUsd = 1.00,
        registradoEn = 2_000L,
      ),
    )

    val summary = PricingIntelligenceEngine.analyze(
      proveedores = listOf(provServicpan, provTinito),
      productos = productos,
      catalogos = emptyList(),
      posProducts = listOf(posPepsi, posPasta),
      historialPrecios = history,
      settings = defaultSettings,
    )

    assertEquals(1, summary.priceIncreaseCount)
    val pepsiIncrease = summary.priceChanges.firstOrNull { it.productName == "Pepsi 1L" }
    assertNotNull(pepsiIncrease)
    assertTrue(pepsiIncrease!!.isIncrease)
    assertEquals(20.0, pepsiIncrease.deltaPercent, 0.2)
    assertEquals(PriceChangeOrigin.CATALOG_HISTORY, pepsiIncrease.origin)

    val plan = PricingIntelligenceEngine.buildOptimalOrderPlan(
      allOffers = summary.allOffers,
      lowStockThreshold = 5.0,
      onlyLowStock = true,
      minSavingsPercent = 3.0,
    )
    assertEquals(2, plan.lines.size)
    assertEquals(2, plan.buckets.size)

    val pepsiLine = plan.lines.first { it.canonicalKey == "pos:pos-pepsi" }
    val pastaLine = plan.lines.first { it.canonicalKey == "pos:pos-pasta" }
    assertEquals(provTinito.id, pepsiLine.selectedOffer.proveedor.id)
    assertEquals(provServicpan.id, pastaLine.selectedOffer.proveedor.id)

    val drafts = PricingIntelligenceEngine.buildDraftPedidosFromPlan(plan)
    assertEquals(2, drafts.size)
    assertTrue(drafts.any { it.proveedorId == provTinito.id && it.items.size == 1 })
    assertTrue(drafts.any { it.proveedorId == provServicpan.id && it.items.size == 1 })
  }
}
