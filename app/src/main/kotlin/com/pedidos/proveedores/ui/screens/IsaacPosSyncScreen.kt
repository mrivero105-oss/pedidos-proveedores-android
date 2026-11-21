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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.data.local.IsaacPosSettings
import com.pedidos.proveedores.domain.model.IsaacPosProducto
import com.pedidos.proveedores.domain.model.Producto
import com.pedidos.proveedores.domain.model.Proveedor
import com.pedidos.proveedores.domain.model.ProveedorResumen
import com.pedidos.proveedores.domain.parser.PriceParser
import com.pedidos.proveedores.ui.IsaacPosSyncUiState
import com.pedidos.proveedores.ui.components.ProveedorFormDialog
import com.pedidos.proveedores.ui.theme.providerAvatarColor
import com.pedidos.proveedores.ui.theme.providerInitials
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PosStockFilter(val label: String) {
  TODOS("Todo stock"),
  CON_STOCK("Con stock (> 0)"),
  BAJO_STOCK("Bajo stock"),
  EN_CERO("En cero (0)"),
}

private data class PosProviderGroup(
  val providerName: String,
  val phone: String,
  val items: List<IsaacPosProducto>,
  val withStockCount: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsaacPosSyncScreen(
  settings: IsaacPosSettings,
  syncState: IsaacPosSyncUiState,
  posProducts: List<IsaacPosProducto>,
  localProveedores: List<ProveedorResumen>,
  localProductos: List<Producto>,
  onBack: () -> Unit,
  onSaveSettings: (url: String, apiKey: String, lowStock: Double) -> Unit,
  onTestConnection: (url: String, apiKey: String) -> Unit,
  onSyncNow: () -> Unit,
  onAssignToProveedor: (proveedorId: Long, products: List<IsaacPosProducto>) -> Unit,
  onCreateProveedorAndAssign: (proveedor: Proveedor, products: List<IsaacPosProducto>) -> Unit,
) {
  var showConfigDialog by remember { mutableStateOf(!settings.isConfigured) }
  var searchQuery by remember { mutableStateOf("") }
  var stockFilter by remember { mutableStateOf(PosStockFilter.TODOS) }
  var selectedProviderFilter by remember { mutableStateOf<String?>(null) }
  var selectedPosIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var showAssignDialog by remember { mutableStateOf(false) }
  var showCreateProviderDialog by remember { mutableStateOf(false) }

  // Mapa de productos POS ya vinculados a algún proveedor local
  val providerNameByPosId = remember(localProductos, localProveedores) {
    val provNameById = localProveedores.associate { it.proveedor.id to it.proveedor.nombre }
    localProductos
      .mapNotNull { prod ->
        val posId = prod.isaacPosId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val provName = provNameById[prod.proveedorId] ?: return@mapNotNull null
        posId to provName
      }
      .toMap()
  }

  val providerPhoneByNameLower = remember(localProveedores) {
    localProveedores
      .filter { it.proveedor.telefonoWhatsApp.isNotBlank() }
      .associate { it.proveedor.nombre.trim().lowercase() to it.proveedor.telefonoWhatsApp.trim() }
  }

  // Solo productos pertenecientes a algún proveedor (cero categorías sueltas sin proveedor)
  val providerProductsOnly = remember(posProducts, providerNameByPosId) {
    posProducts.filter { item ->
      providerNameByPosId.containsKey(item.productoId) || item.proveedorNombreRemoto.isNotBlank()
    }
  }

  val totalWithStock = remember(providerProductsOnly) { providerProductsOnly.count { it.stockActual > 0.0 } }

  // Lista de proveedores detectados para filtro directo en chips
  val detectedProviders = remember(providerProductsOnly, providerNameByPosId) {
    providerProductsOnly
      .mapNotNull { item ->
        providerNameByPosId[item.productoId] ?: item.proveedorNombreRemoto.takeIf { it.isNotBlank() }
      }
      .groupingBy { it }
      .eachCount()
      .entries
      .sortedByDescending { it.value }
      .map { it.key to it.value }
  }

  val filtered = remember(
    providerProductsOnly,
    searchQuery,
    stockFilter,
    selectedProviderFilter,
    providerNameByPosId,
    settings.lowStockThreshold,
  ) {
    providerProductsOnly.filter { item ->
      val assignedProv = providerNameByPosId[item.productoId] ?: item.proveedorNombreRemoto.takeIf { it.isNotBlank() }
      val matchesProviderChip = selectedProviderFilter == null ||
        (assignedProv != null && assignedProv.equals(selectedProviderFilter, ignoreCase = true))

      val matchesStock = when (stockFilter) {
        PosStockFilter.TODOS -> true
        PosStockFilter.CON_STOCK -> item.stockActual > 0.0
        PosStockFilter.BAJO_STOCK -> item.stockActual <= settings.lowStockThreshold
        PosStockFilter.EN_CERO -> item.stockActual <= 0.0
      }

      val matchesQuery = searchQuery.isBlank() || run {
        val q = searchQuery.trim().lowercase()
        item.nombre.lowercase().contains(q) ||
          item.codigoBarra.lowercase().contains(q) ||
          assignedProv.orEmpty().lowercase().contains(q)
      }
      matchesProviderChip && matchesStock && matchesQuery
    }
  }

  // Agrupación visual 100% por proveedor A-Z
  val groupedByProvider = remember(filtered, providerNameByPosId, providerPhoneByNameLower) {
    filtered
      .groupBy { item ->
        providerNameByPosId[item.productoId] ?: item.proveedorNombreRemoto.trim()
      }
      .entries
      .sortedBy { it.key.lowercase() }
      .map { (provName, items) ->
        val sortedItems = items.sortedWith(
          compareByDescending<IsaacPosProducto> { it.stockActual > 0.0 }
            .thenBy { it.nombre.lowercase() }
        )
        val phone = providerPhoneByNameLower[provName.lowercase()]
          ?: items.firstOrNull { it.proveedorTelefonoRemoto.isNotBlank() }?.proveedorTelefonoRemoto.orEmpty()
        PosProviderGroup(
          providerName = provName,
          phone = phone,
          items = sortedItems,
          withStockCount = items.count { it.stockActual > 0.0 },
        )
      }
  }

  val selectedItems = remember(providerProductsOnly, selectedPosIds) {
    providerProductsOnly.filter { it.productoId in selectedPosIds }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Proveedores de Isaac POS") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
          }
        },
        actions = {
          IconButton(onClick = { showConfigDialog = true }) {
            Icon(Icons.Rounded.Settings, contentDescription = "Configurar Supabase")
          }
        },
      )
    },
    bottomBar = {
      if (selectedPosIds.isNotEmpty()) {
        Surface(
          tonalElevation = 4.dp,
          shadowElevation = 8.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .navigationBarsPadding()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedButton(onClick = { selectedPosIds = emptySet() }) {
              Text("Limpiar (${selectedPosIds.size})")
            }
            Button(
              onClick = { showAssignDialog = true },
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Asignar a proveedor (${selectedPosIds.size})")
            }
          }
        }
      }
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp),
    ) {
      // Tarjeta de estado ejecutiva + botón Sincronizar
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          val lastSyncText = settings.lastSyncMillis?.let {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-ES")).format(Date(it))
          } ?: "Nunca sincronizado"

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (providerProductsOnly.isEmpty()) "Conecta tus proveedores de Isaac POS"
                else "${detectedProviders.size} proveedores · ${providerProductsOnly.size} productos",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = if (providerProductsOnly.isEmpty()) "Última sync: $lastSyncText"
                else "$totalWithStock con stock · Sync: $lastSyncText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Button(
              onClick = {
                if (settings.isConfigured) onSyncNow() else showConfigDialog = true
              },
              enabled = syncState !is IsaacPosSyncUiState.Syncing,
            ) {
              if (syncState is IsaacPosSyncUiState.Syncing) {
                CircularProgressIndicator(
                  modifier = Modifier.size(16.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onPrimary,
                )
              } else {
                Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
              }
              Spacer(Modifier.width(6.dp))
              Text(if (settings.isConfigured) "Sincronizar" else "Configurar")
            }
          }

          when (syncState) {
            is IsaacPosSyncUiState.Syncing -> {
              Text(
                text = syncState.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            is IsaacPosSyncUiState.LastResult -> {
              val s = syncState.summary
              val summaryParts = buildList {
                if (s.proveedoresNuevosDesdeSupabase > 0) add("Proveedores nuevos: ${s.proveedoresNuevosDesdeSupabase}")
                if (s.productosNuevosDesdeSupabase > 0) add("Importados: ${s.productosNuevosDesdeSupabase}")
                add("Actualizados: ${s.productosActualizadosLocales}")
                add("Bajo stock: ${s.enBajoStock}")
              }.joinToString(" · ")
              Text(
                text = summaryParts,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            is IsaacPosSyncUiState.Error -> {
              Text(
                text = syncState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
            IsaacPosSyncUiState.Idle -> Unit
          }
        }
      }

      if (providerProductsOnly.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(24.dp),
          ) {
            Icon(
              Icons.Rounded.CloudSync,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              "Aún no has sincronizado proveedores de Isaac POS",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              "Configura la clave Publishable de tu proyecto Supabase y pulsa Sincronizar para traer únicamente tus proveedores activos y sus productos (sin categorías sueltas).",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { showConfigDialog = true }) {
              Text("Configurar conexión Supabase")
            }
          }
        }
      } else {
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Buscar proveedor, producto o código...") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = if (searchQuery.isNotEmpty()) {
            {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Rounded.Close, contentDescription = "Limpiar")
              }
            }
          } else null,
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        // Fila 1: Filtro de stock para pedidos
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          items(PosStockFilter.entries) { sFilter ->
            val label = if (sFilter == PosStockFilter.BAJO_STOCK) {
              "Bajo stock (≤ ${PriceParser.formatQuantity(settings.lowStockThreshold)})"
            } else {
              sFilter.label
            }
            FilterChip(
              selected = stockFilter == sFilter,
              onClick = { stockFilter = sFilter },
              leadingIcon = if (sFilter == PosStockFilter.BAJO_STOCK && stockFilter == sFilter) {
                { Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(16.dp)) }
              } else null,
              label = { Text(label) },
            )
          }
        }

        // Fila 2: Chips de proveedores de Isaac POS (sin categorías de productos)
        if (detectedProviders.isNotEmpty()) {
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp),
          ) {
            item {
              FilterChip(
                selected = selectedProviderFilter == null,
                onClick = { selectedProviderFilter = null },
                leadingIcon = { Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Todos prov. (${detectedProviders.size})") },
              )
            }
            items(detectedProviders) { (provName, count) ->
              FilterChip(
                selected = selectedProviderFilter.equals(provName, ignoreCase = true),
                onClick = {
                  selectedProviderFilter = if (selectedProviderFilter.equals(provName, ignoreCase = true)) null else provName
                },
                label = {
                  Text(
                    text = "$provName ($count)",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Mostrando ${filtered.size} producto(s) de ${groupedByProvider.size} proveedor(es)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (filtered.isNotEmpty()) {
            val allVisibleSelected = filtered.all { it.productoId in selectedPosIds }
            TextButton(
              onClick = {
                selectedPosIds = if (allVisibleSelected) {
                  selectedPosIds - filtered.map { it.productoId }.toSet()
                } else {
                  selectedPosIds + filtered.map { it.productoId }.toSet()
                }
              }
            ) {
              Text(if (allVisibleSelected) "Desmarcar visibles" else "Marcar visibles")
            }
          }
        }

        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(bottom = 24.dp),
        ) {
          groupedByProvider.forEach { group ->
            item(key = "header_${group.providerName}") {
              val groupIds = group.items.map { it.productoId }.toSet()
              val allGroupSelected = groupIds.isNotEmpty() && groupIds.all { it in selectedPosIds }
              val groupAccent = providerAvatarColor(group.providerName)
              Surface(
                color = groupAccent.copy(alpha = 0.11f),
                border = BorderStroke(1.dp, groupAccent.copy(alpha = 0.28f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 4.dp),
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Surface(
                      color = groupAccent.copy(alpha = 0.2f),
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier.size(30.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text(
                          text = providerInitials(group.providerName),
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = FontWeight.Bold,
                          color = groupAccent,
                        )
                      }
                    }
                    Column {
                      Text(
                        text = group.providerName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      val subtitle = buildList {
                        add("${group.items.size} prod.")
                        add("${group.withStockCount} con stock")
                        if (group.phone.isNotBlank()) add("WhatsApp: ${group.phone}")
                      }.joinToString(" · ")
                      Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                  TextButton(
                    onClick = {
                      selectedPosIds = if (allGroupSelected) {
                        selectedPosIds - groupIds
                      } else {
                        selectedPosIds + groupIds
                      }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                  ) {
                    Text(if (allGroupSelected) "Quitar" else "Marcar")
                  }
                }
              }
            }

            items(group.items, key = { "${group.providerName}_${it.productoId}" }) { item ->
              val isChecked = item.productoId in selectedPosIds
              val isLow = item.stockActual <= settings.lowStockThreshold

              Card(
                shape = RoundedCornerShape(11.dp),
                colors = CardDefaults.cardColors(
                  containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                  else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                  width = if (isChecked) 1.5.dp else 1.dp,
                  color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    selectedPosIds = if (isChecked) selectedPosIds - item.productoId else selectedPosIds + item.productoId
                  },
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Checkbox(
                    checked = isChecked,
                    onCheckedChange = { checked ->
                      selectedPosIds = if (checked) selectedPosIds + item.productoId else selectedPosIds - item.productoId
                    },
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = item.nombre,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.Medium,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                    val purchaseMeta = buildList {
                      if (item.unidadPedido.isNotBlank() && !item.unidadPedido.equals("u", ignoreCase = true)) {
                        add(item.unidadPedido)
                      }
                      item.costoUnitario?.let { add("Costo: ${PriceParser.formatCurrency(it)}") }
                    }.joinToString(" · ")
                    if (purchaseMeta.isNotBlank()) {
                      Text(
                        text = purchaseMeta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                    if (item.codigoBarra.isNotBlank()) {
                      Text(
                        text = "Cód: ${item.codigoBarra}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                  Spacer(Modifier.width(6.dp))
                  StockPosBadge(stock = item.stockActual, isLow = isLow)
                }
              }
            }
          }
        }
      }
    }
  }

  if (showConfigDialog) {
    IsaacPosConfigDialog(
      currentSettings = settings,
      testFeedback = (syncState as? IsaacPosSyncUiState.Error)?.message,
      onTest = onTestConnection,
      onSave = { url, key, low ->
        onSaveSettings(url, key, low)
        showConfigDialog = false
      },
      onDismiss = { showConfigDialog = false },
    )
  }

  if (showAssignDialog) {
    AlertDialog(
      onDismissRequest = { showAssignDialog = false },
      title = { Text("Asignar ${selectedItems.size} producto(s) a...") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (localProveedores.isEmpty()) {
            Text(
              "Todavía no tienes proveedores creados. Crea uno ahora para asignarle estos productos.",
              style = MaterialTheme.typography.bodySmall,
            )
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              items(localProveedores, key = { it.proveedor.id }) { resumen ->
                Surface(
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      showAssignDialog = false
                      onAssignToProveedor(resumen.proveedor.id, selectedItems)
                      selectedPosIds = emptySet()
                    },
                ) {
                  Column(modifier = Modifier.padding(12.dp)) {
                    Text(resumen.proveedor.nombre, fontWeight = FontWeight.SemiBold)
                    Text(
                      "${resumen.cantidadProductos} productos actuales",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }
          OutlinedButton(
            onClick = {
              showAssignDialog = false
              showCreateProviderDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Crear proveedor nuevo y asignar")
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showAssignDialog = false }) { Text("Cancelar") }
      },
    )
  }

  if (showCreateProviderDialog) {
    ProveedorFormDialog(
      onDismiss = { showCreateProviderDialog = false },
      onSave = { prov ->
        showCreateProviderDialog = false
        onCreateProveedorAndAssign(prov, selectedItems)
        selectedPosIds = emptySet()
      },
    )
  }
}

@Composable
fun StockPosBadge(stock: Double, isLow: Boolean) {
  val (bg, fg) = when {
    stock <= 0.0 -> Color(0xFFDC2626).copy(alpha = 0.14f) to Color(0xFFDC2626)
    isLow -> Color(0xFFD97706).copy(alpha = 0.16f) to Color(0xFFB45309)
    else -> Color(0xFF16A34A).copy(alpha = 0.14f) to Color(0xFF15803D)
  }
  Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
    Text(
      text = "Stock ${PriceParser.formatQuantity(stock)}",
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = fg,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
private fun IsaacPosConfigDialog(
  currentSettings: IsaacPosSettings,
  testFeedback: String?,
  onTest: (url: String, apiKey: String) -> Unit,
  onSave: (url: String, apiKey: String, lowStock: Double) -> Unit,
  onDismiss: () -> Unit,
) {
  var url by remember(currentSettings.supabaseUrl) { mutableStateOf(currentSettings.supabaseUrl) }
  var apiKey by remember(currentSettings.publishableKey) { mutableStateOf(currentSettings.publishableKey) }
  var lowStockText by remember(currentSettings.lowStockThreshold) {
    mutableStateOf(PriceParser.formatQuantity(currentSettings.lowStockThreshold))
  }
  var showKey by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Conexión Isaac POS (Supabase)") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Conexión de solo lectura mediante clave Publishable o Anon (nunca uses la clave service_role en el teléfono).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          label = { Text("URL del proyecto Supabase") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = apiKey,
          onValueChange = { apiKey = it },
          label = { Text("Clave Publishable / Anon") },
          placeholder = { Text("sb_publishable_... o eyJ...") },
          singleLine = true,
          visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(onClick = { showKey = !showKey }) {
              Icon(
                if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = null,
              )
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = lowStockText,
          onValueChange = { lowStockText = it },
          label = { Text("Alerta de bajo stock (≤)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
          onClick = { onTest(url, apiKey) },
          enabled = url.isNotBlank() && apiKey.isNotBlank(),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Probar conexión")
        }
        if (!testFeedback.isNullOrBlank()) {
          Text(
            text = testFeedback,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val threshold = PriceParser.parse(lowStockText) ?: 5.0
          onSave(url, apiKey, threshold)
        },
        enabled = url.isNotBlank(),
      ) {
        Text("Guardar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cerrar") }
    },
  )
}
