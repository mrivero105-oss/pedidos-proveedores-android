package com.pedidos.proveedores.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private object ThumbBitmapCache {
  private val cache = object : LruCache<String, Bitmap>(48) {}
  fun get(path: String): Bitmap? = cache.get(path)
  fun put(path: String, bmp: Bitmap) {
    cache.put(path, bmp)
  }
}

@Composable
fun ProductThumbnail(
  imagePath: String?,
  contentDescription: String,
  size: Dp = 52.dp,
  modifier: Modifier = Modifier,
) {
  var bitmap by remember(imagePath) {
    mutableStateOf(imagePath?.let { ThumbBitmapCache.get(it) })
  }

  LaunchedEffect(imagePath) {
    if (imagePath.isNullOrBlank()) {
      bitmap = null
      return@LaunchedEffect
    }
    val cached = ThumbBitmapCache.get(imagePath)
    if (cached != null) {
      bitmap = cached
      return@LaunchedEffect
    }
    val loaded = withContext(Dispatchers.IO) {
      runCatching {
        val file = File(imagePath)
        if (!file.exists()) return@runCatching null
        BitmapFactory.decodeFile(file.absolutePath)
      }.getOrNull()
    }
    if (loaded != null) {
      ThumbBitmapCache.put(imagePath, loaded)
      bitmap = loaded
    }
  }

  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(10.dp),
    modifier = modifier.size(size),
  ) {
    val bmp = bitmap
    if (bmp != null) {
      Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .clip(RoundedCornerShape(10.dp)),
      )
    } else {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Rounded.Inventory2,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(size * 0.45f),
        )
      }
    }
  }
}
