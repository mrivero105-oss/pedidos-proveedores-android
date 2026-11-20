package com.pedidos.proveedores.domain.isaacpos

import com.pedidos.proveedores.domain.model.Producto
import com.pedidos.proveedores.domain.model.Proveedor
import org.junit.Assert.assertEquals
import org.junit.Test

class IsaacPosSyncTest {

  @Test
  fun parsesBothEnrichedViewAndBaseCloudProductosJson() {
    val enrichedJson = """
      [
        {
          "producto_id": "pos-1",
          "sucursal_id": "principal",
          "codigo_barra": "7595461001206",
          "producto_nombre": "Café Amanecer 100g",
          "categoria": "Víveres",
          "precio_venta": "1.80",
          "stock_actual": "3.00",
          "proveedor_nombre": "Grupo Amanecer",
          "proveedor_telefono": "+58 412 5550000",
          "unidad_pedido": "bulto x 50 u",
          "costo_unitario": "62.50",
          "stock_minimo": "5"
        }
      ]
    """.trimIndent()

    val enriched = IsaacPosJsonParser.parseProductsArray(enrichedJson)
    assertEquals(1, enriched.size)
    assertEquals("pos-1", enriched[0].productoId)
    assertEquals("7595461001206", enriched[0].codigoBarra)
    assertEquals("Grupo Amanecer", enriched[0].proveedorNombreRemoto)
    assertEquals(3.0, enriched[0].stockActual, 0.001)
    assertEquals(62.50, enriched[0].costoUnitario!!, 0.001)

    val baseJson = """
      [
        {
          "id": "1772071374412-ghbyfjbco",
          "sucursal_id": "principal",
          "codigo_barra": "7596540001544",
          "nombre": "Cafe Arauca 50g",
          "categoria": "Víveres",
          "precio_venta": "0.84",
          "stock_actual": "120.00"
        }
      ]
    """.trimIndent()

    val base = IsaacPosJsonParser.parseProductsArray(baseJson)
    assertEquals(1, base.size)
    assertEquals("1772071374412-ghbyfjbco", base[0].productoId)
    assertEquals("Cafe Arauca 50g", base[0].nombre)
    assertEquals(120.0, base[0].stockActual, 0.001)
    assertEquals("", base[0].proveedorNombreRemoto)
  }

  @Test
  fun matchesByBarcodeCreatesRemoteProvidersAndCountsLowStock() {
    val remoteJson = """
      [
        {
          "id": "pos-amanecer-100",
          "codigo_barra": "7595461001206",
          "nombre": "CAFE AMANECER 100G",
          "categoria": "Víveres",
          "precio_venta": "1.90",
          "stock_actual": "2.00"
        },
        {
          "producto_id": "pos-polar-harina",
          "codigo_barra": "7591000001111",
          "producto_nombre": "Harina PAN 1kg",
          "categoria": "Víveres",
          "precio_venta": "1.20",
          "stock_actual": "4.00",
          "proveedor_nombre": "Alimentos Polar",
          "proveedor_telefono": "+584141112233",
          "unidad_pedido": "bulto x 20 u",
          "costo_unitario": "19.50"
        },
        {
          "id": "pos-sin-prov",
          "codigo_barra": "",
          "nombre": "Chupetas PIN POP",
          "categoria": "Golosinas",
          "precio_venta": "0.29",
          "stock_actual": "106.00"
        }
      ]
    """.trimIndent()

    val remotes = IsaacPosJsonParser.parseProductsArray(remoteJson)
    val localProviders = listOf(Proveedor(id = 10L, nombre = "Grupo Amanecer"))
    val localProducts = listOf(
      Producto(
        id = 101L,
        proveedorId = 10L,
        codigo = "7595461001206",
        nombre = "Café Amanecer 100g",
        unidad = "bulto x 50 u (5kg)",
      )
    )

    val plan = IsaacPosSyncMatcher.computeSyncPlan(remotes, localProviders, localProducts)

    // 1 vinculado automáticamente por código de barras con Grupo Amanecer (stock=2)
    assertEquals(1, plan.localProductUpdates.size)
    assertEquals(101L, plan.localProductUpdates[0].localProductId)
    assertEquals(2.0, plan.localProductUpdates[0].stockPos, 0.001)
    assertEquals(1, plan.summary.vinculadosPorCodigoBarras)

    // 1 proveedor nuevo desde Supabase ("Alimentos Polar") + 1 producto insertado
    assertEquals(1, plan.providersToCreate.size)
    assertEquals("Alimentos Polar", plan.providersToCreate[0].nombre)
    assertEquals(1, plan.productsToInsertByProviderName.size)

    // 1 sin proveedor asignado ("Chupetas PIN POP") y 2 en bajo stock (<= 5)
    assertEquals(1, plan.summary.sinProveedorAsignado)
    assertEquals(2, plan.summary.enBajoStock)
  }

  @Test
  fun updatesGenericUnitAndDeduplicatesLowStockCount() {
    val remoteJson = """
      [
        {
          "producto_id": "pos-pega-loca",
          "codigo_barra": "7591664000046",
          "producto_nombre": "Pega Loca",
          "categoria": "Víveres",
          "precio_venta": "0.45",
          "stock_actual": "76.00",
          "proveedor_nombre": "Distribuidora SOL DE UCHIRE C.A",
          "unidad_pedido": "Caja x 42",
          "costo_unitario": "10.90",
          "stock_minimo": "1.00"
        },
        {
          "producto_id": "pos-dup-low",
          "codigo_barra": "",
          "producto_nombre": "Producto Dos Proveedores",
          "categoria": "General",
          "precio_venta": "1.00",
          "stock_actual": "2.00",
          "proveedor_nombre": "Proveedor Uno",
          "unidad_pedido": "u",
          "stock_minimo": "5.00"
        },
        {
          "producto_id": "pos-dup-low",
          "codigo_barra": "",
          "producto_nombre": "Producto Dos Proveedores",
          "categoria": "General",
          "precio_venta": "1.00",
          "stock_actual": "2.00",
          "proveedor_nombre": "Proveedor Dos",
          "unidad_pedido": "u",
          "stock_minimo": "5.00"
        }
      ]
    """.trimIndent()

    val remotes = IsaacPosJsonParser.parseProductsArray(remoteJson)
    val localProviders = listOf(Proveedor(id = 1L, nombre = "Distribuidora SOL DE UCHIRE C.A"))
    val localProducts = listOf(
      Producto(
        id = 10L,
        proveedorId = 1L,
        codigo = "7591664000046",
        nombre = "Pega Loca",
        unidad = "u",
        precio = null,
      )
    )

    val plan = IsaacPosSyncMatcher.computeSyncPlan(remotes, localProviders, localProducts)
    assertEquals(1, plan.localProductUpdates.size)
    assertEquals("Caja x 42", plan.localProductUpdates[0].newUnitIfGeneric)
    assertEquals(10.90, plan.localProductUpdates[0].newCostIfMissing!!, 0.001)
    assertEquals(76.0, plan.localProductUpdates[0].stockPos, 0.001)
    assertEquals(1, plan.summary.enBajoStock)
    assertEquals(2, plan.providersToCreate.size)
  }
}
