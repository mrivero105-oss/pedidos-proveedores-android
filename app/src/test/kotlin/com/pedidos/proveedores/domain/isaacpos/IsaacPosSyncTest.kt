package com.pedidos.proveedores.domain.isaacpos

import com.pedidos.proveedores.domain.model.Producto
import com.pedidos.proveedores.domain.model.Proveedor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IsaacPosSyncTest {

  @Test
  fun parsesActiveProvidersAndStripsProductCategoriesFromIsaacPos() {
    val providersJson = """
      [
        {"nombre": "Aurora", "telefono_whatsapp": "", "notas": "", "activo": true},
        {"nombre": "Distribuidora SERVICPAN", "telefono_whatsapp": "04121141566", "notas": "Andreina", "activo": true},
        {"nombre": "Proveedor Inactivo", "telefono_whatsapp": "", "notas": "", "activo": false}
      ]
    """.trimIndent()

    val providers = IsaacPosJsonParser.parseProvidersArray(providersJson)
    assertEquals(2, providers.size)
    assertEquals("Aurora", providers[0].nombre)
    assertEquals("04121141566", providers[1].telefonoWhatsApp)

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
    assertEquals("", enriched[0].categoria) // No carga categorías desde Isaac POS
    assertEquals("Grupo Amanecer", enriched[0].proveedorNombreRemoto)
    assertEquals(3.0, enriched[0].stockActual, 0.001)
    assertEquals(62.50, enriched[0].costoUnitario!!, 0.001)
  }

  @Test
  fun syncsOnlyProvidersAndProviderProductsIgnoringUnassignedCategoryItems() {
    val remoteJson = """
      [
        {
          "producto_id": "pos-amanecer-100",
          "codigo_barra": "7595461001206",
          "producto_nombre": "CAFE AMANECER 100G",
          "categoria": "Víveres",
          "precio_venta": "1.90",
          "stock_actual": "2.00",
          "proveedor_nombre": "Grupo Amanecer"
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
          "producto_id": "pos-sin-prov",
          "codigo_barra": "",
          "producto_nombre": "Chupetas PIN POP",
          "categoria": "Golosinas",
          "precio_venta": "0.29",
          "stock_actual": "106.00",
          "proveedor_nombre": null
        }
      ]
    """.trimIndent()

    val remotes = IsaacPosJsonParser.parseProductsArray(remoteJson)
    val remoteProviders = listOf(
      RemoteProviderSeed(nombre = "Aurora", telefonoWhatsApp = ""),
      RemoteProviderSeed(nombre = "Grupo Amanecer", telefonoWhatsApp = "04129998877"),
    )
    val localProviders = listOf(Proveedor(id = 10L, nombre = "Grupo Amanecer", telefonoWhatsApp = ""))
    val localProducts = listOf(
      Producto(
        id = 101L,
        proveedorId = 10L,
        codigo = "7595461001206",
        nombre = "Café Amanecer 100g",
        unidad = "bulto x 50 u (5kg)",
      )
    )

    val plan = IsaacPosSyncMatcher.computeSyncPlan(
      remoteProducts = remotes,
      localProveedores = localProviders,
      localProductos = localProducts,
      remoteProviders = remoteProviders,
    )

    // 1 vinculado por código de barras con Grupo Amanecer (stock=2)
    assertEquals(1, plan.localProductUpdates.size)
    assertEquals(101L, plan.localProductUpdates[0].localProductId)
    assertEquals(2.0, plan.localProductUpdates[0].stockPos, 0.001)
    assertEquals(1, plan.summary.vinculadosPorCodigoBarras)

    // Completa el teléfono de Grupo Amanecer que estaba vacío
    assertEquals(1, plan.providersToUpdatePhone.size)
    assertEquals("04129998877", plan.providersToUpdatePhone[0].telefonoWhatsApp)

    // Crea Aurora (0 productos en POS) + Alimentos Polar (1 producto)
    assertEquals(2, plan.providersToCreate.size)
    assertTrue(plan.providersToCreate.any { it.nombre == "Aurora" })
    assertTrue(plan.providersToCreate.any { it.nombre == "Alimentos Polar" })
    assertEquals(1, plan.productsToInsertByProviderName.size)

    // Ignora "Chupetas PIN POP" (sin proveedor) -> solo 2 productos descargados
    assertEquals(2, plan.summary.totalDescargados)
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
      )
    )

    val plan = IsaacPosSyncMatcher.computeSyncPlan(remotes, localProviders, localProducts)
    assertEquals(1, plan.localProductUpdates.size)
    assertEquals("Caja x 42", plan.localProductUpdates[0].newUnitIfGeneric)
    assertEquals(1, plan.summary.enBajoStock) // deduplicado aunque aparezca en 2 proveedores
  }
}
