package com.pedidos.proveedores.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

class CsvCatalogParserTest {

  @Test
  fun parsesSemicolonSpanishExcelCsvWithTitleAboveHeader() {
    val csv = """
      LISTA DE PRECIOS MAYORISTA - NOVIEMBRE
      Vigencia: 2026
      Código;Descripción;Presentación;Precio Lista;Rubro
      HAR-001;Harina 000 x 25kg;bolsa;${'$'}18.500,00;Panadería
      ACE-015;"Aceite Girasol; 1,5L";caja;14.200,50;Almacén
      TOTAL;;;;
    """.trimIndent()

    val products = CsvCatalogParser.parseText(csv)
    assertEquals(2, products.size)

    assertEquals("HAR-001", products[0].codigo)
    assertEquals("Harina 000 x 25kg", products[0].nombre)
    assertEquals("bolsa", products[0].unidad)
    assertEquals(18500.0, products[0].precio!!, 0.001)
    assertEquals("Panadería", products[0].categoria)

    assertEquals("ACE-015", products[1].codigo)
    assertEquals("Aceite Girasol; 1,5L", products[1].nombre)
    assertEquals("caja", products[1].unidad)
    assertEquals(14200.50, products[1].precio!!, 0.001)
  }

  @Test
  fun parsesWindows1252EncodedCsvWithAccentsAndNoHeaders() {
    val csv = "101,Azúcar Blanca Refinada,kg,1250.50\n102,Café Molido Torrado,paquete,4890.00"
    val bytes = csv.toByteArray(Charset.forName("windows-1252"))

    val products = CsvCatalogParser.parse(ByteArrayInputStream(bytes))
    assertEquals(2, products.size)
    assertEquals("101", products[0].codigo)
    assertTrue(products[0].nombre.contains("Azúcar"))
    assertEquals(1250.50, products[0].precio!!, 0.001)
    assertTrue(products[1].nombre.contains("Café"))
  }
}
