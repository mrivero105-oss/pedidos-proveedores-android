package com.pedidos.proveedores.domain.ota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaVersionAndReleaseParserTest {

  @Test
  fun comparesSemanticVersionsCorrectly() {
    assertTrue(OtaVersionComparator.isNewer("v1.0.1", "1.0.0"))
    assertTrue(OtaVersionComparator.isNewer("v1.10.0", "1.2.0"))
    assertTrue(OtaVersionComparator.isNewer("2.0", "1.9.9"))
    assertTrue(OtaVersionComparator.isNewer("1.2.0", "1.2.0-beta"))

    assertFalse(OtaVersionComparator.isNewer("v1.0.0", "1.0.0"))
    assertFalse(OtaVersionComparator.isNewer("v0.9.9", "1.0.0"))
    assertFalse(OtaVersionComparator.isNewer("1.2.0-beta", "1.2.0"))
  }

  @Test
  fun parsesGitHubLatestReleasePreferringReleaseApkAndExtractingSha256() {
    val sampleJson = """
      {
        "tag_name": "v1.2.0",
        "name": "Pedidos Proveedores v1.2.0",
        "body": "Mejoras:\n- Carga por imágenes\n- Auto-actualización OTA\n\nSHA256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "html_url": "https://github.com/mrivero105-oss/pedidos-proveedores-android/releases/tag/v1.2.0",
        "published_at": "2026-11-20T12:00:00Z",
        "draft": false,
        "prerelease": false,
        "assets": [
          {
            "name": "pedidos-proveedores-1.2.0-debug.apk",
            "size": 70000000,
            "browser_download_url": "https://github.com/mrivero105-oss/pedidos-proveedores-android/releases/download/v1.2.0/pedidos-proveedores-1.2.0-debug.apk"
          },
          {
            "name": "pedidos-proveedores-1.2.0-release.apk",
            "size": 65000000,
            "browser_download_url": "https://github.com/mrivero105-oss/pedidos-proveedores-android/releases/download/v1.2.0/pedidos-proveedores-1.2.0-release.apk"
          }
        ]
      }
    """.trimIndent()

    val info = GitHubReleaseParser.parseLatestRelease(sampleJson)
    assertNotNull(info)
    assertEquals("v1.2.0", info!!.tagName)
    assertEquals("1.2.0", info.versionName)
    assertEquals("pedidos-proveedores-1.2.0-release.apk", info.apkFileName)
    assertEquals(65000000L, info.apkSizeBytes)
    assertEquals(
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      info.expectedSha256,
    )
  }

  @Test
  fun normalizesGitHubRepoSlugsAndUrls() {
    assertEquals(
      "mrivero105-oss/pedidos-proveedores-android",
      GitHubReleaseParser.normalizeRepoSlug("https://github.com/mrivero105-oss/pedidos-proveedores-android.git"),
    )
    assertEquals(
      "mrivero105-oss/pedidos-proveedores-android",
      GitHubReleaseParser.normalizeRepoSlug("mrivero105-oss/pedidos-proveedores-android/"),
    )
    assertNull(GitHubReleaseParser.normalizeRepoSlug("solo-un-texto"))
  }
}
