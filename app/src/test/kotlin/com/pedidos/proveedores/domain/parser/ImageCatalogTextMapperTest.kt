package com.pedidos.proveedores.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCatalogTextMapperTest {

  @Test
  fun extractsMultipleProductsFromPriceListImageOcr() {
    val page = ImageOcrPage(
      index1Based = 1,
      displayName = "lista_whatsapp.jpg",
      width = 1080f,
      height = 1920f,
      spans = emptyList(),
      plainText = """
        LISTA DE PRECIOS MAYORISTA
        HAR-01   Harina Pan 1kg   bulto   $22.50
        AZU-02   Azúcar Montalbán 1kg   bulto   $19.80
      """.trimIndent(),
      thumbPath = "/tmp/thumb_1.jpg",
    )

    val items = ImageCatalogTextMapper.mapOcrPages(listOf(page))
    assertEquals(2, items.size)
    assertEquals("HAR-01", items[0].codigo)
    assertEquals("Harina Pan 1kg", items[0].nombre)
    assertEquals(22.50, items[0].precio!!, 0.001)
    assertEquals("/tmp/thumb_1.jpg", items[0].imagenRuta)
    assertEquals(1, items[0].paginaPdf)
  }

  @Test
  fun createsDraftProductWithThumbnailForSingleProductPhotoWithoutPriceTable() {
    val page = ImageOcrPage(
      index1Based = 2,
      displayName = "mayonesa_mavesa_445g.jpg",
      width = 1000f,
      height = 1000f,
      spans = emptyList(),
      plainText = "Mayonesa Mavesa 445g\n7591002003456",
      thumbPath = "/tmp/thumb_2.jpg",
    )

    val items = ImageCatalogTextMapper.mapOcrPages(listOf(page))
    assertEquals(1, items.size)
    assertTrue(items[0].nombre.contains("Mayonesa Mavesa"))
    assertEquals("7591002003456", items[0].codigo)
    assertEquals("/tmp/thumb_2.jpg", items[0].imagenRuta)
    assertEquals(2, items[0].paginaPdf)
  }

  @Test
  fun fallsBackToCleanFilenameWhenPhotoHasNoReadableText() {
    val page = ImageOcrPage(
      index1Based = 1,
      displayName = "IMG_20261119_120000.jpg",
      width = 800f,
      height = 800f,
      spans = emptyList(),
      plainText = "",
      thumbPath = "/tmp/thumb_empty.jpg",
    )

    val items = ImageCatalogTextMapper.mapOcrPages(listOf(page))
    assertEquals(1, items.size)
    assertEquals("Producto de imagen 1", items[0].nombre)
    assertEquals("/tmp/thumb_empty.jpg", items[0].imagenRuta)
  }
}
