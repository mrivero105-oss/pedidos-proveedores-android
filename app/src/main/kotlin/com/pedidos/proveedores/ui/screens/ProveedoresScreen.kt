package com.pedidos.proveedores.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCartCheckout
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.data.local.AppThemeMode
import com.pedidos.proveedores.domain.model.Proveedor
import com.pedidos.proveedores.domain.model.ProveedorResumen
import com.pedidos.proveedores.ui.components.ProveedorFormDialog
import com.pedidos.proveedores.ui.theme.HeroGradientEndDark
import com.pedidos.proveedores.ui.theme.HeroGradientEndLight
import com.pedidos.proveedores.ui.theme.HeroGradientStartDark
import com.pedidos.proveedores.ui.theme.HeroGradientStartLight
import com.pedidos.proveedores.ui.theme.LowStockAmber
import com.pedidos.proveedores.ui.theme.providerAvatarColor
import com.pedidos.proveedores.ui.theme.providerInitials

@Composable
fun ProveedoresScreen(
  proveedores: List<ProveedorResumen>,
  aiConfigured: Boolean,
  isaacPosSyncedCount: Int,
  isaacPosWithStockCount: Int = 0,
  otaAvailableTag: String?,
  themeMode: AppThemeMode = AppThemeMode.SYSTEM,
  onCycleTheme: () -> Unit = {},
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

  val totalProductosCatalogos = remember(proveedores) { proveedores.sumOf { it.cantidadProductos } }
  val totalBajoStock = remember(proveedores) { proveedores.sumOf { it.productosBajoStock } }

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

  val isDark = when (themeMode) {
    AppThemeMode.DARK -> true
    AppThemeMode.LIGHT -> false
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
  }

  val heroBrush = remember(isDark) {
    Brush.horizontalGradient(
      colors = if (isDark) {
        listOf(HeroGradientStartDark, HeroGradientEndDark)
      } else {
        listOf(HeroGradientStartLight, HeroGradientEndLight)
      }
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  ) {
    // Barra superior ultracompacta (siempre visible, sin FAB gigante tapando tarjetas)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Proveedores",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = if (proveedores.isEmpty()) "Catálogos y pedidos"
          else "${proveedores.size} prov. · $totalProductosCatalogos prod.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
      ) {
        IconButton(onClick = onCycleTheme, modifier = Modifier.size(38.dp)) {
          val themeIcon = when (themeMode) {
            AppThemeMode.DARK -> Icons.Rounded.DarkMode
            AppThemeMode.LIGHT -> Icons.Rounded.LightMode
            AppThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
          }
          Icon(
            imageVector = themeIcon,
            contentDescription = "Cambiar tema (${themeMode.etiqueta})",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
        IconButton(onClick = onOpenAiSettings, modifier = Modifier.size(38.dp)) {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = if (aiConfigured) "IA Gemini activa" else "Configurar IA Gemini",
            tint = if (aiConfigured) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
        IconButton(onClick = onOpenOtaDialog, modifier = Modifier.size(38.dp)) {
          Icon(
            imageVector = Icons.Rounded.SystemUpdate,
            contentDescription = "Actualizaciones OTA",
            tint = if (otaAvailableTag != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
        Spacer(Modifier.width(4.dp))
        FilledTonalIconButton(
          onClick = { showAddDialog = true },
          modifier = Modifier.size(36.dp),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = "Nuevo proveedor", modifier = Modifier.size(20.dp))
        }
      }
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
    ) {
      if (otaAvailableTag != null) {
        item(key = "ota_banner") {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier
              .fillMaxWidth()
              .clickable(onClick = onOpenOtaDialog),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                  Icons.Rounded.SystemUpdate,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                  text = "Actualización $otaAvailableTag disponible",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
              TextButton(
                onClick = onOpenOtaDialog,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
              ) {
                Text("Instalar", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
              }
            }
          }
        }
      }

      // Panel Hero compacto dentro del scroll (libera ~45% de pantalla al deslizar)
      item(key = "hero_isaac_pos") {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(heroBrush)
            .clickable(onClick = onOpenIsaacPos)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                  color = Color.White.copy(alpha = 0.16f),
                  shape = CircleShape,
                  modifier = Modifier.size(26.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      Icons.Rounded.CloudSync,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(15.dp),
                    )
                  }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                  text = if (isaacPosSyncedCount > 0) "Isaac POS · $isaacPosSyncedCount prod. ($isaacPosWithStockCount c/stock)"
                  else "Conectar proveedores Isaac POS",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Surface(
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(16.dp),
              ) {
                Text(
                  text = "Sync",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              HeroKpiBox(
                value = proveedores.size.toString(),
                label = "Proveedores",
                accent = Color.White,
                modifier = Modifier.weight(1f),
              )
              HeroKpiBox(
                value = totalProductosCatalogos.toString(),
                label = "Productos",
                accent = Color(0xFF67E8F9),
                modifier = Modifier.weight(1f),
              )
              HeroKpiBox(
                value = totalBajoStock.toString(),
                label = "Bajo stock",
                accent = if (totalBajoStock > 0) Color(0xFFFDE047) else Color(0xFF6EE7B7),
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }

      if (proveedores.isNotEmpty()) {
        item(key = "search_bar") {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar proveedor o WhatsApp...") },
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
        }
      }

      if (proveedores.isEmpty()) {
        item(key = "empty_state") {
          EmptyProveedoresState(onAddClick = { showAddDialog = true })
        }
      } else if (filtered.isEmpty()) {
        item(key = "empty_filter") {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              "No hay proveedores que coincidan con \"$searchQuery\"",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
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
private fun HeroKpiBox(
  value: String,
  label: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = Color.White.copy(alpha = 0.12f),
    shape = RoundedCornerShape(10.dp),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalAlignment = Alignment.Start,
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = accent,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.82f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun ProveedorCard(
  resumen: ProveedorResumen,
  onOpenCatalog: () -> Unit,
  onStartOrder: () -> Unit,
) {
  val prov = resumen.proveedor
  val avatarColor = remember(prov.nombre) { providerAvatarColor(prov.nombre) }
  val initials = remember(prov.nombre) { providerInitials(prov.nombre) }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onOpenCatalog),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        color = avatarColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, avatarColor.copy(alpha = 0.28f)),
        modifier = Modifier.size(38.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = avatarColor,
          )
        }
      }

      Spacer(Modifier.width(9.dp))

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = prov.nombre,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (prov.telefonoWhatsApp.isNotBlank()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Rounded.Phone,
              contentDescription = null,
              modifier = Modifier.size(11.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(3.dp))
            Text(
              text = prov.telefonoWhatsApp,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
          StatPill(
            icon = Icons.Rounded.Inventory2,
            text = "${resumen.cantidadProductos}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            bg = MaterialTheme.colorScheme.surfaceVariant,
          )
          if (resumen.cantidadCatalogos > 0) {
            StatPill(
              icon = Icons.Rounded.Description,
              text = "${resumen.cantidadCatalogos} listas",
              tint = MaterialTheme.colorScheme.primary,
              bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            )
          }
          if (resumen.productosBajoStock > 0) {
            StatPill(
              icon = Icons.Rounded.WarningAmber,
              text = "${resumen.productosBajoStock} bajo",
              tint = LowStockAmber,
              bg = LowStockAmber.copy(alpha = 0.14f),
            )
          }
        }
      }

      Spacer(Modifier.width(6.dp))

      FilledTonalButton(
        onClick = onStartOrder,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
      ) {
        Icon(Icons.Rounded.ShoppingCartCheckout, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text("Pedir", style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}

@Composable
private fun StatPill(
  icon: ImageVector,
  text: String,
  tint: Color,
  bg: Color,
) {
  Surface(
    color = bg,
    shape = RoundedCornerShape(6.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(12.dp),
        tint = tint,
      )
      Spacer(Modifier.width(3.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = tint,
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
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.size(56.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Rounded.Storefront,
              contentDescription = null,
              modifier = Modifier.size(30.dp),
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
          "1. Crea el proveedor o sincroniza tu inventario de Isaac POS arriba.\n" +
            "2. Sube catálogos en PDF, Excel, CSV o fotos.\n" +
            "3. Arma tu pedido en segundos y envíalo por WhatsApp.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onAddClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
          Icon(Icons.Rounded.Add, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Agregar proveedor")
        }
      }
    }
  }
}
