package com.pedidos.proveedores.domain.reconcile

import com.pedidos.proveedores.domain.isaacpos.IsaacPosSyncMatcher
import com.pedidos.proveedores.domain.model.IsaacPosProducto
import com.pedidos.proveedores.domain.model.Producto
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class CatalogReconciliationLink(
  val localProductId: Long,
  val posProduct: IsaacPosProducto,
)

data class ReconcileCandidate(
  val posProduct: IsaacPosProducto,
  val score: Double,
  val reason: String,
) {
  val confidencePercent: Int
    get() = (score.coerceIn(0.0, 1.0) * 100.0).toInt()
}

data class ReconciledEntry(
  val localProduct: Producto,
  val posProduct: IsaacPosProducto,
)

data class SuggestedEntry(
  val localProduct: Producto,
  val candidates: List<ReconcileCandidate>,
) {
  val best: ReconcileCandidate
    get() = candidates.first()
}

data class ReconciliationReport(
  val vinculados: List<ReconciledEntry>,
  val sugeridos: List<SuggestedEntry>,
  val soloEnCatalogo: List<Producto>,
  val soloEnPos: List<IsaacPosProducto>,
)

/**
 * Motor puro (testeable en JVM) para conciliar los productos del catálogo de un vendedor
 * contra la base de datos sincronizada de Isaac POS.
 *
 * Evalúa coincidencias en 3 niveles:
 * 1. Vínculo existente (`isaacPosId`).
 * 2. Código de barras normalizado exacto (`>= 6` dígitos).
 * 3. Similitud inteligente por nombre, marca, prefijos, tolerancia a typos (Levenshtein <= 1)
 *    y comparación estricta de medidas normalizadas (g/kg y ml/L) para evitar cruzar tamaños.
 */
object CatalogReconciler {

  private const val MIN_SUGGESTION_SCORE = 0.48
  private const val MAX_CANDIDATES = 4

  private val STOPWORDS = setOf(
    "de", "del", "la", "las", "el", "los", "en", "con", "sin", "para", "por", "y", "o", "a",
    "un", "una", "und", "uni", "u", "x", "gr", "grs", "g", "kg", "kilo", "kilos", "ml", "cc",
    "l", "lt", "lts", "litro", "litros", "oz", "bulto", "caja", "bolsa", "paquete", "display",
    "tira", "sachet", "pote", "botella", "lata", "nr", "pet", "ret", "pack", "presentacion",
  )

  private val PACK_COUNT_REGEX = Regex("\\b\\d+\\s*[xX]\\s*\\d+\\b|\\b[xX]\\s*\\d+\\b")

  private val WEIGHT_REGEX = Regex("(\\d+(?:[.,]\\d+)?)\\s*(kg|kilos?|grs?|g|gramos?)\\b", RegexOption.IGNORE_CASE)
  private val VOLUME_REGEX = Regex("(\\d+(?:[.,]\\d+)?)\\s*(ml|cc|lts?|litros?|l)\\b", RegexOption.IGNORE_CASE)

  data class Measure(val kind: String, val baseValue: Double, val label: String)

  fun reconcile(
    providerName: String,
    catalogProducts: List<Producto>,
    allPosProducts: List<IsaacPosProducto>,
    allProviderProducts: List<Producto> = catalogProducts,
  ): ReconciliationReport {
    val posById = allPosProducts.associateBy { it.productoId }
    val posByBarcode = allPosProducts
      .filter { IsaacPosSyncMatcher.normalizeBarcodeKey(it.codigoBarra).length >= 6 }
      .groupBy { IsaacPosSyncMatcher.normalizeBarcodeKey(it.codigoBarra) }

    val providerNorm = IsaacPosSyncMatcher.normalizeKey(providerName)

    // Precomputar tokens y medidas de los ~917 productos del POS una sola vez (O(N))
    val indexedPos = allPosProducts.map { pos ->
      IndexedPos(
        pos = pos,
        normName = IsaacPosSyncMatcher.normalizeKey(pos.nombre),
        tokens = extractSignificantTokens(pos.nombre),
        measure = extractMeasure("${pos.nombre} ${pos.unidadPedido}"),
        sameProvider = providerNorm.isNotBlank() &&
          IsaacPosSyncMatcher.normalizeKey(pos.proveedorNombreRemoto) == providerNorm,
      )
    }

    val vinculados = mutableListOf<ReconciledEntry>()
    val sugeridos = mutableListOf<SuggestedEntry>()
    val soloEnCatalogo = mutableListOf<Producto>()
    val matchedPosIds = mutableSetOf<String>()

    for (local in catalogProducts) {
      val linkedPos = local.isaacPosId?.takeIf { it.isNotBlank() }?.let { posById[it] }
      if (linkedPos != null) {
        vinculados.add(ReconciledEntry(localProduct = local, posProduct = linkedPos))
        matchedPosIds.add(linkedPos.productoId)
        continue
      }

      // 1. Buscar primero por código de barras exacto
      val localBarcode = IsaacPosSyncMatcher.normalizeBarcodeKey(local.codigo)
      val barcodeMatches = if (localBarcode.length >= 6) {
        posByBarcode[localBarcode].orEmpty()
      } else {
        emptyList()
      }

      if (barcodeMatches.isNotEmpty()) {
        val candidates = barcodeMatches
          .sortedByDescending {
            providerNorm.isNotBlank() && IsaacPosSyncMatcher.normalizeKey(it.proveedorNombreRemoto) == providerNorm
          }
          .take(MAX_CANDIDATES)
          .map {
            ReconcileCandidate(
              posProduct = it,
              score = 1.0,
              reason = "Código de barras exacto",
            )
          }
        sugeridos.add(SuggestedEntry(localProduct = local, candidates = candidates))
        continue
      }

      // 2. Buscar por similitud inteligente (nombre + tokens + gramaje/volumen)
      val localNorm = IsaacPosSyncMatcher.normalizeKey(local.nombre)
      val localTokens = extractSignificantTokens(local.nombre)
      val localMeasure = extractMeasure("${local.nombre} ${local.unidad}")

      val scored = ArrayList<ReconcileCandidate>()
      for (idxPos in indexedPos) {
        val candidate = scorePair(
          localNorm = localNorm,
          localTokens = localTokens,
          localMeasure = localMeasure,
          idxPos = idxPos,
        )
        if (candidate != null && candidate.score >= MIN_SUGGESTION_SCORE) {
          scored.add(candidate)
        }
      }

      val top = scored
        .sortedWith(
          compareByDescending<ReconcileCandidate> { it.score }
            .thenByDescending { it.posProduct.stockActual > 0.0 }
            .thenBy { it.posProduct.nombre.lowercase() }
        )
        .take(MAX_CANDIDATES)

      if (top.isNotEmpty()) {
        sugeridos.add(SuggestedEntry(localProduct = local, candidates = top))
      } else {
        soloEnCatalogo.add(local)
      }
    }

    // Identificar productos del proveedor que existen en el POS pero no quedaron vinculados en este catálogo
    val providerPosIdsFromLocal = allProviderProducts
      .mapNotNull { it.isaacPosId?.takeIf { id -> id.isNotBlank() } }
      .toSet()

    val soloEnPos = allPosProducts
      .filter { pos ->
        val belongsToProvider = (providerNorm.isNotBlank() &&
          IsaacPosSyncMatcher.normalizeKey(pos.proveedorNombreRemoto) == providerNorm) ||
          pos.productoId in providerPosIdsFromLocal
        belongsToProvider && pos.productoId !in matchedPosIds
      }
      .sortedWith(
        compareBy<IsaacPosProducto> { it.stockActual }
          .thenBy { it.nombre.lowercase() }
      )

    return ReconciliationReport(
      vinculados = vinculados.sortedWith(
        compareBy<ReconciledEntry> { it.posProduct.stockActual }
          .thenBy { it.localProduct.nombre.lowercase() }
      ),
      sugeridos = sugeridos.sortedByDescending { it.best.score },
      soloEnCatalogo = soloEnCatalogo.sortedBy { it.nombre.lowercase() },
      soloEnPos = soloEnPos,
    )
  }

  /**
   * Busca manualmente dentro de toda la base de datos Isaac POS para vincular un producto.
   */
  fun searchPosProducts(
    query: String,
    allPosProducts: List<IsaacPosProducto>,
    providerName: String = "",
    limit: Int = 40,
  ): List<IsaacPosProducto> {
    val qNorm = IsaacPosSyncMatcher.normalizeKey(query)
    val qDigits = query.filter { it.isDigit() }
    val provNorm = IsaacPosSyncMatcher.normalizeKey(providerName)

    if (qNorm.isBlank() && qDigits.isBlank()) {
      return allPosProducts
        .sortedWith(
          compareByDescending<IsaacPosProducto> {
            provNorm.isNotBlank() && IsaacPosSyncMatcher.normalizeKey(it.proveedorNombreRemoto) == provNorm
          }.thenByDescending { it.stockActual > 0.0 }
            .thenBy { it.nombre.lowercase() }
        )
        .take(limit)
    }

    val qTokens = qNorm.split(' ').filter { it.isNotBlank() }
    return allPosProducts
      .mapNotNull { pos ->
        val nameNorm = IsaacPosSyncMatcher.normalizeKey(pos.nombre)
        val catNorm = IsaacPosSyncMatcher.normalizeKey(pos.categoria)
        val provRemoteNorm = IsaacPosSyncMatcher.normalizeKey(pos.proveedorNombreRemoto)
        val barcodeDigits = pos.codigoBarra.filter { it.isDigit() }

        val score = when {
          qDigits.length >= 4 && barcodeDigits.contains(qDigits) -> 100.0
          nameNorm == qNorm -> 95.0
          nameNorm.contains(qNorm) -> 80.0
          qTokens.all { tok -> nameNorm.contains(tok) || catNorm.contains(tok) || provRemoteNorm.contains(tok) } -> 65.0
          else -> {
            val matched = qTokens.count { tok -> tok.length >= 3 && (nameNorm.contains(tok) || catNorm.contains(tok)) }
            if (matched > 0 && matched * 2 >= qTokens.size) 30.0 + matched * 10.0 else 0.0
          }
        }
        if (score <= 0.0) return@mapNotNull null
        val provBonus = if (provNorm.isNotBlank() && provRemoteNorm == provNorm) 5.0 else 0.0
        pos to (score + provBonus)
      }
      .sortedWith(compareByDescending<Pair<IsaacPosProducto, Double>> { it.second }.thenBy { it.first.nombre.lowercase() })
      .take(limit)
      .map { it.first }
  }

  private data class IndexedPos(
    val pos: IsaacPosProducto,
    val normName: String,
    val tokens: List<String>,
    val measure: Measure?,
    val sameProvider: Boolean,
  )

  private fun scorePair(
    localNorm: String,
    localTokens: List<String>,
    localMeasure: Measure?,
    idxPos: IndexedPos,
  ): ReconcileCandidate? {
    if (localNorm.isBlank() || idxPos.normName.isBlank()) return null

    if (localNorm == idxPos.normName) {
      return ReconcileCandidate(
        posProduct = idxPos.pos,
        score = 0.98,
        reason = "Nombre exacto",
      )
    }

    if (localTokens.isEmpty() || idxPos.tokens.isEmpty()) return null

    // Evaluar similitud bidireccional de tokens con soporte a prefijos y typos leves
    var sumA = 0.0
    var strongMatches = 0
    for (a in localTokens) {
      var bestTok = 0.0
      for (b in idxPos.tokens) {
        val sim = tokenSimilarity(a, b)
        if (sim > bestTok) bestTok = sim
      }
      if (bestTok >= 0.80) strongMatches++
      sumA += bestTok
    }
    if (strongMatches == 0) return null

    var sumB = 0.0
    for (b in idxPos.tokens) {
      var bestTok = 0.0
      for (a in localTokens) {
        val sim = tokenSimilarity(a, b)
        if (sim > bestTok) bestTok = sim
      }
      sumB += bestTok
    }

    val precision = sumA / localTokens.size.toDouble()
    val recall = sumB / idxPos.tokens.size.toDouble()
    var score = if (precision + recall > 0.0) {
      (2.0 * precision * recall) / (precision + recall)
    } else {
      0.0
    }

    // Si todos los tokens de la lista más corta están presentes, subir cobertura (ej. "Harina PAN" en "Harina PAN Maíz Blanco")
    val minCover = max(precision, recall)
    if (minCover >= 0.85 && min(localTokens.size, idxPos.tokens.size) >= 2) {
      score = max(score, 0.72 * minCover)
    }

    val reasonParts = mutableListOf<String>()
    reasonParts.add("Similitud de nombre")

    // Ajuste estricto por gramaje / volumen para no cruzar tamaños diferentes (ej. 100g vs 500g)
    if (localMeasure != null && idxPos.measure != null) {
      if (localMeasure.kind == idxPos.measure.kind) {
        val ratioDiff = abs(localMeasure.baseValue - idxPos.measure.baseValue) /
          max(localMeasure.baseValue, idxPos.measure.baseValue).coerceAtLeast(1.0)
        if (ratioDiff <= 0.05) {
          score += 0.16
          reasonParts.add("Mismo tamaño (${localMeasure.label})")
        } else {
          score *= 0.35
        }
      } else {
        score *= 0.45
      }
    }

    if (idxPos.sameProvider) {
      score += 0.06
      reasonParts.add("Mismo proveedor POS")
    }

    val finalScore = score.coerceIn(0.0, 0.96)
    return ReconcileCandidate(
      posProduct = idxPos.pos,
      score = finalScore,
      reason = reasonParts.joinToString(" · "),
    )
  }

  internal fun extractSignificantTokens(raw: String): List<String> {
    val withoutPack = PACK_COUNT_REGEX.replace(raw, " ")
    val withoutMeasures = WEIGHT_REGEX.replace(VOLUME_REGEX.replace(withoutPack, " "), " ")
    val normalized = IsaacPosSyncMatcher.normalizeKey(withoutMeasures)
    return normalized
      .split(' ')
      .filter { token ->
        token.length >= 2 &&
          token !in STOPWORDS &&
          !token.all { it.isDigit() }
      }
      .distinct()
  }

  internal fun extractMeasure(raw: String): Measure? {
    val cleaned = PACK_COUNT_REGEX.replace(raw, " ")
    val w = WEIGHT_REGEX.find(cleaned)
    if (w != null) {
      val num = w.groupValues[1].replace(',', '.').toDoubleOrNull()
      val unit = w.groupValues[2].lowercase()
      if (num != null && num > 0.0) {
        val grams = if (unit.startsWith("k")) num * 1000.0 else num
        val label = if (grams >= 1000.0 && abs(grams % 1000.0) < 0.01) {
          "${(grams / 1000.0).toInt()}kg"
        } else {
          "${grams.toInt()}g"
        }
        return Measure(kind = "WEIGHT", baseValue = grams, label = label)
      }
    }

    val v = VOLUME_REGEX.find(cleaned)
    if (v != null) {
      val num = v.groupValues[1].replace(',', '.').toDoubleOrNull()
      val unit = v.groupValues[2].lowercase()
      if (num != null && num > 0.0) {
        val ml = if (unit.startsWith("l")) num * 1000.0 else num
        val label = if (ml >= 1000.0 && abs(ml % 1000.0) < 0.01) {
          "${(ml / 1000.0).toInt()}L"
        } else {
          "${ml.toInt()}ml"
        }
        return Measure(kind = "VOLUME", baseValue = ml, label = label)
      }
    }
    return null
  }

  private fun tokenSimilarity(a: String, b: String): Double {
    if (a == b) return 1.0
    val minLen = min(a.length, b.length)
    val maxLen = max(a.length, b.length)
    if (minLen >= 4 && (a.startsWith(b) || b.startsWith(a))) {
      return 0.85
    }
    if (minLen >= 5 && abs(a.length - b.length) <= 1 && levenshtein(a, b) == 1) {
      return 0.88
    }
    if (minLen >= 7 && abs(a.length - b.length) <= 2 && levenshtein(a, b) <= 2) {
      return 0.78
    }
    // Plural simple español ("galletas" vs "galleta", "chupetas" vs "chupeta")
    if (maxLen >= 5 && (a == "${b}s" || b == "${a}s" || a == "${b}es" || b == "${a}es")) {
      return 0.95
    }
    return 0.0
  }

  private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val prev = IntArray(b.length + 1) { it }
    val curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
      curr[0] = i
      for (j in 1..b.length) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        curr[j] = minOf(
          prev[j] + 1,
          curr[j - 1] + 1,
          prev[j - 1] + cost,
        )
      }
      curr.copyInto(prev)
    }
    return prev[b.length]
  }
}
