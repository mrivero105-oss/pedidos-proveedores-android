package com.pedidos.proveedores.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pedidos.proveedores.domain.model.Producto
import com.pedidos.proveedores.domain.model.Proveedor
import com.pedidos.proveedores.domain.parser.PriceParser

@Composable
fun ProveedorFormDialog(
  initial: Proveedor? = null,
  onDismiss: () -> Unit,
  onSave: (Proveedor) -> Unit,
) {
  var nombre by remember { mutableStateOf(initial?.nombre.orEmpty()) }
  var telefono by remember { mutableStateOf(initial?.telefonoWhatsApp.orEmpty()) }
  var notas by remember { mutableStateOf(initial?.notas.orEmpty()) }
  var showError by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "Nuevo proveedor" else "Editar proveedor") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = nombre,
          onValueChange = {
            nombre = it
            showError = false
          },
          label = { Text("Nombre del proveedor *") },
          placeholder = { Text("Ej. Grupo Amanecer") },
          singleLine = true,
          isError = showError,
          supportingText = if (showError) {
            { Text("Ingresa un nombre") }
          } else null,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = telefono,
          onValueChange = { telefono = it },
          label = { Text("WhatsApp (con código de país)") },
          placeholder = { Text("Ej. +58 412 555-1234") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          supportingText = { Text("Se usará para abrir directo su chat de WhatsApp") },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = notas,
          onValueChange = { notas = it },
          label = { Text("Notas (opcional)") },
          placeholder = { Text("Días de entrega, pedido mínimo, etc.") },
          maxLines = 3,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (nombre.isBlank()) {
            showError = true
          } else {
            onSave(
              (initial ?: Proveedor(nombre = nombre)).copy(
                nombre = nombre.trim(),
                telefonoWhatsApp = telefono.trim(),
                notas = notas.trim(),
              )
            )
          }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductoFormDialog(
  title: String,
  initialCodigo: String = "",
  initialNombre: String = "",
  initialUnidad: String = "bulto",
  initialPrecio: Double? = null,
  initialCategoria: String = "",
  initialImagePath: String? = null,
  allowPhotoPicker: Boolean = false,
  onDismiss: () -> Unit,
  onSave: (codigo: String, nombre: String, unidad: String, precio: Double?, categoria: String, newPhotoUri: Uri?) -> Unit,
) {
  var codigo by remember { mutableStateOf(initialCodigo) }
  var nombre by remember { mutableStateOf(initialNombre) }
  var unidad by remember { mutableStateOf(initialUnidad.ifBlank { "u" }) }
  var precioText by remember { mutableStateOf(initialPrecio?.let { PriceParser.formatQuantity(it) }.orEmpty()) }
  var categoria by remember { mutableStateOf(initialCategoria) }
  var pickedPhotoUri by remember { mutableStateOf<Uri?>(null) }
  var nameError by remember { mutableStateOf(false) }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri ->
    if (uri != null) pickedPhotoUri = uri
  }

  val quickUnits = listOf("bulto", "caja", "pack", "u", "kg", "bolsa", "lt")

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (allowPhotoPicker) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            ProductThumbnail(
              imagePath = initialImagePath,
              contentDescription = "Foto del producto",
              size = 48.dp,
            )
            OutlinedButton(
              onClick = { photoPickerLauncher.launch("image/*") },
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(
                when {
                  pickedPhotoUri != null -> "Foto seleccionada"
                  !initialImagePath.isNullOrBlank() -> "Cambiar foto"
                  else -> "Agregar foto"
                }
              )
            }
          }
        }
        OutlinedTextField(
          value = nombre,
          onValueChange = {
            nombre = it
            nameError = false
          },
          label = { Text("Nombre del producto *") },
          isError = nameError,
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
          modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = codigo,
            onValueChange = { codigo = it },
            label = { Text("Cód. barras / SKU") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = precioText,
            onValueChange = { precioText = it },
            label = { Text("Costo / Precio") },
            placeholder = { Text("Opcional") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
          )
        }
        OutlinedTextField(
          value = unidad,
          onValueChange = { unidad = it },
          label = { Text("Unidad / Empaque por bulto") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          quickUnits.forEach { u ->
            FilterChip(
              selected = unidad.equals(u, ignoreCase = true),
              onClick = { unidad = u },
              label = { Text(u) },
            )
          }
        }
        OutlinedTextField(
          value = categoria,
          onValueChange = { categoria = it },
          label = { Text("Categoría / Rubro (opcional)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (nombre.isBlank()) {
            nameError = true
          } else {
            onSave(
              codigo.trim(),
              nombre.trim(),
              unidad.trim().ifBlank { "u" },
              PriceParser.parse(precioText),
              categoria.trim(),
              pickedPhotoUri,
            )
          }
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

@Composable
fun ManualOrderItemDialog(
  onDismiss: () -> Unit,
  onAdd: (nombre: String, cantidad: Double, unidad: String, precio: Double?) -> Unit,
) {
  var nombre by remember { mutableStateOf("") }
  var cantidadText by remember { mutableStateOf("1") }
  var unidad by remember { mutableStateOf("bulto") }
  var precioText by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Ítem fuera de catálogo") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = nombre,
          onValueChange = {
            nombre = it
            error = null
          },
          label = { Text("Producto *") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
          modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = cantidadText,
            onValueChange = {
              cantidadText = it
              error = null
            },
            label = { Text("Cantidad *") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = unidad,
            onValueChange = { unidad = it },
            label = { Text("Unidad") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        OutlinedTextField(
          value = precioText,
          onValueChange = { precioText = it },
          label = { Text("Costo / Precio unitario (opcional)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
          Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val qty = PriceParser.parse(cantidadText)
          when {
            nombre.isBlank() -> error = "Ingresa el nombre del producto"
            qty == null || qty <= 0.0 -> error = "Ingresa una cantidad mayor a 0"
            else -> onAdd(nombre.trim(), qty, unidad.trim().ifBlank { "u" }, PriceParser.parse(precioText))
          }
        }
      ) {
        Text("Agregar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}

@Composable
fun QuantityInputDialog(
  producto: Producto,
  currentQuantity: Double,
  currentUnitPrice: Double?,
  onDismiss: () -> Unit,
  onConfirm: (cantidad: Double, unitPrice: Double?, savePriceToCatalog: Boolean) -> Unit,
) {
  var qtyText by remember {
    mutableStateOf(if (currentQuantity > 0.0) PriceParser.formatQuantity(currentQuantity) else "1")
  }
  var priceText by remember {
    mutableStateOf(currentUnitPrice?.let { PriceParser.formatQuantity(it) }.orEmpty())
  }
  var saveToCatalog by remember { mutableStateOf(false) }
  var invalid by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(producto.nombre) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          "Empaque: ${producto.unidad}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = qtyText,
          onValueChange = {
            qtyText = it
            invalid = false
          },
          label = { Text("Cantidad (0 para quitar)") },
          singleLine = true,
          isError = invalid,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = priceText,
          onValueChange = { priceText = it },
          label = { Text("Costo / Precio unitario (opcional)") },
          placeholder = { Text("Ej. 42.50") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth(),
        )
        if (priceText.isNotBlank()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = saveToCatalog, onCheckedChange = { saveToCatalog = it })
            Text(
              "Guardar este costo también en el catálogo",
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val parsedQty = if (qtyText.isBlank()) 0.0 else PriceParser.parse(qtyText)
          if (parsedQty == null || parsedQty < 0.0) {
            invalid = true
          } else {
            val parsedPrice = if (priceText.isBlank()) null else PriceParser.parse(priceText)
            onConfirm(parsedQty, parsedPrice, saveToCatalog && parsedPrice != null)
          }
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
