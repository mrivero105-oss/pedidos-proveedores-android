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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.domain.model.CatalogoArchivo
import com.pedidos.proveedores.domain.model.CatalogoFormato
import com.pedidos.proveedores.domain.model.Pedido
import com.pedidos.proveedores.domain.model.Producto
import com.pedidos.proveedores.domain.model.ProveedorResumen
import com.pedidos.proveedores.domain.parser.PriceParser
import com.pedidos.proveedores.ui.OrderDraftState
import com.pedidos.proveedores.ui.components.ManualOrderItemDialog
import com.pedidos.proveedores.ui.components.ProductThumbnail
import com.pedidos.proveedores.ui.components.QuantityInputDialog
import com.pedidos.proveedores.ui.theme.WhatsAppGreen

@Composable
fun NuevoPedidoScreen(
  proveedores: List<ProveedorResumen>,
  allProductos: List<Producto>,
  allCatalogos: List<CatalogoArchivo>,
  draft: OrderDraftState,
  currentPedidoPreview: Pedido?,
  lowStockThreshold: Double = 5.0,
  onSelectProveedor: (Long) -> Unit,
  onIncrementProduct: (Producto) -> Unit,
  onDecrementProduct: (Producto) -> Unit,
  onSetProductQuantityAndPrice: (Producto, Double, Double?, Boolean) -> Unit,
  onAddManualItem: (nombre: String, cantidad: Double, unidad: String, precio: Double?) -> Unit,
  onRemoveManualItem: (Int) -> Unit,
  onNotesChange: (String) -> Unit,
  onClearDraft: () -> Unit,
  onOpenProveedorCatalog: (Long) -> Unit,
  onOpenPdf: (rutaLocal: String, titulo: String) -> Unit,
  onSaveDraft: () -> Unit,
  onOpenWhatsAppPreview: () -> Unit,
  onGoToProveedores: () -> Unit,
  onPrefillLowStock: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var searchQuery by remember { mutableStateOf("") }
  var onlySelected by remember { mutableStateOf(false) }
  var onlyLowStock by remember { mutableStateOf(false) }
  var showManualDialog by remember { mutableStateOf(false) }
  var quantityDialogProduct by remember { mutableStateOf<Producto?>(null) }

  if (proveedores.isEmpty()) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(
          Icons.Rounded.Storefront,
          contentDescription = null,
          modifier = Modifier.size(48.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          "Primero agrega un proveedor",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          "Crea tus proveedores y sube sus catálogos en PDF, Excel o CSV para armar pedidos en segundos.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onGoToProveedores) {
          Text("Ir a Proveedores")
        }
      }
    }
    return
  }

  val selectedProvResumen = proveedores.firstOrNull { it.proveedor.id == draft.proveedorId }
    ?: proveedores.first().also { onSelectProveedor(it.proveedor.id) }
  val selectedProv = selectedProvResumen.proveedor

  val provProducts = remember(allProductos, selectedProv.id) {
    allProductos.filter { it.proveedorId == selectedProv.id }
  }
  val latestPdf = remember(allCatalogos, selectedProv.id) {
    allCatalogos.firstOrNull { it.proveedorId == selectedProv.id && it.formato == CatalogoFormato.PDF }
  }

  val hasPosStockData = remember(provProducts) { provProducts.any { it.stockPos != null } }
  val lowStockCount = remember(provProducts, lowStockThreshold) {
    provProducts.count { it.stockPos != null && it.stockPos <= lowStockThreshold }
  }

  val filteredProducts = remember(provProducts, searchQuery, onlySelected, onlyLowStock, lowStockThreshold, draft.quantitiesByProductId) {
    provProducts.filter { prod ->
      val matchesFilter = !onlySelected || (draft.quantitiesByProductId[prod.id] ?: 0.0) > 0.0
      val matchesLowStock = !onlyLowStock || (prod.stockPos != null && prod.stockPos <= lowStockThreshold)
      val matchesQuery = searchQuery.isBlank() || run {
        val q = searchQuery.trim().lowercase()
        prod.nombre.lowercase().contains(q) ||
          prod.codigo.lowercase().contains(q) ||
          prod.categoria.lowercase().contains(q)
      }
      matchesFilter && matchesLowStock && matchesQuery
    }
  }

  Column(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = if (draft.editingPedidoId != null) "Editando borrador" else "Nuevo pedido",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
        if (draft.itemCount > 0) {
          TextButton(onClick = onClearDraft) {
            Text("Limpiar")
          }
        }
      }

      // Selector horizontal de proveedor
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 6.dp),
      ) {
        items(proveedores, key = { it.proveedor.id }) { item ->
          FilterChip(
            selected = item.proveedor.id == selectedProv.id,
            onClick = { onSelectProveedor(item.proveedor.id) },
            label = { Text(item.proveedor.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          )
        }
      }

      Spacer(Modifier.height(8.dp))

      // Buscador + acciones rápidas (Ver PDF / Solo agregados)
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Buscar en catálogo de ${selectedProv.nombre}...") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (searchQuery.isNotEmpty()) {
          {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Rounded.Close, contentDescription = "Limpiar búsqueda")
            }
          }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
      )

      Spacer(Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FilterChip(
          selected = onlySelected,
          onClick = { onlySelected = !onlySelected },
          label = { Text("En pedido (${draft.itemCount})") },
        )
        if (hasPosStockData) {
          FilterChip(
            selected = onlyLowStock,
            onClick = { onlyLowStock = !onlyLowStock },
            label = { Text("Bajo stock") },
          )
        }
        if (latestPdf != null) {
          OutlinedButton(
            onClick = { onOpenPdf(latestPdf.rutaLocal, latestPdf.nombreArchivo) },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Ver PDF", style = MaterialTheme.typography.labelMedium)
          }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { showManualDialog = true }) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(2.dp))
          Text("Manual")
        }
      }

      if (lowStockCount > 0) {
        Spacer(Modifier.height(6.dp))
        FilledTonalButton(
          onClick = {
            onPrefillLowStock()
            onlySelected = true
          },
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Prellenar bajo stock ($lowStockCount prod.)")
        }
      }
    }

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(bottom = 16.dp),
    ) {
      if (provProducts.isEmpty()) {
        item {
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                "${selectedProv.nombre} aún no tiene productos en su catálogo.",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                "Puedes subir su catálogo en PDF, Excel o CSV antes de pedir, o añadir ítems manualmente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onOpenProveedorCatalog(selectedProv.id) }) {
                  Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Subir catálogo")
                }
                OutlinedButton(onClick = { showManualDialog = true }) {
                  Text("+ Ítem manual")
                }
              }
            }
          }
        }
      } else if (filteredProducts.isEmpty()) {
        item {
          Text(
            text = if (onlySelected) "Todavía no agregaste productos de este catálogo."
            else "No se encontraron productos para \"$searchQuery\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
          )
        }
      } else {
        items(filteredProducts, key = { "order_prod_${it.id}" }) { producto ->
          val qty = draft.quantitiesByProductId[producto.id] ?: 0.0
          val effectivePrice = draft.customPricesByProductId[producto.id] ?: producto.precio
          ProductOrderCard(
            producto = producto,
            effectivePrice = effectivePrice,
            lowStockThreshold = lowStockThreshold,
            quantity = qty,
            onIncrement = { onIncrementProduct(producto) },
            onDecrement = { onDecrementProduct(producto) },
            onQuantityClick = { quantityDialogProduct = producto },
          )
        }
      }

      // Ítems manuales fuera de catálogo
      if (draft.manualItems.isNotEmpty()) {
        item {
          Spacer(Modifier.height(4.dp))
          Text(
            text = "Ítems manuales (${draft.manualItems.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
        }
        itemsIndexed(draft.manualItems) { index, item ->
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(item.nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                val qtyLine = buildString {
                  append("${PriceParser.formatQuantity(item.cantidad)} ${item.unidad}")
                  item.precioUnitario?.let { append(" · ${PriceParser.formatCurrency(it)}") }
                }
                Text(
                  qtyLine,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(onClick = { onRemoveManualItem(index) }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Quitar")
              }
            }
          }
        }
      }

      // Notas del pedido
      item {
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
          value = draft.notas,
          onValueChange = onNotesChange,
          label = { Text("Notas para el proveedor (opcional)") },
          placeholder = { Text("Ej. Entregar mañana antes de las 11hs") },
          maxLines = 3,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    // Barra inferior fija con totales y botón WhatsApp
    Surface(
      tonalElevation = 4.dp,
      shadowElevation = 8.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val itemsCount = currentPedidoPreview?.items?.size ?: 0
          val totalQty = currentPedidoPreview?.totalCantidad ?: 0.0
          Text(
            text = "$itemsCount producto(s) · Cant.: ${PriceParser.formatQuantity(totalQty)}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
          )
          currentPedidoPreview?.totalEstimado?.let { total ->
            Text(
              text = "Est.: ${PriceParser.formatCurrency(total)}",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onSaveDraft,
            enabled = draft.itemCount > 0,
            modifier = Modifier.weight(0.42f),
          ) {
            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Borrador")
          }
          Button(
            onClick = onOpenWhatsAppPreview,
            enabled = draft.itemCount > 0,
            colors = ButtonDefaults.buttonColors(
              containerColor = WhatsAppGreen,
              contentColor = Color.White,
            ),
            modifier = Modifier.weight(0.58f),
          ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Enviar WhatsApp")
          }
        }
      }
    }
  }

  if (showManualDialog) {
    ManualOrderItemDialog(
      onDismiss = { showManualDialog = false },
      onAdd = { nombre, cantidad, unidad, precio ->
        showManualDialog = false
        onAddManualItem(nombre, cantidad, unidad, precio)
      },
    )
  }

  quantityDialogProduct?.let { prod ->
    QuantityInputDialog(
      producto = prod,
      currentQuantity = draft.quantitiesByProductId[prod.id] ?: 0.0,
      currentUnitPrice = draft.customPricesByProductId[prod.id] ?: prod.precio,
      onDismiss = { quantityDialogProduct = null },
      onConfirm = { newQty, newPrice, saveToCatalog ->
        quantityDialogProduct = null
        onSetProductQuantityAndPrice(prod, newQty, newPrice, saveToCatalog)
      },
    )
  }
}

@Composable
private fun ProductOrderCard(
  producto: Producto,
  effectivePrice: Double?,
  lowStockThreshold: Double,
  quantity: Double,
  onIncrement: () -> Unit,
  onDecrement: () -> Unit,
  onQuantityClick: () -> Unit,
) {
  val isSelected = quantity > 0.0
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
      } else {
        MaterialTheme.colorScheme.surface
      }
    ),
    border = BorderStroke(
      width = if (isSelected) 1.5.dp else 1.dp,
      color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onQuantityClick),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (!producto.imagenRuta.isNullOrBlank()) {
        ProductThumbnail(
          imagePath = producto.imagenRuta,
          contentDescription = producto.nombre,
          size = 42.dp,
        )
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
        val subtitle = buildList {
          add(producto.unidad)
          if (effectivePrice != null) add(PriceParser.formatCurrency(effectivePrice))
        }.joinToString(" · ")
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (producto.stockPos != null) {
          Spacer(Modifier.height(3.dp))
          StockPosBadge(stock = producto.stockPos, isLow = producto.stockPos <= lowStockThreshold)
        }
      }
      Spacer(Modifier.width(6.dp))
      if (!isSelected) {
        FilledTonalButton(
          onClick = onIncrement,
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(3.dp))
          Text("Pedir", style = MaterialTheme.typography.labelLarge)
        }
      } else {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          FilledTonalIconButton(onClick = onDecrement, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Remove, contentDescription = "Restar", modifier = Modifier.size(16.dp))
          }
          Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.clickable(onClick = onQuantityClick),
          ) {
            Text(
              text = PriceParser.formatQuantity(quantity),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            )
          }
          FilledIconButton(onClick = onIncrement, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = "Sumar", modifier = Modifier.size(16.dp))
          }
        }
      }
    }
  }
}
