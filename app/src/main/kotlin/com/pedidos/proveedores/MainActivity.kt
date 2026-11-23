package com.pedidos.proveedores

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pedidos.proveedores.data.local.AppThemeMode
import com.pedidos.proveedores.domain.parser.PendingSharedPayload
import com.pedidos.proveedores.domain.parser.SharedInboxStaging
import com.pedidos.proveedores.ui.AppViewModel
import com.pedidos.proveedores.ui.theme.PedidosProveedoresTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

  private val pendingSharedPayload = MutableStateFlow<PendingSharedPayload?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val container = (application as PedidosApp).container
    consumeIncomingShareIntent(intent)

    enableEdgeToEdge()
    setContent {
      val themeMode by container.themeSettingsStore.mode.collectAsState()
      val sharedPayload by pendingSharedPayload.collectAsState()
      val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
      }

      DisposableEffect(isDark) {
        enableEdgeToEdge(
          statusBarStyle = if (isDark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
          } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
          },
          navigationBarStyle = if (isDark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
          } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
          },
        )
        onDispose {}
      }

      PedidosProveedoresTheme(darkTheme = isDark) {
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
              pricingSettingsStore = container.pricingSettingsStore,
              bcvRateClient = container.bcvRateClient,
            )
          )
          MainNavigation(
            viewModel = appViewModel,
            themeMode = themeMode,
            onCycleTheme = {
              val next = container.themeSettingsStore.cycleMode()
              appViewModel.showMessage("Tema: ${next.etiqueta}")
            },
            pendingSharedPayload = sharedPayload,
            onConsumeSharedPayload = { pendingSharedPayload.value = null },
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    consumeIncomingShareIntent(intent)
  }

  private fun consumeIncomingShareIntent(incoming: Intent?) {
    if (incoming == null) return
    val action = incoming.action
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
    val staged = SharedInboxStaging.extractFromIntent(this, incoming)
    if (staged != null) {
      pendingSharedPayload.value = staged
      // Limpiar la acción para evitar re-procesar al rotar pantalla
      incoming.action = Intent.ACTION_MAIN
    }
  }
}
