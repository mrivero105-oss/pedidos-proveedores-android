package com.pedidos.proveedores.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.domain.model.Proveedor
import com.pedidos.proveedores.domain.model.ProveedorResumen
import com.pedidos.proveedores.domain.parser.PendingSharedPayload
import com.pedidos.proveedores.domain.parser.WhatsAppCatalogTextParser
import com.pedidos.proveedores.ui.theme.providerAvatarColor
import com.pedidos.proveedores.ui.theme.providerInitials

/**
 * Selector rápido de proveedor al recibir un archivo, fotos o texto compartido desde WhatsApp.
 * Si el archivo o texto menciona a alguno de tus proveedores, lo destaca primero arriba.
 */
@Composable
fun SharedProviderPickerDialog(
  payload: PendingSharedPayload,
  proveedores: List<ProveedorResumen>,
  onSelectProveedor: (Long) -> Unit,
  onCreateProveedorAndImport: (Proveedor) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by remember { mutableStateOf("") }
  var showCreateForm by remember { mutableStateOf(false) }

  val suggestedId = remember(payload, proveedores) {
    WhatsAppCatalogTextParser.suggestProveedorId(
      fileNameOrHint = payload.hintForProviderMatch,
      proveedores = proveedores.map { it.proveedor },
    )
  }

  val suggestedItem = remember(suggestedId, proveedores) {
    suggestedId?.let { id -> proveedores.firstOrNull { it.proveedor.id == id } }
  }

  val filtered = remember(proveedores, query, suggestedId) {
    val q = query.trim().lowercase()
    val base = if (q.isEmpty()) proveedores else proveedores.filter {
      it.proveedor.nombre.lowercase().contains(q) ||
        it.proveedor.telefonoWhatsApp.lowercase().contains(q)
    }
    base.sortedWith(
      compareByDescending<ProveedorResumen> { it.proveedor.id == suggestedId }
        .thenBy { it.proveedor.nombre.lowercase() }
    )
  }

  if (showCreateForm) {
    ProveedorFormDialog(
      onDismiss = { showCreateForm = false },
      onSave = { newProv ->
        showCreateForm = false
        onCreateProveedorAndImport(newProv)
      },
    )
    return
  }

  val payloadIcon = when (payload) {
    is PendingSharedPayload.SingleFile -> Icons.Rounded.UploadFile
    is PendingSharedPayload.MultipleImages -> Icons.Rounded.Image
    is PendingSharedPayload.PlainText -> Icons.AutoMirrored.Rounded.Chat
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("¿De qué proveedor es esta lista?") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Vista previa compacta de lo compartido desde WhatsApp
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = payloadIcon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = payload.previewTitle,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = payload.previewSubtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        if (suggestedItem != null && query.isBlank()) {
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSelectProveedor(suggestedItem.proveedor.id) },
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(6.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Sugerido: ${suggestedItem.proveedor.nombre}",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "Toca aquí para importar directo",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        if (proveedores.size > 4) {
          OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar proveedor...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          )
        }

        if (filtered.isEmpty()) {
          Text(
            text = "No se encontraron proveedores. Crea uno nuevo abajo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 230.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            items(filtered, key = { it.proveedor.id }) { res ->
              val accent = providerAvatarColor(res.proveedor.nombre)
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { onSelectProveedor(res.proveedor.id) },
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Surface(
                    color = accent.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(30.dp),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = providerInitials(res.proveedor.nombre),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                      )
                    }
                  }
                  Spacer(Modifier.width(8.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = res.proveedor.nombre,
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.SemiBold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = "${res.cantidadProductos} prod. · ${res.cantidadCatalogos} listas",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }
        }

        OutlinedButton(
          onClick = { showCreateForm = true },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Nuevo proveedor")
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}

/**
 * Diálogo para pegar directamente una lista de precios copiada de WhatsApp dentro de un proveedor.
 */
@Composable
fun PasteWhatsAppTextDialog(
  proveedorNombre: String,
  onImportText: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var text by remember { mutableStateOf("") }
  val lineCount = remember(text) { text.lineSequence().count { it.isNotBlank() } }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Pegar lista de WhatsApp") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Pega aquí el mensaje de precios de $proveedorNombre. Detecta productos, bultos, $ o Bs. y +IVA automáticamente.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
          onClick = {
            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipText = cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            if (clipText.isNotBlank()) {
              text = clipText
            }
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Pegar del portapapeles")
        }
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          label = { Text("Mensaje con precios") },
          placeholder = { Text("Ej.\n*HARINA PAN 1KG X 20* $18.50\nAceite 1L x 12 ... $22.00") },
          minLines = 5,
          maxLines = 9,
          modifier = Modifier
            .fillMaxWidth()
            .height(170.dp),
        )
        if (lineCount > 0) {
          Text(
            text = "$lineCount línea(s) listas para analizar",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onImportText(text.trim()) },
        enabled = text.isNotBlank(),
      ) {
        Text("Extraer precios")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}
