package com.pedidos.proveedores.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisorPdfScreen(
  rutaLocal: String,
  titulo: String,
  onBack: () -> Unit,
) {
  var pageIndex by remember { mutableIntStateOf(0) }
  var pageCount by remember { mutableIntStateOf(0) }
  var bitmap by remember { mutableStateOf<Bitmap?>(null) }
  var errorMsg by remember { mutableStateOf<String?>(null) }
  var isLoading by remember { mutableStateOf(true) }
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }

  LaunchedEffect(rutaLocal, pageIndex) {
    isLoading = true
    errorMsg = null
    scale = 1f
    offset = Offset.Zero

    val result = withContext(Dispatchers.IO) {
      runCatching {
        val file = File(rutaLocal)
        require(file.exists()) { "El archivo PDF ya no existe en el almacenamiento." }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
          PdfRenderer(pfd).use { renderer ->
            val total = renderer.pageCount
            require(total > 0) { "El PDF no tiene páginas." }
            val safeIndex = pageIndex.coerceIn(0, total - 1)
            renderer.openPage(safeIndex).use { page ->
              val targetWidth = (page.width * 2).coerceIn(800, 2200)
              val targetHeight = (page.height * (targetWidth.toFloat() / page.width)).toInt().coerceAtLeast(1)
              val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
              bmp.eraseColor(AndroidColor.WHITE)
              page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
              total to bmp
            }
          }
        }
      }
    }

    result.onSuccess { (total, bmp) ->
      pageCount = total
      bitmap = bmp
      isLoading = false
    }.onFailure { err ->
      errorMsg = err.localizedMessage ?: "No se pudo abrir el archivo PDF."
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
      if (pageCount > 0) {
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
              onClick = { if (pageIndex > 0) pageIndex-- },
              enabled = pageIndex > 0,
            ) {
              Icon(Icons.AutoMirrored.Rounded.NavigateBefore, contentDescription = "Página anterior")
            }
            Text(
              text = "Página ${pageIndex + 1} de $pageCount",
              style = MaterialTheme.typography.titleSmall,
            )
            IconButton(
              onClick = { if (pageIndex < pageCount - 1) pageIndex++ },
              enabled = pageIndex < pageCount - 1,
            ) {
              Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = "Página siguiente")
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
        .background(Color(0xFF1F2937))
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
        errorMsg != null -> {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(errorMsg!!, color = Color.White, style = MaterialTheme.typography.bodyMedium)
          }
        }
        bitmap != null -> {
          Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Página ${pageIndex + 1} del catálogo",
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
