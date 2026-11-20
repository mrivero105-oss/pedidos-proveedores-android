package com.pedidos.proveedores.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.domain.parser.MlKitImageOcrExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisorImagenScreen(
  rutas: List<String>,
  titulo: String,
  onBack: () -> Unit,
) {
  var currentIndex by remember { mutableIntStateOf(0) }
  var bitmap by remember { mutableStateOf<Bitmap?>(null) }
  var isLoading by remember { mutableStateOf(true) }
  var errorMsg by remember { mutableStateOf<String?>(null) }
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }

  val safePath = rutas.getOrNull(currentIndex)

  LaunchedEffect(safePath) {
    isLoading = true
    errorMsg = null
    scale = 1f
    offset = Offset.Zero

    val result = withContext(Dispatchers.IO) {
      runCatching {
        require(!safePath.isNullOrBlank()) { "No hay imagen para mostrar." }
        val file = File(safePath)
        require(file.exists()) { "La imagen ya no existe en el almacenamiento." }
        MlKitImageOcrExtractor.decodeScaledBitmap(file, maxDimension = 2048)
          ?: error("No se pudo decodificar la imagen.")
      }
    }

    result.onSuccess { bmp ->
      bitmap = bmp
      isLoading = false
    }.onFailure { err ->
      errorMsg = err.localizedMessage ?: "No se pudo abrir la imagen."
      isLoading = false
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(titulo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
          }
        },
        actions = {
          IconButton(onClick = { scale = (scale - 0.5f).coerceAtLeast(1f); if (scale == 1f) offset = Offset.Zero }) {
            Icon(Icons.Rounded.ZoomOut, contentDescription = "Alejar")
          }
          IconButton(onClick = { scale = (scale + 0.5f).coerceAtMost(4f) }) {
            Icon(Icons.Rounded.ZoomIn, contentDescription = "Acercar")
          }
        },
      )
    },
    bottomBar = {
      if (rutas.size > 1) {
        Surface(tonalElevation = 3.dp) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .navigationBarsPadding()
              .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            IconButton(
              onClick = { if (currentIndex > 0) currentIndex-- },
              enabled = currentIndex > 0,
            ) {
              Icon(Icons.AutoMirrored.Rounded.NavigateBefore, contentDescription = "Anterior")
            }
            Text(
              text = "Imagen ${currentIndex + 1} de ${rutas.size}",
              style = MaterialTheme.typography.titleSmall,
            )
            IconButton(
              onClick = { if (currentIndex < rutas.lastIndex) currentIndex++ },
              enabled = currentIndex < rutas.lastIndex,
            ) {
              Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = "Siguiente")
            }
          }
        }
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(Color(0xFF111827))
        .pointerInput(Unit) {
          detectTransformGestures { _, pan, zoom, _ ->
            val newScale = (scale * zoom).coerceIn(1f, 4f)
            scale = newScale
            offset = if (newScale > 1f) offset + pan else Offset.Zero
          }
        },
      contentAlignment = Alignment.Center,
    ) {
      when {
        isLoading -> CircularProgressIndicator(color = Color.White)
        errorMsg != null -> Text(errorMsg!!, color = Color.White, modifier = Modifier.padding(24.dp))
        bitmap != null -> {
          Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = titulo,
            contentScale = ContentScale.Fit,
            modifier = Modifier
              .fillMaxSize()
              .padding(8.dp)
              .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
              ),
          )
        }
      }
    }
  }
}
