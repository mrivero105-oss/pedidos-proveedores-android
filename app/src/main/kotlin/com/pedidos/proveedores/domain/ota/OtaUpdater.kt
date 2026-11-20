package com.pedidos.proveedores.domain.ota

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface OtaCheckResult {
  data class UpdateAvailable(val release: OtaReleaseInfo) : OtaCheckResult
  data class UpToDate(val currentVersion: String, val latestTag: String?) : OtaCheckResult
  data class Error(val message: String) : OtaCheckResult
}

enum class OtaInstallLaunchResult {
  LAUNCHED_INSTALLER,
  NEEDS_UNKNOWN_SOURCES_PERMISSION,
  FAILED,
}

/**
 * Cliente OTA sobre GitHub Releases:
 * - Consulta la última release publicada en /repos/{owner}/{repo}/releases/latest.
 * - Descarga el APK a cacheDir/ota_updates/ con progreso y verificación SHA-256 opcional.
 * - Lanza el instalador nativo de Android mediante FileProvider conservando los datos SQLite.
 */
class OtaUpdater(private val context: Context) {

  suspend fun checkForUpdate(
    repoSlugRaw: String,
    currentVersionName: String,
  ): OtaCheckResult = withContext(Dispatchers.IO) {
    val slug = GitHubReleaseParser.normalizeRepoSlug(repoSlugRaw)
      ?: return@withContext OtaCheckResult.Error(
        "Repositorio inválido. Usa el formato usuario/repositorio (ej. mrivero105-oss/pedidos-proveedores-android)."
      )

    val endpoint = "https://api.github.com/repos/$slug/releases/latest"
    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = 15_000
      readTimeout = 20_000
      setRequestProperty("Accept", "application/vnd.github+json")
      setRequestProperty("User-Agent", "PedidosProveedores-Android/$currentVersionName")
    }

    try {
      val status = conn.responseCode
      val stream = if (status in 200..299) conn.inputStream else conn.errorStream
      val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

      when (status) {
        200 -> {
          val release = GitHubReleaseParser.parseLatestRelease(body)
            ?: return@withContext OtaCheckResult.Error(
              "La última release de $slug no contiene ningún archivo .apk adjunto."
            )
          if (OtaVersionComparator.isNewer(release.versionName, currentVersionName)) {
            OtaCheckResult.UpdateAvailable(release)
          } else {
            OtaCheckResult.UpToDate(currentVersion = currentVersionName, latestTag = release.tagName)
          }
        }
        404 -> OtaCheckResult.Error(
          "No se encontraron releases publicadas en GitHub ($slug). Verifica que el repositorio sea público y tenga al menos una Release."
        )
        403 -> OtaCheckResult.Error("Límite temporal de consultas a GitHub alcanzado. Intenta de nuevo en unos minutos.")
        else -> OtaCheckResult.Error("No se pudo consultar actualizaciones (HTTP $status).")
      }
    } catch (e: Exception) {
      OtaCheckResult.Error("Sin conexión o error al verificar OTA: ${e.localizedMessage ?: "revisa tu internet"}")
    } finally {
      conn.disconnect()
    }
  }

  suspend fun downloadApk(
    release: OtaReleaseInfo,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val updatesDir = File(context.cacheDir, "ota_updates").apply { mkdirs() }
      // Limpiar descargas previas para liberar espacio en el teléfono
      updatesDir.listFiles()?.forEach { runCatching { it.delete() } }

      val safeName = release.apkFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "update.apk" }
      val targetFile = File(updatesDir, safeName)

      val conn = openFollowingRedirects(release.apkDownloadUrl)
      try {
        val status = conn.responseCode
        if (status !in 200..299) {
          error("Error al descargar el APK (HTTP $status).")
        }

        val totalBytes = conn.contentLengthLong.takeIf { it > 0L } ?: release.apkSizeBytes
        val digest = MessageDigest.getInstance("SHA-256")
        var downloaded = 0L
        var lastReported = 0L

        conn.inputStream.use { input ->
          FileOutputStream(targetFile).use { output ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
              val read = input.read(buffer)
              if (read == -1) break
              output.write(buffer, 0, read)
              digest.update(buffer, 0, read)
              downloaded += read
              if (downloaded - lastReported >= 64 * 1024 || downloaded == totalBytes) {
                lastReported = downloaded
                onProgress(downloaded, totalBytes)
              }
            }
          }
        }
        onProgress(downloaded, maxOf(totalBytes, downloaded))

        // Verificar integridad SHA-256 si la release lo especificó
        val expectedHash = release.expectedSha256
        if (!expectedHash.isNullOrBlank()) {
          val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
          if (!actualHash.equals(expectedHash, ignoreCase = true)) {
            targetFile.delete()
            error("La verificación SHA-256 del APK falló. Descarga cancelada por seguridad.")
          }
        }

        targetFile
      } finally {
        conn.disconnect()
      }
    }
  }

  fun launchApkInstall(apkFile: File): OtaInstallLaunchResult {
    if (!apkFile.exists()) return OtaInstallLaunchResult.FAILED

    if (!context.packageManager.canRequestPackageInstalls()) {
      val settingsIntent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
      ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      return runCatching {
        context.startActivity(settingsIntent)
        OtaInstallLaunchResult.NEEDS_UNKNOWN_SOURCES_PERMISSION
      }.getOrDefault(OtaInstallLaunchResult.FAILED)
    }

    val contentUri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      apkFile,
    )
    val installIntent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(contentUri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
      context.startActivity(installIntent)
      OtaInstallLaunchResult.LAUNCHED_INSTALLER
    }.getOrDefault(OtaInstallLaunchResult.FAILED)
  }

  private fun openFollowingRedirects(startUrl: String): HttpURLConnection {
    var currentUrl = startUrl
    repeat(5) {
      val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = 20_000
        readTimeout = 60_000
        setRequestProperty("User-Agent", "PedidosProveedores-Android")
      }
      val code = conn.responseCode
      if (code in listOf(301, 302, 303, 307, 308)) {
        val next = conn.getHeaderField("Location")
        conn.disconnect()
        if (next.isNullOrBlank()) error("Redirección sin destino al descargar el APK.")
        currentUrl = next
      } else {
        return conn
      }
    }
    error("Demasiadas redirecciones al descargar el APK.")
  }
}
