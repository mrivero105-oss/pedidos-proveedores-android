package com.pedidos.proveedores

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute : NavKey
@Serializable data class ProveedorDetalleRoute(val proveedorId: Long) : NavKey
@Serializable data class ImportarCatalogoRoute(val proveedorId: Long) : NavKey
@Serializable data class VisorPdfRoute(val rutaLocal: String, val titulo: String) : NavKey
@Serializable data class VisorImagenRoute(val rutas: List<String>, val titulo: String) : NavKey
@Serializable data class PedidoDetalleRoute(val pedidoId: Long) : NavKey
@Serializable data object IsaacPosSyncRoute : NavKey
@Serializable data class ConciliarCatalogoRoute(val proveedorId: Long) : NavKey
