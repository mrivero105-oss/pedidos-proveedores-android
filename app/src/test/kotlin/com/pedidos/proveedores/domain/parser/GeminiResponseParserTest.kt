package com.pedidos.proveedores.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiResponseParserTest {

  @Test
  fun parsesStructuredJsonWrappedInMarkdownFences() {
    val raw = """
      ```json
      {
        "productos": [
          {
            "codigo": "759 7595461000032",
            "nombre": "Café Amanecer 500g",
            "unidad": "bulto x 10 u (5 kg)",
            "precio": null,
            "categoria": "Café",
            "pagina": 4
          },
          {
            "codigo": "HAR-01",
            "nombre": "Harina Pan 1kg",
            "unidad": "bulto x 20 u",
            "precio": 22.5,
            "categoria": "Harinas",
            "pagina": 5
          },
          {
            "codigo": "7595461000032",
            "nombre": "Café Amanecer 500g",
            "unidad": "bulto x 10 u (5 kg)",
            "precio": null,
            "categoria": "Café",
            "pagina": 4
          }
        ]
      }
      ```
    """.trimIndent()

    val items = GeminiResponseParser.parseProductsJson(raw)
    assertEquals(2, items.size) // Deduplicado automáticamente

    assertEquals("7595461000032", items[0].codigo)
    assertEquals("Café Amanecer 500g", items[0].nombre)
    assertEquals("bulto x 10 u (5 kg)", items[0].unidad)
    assertNull(items[0].precio)
    assertEquals(4, items[0].paginaPdf)

    assertEquals("HAR-01", items[1].codigo)
    assertEquals(22.5, items[1].precio!!, 0.001)
    assertEquals(5, items[1].paginaPdf)
  }
}
