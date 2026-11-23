package com.pedidos.proveedores.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisorTextoScreen(
  rutaLocal: String,
  titulo: String,
  onCopyText: (String) -> Unit,
  onBack: () -> Unit,
) {
  val content = remember(rutaLocal) {
    runCatching { File(rutaLocal).readText(Charsets.UTF_8) }
      .getOrElse { "No se pudo leer el archivo de texto guardado." }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = titulo,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
          }
        },
        actions = {
          IconButton(onClick = { onCopyText(content) }) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copiar texto")
          }
        },
      )
    },
  ) { innerPadding ->
    SelectionContainer(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
      Text(
        text = content,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
