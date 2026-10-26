@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.sohan.linkpilot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sohan.linkpilot.R
import dev.sohan.linkpilot.data.RelayInfo
import dev.sohan.linkpilot.data.ScanNetwork
import dev.sohan.linkpilot.ui.SECURITY_CHOICES
import dev.sohan.linkpilot.ui.components.Caption
import dev.sohan.linkpilot.ui.components.Choice
import dev.sohan.linkpilot.ui.components.ConfirmDialog
import dev.sohan.linkpilot.ui.components.DropdownField
import dev.sohan.linkpilot.ui.components.EmptyState
import dev.sohan.linkpilot.ui.components.InfoRow
import dev.sohan.linkpilot.ui.components.LoadingBlock
import dev.sohan.linkpilot.ui.components.ScreenScaffold
import dev.sohan.linkpilot.ui.components.SecretField
import dev.sohan.linkpilot.ui.components.SectionCard
import dev.sohan.linkpilot.ui.components.SegmentedField
import dev.sohan.linkpilot.ui.relayModeLabel
import dev.sohan.linkpilot.ui.securityLabel
import dev.sohan.linkpilot.ui.signalLabel
import dev.sohan.linkpilot.vm.RouterUiState
import dev.sohan.linkpilot.vm.RouterViewModel

@Composable
fun RelayScreen(ui: RouterUiState, vm: RouterViewModel, onBack: () -> Unit) {
    val relay = ui.relay
    ScreenScaffold(
        title = "Wireless repeating",
        onBack = onBack,
        actions = {
            IconButton(onClick = vm::refreshRelay) { Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh") }
        },
    ) { padding ->
        if (relay == null) {
            Column(Modifier.padding(padding).padding(16.dp)) { LoadingBlock("Reading operating mode") }
        } else {
            RelayContent(relay, ui, vm, padding)
        }
    }
}

@Composable
private fun RelayContent(relay: RelayInfo, ui: RouterUiState, vm: RouterViewModel, padding: PaddingValues) {
    var mode by remember(relay.type) { mutableStateOf(relay.type.ifBlank { "disabled" }) }
    var confirmMode by remember { mutableStateOf(false) }
    var join by remember { mutableStateOf<ScanNetwork?>(null) }

    val modes = listOf(
        Choice("disabled", "Off"),
        Choice("ap", "AP"),
        Choice("wisp", "WISP"),
        Choice("client+ap", "Client+AP"),
    )

    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "Operating mode",
                subtitle = "Changing mode reboots the router",
            ) {
                DropdownField(
                    label = "Mode",
                    options = modes,
                    selected = mode,
                    onSelect = { mode = it },
                )
                when (mode) {
                    "disabled" -> Caption("The router uses its WAN port only.")
                    "ap" -> Caption("Wired uplink, Wi-Fi served locally. The router may move to tendawifi.com.")
                    "wisp" -> Caption("Wi-Fi uplink with a separate LAN subnet.")
                    "client+ap" -> Caption("Wi-Fi uplink bridged to the LAN; clients share the upstream subnet.")
                }
                Button(
                    onClick = { confirmMode = true },
                    enabled = mode != relay.type,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Change mode") }
            }
        }

        item {
            SectionCard(title = "Upstream link") {
                InfoRow("Mode", relayModeLabel(relay.type))
                InfoRow("SSID", relay.ssid)
                InfoRow("BSSID", relay.mac, copyable = true)
                InfoRow("Channel", relay.channel)
                InfoRow("Security", securityLabel(relay.security))
                InfoRow("Status", relay.connectStatus)
                InfoRow("Connected for", if (relay.duration.isBlank() || relay.duration == "0") "—" else relay.duration)
            }
        }

        item {
            SectionCard(
                title = "Scan for upstream networks",
                subtitle = "A scan blocks the router for about five seconds",
            ) {
                FilledTonalButton(
                    onClick = vm::scan,
                    enabled = !ui.scanning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (ui.scanning) "Scanning…" else "Scan")
                }
                if (ui.scanning) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                if (ui.scan.isEmpty() && !ui.scanning) {
                    EmptyState("No scan yet", "Tap Scan to list nearby access points.", R.drawable.ic_wifi)
                }
            }
        }

        val networks = ui.scan
        items(networks, key = { it.mac + it.ssid }) { network ->
            Surface(
                onClick = { join = network },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_wifi), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            network.ssid.ifBlank { "(hidden network)" },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "channel ${network.channel} · ${securityLabel(network.security)} · ${signalLabel(network.signal)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("${network.signal} dBm", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        item {
            SectionCard(title = "Before you switch") {
                Text(
                    "Repeater and AP modes re-address the management UI. If the phone loses the router, reconnect to its new address or to tendawifi.com.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (confirmMode) {
        ConfirmDialog(
            title = "Switch to ${modes.first { it.value == mode }.label}?",
            body = "The router reboots and Wi-Fi drops for up to a minute. If the mode changes the LAN, this app loses its session until you reconnect.",
            confirmLabel = "Reboot",
            destructive = true,
            onConfirm = {
                confirmMode = false
                vm.saveRelay(mode, relay.ssid, relay.mac, relay.security, relay.password, relay.channel)
            },
            onDismiss = { confirmMode = false },
        )
    }

    join?.let { network ->
        JoinDialog(
            network = network,
            onDismiss = { join = null },
            onJoin = { password, security ->
                join = null
                vm.saveRelay(
                    type = if (mode == "disabled") "wisp" else mode,
                    ssid = network.ssid,
                    mac = network.mac,
                    security = security,
                    password = password,
                    channel = network.channel,
                )
            },
        )
    }
}

@Composable
private fun JoinDialog(
    network: ScanNetwork,
    onDismiss: () -> Unit,
    onJoin: (String, String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var security by remember { mutableStateOf(mapSecurity(network.security)) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join ${network.ssid}", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Channel ${network.channel} · ${securityLabel(network.security)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SegmentedField(
                    label = "Security",
                    options = SECURITY_CHOICES,
                    selected = security,
                    onSelect = { security = it },
                )
                if (security != "none") {
                    SecretField(password, { password = it }, "Upstream password")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (security != "none" && password.length < 8) {
                    error = "WPA keys need at least 8 characters"
                } else {
                    onJoin(password, security)
                }
            }) { Text("Join & reboot") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun mapSecurity(scan: String): String = when {
    scan.equals("NONE", true) || scan.isBlank() -> "none"
    scan.contains("WPA", true) && scan.contains("WPA2", true) -> "wpa&wpa2"
    scan.contains("WPA2", true) -> "wpa2-psk"
    scan.contains("WPA", true) -> "wpa-psk"
    else -> "wpa2-psk"
}
