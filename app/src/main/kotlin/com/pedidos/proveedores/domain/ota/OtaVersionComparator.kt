package com.pedidos.proveedores.domain.ota

/**
 * Comparador semántico de versiones para determinar si una Release de GitHub
 * es más reciente que la versión instalada en el teléfono.
 * Soporta formatos: "v1.2.0", "1.2.0", "v1.10.3-beta", "2.0".
 */
object OtaVersionComparator {

  fun normalizeTag(raw: String): String {
    return raw.trim().removePrefix("v").removePrefix("V").trim()
  }

  fun isNewer(remoteVersionRaw: String, currentVersionRaw: String): Boolean {
    return compare(remoteVersionRaw, currentVersionRaw) > 0
  }

  fun compare(aRaw: String, bRaw: String): Int {
    val aParts = parseNumericCore(aRaw)
    val bParts = parseNumericCore(bRaw)
    val maxLen = maxOf(aParts.size, bParts.size)
    for (i in 0 until maxLen) {
      val av = aParts.getOrElse(i) { 0 }
      val bv = bParts.getOrElse(i) { 0 }
      if (av != bv) return av.compareTo(bv)
    }

    // Si los números base son iguales: versión estable > pre-release (ej. 1.2.0 > 1.2.0-beta)
    val aHasPre = normalizeTag(aRaw).contains('-')
    val bHasPre = normalizeTag(bRaw).contains('-')
    return when {
      !aHasPre && bHasPre -> 1
      aHasPre && !bHasPre -> -1
      else -> 0
    }
  }

  private fun parseNumericCore(raw: String): List<Int> {
    val core = normalizeTag(raw).substringBefore('-').substringBefore('+')
    if (core.isBlank()) return listOf(0)
    return core.split('.').map { segment ->
      segment.filter { it.isDigit() }.toIntOrNull() ?: 0
    }
  }
}
