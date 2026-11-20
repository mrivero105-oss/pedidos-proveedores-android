package com.pedidos.proveedores.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// Paleta híbrida: Minimalista Claro + Ejecutivo Azul-Cian/Esmeralda + Oscuro Premium estilo Isaac POS
val PrimaryLight = Color(0xFF0369A1)         // Azul cian ejecutivo
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE0F2FE)
val OnPrimaryContainerLight = Color(0xFF0C4A6E)

val SecondaryLight = Color(0xFF0F766E)       // Esmeralda comercial
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFCCFBF1)
val OnSecondaryContainerLight = Color(0xFF134E4A)

val TertiaryLight = Color(0xFFB45309)        // Ámbar alertas
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFEF3C7)
val OnTertiaryContainerLight = Color(0xFF78350F)

val BackgroundLight = Color(0xFFF5F7FA)      // Blanco niebla limpio
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFEDF2F7)
val OnSurfaceLight = Color(0xFF0F172A)
val OnSurfaceVariantLight = Color(0xFF475569)
val OutlineLight = Color(0xFFCBD5E1)
val OutlineVariantLight = Color(0xFFE2E8F0)

// Oscuro Premium estilo Isaac POS (grafito profundo + cian eléctrico + esmeralda)
val PrimaryDark = Color(0xFF38BDF8)
val OnPrimaryDark = Color(0xFF062336)
val PrimaryContainerDark = Color(0xFF0E3A57)
val OnPrimaryContainerDark = Color(0xFFE0F2FE)

val SecondaryDark = Color(0xFF34D399)
val OnSecondaryDark = Color(0xFF052E20)
val SecondaryContainerDark = Color(0xFF0B3B2E)
val OnSecondaryContainerDark = Color(0xFFD1FAE5)

val TertiaryDark = Color(0xFFFBBF24)
val OnTertiaryDark = Color(0xFF451A03)
val TertiaryContainerDark = Color(0xFF5C2D09)
val OnTertiaryContainerDark = Color(0xFFFEF3C7)

val BackgroundDark = Color(0xFF080D18)
val SurfaceDark = Color(0xFF111827)
val SurfaceVariantDark = Color(0xFF182336)
val OnSurfaceDark = Color(0xFFF1F5F9)
val OnSurfaceVariantDark = Color(0xFF94A3B8)
val OutlineDark = Color(0xFF334155)
val OutlineVariantDark = Color(0xFF223047)

// Degradado ejecutivo Hero (estilo Isaac POS)
val HeroGradientStartLight = Color(0xFF0C4A6E)
val HeroGradientEndLight = Color(0xFF0F766E)
val HeroGradientStartDark = Color(0xFF0F2942)
val HeroGradientEndDark = Color(0xFF0B3B3A)

// Colores semánticos de dominio
val WhatsAppGreen = Color(0xFF16A34A)
val WhatsAppGreenDark = Color(0xFF15803D)
val PdfBadgeColor = Color(0xFFDC2626)
val ExcelBadgeColor = Color(0xFF15803D)
val CsvBadgeColor = Color(0xFF2563EB)
val ImageBadgeColor = Color(0xFF7C3AED)
val StatusDraftColor = Color(0xFFD97706)
val StatusSentColor = Color(0xFF0284C7)
val StatusReceivedColor = Color(0xFF16A34A)
val LowStockAmber = Color(0xFFD97706)
val ZeroStockRed = Color(0xFFDC2626)
val HealthyStockGreen = Color(0xFF16A34A)

// Paleta de avatares por proveedor (6 tonos ejecutivos consistentes)
private val ProviderAvatarColors = listOf(
  Color(0xFF0284C7), // Cian
  Color(0xFF0F766E), // Esmeralda
  Color(0xFF4F46E5), // Índigo
  Color(0xFFD97706), // Ámbar
  Color(0xFF7C3AED), // Violeta
  Color(0xFFBE185D), // Carmesí
)

fun providerAvatarColor(name: String): Color {
  val idx = abs(name.trim().lowercase().hashCode()) % ProviderAvatarColors.size
  return ProviderAvatarColors[idx]
}

fun providerInitials(name: String): String {
  val words = name.trim()
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() && it.lowercase() !in setOf("de", "del", "la", "las", "los", "y", "c.a", "c.a.", "s.a", "s.a.") }
  return when {
    words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
    words.size == 1 -> words[0].take(2).uppercase()
    else -> "PR"
  }
}
