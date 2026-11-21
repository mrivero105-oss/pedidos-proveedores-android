package com.pedidos.proveedores.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCartCheckout
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.domain.model.CatalogoArchivo
import com.pedidos.proveedores.domain.model.CatalogoFormato
import com.pedidos.proveedores.domain.model.ModoIvaCatalogo
import com.pedidos.proveedores.domain.model.MonedaCatalogo
import com.pedidos.proveedores.domain.model.Producto
import com.pedidos.proveedores.domain.model.Proveedor
import com.pedidos.proveedores.domain.parser.PriceParser
import com.pedidos.proveedores.ui.components.CatalogoPricingDialog
import com.pedidos.proveedores.ui.components.ProductThumbnail
import com.pedidos.proveedores.ui.components.ProductoFormDialog
import com.pedidos.proveedores.ui.components.ProveedorFormDialog
import com.pedidos.proveedores.ui.theme.CsvBadgeColor
import com.pedidos.proveedores.ui.theme.ExcelBadgeColor
import com.pedidos.proveedores.ui.theme.ImageBadgeColor
import com.pedidos.proveedores.ui.theme.PdfBadgeColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProveedorDetalleScreen(
  proveedor: Proveedor?,
  catalogos: List<CatalogoArchivo>,
  productos: List<Producto>,
  onBack: () -> Unit,
  onUpdateProveedor: (Proveedor) -> Unit,
  onDeleteProveedor: () -> Unit,
  onPickCatalogFile: (Uri) -> Unit,
  onPickCatalogImages: (List<Uri>) -> Unit,
  onOpenPdf: (rutaLocal: String, titulo: String) -> Unit,
  onOpenImages: (rutas: List<String>, titulo: String) -> Unit,
  onDeleteCatalogo: (catalogoId: Long, eliminarProductos: Boolean) -> Unit,
  onUpdateCatalogoPricing: (Long, MonedaCatalogo, Double?, ModoIvaCatalogo, Double) -> Unit = { _, _, _, _, _ -> },
  globalRateBs: Double = 0.0,
  onSaveProducto: (Producto, Uri?) -> Unit,
  onDeleteProducto: (Long) -> Unit,
  onStartOrder: () -> Unit,
  onOpenConciliar: () -> Unit = {},
) {
  if (proveedor == null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Proveedor no encontrado")
    }
    return
  }

  var searchQuery by remember { mutableStateOf("") }
  var showEditProveedor by remember { mutableStateOf(false) }
  var showDeleteProveedorConfirm by remember { mutableStateOf(false) }
  var showAddProducto by remember { mutableStateOf(false) }
  var editingProducto by remember { mutableStateOf<Producto?>(null) }
  var deletingCatalogo by remember { mutableStateOf<CatalogoArchivo?>(null) }
  var pricingCatalogo by remember { mutableStateOf<CatalogoArchivo?>(null) }

  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      onPickCatalogFile(uri)
    }
  }

  val imagesPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments()
  ) { uris ->
    if (uris.isNotEmpty()) {
      onPickCatalogImages(uris)
    }
  }

  val filteredProducts = remember(productos, searchQuery) {
    if (searchQuery.isBlank()) productos
    else {
      val q = searchQuery.trim().lowercase()
      productos.filter {
        it.nombre.lowercase().contains(q) ||
          it.codigo.lowercase().contains(q) ||
          it.categoria.lowercase().contains(q)
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = proveedor.nombre,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
          }
        },
        actions = {
          IconButton(onClick = { showEditProveedor = true }) {
            Icon(Icons.Rounded.Edit, contentDescription = "Editar proveedor")
          }
          IconButton(onClick = { showDeleteProveedorConfirm = true }) {
            Icon(Icons.Rounded.Delete, contentDescription = "Eliminar proveedor")
          }
        },
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
    ) {
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = if (proveedor.telefonoWhatsApp.isNotBlank()) {
                "WhatsApp: ${proveedor.telefonoWhatsApp}"
              } else {
                "Sin teléfono WhatsApp registrado"
              },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (proveedor.notas.isNotBlank()) {
              Text(
                text = proveedor.notas,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilledTonalButton(
                onClick = {
                  filePickerLauncher.launch(
                    arrayOf(
                      "application/pdf",
                      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                      "application/vnd.ms-excel",
                      "text/csv",
                      "text/comma-separated-values",
                      "text/plain",
                      "image/*",
                      "application/octet-stream",
                    )
                  )
                },
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("PDF / Excel")
              }
              FilledTonalButton(
                onClick = {
                  imagesPickerLauncher.launch(arrayOf("image/*"))
                },
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Imágenes")
              }
            }
            val unlinkedCount = productos.count { it.isaacPosId.isNullOrBlank() }
            OutlinedButton(
              onClick = onOpenConciliar,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.AutoMirrored.Rounded.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(
                if (unlinkedCount > 0) "Conciliar con mi POS ($unlinkedCount sin vincular)"
                else "Conciliar catálogo con mi Isaac POS"
              )
            }
            Button(
              onClick = onStartOrder,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Rounded.ShoppingCartCheckout, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Hacer pedido")
            }
            Text(
              text = "Admite PDF, Excel (.xlsx), CSV y una o varias imágenes de galería/WhatsApp.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (catalogos.isNotEmpty()) {
        item {
          Text(
            text = "Catálogos subidos (${catalogos.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
        items(catalogos, key = { "cat_${it.id}" }) { cat ->
          CatalogoArchivoCard(
            catalogo = cat,
            onView = when (cat.formato) {
              CatalogoFormato.PDF -> ({ onOpenPdf(cat.rutaLocal, cat.nombreArchivo) })
              CatalogoFormato.IMAGEN -> ({ onOpenImages(listOf(cat.rutaLocal), cat.nombreArchivo) })
              else -> null
            },
            viewLabel = if (cat.formato == CatalogoFormato.IMAGEN) "Ver foto" else "Ver PDF",
            onEditPricing = { pricingCatalogo = cat },
            onDelete = { deletingCatalogo = cat },
          )
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Productos (${productos.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          OutlinedButton(onClick = { showAddProducto = true }) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Manual")
          }
        }
      }

      if (productos.isNotEmpty()) {
        item {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar por nombre, código o rubro...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      if (productos.isEmpty()) {
        item {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Todavía no hay productos cargados. Sube un catálogo en PDF, Excel, CSV o imágenes, o agrega un producto manual con foto.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      } else if (filteredProducts.isEmpty()) {
        item {
          Text(
            text = "Sin resultados para \"$searchQuery\"",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
          )
        }
      } else {
        items(filteredProducts, key = { "prod_${it.id}" }) { producto ->
          ProductoCatalogoRow(
            producto = producto,
            onEdit = { editingProducto = producto },
            onDelete = { onDeleteProducto(producto.id) },
          )
        }
      }
    }
  }

  if (showEditProveedor) {
    ProveedorFormDialog(
      initial = proveedor,
      onDismiss = { showEditProveedor = false },
      onSave = { updated ->
        showEditProveedor = false
        onUpdateProveedor(updated)
      },
    )
  }

  if (showDeleteProveedorConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteProveedorConfirm = false },
      title = { Text("Eliminar proveedor") },
      text = {
        Text("Se eliminarán \"${proveedor.nombre}\", sus catálogos y sus pedidos registrados. Esta acción no se puede deshacer.")
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteProveedorConfirm = false
            onDeleteProveedor()
          }
        ) {
          Text("Eliminar")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteProveedorConfirm = false }) { Text("Cancelar") }
      },
    )
  }

  if (showAddProducto) {
    ProductoFormDialog(
      title = "Agregar producto",
      allowPhotoPicker = true,
      onDismiss = { showAddProducto = false },
      onSave = { codigo, nombre, unidad, precio, categoria, photoUri ->
        showAddProducto = false
        onSaveProducto(
          Producto(
            proveedorId = proveedor.id,
            codigo = codigo,
            nombre = nombre,
            unidad = unidad,
            precio = precio,
            categoria = categoria,
          ),
          photoUri,
        )
      },
    )
  }

  editingProducto?.let { prod ->
    ProductoFormDialog(
      title = "Editar producto",
      initialCodigo = prod.codigo,
      initialNombre = prod.nombre,
      initialUnidad = prod.unidad,
      initialPrecio = prod.precio,
      initialCategoria = prod.categoria,
      initialImagePath = prod.imagenRuta,
      allowPhotoPicker = true,
      onDismiss = { editingProducto = null },
      onSave = { codigo, nombre, unidad, precio, categoria, photoUri ->
        editingProducto = null
        onSaveProducto(
          prod.copy(
            codigo = codigo,
            nombre = nombre,
            unidad = unidad,
            precio = precio,
            categoria = categoria,
          ),
          photoUri,
        )
      },
    )
  }

  pricingCatalogo?.let { cat ->
    CatalogoPricingDialog(
      catalogo = cat,
      globalRateBs = globalRateBs,
      onSave = { moneda, tasaFija, modoIva, ivaPct ->
        onUpdateCatalogoPricing(cat.id, moneda, tasaFija, modoIva, ivaPct)
      },
      onDismiss = { pricingCatalogo = null },
    )
  }

  deletingCatalogo?.let { cat ->
    AlertDialog(
      onDismissRequest = { deletingCatalogo = null },
      title = { Text("Eliminar catálogo") },
      text = {
        Text("¿Quieres eliminar solo el archivo \"${cat.nombreArchivo}\" o también los productos importados desde este catálogo?")
      },
      confirmButton = {
        Button(
          onClick = {
            deletingCatalogo = null
            onDeleteCatalogo(cat.id, true)
          }
        ) {
          Text("Archivo y productos")
        }
      },
      dismissButton = {
        Row {
          TextButton(
            onClick = {
              deletingCatalogo = null
              onDeleteCatalogo(cat.id, false)
            }
          ) {
            Text("Solo archivo")
          }
          TextButton(onClick = { deletingCatalogo = null }) { Text("Cancelar") }
        }
      },
    )
  }
}

@Composable
private fun CatalogoArchivoCard(
  catalogo: CatalogoArchivo,
  onView: (() -> Unit)?,
  viewLabel: String,
  onEditPricing: () -> Unit,
  onDelete: () -> Unit,
) {
  val dateStr = remember(catalogo.importadoEn) {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-ES")).format(Date(catalogo.importadoEn))
  }
  val badgeColor = when (catalogo.formato) {
    CatalogoFormato.PDF -> PdfBadgeColor
    CatalogoFormato.XLSX -> ExcelBadgeColor
    CatalogoFormato.CSV -> CsvBadgeColor
    CatalogoFormato.IMAGEN -> ImageBadgeColor
  }
  val icon = when (catalogo.formato) {
    CatalogoFormato.PDF -> Icons.Rounded.PictureAsPdf
    CatalogoFormato.IMAGEN -> Icons.Rounded.Image
    CatalogoFormato.XLSX, CatalogoFormato.CSV -> Icons.Rounded.TableChart
  }

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        color = badgeColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(icon, contentDescription = null, tint = badgeColor)
        }
      }
      Spacer(Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = catalogo.nombreArchivo,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${catalogo.formato.etiqueta} · ${catalogo.productosImportados} prod. · $dateStr",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val pricingMeta = buildString {
          append(catalogo.moneda.simbolo)
          if (catalogo.moneda == MonedaCatalogo.VES && catalogo.tasaFijaBs != null) {
            append(" (fija ${PriceParser.formatQuantity(catalogo.tasaFijaBs)})")
          }
          append(" · ${catalogo.modoIva.etiquetaCorta}")
        }
        Text(
          text = pricingMeta,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      if (onView != null) {
        TextButton(
          onClick = onView,
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) {
          Text(viewLabel)
        }
      }
      IconButton(onClick = onEditPricing) {
        Icon(
          Icons.Rounded.Tune,
          contentDescription = "Configurar moneda e IVA",
          tint = MaterialTheme.colorScheme.primary,
        )
      }
      IconButton(onClick = onDelete) {
        Icon(
          Icons.Rounded.Delete,
          contentDescription = "Eliminar catálogo",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun ProductoCatalogoRow(
  producto: Producto,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (!producto.imagenRuta.isNullOrBlank()) {
        ProductThumbnail(
          imagePath = producto.imagenRuta,
          contentDescription = producto.nombre,
          size = 44.dp,
        )
        Spacer(Modifier.width(8.dp))
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = producto.nombre,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        val details = buildList {
          if (producto.categoria.isNotBlank()) add(producto.categoria)
          add(producto.unidad)
        }.joinToString(" · ")
        Text(
          text = details,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (producto.codigo.isNotBlank()) {
          Text(
            text = "Cód: ${producto.codigo}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (producto.precio != null) {
          Text(
            text = PriceParser.formatCurrency(producto.precio),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        if (producto.stockPos != null) {
          Spacer(Modifier.height(4.dp))
          StockPosBadge(stock = producto.stockPos, isLow = producto.stockPos <= 5.0)
        }
      }
      IconButton(onClick = onEdit) {
        Icon(Icons.Rounded.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      IconButton(onClick = onDelete) {
        Icon(Icons.Rounded.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
