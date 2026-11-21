package com.pedidos.proveedores.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.domain.model.CatalogoFormato
import com.pedidos.proveedores.domain.model.ModoIvaCatalogo
import com.pedidos.proveedores.domain.model.MonedaCatalogo
import com.pedidos.proveedores.domain.model.ProductoImportado
import com.pedidos.proveedores.domain.parser.PriceParser
import com.pedidos.proveedores.ui.CatalogImportUiState
import com.pedidos.proveedores.ui.components.ProductThumbnail
import com.pedidos.proveedores.ui.components.ProductoFormDialog
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportarCatalogoScreen(
  state: CatalogImportUiState,
  aiConfigured: Boolean,
  globalRateBs: Double = 0.0,
  onToggleItem: (Int) -> Unit,
  onSelectAll: (Boolean) -> Unit,
  onUpdateItem: (ProductoImportado) -> Unit,
  onSetReplaceExisting: (Boolean) -> Unit,
  onSetPricingConfig: (MonedaCatalogo, Double?, ModoIvaCatalogo, Double) -> Unit = { _, _, _, _ -> },
  onOpenPdfPreview: (rutaLocal: String, titulo: String) -> Unit,
  onOpenImagesPreview: (rutas: List<String>, titulo: String) -> Unit,
  onExtractWithAi: () -> Unit,
  onOpenAiSettings: () -> Unit,
  onConfirmImport: () -> Unit,
  onCancel: () -> Unit,
) {
  var editingItem by remember { mutableStateOf<ProductoImportado?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Importar catálogo") },
        navigationIcon = {
          IconButton(onClick = onCancel) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Cancelar")
          }
        },
      )
    },
    bottomBar = {
      if (state is CatalogImportUiState.Ready) {
        val selectedCount = state.resultado.productos.count { it.seleccionado }
        val isVisual = state.resultado.formato == CatalogoFormato.PDF || state.resultado.formato == CatalogoFormato.IMAGEN
        val canSave = selectedCount > 0 || isVisual
        Surface(
          tonalElevation = 3.dp,
          shadowElevation = 6.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .navigationBarsPadding()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onCancel,
              enabled = !state.guardando,
              modifier = Modifier.weight(0.4f),
            ) {
              Text("Cancelar")
            }
            Button(
              onClick = onConfirmImport,
              enabled = canSave && !state.guardando,
              modifier = Modifier.weight(0.6f),
            ) {
              if (state.guardando) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onPrimary,
                )
              } else {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                  when {
                    selectedCount > 0 -> "Importar ($selectedCount)"
                    state.resultado.formato == CatalogoFormato.IMAGEN -> "Guardar foto"
                    else -> "Guardar PDF"
                  }
                )
              }
            }
          }
        }
      }
    },
  ) { innerPadding ->
    when (state) {
      CatalogImportUiState.Idle, is CatalogImportUiState.Loading -> {
        val loading = state as? CatalogImportUiState.Loading
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            CircularProgressIndicator()
            Text(
              loading?.statusText ?: "Analizando catálogo...",
              style = MaterialTheme.typography.titleMedium,
            )
            if (loading != null && loading.totalPages > 0) {
              LinearProgressIndicator(
                progress = { (loading.currentPage.toFloat() / loading.totalPages).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(0.75f),
              )
            }
            Text(
              "Extrayendo presentaciones, bultos, códigos y miniaturas",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      is CatalogImportUiState.Error -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
              Icons.Rounded.WarningAmber,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = MaterialTheme.colorScheme.error,
            )
            Text("No se pudo leer el archivo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(state.mensaje, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onCancel) { Text("Volver") }
          }
        }
      }

      is CatalogImportUiState.Ready -> {
        val resultado = state.resultado
        val allSelected = resultado.productos.isNotEmpty() && resultado.productos.all { it.seleccionado }
        val isVisual = resultado.formato == CatalogoFormato.PDF || resultado.formato == CatalogoFormato.IMAGEN

        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
          item {
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = resultado.nombreArchivo,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "Formato: ${resultado.formato.etiqueta} · Método: ${resultado.metodoExtraccion} · Detectados: ${resultado.productos.size}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isVisual) {
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                      onClick = {
                        if (resultado.formato == CatalogoFormato.PDF) {
                          onOpenPdfPreview(resultado.rutaLocal, resultado.nombreArchivo)
                        } else {
                          val paths = resultado.rutasImagenesFuente.ifEmpty { listOf(resultado.rutaLocal) }
                          onOpenImagesPreview(paths, resultado.nombreArchivo)
                        }
                      },
                      modifier = Modifier.weight(1f),
                    ) {
                      Icon(
                        if (resultado.formato == CatalogoFormato.PDF) Icons.Rounded.PictureAsPdf else Icons.Rounded.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                      )
                      Spacer(Modifier.width(6.dp))
                      Text(if (resultado.formato == CatalogoFormato.PDF) "Ver PDF" else "Ver foto(s)")
                    }
                    FilledTonalButton(
                      onClick = {
                        if (aiConfigured) onExtractWithAi() else onOpenAiSettings()
                      },
                      modifier = Modifier.weight(1f),
                    ) {
                      Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.width(6.dp))
                      Text(if (aiConfigured) "Leer con IA" else "Activar IA")
                    }
                  }
                }

                if (resultado.productos.isNotEmpty()) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text("Reemplazar catálogo anterior", style = MaterialTheme.typography.bodyMedium)
                      Text(
                        "Borra los productos viejos de este proveedor antes de cargar los nuevos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                      checked = state.reemplazarExistentes,
                      onCheckedChange = onSetReplaceExisting,
                    )
                  }

                  HorizontalDivider()

                  // Moneda e IVA del catálogo
                  var tasaFijaText by remember(state.resultado.rutaLocal, state.moneda) {
                    mutableStateOf(state.tasaFijaBs?.let { "%.2f".format(Locale.US, it) }.orEmpty())
                  }
                  Text(
                    text = "Moneda e IVA de esta lista",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonedaCatalogo.entries.forEach { monedaOpt ->
                      FilterChip(
                        selected = state.moneda == monedaOpt,
                        onClick = {
                          onSetPricingConfig(
                            monedaOpt,
                            if (monedaOpt == MonedaCatalogo.VES) PriceParser.parse(tasaFijaText)?.takeIf { it > 0.0 } else null,
                            state.modoIva,
                            state.porcentajeIva,
                          )
                        },
                        label = { Text(monedaOpt.etiqueta) },
                      )
                    }
                  }
                  if (state.moneda == MonedaCatalogo.VES) {
                    OutlinedTextField(
                      value = tasaFijaText,
                      onValueChange = { txt ->
                        tasaFijaText = txt
                        onSetPricingConfig(
                          MonedaCatalogo.VES,
                          PriceParser.parse(txt)?.takeIf { it > 0.0 },
                          state.modoIva,
                          state.porcentajeIva,
                        )
                      },
                      label = { Text("Tasa fija Bs/USD (opcional)") },
                      placeholder = {
                        Text(
                          if (globalRateBs > 0.0) "Vacío = tasa actual (${"%.2f".format(Locale.US, globalRateBs)})"
                          else "Vacío = usar tasa BCV global"
                        )
                      },
                      singleLine = true,
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                      modifier = Modifier.fillMaxWidth(),
                    )
                  }
                  FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    ModoIvaCatalogo.entries.forEach { modo ->
                      FilterChip(
                        selected = state.modoIva == modo,
                        onClick = {
                          onSetPricingConfig(
                            state.moneda,
                            state.tasaFijaBs,
                            modo,
                            state.porcentajeIva,
                          )
                        },
                        label = { Text(modo.etiquetaCorta) },
                      )
                    }
                  }
                  Text(
                    text = state.modoIva.descripcion,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }

          if (resultado.advertencia != null) {
            item {
              Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.Top,
                ) {
                  Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                  )
                  Spacer(Modifier.width(10.dp))
                  Text(
                    text = resultado.advertencia,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                  )
                }
              }
            }
          }

          if (resultado.productos.isNotEmpty()) {
            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  "Revisa los productos a importar",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { onSelectAll(!allSelected) }) {
                  Text(if (allSelected) "Desmarcar todos" else "Marcar todos")
                }
              }
            }

            items(resultado.productos, key = { it.tempId }) { item ->
              Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Checkbox(
                    checked = item.seleccionado,
                    onCheckedChange = { onToggleItem(item.tempId) },
                  )
                  if (!item.imagenRuta.isNullOrBlank()) {
                    ProductThumbnail(
                      imagePath = item.imagenRuta,
                      contentDescription = item.nombre,
                      size = 44.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = item.nombre,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.Medium,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                    val meta = buildList {
                      if (item.categoria.isNotBlank()) add(item.categoria)
                      add(item.unidad)
                      item.paginaPdf?.let {
                        add(if (resultado.formato == CatalogoFormato.IMAGEN) "Foto $it" else "Pág. $it")
                      }
                    }.joinToString(" · ")
                    Text(
                      text = meta,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.codigo.isNotBlank()) {
                      Text(
                        text = "Cód: ${item.codigo}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    if (item.precio != null) {
                      Text(
                        text = PriceParser.formatCurrency(item.precio),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }
                  IconButton(onClick = { editingItem = item }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Editar fila")
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  editingItem?.let { item ->
    ProductoFormDialog(
      title = "Ajustar producto",
      initialCodigo = item.codigo,
      initialNombre = item.nombre,
      initialUnidad = item.unidad,
      initialPrecio = item.precio,
      initialCategoria = item.categoria,
      initialImagePath = item.imagenRuta,
      allowPhotoPicker = false,
      onDismiss = { editingItem = null },
      onSave = { codigo, nombre, unidad, precio, categoria, _ ->
        editingItem = null
        onUpdateItem(
          item.copy(
            codigo = codigo,
            nombre = nombre,
            unidad = unidad,
            precio = precio,
            categoria = categoria,
          )
        )
      },
    )
  }
}
