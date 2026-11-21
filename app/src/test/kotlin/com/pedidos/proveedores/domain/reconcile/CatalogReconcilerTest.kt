package com.pedidos.proveedores.domain.reconcile

import com.pedidos.proveedores.domain.model.IsaacPosProducto
import com.pedidos.proveedores.domain.model.Producto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogReconcilerTest {

  private val posDatabase = listOf(
    IsaacPosProducto(
      productoId = "pos-cafe-100",
      codigoBarra = "7595461001206",
      nombre = "Cafe Amanecer 100 Grs",
      categoria = "Víveres",
      precioVenta = 1.52,
      stockActual = 50.0,
      proveedorNombreRemoto = "ITC Distribuciones oriente",
      unidadPedido = "Bulto x 50",
      costoUnitario = 57.20,
    ),
    IsaacPosProducto(
      productoId = "pos-cafe-500",
      codigoBarra = "7595461000032",
      nombre = "Cafe Amanecer 500 Grs",
      categoria = "Víveres",
      precioVenta = 7.10,
      stockActual = 4.0,
      proveedorNombreRemoto = "ITC Distribuciones oriente",
      unidadPedido = "Bulto x 10",
      costoUnitario = 58.00,
    ),
    IsaacPosProducto(
      productoId = "pos-coca-2l",
      codigoBarra = "",
      nombre = "Coca-Cola 2l",
      categoria = "Bebidas",
      precioVenta = 2.00,
      stockActual = 92.0,
      proveedorNombreRemoto = "Gaseosas de Venezuela C.A",
      unidadPedido = "Bulto x 6",
      costoUnitario = 8.01,
    ),
    IsaacPosProducto(
      productoId = "pos-coca-1l",
      codigoBarra = "",
      nombre = "Coca-Cola 1l",
      categoria = "Bebidas",
      precioVenta = 1.25,
      stockActual = 62.0,
      proveedorNombreRemoto = "Gaseosas de Venezuela C.A",
      unidadPedido = "Bulto x 12",
      costoUnitario = 10.20,
    ),
    IsaacPosProducto(
      productoId = "pos-sedal",
      codigoBarra = "",
      nombre = "SEDAL SHAMPOO CERANIDAS RISTRA 12X24ML",
      categoria = "Higiene Personal",
      precioVenta = 0.55,
      stockActual = 24.0,
      proveedorNombreRemoto = "COMERCIAL TINITO BARCELONA",
      unidadPedido = "Bulto x 12",
      costoUnitario = 4.78,
    ),
    IsaacPosProducto(
      productoId = "pos-solo-pos",
      codigoBarra = "",
      nombre = "Sardina Dellanonna",
      categoria = "Víveres",
      precioVenta = 1.10,
      stockActual = 3.0,
      proveedorNombreRemoto = "ITC Distribuciones oriente",
    ),
  )

  @Test
  fun matchesByExactBarcodeAndRespectsMeasuresWithoutMixingSizes() {
    val catalog = listOf(
      // Ya vinculado previamente
      Producto(
        id = 1L,
        proveedorId = 10L,
        catalogoId = 1L,
        codigo = "7595461001206",
        nombre = "Café Amanecer 100g",
        unidad = "bulto x 50 u",
        isaacPosId = "pos-cafe-100",
        stockPos = 50.0,
      ),
      // Sin código de barras, pero con 500g -> debe sugerir pos-cafe-500 y NO pos-cafe-100
      Producto(
        id = 2L,
        proveedorId = 10L,
        catalogoId = 1L,
        codigo = "",
        nombre = "CAFÉ AMANECER MOLIDO 500 G",
        unidad = "bulto x 10",
      ),
      // Producto completamente nuevo del vendedor
      Producto(
        id = 3L,
        proveedorId = 10L,
        catalogoId = 1L,
        codigo = "9999999999999",
        nombre = "Té Verde Matcha Premium 250g",
        unidad = "caja x 12",
      ),
    )

    val report = CatalogReconciler.reconcile(
      providerName = "ITC Distribuciones oriente",
      catalogProducts = catalog,
      allPosProducts = posDatabase,
    )

    assertEquals(1, report.vinculados.size)
    assertEquals("pos-cafe-100", report.vinculados[0].posProduct.productoId)

    assertEquals(1, report.sugeridos.size)
    assertEquals(2L, report.sugeridos[0].localProduct.id)
    assertEquals("pos-cafe-500", report.sugeridos[0].best.posProduct.productoId)
    assertTrue(report.sugeridos[0].best.confidencePercent >= 70)

    assertEquals(1, report.soloEnCatalogo.size)
    assertEquals("Té Verde Matcha Premium 250g", report.soloEnCatalogo[0].nombre)

    // pos-cafe-500 (aún sin aprobar) y pos-solo-pos siguen apareciendo en soloEnPos hasta vincular
    assertTrue(report.soloEnPos.any { it.productoId == "pos-solo-pos" })
  }

  @Test
  fun matchesVolumesAndToleratesSingleCharacterTypos() {
    val catalog = listOf(
      Producto(
        id = 10L,
        proveedorId = 20L,
        catalogoId = 2L,
        nombre = "COCA COLA 2 LTS (6 UND)",
        unidad = "Bulto x 6",
      ),
      Producto(
        id = 11L,
        proveedorId = 20L,
        catalogoId = 2L,
        nombre = "SEDAL SHAMPOO CERAMIDAS RISTRA 12X24ML",
        unidad = "Bulto x 12",
      ),
    )

    val report = CatalogReconciler.reconcile(
      providerName = "Gaseosas de Venezuela C.A",
      catalogProducts = catalog,
      allPosProducts = posDatabase,
    )

    assertEquals(2, report.sugeridos.size)
    val cocaSuggestion = report.sugeridos.first { it.localProduct.id == 10L }
    assertEquals("pos-coca-2l", cocaSuggestion.best.posProduct.productoId)

    val sedalSuggestion = report.sugeridos.first { it.localProduct.id == 11L }
    assertEquals("pos-sedal", sedalSuggestion.best.posProduct.productoId)
  }

  @Test
  fun manualSearchFindsByBarcodeAndPartialName() {
    val byBarcode = CatalogReconciler.searchPosProducts("7595461001206", posDatabase)
    assertEquals("pos-cafe-100", byBarcode.first().productoId)

    val byName = CatalogReconciler.searchPosProducts("coca 2l", posDatabase)
    assertEquals("pos-coca-2l", byName.first().productoId)
  }
}
