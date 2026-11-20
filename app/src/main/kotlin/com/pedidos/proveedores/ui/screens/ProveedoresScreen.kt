package com.pedidos.proveedores.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCartCheckout
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.domain.model.Proveedor
import com.pedidos.proveedores.domain.model.ProveedorResumen
import com.pedidos.proveedores.ui.components.ProveedorFormDialog

@Composable
fun ProveedoresScreen(
  proveedores: List<ProveedorResumen>,
  aiConfigured: Boolean,
  isaacPosSyncedCount: Int,
  otaAvailableTag: String?,
  onOpenAiSettings: () -> Unit,
  onOpenOtaDialog: () -> Unit,
  onOpenIsaacPos: () -> Unit,
  onSaveProveedor: (Proveedor) -> Unit,
  onOpenProveedor: (Long) -> Unit,
  onStartOrder: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  var searchQuery by remember { mutableStateOf("") }
  var showAddDialog by remember { mutableStateOf(false) }

  val filtered = remember(proveedores, searchQuery) {
    if (searchQuery.isBlank()) proveedores
    else {
      val q = searchQuery.trim().lowercase()
      proveedores.filter {
        it.proveedor.nombre.lowercase().contains(q) ||
          it.proveedor.telefonoWhatsApp.lowercase().contains(q) ||
          it.proveedor.notas.lowercase().contains(q)
      }
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = { showAddDialog = true },
        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        text = { Text("Proveedor") },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp),
    ) {
      Spacer(Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Proveedores",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Catálogos PDF, Excel, CSV e Imágenes",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          IconButton(onClick = onOpenOtaDialog) {
            Icon(
              Icons.Rounded.SystemUpdate,
              contentDescription = "Actualizaciones OTA",
              tint = if (otaAvailableTag != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          AssistChip(
            onClick = onOpenAiSettings,
            leadingIcon = {
              Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (aiConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            },
            label = { Text(if (aiConfigured) "IA activa" else "IA Gemini") },
          )
        }
      }
      if (otaAvailableTag != null) {
        Spacer(Modifier.height(8.dp))
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenOtaDialog),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(
                Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = "Actualización $otaAvailableTag disponible",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            TextButton(onClick = onOpenOtaDialog) {
              Text("Actualizar")
            }
          }
        }
      }
      Spacer(Modifier.height(10.dp))
      FilledTonalButton(
        onClick = onOpenIsaacPos,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
          if (isaacPosSyncedCount > 0) "Isaac POS · $isaacPosSyncedCount productos sincronizados"
          else "Conectar y sincronizar Isaac POS (Supabase)"
        )
      }
      Spacer(Modifier.height(10.dp))

      if (proveedores.isNotEmpty()) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Buscar proveedor...") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
      }

      if (proveedores.isEmpty()) {
        EmptyProveedoresState(onAddClick = { showAddDialog = true })
      } else if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            "No hay proveedores que coincidan con \"$searchQuery\"",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(bottom = 88.dp),
        ) {
          items(filtered, key = { it.proveedor.id }) { resumen ->
            ProveedorCard(
              resumen = resumen,
              onOpenCatalog = { onOpenProveedor(resumen.proveedor.id) },
              onStartOrder = { onStartOrder(resumen.proveedor.id) },
            )
          }
        }
      }
    }
  }

  if (showAddDialog) {
    ProveedorFormDialog(
      onDismiss = { showAddDialog = false },
      onSave = { prov ->
        showAddDialog = false
        onSaveProveedor(prov)
      },
    )
  }
}

@Composable
private fun ProveedorCard(
  resumen: ProveedorResumen,
  onOpenCatalog: () -> Unit,
  onStartOrder: () -> Unit,
) {
  val prov = resumen.proveedor
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onOpenCatalog),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.size(44.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Rounded.Storefront,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = prov.nombre,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (prov.telefonoWhatsApp.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Rounded.Phone,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.width(4.dp))
              Text(
                text = prov.telefonoWhatsApp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        Icon(
          Icons.Rounded.ChevronRight,
          contentDescription = "Abrir catálogo",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatPill(
          icon = Icons.Rounded.Inventory2,
          text = "${resumen.cantidadProductos} prod.",
        )
        StatPill(
          icon = Icons.Rounded.Description,
          text = "${resumen.cantidadCatalogos} cat.",
        )
        if (resumen.productosBajoStock > 0) {
          StatPill(
            icon = Icons.Rounded.WarningAmber,
            text = "${resumen.productosBajoStock} bajo stock",
          )
        }
      }

      Spacer(Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
          onClick = onOpenCatalog,
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Catálogo")
        }
        Button(
          onClick = onStartOrder,
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Rounded.ShoppingCartCheckout, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Pedir")
        }
      }
    }
  }
}

@Composable
private fun StatPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(8.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.width(4.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun EmptyProveedoresState(onAddClick: () -> Unit) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(bottom = 72.dp),
    contentAlignment = Alignment.Center,
  ) {
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.size(64.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Rounded.Storefront,
              contentDescription = null,
              modifier = Modifier.size(34.dp),
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
        Text(
          "Empieza agregando tu primer proveedor",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          "1. Crea el proveedor con su número de WhatsApp.\n" +
            "2. Sube su catálogo en PDF, Excel (.xlsx), CSV o imágenes.\n" +
            "3. Selecciona cantidades/costos y envía el pedido por WhatsApp (todo queda guardado en tu historial SQLite).",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onAddClick, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Rounded.Add, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Agregar proveedor")
        }
      }
    }
  }
}
