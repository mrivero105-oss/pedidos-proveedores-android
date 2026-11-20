package com.pedidos.proveedores.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceParserTest {

  @Test
  fun parsesLatinAndUsFormats() {
    assertEquals(1250.50, PriceParser.parse("$1.250,50")!!, 0.001)
    assertEquals(1250.50, PriceParser.parse("USD 1,250.50")!!, 0.001)
    assertEquals(18500.0, PriceParser.parse("$ 18.500")!!, 0.001)
    assertEquals(12.50, PriceParser.parse("12,50")!!, 0.001)
    assertEquals(45.90, PriceParser.parse("45.90 ARS")!!, 0.001)
    assertEquals(35.90, PriceParser.parse("S/ 35.90")!!, 0.001)
    assertEquals(0.75, PriceParser.parse("0.75")!!, 0.001)
  }

  @Test
  fun rejectsInvalidValues() {
    assertNull(PriceParser.parse(""))
    assertNull(PriceParser.parse("Sin precio"))
    assertNull(PriceParser.parse("ABC-123"))
  }

  @Test
  fun formatsQuantitiesAndCurrencyCleanly() {
    assertEquals("5", PriceParser.formatQuantity(5.0))
    assertEquals("2,5", PriceParser.formatQuantity(2.5))
    assertEquals("$18.500,00", PriceParser.formatCurrency(18500.0))
  }
}
