package com.pedidos.proveedores.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.pedidos.proveedores.ui.components.PasteWhatsAppTextDialog
import com.pedidos.proveedores.ui.components.ProductThumbnail
import com.pedidos.proveedores.ui.components.ProductoFormDialog
import com.pedidos.proveedores.ui.components.ProveedorFormDialog
import com.pedidos.proveedores.ui.theme.CsvBadgeColor
import com.pedidos.proveedores.ui.theme.ExcelBadgeColor
import com.pedidos.proveedores.ui.theme.ImageBadgeColor
import com.pedidos.proveedores.ui.theme.PdfBadgeColor
import com.pedidos.proveedores.ui.theme.WhatsAppGreen
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
  onPickCatalogText: (String) -> Unit = {},
  onOpenPdf: (rutaLocal: String, titulo: String) -> Unit,
  onOpenImages: (rutas: List<String>, titulo: String) -> Unit,
  onOpenText: (rutaLocal: String, titulo: String) -> Unit = { _, _ -> },
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
  var showPasteTextDialog by remember { mutableStateOf(false) }
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
          Column {
            Text(
              text = proveedor.nombre,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            val sub = buildList {
              if (proveedor.telefonoWhatsApp.isNotBlank()) add(proveedor.telefonoWhatsApp)
              add("${productos.size} prod.")
            }.joinToString(" · ")
            Text(
              text = sub,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
          }
        },
        actions = {
          FilledTonalButton(
            onClick = onStartOrder,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          ) {
            Icon(Icons.Rounded.ShoppingCartCheckout, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Pedir", style = MaterialTheme.typography.labelMedium)
          }
          IconButton(onClick = { showEditProveedor = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Edit, contentDescription = "Editar proveedor", modifier = Modifier.size(18.dp))
          }
          IconButton(onClick = { showDeleteProveedorConfirm = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Delete, contentDescription = "Eliminar proveedor", modifier = Modifier.size(18.dp))
          }
        },
      )
    },
  ) { innerPadding ->
    val unlinkedCount = remember(productos) { productos.count { it.isaacPosId.isNullOrBlank() } }

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
    ) {
      if (proveedor.notas.isNotBlank()) {
        item(key = "prov_notes") {
          Text(
            text = proveedor.notas,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // Barra de 4 acciones rápidas en 1 sola fila compacta (ahorra ~240.dp verticales)
      item(key = "quick_actions_row") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          QuickActionTile(
            icon = Icons.Rounded.UploadFile,
            label = "PDF/Excel",
            tint = MaterialTheme.colorScheme.primary,
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
          )
          QuickActionTile(
            icon = Icons.Rounded.AddPhotoAlternate,
            label = "Fotos",
            tint = ImageBadgeColor,
            onClick = { imagesPickerLauncher.launch(arrayOf("image/*")) },
            modifier = Modifier.weight(1f),
          )
          QuickActionTile(
            icon = Icons.AutoMirrored.Rounded.Chat,
            label = "Texto WA",
            tint = WhatsAppGreen,
            onClick = { showPasteTextDialog = true },
            modifier = Modifier.weight(1f),
          )
          QuickActionTile(
            icon = Icons.AutoMirrored.Rounded.CompareArrows,
            label = if (unlinkedCount > 0) "POS ($unlinkedCount)" else "Conciliar",
            tint = MaterialTheme.colorScheme.secondary,
            onClick = onOpenConciliar,
            modifier = Modifier.weight(1f),
          )
        }
      }

      // Catálogos en carrusel horizontal compacto (no empuja los productos fuera de pantalla)
      if (catalogos.isNotEmpty()) {
        item(key = "catalogs_strip") {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "Listas recibidas (${catalogos.size})",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(catalogos, key = { "cat_${it.id}" }) { cat ->
                CatalogoArchivoCard(
                  catalogo = cat,
                  onView = when (cat.formato) {
                    CatalogoFormato.PDF -> ({ onOpenPdf(cat.rutaLocal, cat.nombreArchivo) })
                    CatalogoFormato.IMAGEN -> ({ onOpenImages(listOf(cat.rutaLocal), cat.nombreArchivo) })
                    CatalogoFormato.TEXTO -> ({ onOpenText(cat.rutaLocal, cat.nombreArchivo) })
                    else -> null
                  },
                  viewLabel = when (cat.formato) {
                    CatalogoFormato.IMAGEN -> "Foto"
                    CatalogoFormato.TEXTO -> "Texto"
                    else -> "PDF"
                  },
                  onEditPricing = { pricingCatalogo = cat },
                  onDelete = { deletingCatalogo = cat },
                )
              }
            }
          }
        }
      }

      item(key = "products_header") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Productos (${filteredProducts.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          OutlinedButton(
            onClick = { showAddProducto = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Manual", style = MaterialTheme.typography.labelMedium)
          }
        }
      }

      if (productos.isNotEmpty()) {
        item(key = "products_search") {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar por nombre o código...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      if (productos.isEmpty()) {
        item(key = "empty_products") {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Todavía no hay productos cargados. Sube un PDF/Excel, fotos o pega una lista de WhatsApp arriba (o compártela directo desde WhatsApp).",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(14.dp),
            )
          }
        }
      } else if (filteredProducts.isEmpty()) {
        item(key = "empty_search") {
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

  if (showPasteTextDialog) {
    PasteWhatsAppTextDialog(
      proveedorNombre = proveedor.nombre,
      onImportText = { rawText ->
        showPasteTextDialog = false
        onPickCatalogText(rawText)
      },
      onDismiss = { showPasteTextDialog = false },
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
private fun QuickActionTile(
  icon: ImageVector,
  label: String,
  tint: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = RoundedCornerShape(11.dp),
    modifier = modifier.clickable(onClick = onClick),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
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
    SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("es-ES")).format(Date(catalogo.importadoEn))
  }
  val badgeColor = when (catalogo.formato) {
    CatalogoFormato.PDF -> PdfBadgeColor
    CatalogoFormato.XLSX -> ExcelBadgeColor
    CatalogoFormato.CSV -> CsvBadgeColor
    CatalogoFormato.IMAGEN -> ImageBadgeColor
    CatalogoFormato.TEXTO -> WhatsAppGreen
  }
  val icon = when (catalogo.formato) {
    CatalogoFormato.PDF -> Icons.Rounded.PictureAsPdf
    CatalogoFormato.IMAGEN -> Icons.Rounded.Image
    CatalogoFormato.TEXTO -> Icons.AutoMirrored.Rounded.Chat
    CatalogoFormato.XLSX, CatalogoFormato.CSV -> Icons.Rounded.TableChart
  }

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier.width(236.dp),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          color = badgeColor.copy(alpha = 0.14f),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.size(30.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(16.dp))
          }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = catalogo.nombreArchivo,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${catalogo.productosImportados} prod. · $dateStr",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        val pricingMeta = buildString {
          append(catalogo.moneda.simbolo)
          if (catalogo.moneda == MonedaCatalogo.VES && catalogo.tasaFijaBs != null) {
            append(" (${PriceParser.formatQuantity(catalogo.tasaFijaBs)})")
          }
          append(" · ${catalogo.modoIva.etiquetaCorta}")
        }
        Text(
          text = pricingMeta,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (onView != null) {
            TextButton(
              onClick = onView,
              contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
              Text(viewLabel, style = MaterialTheme.typography.labelSmall)
            }
          }
          IconButton(onClick = onEditPricing, modifier = Modifier.size(28.dp)) {
            Icon(
              Icons.Rounded.Tune,
              contentDescription = "Configurar moneda e IVA",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
          }
          IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(
              Icons.Rounded.Delete,
              contentDescription = "Eliminar catálogo",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
          }
        }
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
