package com.pedidos.proveedores.data.local

import android.content.ContentValues
import android.database.Cursor
import androidx.core.database.getDoubleOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.pedidos.proveedores.domain.isaacpos.IsaacPosSyncMatcher
import com.pedidos.proveedores.domain.isaacpos.IsaacPosSyncSummary
import com.pedidos.proveedores.domain.isaacpos.RemoteProviderSeed
import com.pedidos.proveedores.domain.pricing.PricingIntelligenceEngine
import com.pedidos.proveedores.domain.reconcile.CatalogReconciliationLink
import com.pedidos.proveedores.domain.model.CatalogImportSaveResult
import com.pedidos.proveedores.domain.model.CatalogoArchivo
import com.pedidos.proveedores.domain.model.CatalogoFormato
import com.pedidos.proveedores.domain.model.HistorialPrecio
import com.pedidos.proveedores.domain.model.IsaacPosProducto
import com.pedidos.proveedores.domain.model.ModoImportacionCatalogo
import com.pedidos.proveedores.domain.model.ModoIvaCatalogo
import com.pedidos.proveedores.domain.model.MonedaCatalogo
import com.pedidos.proveedores.domain.model.Pedido
import com.pedidos.proveedores.domain.model.PedidoEstado
import com.pedidos.proveedores.domain.model.PedidoItem
import com.pedidos.proveedores.domain.model.Producto
import com.pedidos.proveedores.domain.model.ProductoImportado
import com.pedidos.proveedores.domain.model.Proveedor
import com.pedidos.proveedores.domain.model.ProveedorResumen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

interface PedidosRepository {
  val proveedoresResumen: StateFlow<List<ProveedorResumen>>
  val productos: StateFlow<List<Producto>>
  val catalogos: StateFlow<List<CatalogoArchivo>>
  val pedidos: StateFlow<List<Pedido>>
  val isaacPosProductos: StateFlow<List<IsaacPosProducto>>
  val historialPrecios: StateFlow<List<HistorialPrecio>>

  suspend fun refreshAll()
  suspend fun saveProveedor(proveedor: Proveedor): Long
  suspend fun deleteProveedor(proveedorId: Long)

  suspend fun saveProducto(
    producto: Producto,
    pricingSettings: PricingSettings = PricingSettings(),
  ): Long
  suspend fun updateProductoPricingOverrides(
    productoId: Long,
    unidadesPorEmpaque: Double?,
    esGravado: Boolean?,
  )
  suspend fun deleteProducto(productoId: Long)

  suspend fun saveImportedCatalog(
    proveedorId: Long,
    nombreArchivo: String,
    formato: CatalogoFormato,
    rutaLocal: String,
    productosImportados: List<ProductoImportado>,
    modoImportacion: ModoImportacionCatalogo = ModoImportacionCatalogo.ACTUALIZAR_Y_AGREGAR,
    moneda: MonedaCatalogo = MonedaCatalogo.USD,
    tasaFijaBs: Double? = null,
    modoIva: ModoIvaCatalogo = ModoIvaCatalogo.INCLUIDO,
    porcentajeIva: Double = 16.0,
    pricingSettings: PricingSettings = PricingSettings(),
  ): CatalogImportSaveResult

  suspend fun updateCatalogPricingConfig(
    catalogoId: Long,
    moneda: MonedaCatalogo,
    tasaFijaBs: Double?,
    modoIva: ModoIvaCatalogo,
    porcentajeIva: Double,
  )

  suspend fun deleteCatalogo(catalogoId: Long, eliminarProductosAsociados: Boolean)

  suspend fun syncIsaacPosProducts(
    remoteProducts: List<IsaacPosProducto>,
    remoteProviders: List<RemoteProviderSeed> = emptyList(),
  ): IsaacPosSyncSummary
  suspend fun assignIsaacPosProductsToProveedor(
    proveedorId: Long,
    posProducts: List<IsaacPosProducto>,
  ): Int

  suspend fun reconcileCatalogLinks(
    proveedorId: Long,
    links: List<CatalogReconciliationLink>,
  ): Int

  suspend fun unlinkCatalogProductFromPos(localProductId: Long)

  suspend fun savePedido(pedido: Pedido): Long
  suspend fun saveMultiplePedidos(pedidos: List<Pedido>): List<Long>
  suspend fun updatePedidoEstado(pedidoId: Long, estado: PedidoEstado)
  suspend fun deletePedido(pedidoId: Long)
}

class SqlitePedidosRepository(
  private val dbHelper: PedidosDbHelper,
) : PedidosRepository {

  private val _proveedoresResumen = MutableStateFlow<List<ProveedorResumen>>(emptyList())
  override val proveedoresResumen: StateFlow<List<ProveedorResumen>> = _proveedoresResumen.asStateFlow()

  private val _productos = MutableStateFlow<List<Producto>>(emptyList())
  override val productos: StateFlow<List<Producto>> = _productos.asStateFlow()

  private val _catalogos = MutableStateFlow<List<CatalogoArchivo>>(emptyList())
  override val catalogos: StateFlow<List<CatalogoArchivo>> = _catalogos.asStateFlow()

  private val _pedidos = MutableStateFlow<List<Pedido>>(emptyList())
  override val pedidos: StateFlow<List<Pedido>> = _pedidos.asStateFlow()

  private val _isaacPosProductos = MutableStateFlow<List<IsaacPosProducto>>(emptyList())
  override val isaacPosProductos: StateFlow<List<IsaacPosProducto>> = _isaacPosProductos.asStateFlow()

  private val _historialPrecios = MutableStateFlow<List<HistorialPrecio>>(emptyList())
  override val historialPrecios: StateFlow<List<HistorialPrecio>> = _historialPrecios.asStateFlow()

  override suspend fun refreshAll() = withContext(Dispatchers.IO) {
    reloadSync()
  }

  override suspend fun saveProveedor(proveedor: Proveedor): Long = withContext(Dispatchers.IO) {
    val db = dbHelper.writableDatabase
    val values = ContentValues().apply {
      put("nombre", proveedor.nombre.trim())
      put("telefono_whatsapp", proveedor.telefonoWhatsApp.trim())
      put("notas", proveedor.notas.trim())
      if (proveedor.id == 0L) {
        put("creado_en", proveedor.creadoEn)
      }
    }
    val id = if (proveedor.id == 0L) {
      db.insertOrThrow("proveedores", null, values)
    } else {
      db.update("proveedores", values, "id = ?", arrayOf(proveedor.id.toString()))
      val orderValues = ContentValues().apply {
        put("proveedor_nombre", proveedor.nombre.trim())
        put("proveedor_telefono", proveedor.telefonoWhatsApp.trim())
      }
      db.update(
        "pedidos",
        orderValues,
        "proveedor_id = ? AND estado = ?",
        arrayOf(proveedor.id.toString(), PedidoEstado.BORRADOR.name),
      )
      proveedor.id
    }
    reloadSync()
    id
  }

  override suspend fun deleteProveedor(proveedorId: Long) = withContext(Dispatchers.IO) {
    val db = dbHelper.writableDatabase
    val pathsToDelete = mutableListOf<String>()
    db.rawQuery("SELECT ruta_local FROM catalogos WHERE proveedor_id = ?", arrayOf(proveedorId.toString())).use { c ->
      while (c.moveToNext()) pathsToDelete.add(c.getString(0))
    }
    db.rawQuery("SELECT imagen_ruta FROM productos WHERE proveedor_id = ? AND imagen_ruta IS NOT NULL", arrayOf(proveedorId.toString())).use { c ->
      while (c.moveToNext()) pathsToDelete.add(c.getString(0))
    }
    db.delete("proveedores", "id = ?", arrayOf(proveedorId.toString()))
    pathsToDelete.distinct().forEach { path -> runCatching { File(path).delete() } }
    reloadSync()
  }

  override suspend fun saveProducto(
    producto: Producto,
    pricingSettings: PricingSettings,
  ): Long = withContext(Dispatchers.IO) {
    val db = dbHelper.writableDatabase
    val previousPrice = if (producto.id != 0L) {
      db.rawQuery("SELECT precio FROM productos WHERE id = ?", arrayOf(producto.id.toString())).use { c ->
        if (c.moveToFirst()) c.getDoubleOrNull(0) else null
      }
    } else null

    val values = ContentValues().apply {
      put("proveedor_id", producto.proveedorId)
      if (producto.catalogoId != null) put("catalogo_id", producto.catalogoId) else putNull("catalogo_id")
      put("codigo", producto.codigo.trim())
      put("nombre", producto.nombre.trim())
      put("unidad", producto.unidad.trim().ifBlank { "u" })
      if (producto.precio != null) put("precio", producto.precio) else putNull("precio")
      put("categoria", producto.categoria.trim())
      if (producto.imagenRuta != null) put("imagen_ruta", producto.imagenRuta) else putNull("imagen_ruta")
      if (producto.paginaPdf != null) put("pagina_pdf", producto.paginaPdf) else putNull("pagina_pdf")
      if (producto.stockPos != null) put("stock_pos", producto.stockPos) else putNull("stock_pos")
      if (producto.precioVentaPos != null) put("precio_venta_pos", producto.precioVentaPos) else putNull("precio_venta_pos")
      if (producto.isaacPosId != null) put("isaac_pos_id", producto.isaacPosId) else putNull("isaac_pos_id")
      if (producto.unidadesPorEmpaque != null && producto.unidadesPorEmpaque > 0.0) {
        put("unidades_por_empaque", producto.unidadesPorEmpaque)
      } else {
        putNull("unidades_por_empaque")
      }
      when (producto.esGravado) {
        true -> put("es_gravado", 1)
        false -> put("es_gravado", 0)
        null -> putNull("es_gravado")
      }
    }
    val id = if (producto.id == 0L) {
      db.insertOrThrow("productos", null, values)
    } else {
      db.update("productos", values, "id = ?", arrayOf(producto.id.toString()))
      producto.id
    }

    val newPrice = producto.precio
    if (newPrice != null && newPrice > 0.0 && (previousPrice == null || abs(newPrice - previousPrice) >= 0.005)) {
      val savedProd = producto.copy(id = id)
      val cat = savedProd.catalogoId?.let { catId -> loadCatalogos(db).firstOrNull { it.id == catId } }
      val pos = savedProd.isaacPosId?.let { posId -> loadIsaacPosCache(db).firstOrNull { it.productoId == posId } }
      val (unitUsd, tasaUsada) = PricingIntelligenceEngine.computeEffectiveUnitCostUsd(
        rawPrice = newPrice,
        producto = savedProd,
        catalogo = cat,
        posProduct = pos,
        settings = pricingSettings,
      )
      if (unitUsd != null && unitUsd > 0.0) {
        insertPriceHistoryRow(
          db = db,
          proveedorId = savedProd.proveedorId,
          productoId = id,
          isaacPosId = savedProd.isaacPosId,
          catalogoId = savedProd.catalogoId,
          nombreProducto = savedProd.nombre.trim(),
          unidad = savedProd.unidad.trim().ifBlank { "u" },
          precioOriginal = newPrice,
          moneda = cat?.moneda ?: MonedaCatalogo.USD,
          tasaUsadaBs = tasaUsada,
          costoUnitarioUsd = unitUsd,
          registradoEn = System.currentTimeMillis(),
        )
      }
    }

    reloadSync()
    id
  }

  override suspend fun updateProductoPricingOverrides(
    productoId: Long,
    unidadesPorEmpaque: Double?,
    esGravado: Boolean?,
  ) = withContext(Dispatchers.IO) {
    val db = dbHelper.writableDatabase
    val cv = ContentValues().apply {
      if (unidadesPorEmpaque != null && unidadesPorEmpaque > 0.0 && unidadesPorEmpaque.isFinite()) {
        put("unidades_por_empaque", unidadesPorEmpaque)
      } else {
        putNull("unidades_por_empaque")
      }
      when (esGravado) {
        true -> put("es_gravado", 1)
        false -> put("es_gravado", 0)
        null -> putNull("es_gravado")
      }
    }
    db.update("productos", cv, "id = ?", arrayOf(productoId.toString()))
    reloadSync()
  }

  override suspend fun deleteProducto(productoId: Long) = withContext(Dispatchers.IO) {
    dbHelper.writableDatabase.delete("productos", "id = ?", arrayOf(productoId.toString()))
    reloadSync()
  }

  override suspend fun saveImportedCatalog(
    proveedorId: Long,
    nombreArchivo: String,
    formato: CatalogoFormato,
    rutaLocal: String,
    productosImportados: List<ProductoImportado>,
    modoImportacion: ModoImportacionCatalogo,
    moneda: MonedaCatalogo,
    tasaFijaBs: Double?,
    modoIva: ModoIvaCatalogo,
    porcentajeIva: Double,
    pricingSettings: PricingSettings,
  ): CatalogImportSaveResult = withContext(Dispatchers.IO) {
    val db = dbHelper.writableDatabase
    val seleccionados = productosImportados.filter { it.seleccionado && it.nombre.isNotBlank() }
    val posById = loadIsaacPosCache(db).associateBy { it.productoId }
    val existingForProv = loadProductos(db).filter { it.proveedorId == proveedorId }
    val existingByCode = existingForProv
      .filter { it.codigo.isNotBlank() }
      .associateBy { it.codigo.trim().lowercase() }
      .toMutableMap()
    val existingByName = existingForProv
      .associateBy { IsaacPosSyncMatcher.normalizeKey(it.nombre) }
      .toMutableMap()

    val replaceAll = modoImportacion == ModoImportacionCatalogo.REEMPLAZAR_TODO
    val now = System.currentTimeMillis()
    var catalogoId = 0L
    var updatedCount = 0
    var insertedCount = 0
    var unlinkedCount = 0

    db.beginTransaction()
    try {
      if (replaceAll) {
        db.delete("productos", "proveedor_id = ?", arrayOf(proveedorId.toString()))
      }

      val cleanTasaFija = tasaFijaBs?.takeIf { moneda == MonedaCatalogo.VES && it > 0.0 && it.isFinite() }
      val safeIva = porcentajeIva.coerceIn(0.0, 50.0)
      val catValues = ContentValues().apply {
        put("proveedor_id", proveedorId)
        put("nombre_archivo", nombreArchivo)
        put("formato", formato.name)
        put("ruta_local", rutaLocal)
        put("productos_importados", seleccionados.size)
        put("importado_en", now)
        put("moneda", moneda.name)
        if (cleanTasaFija != null) put("tasa_fija_bs", cleanTasaFija) else putNull("tasa_fija_bs")
        put("modo_iva", modoIva.name)
        put("porcentaje_iva", safeIva)
      }
      catalogoId = db.insertOrThrow("catalogos", null, catValues)

      val newCatalogModel = CatalogoArchivo(
        id = catalogoId,
        proveedorId = proveedorId,
        nombreArchivo = nombreArchivo,
        formato = formato,
        rutaLocal = rutaLocal,
        productosImportados = seleccionados.size,
        importadoEn = now,
        moneda = moneda,
        tasaFijaBs = cleanTasaFija,
        modoIva = modoIva,
        porcentajeIva = safeIva,
      )

      for (item in seleccionados) {
        val codeKey = item.codigo.trim().lowercase()
        val nameKey = IsaacPosSyncMatcher.normalizeKey(item.nombre)
        val prevMatch = codeKey.takeIf { it.isNotBlank() }?.let { existingByCode[it] }
          ?: existingByName[nameKey]

        if (!replaceAll && prevMatch != null) {
          // Modo inteligente ACTUALIZAR_Y_AGREGAR: actualiza costo y metadatos sin duplicar ni perder vínculo POS
          val newUnit = item.unidad.trim().ifBlank { "u" }
          val finalUnit = if (newUnit.equals("u", true) && !prevMatch.unidad.equals("u", true)) {
            prevMatch.unidad
          } else {
            newUnit
          }
          val finalCode = if (prevMatch.codigo.isBlank() && item.codigo.isNotBlank()) item.codigo.trim() else prevMatch.codigo
          val finalImage = prevMatch.imagenRuta?.takeIf { it.isNotBlank() } ?: item.imagenRuta
          val finalPrice = item.precio ?: prevMatch.precio

          val updValues = ContentValues().apply {
            put("catalogo_id", catalogoId)
            put("codigo", finalCode)
            put("unidad", finalUnit)
            if (finalPrice != null) put("precio", finalPrice) else putNull("precio")
            if (finalImage != null) put("imagen_ruta", finalImage) else putNull("imagen_ruta")
            if (item.paginaPdf != null) put("pagina_pdf", item.paginaPdf)
          }
          db.update("productos", updValues, "id = ?", arrayOf(prevMatch.id.toString()))
          updatedCount++
          if (prevMatch.isaacPosId.isNullOrBlank()) {
            unlinkedCount++
          }

          val updatedProd = prevMatch.copy(
            catalogoId = catalogoId,
            codigo = finalCode,
            unidad = finalUnit,
            precio = finalPrice,
            imagenRuta = finalImage,
          )
          if (finalCode.isNotBlank()) existingByCode[finalCode.lowercase()] = updatedProd
          existingByName[nameKey] = updatedProd

          val price = item.precio
          if (price != null && price > 0.0) {
            val pos = prevMatch.isaacPosId?.let { posById[it] }
            val (unitUsd, tasaUsada) = PricingIntelligenceEngine.computeEffectiveUnitCostUsd(
              rawPrice = price,
              producto = updatedProd,
              catalogo = newCatalogModel,
              posProduct = pos,
              settings = pricingSettings,
            )
            if (unitUsd != null && unitUsd > 0.0) {
              insertPriceHistoryRow(
                db = db,
                proveedorId = proveedorId,
                productoId = prevMatch.id,
                isaacPosId = prevMatch.isaacPosId,
                catalogoId = catalogoId,
                nombreProducto = updatedProd.nombre,
                unidad = finalUnit,
                precioOriginal = price,
                moneda = moneda,
                tasaUsadaBs = tasaUsada,
                costoUnitarioUsd = unitUsd,
                registradoEn = now,
              )
            }
          }
          continue
        }

        // Inserción nueva (o reemplazo completo preservando vínculo POS y overrides de coincidencias exactas)
        val preservedPosId = if (replaceAll) prevMatch?.isaacPosId else null
        val preservedStock = if (replaceAll) prevMatch?.stockPos else null
        val preservedPvp = if (replaceAll) prevMatch?.precioVentaPos else null
        val preservedPack = if (replaceAll) prevMatch?.unidadesPorEmpaque else null
        val preservedTax = if (replaceAll) prevMatch?.esGravado else null
        val finalUnit = item.unidad.trim().ifBlank { "u" }

        val prodValues = ContentValues().apply {
          put("proveedor_id", proveedorId)
          put("catalogo_id", catalogoId)
          put("codigo", item.codigo.trim())
          put("nombre", item.nombre.trim())
          put("unidad", finalUnit)
          if (item.precio != null) put("precio", item.precio) else putNull("precio")
          put("categoria", item.categoria.trim())
          if (item.imagenRuta != null) put("imagen_ruta", item.imagenRuta) else putNull("imagen_ruta")
          if (item.paginaPdf != null) put("pagina_pdf", item.paginaPdf) else putNull("pagina_pdf")
          if (preservedStock != null) put("stock_pos", preservedStock) else putNull("stock_pos")
          if (preservedPvp != null) put("precio_venta_pos", preservedPvp) else putNull("precio_venta_pos")
          if (preservedPosId != null) put("isaac_pos_id", preservedPosId) else putNull("isaac_pos_id")
          if (preservedPack != null) put("unidades_por_empaque", preservedPack) else putNull("unidades_por_empaque")
          when (preservedTax) {
            true -> put("es_gravado", 1)
            false -> put("es_gravado", 0)
            null -> putNull("es_gravado")
          }
        }
        val newProdId = db.insertOrThrow("productos", null, prodValues)
        insertedCount++
        if (preservedPosId.isNullOrBlank()) {
          unlinkedCount++
        }

        val insertedProd = Producto(
          id = newProdId,
          proveedorId = proveedorId,
          catalogoId = catalogoId,
          codigo = item.codigo.trim(),
          nombre = item.nombre.trim(),
          unidad = finalUnit,
          precio = item.precio,
          categoria = item.categoria.trim(),
          stockPos = preservedStock,
          precioVentaPos = preservedPvp,
          isaacPosId = preservedPosId,
          unidadesPorEmpaque = preservedPack,
          esGravado = preservedTax,
        )
        if (codeKey.isNotBlank()) existingByCode[codeKey] = insertedProd
        existingByName[nameKey] = insertedProd

        val price = item.precio
        if (price != null && price > 0.0) {
          val pos = preservedPosId?.let { posById[it] }
          val (unitUsd, tasaUsada) = PricingIntelligenceEngine.computeEffectiveUnitCostUsd(
            rawPrice = price,
            producto = insertedProd,
            catalogo = newCatalogModel,
            posProduct = pos,
            settings = pricingSettings,
          )
          if (unitUsd != null && unitUsd > 0.0) {
            insertPriceHistoryRow(
              db = db,
              proveedorId = proveedorId,
              productoId = newProdId,
              isaacPosId = preservedPosId,
              catalogoId = catalogoId,
              nombreProducto = item.nombre.trim(),
              unidad = finalUnit,
              precioOriginal = price,
              moneda = moneda,
              tasaUsadaBs = tasaUsada,
              costoUnitarioUsd = unitUsd,
              registradoEn = now,
            )
          }
        }
      }

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }

    reloadSync()
    CatalogImportSaveResult(
      catalogoId = catalogoId,
      updatedCount = updatedCount,
      insertedCount = insertedCount,
      unlinkedCount = unlinkedCount,
    )
  }

  override suspend fun updateCatalogPricingConfig(
    catalogoId: Long,
    moneda: MonedaCatalogo,
    tasaFijaBs: Double?,
    modoIva: ModoIvaCatalogo,
    porcentajeIva: Double,
  ) = withContext(Dispatchers.IO) {
    val db = dbHelper.writableDatabase
    val cleanTasaFija = tasaFijaBs?.takeIf { moneda == MonedaCatalogo.VES && it > 0.0 && it.isFinite() }
    val cv = ContentValues().apply {
      put("moneda", moneda.name)
      if (cleanTasaFija != null) put("tasa_fija_bs", cleanTasaFija) else putNull("tasa_fija_bs")
      put("modo_iva", modoIva.name)
      put("porcentaje_iva", porcentajeIva.coerceIn(0.0, 50.0))
    }
    db.update("catalogos", cv, "id = ?", arrayOf(catalogoId.toString()))
    reloadSync()
  }

  override suspend fun deleteCatalogo(catalogoId: Long, eliminarProductosAsociados: Boolean) =
    withContext(Dispatchers.IO) {
      val db = dbHelper.writableDatabase
      var ruta: String? = null
      db.rawQuery("SELECT ruta_local FROM catalogos WHERE id = ?", arrayOf(catalogoId.toString())).use { c ->
        if (c.moveToFirst()) ruta = c.getString(0)
      }
      db.beginTransaction()
      try {
        if (eliminarProductosAsociados) {
          db.delete("productos", "catalogo_id = ?", arrayOf(catalogoId.toString()))
        }
        db.delete("catalogos", "id = ?", arrayOf(catalogoId.toString()))
        db.setTransactionSuccessful()
      } finally {
        db.endTransaction()
      }
      ruta?.let { runCatching { File(it).delete() } }
      reloadSync()
    }

  override suspend fun syncIsaacPosProducts(
    remoteProducts: List<IsaacPosProducto>,
    remoteProviders: List<RemoteProviderSeed>,
  ): IsaacPosSyncSummary =
    withContext(Dispatchers.IO) {
      val db = dbHelper.writableDatabase
      val existingProviders = loadProveedoresBasic(db)
      val existingProducts = loadProductos(db)
      val providerProducts = remoteProducts.filter { it.proveedorNombreRemoto.isNotBlank() }

      val plan = IsaacPosSyncMatcher.computeSyncPlan(
        remoteProducts = providerProducts,
        localProveedores = existingProviders,
        localProductos = existingProducts,
        remoteProviders = remoteProviders,
      )

      db.beginTransaction()
      try {
        // 1. Reemplazar caché offline de Isaac POS únicamente con productos de proveedores (sin categorías)
        db.delete("isaac_pos_cache", null, null)
        for (pos in providerProducts) {
          val cv = ContentValues().apply {
            put("producto_id", pos.productoId)
            put("sucursal_id", pos.sucursalId)
            put("codigo_barra", pos.codigoBarra)
            put("nombre", pos.nombre)
            put("categoria", "")
            if (pos.precioVenta != null) put("precio_venta", pos.precioVenta) else putNull("precio_venta")
            put("stock_actual", pos.stockActual)
            put("proveedor_nombre_remoto", pos.proveedorNombreRemoto)
            put("proveedor_telefono_remoto", pos.proveedorTelefonoRemoto)
            put("unidad_pedido", pos.unidadPedido)
            if (pos.costoUnitario != null) put("costo_unitario", pos.costoUnitario) else putNull("costo_unitario")
            put("stock_minimo", pos.stockMinimo)
            put("sincronizado_en", pos.sincronizadoEn)
          }
          db.insertWithOnConflict(
            "isaac_pos_cache",
            null,
            cv,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
          )
        }

        // 2. Actualizar stock y vínculo de productos locales emparejados (limpiando categorías previas de POS)
        for (upd in plan.localProductUpdates) {
          val cv = ContentValues().apply {
            put("stock_pos", upd.stockPos)
            if (upd.precioVentaPos != null) put("precio_venta_pos", upd.precioVentaPos)
            put("isaac_pos_id", upd.isaacPosId)
            if (upd.newCostIfMissing != null) put("precio", upd.newCostIfMissing)
            if (upd.newUnitIfGeneric != null) put("unidad", upd.newUnitIfGeneric)
          }
          db.update("productos", cv, "id = ?", arrayOf(upd.localProductId.toString()))
        }
        // Limpiar categorías heredadas de Isaac POS en productos sincronizados sin catálogo local
        val clearCatCv = ContentValues().apply { put("categoria", "") }
        db.update("productos", clearCatCv, "catalogo_id IS NULL AND isaac_pos_id IS NOT NULL", null)

        // 3. Crear todos los proveedores activos de Isaac POS (cloud_proveedores) y completar WhatsApp faltantes
        val provIdByNormName = existingProviders
          .associate { IsaacPosSyncMatcher.normalizeKey(it.nombre) to it.id }
          .toMutableMap()
        for (newProv in plan.providersToCreate) {
          val key = IsaacPosSyncMatcher.normalizeKey(newProv.nombre)
          if (!provIdByNormName.containsKey(key)) {
            val cv = ContentValues().apply {
              put("nombre", newProv.nombre.trim())
              put("telefono_whatsapp", newProv.telefonoWhatsApp.trim())
              put("notas", newProv.notas.trim().ifBlank { "Sincronizado desde Isaac POS" })
              put("creado_en", System.currentTimeMillis())
            }
            val id = db.insertOrThrow("proveedores", null, cv)
            provIdByNormName[key] = id
          }
        }
        for (phoneUpd in plan.providersToUpdatePhone) {
          val cv = ContentValues().apply { put("telefono_whatsapp", phoneUpd.telefonoWhatsApp) }
          db.update("proveedores", cv, "id = ?", arrayOf(phoneUpd.proveedorId.toString()))
        }

        for (insertItem in plan.productsToInsertByProviderName) {
          val provId = provIdByNormName[IsaacPosSyncMatcher.normalizeKey(insertItem.providerName)] ?: continue
          val pos = insertItem.posProduct
          val cv = ContentValues().apply {
            put("proveedor_id", provId)
            putNull("catalogo_id")
            put("codigo", pos.codigoBarra)
            put("nombre", pos.nombre)
            put("unidad", pos.unidadPedido.ifBlank { "u" })
            if (pos.costoUnitario != null) put("precio", pos.costoUnitario) else putNull("precio")
            put("categoria", "")
            put("stock_pos", pos.stockActual)
            if (pos.precioVenta != null) put("precio_venta_pos", pos.precioVenta) else putNull("precio_venta_pos")
            put("isaac_pos_id", pos.productoId)
          }
          db.insertOrThrow("productos", null, cv)
        }

        db.setTransactionSuccessful()
      } finally {
        db.endTransaction()
      }

      reloadSync()
      plan.summary
    }

  override suspend fun assignIsaacPosProductsToProveedor(
    proveedorId: Long,
    posProducts: List<IsaacPosProducto>,
  ): Int = withContext(Dispatchers.IO) {
    if (posProducts.isEmpty()) return@withContext 0
    val db = dbHelper.writableDatabase
    val existingForProv = loadProductos(db).filter { it.proveedorId == proveedorId }
    val existingByPosId = existingForProv.mapNotNull { p -> p.isaacPosId?.let { it to p } }.toMap()
    val existingByCode = existingForProv.filter { it.codigo.isNotBlank() }.associateBy { it.codigo.trim().lowercase() }

    var addedOrUpdated = 0
    db.beginTransaction()
    try {
      for (pos in posProducts) {
        val existing = existingByPosId[pos.productoId]
          ?: pos.codigoBarra.takeIf { it.isNotBlank() }?.let { existingByCode[it.trim().lowercase()] }

        if (existing != null) {
          val cv = ContentValues().apply {
            put("stock_pos", pos.stockActual)
            if (pos.precioVenta != null) put("precio_venta_pos", pos.precioVenta)
            put("isaac_pos_id", pos.productoId)
            if (existing.precio == null && pos.costoUnitario != null) put("precio", pos.costoUnitario)
          }
          db.update("productos", cv, "id = ?", arrayOf(existing.id.toString()))
        } else {
          val cv = ContentValues().apply {
            put("proveedor_id", proveedorId)
            putNull("catalogo_id")
            put("codigo", pos.codigoBarra)
            put("nombre", pos.nombre)
            put("unidad", pos.unidadPedido.ifBlank { "u" })
            if (pos.costoUnitario != null) put("precio", pos.costoUnitario) else putNull("precio")
            put("categoria", "")
            put("stock_pos", pos.stockActual)
            if (pos.precioVenta != null) put("precio_venta_pos", pos.precioVenta) else putNull("precio_venta_pos")
            put("isaac_pos_id", pos.productoId)
          }
          db.insertOrThrow("productos", null, cv)
        }
        addedOrUpdated++
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }

    reloadSync()
    addedOrUpdated
  }

  override suspend fun reconcileCatalogLinks(
    proveedorId: Long,
    links: List<CatalogReconciliationLink>,
  ): Int = withContext(Dispatchers.IO) {
    if (links.isEmpty()) return@withContext 0
    val db = dbHelper.writableDatabase
    val existingById = loadProductos(db)
      .filter { it.proveedorId == proveedorId }
      .associateBy { it.id }
      .toMutableMap()

    var reconciledCount = 0
    db.beginTransaction()
    try {
      for (link in links) {
        val local = existingById[link.localProductId] ?: continue
        val pos = link.posProduct

        // Detectar duplicado previo con el mismo isaac_pos_id en este proveedor para fusionar sin perder historial
        val duplicate = existingById.values.firstOrNull { other ->
          other.id != local.id && other.isaacPosId == pos.productoId
        }

        val cv = ContentValues().apply {
          put("isaac_pos_id", pos.productoId)
          put("stock_pos", pos.stockActual)
          if (pos.precioVenta != null) put("precio_venta_pos", pos.precioVenta)
          if (local.codigo.isBlank() && pos.codigoBarra.isNotBlank()) {
            put("codigo", pos.codigoBarra)
          }
          if (local.precio == null) {
            val fallbackPrice = pos.costoUnitario ?: duplicate?.precio
            if (fallbackPrice != null) put("precio", fallbackPrice)
          }
          val remoteUnit = pos.unidadPedido.trim()
          if (local.unidad.trim().equals("u", ignoreCase = true) &&
            remoteUnit.isNotBlank() &&
            !remoteUnit.equals("u", ignoreCase = true)
          ) {
            put("unidad", remoteUnit)
          }
          val duplicateImage = duplicate?.imagenRuta
          if (local.imagenRuta.isNullOrBlank() && !duplicateImage.isNullOrBlank()) {
            put("imagen_ruta", duplicateImage)
          }
        }
        db.update("productos", cv, "id = ?", arrayOf(local.id.toString()))

        if (duplicate != null) {
          val relinkCv = ContentValues().apply { put("producto_id", local.id) }
          db.update("pedido_items", relinkCv, "producto_id = ?", arrayOf(duplicate.id.toString()))
          db.update("historial_precios", relinkCv, "producto_id = ?", arrayOf(duplicate.id.toString()))
          db.delete("productos", "id = ?", arrayOf(duplicate.id.toString()))
          existingById.remove(duplicate.id)
        }

        // Propagar isaac_pos_id al historial de precios de este producto
        val histPosCv = ContentValues().apply { put("isaac_pos_id", pos.productoId) }
        db.update(
          "historial_precios",
          histPosCv,
          "producto_id = ? AND (isaac_pos_id IS NULL OR isaac_pos_id = '')",
          arrayOf(local.id.toString()),
        )

        existingById[local.id] = local.copy(
          isaacPosId = pos.productoId,
          stockPos = pos.stockActual,
          precioVentaPos = pos.precioVenta,
        )
        reconciledCount++
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }

    reloadSync()
    reconciledCount
  }

  override suspend fun unlinkCatalogProductFromPos(localProductId: Long) = withContext(Dispatchers.IO) {
    val db = dbHelper.writableDatabase
    val cv = ContentValues().apply {
      putNull("isaac_pos_id")
      putNull("stock_pos")
      putNull("precio_venta_pos")
    }
    db.update("productos", cv, "id = ?", arrayOf(localProductId.toString()))
    reloadSync()
  }

  override suspend fun savePedido(pedido: Pedido): Long = withContext(Dispatchers.IO) {
    val db = dbHelper.writableDatabase
    var savedId = pedido.id
    db.beginTransaction()
    try {
      val values = ContentValues().apply {
        put("proveedor_id", pedido.proveedorId)
        put("proveedor_nombre", pedido.proveedorNombre)
        put("proveedor_telefono", pedido.proveedorTelefono)
        put("estado", pedido.estado.name)
        put("notas", pedido.notas.trim())
        put("incluir_precios", if (pedido.incluirPrecios) 1 else 0)
        put("creado_en", pedido.creadoEn)
        if (pedido.enviadoEn != null) put("enviado_en", pedido.enviadoEn) else putNull("enviado_en")
      }
      if (savedId == 0L) {
        savedId = db.insertOrThrow("pedidos", null, values)
      } else {
        db.update("pedidos", values, "id = ?", arrayOf(savedId.toString()))
        db.delete("pedido_items", "pedido_id = ?", arrayOf(savedId.toString()))
      }

      for (item in pedido.items) {
        val itemValues = ContentValues().apply {
          put("pedido_id", savedId)
          if (item.productoId != null) put("producto_id", item.productoId) else putNull("producto_id")
          put("codigo", item.codigo)
          put("nombre", item.nombre)
          put("unidad", item.unidad)
          if (item.precioUnitario != null) put("precio_unitario", item.precioUnitario) else putNull("precio_unitario")
          put("cantidad", item.cantidad)
        }
        db.insertOrThrow("pedido_items", null, itemValues)
      }

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    reloadSync()
    savedId
  }

  override suspend fun saveMultiplePedidos(pedidos: List<Pedido>): List<Long> = withContext(Dispatchers.IO) {
    if (pedidos.isEmpty()) return@withContext emptyList()
    val db = dbHelper.writableDatabase
    val savedIds = mutableListOf<Long>()
    db.beginTransaction()
    try {
      for (pedido in pedidos) {
        if (pedido.items.isEmpty()) continue
        val values = ContentValues().apply {
          put("proveedor_id", pedido.proveedorId)
          put("proveedor_nombre", pedido.proveedorNombre)
          put("proveedor_telefono", pedido.proveedorTelefono)
          put("estado", pedido.estado.name)
          put("notas", pedido.notas.trim())
          put("incluir_precios", if (pedido.incluirPrecios) 1 else 0)
          put("creado_en", pedido.creadoEn)
          if (pedido.enviadoEn != null) put("enviado_en", pedido.enviadoEn) else putNull("enviado_en")
        }
        val newId = db.insertOrThrow("pedidos", null, values)
        for (item in pedido.items) {
          val itemValues = ContentValues().apply {
            put("pedido_id", newId)
            if (item.productoId != null) put("producto_id", item.productoId) else putNull("producto_id")
            put("codigo", item.codigo)
            put("nombre", item.nombre)
            put("unidad", item.unidad)
            if (item.precioUnitario != null) put("precio_unitario", item.precioUnitario) else putNull("precio_unitario")
            put("cantidad", item.cantidad)
          }
          db.insertOrThrow("pedido_items", null, itemValues)
        }
        savedIds.add(newId)
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    reloadSync()
    savedIds
  }

  override suspend fun updatePedidoEstado(pedidoId: Long, estado: PedidoEstado) =
    withContext(Dispatchers.IO) {
      val db = dbHelper.writableDatabase
      val values = ContentValues().apply {
        put("estado", estado.name)
        if (estado == PedidoEstado.ENVIADO) {
          put("enviado_en", System.currentTimeMillis())
        }
      }
      db.update("pedidos", values, "id = ?", arrayOf(pedidoId.toString()))
      reloadSync()
    }

  override suspend fun deletePedido(pedidoId: Long) = withContext(Dispatchers.IO) {
    dbHelper.writableDatabase.delete("pedidos", "id = ?", arrayOf(pedidoId.toString()))
    reloadSync()
  }

  private fun reloadSync() {
    val db = dbHelper.readableDatabase
    val loadedCatalogs = loadCatalogos(db)
    val loadedProducts = loadProductos(db)
    val loadedProviders = loadProveedores(db, loadedProducts, loadedCatalogs)
    val loadedOrders = loadPedidos(db)
    val loadedIsaacCache = loadIsaacPosCache(db)
    val loadedPriceHistory = loadHistorialPrecios(db)

    _catalogos.value = loadedCatalogs
    _productos.value = loadedProducts
    _proveedoresResumen.value = loadedProviders
    _pedidos.value = loadedOrders
    _isaacPosProductos.value = loadedIsaacCache
    _historialPrecios.value = loadedPriceHistory
  }

  private fun insertPriceHistoryRow(
    db: android.database.sqlite.SQLiteDatabase,
    proveedorId: Long,
    productoId: Long?,
    isaacPosId: String?,
    catalogoId: Long?,
    nombreProducto: String,
    unidad: String,
    precioOriginal: Double,
    moneda: MonedaCatalogo,
    tasaUsadaBs: Double?,
    costoUnitarioUsd: Double,
    registradoEn: Long,
  ) {
    val cv = ContentValues().apply {
      put("proveedor_id", proveedorId)
      if (productoId != null) put("producto_id", productoId) else putNull("producto_id")
      if (!isaacPosId.isNullOrBlank()) put("isaac_pos_id", isaacPosId) else putNull("isaac_pos_id")
      if (catalogoId != null) put("catalogo_id", catalogoId) else putNull("catalogo_id")
      put("nombre_producto", nombreProducto)
      put("unidad", unidad)
      put("precio_original", precioOriginal)
      put("moneda", moneda.name)
      if (tasaUsadaBs != null && tasaUsadaBs > 0.0) put("tasa_usada_bs", tasaUsadaBs) else putNull("tasa_usada_bs")
      put("costo_unitario_usd", costoUnitarioUsd)
      put("registrado_en", registradoEn)
    }
    db.insert("historial_precios", null, cv)
  }

  private fun loadProveedoresBasic(db: android.database.sqlite.SQLiteDatabase): List<Proveedor> {
    val list = mutableListOf<Proveedor>()
    db.rawQuery(
      "SELECT id, nombre, telefono_whatsapp, notas, creado_en FROM proveedores ORDER BY LOWER(nombre) ASC",
      null,
    ).use { c ->
      while (c.moveToNext()) {
        list.add(
          Proveedor(
            id = c.getLong(0),
            nombre = c.getString(1),
            telefonoWhatsApp = c.getString(2),
            notas = c.getString(3),
            creadoEn = c.getLong(4),
          )
        )
      }
    }
    return list
  }

  private fun loadProveedores(
    db: android.database.sqlite.SQLiteDatabase,
    allProducts: List<Producto>,
    allCatalogs: List<CatalogoArchivo>,
  ): List<ProveedorResumen> {
    val productsByProv = allProducts.groupBy { it.proveedorId }
    val catalogsByProv = allCatalogs.groupBy { it.proveedorId }
    return loadProveedoresBasic(db).map { prov ->
      val provProducts = productsByProv[prov.id].orEmpty()
      val provCatalogs = catalogsByProv[prov.id].orEmpty()
      ProveedorResumen(
        proveedor = prov,
        cantidadProductos = provProducts.size,
        cantidadCatalogos = provCatalogs.size,
        ultimoCatalogoEn = provCatalogs.maxOfOrNull { it.importadoEn },
        productosBajoStock = provProducts.count { it.stockPos != null && it.stockPos <= 5.0 },
      )
    }
  }

  private fun loadCatalogos(db: android.database.sqlite.SQLiteDatabase): List<CatalogoArchivo> {
    val result = mutableListOf<CatalogoArchivo>()
    db.rawQuery(
      """
      SELECT id, proveedor_id, nombre_archivo, formato, ruta_local, productos_importados, importado_en,
             moneda, tasa_fija_bs, modo_iva, porcentaje_iva
      FROM catalogos
      ORDER BY importado_en DESC
      """.trimIndent(),
      null,
    ).use { c ->
      while (c.moveToNext()) {
        val formato = runCatching { CatalogoFormato.valueOf(c.getString(3)) }.getOrDefault(CatalogoFormato.PDF)
        result.add(
          CatalogoArchivo(
            id = c.getLong(0),
            proveedorId = c.getLong(1),
            nombreArchivo = c.getString(2),
            formato = formato,
            rutaLocal = c.getString(4),
            productosImportados = c.getInt(5),
            importadoEn = c.getLong(6),
            moneda = MonedaCatalogo.fromDb(c.getStringOrNull(7)),
            tasaFijaBs = c.getDoubleOrNull(8),
            modoIva = ModoIvaCatalogo.fromDb(c.getStringOrNull(9)),
            porcentajeIva = c.getDoubleOrNull(10) ?: 16.0,
          )
        )
      }
    }
    return result
  }

  private fun loadProductos(db: android.database.sqlite.SQLiteDatabase): List<Producto> {
    val result = mutableListOf<Producto>()
    db.rawQuery(
      """
      SELECT id, proveedor_id, catalogo_id, codigo, nombre, unidad, precio, categoria,
             imagen_ruta, pagina_pdf, stock_pos, precio_venta_pos, isaac_pos_id,
             unidades_por_empaque, es_gravado
      FROM productos
      ORDER BY LOWER(nombre) ASC
      """.trimIndent(),
      null,
    ).use { c ->
      while (c.moveToNext()) {
        result.add(c.toProducto())
      }
    }
    return result
  }

  private fun loadHistorialPrecios(db: android.database.sqlite.SQLiteDatabase): List<HistorialPrecio> {
    val result = mutableListOf<HistorialPrecio>()
    db.rawQuery(
      """
      SELECT id, proveedor_id, producto_id, isaac_pos_id, catalogo_id, nombre_producto,
             unidad, precio_original, moneda, tasa_usada_bs, costo_unitario_usd, registrado_en
      FROM historial_precios
      ORDER BY registrado_en DESC, id DESC
      LIMIT 3000
      """.trimIndent(),
      null,
    ).use { c ->
      while (c.moveToNext()) {
        result.add(
          HistorialPrecio(
            id = c.getLong(0),
            proveedorId = c.getLong(1),
            productoId = c.getLongOrNull(2),
            isaacPosId = c.getStringOrNull(3),
            catalogoId = c.getLongOrNull(4),
            nombreProducto = c.getString(5),
            unidad = c.getString(6),
            precioOriginal = c.getDouble(7),
            moneda = MonedaCatalogo.fromDb(c.getStringOrNull(8)),
            tasaUsadaBs = c.getDoubleOrNull(9),
            costoUnitarioUsd = c.getDouble(10),
            registradoEn = c.getLong(11),
          )
        )
      }
    }
    return result
  }

  private fun loadIsaacPosCache(db: android.database.sqlite.SQLiteDatabase): List<IsaacPosProducto> {
    val result = mutableListOf<IsaacPosProducto>()
    db.rawQuery(
      """
      SELECT producto_id, sucursal_id, codigo_barra, nombre, categoria, precio_venta,
             stock_actual, proveedor_nombre_remoto, proveedor_telefono_remoto,
             unidad_pedido, costo_unitario, stock_minimo, sincronizado_en
      FROM isaac_pos_cache
      ORDER BY CASE WHEN proveedor_nombre_remoto = '' THEN 1 ELSE 0 END,
               LOWER(proveedor_nombre_remoto) ASC,
               CASE WHEN stock_actual > 0 THEN 0 ELSE 1 END,
               LOWER(nombre) ASC
      """.trimIndent(),
      null,
    ).use { c ->
      while (c.moveToNext()) {
        result.add(
          IsaacPosProducto(
            productoId = c.getString(0),
            sucursalId = c.getString(1),
            codigoBarra = c.getString(2),
            nombre = c.getString(3),
            categoria = c.getString(4),
            precioVenta = c.getDoubleOrNull(5),
            stockActual = c.getDouble(6),
            proveedorNombreRemoto = c.getString(7),
            proveedorTelefonoRemoto = c.getString(8),
            unidadPedido = c.getString(9),
            costoUnitario = c.getDoubleOrNull(10),
            stockMinimo = c.getDouble(11),
            sincronizadoEn = c.getLong(12),
          )
        )
      }
    }
    return result
  }

  private fun loadPedidos(db: android.database.sqlite.SQLiteDatabase): List<Pedido> {
    val itemsByPedido = mutableMapOf<Long, MutableList<PedidoItem>>()
    db.rawQuery(
      "SELECT id, pedido_id, producto_id, codigo, nombre, unidad, precio_unitario, cantidad FROM pedido_items ORDER BY id ASC",
      null,
    ).use { c ->
      while (c.moveToNext()) {
        val item = PedidoItem(
          id = c.getLong(0),
          pedidoId = c.getLong(1),
          productoId = c.getLongOrNull(2),
          codigo = c.getString(3),
          nombre = c.getString(4),
          unidad = c.getString(5),
          precioUnitario = c.getDoubleOrNull(6),
          cantidad = c.getDouble(7),
        )
        itemsByPedido.getOrPut(item.pedidoId) { mutableListOf() }.add(item)
      }
    }

    val result = mutableListOf<Pedido>()
    db.rawQuery(
      "SELECT id, proveedor_id, proveedor_nombre, proveedor_telefono, estado, notas, incluir_precios, creado_en, enviado_en FROM pedidos ORDER BY creado_en DESC",
      null,
    ).use { c ->
      while (c.moveToNext()) {
        val id = c.getLong(0)
        result.add(
          Pedido(
            id = id,
            proveedorId = c.getLong(1),
            proveedorNombre = c.getString(2),
            proveedorTelefono = c.getString(3),
            estado = PedidoEstado.fromDb(c.getString(4)),
            notas = c.getString(5),
            incluirPrecios = c.getInt(6) != 0,
            creadoEn = c.getLong(7),
            enviadoEn = c.getLongOrNull(8),
            items = itemsByPedido[id].orEmpty(),
          )
        )
      }
    }
    return result
  }

  private fun Cursor.toProducto(): Producto = Producto(
    id = getLong(0),
    proveedorId = getLong(1),
    catalogoId = getLongOrNull(2),
    codigo = getString(3),
    nombre = getString(4),
    unidad = getString(5),
    precio = getDoubleOrNull(6),
    categoria = getString(7),
    imagenRuta = getStringOrNull(8),
    paginaPdf = getIntOrNull(9),
    stockPos = getDoubleOrNull(10),
    precioVentaPos = getDoubleOrNull(11),
    isaacPosId = getStringOrNull(12),
    unidadesPorEmpaque = getDoubleOrNull(13),
    esGravado = getIntOrNull(14)?.let { it != 0 },
  )
}
