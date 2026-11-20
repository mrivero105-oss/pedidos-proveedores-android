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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.pedidos.proveedores.domain.model.Pedido
import com.pedidos.proveedores.domain.model.PedidoEstado
import com.pedidos.proveedores.domain.parser.PriceParser
import com.pedidos.proveedores.ui.theme.StatusDraftColor
import com.pedidos.proveedores.ui.theme.StatusReceivedColor
import com.pedidos.proveedores.ui.theme.StatusSentColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistorialPedidosScreen(
  pedidos: List<Pedido>,
  onOpenPedido: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedFilter by remember { mutableStateOf<PedidoEstado?>(null) }

  val filtered = remember(pedidos, selectedFilter) {
    if (selectedFilter == null) pedidos
    else pedidos.filter { it.estado == selectedFilter }
  }

  val totalAcumulado = remember(filtered) {
    val conCosto = filtered.mapNotNull { it.totalEstimado }
    if (conCosto.isEmpty()) null else conCosto.sum()
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
  ) {
    Spacer(Modifier.height(12.dp))
    Text(
      text = "Historial de pedidos",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = "Registro guardado en SQLite con estados, costos y reenvío por WhatsApp.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))

    if (filtered.isNotEmpty()) {
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = "${filtered.size} pedido(s) registrados",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            val totalBultos = filtered.sumOf { it.totalCantidad }
            Text(
              text = "Cantidad total: ${PriceParser.formatQuantity(totalBultos)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
          }
          if (totalAcumulado != null) {
            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "Costo acumulado",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
              )
              Text(
                text = PriceParser.formatCurrency(totalAcumulado),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }
      Spacer(Modifier.height(10.dp))
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      item {
        FilterChip(
          selected = selectedFilter == null,
          onClick = { selectedFilter = null },
          label = { Text("Todos (${pedidos.size})") },
        )
      }
      items(PedidoEstado.entries) { estado ->
        val count = pedidos.count { it.estado == estado }
        FilterChip(
          selected = selectedFilter == estado,
          onClick = { selectedFilter = if (selectedFilter == estado) null else estado },
          label = { Text("${estado.etiqueta} ($count)") },
        )
      }
    }

    Spacer(Modifier.height(12.dp))

    if (filtered.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            Icons.AutoMirrored.Rounded.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = if (pedidos.isEmpty()) "Todavía no realizaste pedidos"
            else "No hay pedidos en estado ${selectedFilter?.etiqueta}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
      ) {
        items(filtered, key = { it.id }) { pedido ->
          PedidoHistoryCard(
            pedido = pedido,
            onClick = { onOpenPedido(pedido.id) },
          )
        }
      }
    }
  }
}

@Composable
private fun PedidoHistoryCard(
  pedido: Pedido,
  onClick: () -> Unit,
) {
  val dateStr = remember(pedido.creadoEn) {
    SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.forLanguageTag("es-ES")).format(Date(pedido.creadoEn))
  }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = pedido.proveedorNombre,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        PedidoStatusBadge(estado = pedido.estado)
      }
      Text(
        text = dateStr,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "${pedido.items.size} producto(s) · Cant.: ${PriceParser.formatQuantity(pedido.totalCantidad)}",
          style = MaterialTheme.typography.bodyMedium,
        )
        pedido.totalEstimado?.let { total ->
          Text(
            text = PriceParser.formatCurrency(total),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

@Composable
fun PedidoStatusBadge(estado: PedidoEstado) {
  val color = when (estado) {
    PedidoEstado.BORRADOR -> StatusDraftColor
    PedidoEstado.ENVIADO -> StatusSentColor
    PedidoEstado.RECIBIDO -> StatusReceivedColor
  }
  Surface(
    color = color.copy(alpha = 0.14f),
    shape = RoundedCornerShape(8.dp),
  ) {
    Text(
      text = estado.etiqueta,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = color,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}
