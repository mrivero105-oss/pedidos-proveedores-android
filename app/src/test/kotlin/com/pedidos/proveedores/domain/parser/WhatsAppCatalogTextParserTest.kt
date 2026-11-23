package com.pedidos.proveedores.domain.parser

import com.pedidos.proveedores.domain.model.ModoIvaCatalogo
import com.pedidos.proveedores.domain.model.MonedaCatalogo
import com.pedidos.proveedores.domain.model.Proveedor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppCatalogTextParserTest {

  @Test
  fun parsesWhatsAppPriceListInUsdIgnoringGreetingsAndBankInfo() {
    val raw = """
      Buenos días estimado cliente!
      Lista de precios actualizada SERVICPAN (Precios + IVA)
      22/11/2026

      🔥 *HARINA PAN 1KG BULTO X 20* ... $18.50
      ✅ Refresco Pepsi 1L Caja x 6: 5,20$
      • 7591664000046 Pega Loca Caja x 42 - USD 10.90
      $1.32 - Pasta Corta Primor 500g

      Pago Móvil Banesco
      0134-0001-02-0001234567
      Teléfono: 0412-1141566
      RIF: J-12345678-9
      Gracias por su compra!
    """.trimIndent()

    val res = WhatsAppCatalogTextParser.parse(raw)
    assertEquals(MonedaCatalogo.USD, res.monedaSugerida)
    assertEquals(ModoIvaCatalogo.SOLO_GRAVADOS, res.modoIvaSugerido)
    assertEquals(4, res.productos.size)

    val harina = res.productos[0]
    assertTrue(harina.nombre.contains("HARINA PAN", ignoreCase = true))
    assertEquals("BULTO X 20", harina.unidad)
    assertEquals(18.50, harina.precio!!, 0.001)

    val pepsi = res.productos[1]
    assertEquals("Caja x 6", pepsi.unidad)
    assertEquals(5.20, pepsi.precio!!, 0.001)

    val pega = res.productos[2]
    assertEquals("7591664000046", pega.codigo)
    assertEquals("Caja x 42", pega.unidad)
    assertEquals(10.90, pega.precio!!, 0.001)

    val pasta = res.productos[3]
    assertEquals("Pasta Corta Primor 500g", pasta.nombre)
    assertEquals(1.32, pasta.precio!!, 0.001)
  }

  @Test
  fun detectsVesCurrencyFromBsWhatsAppList() {
    val raw = """
      Lista de precios en Bolívares (IVA incluido)
      Arroz Mary 1kg x 24 - Bs. 22.500,00
      Azúcar Montalbán 1kg: Bs 1.150,50
    """.trimIndent()

    val res = WhatsAppCatalogTextParser.parse(raw)
    assertEquals(MonedaCatalogo.VES, res.monedaSugerida)
    assertEquals(ModoIvaCatalogo.INCLUIDO, res.modoIvaSugerido)
    assertEquals(2, res.productos.size)
    assertEquals(22500.0, res.productos[0].precio!!, 0.01)
    assertEquals(1150.50, res.productos[1].precio!!, 0.01)
  }

  @Test
  fun suggestsMatchingProviderFromFileNameOrMessageHint() {
    val proveedores = listOf(
      Proveedor(id = 1L, nombre = "Distribuidora SERVICPAN"),
      Proveedor(id = 2L, nombre = "COMERCIAL TINITO BARCELONA"),
      Proveedor(id = 3L, nombre = "DRINK and FOOD"),
    )

    assertEquals(
      1L,
      WhatsAppCatalogTextParser.suggestProveedorId("Lista_Precios_Servicpan_Nov.pdf", proveedores),
    )
    assertEquals(
      2L,
      WhatsAppCatalogTextParser.suggestProveedorId("Buenos días, lista actualizada de Tinito hoy", proveedores),
    )
    assertNull(
      WhatsAppCatalogTextParser.suggestProveedorId("catalogo_general_2026.pdf", proveedores),
    )
  }
}
