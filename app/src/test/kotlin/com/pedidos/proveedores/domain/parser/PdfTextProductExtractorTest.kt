package com.pedidos.proveedores.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfTextProductExtractorTest {

  @Test
  fun extractsProductsAndFiltersHeaderAndNoiseLines() {
    val pdfText = """
      LISTA DE PRECIOS MAYORISTA
      Teléfono: +54 11 4555-0000
      Código    Producto    Unidad    Precio
      ART-001   Harina 000 x 25kg   bolsa   ${'$'}18.500,00
      ART-002   Aceite Girasol 1.5L   caja   14.200,50
      Levadura Fresca 500g  ${'$'}2.450,00
      Página 1 de 4
    """.trimIndent()

    val products = PdfTextProductExtractor.extractFromText(pdfText)
    assertEquals(3, products.size)

    assertEquals("ART-001", products[0].codigo)
    assertEquals("Harina 000 x 25kg", products[0].nombre)
    assertEquals("bolsa", products[0].unidad)
    assertEquals(18500.0, products[0].precio!!, 0.001)

    assertEquals("ART-002", products[1].codigo)
    assertEquals("Aceite Girasol 1.5L", products[1].nombre)
    assertEquals("caja", products[1].unidad)
    assertEquals(14200.50, products[1].precio!!, 0.001)

    assertEquals("", products[2].codigo)
    assertEquals("Levadura Fresca 500g", products[2].nombre)
    assertEquals(2450.0, products[2].precio!!, 0.001)
  }
}
