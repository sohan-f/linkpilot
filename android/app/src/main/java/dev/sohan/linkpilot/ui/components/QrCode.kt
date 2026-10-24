package dev.sohan.linkpilot.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private fun escape(value: String): String =
    value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")

fun wifiJoinString(ssid: String, password: String, security: String): String {
    val type = if (security == "none") "nopass" else "WPA"
    return "WIFI:T:$type;S:${escape(ssid)};P:${escape(password)};;"
}

private fun wifiQr(ssid: String, password: String, security: String, size: Int = 720): Bitmap? {
    return runCatching {
        val matrix = QRCodeWriter().encode(wifiJoinString(ssid, password, security), BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        }
    }.getOrNull()
}

@Composable
fun WifiQrDialog(
    ssid: String,
    password: String,
    security: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap = remember(ssid, password, security) { wifiQr(ssid, password, security) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join \"$ssid\"", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR code containing the Wi-Fi credentials",
                        modifier = Modifier.size(220.dp),
                    )
                } else {
                    Text(
                        "Could not render the QR code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    if (security == "none") "Open network — no password" else "Scan with the other device's camera",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "The code contains the Wi-Fi password. Show it only to people you trust.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Wi-Fi network $ssid")
                        putExtra(Intent.EXTRA_TEXT, wifiJoinString(ssid, password, security))
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Wi-Fi"))
                },
            ) { Text("Share") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
