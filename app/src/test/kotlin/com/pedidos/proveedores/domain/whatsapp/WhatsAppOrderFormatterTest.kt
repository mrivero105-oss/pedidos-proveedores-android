package com.pedidos.proveedores.domain.whatsapp

import com.pedidos.proveedores.domain.model.Pedido
import com.pedidos.proveedores.domain.model.PedidoEstado
import com.pedidos.proveedores.domain.model.PedidoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppOrderFormatterTest {

  private val samplePedido = Pedido(
    id = 1L,
    proveedorId = 10L,
    proveedorNombre = "Distribuidora Central",
    proveedorTelefono = "+54 9 11 5555-1234",
    estado = PedidoEstado.ENVIADO,
    notas = "Entregar antes de las 11hs",
    incluirPrecios = true,
    creadoEn = 1763560000000L,
    items = listOf(
      PedidoItem(
        codigo = "HAR-001",
        nombre = "Harina 000",
        unidad = "bolsa",
        precioUnitario = 18500.0,
        cantidad = 2.0,
      ),
      PedidoItem(
        codigo = "",
        nombre = "Queso Cremoso",
        unidad = "kg",
        precioUnitario = 6400.0,
        cantidad = 2.5,
      ),
    ),
  )

  @Test
  fun formatsOrderWithPricesAndNotes() {
    val text = WhatsAppOrderFormatter.format(samplePedido, incluirPrecios = true)
    assertTrue(text.contains("*PEDIDO A PROVEEDOR*"))
    assertTrue(text.contains("*Proveedor:* Distribuidora Central"))
    assertTrue(text.contains("1. *Harina 000* [HAR-001]"))
    assertTrue(text.contains("Cant.: 2 bolsa × $18.500,00 = $37.000,00"))
    assertTrue(text.contains("Cant.: 2,5 kg × $6.400,00 = $16.000,00"))
    assertTrue(text.contains("*Total estimado:* $53.000,00"))
    assertTrue(text.contains("*Notas:* Entregar antes de las 11hs"))
  }

  @Test
  fun omitsPricesWhenDisabled() {
    val text = WhatsAppOrderFormatter.format(samplePedido, incluirPrecios = false)
    assertTrue(text.contains("Cant.: 2 bolsa"))
    assertFalse(text.contains("$18.500,00"))
    assertFalse(text.contains("*Total estimado:*"))
  }

  @Test
  fun sanitizesWhatsAppPhoneNumber() {
    assertEquals("5491155551234", WhatsAppOrderFormatter.sanitizePhone("+54 9 11 5555-1234"))
    assertEquals("584248206369", WhatsAppOrderFormatter.sanitizePhone("04248206369"))
    assertEquals("584121141566", WhatsAppOrderFormatter.sanitizePhone("0412-114.15.66"))
    assertEquals("584141234567", WhatsAppOrderFormatter.sanitizePhone("4141234567"))
    assertEquals("584125577625", WhatsAppOrderFormatter.sanitizePhone("+58 412 557-7625"))
  }

  @Test
  fun defaultPedidoOmitsPricesByDefault() {
    val defaultOrder = Pedido(
      proveedorId = 1L,
      proveedorNombre = "Distribuidora SERVICPAN",
      proveedorTelefono = "04121141566",
      items = listOf(
        PedidoItem(nombre = "Cerveza tercio botella", unidad = "Bulto x 24", precioUnitario = 20.53, cantidad = 2.0)
      ),
    )
    assertFalse(defaultOrder.incluirPrecios)
    val text = WhatsAppOrderFormatter.format(defaultOrder)
    assertTrue(text.contains("Cant.: 2 Bulto x 24"))
    assertFalse(text.contains("20,53"))
    assertFalse(text.contains("*Total estimado:*"))
  }
}
