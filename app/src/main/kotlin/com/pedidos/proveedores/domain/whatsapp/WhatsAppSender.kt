package com.pedidos.proveedores.domain.whatsapp

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

object WhatsAppSender {

  private const val WHATSAPP_PACKAGE = "com.whatsapp"
  private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

  /**
   * Envía el mensaje directamente al WhatsApp del proveedor (si hay número cargado)
   * o abre WhatsApp / selector de compartir del teléfono si no tiene número o no está instalado.
   * Devuelve true si se pudo lanzar una actividad receptora.
   */
  fun sendMessage(context: Context, phoneRaw: String, message: String): Boolean {
    val cleanPhone = WhatsAppOrderFormatter.sanitizePhone(phoneRaw)
    val encodedMessage = URLEncoder.encode(message, Charsets.UTF_8.name())

    if (cleanPhone.length >= 7) {
      val waUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage")
      // Intentar abrir directamente en WhatsApp o WhatsApp Business
      for (pkg in listOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE)) {
        val directIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
          setPackage(pkg)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(context, directIntent)) return true
      }
      // Fallback: abrir enlace wa.me en navegador / app por defecto
      val browserIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      if (tryStart(context, browserIntent)) return true
    } else {
      // Sin teléfono: enviar texto directo a WhatsApp o WhatsApp Business para elegir contacto
      for (pkg in listOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE)) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_TEXT, message)
          setPackage(pkg)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(context, sendIntent)) return true
      }
    }

    // Fallback universal: compartir con cualquier app instalada
    val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, message)
    }
    val chooser = Intent.createChooser(fallbackIntent, "Enviar pedido").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return tryStart(context, chooser)
  }

  fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Pedido a proveedor", text))
  }

  private fun tryStart(context: Context, intent: Intent): Boolean {
    return try {
      context.startActivity(intent)
      true
    } catch (_: ActivityNotFoundException) {
      false
    } catch (_: SecurityException) {
      false
    }
  }
}
