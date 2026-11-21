package com.pedidos.proveedores.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PedidosDbHelper(context: Context) : SQLiteOpenHelper(
  context,
  DATABASE_NAME,
  null,
  DATABASE_VERSION,
) {

  override fun onConfigure(db: SQLiteDatabase) {
    super.onConfigure(db)
    db.setForeignKeyConstraintsEnabled(true)
    db.enableWriteAheadLogging()
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE proveedores (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        nombre TEXT NOT NULL,
        telefono_whatsapp TEXT NOT NULL DEFAULT '',
        notas TEXT NOT NULL DEFAULT '',
        creado_en INTEGER NOT NULL
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      CREATE TABLE catalogos (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        proveedor_id INTEGER NOT NULL,
        nombre_archivo TEXT NOT NULL,
        formato TEXT NOT NULL,
        ruta_local TEXT NOT NULL,
        productos_importados INTEGER NOT NULL DEFAULT 0,
        importado_en INTEGER NOT NULL,
        moneda TEXT NOT NULL DEFAULT 'USD',
        tasa_fija_bs REAL,
        modo_iva TEXT NOT NULL DEFAULT 'INCLUIDO',
        porcentaje_iva REAL NOT NULL DEFAULT 16.0,
        FOREIGN KEY (proveedor_id) REFERENCES proveedores(id) ON DELETE CASCADE
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      CREATE TABLE productos (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        proveedor_id INTEGER NOT NULL,
        catalogo_id INTEGER,
        codigo TEXT NOT NULL DEFAULT '',
        nombre TEXT NOT NULL,
        unidad TEXT NOT NULL DEFAULT 'u',
        precio REAL,
        categoria TEXT NOT NULL DEFAULT '',
        imagen_ruta TEXT,
        pagina_pdf INTEGER,
        stock_pos REAL,
        precio_venta_pos REAL,
        isaac_pos_id TEXT,
        unidades_por_empaque REAL,
        es_gravado INTEGER,
        FOREIGN KEY (proveedor_id) REFERENCES proveedores(id) ON DELETE CASCADE,
        FOREIGN KEY (catalogo_id) REFERENCES catalogos(id) ON DELETE SET NULL
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      CREATE TABLE pedidos (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        proveedor_id INTEGER NOT NULL,
        proveedor_nombre TEXT NOT NULL,
        proveedor_telefono TEXT NOT NULL DEFAULT '',
        estado TEXT NOT NULL,
        notas TEXT NOT NULL DEFAULT '',
        incluir_precios INTEGER NOT NULL DEFAULT 1,
        creado_en INTEGER NOT NULL,
        enviado_en INTEGER,
        FOREIGN KEY (proveedor_id) REFERENCES proveedores(id) ON DELETE CASCADE
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      CREATE TABLE pedido_items (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        pedido_id INTEGER NOT NULL,
        producto_id INTEGER,
        codigo TEXT NOT NULL DEFAULT '',
        nombre TEXT NOT NULL,
        unidad TEXT NOT NULL DEFAULT 'u',
        precio_unitario REAL,
        cantidad REAL NOT NULL,
        FOREIGN KEY (pedido_id) REFERENCES pedidos(id) ON DELETE CASCADE,
        FOREIGN KEY (producto_id) REFERENCES productos(id) ON DELETE SET NULL
      )
      """.trimIndent()
    )

    createIsaacPosCacheTable(db)
    createPriceHistoryTable(db)

    db.execSQL("CREATE INDEX idx_catalogos_proveedor ON catalogos(proveedor_id)")
    db.execSQL("CREATE INDEX idx_productos_proveedor ON productos(proveedor_id)")
    db.execSQL("CREATE INDEX idx_productos_isaac_pos_id ON productos(isaac_pos_id)")
    db.execSQL("CREATE INDEX idx_pedidos_proveedor ON pedidos(proveedor_id)")
    db.execSQL("CREATE INDEX idx_pedido_items_pedido ON pedido_items(pedido_id)")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 2) {
      db.execSQL("ALTER TABLE productos ADD COLUMN imagen_ruta TEXT")
      db.execSQL("ALTER TABLE productos ADD COLUMN pagina_pdf INTEGER")
    }
    if (oldVersion < 3) {
      db.execSQL("ALTER TABLE productos ADD COLUMN stock_pos REAL")
      db.execSQL("ALTER TABLE productos ADD COLUMN precio_venta_pos REAL")
      db.execSQL("ALTER TABLE productos ADD COLUMN isaac_pos_id TEXT")
      db.execSQL("CREATE INDEX IF NOT EXISTS idx_productos_isaac_pos_id ON productos(isaac_pos_id)")
      createIsaacPosCacheTable(db)
    }
    if (oldVersion < 4) {
      db.execSQL("ALTER TABLE catalogos ADD COLUMN moneda TEXT NOT NULL DEFAULT 'USD'")
      db.execSQL("ALTER TABLE catalogos ADD COLUMN tasa_fija_bs REAL")
      db.execSQL("ALTER TABLE catalogos ADD COLUMN modo_iva TEXT NOT NULL DEFAULT 'INCLUIDO'")
      db.execSQL("ALTER TABLE catalogos ADD COLUMN porcentaje_iva REAL NOT NULL DEFAULT 16.0")
      db.execSQL("ALTER TABLE productos ADD COLUMN unidades_por_empaque REAL")
      db.execSQL("ALTER TABLE productos ADD COLUMN es_gravado INTEGER")
      createPriceHistoryTable(db)
    }
  }

  private fun createPriceHistoryTable(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS historial_precios (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        proveedor_id INTEGER NOT NULL,
        producto_id INTEGER,
        isaac_pos_id TEXT,
        catalogo_id INTEGER,
        nombre_producto TEXT NOT NULL,
        unidad TEXT NOT NULL DEFAULT 'u',
        precio_original REAL NOT NULL,
        moneda TEXT NOT NULL DEFAULT 'USD',
        tasa_usada_bs REAL,
        costo_unitario_usd REAL NOT NULL,
        registrado_en INTEGER NOT NULL,
        FOREIGN KEY (proveedor_id) REFERENCES proveedores(id) ON DELETE CASCADE,
        FOREIGN KEY (producto_id) REFERENCES productos(id) ON DELETE SET NULL,
        FOREIGN KEY (catalogo_id) REFERENCES catalogos(id) ON DELETE SET NULL
      )
      """.trimIndent()
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_hist_precios_prov ON historial_precios(proveedor_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_hist_precios_pos ON historial_precios(isaac_pos_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_hist_precios_prod ON historial_precios(producto_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_hist_precios_fecha ON historial_precios(registrado_en DESC)")
  }

  private fun createIsaacPosCacheTable(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS isaac_pos_cache (
        producto_id TEXT PRIMARY KEY,
        sucursal_id TEXT NOT NULL DEFAULT 'principal',
        codigo_barra TEXT NOT NULL DEFAULT '',
        nombre TEXT NOT NULL,
        categoria TEXT NOT NULL DEFAULT '',
        precio_venta REAL,
        stock_actual REAL NOT NULL DEFAULT 0,
        proveedor_nombre_remoto TEXT NOT NULL DEFAULT '',
        proveedor_telefono_remoto TEXT NOT NULL DEFAULT '',
        unidad_pedido TEXT NOT NULL DEFAULT 'u',
        costo_unitario REAL,
        stock_minimo REAL NOT NULL DEFAULT 5,
        sincronizado_en INTEGER NOT NULL
      )
      """.trimIndent()
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_isaac_cache_categoria ON isaac_pos_cache(categoria)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_isaac_cache_stock ON isaac_pos_cache(stock_actual)")
  }

  companion object {
    const val DATABASE_NAME = "pedidos_proveedores.db"
    const val DATABASE_VERSION = 4
  }
}
