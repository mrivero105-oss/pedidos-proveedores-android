package com.pedidos.proveedores.domain.model

enum class CatalogoFormato(val etiqueta: String, val extension: String) {
  PDF("PDF", "pdf"),
  XLSX("Excel", "xlsx"),
  CSV("CSV", "csv"),
  IMAGEN("Imágenes", "jpg"),
}

enum class PedidoEstado(val etiqueta: String) {
  BORRADOR("Borrador"),
  ENVIADO("Enviado"),
  RECIBIDO("Recibido");

  companion object {
    fun fromDb(value: String): PedidoEstado =
      entries.firstOrNull { it.name == value } ?: BORRADOR
  }
}

data class Proveedor(
  val id: Long = 0L,
  val nombre: String,
  val telefonoWhatsApp: String = "",
  val notas: String = "",
  val creadoEn: Long = System.currentTimeMillis(),
)

data class ProveedorResumen(
  val proveedor: Proveedor,
  val cantidadProductos: Int,
  val cantidadCatalogos: Int,
  val ultimoCatalogoEn: Long?,
  val productosBajoStock: Int = 0,
)

data class CatalogoArchivo(
  val id: Long = 0L,
  val proveedorId: Long,
  val nombreArchivo: String,
  val formato: CatalogoFormato,
  val rutaLocal: String,
  val productosImportados: Int,
  val importadoEn: Long = System.currentTimeMillis(),
)

data class Producto(
  val id: Long = 0L,
  val proveedorId: Long,
  val catalogoId: Long? = null,
  val codigo: String = "",
  val nombre: String,
  val unidad: String = "u",
  val precio: Double? = null,
  val categoria: String = "",
  val imagenRuta: String? = null,
  val paginaPdf: Int? = null,
  val stockPos: Double? = null,
  val precioVentaPos: Double? = null,
  val isaacPosId: String? = null,
)

data class IsaacPosProducto(
  val productoId: String,
  val sucursalId: String = "principal",
  val codigoBarra: String = "",
  val nombre: String,
  val categoria: String = "",
  val precioVenta: Double? = null,
  val stockActual: Double = 0.0,
  val proveedorNombreRemoto: String = "",
  val proveedorTelefonoRemoto: String = "",
  val unidadPedido: String = "u",
  val costoUnitario: Double? = null,
  val stockMinimo: Double = 5.0,
  val sincronizadoEn: Long = System.currentTimeMillis(),
) {
  val esBajoStock: Boolean
    get() = stockActual <= stockMinimo
}

data class ProductoImportado(
  val tempId: Int,
  val codigo: String = "",
  val nombre: String,
  val unidad: String = "u",
  val precio: Double? = null,
  val categoria: String = "",
  val imagenRuta: String? = null,
  val paginaPdf: Int? = null,
  val seleccionado: Boolean = true,
)

data class ResultadoImportacion(
  val formato: CatalogoFormato,
  val nombreArchivo: String,
  val rutaLocal: String,
  val rutasImagenesFuente: List<String> = emptyList(),
  val productos: List<ProductoImportado>,
  val advertencia: String? = null,
  val metodoExtraccion: String = "Local (offline)",
)

data class PedidoItem(
  val id: Long = 0L,
  val pedidoId: Long = 0L,
  val productoId: Long? = null,
  val codigo: String = "",
  val nombre: String,
  val unidad: String = "u",
  val precioUnitario: Double? = null,
  val cantidad: Double,
) {
  val subtotal: Double?
    get() = precioUnitario?.let { it * cantidad }
}

data class Pedido(
  val id: Long = 0L,
  val proveedorId: Long,
  val proveedorNombre: String,
  val proveedorTelefono: String = "",
  val estado: PedidoEstado = PedidoEstado.BORRADOR,
  val notas: String = "",
  val incluirPrecios: Boolean = false,
  val creadoEn: Long = System.currentTimeMillis(),
  val enviadoEn: Long? = null,
  val items: List<PedidoItem> = emptyList(),
) {
  val totalCantidad: Double
    get() = items.sumOf { it.cantidad }

  val totalEstimado: Double?
    get() {
      val conPrecio = items.mapNotNull { it.subtotal }
      return if (conPrecio.isEmpty()) null else conPrecio.sum()
    }
}
