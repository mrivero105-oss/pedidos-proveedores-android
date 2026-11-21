package com.pedidos.proveedores.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.data.local.PricingSettings
import com.pedidos.proveedores.domain.model.CatalogoArchivo
import com.pedidos.proveedores.domain.model.ModoIvaCatalogo
import com.pedidos.proveedores.domain.model.MonedaCatalogo
import com.pedidos.proveedores.domain.parser.PriceParser
import com.pedidos.proveedores.domain.pricing.EvaluatedOffer
import java.util.Locale

@Composable
fun PricingSettingsDialog(
  currentSettings: PricingSettings,
  bcvRefreshing: Boolean,
  onFetchBcvRate: () -> Unit,
  onSave: (
    tasaBsPorUsd: Double,
    tasaSourceLabel: String,
    defaultIvaPercent: Double,
    minOptimalSavingsPercent: Double,
  ) -> Unit,
  onDismiss: () -> Unit,
) {
  var rateText by remember {
    mutableStateOf(
      if (currentSettings.hasValidExchangeRate) {
        "%.2f".format(Locale.US, currentSettings.tasaBsPorUsd)
      } else ""
    )
  }
  var ivaText by remember { mutableStateOf(PriceParser.formatQuantity(currentSettings.defaultIvaPercent)) }
  var minSavingsText by remember { mutableStateOf(PriceParser.formatQuantity(currentSettings.minOptimalSavingsPercent)) }
  var lastSource by remember { mutableStateOf(currentSettings.tasaSourceLabel) }

  LaunchedEffect(currentSettings.tasaBsPorUsd, currentSettings.tasaUpdatedAtMillis) {
    if (currentSettings.hasValidExchangeRate) {
      rateText = "%.2f".format(Locale.US, currentSettings.tasaBsPorUsd)
      lastSource = currentSettings.tasaSourceLabel
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Tasa BCV e IVA de Compras") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Tasa de cambio (Bs. / USD)",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
          value = rateText,
          onValueChange = {
            rateText = it
            lastSource = "Manual"
          },
          label = { Text("Bs. por cada $1 USD") },
          placeholder = { Text("Ej. 918.77") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          supportingText = {
            val info = if (lastSource.isNotBlank()) "Origen: $lastSource" else "Convierte listas de proveedores en Bolívares para comparar en USD"
            Text(info)
          },
          modifier = Modifier.fillMaxWidth(),
        )
        FilledTonalButton(
          onClick = onFetchBcvRate,
          enabled = !bcvRefreshing,
          modifier = Modifier.fillMaxWidth(),
        ) {
          if (bcvRefreshing) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Consultando BCV oficial...")
          } else {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Traer tasa BCV oficial de hoy")
          }
        }

        HorizontalDivider()

        Text(
          text = "Reglas de comparación de proveedores",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = ivaText,
            onValueChange = { ivaText = it },
            label = { Text("IVA %") },
            placeholder = { Text("16") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = minSavingsText,
            onValueChange = { minSavingsText = it },
            label = { Text("Ahorro mín. %") },
            placeholder = { Text("3") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
          )
        }
        Text(
          text = "Ahorro mínimo %: umbral en Pedido Óptimo para recomendar cambiar de proveedor por otro más barato.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsedRate = PriceParser.parse(rateText) ?: currentSettings.tasaBsPorUsd
          val parsedIva = PriceParser.parse(ivaText) ?: currentSettings.defaultIvaPercent
          val parsedMinSave = PriceParser.parse(minSavingsText) ?: currentSettings.minOptimalSavingsPercent
          onSave(
            parsedRate,
            lastSource.ifBlank { "Manual" },
            parsedIva,
            parsedMinSave,
          )
          onDismiss()
        }
      ) {
        Text("Guardar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cerrar") }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogoPricingDialog(
  catalogo: CatalogoArchivo,
  globalRateBs: Double,
  onSave: (
    moneda: MonedaCatalogo,
    tasaFijaBs: Double?,
    modoIva: ModoIvaCatalogo,
    porcentajeIva: Double,
  ) -> Unit,
  onDismiss: () -> Unit,
) {
  var moneda by remember { mutableStateOf(catalogo.moneda) }
  var tasaFijaText by remember {
    mutableStateOf(catalogo.tasaFijaBs?.let { "%.2f".format(Locale.US, it) }.orEmpty())
  }
  var modoIva by remember { mutableStateOf(catalogo.modoIva) }
  var ivaPctText by remember { mutableStateOf(PriceParser.formatQuantity(catalogo.porcentajeIva)) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Moneda e IVA del catálogo") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = catalogo.nombreArchivo,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "Moneda de los precios",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          MonedaCatalogo.entries.forEach { m ->
            FilterChip(
              selected = moneda == m,
              onClick = { moneda = m },
              label = { Text(m.etiqueta) },
            )
          }
        }

        if (moneda == MonedaCatalogo.VES) {
          OutlinedTextField(
            value = tasaFijaText,
            onValueChange = { tasaFijaText = it },
            label = { Text("Tasa fija Bs/USD (opcional)") },
            placeholder = {
              Text(
                if (globalRateBs > 0.0) "Vacío = usar tasa actual (${"%.2f".format(Locale.US, globalRateBs)})"
                else "Vacío = usar tasa global BCV"
              )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = {
              Text("Déjalo vacío para recalcular automáticamente cuando cambie la tasa BCV.")
            },
            modifier = Modifier.fillMaxWidth(),
          )
        }

        Text(
          text = "Impuesto (IVA)",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          ModoIvaCatalogo.entries.forEach { modo ->
            FilterChip(
              selected = modoIva == modo,
              onClick = { modoIva = modo },
              label = { Text(modo.etiquetaCorta) },
            )
          }
        }
        Text(
          text = modoIva.descripcion,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (modoIva != ModoIvaCatalogo.INCLUIDO) {
          OutlinedTextField(
            value = ivaPctText,
            onValueChange = { ivaPctText = it },
            label = { Text("Porcentaje IVA (%)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val fixedRate = if (moneda == MonedaCatalogo.VES) PriceParser.parse(tasaFijaText)?.takeIf { it > 0.0 } else null
          val ivaPct = PriceParser.parse(ivaPctText) ?: 16.0
          onSave(moneda, fixedRate, modoIva, ivaPct)
          onDismiss()
        }
      ) {
        Text("Aplicar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductPackTaxDialog(
  offer: EvaluatedOffer,
  onSave: (unidadesPorEmpaque: Double?, esGravado: Boolean?) -> Unit,
  onDismiss: () -> Unit,
) {
  var unitsText by remember {
    mutableStateOf(
      offer.producto.unidadesPorEmpaque?.let { PriceParser.formatQuantity(it) }.orEmpty()
    )
  }
  var taxOverride by remember { mutableStateOf<Boolean?>(offer.producto.esGravado) }
  val quickPacks = listOf(1.0, 6.0, 12.0, 20.0, 24.0)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Ajustar empaque e IVA") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = offer.displayName,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Proveedor: ${offer.proveedor.nombre} · Unidad: ${offer.producto.unidad}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = unitsText,
          onValueChange = { unitsText = it },
          label = { Text("Unidades que trae este precio") },
          placeholder = { Text("Vacío = automático (${PriceParser.formatQuantity(offer.packDetection.units)})") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          supportingText = {
            Text("Ej: si el proveedor cotiza un bulto de 12 unidades, coloca 12. Si cotiza por unidad, coloca 1.")
          },
          modifier = Modifier.fillMaxWidth(),
        )

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          FilterChip(
            selected = unitsText.isBlank(),
            onClick = { unitsText = "" },
            label = { Text("Auto") },
          )
          quickPacks.forEach { n ->
            val txt = PriceParser.formatQuantity(n)
            FilterChip(
              selected = unitsText == txt,
              onClick = { unitsText = txt },
              label = { Text("x$txt") },
            )
          }
        }

        HorizontalDivider()

        Text(
          text = "Condición de IVA",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(
            selected = taxOverride == null,
            onClick = { taxOverride = null },
            label = { Text("Auto (G)") },
          )
          FilterChip(
            selected = taxOverride == true,
            onClick = { taxOverride = true },
            label = { Text("Gravado") },
          )
          FilterChip(
            selected = taxOverride == false,
            onClick = { taxOverride = false },
            label = { Text("Exento") },
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsedUnits = if (unitsText.isBlank()) null else PriceParser.parse(unitsText)?.takeIf { it > 0.0 }
          onSave(parsedUnits, taxOverride)
          onDismiss()
        }
      ) {
        Text("Guardar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}
