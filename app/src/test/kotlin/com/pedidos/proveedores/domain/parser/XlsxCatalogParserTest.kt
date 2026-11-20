package com.pedidos.proveedores.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxCatalogParserTest {

  @Test
  fun parsesRealMultiSheetXlsxWorkbook() {
    val xlsxBytes = buildMinimalXlsx()
    val result = XlsxCatalogParser.parse(ByteArrayInputStream(xlsxBytes))

    assertEquals(3, result.productos.size)

    val p1 = result.productos[0]
    assertEquals("BEB-10", p1.codigo)
    assertEquals("Agua Mineral 2L", p1.nombre)
    assertEquals("pack", p1.unidad)
    assertEquals(3500.0, p1.precio!!, 0.001)
    assertEquals("Bebidas", p1.categoria)

    val p2 = result.productos[1]
    assertEquals("", p2.codigo) // celda A vacía saltada correctamente
    assertEquals("Jugo Naranja 1L", p2.nombre)
    assertEquals(1850.5, p2.precio!!, 0.001)

    val p3 = result.productos[2]
    assertEquals("LIM-01", p3.codigo)
    assertEquals("Detergente 5L", p3.nombre)
    assertEquals("Limpieza", p3.categoria)
    assertEquals(6200.0, p3.precio!!, 0.001)
  }

  @Test
  fun detectsLegacyBinaryXlsGracefully() {
    val ole2Bytes = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0x00, 0x00)
    val result = XlsxCatalogParser.parse(ByteArrayInputStream(ole2Bytes))

    assertTrue(result.productos.isEmpty())
    assertNotNull(result.advertencia)
    assertTrue(result.advertencia!!.contains(".xlsx"))
  }

  private fun buildMinimalXlsx(): ByteArray {
    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zos ->
      fun add(name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
      }

      add(
        "xl/workbook.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>
            <sheet name="Bebidas" sheetId="1" r:id="rId1"/>
            <sheet name="Limpieza" sheetId="2" r:id="rId2"/>
          </sheets>
        </workbook>
        """.trimIndent(),
      )

      add(
        "xl/_rels/workbook.xml.rels",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Target="worksheets/sheet2.xml"/>
        </Relationships>
        """.trimIndent(),
      )

      add(
        "xl/sharedStrings.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <si><t>Codigo</t></si>
          <si><t>Producto</t></si>
          <si><t>Unidad</t></si>
          <si><t>Precio</t></si>
          <si><t>BEB-10</t></si>
          <si><t>Agua Mineral 2L</t></si>
          <si><t>pack</t></si>
          <si><t>Jugo Naranja 1L</t></si>
          <si><t>LIM-01</t></si>
          <si><t>Detergente 5L</t></si>
        </sst>
        """.trimIndent(),
      )

      add(
        "xl/worksheets/sheet1.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <sheetData>
            <row r="1">
              <c r="A1" t="s"><v>0</v></c>
              <c r="B1" t="s"><v>1</v></c>
              <c r="C1" t="s"><v>2</v></c>
              <c r="D1" t="s"><v>3</v></c>
            </row>
            <row r="2">
              <c r="A2" t="s"><v>4</v></c>
              <c r="B2" t="s"><v>5</v></c>
              <c r="C2" t="s"><v>6</v></c>
              <c r="D2"><v>3500</v></c>
            </row>
            <row r="3">
              <c r="B3" t="s"><v>7</v></c>
              <c r="D3"><v>1850.5</v></c>
            </row>
          </sheetData>
        </worksheet>
        """.trimIndent(),
      )

      add(
        "xl/worksheets/sheet2.xml",
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <sheetData>
            <row r="1">
              <c r="A1" t="s"><v>0</v></c>
              <c r="B1" t="s"><v>1</v></c>
              <c r="C1" t="s"><v>3</v></c>
            </row>
            <row r="2">
              <c r="A2" t="s"><v>8</v></c>
              <c r="B2" t="s"><v>9</v></c>
              <c r="C2"><v>6200</v></c>
            </row>
          </sheetData>
        </worksheet>
        """.trimIndent(),
      )
    }
    return baos.toByteArray()
  }
}
