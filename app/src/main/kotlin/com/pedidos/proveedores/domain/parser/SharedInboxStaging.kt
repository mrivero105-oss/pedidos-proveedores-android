package com.pedidos.proveedores.domain.parser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

sealed interface PendingSharedPayload {
  val previewTitle: String
  val previewSubtitle: String
  val hintForProviderMatch: String

  data class SingleFile(
    val stagedUri: Uri,
    val displayName: String,
    val mimeType: String,
  ) : PendingSharedPayload {
    override val previewTitle: String get() = displayName
    override val previewSubtitle: String get() = "Archivo compartido desde WhatsApp"
    override val hintForProviderMatch: String get() = displayName
  }

  data class MultipleImages(
    val stagedUris: List<Uri>,
    val displayName: String,
  ) : PendingSharedPayload {
    override val previewTitle: String get() = displayName
    override val previewSubtitle: String get() = "${stagedUris.size} imagen(es) listas para importar"
    override val hintForProviderMatch: String get() = displayName
  }

  data class PlainText(
    val text: String,
    val subject: String = "",
  ) : PendingSharedPayload {
    override val previewTitle: String
      get() = subject.ifBlank {
        text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(48) ?: "Lista de texto de WhatsApp"
      }
    override val previewSubtitle: String
      get() {
        val lineCount = text.lineSequence().count { it.isNotBlank() }
        return "Mensaje de texto · $lineCount línea(s)"
      }
    override val hintForProviderMatch: String
      get() = "$subject ${text.take(400)}"
  }
}

/**
 * Copia de forma inmediata los archivos/imágenes compartidos vía `ACTION_SEND` o `ACTION_SEND_MULTIPLE`
 * a `cacheDir/shared_inbox/` para no perder los permisos temporales de lectura otorgados por WhatsApp.
 */
object SharedInboxStaging {

  fun extractFromIntent(context: Context, intent: Intent?): PendingSharedPayload? {
    if (intent == null) return null
    val action = intent.action ?: return null
    val mime = intent.type.orEmpty().lowercase()

    return when (action) {
      Intent.ACTION_SEND -> {
        val streamUri = getParcelableUri(intent, Intent.EXTRA_STREAM)
        if (streamUri != null) {
          val staged = stageUriToCache(context, streamUri, mime) ?: return null
          val isImg = mime.startsWith("image/") || isImageName(staged.second)
          if (isImg) {
            PendingSharedPayload.MultipleImages(
              stagedUris = listOf(staged.first),
              displayName = staged.second,
            )
          } else {
            PendingSharedPayload.SingleFile(
              stagedUri = staged.first,
              displayName = staged.second,
              mimeType = mime,
            )
          }
        } else {
          val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
          if (sharedText.isBlank()) null
          else PendingSharedPayload.PlainText(
            text = sharedText,
            subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty(),
          )
        }
      }

      Intent.ACTION_SEND_MULTIPLE -> {
        val uris = getParcelableUriList(intent, Intent.EXTRA_STREAM)
        if (uris.isEmpty()) return null
        val stagedPairs = uris.mapNotNull { uri -> stageUriToCache(context, uri, mime) }
        if (stagedPairs.isEmpty()) return null
        if (stagedPairs.size == 1 && !mime.startsWith("image/") && !isImageName(stagedPairs.first().second)) {
          PendingSharedPayload.SingleFile(
            stagedUri = stagedPairs.first().first,
            displayName = stagedPairs.first().second,
            mimeType = mime,
          )
        } else {
          val label = if (stagedPairs.size == 1) stagedPairs.first().second
          else "Imágenes de WhatsApp (${stagedPairs.size})"
          PendingSharedPayload.MultipleImages(
            stagedUris = stagedPairs.map { it.first },
            displayName = label,
          )
        }
      }

      else -> null
    }
  }

  private fun stageUriToCache(context: Context, sourceUri: Uri, fallbackMime: String): Pair<Uri, String>? {
    return runCatching {
      val displayName = resolveName(context, sourceUri, fallbackMime)
      val inboxDir = File(context.cacheDir, "shared_inbox").apply { mkdirs() }
      // Limpiar archivos temporales antiguos (> 24 h)
      val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
      inboxDir.listFiles()?.forEach { f ->
        if (f.lastModified() < cutoff) runCatching { f.delete() }
      }
      val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
      val destFile = File(inboxDir, "${System.currentTimeMillis()}_$safeName")
      context.contentResolver.openInputStream(sourceUri)?.use { input ->
        FileOutputStream(destFile).use { output ->
          input.copyTo(output)
        }
      } ?: return null
      Uri.fromFile(destFile) to displayName
    }.getOrNull()
  }

  private fun resolveName(context: Context, uri: Uri, fallbackMime: String): String {
    if (uri.scheme == "content") {
      runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
          if (c.moveToFirst()) {
            val name = c.getString(0)
            if (!name.isNullOrBlank()) return name
          }
        }
      }
    }
    val seg = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    if (seg != null && seg.contains('.')) return seg
    val ext = when {
      fallbackMime.contains("pdf") -> "pdf"
      fallbackMime.contains("spreadsheet") || fallbackMime.contains("excel") -> "xlsx"
      fallbackMime.contains("csv") -> "csv"
      fallbackMime.contains("png") -> "png"
      fallbackMime.startsWith("image/") -> "jpg"
      else -> "bin"
    }
    return "catalogo_whatsapp.$ext"
  }

  private fun isImageName(name: String): Boolean {
    val l = name.lowercase()
    return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp")
  }

  @Suppress("DEPRECATION")
  private fun getParcelableUri(intent: Intent, key: String): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(key, Uri::class.java)
    } else {
      intent.getParcelableExtra(key)
    }
  }

  @Suppress("DEPRECATION")
  private fun getParcelableUriList(intent: Intent, key: String): List<Uri> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableArrayListExtra(key, Uri::class.java).orEmpty()
    } else {
      intent.getParcelableArrayListExtra<Uri>(key).orEmpty()
    }
  }
}
