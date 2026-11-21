package com.pedidos.proveedores.domain.pricing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BcvRateClientParserTest {

  @Test
  fun parsesPrimaryDolarApiOfficialPayload() {
    val json = """
      {"moneda":"USD","fuente":"oficial","nombre":"Dólar","compra":null,"venta":null,"promedio":918.7662,"fechaActualizacion":"2026-11-20T00:00:00-04:00"}
    """.trimIndent()

    val quote = BcvRateClient.parseDolarApiJson(json)
    assertNotNull(quote)
    assertEquals(918.7662, quote!!.rateBsPerUsd, 0.0001)
    assertEquals("BCV oficial", quote.sourceLabel)
    assertEquals("2026-11-20", quote.dateLabel)
  }

  @Test
  fun parsesFallbackExchangeRateApiPayload() {
    val json = """
      {"result":"success","time_last_update_utc":"Fri, 20 Nov 2026 00:02:32 +0000","base_code":"USD","rates":{"USD":1,"VES":918.7662}}
    """.trimIndent()

    val quote = BcvRateClient.parseExchangeRateApiJson(json)
    assertNotNull(quote)
    assertEquals(918.7662, quote!!.rateBsPerUsd, 0.0001)
    assertEquals("BCV respaldo", quote.sourceLabel)
  }

  @Test
  fun rejectsInvalidOrZeroPayloads() {
    assertNull(BcvRateClient.parseDolarApiJson(""))
    assertNull(BcvRateClient.parseDolarApiJson("""{"promedio":0}"""))
    assertNull(BcvRateClient.parseExchangeRateApiJson("""{"rates":{"EUR":0.91}}"""))
  }
}
