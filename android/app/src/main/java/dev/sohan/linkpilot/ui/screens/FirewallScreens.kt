@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.sohan.linkpilot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sohan.linkpilot.data.MacFilterEntry
import dev.sohan.linkpilot.data.NatSnapshot
import dev.sohan.linkpilot.data.forFilterMode
import dev.sohan.linkpilot.ui.components.Caption
import dev.sohan.linkpilot.ui.components.Choice
import dev.sohan.linkpilot.ui.components.ConfirmDialog
import dev.sohan.linkpilot.ui.components.DiffDialog
import dev.sohan.linkpilot.ui.components.EmptyState
import dev.sohan.linkpilot.ui.components.FormField
import dev.sohan.linkpilot.ui.components.InfoRow
import dev.sohan.linkpilot.ui.components.LoadingBlock
import dev.sohan.linkpilot.ui.components.ScreenScaffold
import dev.sohan.linkpilot.ui.components.SectionCard
import dev.sohan.linkpilot.ui.components.SecretField
import dev.sohan.linkpilot.ui.components.SegmentedField
import dev.sohan.linkpilot.ui.components.SettingSwitch
import dev.sohan.linkpilot.ui.isIpv4
import dev.sohan.linkpilot.ui.isMac
import dev.sohan.linkpilot.ui.sameSubnet
import dev.sohan.linkpilot.vm.RouterUiState
import dev.sohan.linkpilot.vm.RouterViewModel

@Composable
fun FirewallScreen(ui: RouterUiState, vm: RouterViewModel, onBack: () -> Unit) {
    val nat = ui.nat
    ScreenScaffold(
        title = "Firewall & UPnP",
        onBack = onBack,
        actions = {
            IconButton(onClick = vm::refreshNat) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
        },
    ) { padding ->
        if (nat == null) {
            Column(Modifier.padding(padding).padding(16.dp)) { LoadingBlock("Reading firewall state") }
        } else {
            FirewallContent(nat, ui, vm, padding)
        }
    }
}

@Composable
private fun FirewallContent(nat: NatSnapshot, ui: RouterUiState, vm: RouterViewModel, padding: PaddingValues) {
    var dmzOn by remember(nat.dmzEnabled) { mutableStateOf(nat.dmzEnabled) }
    var dmzHost by remember(nat.dmzHost) { mutableStateOf(nat.dmzHost) }
    var ddnsOn by remember(nat.ddnsEnabled) { mutableStateOf(nat.ddnsEnabled) }
    var service by remember(nat.ddnsService) { mutableStateOf(nat.ddnsService.ifBlank { "oray.com" }) }
    var server by remember(nat.ddnsServer) { mutableStateOf(nat.ddnsServer) }
    var user by remember(nat.ddnsUser) { mutableStateOf(nat.ddnsUser) }
    var pwd by remember(nat.ddnsPassword) { mutableStateOf(nat.ddnsPassword) }

    var confirmDmz by remember { mutableStateOf(false) }
    var confirmDdns by remember { mutableStateOf(false) }
    var confirmUpnp by remember { mutableStateOf(false) }

    val lanIp = ui.wan?.lan?.ip.orEmpty()
    val lanMask = ui.wan?.lan?.mask.orEmpty()
    val dmzError = when {
        !dmzOn -> null
        !isIpv4(dmzHost) -> "Enter a valid LAN address"
        lanIp.isNotBlank() && lanMask.isNotBlank() && !sameSubnet(dmzHost, lanIp, lanMask) -> "Host must be on the LAN subnet"
        else -> null
    }

    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "DMZ host", subtitle = "Expose one LAN device to all WAN ports") {
                SettingSwitch(
                    title = "Enable DMZ",
                    checked = dmzOn,
                    onCheckedChange = { dmzOn = it },
                    subtitle = "Overrides port forwarding for this host",
                )
                if (dmzOn) {
                    FormField(dmzHost, { dmzHost = it }, "DMZ host IP", error = dmzError, keyboard = KeyboardType.Uri)
                }
                Button(
                    onClick = { confirmDmz = true },
                    enabled = (dmzOn != nat.dmzEnabled || dmzHost != nat.dmzHost) && dmzError == null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Apply DMZ") }
                Caption("A DMZ host is fully reachable from the Internet; keep it patched.")
            }
        }

        item {
            SectionCard(title = "Dynamic DNS") {
                SettingSwitch(
                    title = "Enable DDNS",
                    checked = ddnsOn,
                    onCheckedChange = { ddnsOn = it },
                    subtitle = "Status: ${nat.ddnsStatus.ifBlank { "—" }}",
                )
                if (ddnsOn) {
                    FormField(service, { service = it }, "Provider", helper = "The stock UI uses oray.com")
                    FormField(server, { server = it }, "Server", helper = "Leave empty to use the provider default")
                    FormField(user, { user = it }, "Username / hostname")
                    SecretField(pwd, { pwd = it }, "Password or token")
                }
                Button(
                    onClick = { confirmDdns = true },
                    enabled = ddnsOn != nat.ddnsEnabled || service != nat.ddnsService || server != nat.ddnsServer ||
                        user != nat.ddnsUser || pwd != nat.ddnsPassword,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Apply DDNS") }
            }
        }

        item {
            SectionCard(title = "Discovery") {
                SettingSwitch(
                    title = "UPnP",
                    checked = nat.upnp,
                    onCheckedChange = {
                        if (it) confirmUpnp = true else vm.setUpnp(false)
                    },
                    subtitle = "Lets LAN apps open ports without asking",
                )
                SettingSwitch(
                    title = "Respond to WAN ping",
                    checked = nat.wanPing,
                    onCheckedChange = vm::setWanPing,
                    subtitle = "Makes the router visible to Internet scanners",
                )
                SettingSwitch(
                    title = "AP isolation",
                    checked = nat.apIsolation,
                    onCheckedChange = vm::setApIsolation,
                    subtitle = "Separates Wi-Fi clients from each other",
                )
            }
        }

        item {
            SectionCard(title = "Port forwarding") {
                InfoRow("Active rules", nat.ports.size.toString())
                InfoRow("Reservations", nat.leases.size.toString())
                Caption("Port rules live in More → Port forwarding.")
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (confirmDmz) {
        DiffDialog(
            title = if (dmzOn) "Enable DMZ for $dmzHost?" else "Disable DMZ?",
            changes = listOf(
                "DMZ" to (nat.dmzEnabled.toString() to dmzOn.toString()),
                "Host" to (nat.dmzHost to if (dmzOn) dmzHost else nat.dmzHost),
            ),
            warning = if (dmzOn) "Every unsolicited WAN connection reaches $dmzHost." else null,
            onConfirm = {
                confirmDmz = false
                vm.setDmz(dmzOn, dmzHost)
            },
            onDismiss = { confirmDmz = false },
        )
    }

    if (confirmDdns) {
        DiffDialog(
            title = "Apply DDNS?",
            changes = listOf(
                "Enabled" to (nat.ddnsEnabled.toString() to ddnsOn.toString()),
                "Provider" to (nat.ddnsService to service),
                "User" to (nat.ddnsUser to user),
            ),
            warning = null,
            onConfirm = {
                confirmDdns = false
                vm.setDdns(ddnsOn, service, server, user, pwd)
            },
            onDismiss = { confirmDdns = false },
        )
    }

    if (confirmUpnp) {
        ConfirmDialog(
            title = "Enable UPnP?",
            body = "Any app on the LAN can ask the router to open a WAN port. Enable it only for consoles or apps you trust.",
            confirmLabel = "Enable",
            onConfirm = {
                confirmUpnp = false
                vm.setUpnp(true)
            },
            onDismiss = { confirmUpnp = false },
        )
    }
}

@Composable
fun MacFilterScreen(ui: RouterUiState, vm: RouterViewModel, onBack: () -> Unit) {
    val nat = ui.nat
    ScreenScaffold(
        title = "MAC filter",
        onBack = onBack,
        actions = {
            IconButton(onClick = vm::refreshNat) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
        },
    ) { padding ->
        if (nat == null) {
            Column(Modifier.padding(padding).padding(16.dp)) { LoadingBlock("Reading the MAC filter") }
        } else {
            MacFilterContent(ui, vm, padding)
        }
    }
}

@Composable
private fun MacFilterContent(ui: RouterUiState, vm: RouterViewModel, padding: PaddingValues) {
    val nat = ui.nat ?: return
    var mode by remember(nat.filterMode) { mutableStateOf(nat.filterMode.ifBlank { "deny" }) }
    val allEntries = remember(nat.filterEntries) {
        mutableStateListOf<MacFilterEntry>().apply { addAll(nat.filterEntries) }
    }
    val entries = allEntries.forFilterMode(mode, nat.filterMode)
    val initialForMode = nat.filterEntries.forFilterMode(mode, nat.filterMode)
    val limit = if (mode == "pass") 20 else 10
    val phoneReserved = mode == "pass" && nat.localMac.isNotBlank() &&
        entries.none { it.mac.equals(nat.localMac, ignoreCase = true) }
    val effectiveLimit = if (phoneReserved) limit - 1 else limit
    val overCapacity = entries.size + (if (phoneReserved) 1 else 0) > limit
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MacFilterEntry?>(null) }
    var confirmSave by remember { mutableStateOf(false) }
    // Compare only the visible list; the other mode's entries stay untouched in the router.
    val changed = mode != nat.filterMode ||
        entries.map { it.copy(mode = "") } != initialForMode.map { it.copy(mode = "") }

    fun entryName(entry: MacFilterEntry) = ui.deviceName(
        entry.mac,
        filterRemark = entry.remark,
        hostname = entry.hostname,
        fallback = entry.mac,
    )

    // Names come from the QoS table and reservations too, so adding a known device does not
    // require retyping its MAC or inventing a second label.
    val suggestions = remember(ui.qos, ui.nat, mode, allEntries.toList()) {
        buildList {
            (ui.qos?.clients.orEmpty() + ui.qos?.blocked.orEmpty()).forEach { client ->
                add(
                    MacFilterEntry(
                        client.hostname,
                        ui.deviceName(client.mac, hostname = client.hostname, fallback = client.ip),
                        client.mac,
                        mode,
                    )
                )
            }
            nat.leases.forEach { lease ->
                add(
                    MacFilterEntry(
                        "",
                        ui.deviceName(lease.mac, leaseRemark = lease.remark, fallback = lease.mac),
                        lease.mac,
                        mode,
                    )
                )
            }
        }
            .filter { it.mac.isNotBlank() }
            .distinctBy { it.mac.lowercase() }
            .filterNot { candidate -> allEntries.any { it.mac.equals(candidate.mac, ignoreCase = true) } }
            .filterNot { candidate ->
                mode == "deny" && nat.localMac.isNotBlank() &&
                    candidate.mac.equals(nat.localMac, ignoreCase = true)
            }
            .filter { it.remark.isNotBlank() && !it.remark.equals(it.mac, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionCard(title = "Mode") {
                SegmentedField(
                    label = "Filtering rule",
                    options = listOf(Choice("deny", "Block listed"), Choice("pass", "Allow only listed")),
                    selected = mode,
                    onSelect = { mode = it },
                )
                Caption(
                    if (mode == "deny") "Listed devices are denied Wi-Fi and LAN access."
                    else "Only listed devices can join the network. Keep at least one admin device listed."
                )
                Caption("The firmware keeps a separate list for each rule; only the selected list is sent on save.")
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${entries.size} entries",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = { adding = true }, enabled = entries.size < effectiveLimit) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        if (mode == "pass") {
            item {
                Caption(
                    when {
                        nat.localMac.isBlank() -> "Saving keeps the app's own device reachable when possible."
                        phoneReserved -> "One of the 20 slots is reserved: this phone's MAC (${nat.localMac}) is added when you save."
                        else -> "This phone is on the allow list, so saving keeps the app reachable."
                    }
                )
            }
        }

        if (mode == "deny" && nat.localMac.isNotBlank() &&
            entries.any { it.mac.equals(nat.localMac, ignoreCase = true) }
        ) {
            item {
                Text(
                    "This block list contains this phone's MAC. Remove it before saving, or the app loses access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (overCapacity) {
            item {
                Text(
                    if (mode == "pass") {
                        "The allow list would exceed 20 entries after this phone is added. Remove one entry before saving."
                    } else {
                        "The block list exceeds the firmware limit of 10 entries. Remove one before saving."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (entries.size >= effectiveLimit) {
            item {
                Caption(
                    if (phoneReserved) {
                        "The firmware allows 20 allow-list entries; one slot is reserved for this phone."
                    } else {
                        "The firmware accepts at most $limit ${if (mode == "pass") "whitelist" else "block list"} entries."
                    }
                )
            }
        }

        if (entries.isEmpty()) {
            item { EmptyState("No MAC entries", "Add a device by MAC address to enforce the selected rule.") }
        }

        itemsIndexed(entries, key = { index, _ -> index }) { _, entry ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entryName(entry), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            entry.mac,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editing = entry }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit entry")
                    }
                    IconButton(onClick = { allEntries.remove(entry) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Remove entry")
                    }
                }
            }
        }

        item {
            Button(
                onClick = { confirmSave = true },
                enabled = changed && !overCapacity,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) { Text("Save filter") }
        }

        item {
            Text(
                "Renaming an entry updates the same device's label in Devices and DHCP reservations. " +
                    "The firmware treats the list as a full replacement, so changes apply immediately.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (adding) {
        MacEntryDialog(
            existing = entries,
            mode = mode,
            localMac = nat.localMac,
            suggestions = suggestions,
            onDismiss = { adding = false },
            onSave = {
                allEntries.add(it)
                adding = false
            },
        )
    }

    editing?.let { original ->
        val index = allEntries.indexOf(original)
        if (index >= 0) {
            MacEntryDialog(
                existing = entries.filter { it !== original },
                mode = mode,
                localMac = nat.localMac,
                initial = original,
                initialName = entryName(original)
                    .takeUnless { it.equals(original.mac, ignoreCase = true) }
                    .orEmpty(),
                onDismiss = { editing = null },
                onSave = { updated ->
                    allEntries[index] = updated
                    editing = null
                },
            )
        }
    }

    if (confirmSave) {
        DiffDialog(
            title = "Save MAC filter?",
            changes = listOf(
                "Mode" to (nat.filterMode to mode),
                "Entries" to (initialForMode.size.toString() to entries.size.toString()),
            ),
            warning = if (mode == "pass") {
                "Allow-only mode blocks every device not in the list. Keep a wired connection available."
            } else {
                "Listed devices lose access immediately."
            },
            onConfirm = {
                confirmSave = false
                vm.setMacFilter(mode, entries.toList())
            },
            onDismiss = { confirmSave = false },
        )
    }
}

@Composable
private fun MacEntryDialog(
    existing: List<MacFilterEntry>,
    mode: String,
    localMac: String,
    onDismiss: () -> Unit,
    onSave: (MacFilterEntry) -> Unit,
    initial: MacFilterEntry? = null,
    initialName: String = "",
    suggestions: List<MacFilterEntry> = emptyList(),
) {
    var mac by remember(initial) { mutableStateOf(initial?.mac ?: "") }
    var name by remember(initial, initialName) {
        mutableStateOf(initialName.ifBlank { initial?.remark.orEmpty() })
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) "Add MAC entry" else "Edit MAC entry",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (initial == null && suggestions.isNotEmpty()) {
                    Text("Known devices", style = MaterialTheme.typography.labelLarge)
                    suggestions.take(12).forEach { candidate ->
                        TextButton(
                            onClick = {
                                mac = candidate.mac
                                name = candidate.remark
                                error = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                candidate.remark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                candidate.mac,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                FormField(mac, { mac = it }, "MAC address", placeholder = "AA:BB:CC:DD:EE:FF")
                FormField(name, { name = it }, "Label (optional)")
                Text(
                    "The label is shared with Devices and DHCP reservations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    !isMac(mac) -> "Invalid MAC address"
                    !mac.equals(initial?.mac.orEmpty(), ignoreCase = true) &&
                        existing.any { it.mac.equals(mac, true) } -> "That MAC is already listed"
                    mode == "deny" && localMac.isNotBlank() && mac.equals(localMac, true) ->
                        "That is this phone; blocking it would cut off LinkPilot"
                    else -> null
                }
                if (error == null) onSave(
                    MacFilterEntry(
                        hostname = if (initial != null && mac.equals(initial.mac, ignoreCase = true)) {
                            initial.hostname
                        } else {
                            ""
                        },
                        remark = name.trim(),
                        mac = mac.lowercase(),
                        mode = mode,
                    )
                )
            }) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
