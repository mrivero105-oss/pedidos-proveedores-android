package com.pedidos.proveedores

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ShoppingCartCheckout
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.pedidos.proveedores.data.local.AppThemeMode
import com.pedidos.proveedores.domain.model.Pedido
import com.pedidos.proveedores.domain.model.PedidoEstado
import com.pedidos.proveedores.domain.parser.PendingSharedPayload
import com.pedidos.proveedores.domain.pricing.PricingIntelligenceEngine
import com.pedidos.proveedores.domain.whatsapp.WhatsAppOrderFormatter
import com.pedidos.proveedores.domain.whatsapp.WhatsAppSender
import com.pedidos.proveedores.ui.AppViewModel
import com.pedidos.proveedores.ui.OtaUiState
import com.pedidos.proveedores.ui.components.AiSettingsDialog
import com.pedidos.proveedores.ui.components.OtaUpdateDialog
import com.pedidos.proveedores.ui.components.SharedProviderPickerDialog
import com.pedidos.proveedores.ui.components.WhatsAppPreviewDialog
import com.pedidos.proveedores.ui.screens.ConciliarCatalogoScreen
import com.pedidos.proveedores.ui.screens.HistorialPedidosScreen
import com.pedidos.proveedores.ui.screens.ImportarCatalogoScreen
import com.pedidos.proveedores.ui.screens.IsaacPosSyncScreen
import com.pedidos.proveedores.ui.screens.NuevoPedidoScreen
import com.pedidos.proveedores.ui.screens.PedidoDetalleScreen
import com.pedidos.proveedores.ui.screens.PreciosInteligenciaScreen
import com.pedidos.proveedores.ui.screens.ProveedorDetalleScreen
import com.pedidos.proveedores.ui.screens.ProveedoresScreen
import com.pedidos.proveedores.ui.screens.VisorImagenScreen
import com.pedidos.proveedores.ui.screens.VisorPdfScreen
import com.pedidos.proveedores.ui.screens.VisorTextoScreen

private const val TAB_PROVEEDORES = 0
private const val TAB_PRECIOS = 1
private const val TAB_NUEVO_PEDIDO = 2
private const val TAB_HISTORIAL = 3

@Composable
fun MainNavigation(
  viewModel: AppViewModel,
  themeMode: AppThemeMode = AppThemeMode.SYSTEM,
  onCycleTheme: () -> Unit = {},
  pendingSharedPayload: PendingSharedPayload? = null,
  onConsumeSharedPayload: () -> Unit = {},
) {
  val context = LocalContext.current
  val backStack = rememberNavBackStack(HomeRoute)
  var selectedTab by rememberSaveable { mutableIntStateOf(TAB_PROVEEDORES) }

  val proveedores by viewModel.proveedoresResumen.collectAsState()
  val productos by viewModel.productos.collectAsState()
  val catalogos by viewModel.catalogos.collectAsState()
  val pedidos by viewModel.pedidos.collectAsState()
  val importState by viewModel.importState.collectAsState()
  val orderDraft by viewModel.orderDraft.collectAsState()
  val snackbarMessage by viewModel.snackbarMessage.collectAsState()
  val aiSettings by viewModel.aiSettings.collectAsState()
  val aiTestRunning by viewModel.aiTestRunning.collectAsState()
  val aiTestFeedback by viewModel.aiTestFeedback.collectAsState()
  val otaSettings by viewModel.otaSettings.collectAsState()
  val otaState by viewModel.otaState.collectAsState()
  val isaacPosSettings by viewModel.isaacPosSettings.collectAsState()
  val isaacPosState by viewModel.isaacPosState.collectAsState()
  val isaacPosProductos by viewModel.isaacPosProductos.collectAsState()
  val historialPrecios by viewModel.historialPrecios.collectAsState()
  val pricingSettings by viewModel.pricingSettings.collectAsState()
  val bcvRefreshing by viewModel.bcvRefreshing.collectAsState()

  val pricingSummary = remember(
    proveedores,
    productos,
    catalogos,
    isaacPosProductos,
    historialPrecios,
    pricingSettings,
  ) {
    PricingIntelligenceEngine.analyze(
      proveedores = proveedores.map { it.proveedor },
      productos = productos,
      catalogos = catalogos,
      posProducts = isaacPosProductos,
      historialPrecios = historialPrecios,
      settings = pricingSettings,
    )
  }

  val snackbarHostState = remember { SnackbarHostState() }
  var whatsAppDialogPedido by remember { mutableStateOf<Pair<Pedido, Boolean>?>(null) }
  var showAiSettingsDialog by remember { mutableStateOf(false) }
  var showOtaDialog by remember { mutableStateOf(false) }

  val otaAvailableTag = when (val s = otaState) {
    is OtaUiState.UpdateAvailable -> s.release.tagName
    is OtaUiState.ReadyToInstall -> s.release.tagName
    else -> null
  }

  LaunchedEffect(snackbarMessage) {
    snackbarMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearMessage()
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { outerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(outerPadding),
    ) {
      NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
          entry<HomeRoute> {
            Scaffold(
              bottomBar = {
                Column {
                  HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                  NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                  ) {
                    NavigationBarItem(
                      selected = selectedTab == TAB_PROVEEDORES,
                      onClick = { selectedTab = TAB_PROVEEDORES },
                      icon = { Icon(Icons.Rounded.Storefront, contentDescription = null) },
                      label = { Text("Inicio") },
                    )
                    NavigationBarItem(
                      selected = selectedTab == TAB_PRECIOS,
                      onClick = { selectedTab = TAB_PRECIOS },
                      icon = { Icon(Icons.AutoMirrored.Rounded.CompareArrows, contentDescription = null) },
                      label = { Text("Compras") },
                    )
                    NavigationBarItem(
                      selected = selectedTab == TAB_NUEVO_PEDIDO,
                      onClick = { selectedTab = TAB_NUEVO_PEDIDO },
                      icon = {
                        BadgedBox(
                          badge = {
                            if (orderDraft.itemCount > 0) {
                              Badge { Text(orderDraft.itemCount.toString()) }
                            }
                          }
                        ) {
                          Icon(Icons.Rounded.ShoppingCartCheckout, contentDescription = null)
                        }
                      },
                      label = { Text("Pedir") },
                    )
                    NavigationBarItem(
                      selected = selectedTab == TAB_HISTORIAL,
                      onClick = { selectedTab = TAB_HISTORIAL },
                      icon = { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null) },
                      label = { Text("Pedidos") },
                    )
                  }
                }
              },
            ) { homePadding ->
              when (selectedTab) {
                TAB_PROVEEDORES -> ProveedoresScreen(
                  proveedores = proveedores,
                  aiConfigured = aiSettings.isConfigured,
                  isaacPosSyncedCount = isaacPosProductos.size,
                  isaacPosWithStockCount = isaacPosProductos.count { it.stockActual > 0.0 },
                  otaAvailableTag = otaAvailableTag,
                  themeMode = themeMode,
                  onCycleTheme = onCycleTheme,
                  onOpenAiSettings = {
                    viewModel.resetAiTestFeedback()
                    showAiSettingsDialog = true
                  },
                  onOpenOtaDialog = { showOtaDialog = true },
                  onOpenIsaacPos = { backStack.add(IsaacPosSyncRoute) },
                  onSaveProveedor = { prov ->
                    viewModel.saveProveedor(prov) { newId ->
                      if (prov.id == 0L) {
                        backStack.add(ProveedorDetalleRoute(newId))
                      }
                    }
                  },
                  onOpenProveedor = { id -> backStack.add(ProveedorDetalleRoute(id)) },
                  onStartOrder = { id ->
                    viewModel.startOrderForProveedor(id)
                    selectedTab = TAB_NUEVO_PEDIDO
                  },
                  modifier = Modifier.padding(homePadding),
                )

                TAB_PRECIOS -> PreciosInteligenciaScreen(
                  summary = pricingSummary,
                  pricingSettings = pricingSettings,
                  lowStockThreshold = isaacPosSettings.lowStockThreshold,
                  bcvRefreshing = bcvRefreshing,
                  onFetchBcvRate = { viewModel.fetchOfficialBcvRate(silent = false) },
                  onSavePricingSettings = { rate, src, iva, minSave ->
                    viewModel.savePricingSettings(rate, src, iva, minSave)
                  },
                  onUpdateMinSavingsPercent = { viewModel.updateMinOptimalSavingsPercent(it) },
                  onUpdateProductPackTax = { prodId, units, gravado ->
                    viewModel.updateProductoPricingOverrides(prodId, units, gravado)
                  },
                  onCreateOptimalDrafts = { plan ->
                    viewModel.createDraftOrdersFromOptimalPlan(plan) { created ->
                      if (created > 0) {
                        selectedTab = TAB_HISTORIAL
                      }
                    }
                  },
                  modifier = Modifier.padding(homePadding),
                )

                TAB_NUEVO_PEDIDO -> {
                  val previewPedido = remember(orderDraft, proveedores, productos) {
                    viewModel.buildCurrentPedido(PedidoEstado.BORRADOR)
                  }
                  NuevoPedidoScreen(
                    proveedores = proveedores,
                    allProductos = productos,
                    allCatalogos = catalogos,
                    draft = orderDraft,
                    currentPedidoPreview = previewPedido,
                    lowStockThreshold = isaacPosSettings.lowStockThreshold,
                    onSelectProveedor = { viewModel.startOrderForProveedor(it) },
                    onIncrementProduct = { viewModel.incrementProduct(it) },
                    onDecrementProduct = { viewModel.decrementProduct(it) },
                    onSetProductQuantityAndPrice = { prod, qty, price, saveToCatalog ->
                      viewModel.setProductQuantityAndPrice(prod, qty, price, saveToCatalog)
                    },
                    onAddManualItem = { n, c, u, p -> viewModel.addManualItem(n, c, u, p) },
                    onRemoveManualItem = { viewModel.removeManualItem(it) },
                    onNotesChange = { viewModel.setOrderNotes(it) },
                    onClearDraft = { viewModel.clearOrderDraft() },
                    onOpenProveedorCatalog = { id -> backStack.add(ProveedorDetalleRoute(id)) },
                    onOpenPdf = { ruta, titulo -> backStack.add(VisorPdfRoute(ruta, titulo)) },
                    onSaveDraft = {
                      viewModel.saveCurrentOrder(PedidoEstado.BORRADOR) {
                        selectedTab = TAB_HISTORIAL
                      }
                    },
                    onOpenWhatsAppPreview = {
                      val pedido = viewModel.buildCurrentPedido(PedidoEstado.ENVIADO)
                      if (pedido != null) {
                        whatsAppDialogPedido = pedido to true
                      } else {
                        viewModel.showMessage("Agrega al menos un producto al pedido")
                      }
                    },
                    onGoToProveedores = { selectedTab = TAB_PROVEEDORES },
                    onPrefillLowStock = { viewModel.prefillLowStockForCurrentProveedor() },
                    modifier = Modifier.padding(homePadding),
                  )
                }

                TAB_HISTORIAL -> HistorialPedidosScreen(
                  pedidos = pedidos,
                  onOpenPedido = { id -> backStack.add(PedidoDetalleRoute(id)) },
                  modifier = Modifier.padding(homePadding),
                )
              }
            }
          }

          entry<ProveedorDetalleRoute> { route ->
            val proveedor = proveedores.firstOrNull { it.proveedor.id == route.proveedorId }?.proveedor
            val provCatalogs = catalogos.filter { it.proveedorId == route.proveedorId }
            val provProducts = productos.filter { it.proveedorId == route.proveedorId }

            ProveedorDetalleScreen(
              proveedor = proveedor,
              catalogos = provCatalogs,
              productos = provProducts,
              onBack = { backStack.removeLastOrNull() },
              onUpdateProveedor = { viewModel.saveProveedor(it) },
              onDeleteProveedor = {
                viewModel.deleteProveedor(route.proveedorId) {
                  backStack.removeLastOrNull()
                }
              },
              onPickCatalogFile = { uri ->
                viewModel.startCatalogImport(route.proveedorId, uri)
                backStack.add(ImportarCatalogoRoute(route.proveedorId))
              },
              onPickCatalogImages = { uris ->
                viewModel.startImagesImport(route.proveedorId, uris)
                backStack.add(ImportarCatalogoRoute(route.proveedorId))
              },
              onPickCatalogText = { rawText ->
                viewModel.startPlainTextImport(route.proveedorId, rawText)
                backStack.add(ImportarCatalogoRoute(route.proveedorId))
              },
              onOpenPdf = { ruta, titulo -> backStack.add(VisorPdfRoute(ruta, titulo)) },
              onOpenImages = { rutas, titulo -> backStack.add(VisorImagenRoute(rutas, titulo)) },
              onOpenText = { ruta, titulo -> backStack.add(VisorTextoRoute(ruta, titulo)) },
              onDeleteCatalogo = { catId, delProds -> viewModel.deleteCatalogo(catId, delProds) },
              onUpdateCatalogoPricing = { catId, moneda, tasaFija, modoIva, ivaPct ->
                viewModel.updateCatalogPricingConfig(catId, moneda, tasaFija, modoIva, ivaPct)
              },
              globalRateBs = pricingSettings.tasaBsPorUsd,
              onSaveProducto = { prod, photoUri -> viewModel.saveProducto(prod, photoUri) },
              onDeleteProducto = { viewModel.deleteProducto(it) },
              onStartOrder = {
                viewModel.startOrderForProveedor(route.proveedorId)
                selectedTab = TAB_NUEVO_PEDIDO
                while (backStack.size > 1) {
                  backStack.removeLastOrNull()
                }
              },
              onOpenConciliar = { backStack.add(ConciliarCatalogoRoute(route.proveedorId)) },
            )
          }

          entry<ImportarCatalogoRoute> { route ->
            ImportarCatalogoScreen(
              state = importState,
              aiConfigured = aiSettings.isConfigured,
              globalRateBs = pricingSettings.tasaBsPorUsd,
              onToggleItem = { viewModel.toggleImportedItem(it) },
              onSelectAll = { viewModel.selectAllImported(it) },
              onUpdateItem = { viewModel.updateImportedItem(it) },
              onSetImportMode = { viewModel.setImportMode(it) },
              onSetPricingConfig = { moneda, tasaFija, modoIva, ivaPct ->
                viewModel.setImportPricingConfig(moneda, tasaFija, modoIva, ivaPct)
              },
              onOpenPdfPreview = { ruta, titulo -> backStack.add(VisorPdfRoute(ruta, titulo)) },
              onOpenImagesPreview = { rutas, titulo -> backStack.add(VisorImagenRoute(rutas, titulo)) },
              onOpenTextPreview = { ruta, titulo -> backStack.add(VisorTextoRoute(ruta, titulo)) },
              onExtractWithAi = { viewModel.reExtractCurrentWithGemini() },
              onOpenAiSettings = {
                viewModel.resetAiTestFeedback()
                showAiSettingsDialog = true
              },
              onConfirmImport = {
                viewModel.confirmCatalogImport { saveRes ->
                  backStack.removeLastOrNull()
                  if (saveRes.unlinkedCount > 0 && isaacPosProductos.isNotEmpty()) {
                    backStack.add(ConciliarCatalogoRoute(route.proveedorId))
                  }
                }
              },
              onCancel = {
                viewModel.cancelCatalogImport()
                backStack.removeLastOrNull()
              },
            )
          }

          entry<ConciliarCatalogoRoute> { route ->
            val proveedor = proveedores.firstOrNull { it.proveedor.id == route.proveedorId }?.proveedor
            val provCatalogs = catalogos.filter { it.proveedorId == route.proveedorId }
            val provProducts = productos.filter { it.proveedorId == route.proveedorId }

            ConciliarCatalogoScreen(
              proveedor = proveedor,
              catalogos = provCatalogs,
              providerProducts = provProducts,
              allPosProducts = isaacPosProductos,
              lowStockThreshold = isaacPosSettings.lowStockThreshold,
              onBack = { backStack.removeLastOrNull() },
              onOpenIsaacPosSync = { backStack.add(IsaacPosSyncRoute) },
              onApproveLinks = { links -> viewModel.reconcileCatalogLinks(route.proveedorId, links) },
              onUnlinkProduct = { localId -> viewModel.unlinkCatalogProductFromPos(localId) },
              onAddPosOnlyToProvider = { pos -> viewModel.assignIsaacPosProductsToProveedor(route.proveedorId, listOf(pos)) },
              onStartOrderWithLowStock = {
                viewModel.startOrderForProveedor(route.proveedorId)
                viewModel.prefillLowStockForCurrentProveedor()
                selectedTab = TAB_NUEVO_PEDIDO
                while (backStack.size > 1) {
                  backStack.removeLastOrNull()
                }
              },
            )
          }

          entry<VisorPdfRoute> { route ->
            VisorPdfScreen(
              rutaLocal = route.rutaLocal,
              titulo = route.titulo,
              onBack = { backStack.removeLastOrNull() },
            )
          }

          entry<VisorImagenRoute> { route ->
            VisorImagenScreen(
              rutas = route.rutas,
              titulo = route.titulo,
              onBack = { backStack.removeLastOrNull() },
            )
          }

          entry<VisorTextoRoute> { route ->
            VisorTextoScreen(
              rutaLocal = route.rutaLocal,
              titulo = route.titulo,
              onCopyText = { txt ->
                WhatsAppSender.copyToClipboard(context, txt)
                viewModel.showMessage("Texto copiado al portapapeles")
              },
              onBack = { backStack.removeLastOrNull() },
            )
          }

          entry<IsaacPosSyncRoute> {
            IsaacPosSyncScreen(
              settings = isaacPosSettings,
              syncState = isaacPosState,
              posProducts = isaacPosProductos,
              localProveedores = proveedores,
              localProductos = productos,
              onBack = { backStack.removeLastOrNull() },
              onSaveSettings = { url, key, low -> viewModel.saveIsaacPosSettings(url, key, low) },
              onTestConnection = { url, key -> viewModel.testIsaacPosConnection(url, key) },
              onSyncNow = { viewModel.syncIsaacPosNow() },
              onAssignToProveedor = { provId, items -> viewModel.assignIsaacPosProductsToProveedor(provId, items) },
              onCreateProveedorAndAssign = { prov, items -> viewModel.createProveedorAndAssignIsaacPos(prov, items) },
            )
          }

          entry<PedidoDetalleRoute> { route ->
            val pedido = pedidos.firstOrNull { it.id == route.pedidoId }
            PedidoDetalleScreen(
              pedido = pedido,
              onBack = { backStack.removeLastOrNull() },
              onUpdateEstado = { estado -> viewModel.updatePedidoEstado(route.pedidoId, estado) },
              onOpenWhatsAppPreview = { p -> whatsAppDialogPedido = p to false },
              onCopyOrderText = { p ->
                val text = WhatsAppOrderFormatter.format(p, p.incluirPrecios)
                WhatsAppSender.copyToClipboard(context, text)
                viewModel.showMessage("Pedido copiado al portapapeles")
              },
              onEditDraft = { p ->
                viewModel.loadPedidoIntoDraft(p, asDuplicate = false)
                selectedTab = TAB_NUEVO_PEDIDO
                while (backStack.size > 1) {
                  backStack.removeLastOrNull()
                }
              },
              onDuplicateOrder = { p ->
                viewModel.loadPedidoIntoDraft(p, asDuplicate = true)
                selectedTab = TAB_NUEVO_PEDIDO
                while (backStack.size > 1) {
                  backStack.removeLastOrNull()
                }
                viewModel.showMessage("Pedido duplicado en borrador")
              },
              onDelete = {
                viewModel.deletePedido(route.pedidoId) {
                  backStack.removeLastOrNull()
                }
              },
            )
          }
        },
      )
    }
  }

  if (showAiSettingsDialog) {
    AiSettingsDialog(
      currentSettings = aiSettings,
      testingConnection = aiTestRunning,
      testFeedback = aiTestFeedback,
      onTestConnection = { key, model -> viewModel.testGeminiConnection(key, model) },
      onSave = { key, model -> viewModel.saveAiSettings(key, model) },
      onClearKey = { viewModel.clearAiApiKey() },
      onDismiss = { showAiSettingsDialog = false },
    )
  }

  if (showOtaDialog) {
    OtaUpdateDialog(
      otaSettings = otaSettings,
      otaState = otaState,
      onSaveSettings = { slug, auto -> viewModel.saveOtaSettings(slug, auto) },
      onCheckNow = { slug -> viewModel.checkForOtaUpdate(repoSlugOverride = slug, silent = false) },
      onDownloadAndInstall = { viewModel.downloadAndInstallOta() },
      onInstallDownloaded = { viewModel.installDownloadedOta() },
      onDismiss = { showOtaDialog = false },
    )
  }

  pendingSharedPayload?.let { payload ->
    fun launchPayloadForProvider(provId: Long) {
      onConsumeSharedPayload()
      when (payload) {
        is PendingSharedPayload.SingleFile -> viewModel.startCatalogImport(provId, payload.stagedUri)
        is PendingSharedPayload.MultipleImages -> viewModel.startImagesImport(provId, payload.stagedUris)
        is PendingSharedPayload.PlainText -> viewModel.startPlainTextImport(provId, payload.text, payload.subject)
      }
      if (backStack.lastOrNull() != ProveedorDetalleRoute(provId)) {
        backStack.add(ProveedorDetalleRoute(provId))
      }
      backStack.add(ImportarCatalogoRoute(provId))
    }

    SharedProviderPickerDialog(
      payload = payload,
      proveedores = proveedores,
      onSelectProveedor = { provId -> launchPayloadForProvider(provId) },
      onCreateProveedorAndImport = { newProv ->
        viewModel.saveProveedor(newProv) { newId ->
          launchPayloadForProvider(newId)
        }
      },
      onDismiss = onConsumeSharedPayload,
    )
  }

  whatsAppDialogPedido?.let { (pedido, isFromDraft) ->
    WhatsAppPreviewDialog(
      pedido = pedido,
      onDismiss = { whatsAppDialogPedido = null },
      onCopy = { msg ->
        WhatsAppSender.copyToClipboard(context, msg)
        viewModel.showMessage("Mensaje copiado al portapapeles")
      },
      onSendWhatsApp = { msg, incluirPrecios ->
        whatsAppDialogPedido = null
        if (isFromDraft) {
          viewModel.setOrderIncludePrices(incluirPrecios)
          viewModel.saveCurrentOrder(PedidoEstado.ENVIADO) { saved ->
            val launched = WhatsAppSender.sendMessage(context, saved.proveedorTelefono, msg)
            if (!launched) {
              WhatsAppSender.copyToClipboard(context, msg)
              viewModel.showMessage("No se encontró app para compartir; el texto se copió al portapapeles")
            }
          }
        } else {
          if (pedido.estado == PedidoEstado.BORRADOR) {
            viewModel.updatePedidoEstado(pedido.id, PedidoEstado.ENVIADO)
          }
          val launched = WhatsAppSender.sendMessage(context, pedido.proveedorTelefono, msg)
          if (!launched) {
            WhatsAppSender.copyToClipboard(context, msg)
            viewModel.showMessage("No se encontró app para compartir; el texto se copió al portapapeles")
          }
        }
      },
    )
  }
}
