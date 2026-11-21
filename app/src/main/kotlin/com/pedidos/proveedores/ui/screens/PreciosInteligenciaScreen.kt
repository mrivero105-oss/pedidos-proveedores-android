package com.pedidos.proveedores.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCartCheckout
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.data.local.PricingSettings
import com.pedidos.proveedores.domain.model.MonedaCatalogo
import com.pedidos.proveedores.domain.parser.PriceParser
import com.pedidos.proveedores.domain.pricing.EvaluatedOffer
import com.pedidos.proveedores.domain.pricing.MarginAlertItem
import com.pedidos.proveedores.domain.pricing.MarginHealth
import com.pedidos.proveedores.domain.pricing.OptimalOrderLine
import com.pedidos.proveedores.domain.pricing.OptimalOrderPlan
import com.pedidos.proveedores.domain.pricing.PriceChangeItem
import com.pedidos.proveedores.domain.pricing.PricingIntelligenceEngine
import com.pedidos.proveedores.domain.pricing.PricingIntelligenceSummary
import com.pedidos.proveedores.domain.pricing.SupplierComparisonItem
import com.pedidos.proveedores.ui.components.PricingSettingsDialog
import com.pedidos.proveedores.ui.components.ProductPackTaxDialog
import com.pedidos.proveedores.ui.theme.HealthyStockGreen
import com.pedidos.proveedores.ui.theme.HeroGradientEndDark
import com.pedidos.proveedores.ui.theme.HeroGradientEndLight
import com.pedidos.proveedores.ui.theme.HeroGradientStartDark
import com.pedidos.proveedores.ui.theme.HeroGradientStartLight
import com.pedidos.proveedores.ui.theme.LowStockAmber
import com.pedidos.proveedores.ui.theme.WhatsAppGreen
import com.pedidos.proveedores.ui.theme.ZeroStockRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SUBTAB_MARGENES = 0
private const val SUBTAB_COMPARADOR = 1
private const val SUBTAB_SUBIDAS = 2
private const val SUBTAB_OPTIMO = 3

private const val MARGIN_FILTER_ALERTAS = 0
private const val MARGIN_FILTER_CRITICOS = 1
private const val MARGIN_FILTER_BAJO_META = 2
private const val MARGIN_FILTER_TODOS = 3

private const val CHANGE_FILTER_TODAS = 0
private const val CHANGE_FILTER_SUBIERON = 1
private const val CHANGE_FILTER_BAJARON = 2

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreciosInteligenciaScreen(
  summary: PricingIntelligenceSummary,
  pricingSettings: PricingSettings,
  lowStockThreshold: Double,
  bcvRefreshing: Boolean,
  onFetchBcvRate: () -> Unit,
  onSavePricingSettings: (
    tasaBsPorUsd: Double,
    tasaSourceLabel: String,
    targetMarkupPercent: Double,
    criticalMarkupPercent: Double,
    defaultIvaPercent: Double,
    minOptimalSavingsPercent: Double,
  ) -> Unit,
  onUpdateMinSavingsPercent: (Double) -> Unit,
  onUpdateProductPackTax: (productoId: Long, unidadesPorEmpaque: Double?, esGravado: Boolean?) -> Unit,
  onCopyPvpList: (List<MarginAlertItem>) -> Unit,
  onSharePvpWhatsApp: (List<MarginAlertItem>) -> Unit,
  onExportPvpCsv: (List<MarginAlertItem>) -> Unit,
  onCreateOptimalDrafts: (OptimalOrderPlan) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedSubTab by rememberSaveable { mutableIntStateOf(SUBTAB_MARGENES) }
  var searchQuery by remember { mutableStateOf("") }
  var marginFilter by rememberSaveable { mutableIntStateOf(MARGIN_FILTER_ALERTAS) }
  var changeFilter by rememberSaveable { mutableIntStateOf(CHANGE_FILTER_TODAS) }
  var onlyLowStockOptimal by rememberSaveable { mutableStateOf(true) }

  var manualProviderOverrides by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
  var quantitiesByKey by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
  var excludedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

  var showSettingsDialog by remember { mutableStateOf(false) }
  var editingPackOffer by remember { mutableStateOf<EvaluatedOffer?>(null) }

  val queryTrim = searchQuery.trim()

  val filteredComparisons = remember(summary.comparisons, queryTrim) {
    if (queryTrim.isEmpty()) summary.comparisons
    else summary.comparisons.filter { item ->
      item.displayName.contains(queryTrim, ignoreCase = true) ||
        item.codigoBarra.contains(queryTrim, ignoreCase = true) ||
        item.offers.any { it.proveedor.nombre.contains(queryTrim, ignoreCase = true) }
    }
  }

  val filteredMargins = remember(summary.marginAlerts, marginFilter, queryTrim) {
    val byStatus = when (marginFilter) {
      MARGIN_FILTER_CRITICOS -> summary.marginAlerts.filter {
        it.primaryOffer.health == MarginHealth.LOSS || it.primaryOffer.health == MarginHealth.CRITICAL
      }
      MARGIN_FILTER_BAJO_META -> summary.marginAlerts.filter {
        it.primaryOffer.health == MarginHealth.BELOW_TARGET
      }
      MARGIN_FILTER_ALERTAS -> summary.marginAlerts.filter {
        it.primaryOffer.health == MarginHealth.LOSS ||
          it.primaryOffer.health == MarginHealth.CRITICAL ||
          it.primaryOffer.health == MarginHealth.BELOW_TARGET
      }
      else -> summary.marginAlerts
    }
    if (queryTrim.isEmpty()) byStatus
    else byStatus.filter { item ->
      item.primaryOffer.displayName.contains(queryTrim, ignoreCase = true) ||
        item.primaryOffer.proveedor.nombre.contains(queryTrim, ignoreCase = true) ||
        item.primaryOffer.producto.codigo.contains(queryTrim, ignoreCase = true)
    }
  }

  val filteredChanges = remember(summary.priceChanges, changeFilter, queryTrim) {
    val byDir = when (changeFilter) {
      CHANGE_FILTER_SUBIERON -> summary.priceChanges.filter { it.isIncrease }
      CHANGE_FILTER_BAJARON -> summary.priceChanges.filter { !it.isIncrease }
      else -> summary.priceChanges
    }
    if (queryTrim.isEmpty()) byDir
    else byDir.filter { ch ->
      ch.productName.contains(queryTrim, ignoreCase = true) ||
        ch.proveedorNombre.contains(queryTrim, ignoreCase = true)
    }
  }

  val optimalPlan = remember(
    summary.allOffers,
    lowStockThreshold,
    onlyLowStockOptimal,
    pricingSettings.minOptimalSavingsPercent,
    manualProviderOverrides,
    quantitiesByKey,
    excludedKeys,
  ) {
    PricingIntelligenceEngine.buildOptimalOrderPlan(
      allOffers = summary.allOffers,
      lowStockThreshold = lowStockThreshold,
      onlyLowStock = onlyLowStockOptimal,
      minSavingsPercent = pricingSettings.minOptimalSavingsPercent,
      manualProviderOverrides = manualProviderOverrides,
      quantitiesByKey = quantitiesByKey,
      excludedKeys = excludedKeys,
    )
  }

  val filteredOptimalLines = remember(optimalPlan.lines, queryTrim) {
    if (queryTrim.isEmpty()) optimalPlan.lines
    else optimalPlan.lines.filter { line ->
      line.displayName.contains(queryTrim, ignoreCase = true) ||
        line.selectedOffer.proveedor.nombre.contains(queryTrim, ignoreCase = true) ||
        line.codigo.contains(queryTrim, ignoreCase = true)
    }
  }

  val totalAlertasMargen = summary.criticalOrLossCount + summary.belowTargetCount
  val totalSubidas = remember(summary.priceChanges) { summary.priceChanges.count { it.isIncrease } }
  val isDark = isSystemInDarkTheme()
  val heroBrush = remember(isDark) {
    Brush.linearGradient(
      colors = if (isDark) listOf(HeroGradientStartDark, HeroGradientEndDark)
      else listOf(HeroGradientStartLight, HeroGradientEndLight)
    )
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Hero compacto de Inteligencia de Compras
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(heroBrush)
          .padding(horizontal = 16.dp, vertical = 14.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Precios y Márgenes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
              )
              Text(
                text = "Comparador multi-proveedor, rentabilidad y pedido óptimo",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.82f),
              )
            }
            Surface(
              color = Color.White.copy(alpha = 0.16f),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.clickable { showSettingsDialog = true },
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.Rounded.Tune, contentDescription = "Configurar", tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                  text = "Meta +${PriceParser.formatQuantity(pricingSettings.targetMarkupPercent)}%",
                  style = MaterialTheme.typography.labelMedium,
                  color = Color.White,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }

          // Barra de Tasa BCV
          Surface(
            color = Color.White.copy(alpha = 0.14f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(
                modifier = Modifier
                  .weight(1f)
                  .clickable { showSettingsDialog = true },
              ) {
                val rateLabel = if (pricingSettings.hasValidExchangeRate) {
                  "Tasa: Bs. ${"%.2f".format(Locale.US, pricingSettings.tasaBsPorUsd)} / USD"
                } else {
                  "Tasa Bs/USD sin configurar"
                }
                Text(
                  text = rateLabel,
                  style = MaterialTheme.typography.labelLarge,
                  color = Color.White,
                  fontWeight = FontWeight.SemiBold,
                )
                val sourceInfo = pricingSettings.tasaSourceLabel.ifBlank { "Toca para ingresar o actualizar BCV" }
                Text(
                  text = sourceInfo,
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.78f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              TextButton(
                onClick = onFetchBcvRate,
                enabled = !bcvRefreshing,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
              ) {
                if (bcvRefreshing) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                  )
                } else {
                  Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text("BCV hoy", color = Color.White, style = MaterialTheme.typography.labelMedium)
              }
            }
          }

          // 3 KPIs rápidos
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            PricingHeroKpi(
              value = totalAlertasMargen.toString(),
              label = if (summary.criticalOrLossCount > 0) "${summary.criticalOrLossCount} críticos" else "Alertas PVP",
              accent = when {
                summary.criticalOrLossCount > 0 -> Color(0xFFFCA5A5)
                summary.belowTargetCount > 0 -> Color(0xFFFCD34D)
                else -> Color(0xFF6EE7B7)
              },
              onClick = { selectedSubTab = SUBTAB_MARGENES },
              modifier = Modifier.weight(1f),
            )
            PricingHeroKpi(
              value = summary.comparisons.size.toString(),
              label = "Comparables",
              accent = Color(0xFF7DD3FC),
              onClick = { selectedSubTab = SUBTAB_COMPARADOR },
              modifier = Modifier.weight(1f),
            )
            PricingHeroKpi(
              value = totalSubidas.toString(),
              label = "Subidas",
              accent = if (totalSubidas > 0) Color(0xFFFCD34D) else Color(0xFF6EE7B7),
              onClick = { selectedSubTab = SUBTAB_SUBIDAS },
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }

    if (summary.missingRateOffersCount > 0) {
      item {
        Surface(
          color = LowStockAmber.copy(alpha = 0.14f),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, LowStockAmber.copy(alpha = 0.5f)),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = LowStockAmber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
              text = "${summary.missingRateOffersCount} producto(s) en Bolívares esperan tasa Bs/USD.",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onFetchBcvRate, enabled = !bcvRefreshing) {
              Text("Traer BCV")
            }
          }
        }
      }
    }

    // 4 sub-pestañas
    item {
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        item {
          FilterChip(
            selected = selectedSubTab == SUBTAB_MARGENES,
            onClick = { selectedSubTab = SUBTAB_MARGENES },
            label = { Text("Márgenes ($totalAlertasMargen)") },
          )
        }
        item {
          FilterChip(
            selected = selectedSubTab == SUBTAB_COMPARADOR,
            onClick = { selectedSubTab = SUBTAB_COMPARADOR },
            label = { Text("Comparador (${summary.comparisons.size})") },
          )
        }
        item {
          FilterChip(
            selected = selectedSubTab == SUBTAB_SUBIDAS,
            onClick = { selectedSubTab = SUBTAB_SUBIDAS },
            label = { Text("Subidas (${summary.priceChanges.size})") },
          )
        }
        item {
          FilterChip(
            selected = selectedSubTab == SUBTAB_OPTIMO,
            onClick = { selectedSubTab = SUBTAB_OPTIMO },
            label = { Text("Pedido Óptimo (${optimalPlan.lines.size})") },
          )
        }
      }
    }

    // Buscador común
    item {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Buscar producto, código o proveedor...") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
          if (searchQuery.isNotBlank()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Rounded.Close, contentDescription = "Limpiar")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
      )
    }

    when (selectedSubTab) {
      SUBTAB_MARGENES -> {
        item {
          LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            item {
              FilterChip(
                selected = marginFilter == MARGIN_FILTER_ALERTAS,
                onClick = { marginFilter = MARGIN_FILTER_ALERTAS },
                label = { Text("Alertas ($totalAlertasMargen)") },
              )
            }
            item {
              FilterChip(
                selected = marginFilter == MARGIN_FILTER_CRITICOS,
                onClick = { marginFilter = MARGIN_FILTER_CRITICOS },
                label = { Text("Crítico/Pérdida (${summary.criticalOrLossCount})") },
              )
            }
            item {
              FilterChip(
                selected = marginFilter == MARGIN_FILTER_BAJO_META,
                onClick = { marginFilter = MARGIN_FILTER_BAJO_META },
                label = { Text("Bajo meta (${summary.belowTargetCount})") },
              )
            }
            item {
              FilterChip(
                selected = marginFilter == MARGIN_FILTER_TODOS,
                onClick = { marginFilter = MARGIN_FILTER_TODOS },
                label = { Text("Todos (${summary.marginAlerts.size})") },
              )
            }
          }
        }

        if (filteredMargins.isNotEmpty()) {
          item {
            Card(
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = "Exportar lista de PVP sugeridos (${filteredMargins.size} productos)",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  OutlinedButton(
                    onClick = { onCopyPvpList(filteredMargins) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copiar", style = MaterialTheme.typography.labelMedium)
                  }
                  FilledTonalButton(
                    onClick = { onSharePvpWhatsApp(filteredMargins) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(
                      Icons.AutoMirrored.Rounded.Send,
                      contentDescription = null,
                      tint = WhatsAppGreen,
                      modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("WhatsApp", color = WhatsAppGreen, style = MaterialTheme.typography.labelMedium)
                  }
                  OutlinedButton(
                    onClick = { onExportPvpCsv(filteredMargins) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CSV", style = MaterialTheme.typography.labelMedium)
                  }
                }
              }
            }
          }
        }

        if (filteredMargins.isEmpty()) {
          item {
            EmptyPricingNotice(
              title = if (summary.marginAlerts.isEmpty()) "Sin productos evaluados aún" else "Sin alertas en este filtro",
              body = if (summary.marginAlerts.isEmpty()) {
                "Sincroniza Isaac POS y concilia el catálogo de tus proveedores para ver tu ganancia real y el PVP sugerido."
              } else {
                "Todos tus productos en esta vista están cumpliendo tu meta de ganancia (+${PriceParser.formatQuantity(pricingSettings.targetMarkupPercent)}% s/costo)."
              },
            )
          }
        } else {
          items(filteredMargins, key = { "margin_${it.canonicalKey}" }) { item ->
            MarginAlertCard(
              item = item,
              targetMarkup = pricingSettings.targetMarkupPercent,
              onEditPackTax = { editingPackOffer = item.primaryOffer },
              modifier = Modifier.padding(horizontal = 16.dp),
            )
          }
        }
      }

      SUBTAB_COMPARADOR -> {
        if (filteredComparisons.isEmpty()) {
          item {
            EmptyPricingNotice(
              title = "Aún no hay productos compartidos entre 2+ proveedores",
              body = "Cuando concilies el mismo producto de Isaac POS en catálogos de dos o más proveedores, aquí verás automáticamente quién vende más barato por unidad y cuánto ahorras.",
            )
          }
        } else {
          items(filteredComparisons, key = { "cmp_${it.canonicalKey}" }) { cmp ->
            SupplierComparisonCard(
              item = cmp,
              onEditOfferPackTax = { editingPackOffer = it },
              modifier = Modifier.padding(horizontal = 16.dp),
            )
          }
        }
      }

      SUBTAB_SUBIDAS -> {
        item {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterChip(
              selected = changeFilter == CHANGE_FILTER_TODAS,
              onClick = { changeFilter = CHANGE_FILTER_TODAS },
              label = { Text("Todas (${summary.priceChanges.size})") },
            )
            FilterChip(
              selected = changeFilter == CHANGE_FILTER_SUBIERON,
              onClick = { changeFilter = CHANGE_FILTER_SUBIERON },
              label = { Text("Subieron ($totalSubidas)") },
            )
            FilterChip(
              selected = changeFilter == CHANGE_FILTER_BAJARON,
              onClick = { changeFilter = CHANGE_FILTER_BAJARON },
              label = { Text("Bajaron (${summary.priceChanges.size - totalSubidas})") },
            )
          }
        }

        if (filteredChanges.isEmpty()) {
          item {
            EmptyPricingNotice(
              title = "Sin variaciones de precio detectadas",
              body = "Cada vez que importes un catálogo nuevo o actualices un costo, la app guardará el historial automáticamente para avisarte qué subió o bajó frente al catálogo anterior y frente a Isaac POS.",
            )
          }
        } else {
          items(filteredChanges, key = { it.idKey }) { change ->
            PriceChangeCard(
              item = change,
              modifier = Modifier.padding(horizontal = 16.dp),
            )
          }
        }
      }

      SUBTAB_OPTIMO -> {
        item {
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Solo productos en bajo stock",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = "Umbral actual: ≤ ${PriceParser.formatQuantity(lowStockThreshold)} u.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = onlyLowStockOptimal,
                  onCheckedChange = { onlyLowStockOptimal = it },
                )
              }

              Text(
                text = "Ahorro mínimo para cambiar de proveedor:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                listOf(0.0, 3.0, 5.0, 10.0).forEach { pct ->
                  FilterChip(
                    selected = kotlin.math.abs(pricingSettings.minOptimalSavingsPercent - pct) < 0.1,
                    onClick = { onUpdateMinSavingsPercent(pct) },
                    label = {
                      Text(if (pct == 0.0) "0% (Siempre el más barato)" else "${pct.toInt()}%")
                    },
                  )
                }
              }

              if (excludedKeys.isNotEmpty()) {
                TextButton(
                  onClick = { excludedKeys = emptySet() },
                  contentPadding = PaddingValues(0.dp),
                ) {
                  Text("Restaurar ${excludedKeys.size} producto(s) quitado(s)")
                }
              }

              HorizontalDivider()

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text(
                    text = "${optimalPlan.lines.size} prod. en ${optimalPlan.buckets.size} proveedor(es)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  val saveTxt = if (optimalPlan.totalSavingsUsd > 0.01) {
                    " · Ahorro est. ${PriceParser.formatCurrency(optimalPlan.totalSavingsUsd)}"
                  } else ""
                  Text(
                    text = "Total est. ${PriceParser.formatCurrency(optimalPlan.totalEstimatedUsd)}$saveTxt",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (optimalPlan.totalSavingsUsd > 0.01) HealthyStockGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
              }

              if (optimalPlan.buckets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  optimalPlan.buckets.forEach { bucket ->
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Text(
                        text = "• ${bucket.proveedor.nombre} (${bucket.lines.size} ítems)",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                      )
                      Text(
                        text = PriceParser.formatCurrency(bucket.totalEstimatedUsd),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                      )
                    }
                  }
                }
                Button(
                  onClick = { onCreateOptimalDrafts(optimalPlan) },
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Icon(Icons.Rounded.ShoppingCartCheckout, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(
                    if (optimalPlan.buckets.size == 1) "Crear 1 borrador de pedido"
                    else "Crear ${optimalPlan.buckets.size} borradores (1 por proveedor)"
                  )
                }
              }
            }
          }
        }

        if (filteredOptimalLines.isEmpty()) {
          item {
            EmptyPricingNotice(
              title = if (onlyLowStockOptimal) "Sin productos en bajo stock con precio" else "Sin productos disponibles",
              body = if (onlyLowStockOptimal) {
                "No tienes productos en bajo stock con costo cargado. Puedes desactivar \"Solo productos en bajo stock\" arriba para planificar una compra completa."
              } else {
                "Importa o sincroniza productos con precio para armar un Pedido Óptimo multi-proveedor."
              },
            )
          }
        } else {
          items(filteredOptimalLines, key = { "opt_${it.canonicalKey}" }) { line ->
            OptimalOrderLineCard(
              line = line,
              onSelectProveedor = { provId ->
                manualProviderOverrides = manualProviderOverrides + (line.canonicalKey to provId)
              },
              onChangeQuantity = { newQty ->
                if (newQty <= 0.0) {
                  excludedKeys = excludedKeys + line.canonicalKey
                } else {
                  quantitiesByKey = quantitiesByKey + (line.canonicalKey to newQty)
                }
              },
              onExclude = {
                excludedKeys = excludedKeys + line.canonicalKey
              },
              modifier = Modifier.padding(horizontal = 16.dp),
            )
          }
        }
      }
    }
  }

  if (showSettingsDialog) {
    PricingSettingsDialog(
      currentSettings = pricingSettings,
      bcvRefreshing = bcvRefreshing,
      onFetchBcvRate = onFetchBcvRate,
      onSave = onSavePricingSettings,
      onDismiss = { showSettingsDialog = false },
    )
  }

  editingPackOffer?.let { offer ->
    ProductPackTaxDialog(
      offer = offer,
      onSave = { units, gravado ->
        onUpdateProductPackTax(offer.producto.id, units, gravado)
      },
      onDismiss = { editingPackOffer = null },
    )
  }
}

@Composable
private fun PricingHeroKpi(
  value: String,
  label: String,
  accent: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = Color.White.copy(alpha = 0.13f),
    shape = RoundedCornerShape(10.dp),
    modifier = modifier.clickable(onClick = onClick),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.Start,
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = accent,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.85f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun MarginAlertCard(
  item: MarginAlertItem,
  targetMarkup: Double,
  onEditPackTax: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val o = item.primaryOffer
  val statusColor = when (o.health) {
    MarginHealth.LOSS, MarginHealth.CRITICAL -> ZeroStockRed
    MarginHealth.BELOW_TARGET -> LowStockAmber
    MarginHealth.HEALTHY -> HealthyStockGreen
    MarginHealth.NO_PVP -> MaterialTheme.colorScheme.outline
  }

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.38f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = o.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = buildString {
              append(o.proveedor.nombre)
              o.stockPos?.let { append(" · Stock: ${PriceParser.formatQuantity(it)}") }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
          color = statusColor.copy(alpha = 0.14f),
          shape = RoundedCornerShape(8.dp),
        ) {
          Text(
            text = o.health.etiqueta,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = statusColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      // Costo u. | PVP actual | PVP sugerido
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
          .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        MetricColumn(
          label = "Costo u.",
          value = o.effectiveUnitCostUsd?.let { PriceParser.formatCurrency(it) } ?: "-",
          valueColor = MaterialTheme.colorScheme.onSurface,
        )
        MetricColumn(
          label = "PVP POS",
          value = o.posPvpUsd?.let { PriceParser.formatCurrency(it) } ?: "-",
          valueColor = statusColor,
        )
        MetricColumn(
          label = "PVP +${PriceParser.formatQuantity(targetMarkup)}%",
          value = o.suggestedPvpUsd?.let { PriceParser.formatCurrency(it) } ?: "-",
          valueColor = MaterialTheme.colorScheme.primary,
        )
      }

      // Ambos indicadores: % sobre costo y % sobre PVP
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        val markupStr = o.markupPercent?.let { "${if (it >= 0) "+" else ""}${"%.1f".format(Locale.US, it)}% s/costo" } ?: "-"
        val grossStr = o.grossMarginPercent?.let { "${"%.1f".format(Locale.US, it)}% s/PVP" } ?: "-"
        val profitStr = o.profitPerUnitUsd?.let {
          val sign = if (it >= 0) "+" else "-"
          " ($sign${PriceParser.formatCurrency(kotlin.math.abs(it))}/u)"
        }.orEmpty()
        Text(
          text = "$markupStr · $grossStr$profitStr",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = statusColor,
        )
      }

      // Alternativa más barata si existe otro proveedor
      item.cheaperAlternative?.let { alt ->
        Surface(
          color = HealthyStockGreen.copy(alpha = 0.10f),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = HealthyStockGreen, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            val altCost = alt.effectiveUnitCostUsd?.let { PriceParser.formatCurrency(it) } ?: "-"
            val altMarkup = alt.markupPercent?.let { " (+${"%.1f".format(Locale.US, it)}% s/costo)" }.orEmpty()
            Text(
              text = "${alt.proveedor.nombre} lo ofrece a $altCost$altMarkup",
              style = MaterialTheme.typography.labelSmall,
              color = HealthyStockGreen,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }

      // Pie de detalle empaque / moneda / IVA (tocable para corregir)
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        val rawInfo = buildString {
          append(o.packDetection.label)
          if (o.moneda == MonedaCatalogo.VES && o.rawPriceOriginal != null) {
            append(" · Bs. ${PriceParser.formatQuantity(o.rawPriceOriginal)}")
          }
          if (o.ivaApplied) {
            append(" · +${PriceParser.formatQuantity(o.ivaPercentApplied)}% IVA")
          } else if (o.taxDetection.isTaxable) {
            append(" · (G)")
          }
        }
        Text(
          text = rawInfo,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        TextButton(
          onClick = onEditPackTax,
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) {
          Text("Ajustar empaque/IVA", style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}

@Composable
private fun SupplierComparisonCard(
  item: SupplierComparisonItem,
  onEditOfferPackTax: (EvaluatedOffer) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = item.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          val meta = buildList {
            if (item.codigoBarra.isNotBlank()) add("Cód: ${item.codigoBarra}")
            item.stockPos?.let { add("Stock: ${PriceParser.formatQuantity(it)}") }
            item.posPvpUsd?.let { add("PVP: ${PriceParser.formatCurrency(it)}") }
          }.joinToString(" · ")
          if (meta.isNotBlank()) {
            Text(
              text = meta,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (item.savingsPercent >= 0.5) {
          Spacer(Modifier.width(8.dp))
          Surface(
            color = HealthyStockGreen.copy(alpha = 0.14f),
            shape = RoundedCornerShape(8.dp),
          ) {
            Text(
              text = "-${"%.1f".format(Locale.US, item.savingsPercent)}%",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = HealthyStockGreen,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item.offers.forEachIndexed { idx, offer ->
          val isBest = idx == 0
          val bg = if (isBest) HealthyStockGreen.copy(alpha = 0.10f)
          else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          val border = if (isBest) BorderStroke(1.dp, HealthyStockGreen.copy(alpha = 0.45f)) else null
          Surface(
            color = bg,
            shape = RoundedCornerShape(10.dp),
            border = border,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onEditOfferPackTax(offer) },
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = if (isBest) "★ ${offer.proveedor.nombre}" else offer.proveedor.nombre,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isBest) FontWeight.Bold else FontWeight.Medium,
                    color = if (isBest) HealthyStockGreen else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                val detail = buildList {
                  add(offer.packDetection.label)
                  if (offer.moneda == MonedaCatalogo.VES && offer.rawPriceOriginal != null) {
                    add("Bs. ${PriceParser.formatQuantity(offer.rawPriceOriginal)}")
                  } else if (offer.rawPriceOriginal != null && offer.packDetection.units > 1.0) {
                    add("Emp: ${PriceParser.formatCurrency(offer.rawPriceOriginal)}")
                  }
                  if (offer.ivaApplied) add("+IVA")
                }.joinToString(" · ")
                Text(
                  text = detail,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = offer.effectiveUnitCostUsd?.let { "${PriceParser.formatCurrency(it)}/u" } ?: "-",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Bold,
                  color = if (isBest) HealthyStockGreen else MaterialTheme.colorScheme.onSurface,
                )
                if (!isBest && item.cheapestOffer.effectiveUnitCostUsd != null && offer.effectiveUnitCostUsd != null) {
                  val extra = offer.effectiveUnitCostUsd - item.cheapestOffer.effectiveUnitCostUsd
                  if (extra > 0.005) {
                    Text(
                      text = "+${PriceParser.formatCurrency(extra)}/u",
                      style = MaterialTheme.typography.labelSmall,
                      color = ZeroStockRed,
                    )
                  }
                } else if (isBest && item.savingsPerUnitUsd > 0.005) {
                  Text(
                    text = "Ahorras ${PriceParser.formatCurrency(item.savingsPerUnitUsd)}/u",
                    style = MaterialTheme.typography.labelSmall,
                    color = HealthyStockGreen,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PriceChangeCard(
  item: PriceChangeItem,
  modifier: Modifier = Modifier,
) {
  val accent = if (item.isIncrease) ZeroStockRed else HealthyStockGreen
  val dateStr = remember(item.currentDateMillis) {
    SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("es-ES")).format(Date(item.currentDateMillis))
  }

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = if (item.isIncrease) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
            contentDescription = null,
            tint = accent,
          )
        }
      }
      Spacer(Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.productName,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${item.proveedorNombre} · ${item.origin.etiqueta} · $dateStr",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = "${PriceParser.formatCurrency(item.previousUnitCostUsd)}/u  →  ${PriceParser.formatCurrency(item.currentUnitCostUsd)}/u",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
        )
      }
      Spacer(Modifier.width(8.dp))
      Column(horizontalAlignment = Alignment.End) {
        val sign = if (item.isIncrease) "+" else ""
        Text(
          text = "$sign${"%.1f".format(Locale.US, item.deltaPercent)}%",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = accent,
        )
        Text(
          text = "$sign${PriceParser.formatCurrency(item.deltaUsd)}/u",
          style = MaterialTheme.typography.labelSmall,
          color = accent,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptimalOrderLineCard(
  line: OptimalOrderLine,
  onSelectProveedor: (Long) -> Unit,
  onChangeQuantity: (Double) -> Unit,
  onExclude: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = line.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          val sub = buildList {
            line.stockPos?.let { add("Stock: ${PriceParser.formatQuantity(it)}") }
            add("Empaque: ${line.selectedOffer.producto.unidad}")
            line.selectedOffer.effectiveUnitCostUsd?.let { add("${PriceParser.formatCurrency(it)}/u") }
          }.joinToString(" · ")
          Text(
            text = sub,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = onExclude, modifier = Modifier.size(28.dp)) {
          Icon(Icons.Rounded.Close, contentDescription = "Quitar", modifier = Modifier.size(16.dp))
        }
      }

      // Selector de proveedor (si hay 2+ opciones muestra chips comparables; si solo 1 muestra etiqueta)
      if (line.allOffers.size > 1) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          line.allOffers.forEach { offer ->
            val selected = offer.proveedor.id == line.selectedOffer.proveedor.id
            val isCheapest = offer.proveedor.id == line.allOffers.first().proveedor.id
            val costTxt = offer.effectiveUnitCostUsd?.let { PriceParser.formatCurrency(it) } ?: "-"
            FilterChip(
              selected = selected,
              onClick = { onSelectProveedor(offer.proveedor.id) },
              label = {
                Text(
                  text = "${if (isCheapest) "★ " else ""}${offer.proveedor.nombre} · $costTxt/u",
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
            )
          }
        }
      } else {
        Text(
          text = "Proveedor: ${line.selectedOffer.proveedor.nombre}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Text(
            text = "Subtotal: ${PriceParser.formatCurrency(line.estimatedLineTotalUsd)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
          )
          if (line.estimatedLineSavingsUsd > 0.01) {
            Text(
              text = "Ahorras ${PriceParser.formatCurrency(line.estimatedLineSavingsUsd)}",
              style = MaterialTheme.typography.labelSmall,
              color = HealthyStockGreen,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = { onChangeQuantity((line.quantityPacks - 1.0).coerceAtLeast(0.0)) },
            modifier = Modifier.size(32.dp),
          ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Menos", modifier = Modifier.size(18.dp))
          }
          Text(
            text = PriceParser.formatQuantity(line.quantityPacks),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp),
          )
          IconButton(
            onClick = { onChangeQuantity(line.quantityPacks + 1.0) },
            modifier = Modifier.size(32.dp),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Más", modifier = Modifier.size(18.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun MetricColumn(
  label: String,
  value: String,
  valueColor: Color,
) {
  Column(horizontalAlignment = Alignment.Start) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Bold,
      color = valueColor,
    )
  }
}

@Composable
private fun EmptyPricingNotice(
  title: String,
  body: String,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.AutoMirrored.Rounded.CompareArrows,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
