package com.pedidos.proveedores.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.data.local.AiSettings

@Composable
fun AiSettingsDialog(
  currentSettings: AiSettings,
  testingConnection: Boolean,
  testFeedback: String?,
  onTestConnection: (apiKey: String, model: String) -> Unit,
  onSave: (apiKey: String, model: String) -> Unit,
  onClearKey: () -> Unit,
  onDismiss: () -> Unit,
) {
  var apiKey by remember(currentSettings.geminiApiKey) { mutableStateOf(currentSettings.geminiApiKey) }
  var selectedModel by remember(currentSettings.geminiModel) { mutableStateOf(currentSettings.geminiModel) }
  var showKey by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
    title = { Text("IA Gratuita (Gemini Vision)") },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
      ) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Conecta tu clave gratuita de Google AI Studio para leer cualquier catálogo PDF (incluso escaneados o con fotos) con máxima precisión visual.\n\n" +
              "1. Entra a aistudio.google.com/apikey\n" +
              "2. Crea una API Key gratuita\n" +
              "3. Pégala aquí (se guarda solo en tu teléfono).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(10.dp),
          )
        }

        OutlinedTextField(
          value = apiKey,
          onValueChange = { apiKey = it },
          label = { Text("Clave API de Gemini") },
          placeholder = { Text("AIza... o AQ....") },
          singleLine = true,
          visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(onClick = { showKey = !showKey }) {
              Icon(
                if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = if (showKey) "Ocultar" else "Mostrar",
              )
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )

        Text("Modelo:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        AiSettings.AVAILABLE_MODELS.forEach { (modelId, label) ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { selectedModel = modelId },
          ) {
            RadioButton(
              selected = selectedModel == modelId,
              onClick = { selectedModel = modelId },
            )
            Text(text = label, style = MaterialTheme.typography.bodySmall)
          }
        }

        OutlinedButton(
          onClick = { onTestConnection(apiKey, selectedModel) },
          enabled = apiKey.isNotBlank() && !testingConnection,
          modifier = Modifier.fillMaxWidth(),
        ) {
          if (testingConnection) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Probando conexión...")
          } else {
            Text("Probar conexión")
          }
        }

        if (testFeedback != null) {
          Text(
            text = testFeedback,
            style = MaterialTheme.typography.bodySmall,
            color = if (testFeedback.startsWith("Conexión exitosa")) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.error
            },
          )
        }

        if (currentSettings.isConfigured) {
          TextButton(
            onClick = {
              apiKey = ""
              onClearKey()
            },
          ) {
            Text("Borrar clave guardada")
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSave(apiKey, selectedModel)
          onDismiss()
        },
      ) {
        Text("Guardar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cerrar") }
    },
  )
}
