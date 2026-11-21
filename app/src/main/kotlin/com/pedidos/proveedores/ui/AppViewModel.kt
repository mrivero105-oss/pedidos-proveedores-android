package com.pedidos.proveedores.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pedidos.proveedores.BuildConfig
import com.pedidos.proveedores.data.local.AiSettings
import com.pedidos.proveedores.data.local.AiSettingsStore
import com.pedidos.proveedores.data.local.IsaacPosSettings
import com.pedidos.proveedores.data.local.IsaacPosSettingsStore
import com.pedidos.proveedores.data.local.OtaSettings
import com.pedidos.proveedores.data.local.OtaSettingsStore
import com.pedidos.proveedores.data.local.PedidosRepository
import com.pedidos.proveedores.data.local.PricingSettings
import com.pedidos.proveedores.data.local.PricingSettingsStore
import com.pedidos.proveedores.domain.isaacpos.IsaacPosClient
import com.pedidos.proveedores.domain.isaacpos.IsaacPosSyncSummary
import com.pedidos.proveedores.domain.pricing.BcvRateClient
import com.pedidos.proveedores.domain.pricing.OptimalOrderPlan
import com.pedidos.proveedores.domain.pricing.PricingIntelligenceEngine
import com.pedidos.proveedores.domain.reconcile.CatalogReconciliationLink
import com.pedidos.proveedores.domain.model.CatalogoArchivo
import com.pedidos.proveedores.domain.model.CatalogoFormato
import com.pedidos.proveedores.domain.model.HistorialPrecio
import com.pedidos.proveedores.domain.model.IsaacPosProducto
import com.pedidos.proveedores.domain.model.ModoIvaCatalogo
import com.pedidos.proveedores.domain.model.MonedaCatalogo
import com.pedidos.proveedores.domain.model.Pedido
import com.pedidos.proveedores.domain.model.PedidoEstado
import com.pedidos.proveedores.domain.model.PedidoItem
import com.pedidos.proveedores.domain.model.Producto
import com.pedidos.proveedores.domain.model.ProductoImportado
import com.pedidos.proveedores.domain.model.Proveedor
import com.pedidos.proveedores.domain.model.ProveedorResumen
import com.pedidos.proveedores.domain.model.ResultadoImportacion
import com.pedidos.proveedores.domain.ota.OtaCheckResult
import com.pedidos.proveedores.domain.ota.OtaInstallLaunchResult
import com.pedidos.proveedores.domain.ota.OtaReleaseInfo
import com.pedidos.proveedores.domain.ota.OtaUpdater
import com.pedidos.proveedores.domain.parser.CatalogImporter
import com.pedidos.proveedores.domain.parser.GeminiCatalogExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface CatalogImportUiState {
  data object Idle : CatalogImportUiState
  data class Loading(
    val proveedorId: Long,
    val statusText: String = "Analizando catálogo...",
    val currentPage: Int = 0,
    val totalPages: Int = 0,
  ) : CatalogImportUiState
  data class Ready(
    val proveedorId: Long,
    val resultado: ResultadoImportacion,
    val reemplazarExistentes: Boolean = false,
    val moneda: MonedaCatalogo = MonedaCatalogo.USD,
    val tasaFijaBs: Double? = null,
    val modoIva: ModoIvaCatalogo = ModoIvaCatalogo.INCLUIDO,
    val porcentajeIva: Double = 16.0,
    val guardando: Boolean = false,
  ) : CatalogImportUiState
  data class Error(val proveedorId: Long, val mensaje: String) : CatalogImportUiState
}

sealed interface OtaUiState {
  data object Idle : OtaUiState
  data object Checking : OtaUiState
  data class UpToDate(val currentVersion: String, val latestTag: String?) : OtaUiState
  data class UpdateAvailable(val release: OtaReleaseInfo) : OtaUiState
  data class Downloading(
    val release: OtaReleaseInfo,
    val downloadedBytes: Long,
    val totalBytes: Long,
  ) : OtaUiState
  data class ReadyToInstall(
    val release: OtaReleaseInfo,
    val apkFile: File,
    val waitingForPermission: Boolean = false,
  ) : OtaUiState
  data class Error(val message: String) : OtaUiState
}

sealed interface IsaacPosSyncUiState {
  data object Idle : IsaacPosSyncUiState
  data class Syncing(val fetchedCount: Int, val statusText: String) : IsaacPosSyncUiState
  data class LastResult(val summary: IsaacPosSyncSummary, val sourceLabel: String) : IsaacPosSyncUiState
  data class Error(val message: String) : IsaacPosSyncUiState
}

data class OrderDraftState(
  val editingPedidoId: Long? = null,
  val proveedorId: Long? = null,
  val quantitiesByProductId: Map<Long, Double> = emptyMap(),
  val customPricesByProductId: Map<Long, Double> = emptyMap(),
  val manualItems: List<PedidoItem> = emptyList(),
  val notas: String = "",
  val incluirPrecios: Boolean = false,
) {
  val itemCount: Int
    get() = quantitiesByProductId.count { it.value > 0.0 } + manualItems.size
}

class AppViewModel(
  private val repository: PedidosRepository,
  private val catalogImporter: CatalogImporter,
  private val aiSettingsStore: AiSettingsStore,
  private val geminiExtractor: GeminiCatalogExtractor,
  private val otaSettingsStore: OtaSettingsStore,
  private val otaUpdater: OtaUpdater,
  private val isaacPosSettingsStore: IsaacPosSettingsStore,
  private val isaacPosClient: IsaacPosClient,
  private val pricingSettingsStore: PricingSettingsStore,
  private val bcvRateClient: BcvRateClient,
) : ViewModel() {

  val proveedoresResumen: StateFlow<List<ProveedorResumen>> = repository.proveedoresResumen
  val productos: StateFlow<List<Producto>> = repository.productos
  val catalogos: StateFlow<List<CatalogoArchivo>> = repository.catalogos
  val pedidos: StateFlow<List<Pedido>> = repository.pedidos
  val isaacPosProductos: StateFlow<List<IsaacPosProducto>> = repository.isaacPosProductos
  val historialPrecios: StateFlow<List<HistorialPrecio>> = repository.historialPrecios
  val aiSettings: StateFlow<AiSettings> = aiSettingsStore.settings
  val otaSettings: StateFlow<OtaSettings> = otaSettingsStore.settings
  val isaacPosSettings: StateFlow<IsaacPosSettings> = isaacPosSettingsStore.settings
  val pricingSettings: StateFlow<PricingSettings> = pricingSettingsStore.settings

  private val _bcvRefreshing = MutableStateFlow(false)
  val bcvRefreshing: StateFlow<Boolean> = _bcvRefreshing.asStateFlow()

  private val _importState = MutableStateFlow<CatalogImportUiState>(CatalogImportUiState.Idle)
  val importState: StateFlow<CatalogImportUiState> = _importState.asStateFlow()

  private val _otaState = MutableStateFlow<OtaUiState>(OtaUiState.Idle)
  val otaState: StateFlow<OtaUiState> = _otaState.asStateFlow()

  private val _isaacPosState = MutableStateFlow<IsaacPosSyncUiState>(IsaacPosSyncUiState.Idle)
  val isaacPosState: StateFlow<IsaacPosSyncUiState> = _isaacPosState.asStateFlow()

  private val _orderDraft = MutableStateFlow(OrderDraftState())
  val orderDraft: StateFlow<OrderDraftState> = _orderDraft.asStateFlow()

  private val _snackbarMessage = MutableStateFlow<String?>(null)
  val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

  private val _aiTestRunning = MutableStateFlow(false)
  val aiTestRunning: StateFlow<Boolean> = _aiTestRunning.asStateFlow()

  private val _aiTestFeedback = MutableStateFlow<String?>(null)
  val aiTestFeedback: StateFlow<String?> = _aiTestFeedback.asStateFlow()

  init {
    viewModelScope.launch {
      repository.refreshAll()
      val posCfg = isaacPosSettingsStore.settings.value
      val elapsedMs = posCfg.lastSyncMillis?.let { System.currentTimeMillis() - it } ?: Long.MAX_VALUE
      if (posCfg.isConfigured && elapsedMs >= AUTO_SYNC_INTERVAL_MS) {
        syncIsaacPosNow(silent = true)
      }
    }
    if (otaSettingsStore.settings.value.autoCheckOnLaunch) {
      checkForOtaUpdate(silent = true)
    }
    val pCfg = pricingSettingsStore.settings.value
    val bcvElapsed = pCfg.tasaUpdatedAtMillis?.let { System.currentTimeMillis() - it } ?: Long.MAX_VALUE
    val shouldAutoFetchBcv = !pCfg.hasValidExchangeRate ||
      (pCfg.tasaSourceLabel.startsWith("BCV", ignoreCase = true) && bcvElapsed >= BCV_AUTO_REFRESH_MS)
    if (shouldAutoFetchBcv) {
      fetchOfficialBcvRate(silent = true)
    }
  }

  fun showMessage(message: String) {
    _snackbarMessage.value = message
  }

  fun clearMessage() {
    _snackbarMessage.value = null
  }

  // --- Actualizaciones OTA (GitHub Releases) ---

  fun saveOtaSettings(repoSlug: String, autoCheckOnLaunch: Boolean) {
    otaSettingsStore.save(repoSlug, autoCheckOnLaunch)
  }

  fun checkForOtaUpdate(
    repoSlugOverride: String? = null,
    silent: Boolean = false,
  ) {
    val slug = repoSlugOverride ?: otaSettingsStore.settings.value.repoSlug
    if (!silent) {
      _otaState.value = OtaUiState.Checking
    }
    viewModelScope.launch {
      when (val res = otaUpdater.checkForUpdate(slug, BuildConfig.VERSION_NAME)) {
        is OtaCheckResult.UpdateAvailable -> {
          _otaState.value = OtaUiState.UpdateAvailable(res.release)
        }
        is OtaCheckResult.UpToDate -> {
          if (!silent) {
            _otaState.value = OtaUiState.UpToDate(res.currentVersion, res.latestTag)
          }
        }
        is OtaCheckResult.Error -> {
          if (!silent) {
            _otaState.value = OtaUiState.Error(res.message)
          }
        }
      }
    }
  }

  fun downloadAndInstallOta() {
    val release = when (val s = _otaState.value) {
      is OtaUiState.UpdateAvailable -> s.release
      is OtaUiState.ReadyToInstall -> s.release
      else -> return
    }
    _otaState.value = OtaUiState.Downloading(release, 0L, release.apkSizeBytes)
    viewModelScope.launch {
      val result = otaUpdater.downloadApk(release) { downloaded, total ->
        _otaState.value = OtaUiState.Downloading(release, downloaded, total)
      }
      result.onSuccess { apkFile ->
        _otaState.value = OtaUiState.ReadyToInstall(release, apkFile, waitingForPermission = false)
        installDownloadedOta()
      }.onFailure { err ->
        _otaState.value = OtaUiState.Error(err.localizedMessage ?: "No se pudo descargar el APK.")
      }
    }
  }

  fun installDownloadedOta() {
    val current = _otaState.value as? OtaUiState.ReadyToInstall ?: return
    when (otaUpdater.launchApkInstall(current.apkFile)) {
      OtaInstallLaunchResult.LAUNCHED_INSTALLER -> {
        _otaState.value = current.copy(waitingForPermission = false)
      }
      OtaInstallLaunchResult.NEEDS_UNKNOWN_SOURCES_PERMISSION -> {
        _otaState.value = current.copy(waitingForPermission = true)
        showMessage("Activa el permiso para instalar actualizaciones y vuelve a la app")
      }
      OtaInstallLaunchResult.FAILED -> {
        _otaState.value = OtaUiState.Error("No se pudo abrir el instalador de Android para el APK descargado.")
      }
    }
  }

  // --- Sincronización Isaac POS (Supabase solo lectura) ---

  fun saveIsaacPosSettings(url: String, publishableKey: String, lowStockThreshold: Double) {
    isaacPosSettingsStore.save(url, publishableKey, lowStockThreshold)
    showMessage("Configuración de Isaac POS guardada")
  }

  fun testIsaacPosConnection(url: String, apiKey: String) {
    _isaacPosState.value = IsaacPosSyncUiState.Syncing(0, "Probando conexión con Supabase...")
    viewModelScope.launch {
      val result = isaacPosClient.testConnection(url, apiKey)
      result.onSuccess { msg ->
        _isaacPosState.value = IsaacPosSyncUiState.Error(msg)
        showMessage(msg)
      }.onFailure { err ->
        val msg = err.localizedMessage ?: "No se pudo conectar con Supabase"
        _isaacPosState.value = IsaacPosSyncUiState.Error(msg)
      }
    }
  }

  fun syncIsaacPosNow(silent: Boolean = false) {
    val cfg = isaacPosSettingsStore.settings.value
    if (!cfg.isConfigured) {
      if (!silent) showMessage("Configura primero tu clave Publishable de Supabase")
      return
    }
    if (_isaacPosState.value is IsaacPosSyncUiState.Syncing) return
    if (!silent) {
      _isaacPosState.value = IsaacPosSyncUiState.Syncing(0, "Conectando con Isaac POS...")
    }
    viewModelScope.launch {
      try {
        val fetched = isaacPosClient.fetchAllProducts(
          supabaseUrl = cfg.supabaseUrl,
          apiKey = cfg.publishableKey,
          defaultLowStock = cfg.lowStockThreshold,
          onProgress = { count, msg ->
            if (!silent) {
              _isaacPosState.value = IsaacPosSyncUiState.Syncing(count, msg)
            }
          },
        )
        if (!silent) {
          _isaacPosState.value = IsaacPosSyncUiState.Syncing(
            fetched.productos.size,
            "Cruzando ${fetched.productos.size} productos con tus proveedores...",
          )
        }
        val summary = repository.syncIsaacPosProducts(fetched.productos)
        isaacPosSettingsStore.markSyncedNow()
        _isaacPosState.value = IsaacPosSyncUiState.LastResult(summary, fetched.sourceLabel)
        if (!silent) {
          showMessage("Isaac POS sincronizado: ${summary.totalDescargados} productos (${summary.productosActualizadosLocales} vinculados)")
        }
      } catch (e: Exception) {
        if (!silent) {
          val msg = e.localizedMessage ?: "Error al sincronizar con Isaac POS"
          _isaacPosState.value = IsaacPosSyncUiState.Error(msg)
          showMessage(msg)
        }
      }
    }
  }

  fun assignIsaacPosProductsToProveedor(proveedorId: Long, items: List<IsaacPosProducto>) {
    viewModelScope.launch {
      val count = repository.assignIsaacPosProductsToProveedor(proveedorId, items)
      showMessage("$count producto(s) de Isaac POS asignados al proveedor")
    }
  }

  fun createProveedorAndAssignIsaacPos(proveedor: Proveedor, items: List<IsaacPosProducto>) {
    viewModelScope.launch {
      val provId = repository.saveProveedor(proveedor)
      val count = repository.assignIsaacPosProductsToProveedor(provId, items)
      showMessage("Proveedor \"${proveedor.nombre}\" creado con $count producto(s) de Isaac POS")
    }
  }

  fun reconcileCatalogLinks(proveedorId: Long, links: List<CatalogReconciliationLink>) {
    if (links.isEmpty()) return
    viewModelScope.launch {
      val count = repository.reconcileCatalogLinks(proveedorId, links)
      showMessage(
        if (count == 1) "1 producto conciliado con tu base de datos Isaac POS"
        else "$count productos conciliados con tu base de datos Isaac POS"
      )
    }
  }

  fun unlinkCatalogProductFromPos(localProductId: Long) {
    viewModelScope.launch {
      repository.unlinkCatalogProductFromPos(localProductId)
      showMessage("Vínculo con Isaac POS eliminado")
    }
  }

  // --- Ajustes de IA (Gemini Vision gratuito) ---

  fun saveAiSettings(apiKey: String, model: String) {
    aiSettingsStore.save(apiKey, model)
    _aiTestFeedback.value = null
    showMessage(if (apiKey.isNotBlank()) "IA Gemini configurada" else "Configuración de IA guardada")
  }

  fun clearAiApiKey() {
    aiSettingsStore.clearKey()
    _aiTestFeedback.value = null
    showMessage("Clave API eliminada")
  }

  fun testGeminiConnection(apiKey: String, model: String) {
    _aiTestRunning.value = true
    _aiTestFeedback.value = null
    viewModelScope.launch {
      val result = geminiExtractor.testConnection(apiKey, model)
      _aiTestRunning.value = false
      _aiTestFeedback.value = result.fold(
        onSuccess = { it },
        onFailure = { "Error: ${it.localizedMessage ?: "no se pudo conectar"}" },
      )
    }
  }

  fun resetAiTestFeedback() {
    _aiTestFeedback.value = null
  }

  // --- Proveedores ---

  fun saveProveedor(proveedor: Proveedor, onSaved: (Long) -> Unit = {}) {
    viewModelScope.launch {
      val id = repository.saveProveedor(proveedor)
      showMessage(if (proveedor.id == 0L) "Proveedor creado" else "Proveedor actualizado")
      onSaved(id)
    }
  }

  fun deleteProveedor(proveedorId: Long, onDeleted: () -> Unit = {}) {
    viewModelScope.launch {
      if (_orderDraft.value.proveedorId == proveedorId) {
        _orderDraft.value = OrderDraftState()
      }
      repository.deleteProveedor(proveedorId)
      showMessage("Proveedor eliminado")
      onDeleted()
    }
  }

  // --- Productos ---

  fun saveProducto(producto: Producto, newPhotoUri: Uri? = null) {
    viewModelScope.launch {
      val finalProducto = if (newPhotoUri != null) {
        val savedThumbPath = runCatching {
          catalogImporter.saveSingleProductPhoto(producto.proveedorId, newPhotoUri)
        }.getOrNull()
        producto.copy(imagenRuta = savedThumbPath ?: producto.imagenRuta)
      } else {
        producto
      }
      repository.saveProducto(finalProducto, pricingSettingsStore.settings.value)
      showMessage(if (producto.id == 0L) "Producto agregado" else "Producto actualizado")
    }
  }

  // --- Inteligencia de Compras y Márgenes ---

  fun fetchOfficialBcvRate(silent: Boolean = false) {
    if (_bcvRefreshing.value) return
    _bcvRefreshing.value = true
    viewModelScope.launch {
      try {
        val result = bcvRateClient.fetchOfficialRate()
        result.onSuccess { quote ->
          val label = if (quote.dateLabel.isNotBlank()) "${quote.sourceLabel} (${quote.dateLabel})" else quote.sourceLabel
          pricingSettingsStore.updateExchangeRate(quote.rateBsPerUsd, label)
          if (!silent) {
            showMessage("Tasa BCV actualizada: Bs. ${"%.2f".format(quote.rateBsPerUsd)} / USD")
          }
        }.onFailure { err ->
          if (!silent) {
            showMessage(err.localizedMessage ?: "No se pudo consultar la tasa BCV; puedes ingresarla manualmente")
          }
        }
      } finally {
        _bcvRefreshing.value = false
      }
    }
  }

  fun savePricingSettings(
    tasaBsPorUsd: Double,
    tasaSourceLabel: String,
    defaultIvaPercent: Double = 16.0,
    minOptimalSavingsPercent: Double = 3.0,
  ) {
    pricingSettingsStore.saveSettings(
      tasaBsPorUsd = tasaBsPorUsd,
      tasaSourceLabel = tasaSourceLabel,
      defaultIvaPercent = defaultIvaPercent,
      minOptimalSavingsPercent = minOptimalSavingsPercent,
    )
    showMessage("Configuración de tasa e IVA guardada")
  }

  fun updateMinOptimalSavingsPercent(percent: Double) {
    pricingSettingsStore.updateMinOptimalSavingsPercent(percent)
  }

  fun updateCatalogPricingConfig(
    catalogoId: Long,
    moneda: MonedaCatalogo,
    tasaFijaBs: Double?,
    modoIva: ModoIvaCatalogo,
    porcentajeIva: Double = 16.0,
  ) {
    viewModelScope.launch {
      repository.updateCatalogPricingConfig(
        catalogoId = catalogoId,
        moneda = moneda,
        tasaFijaBs = tasaFijaBs,
        modoIva = modoIva,
        porcentajeIva = porcentajeIva,
      )
      showMessage("Moneda e IVA del catálogo actualizados")
    }
  }

  fun updateProductoPricingOverrides(
    productoId: Long,
    unidadesPorEmpaque: Double?,
    esGravado: Boolean?,
  ) {
    viewModelScope.launch {
      repository.updateProductoPricingOverrides(
        productoId = productoId,
        unidadesPorEmpaque = unidadesPorEmpaque,
        esGravado = esGravado,
      )
      showMessage("Empaque e IVA del producto actualizados")
    }
  }

  fun createDraftOrdersFromOptimalPlan(
    plan: OptimalOrderPlan,
    onCreated: (Int) -> Unit,
  ) {
    val drafts = PricingIntelligenceEngine.buildDraftPedidosFromPlan(plan)
    if (drafts.isEmpty()) {
      showMessage("No hay productos seleccionados para generar borradores")
      return
    }
    viewModelScope.launch {
      val ids = repository.saveMultiplePedidos(drafts)
      showMessage(
        if (ids.size == 1) "1 borrador de pedido creado en Pedidos"
        else "${ids.size} borradores de pedido creados en Pedidos"
      )
      onCreated(ids.size)
    }
  }

  fun deleteProducto(productoId: Long) {
    viewModelScope.launch {
      repository.deleteProducto(productoId)
      _orderDraft.update { draft ->
        draft.copy(
          quantitiesByProductId = draft.quantitiesByProductId - productoId,
          customPricesByProductId = draft.customPricesByProductId - productoId,
        )
      }
      showMessage("Producto eliminado")
    }
  }

  // --- Importación de Catálogos (PDF / Excel / CSV / Imágenes + IA Gemini opcional) ---

  fun startCatalogImport(proveedorId: Long, uri: Uri) {
    _importState.value = CatalogImportUiState.Loading(
      proveedorId = proveedorId,
      statusText = "Analizando archivo...",
    )
    viewModelScope.launch {
      try {
        val result = catalogImporter.importFromUri(
          proveedorId = proveedorId,
          uri = uri,
          aiSettings = aiSettingsStore.settings.value,
          onProgress = { cur, total, msg ->
            _importState.value = CatalogImportUiState.Loading(
              proveedorId = proveedorId,
              statusText = msg,
              currentPage = cur,
              totalPages = total,
            )
          },
        )
        _importState.value = CatalogImportUiState.Ready(
          proveedorId = proveedorId,
          resultado = result,
          reemplazarExistentes = false,
        )
      } catch (e: Exception) {
        _importState.value = CatalogImportUiState.Error(
          proveedorId = proveedorId,
          mensaje = e.localizedMessage ?: "No se pudo procesar el archivo seleccionado.",
        )
      }
    }
  }

  fun startImagesImport(proveedorId: Long, uris: List<Uri>) {
    if (uris.isEmpty()) return
    _importState.value = CatalogImportUiState.Loading(
      proveedorId = proveedorId,
      statusText = "Preparando ${uris.size} imagen(es)...",
      currentPage = 0,
      totalPages = uris.size,
    )
    viewModelScope.launch {
      try {
        val result = catalogImporter.importImagesFromUris(
          proveedorId = proveedorId,
          uris = uris,
          aiSettings = aiSettingsStore.settings.value,
          onProgress = { cur, total, msg ->
            _importState.value = CatalogImportUiState.Loading(
              proveedorId = proveedorId,
              statusText = msg,
              currentPage = cur,
              totalPages = total,
            )
          },
        )
        _importState.value = CatalogImportUiState.Ready(
          proveedorId = proveedorId,
          resultado = result,
          reemplazarExistentes = false,
        )
      } catch (e: Exception) {
        _importState.value = CatalogImportUiState.Error(
          proveedorId = proveedorId,
          mensaje = e.localizedMessage ?: "No se pudieron procesar las imágenes seleccionadas.",
        )
      }
    }
  }

  fun reExtractCurrentWithGemini() {
    val current = _importState.value as? CatalogImportUiState.Ready ?: return
    val formato = current.resultado.formato
    if (formato != CatalogoFormato.PDF && formato != CatalogoFormato.IMAGEN) return
    val settings = aiSettingsStore.settings.value
    if (!settings.isConfigured) {
      showMessage("Primero configura tu clave API gratuita de Gemini")
      return
    }

    val displayName = current.resultado.nombreArchivo
    val proveedorId = current.proveedorId
    val replaceFlag = current.reemplazarExistentes
    val prevMoneda = current.moneda
    val prevTasaFija = current.tasaFijaBs
    val prevModoIva = current.modoIva
    val prevPorcentajeIva = current.porcentajeIva

    _importState.value = CatalogImportUiState.Loading(
      proveedorId = proveedorId,
      statusText = if (formato == CatalogoFormato.PDF) {
        "Preparando páginas para Gemini Vision..."
      } else {
        "Enviando imágenes a Gemini Vision..."
      },
    )

    viewModelScope.launch {
      try {
        val aiResult = when (formato) {
          CatalogoFormato.PDF -> catalogImporter.extractPdfWithGemini(
            proveedorId = proveedorId,
            pdfFile = File(current.resultado.rutaLocal),
            displayName = displayName,
            apiKey = settings.geminiApiKey,
            model = settings.geminiModel,
            onProgress = { cur, total, msg ->
              _importState.value = CatalogImportUiState.Loading(
                proveedorId = proveedorId,
                statusText = msg,
                currentPage = cur,
                totalPages = total,
              )
            },
          )
          CatalogoFormato.IMAGEN -> {
            val paths = current.resultado.rutasImagenesFuente.ifEmpty { listOf(current.resultado.rutaLocal) }
            catalogImporter.extractImagesWithGemini(
              imagePaths = paths,
              displayName = displayName,
              apiKey = settings.geminiApiKey,
              model = settings.geminiModel,
              onProgress = { cur, total, msg ->
                _importState.value = CatalogImportUiState.Loading(
                  proveedorId = proveedorId,
                  statusText = msg,
                  currentPage = cur,
                  totalPages = total,
                )
              },
            )
          }
          CatalogoFormato.XLSX, CatalogoFormato.CSV -> current.resultado
        }

        _importState.value = CatalogImportUiState.Ready(
          proveedorId = proveedorId,
          resultado = aiResult,
          reemplazarExistentes = replaceFlag,
          moneda = prevMoneda,
          tasaFijaBs = prevTasaFija,
          modoIva = prevModoIva,
          porcentajeIva = prevPorcentajeIva,
        )
      } catch (e: Exception) {
        _importState.value = current.copy(
          resultado = current.resultado.copy(
            advertencia = "Error con Gemini IA: ${e.localizedMessage ?: "verifica tu conexión o clave API"}. Se mantienen los resultados locales.",
          )
        )
      }
    }
  }

  fun toggleImportedItem(tempId: Int) {
    _importState.update { state ->
      if (state !is CatalogImportUiState.Ready) return@update state
      val updated = state.resultado.productos.map { item ->
        if (item.tempId == tempId) item.copy(seleccionado = !item.seleccionado) else item
      }
      state.copy(resultado = state.resultado.copy(productos = updated))
    }
  }

  fun selectAllImported(selected: Boolean) {
    _importState.update { state ->
      if (state !is CatalogImportUiState.Ready) return@update state
      val updated = state.resultado.productos.map { it.copy(seleccionado = selected) }
      state.copy(resultado = state.resultado.copy(productos = updated))
    }
  }

  fun updateImportedItem(updatedItem: ProductoImportado) {
    _importState.update { state ->
      if (state !is CatalogImportUiState.Ready) return@update state
      val updated = state.resultado.productos.map { item ->
        if (item.tempId == updatedItem.tempId) updatedItem else item
      }
      state.copy(resultado = state.resultado.copy(productos = updated))
    }
  }

  fun setReplaceExisting(replace: Boolean) {
    _importState.update { state ->
      if (state !is CatalogImportUiState.Ready) return@update state
      state.copy(reemplazarExistentes = replace)
    }
  }

  fun setImportPricingConfig(
    moneda: MonedaCatalogo,
    tasaFijaBs: Double?,
    modoIva: ModoIvaCatalogo,
    porcentajeIva: Double = 16.0,
  ) {
    _importState.update { state ->
      if (state !is CatalogImportUiState.Ready) return@update state
      state.copy(
        moneda = moneda,
        tasaFijaBs = tasaFijaBs,
        modoIva = modoIva,
        porcentajeIva = porcentajeIva,
      )
    }
  }

  fun confirmCatalogImport(onDone: () -> Unit) {
    val current = _importState.value as? CatalogImportUiState.Ready ?: return
    if (current.guardando) return
    _importState.value = current.copy(guardando = true)

    viewModelScope.launch {
      try {
        val count = current.resultado.productos.count { it.seleccionado && it.nombre.isNotBlank() }
        repository.saveImportedCatalog(
          proveedorId = current.proveedorId,
          nombreArchivo = current.resultado.nombreArchivo,
          formato = current.resultado.formato,
          rutaLocal = current.resultado.rutaLocal,
          productosImportados = current.resultado.productos,
          reemplazarExistentes = current.reemplazarExistentes,
          moneda = current.moneda,
          tasaFijaBs = current.tasaFijaBs,
          modoIva = current.modoIva,
          porcentajeIva = current.porcentajeIva,
          pricingSettings = pricingSettingsStore.settings.value,
        )
        _importState.value = CatalogImportUiState.Idle
        showMessage(
          if (count > 0) "Catálogo guardado con $count productos"
          else "Archivo de catálogo guardado para consulta"
        )
        onDone()
      } catch (e: Exception) {
        _importState.value = current.copy(guardando = false)
        showMessage("Error al guardar el catálogo: ${e.localizedMessage ?: "desconocido"}")
      }
    }
  }

  fun cancelCatalogImport() {
    val current = _importState.value
    if (current is CatalogImportUiState.Ready) {
      runCatching { File(current.resultado.rutaLocal).delete() }
      current.resultado.rutasImagenesFuente.forEach { path -> runCatching { File(path).delete() } }
    }
    _importState.value = CatalogImportUiState.Idle
  }

  fun deleteCatalogo(catalogoId: Long, eliminarProductos: Boolean) {
    viewModelScope.launch {
      repository.deleteCatalogo(catalogoId, eliminarProductos)
      showMessage(if (eliminarProductos) "Catálogo y productos eliminados" else "Archivo de catálogo eliminado")
    }
  }

  // --- Armado de Pedidos ---

  fun startOrderForProveedor(proveedorId: Long) {
    val current = _orderDraft.value
    if (current.proveedorId == proveedorId && current.editingPedidoId == null) return
    _orderDraft.value = OrderDraftState(proveedorId = proveedorId)
  }

  fun incrementProduct(producto: Producto) {
    _orderDraft.update { draft ->
      val currentQty = draft.quantitiesByProductId[producto.id] ?: 0.0
      draft.copy(
        proveedorId = producto.proveedorId,
        quantitiesByProductId = draft.quantitiesByProductId + (producto.id to (currentQty + 1.0)),
      )
    }
  }

  fun decrementProduct(producto: Producto) {
    _orderDraft.update { draft ->
      val currentQty = draft.quantitiesByProductId[producto.id] ?: 0.0
      val next = (currentQty - 1.0).coerceAtLeast(0.0)
      val updatedMap = if (next <= 0.0) {
        draft.quantitiesByProductId - producto.id
      } else {
        draft.quantitiesByProductId + (producto.id to next)
      }
      draft.copy(quantitiesByProductId = updatedMap)
    }
  }

  fun setProductQuantityAndPrice(
    producto: Producto,
    quantity: Double,
    unitPrice: Double?,
    savePriceToCatalog: Boolean,
  ) {
    _orderDraft.update { draft ->
      val updatedQty = if (quantity <= 0.0) {
        draft.quantitiesByProductId - producto.id
      } else {
        draft.quantitiesByProductId + (producto.id to quantity)
      }
      val updatedPrices = if (unitPrice != null) {
        draft.customPricesByProductId + (producto.id to unitPrice)
      } else {
        draft.customPricesByProductId - producto.id
      }
      draft.copy(
        proveedorId = producto.proveedorId,
        quantitiesByProductId = updatedQty,
        customPricesByProductId = updatedPrices,
      )
    }

    if (savePriceToCatalog && unitPrice != null) {
      viewModelScope.launch {
        repository.saveProducto(producto.copy(precio = unitPrice), pricingSettingsStore.settings.value)
      }
    }
  }

  fun addManualItem(nombre: String, cantidad: Double, unidad: String, precio: Double?) {
    if (nombre.isBlank() || cantidad <= 0.0) return
    _orderDraft.update { draft ->
      val newItem = PedidoItem(
        nombre = nombre.trim(),
        unidad = unidad.trim().ifBlank { "u" },
        precioUnitario = precio,
        cantidad = cantidad,
      )
      draft.copy(manualItems = draft.manualItems + newItem)
    }
  }

  fun removeManualItem(index: Int) {
    _orderDraft.update { draft ->
      if (index !in draft.manualItems.indices) return@update draft
      draft.copy(manualItems = draft.manualItems.filterIndexed { i, _ -> i != index })
    }
  }

  fun setOrderNotes(notas: String) {
    _orderDraft.update { it.copy(notas = notas) }
  }

  fun setOrderIncludePrices(incluir: Boolean) {
    _orderDraft.update { it.copy(incluirPrecios = incluir) }
  }

  fun prefillLowStockForCurrentProveedor() {
    val draft = _orderDraft.value
    val provId = draft.proveedorId ?: proveedoresResumen.value.firstOrNull()?.proveedor?.id ?: return
    val threshold = isaacPosSettingsStore.settings.value.lowStockThreshold
    val lowStockProducts = productos.value.filter { prod ->
      prod.proveedorId == provId && prod.stockPos != null && prod.stockPos <= threshold
    }
    if (lowStockProducts.isEmpty()) {
      showMessage("Este proveedor no tiene productos en bajo stock (≤ ${threshold.toInt()})")
      return
    }
    var addedCount = 0
    _orderDraft.update { current ->
      val updatedMap = current.quantitiesByProductId.toMutableMap()
      for (prod in lowStockProducts) {
        if ((updatedMap[prod.id] ?: 0.0) <= 0.0) {
          updatedMap[prod.id] = 1.0
          addedCount++
        }
      }
      current.copy(
        proveedorId = provId,
        quantitiesByProductId = updatedMap,
      )
    }
    if (addedCount > 0) {
      showMessage("$addedCount producto(s) en bajo stock agregados con cantidad 1")
    } else {
      showMessage("Todos los productos en bajo stock ya estaban en el pedido")
    }
  }

  fun clearOrderDraft() {
    val provId = _orderDraft.value.proveedorId
    _orderDraft.value = OrderDraftState(proveedorId = provId)
  }

  fun buildCurrentPedido(estado: PedidoEstado = PedidoEstado.BORRADOR): Pedido? {
    val draft = _orderDraft.value
    val proveedorId = draft.proveedorId ?: return null
    val proveedor = proveedoresResumen.value.firstOrNull { it.proveedor.id == proveedorId }?.proveedor ?: return null
    val provProducts = productos.value.filter { it.proveedorId == proveedorId }.associateBy { it.id }

    val catalogItems = draft.quantitiesByProductId.entries
      .filter { it.value > 0.0 }
      .mapNotNull { (prodId, qty) ->
        val prod = provProducts[prodId] ?: return@mapNotNull null
        val effectivePrice = draft.customPricesByProductId[prodId] ?: prod.precio
        PedidoItem(
          productoId = prod.id,
          codigo = prod.codigo,
          nombre = prod.nombre,
          unidad = prod.unidad,
          precioUnitario = effectivePrice,
          cantidad = qty,
        )
      }

    val allItems = catalogItems + draft.manualItems
    if (allItems.isEmpty()) return null

    val now = System.currentTimeMillis()
    return Pedido(
      id = draft.editingPedidoId ?: 0L,
      proveedorId = proveedor.id,
      proveedorNombre = proveedor.nombre,
      proveedorTelefono = proveedor.telefonoWhatsApp,
      estado = estado,
      notas = draft.notas.trim(),
      incluirPrecios = draft.incluirPrecios,
      creadoEn = now,
      enviadoEn = if (estado == PedidoEstado.ENVIADO) now else null,
      items = allItems,
    )
  }

  fun saveCurrentOrder(estado: PedidoEstado, onSaved: (Pedido) -> Unit = {}) {
    val pedido = buildCurrentPedido(estado) ?: run {
      showMessage("Agrega al menos un producto al pedido")
      return
    }
    viewModelScope.launch {
      val savedId = repository.savePedido(pedido)
      val savedPedido = pedido.copy(id = savedId)
      _orderDraft.value = OrderDraftState(proveedorId = pedido.proveedorId)
      showMessage(
        when (estado) {
          PedidoEstado.BORRADOR -> "Borrador guardado en historial"
          PedidoEstado.ENVIADO -> "Pedido registrado como enviado"
          PedidoEstado.RECIBIDO -> "Pedido guardado"
        }
      )
      onSaved(savedPedido)
    }
  }

  fun loadPedidoIntoDraft(pedido: Pedido, asDuplicate: Boolean) {
    val provProducts = productos.value.filter { it.proveedorId == pedido.proveedorId }.associateBy { it.id }
    val quantities = mutableMapOf<Long, Double>()
    val customPrices = mutableMapOf<Long, Double>()
    val manuals = mutableListOf<PedidoItem>()

    for (item in pedido.items) {
      val prodId = item.productoId
      if (prodId != null && provProducts.containsKey(prodId)) {
        quantities[prodId] = (quantities[prodId] ?: 0.0) + item.cantidad
        if (item.precioUnitario != null) {
          customPrices[prodId] = item.precioUnitario
        }
      } else {
        manuals.add(item.copy(id = 0L, pedidoId = 0L))
      }
    }

    _orderDraft.value = OrderDraftState(
      editingPedidoId = if (asDuplicate) null else pedido.id,
      proveedorId = pedido.proveedorId,
      quantitiesByProductId = quantities,
      customPricesByProductId = customPrices,
      manualItems = manuals,
      notas = pedido.notas,
      incluirPrecios = pedido.incluirPrecios,
    )
  }

  fun updatePedidoEstado(pedidoId: Long, estado: PedidoEstado) {
    viewModelScope.launch {
      repository.updatePedidoEstado(pedidoId, estado)
      showMessage("Estado actualizado a ${estado.etiqueta}")
    }
  }

  fun deletePedido(pedidoId: Long, onDeleted: () -> Unit = {}) {
    viewModelScope.launch {
      repository.deletePedido(pedidoId)
      showMessage("Pedido eliminado")
      onDeleted()
    }
  }

  companion object {
    private const val AUTO_SYNC_INTERVAL_MS = 15L * 60L * 1000L
    private const val BCV_AUTO_REFRESH_MS = 12L * 60L * 60L * 1000L

    fun factory(
      repository: PedidosRepository,
      catalogImporter: CatalogImporter,
      aiSettingsStore: AiSettingsStore,
      geminiExtractor: GeminiCatalogExtractor,
      otaSettingsStore: OtaSettingsStore,
      otaUpdater: OtaUpdater,
      isaacPosSettingsStore: IsaacPosSettingsStore,
      isaacPosClient: IsaacPosClient,
      pricingSettingsStore: PricingSettingsStore,
      bcvRateClient: BcvRateClient,
    ): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return AppViewModel(
            repository = repository,
            catalogImporter = catalogImporter,
            aiSettingsStore = aiSettingsStore,
            geminiExtractor = geminiExtractor,
            otaSettingsStore = otaSettingsStore,
            otaUpdater = otaUpdater,
            isaacPosSettingsStore = isaacPosSettingsStore,
            isaacPosClient = isaacPosClient,
            pricingSettingsStore = pricingSettingsStore,
            bcvRateClient = bcvRateClient,
          ) as T
        }
      }
  }
}
