package com.pedidos.proveedores.domain.parser

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfProductSheetParserAmanecerTest {

  private val fixtureJson = Json { ignoreUnknownKeys = true }

  @Serializable
  private data class SpanFixture(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val size: Float,
    val text: String,
  )

  @Serializable
  private data class PageFixture(
    val page: Int,
    val width: Float,
    val height: Float,
    val spans: List<SpanFixture>,
  )

  @Test
  fun extractsAll24ProductsFromRealAmanecerCatalogWithExactBarcodesAndBulkUnits() {
    val stream = javaClass.classLoader!!.getResourceAsStream("amanecer_spans.json")
    assertNotNull("Fixture amanecer_spans.json debe existir", stream)
    val rawJson = stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }

    val fixtures = fixtureJson.decodeFromString<List<PageFixture>>(rawJson)
    assertEquals(18, fixtures.size)

    val pages = fixtures.map { pf ->
      PdfPageLayout(
        pageNumber = pf.page,
        width = pf.width,
        height = pf.height,
        spans = pf.spans.map { s ->
          PdfSpan(x = s.x, y = s.y, width = s.w, height = s.h, fontSize = s.size, text = s.text)
        },
      )
    }

    val extracted = PdfProductSheetParser.extractFromPages(pages)

    extracted.forEachIndexed { idx, p ->
      println("${idx + 1}. [P${p.paginaPdf}] ${p.nombre} | ${p.unidad} | cod=${p.codigo} | cat=${p.categoria}")
    }

    // Portada e introducción (páginas 1, 2 y 3) NO deben producir productos
    assertTrue(extracted.none { (it.paginaPdf ?: 0) <= 3 })

    // Deben salir exactamente las 24 presentaciones reales del catálogo Grupo Amanecer
    assertEquals("Debe extraer los 24 productos exactos de Amanecer", 24, extracted.size)

    // Ningún nombre debe conservar erratas OCR típicas ("20og", "50og", "9oog", "17og", "40og", "250og", "Intensal")
    for (p in extracted) {
      assertFalse("Errata OCR en nombre: ${p.nombre}", Regex("""\d[oO]g""", RegexOption.IGNORE_CASE).containsMatchIn(p.nombre))
      assertFalse("Errata Intensal en nombre: ${p.nombre}", p.nombre.contains("Intensal"))
      assertTrue("Debe tener código de barras: ${p.nombre}", p.codigo.length >= 11)
      assertTrue("Debe especificar bulto con unidades: ${p.nombre} -> ${p.unidad}", p.unidad.startsWith("bulto x "))
    }

    val byBarcode = extracted.associateBy { it.codigo }

    // Verificaciones puntuales en cada familia de productos del catálogo
    val cafe100 = byBarcode["7595461001206"]
    assertNotNull(cafe100)
    assertEquals("Café Amanecer 100g", cafe100!!.nombre)
    assertEquals("bulto x 50 u (5kg)", cafe100.unidad)
    assertEquals("Café", cafe100.categoria)

    val cafe500 = byBarcode["7595461000032"] // Prefijo duplicado "759 7595461000032" normalizado
    assertNotNull("Código con prefijo duplicado debe normalizarse a 7595461000032", cafe500)
    assertEquals("Café Amanecer 500g", cafe500!!.nombre)
    assertEquals("bulto x 10 u (5kg)", cafe500.unidad)

    val cafeGranos = byBarcode["7595461000094"]
    assertNotNull(cafeGranos)
    assertEquals("Café Amanecer en Granos 1kg", cafeGranos!!.nombre)
    assertEquals("bulto x 5 u (5kg)", cafeGranos.unidad)

    val cafeVacio = byBarcode["7595461000063"]
    assertNotNull(cafeVacio)
    assertEquals("Café Amanecer Envasado al vacío 250g", cafeVacio!!.nombre)
    assertEquals("bulto x 12 u (3kg)", cafeVacio.unidad)

    val intensaVelada = byBarcode["7595461000254"]
    assertNotNull(intensaVelada)
    assertEquals("Café Amanecer Lata Intensa Velada 250g", intensaVelada!!.nombre)
    assertEquals("bulto x 16 u (4kg)", intensaVelada.unidad)

    val arrozTrad = byBarcode["7599450000362"]
    assertNotNull(arrozTrad)
    assertEquals("Arroz Tradicional Amanecer 900g", arrozTrad!!.nombre)
    assertEquals("bulto x 24 u (21.6kg)", arrozTrad.unidad)
    assertEquals("Arroz", arrozTrad.categoria)

    val sardinaNatural = byBarcode["7599450000270"]
    assertNotNull(sardinaNatural)
    assertTrue(sardinaNatural!!.nombre.contains("Sardinas Della Nonna"))
    assertTrue(sardinaNatural.nombre.lowercase().contains("natural"))
    assertTrue(sardinaNatural.nombre.contains("170g"))
    assertEquals("bulto x 12 u (2.04kg)", sardinaNatural.unidad)
    assertEquals("Enlatados", sardinaNatural.categoria)

    val sardinaTomate = byBarcode["7599450000263"]
    assertNotNull(sardinaTomate)
    assertTrue(sardinaTomate!!.nombre.lowercase().contains("tomate"))
    assertEquals("bulto x 12 u (2.04kg)", sardinaTomate.unidad)

    val leche400 = byBarcode["7599450000317"]
    assertNotNull(leche400)
    assertEquals("Leche completa en polvo Amanecer 400g", leche400!!.nombre)
    assertEquals("bulto x 24 u (9.6kg)", leche400.unidad)
    assertEquals("Lácteos", leche400.categoria)
  }
}
