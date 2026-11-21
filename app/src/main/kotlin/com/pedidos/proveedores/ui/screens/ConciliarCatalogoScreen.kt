package com.pedidos.proveedores.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCartCheckout
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.domain.model.CatalogoArchivo
import com.pedidos.proveedores.domain.model.IsaacPosProducto
import com.pedidos.proveedores.domain.model.Producto
import com.pedidos.proveedores.domain.model.Proveedor
import com.pedidos.proveedores.domain.parser.PriceParser
import com.pedidos.proveedores.domain.reconcile.CatalogReconciler
import com.pedidos.proveedores.domain.reconcile.CatalogReconciliationLink
import com.pedidos.proveedores.domain.reconcile.ReconcileCandidate
import com.pedidos.proveedores.domain.reconcile.ReconciledEntry
import com.pedidos.proveedores.domain.reconcile.SuggestedEntry
import com.pedidos.proveedores.ui.components.ProductThumbnail
import com.pedidos.proveedores.ui.theme.HealthyStockGreen
import com.pedidos.proveedores.ui.theme.LowStockAmber
import com.pedidos.proveedores.ui.theme.ZeroStockRed

private enum class ReconcileTab(val label: String) {
  SUGERIDOS("Sugeridos"),
  VINCULADOS("Vinculados"),
  SOLO_CATALOGO("Solo catálogo"),
  SOLO_POS("Solo mi POS"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConciliarCatalogoScreen(
  proveedor: Proveedor?,
  catalogos: List<CatalogoArchivo>,
  providerProducts: List<Producto>,
  allPosProducts: List<IsaacPosProducto>,
  lowStockThreshold: Double,
  onBack: () -> Unit,
  onOpenIsaacPosSync: () -> Unit,
  onApproveLinks: (List<CatalogReconciliationLink>) -> Unit,
  onUnlinkProduct: (Long) -> Unit,
  onAddPosOnlyToProvider: (IsaacPosProducto) -> Unit,
  onStartOrderWithLowStock: () -> Unit,
) {
  if (proveedor == null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Proveedor no encontrado")
    }
    return
  }

  // Si el proveedor tiene catálogos subidos, seleccionar por defecto el más reciente para conciliarlo directo
  var selectedCatalogId by remember(catalogos) {
    mutableStateOf<Long?>(catalogos.firstOrNull()?.id)
  }
  var searchQuery by remember { mutableStateOf("") }
  var pickingForProduct by remember { mutableStateOf<Pair<Producto, List<ReconcileCandidate>>?>(null) }

  val targetCatalogProducts = remember(providerProducts, selectedCatalogId) {
    if (selectedCatalogId == null) providerProducts
    else providerProducts.filter { it.catalogoId == selectedCatalogId }
  }

  val report = remember(proveedor.nombre, targetCatalogProducts, allPosProducts, providerProducts) {
    CatalogReconciler.reconcile(
      providerName = proveedor.nombre,
      catalogProducts = targetCatalogProducts,
      allPosProducts = allPosProducts,
      allProviderProducts = providerProducts,
    )
  }

  var activeTab by remember { mutableStateOf(ReconcileTab.SUGERIDOS) }
  var initializedTab by remember(selectedCatalogId) { mutableStateOf(false) }
  LaunchedEffect(report, initializedTab) {
    if (!initializedTab) {
      activeTab = when {
        report.sugeridos.isNotEmpty() -> ReconcileTab.SUGERIDOS
        report.soloEnCatalogo.isNotEmpty() -> ReconcileTab.SOLO_CATALOGO
        report.vinculados.isNotEmpty() -> ReconcileTab.VINCULADOS
        else -> ReconcileTab.SOLO_POS
      }
      initializedTab = true
    }
  }

  val linkedLowStockCount = remember(report.vinculados, lowStockThreshold) {
    report.vinculados.count { it.posProduct.stockActual <= lowStockThreshold }
  }

  val queryLower = searchQuery.trim().lowercase()

  val filteredSugeridos = remember(report.sugeridos, queryLower) {
    if (queryLower.isBlank()) report.sugeridos
    else report.sugeridos.filter {
      it.localProduct.nombre.lowercase().contains(queryLower) ||
        it.localProduct.codigo.lowercase().contains(queryLower) ||
        it.best.posProduct.nombre.lowercase().contains(queryLower)
    }
  }

  val filteredVinculados = remember(report.vinculados, queryLower) {
    if (queryLower.isBlank()) report.vinculados
    else report.vinculados.filter {
      it.localProduct.nombre.lowercase().contains(queryLower) ||
        it.localProduct.codigo.lowercase().contains(queryLower) ||
        it.posProduct.nombre.lowercase().contains(queryLower)
    }
  }

  val filteredSoloCatalogo = remember(report.soloEnCatalogo, queryLower) {
    if (queryLower.isBlank()) report.soloEnCatalogo
    else report.soloEnCatalogo.filter {
      it.nombre.lowercase().contains(queryLower) ||
        it.codigo.lowercase().contains(queryLower) ||
        it.categoria.lowercase().contains(queryLower)
    }
  }

  val filteredSoloPos = remember(report.soloEnPos, queryLower) {
    if (queryLower.isBlank()) report.soloEnPos
    else report.soloEnPos.filter {
      it.nombre.lowercase().contains(queryLower) ||
        it.codigoBarra.lowercase().contains(queryLower) ||
        it.categoria.lowercase().contains(queryLower)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Conciliar con Isaac POS", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              text = proveedor.nombre,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
          }
        },
        actions = {
          IconButton(onClick = onOpenIsaacPosSync) {
            Icon(Icons.Rounded.CloudSync, contentDescription = "Sincronizar Isaac POS")
          }
        },
      )
    },
    bottomBar = {
      if (allPosProducts.isNotEmpty() && report.vinculados.isNotEmpty()) {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 2.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Button(
                onClick = onStartOrderWithLowStock,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.Rounded.ShoppingCartCheckout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                  text = if (linkedLowStockCount > 0) {
                    "Pedir productos en bajo stock ($linkedLowStockCount)"
                  } else {
                    "Ir a armar pedido con este proveedor"
                  }
                )
              }
            }
          }
        }
      }
    },
  ) { innerPadding ->
    if (allPosProducts.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              Icons.Rounded.CloudSync,
              contentDescription = null,
              modifier = Modifier.size(44.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              "Sincroniza primero tu Isaac POS",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              "Necesitas descargar tu inventario de Isaac POS (Supabase) para compararlo contra el catálogo de ${proveedor.nombre}.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenIsaacPosSync, modifier = Modifier.fillMaxWidth()) {
              Text("Abrir sincronización Isaac POS")
            }
          }
        }
      }
      return@Scaffold
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp),
    ) {
      // Selector de catálogo subido vs todos los productos del proveedor
      if (catalogos.isNotEmpty()) {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(bottom = 6.dp),
        ) {
          items(catalogos, key = { it.id }) { cat ->
            FilterChip(
              selected = selectedCatalogId == cat.id,
              onClick = { selectedCatalogId = cat.id },
              label = {
                Text(
                  text = "Catálogo: ${cat.nombreArchivo}",
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
            )
          }
          item {
            FilterChip(
              selected = selectedCatalogId == null,
              onClick = { selectedCatalogId = null },
              label = { Text("Todo el proveedor (${providerProducts.size})") },
            )
          }
        }
      }

      // 4 pestañas resumen clicables
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 8.dp),
      ) {
        item {
          FilterChip(
            selected = activeTab == ReconcileTab.SUGERIDOS,
            onClick = { activeTab = ReconcileTab.SUGERIDOS },
            label = { Text("${ReconcileTab.SUGERIDOS.label} (${report.sugeridos.size})") },
          )
        }
        item {
          FilterChip(
            selected = activeTab == ReconcileTab.VINCULADOS,
            onClick = { activeTab = ReconcileTab.VINCULADOS },
            label = { Text("${ReconcileTab.VINCULADOS.label} (${report.vinculados.size})") },
          )
        }
        item {
          FilterChip(
            selected = activeTab == ReconcileTab.SOLO_CATALOGO,
            onClick = { activeTab = ReconcileTab.SOLO_CATALOGO },
            label = { Text("${ReconcileTab.SOLO_CATALOGO.label} (${report.soloEnCatalogo.size})") },
          )
        }
        item {
          FilterChip(
            selected = activeTab == ReconcileTab.SOLO_POS,
            onClick = { activeTab = ReconcileTab.SOLO_POS },
            label = { Text("${ReconcileTab.SOLO_POS.label} (${report.soloEnPos.size})") },
          )
        }
      }

      // Buscador rápido dentro de la pestaña activa
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Filtrar producto o código...") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = if (searchQuery.isNotEmpty()) {
          {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Rounded.Close, contentDescription = "Limpiar", modifier = Modifier.size(18.dp))
            }
          }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      )

      Spacer(Modifier.height(8.dp))

      // Banner de aprobación masiva en 1 toque para sugerencias
      if (activeTab == ReconcileTab.SUGERIDOS && filteredSugeridos.isNotEmpty()) {
        val highConfidence = filteredSugeridos.filter { it.best.score >= 0.72 }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilledTonalButton(
            onClick = {
              val links = filteredSugeridos.map {
                CatalogReconciliationLink(localProductId = it.localProduct.id, posProduct = it.best.posProduct)
              }
              onApproveLinks(links)
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Aprobar todos (${filteredSugeridos.size})")
          }
          if (highConfidence.isNotEmpty() && highConfidence.size < filteredSugeridos.size) {
            OutlinedButton(
              onClick = {
                val links = highConfidence.map {
                  CatalogReconciliationLink(localProductId = it.localProduct.id, posProduct = it.best.posProduct)
                }
                onApproveLinks(links)
              },
              shape = RoundedCornerShape(10.dp),
            ) {
              Text("Seguros (${highConfidence.size})")
            }
          }
        }
      }

      // Lista principal según pestaña activa
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
      ) {
        when (activeTab) {
          ReconcileTab.SUGERIDOS -> {
            if (filteredSugeridos.isEmpty()) {
              item {
                EmptyReconcileNote(
                  title = "Sin sugerencias pendientes",
                  body = if (report.vinculados.isNotEmpty()) {
                    "Ya tienes ${report.vinculados.size} producto(s) vinculados con tu Isaac POS. Revisa las pestañas Vinculados o Solo catálogo."
                  } else {
                    "No se encontraron coincidencias automáticas adicionales. Puedes vincular manualmente desde la pestaña Solo catálogo."
                  },
                )
              }
            } else {
              items(filteredSugeridos, key = { "sug_${it.localProduct.id}" }) { entry ->
                SuggestedReconcileCard(
                  entry = entry,
                  lowStockThreshold = lowStockThreshold,
                  onApprove = {
                    onApproveLinks(
                      listOf(
                        CatalogReconciliationLink(
                          localProductId = entry.localProduct.id,
                          posProduct = entry.best.posProduct,
                        )
                      )
                    )
                  },
                  onPickAnother = {
                    pickingForProduct = entry.localProduct to entry.candidates
                  },
                )
              }
            }
          }

          ReconcileTab.VINCULADOS -> {
            if (filteredVinculados.isEmpty()) {
              item {
                EmptyReconcileNote(
                  title = "Todavía no hay productos vinculados",
                  body = "Aprueba sugerencias o vincula manualmente tus productos del catálogo contra Isaac POS para ver su stock real aquí.",
                )
              }
            } else {
              items(filteredVinculados, key = { "vinc_${it.localProduct.id}" }) { entry ->
                LinkedReconcileCard(
                  entry = entry,
                  lowStockThreshold = lowStockThreshold,
                  onChangeLink = {
                    pickingForProduct = entry.localProduct to emptyList()
                  },
                  onUnlink = { onUnlinkProduct(entry.localProduct.id) },
                )
              }
            }
          }

          ReconcileTab.SOLO_CATALOGO -> {
            if (filteredSoloCatalogo.isEmpty()) {
              item {
                EmptyReconcileNote(
                  title = "Todos los productos tienen vínculo o sugerencia",
                  body = "No quedan productos sin emparejar en este catálogo.",
                )
              }
            } else {
              items(filteredSoloCatalogo, key = { "catonly_${it.id}" }) { prod ->
                CatalogOnlyCard(
                  producto = prod,
                  onManualLink = {
                    pickingForProduct = prod to emptyList()
                  },
                )
              }
            }
          }

          ReconcileTab.SOLO_POS -> {
            if (filteredSoloPos.isEmpty()) {
              item {
                EmptyReconcileNote(
                  title = "Sin faltantes en tu base de datos POS",
                  body = "Todos los productos que tienes en Isaac POS bajo ${proveedor.nombre} ya están conciliados en este catálogo.",
                )
              }
            } else {
              items(filteredSoloPos, key = { "posonly_${it.productoId}" }) { pos ->
                PosOnlyCard(
                  pos = pos,
                  lowStockThreshold = lowStockThreshold,
                  onAddToProvider = { onAddPosOnlyToProvider(pos) },
                )
              }
            }
          }
        }
      }
    }
  }

  pickingForProduct?.let { (localProd, suggestedCandidates) ->
    PosProductPickerDialog(
      localProduct = localProd,
      providerName = proveedor.nombre,
      suggestedCandidates = suggestedCandidates,
      allPosProducts = allPosProducts,
      lowStockThreshold = lowStockThreshold,
      onSelect = { chosenPos ->
        pickingForProduct = null
        onApproveLinks(listOf(CatalogReconciliationLink(localProductId = localProd.id, posProduct = chosenPos)))
      },
      onDismiss = { pickingForProduct = null },
    )
  }
}

@Composable
private fun SuggestedReconcileCard(
  entry: SuggestedEntry,
  lowStockThreshold: Double,
  onApprove: () -> Unit,
  onPickAnother: () -> Unit,
) {
  val local = entry.localProduct
  val best = entry.best
  val pos = best.posProduct
  val confColor = when {
    best.confidencePercent >= 85 -> HealthyStockGreen
    best.confidencePercent >= 65 -> MaterialTheme.colorScheme.primary
    else -> LowStockAmber
  }

  Card(
    shape = RoundedCornerShape(13.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      // Lado A: Catálogo del vendedor
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (!local.imagenRuta.isNullOrBlank()) {
          ProductThumbnail(imagePath = local.imagenRuta, contentDescription = local.nombre, size = 38.dp)
          Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "CATÁLOGO VENDEDOR",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = local.nombre,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          val catMeta = buildList {
            if (local.unidad.isNotBlank()) add(local.unidad)
            local.precio?.let { add("Precio cat.: ${PriceParser.formatCurrency(it)}") }
            if (local.codigo.isNotBlank()) add("Cód: ${local.codigo}")
          }.joinToString(" · ")
          if (catMeta.isNotBlank()) {
            Text(
              text = catMeta,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Badge de confianza y motivo
      Surface(
        color = confColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            Icons.AutoMirrored.Rounded.CompareArrows,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = confColor,
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = "${best.confidencePercent}% · ${best.reason}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = confColor,
          )
        }
      }

      // Lado B: Producto en tu Isaac POS
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "TU BASE DE DATOS ISAAC POS",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = pos.nombre,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            val posMeta = buildList {
              if (pos.unidadPedido.isNotBlank()) add(pos.unidadPedido)
              pos.costoUnitario?.let { add("Costo ant.: ${PriceParser.formatCurrency(it)}") }
              if (pos.codigoBarra.isNotBlank()) add("Cód: ${pos.codigoBarra}")
            }.joinToString(" · ")
            if (posMeta.isNotBlank()) {
              Text(
                text = posMeta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Spacer(Modifier.width(8.dp))
          StockPosBadge(stock = pos.stockActual, isLow = pos.stockActual <= lowStockThreshold)
        }
      }

      PriceDiffBanner(catalogPrice = local.precio, posCost = pos.costoUnitario)

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
          onClick = onPickAnother,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(0.45f),
        ) {
          Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Cambiar")
        }
        Button(
          onClick = onApprove,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(0.55f),
        ) {
          Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Aprobar vínculo")
        }
      }
    }
  }
}

@Composable
private fun LinkedReconcileCard(
  entry: ReconciledEntry,
  lowStockThreshold: Double,
  onChangeLink: () -> Unit,
  onUnlink: () -> Unit,
) {
  val local = entry.localProduct
  val pos = entry.posProduct

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (!local.imagenRuta.isNullOrBlank()) {
          ProductThumbnail(imagePath = local.imagenRuta, contentDescription = local.nombre, size = 38.dp)
          Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = local.nombre,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "POS: ${pos.nombre}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          val meta = buildList {
            if (local.unidad.isNotBlank()) add(local.unidad)
            local.precio?.let { add("Costo lista: ${PriceParser.formatCurrency(it)}") }
            pos.costoUnitario?.let { add("Costo ant.: ${PriceParser.formatCurrency(it)}") }
          }.joinToString(" · ")
          if (meta.isNotBlank()) {
            Text(
              text = meta,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Spacer(Modifier.width(6.dp))
        StockPosBadge(stock = pos.stockActual, isLow = pos.stockActual <= lowStockThreshold)
      }

      PriceDiffBanner(catalogPrice = local.precio, posCost = pos.costoUnitario)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        TextButton(
          onClick = onChangeLink,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
          Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(Modifier.width(4.dp))
          Text("Cambiar")
        }
        TextButton(
          onClick = onUnlink,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
          Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(Modifier.width(4.dp))
          Text("Desvincular")
        }
      }
    }
  }
}

@Composable
private fun CatalogOnlyCard(
  producto: Producto,
  onManualLink: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (!producto.imagenRuta.isNullOrBlank()) {
        ProductThumbnail(imagePath = producto.imagenRuta, contentDescription = producto.nombre, size = 38.dp)
        Spacer(Modifier.width(8.dp))
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = producto.nombre,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        val meta = buildList {
          add(producto.unidad)
          producto.precio?.let { add(PriceParser.formatCurrency(it)) }
          if (producto.codigo.isNotBlank()) add("Cód: ${producto.codigo}")
        }.joinToString(" · ")
        Text(
          text = meta,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "No encontrado en tu Isaac POS (producto nuevo o con otro nombre)",
          style = MaterialTheme.typography.labelSmall,
          color = LowStockAmber,
        )
      }
      Spacer(Modifier.width(8.dp))
      FilledTonalButton(
        onClick = onManualLink,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
      ) {
        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Vincular")
      }
    }
  }
}

@Composable
private fun PosOnlyCard(
  pos: IsaacPosProducto,
  lowStockThreshold: Double,
  onAddToProvider: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = pos.nombre,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        val meta = buildList {
          if (pos.unidadPedido.isNotBlank()) add(pos.unidadPedido)
          pos.costoUnitario?.let { add("Último costo: ${PriceParser.formatCurrency(it)}") }
        }.joinToString(" · ")
        if (meta.isNotBlank()) {
          Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          text = "Existe en tu Isaac POS pero no aparece en el catálogo subido",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(Modifier.width(8.dp))
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StockPosBadge(stock = pos.stockActual, isLow = pos.stockActual <= lowStockThreshold)
        TextButton(
          onClick = onAddToProvider,
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(2.dp))
          Text("Sumar", style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}

@Composable
private fun PriceDiffBanner(catalogPrice: Double?, posCost: Double?) {
  if (catalogPrice == null || posCost == null || posCost <= 0.0) return
  val diff = catalogPrice - posCost
  if (kotlin.math.abs(diff) < 0.01) return
  val pct = (diff / posCost) * 100.0
  val isHigher = diff > 0.0
  val color = if (isHigher) ZeroStockRed else HealthyStockGreen
  val sign = if (isHigher) "+" else ""
  val label = if (isHigher) "Subió" else "Bajó"

  Surface(
    color = color.copy(alpha = 0.10f),
    shape = RoundedCornerShape(7.dp),
  ) {
    Text(
      text = "$label vs. costo POS (${PriceParser.formatCurrency(posCost)} → ${PriceParser.formatCurrency(catalogPrice)} · $sign${"%.1f".format(pct)}%)",
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      color = color,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

@Composable
private fun EmptyReconcileNote(title: String, body: String) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 12.dp),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun PosProductPickerDialog(
  localProduct: Producto,
  providerName: String,
  suggestedCandidates: List<ReconcileCandidate>,
  allPosProducts: List<IsaacPosProducto>,
  lowStockThreshold: Double,
  onSelect: (IsaacPosProducto) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by remember { mutableStateOf("") }

  val results = remember(query, allPosProducts, suggestedCandidates, providerName) {
    if (query.isBlank()) {
      val topPos = suggestedCandidates.map { it.posProduct }
      val topIds = topPos.map { it.productoId }.toSet()
      val extra = CatalogReconciler.searchPosProducts("", allPosProducts, providerName, limit = 30)
        .filter { it.productoId !in topIds }
      topPos + extra
    } else {
      CatalogReconciler.searchPosProducts(query, allPosProducts, providerName, limit = 40)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column {
        Text("Vincular producto con Isaac POS", style = MaterialTheme.typography.titleMedium)
        Text(
          text = localProduct.nombre,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Buscar en tus ${allPosProducts.size} productos POS...") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
          trailingIcon = if (query.isNotEmpty()) {
            {
              IconButton(onClick = { query = "" }) {
                Icon(Icons.Rounded.Close, contentDescription = "Limpiar", modifier = Modifier.size(16.dp))
              }
            }
          } else null,
          singleLine = true,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(results, key = { it.productoId }) { pos ->
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(pos) },
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = pos.nombre,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                  val sub = buildList {
                    if (pos.proveedorNombreRemoto.isNotBlank()) add(pos.proveedorNombreRemoto)
                    if (pos.codigoBarra.isNotBlank()) add(pos.codigoBarra)
                    pos.costoUnitario?.let { add("Costo ${PriceParser.formatCurrency(it)}") }
                  }.joinToString(" · ")
                  if (sub.isNotBlank()) {
                    Text(
                      text = sub,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
                Spacer(Modifier.width(6.dp))
                StockPosBadge(stock = pos.stockActual, isLow = pos.stockActual <= lowStockThreshold)
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Cerrar") }
    },
  )
}
