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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.BuildConfig
import com.pedidos.proveedores.data.local.OtaSettings
import com.pedidos.proveedores.ui.OtaUiState
import java.util.Locale

@Composable
fun OtaUpdateDialog(
  otaSettings: OtaSettings,
  otaState: OtaUiState,
  onSaveSettings: (repoSlug: String, autoCheck: Boolean) -> Unit,
  onCheckNow: (repoSlug: String) -> Unit,
  onDownloadAndInstall: () -> Unit,
  onInstallDownloaded: () -> Unit,
  onDismiss: () -> Unit,
) {
  var repoSlug by remember(otaSettings.repoSlug) { mutableStateOf(otaSettings.repoSlug) }
  var autoCheck by remember(otaSettings.autoCheckOnLaunch) { mutableStateOf(otaSettings.autoCheckOnLaunch) }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
    title = { Text("Actualizaciones OTA") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Versión instalada: v${BuildConfig.VERSION_NAME} (código ${BuildConfig.VERSION_CODE})",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = repoSlug,
          onValueChange = { repoSlug = it },
          label = { Text("Repositorio GitHub (usuario/repo)") },
          placeholder = { Text("mrivero105-oss/pedidos-proveedores-android") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("Buscar al abrir la app", style = MaterialTheme.typography.bodyMedium)
          Switch(
            checked = autoCheck,
            onCheckedChange = {
              autoCheck = it
              onSaveSettings(repoSlug, it)
            },
          )
        }

        when (otaState) {
          OtaUiState.Idle -> {
            OutlinedButton(
              onClick = {
                onSaveSettings(repoSlug, autoCheck)
                onCheckNow(repoSlug)
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Buscar actualizaciones ahora")
            }
          }

          OtaUiState.Checking -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.padding(vertical = 6.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
              Text("Consultando GitHub Releases...", style = MaterialTheme.typography.bodyMedium)
            }
          }

          is OtaUiState.UpToDate -> {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                  text = "Ya tienes la última versión (v${otaState.currentVersion}).",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }
            OutlinedButton(
              onClick = {
                onSaveSettings(repoSlug, autoCheck)
                onCheckNow(repoSlug)
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Volver a comprobar")
            }
          }

          is OtaUiState.UpdateAvailable -> {
            val rel = otaState.release
            val sizeMb = if (rel.apkSizeBytes > 0) {
              String.format(Locale.US, "%.1f MB", rel.apkSizeBytes / (1024.0 * 1024.0))
            } else ""
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = "Nueva versión disponible: ${rel.tagName}",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (sizeMb.isNotEmpty()) {
                  Text(
                    text = "Archivo: ${rel.apkFileName} ($sizeMb)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                }
                if (rel.releaseNotes.isNotBlank()) {
                  Text(
                    text = rel.releaseNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                      .heightIn(max = 120.dp)
                      .verticalScroll(rememberScrollState()),
                  )
                }
              }
            }
            Button(
              onClick = onDownloadAndInstall,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Descargar y actualizar")
            }
          }

          is OtaUiState.Downloading -> {
            val progress = if (otaState.totalBytes > 0L) {
              (otaState.downloadedBytes.toFloat() / otaState.totalBytes).coerceIn(0f, 1f)
            } else 0f
            val dlMb = String.format(Locale.US, "%.1f", otaState.downloadedBytes / (1024.0 * 1024.0))
            val totMb = if (otaState.totalBytes > 0L) {
              String.format(Locale.US, "%.1f MB", otaState.totalBytes / (1024.0 * 1024.0))
            } else "..."
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "Descargando ${otaState.release.tagName} ($dlMb / $totMb)...",
                style = MaterialTheme.typography.bodyMedium,
              )
              if (otaState.totalBytes > 0L) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
              } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
              }
            }
          }

          is OtaUiState.ReadyToInstall -> {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = if (otaState.waitingForPermission) {
                  "Activa el permiso \"Instalar aplicaciones desconocidas\" para Pedidos Proveedores y toca el botón para finalizar la actualización."
                } else {
                  "APK ${otaState.release.tagName} descargado y verificado. Toca para instalar conservando tus datos."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(12.dp),
              )
            }
            Button(
              onClick = onInstallDownloaded,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Instalar ${otaState.release.tagName}")
            }
          }

          is OtaUiState.Error -> {
            Text(
              text = otaState.message,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(
              onClick = {
                onSaveSettings(repoSlug, autoCheck)
                onCheckNow(repoSlug)
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Reintentar")
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSaveSettings(repoSlug, autoCheck)
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
