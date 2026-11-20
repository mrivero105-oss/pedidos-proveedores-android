package com.pedidos.proveedores

import android.app.Application
import com.pedidos.proveedores.data.local.AiSettingsStore
import com.pedidos.proveedores.data.local.IsaacPosSettingsStore
import com.pedidos.proveedores.data.local.OtaSettingsStore
import com.pedidos.proveedores.data.local.PedidosDbHelper
import com.pedidos.proveedores.data.local.PedidosRepository
import com.pedidos.proveedores.data.local.SqlitePedidosRepository
import com.pedidos.proveedores.data.local.ThemeSettingsStore
import com.pedidos.proveedores.domain.isaacpos.IsaacPosClient
import com.pedidos.proveedores.domain.ota.OtaUpdater
import com.pedidos.proveedores.domain.parser.CatalogImporter
import com.pedidos.proveedores.domain.parser.GeminiCatalogExtractor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AppContainer(app: Application) {
  private val dbHelper = PedidosDbHelper(app)
  val repository: PedidosRepository = SqlitePedidosRepository(dbHelper)
  val aiSettingsStore: AiSettingsStore = AiSettingsStore(app)
  val geminiExtractor: GeminiCatalogExtractor = GeminiCatalogExtractor()
  val catalogImporter: CatalogImporter = CatalogImporter(app, geminiExtractor)
  val otaSettingsStore: OtaSettingsStore = OtaSettingsStore(app)
  val otaUpdater: OtaUpdater = OtaUpdater(app)
  val isaacPosSettingsStore: IsaacPosSettingsStore = IsaacPosSettingsStore(app)
  val isaacPosClient: IsaacPosClient = IsaacPosClient()
  val themeSettingsStore: ThemeSettingsStore = ThemeSettingsStore(app)
}

class PedidosApp : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    PDFBoxResourceLoader.init(applicationContext)
    container = AppContainer(this)
  }
}
