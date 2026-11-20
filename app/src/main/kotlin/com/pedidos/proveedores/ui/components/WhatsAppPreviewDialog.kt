package com.pedidos.proveedores.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.domain.model.Pedido
import com.pedidos.proveedores.domain.whatsapp.WhatsAppOrderFormatter
import com.pedidos.proveedores.ui.theme.WhatsAppGreen

@Composable
fun WhatsAppPreviewDialog(
  pedido: Pedido,
  onDismiss: () -> Unit,
  onCopy: (String) -> Unit,
  onSendWhatsApp: (message: String, incluirPrecios: Boolean) -> Unit,
) {
  var incluirPrecios by remember(pedido) { mutableStateOf(pedido.incluirPrecios) }
  val message = remember(pedido, incluirPrecios) {
    WhatsAppOrderFormatter.format(pedido, incluirPrecios)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Enviar por WhatsApp") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val telefono = pedido.proveedorTelefono.ifBlank { "Sin número guardado (elegirás el chat)" }
        Text(
          text = "Destinatario: ${pedido.proveedorNombre}\n$telefono",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("Incluir precios y total", style = MaterialTheme.typography.bodyMedium)
          Switch(checked = incluirPrecios, onCheckedChange = { incluirPrecios = it })
        }

        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 280.dp),
        ) {
          Text(
            text = message,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
              .verticalScroll(rememberScrollState())
              .padding(12.dp),
          )
        }

        OutlinedButton(
          onClick = { onCopy(message) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Copiar mensaje")
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSendWhatsApp(message, incluirPrecios) },
        colors = ButtonDefaults.buttonColors(
          containerColor = WhatsAppGreen,
          contentColor = Color.White,
        ),
      ) {
        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Abrir WhatsApp")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}
