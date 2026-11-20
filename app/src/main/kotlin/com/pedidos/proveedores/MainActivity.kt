package com.pedidos.proveedores

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pedidos.proveedores.ui.AppViewModel
import com.pedidos.proveedores.ui.theme.PedidosProveedoresTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val container = (application as PedidosApp).container

    enableEdgeToEdge()
    setContent {
      PedidosProveedoresTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          val appViewModel: AppViewModel = viewModel(
            factory = AppViewModel.factory(
              repository = container.repository,
              catalogImporter = container.catalogImporter,
              aiSettingsStore = container.aiSettingsStore,
              geminiExtractor = container.geminiExtractor,
              otaSettingsStore = container.otaSettingsStore,
              otaUpdater = container.otaUpdater,
              isaacPosSettingsStore = container.isaacPosSettingsStore,
              isaacPosClient = container.isaacPosClient,
            )
          )
          MainNavigation(viewModel = appViewModel)
        }
      }
    }
  }
}
