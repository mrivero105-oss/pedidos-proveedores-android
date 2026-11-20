package com.pedidos.proveedores.domain.ota

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class OtaReleaseInfo(
  val tagName: String,
  val versionName: String,
  val releaseTitle: String,
  val releaseNotes: String,
  val apkFileName: String,
  val apkDownloadUrl: String,
  val apkSizeBytes: Long,
  val expectedSha256: String? = null,
  val htmlUrl: String = "",
  val publishedAt: String = "",
)

@Serializable
private data class GitHubReleaseDto(
  @SerialName("tag_name") val tagName: String = "",
  val name: String? = null,
  val body: String? = null,
  @SerialName("html_url") val htmlUrl: String = "",
  @SerialName("published_at") val publishedAt: String = "",
  val draft: Boolean = false,
  val prerelease: Boolean = false,
  val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
private data class GitHubAssetDto(
  val name: String = "",
  val size: Long = 0L,
  @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

/**
 * Parsea la respuesta JSON de GitHub Releases API (/repos/{owner}/{repo}/releases/latest)
 * para encontrar el APK instalable y su hash SHA-256 opcional.
 */
object GitHubReleaseParser {

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
  }

  private val sha256Regex = Regex("""(?i)\bsha-?256\s*[:=]\s*([a-f0-9]{64})\b""")

  fun parseLatestRelease(rawJson: String): OtaReleaseInfo? {
    if (rawJson.isBlank()) return null
    val dto = runCatching { json.decodeFromString<GitHubReleaseDto>(rawJson) }.getOrNull() ?: return null
    if (dto.draft || dto.tagName.isBlank()) return null

    val apkAssets = dto.assets.filter {
      it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.startsWith("https://")
    }
    if (apkAssets.isEmpty()) return null

    // Preferir APK release firmado si existe; si no, el primer APK disponible
    val chosenAsset = apkAssets.firstOrNull { it.name.contains("release", ignoreCase = true) }
      ?: apkAssets.first()

    val notes = dto.body.orEmpty().trim()
    val expectedSha = sha256Regex.find(notes)?.groupValues?.get(1)?.lowercase()

    return OtaReleaseInfo(
      tagName = dto.tagName.trim(),
      versionName = OtaVersionComparator.normalizeTag(dto.tagName),
      releaseTitle = dto.name?.takeIf { it.isNotBlank() } ?: dto.tagName.trim(),
      releaseNotes = notes,
      apkFileName = chosenAsset.name,
      apkDownloadUrl = chosenAsset.browserDownloadUrl,
      apkSizeBytes = chosenAsset.size,
      expectedSha256 = expectedSha,
      htmlUrl = dto.htmlUrl,
      publishedAt = dto.publishedAt,
    )
  }

  /**
   * Valida y limpia el slug "usuario/repositorio" (también acepta una URL completa de GitHub).
   */
  fun normalizeRepoSlug(raw: String): String? {
    var cleaned = raw.trim()
      .removePrefix("https://github.com/")
      .removePrefix("http://github.com/")
      .removePrefix("github.com/")
      .removeSuffix(".git")
      .trim('/')
    val parts = cleaned.split('/').filter { it.isNotBlank() }
    if (parts.size < 2) return null
    val owner = parts[0]
    val repo = parts[1]
    val validPattern = Regex("^[A-Za-z0-9._-]+$")
    return if (validPattern.matches(owner) && validPattern.matches(repo)) "$owner/$repo" else null
  }
}
