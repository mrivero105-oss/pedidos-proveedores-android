package com.pedidos.proveedores.ui.screens

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.domain.model.Pedido
import com.pedidos.proveedores.domain.model.PedidoEstado
import com.pedidos.proveedores.domain.parser.PriceParser
import com.pedidos.proveedores.ui.theme.WhatsAppGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidoDetalleScreen(
  pedido: Pedido?,
  onBack: () -> Unit,
  onUpdateEstado: (PedidoEstado) -> Unit,
  onOpenWhatsAppPreview: (Pedido) -> Unit,
  onCopyOrderText: (Pedido) -> Unit,
  onEditDraft: (Pedido) -> Unit,
  onDuplicateOrder: (Pedido) -> Unit,
  onDelete: () -> Unit,
) {
  if (pedido == null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Pedido no encontrado")
    }
    return
  }

  var showDeleteConfirm by remember { mutableStateOf(false) }
  val dateStr = remember(pedido.creadoEn) {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-ES")).format(Date(pedido.creadoEn))
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Pedido #${pedido.id}") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
          }
        },
        actions = {
          IconButton(onClick = { showDeleteConfirm = true }) {
            Icon(Icons.Rounded.Delete, contentDescription = "Eliminar pedido")
          }
        },
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
    ) {
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = pedido.proveedorNombre,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
              )
              PedidoStatusBadge(estado = pedido.estado)
            }
            Text(
              text = "Creado: $dateStr",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pedido.proveedorTelefono.isNotBlank()) {
              Text(
                text = "WhatsApp: ${pedido.proveedorTelefono}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Spacer(Modifier.height(4.dp))
            Text("Estado del pedido", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              PedidoEstado.entries.forEach { estado ->
                FilterChip(
                  selected = pedido.estado == estado,
                  onClick = { onUpdateEstado(estado) },
                  label = { Text(estado.etiqueta) },
                )
              }
            }

            Spacer(Modifier.height(4.dp))
            Button(
              onClick = { onOpenWhatsAppPreview(pedido) },
              colors = ButtonDefaults.buttonColors(
                containerColor = WhatsAppGreen,
                contentColor = Color.White,
              ),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(if (pedido.estado == PedidoEstado.ENVIADO) "Reenviar por WhatsApp" else "Enviar por WhatsApp")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = { onCopyOrderText(pedido) },
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copiar")
              }
              OutlinedButton(
                onClick = { onDuplicateOrder(pedido) },
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Rounded.FileCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Repetir")
              }
            }

            if (pedido.estado == PedidoEstado.BORRADOR) {
              OutlinedButton(
                onClick = { onEditDraft(pedido) },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Seguir editando borrador")
              }
            }
          }
        }
      }

      item {
        Text(
          text = "Productos (${pedido.items.size})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }

      items(pedido.items, key = { it.id }) { item ->
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = item.nombre,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
              )
              val details = buildList {
                if (item.codigo.isNotBlank()) add("Cód: ${item.codigo}")
                add("${PriceParser.formatQuantity(item.cantidad)} ${item.unidad}")
                item.precioUnitario?.let { add("× ${PriceParser.formatCurrency(it)}") }
              }.joinToString(" · ")
              Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            item.subtotal?.let { sub ->
              Text(
                text = PriceParser.formatCurrency(sub),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }

      pedido.totalEstimado?.let { total ->
        item {
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Total estimado",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Text(
                text = PriceParser.formatCurrency(total),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }

      if (pedido.notas.isNotBlank()) {
        item {
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text("Notas", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
              Spacer(Modifier.height(4.dp))
              Text(pedido.notas, style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }
    }
  }

  if (showDeleteConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text("Eliminar pedido") },
      text = { Text("¿Seguro que quieres eliminar el pedido #${pedido.id}?") },
      confirmButton = {
        Button(
          onClick = {
            showDeleteConfirm = false
            onDelete()
          }
        ) {
          Text("Eliminar")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
      },
    )
  }
}
