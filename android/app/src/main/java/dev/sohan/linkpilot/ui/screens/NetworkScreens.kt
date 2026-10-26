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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sohan.linkpilot.R
import dev.sohan.linkpilot.data.LanConfig
import dev.sohan.linkpilot.data.PortRule
import dev.sohan.linkpilot.data.StaticLease
import dev.sohan.linkpilot.data.WanBasic
import dev.sohan.linkpilot.data.WanSnapshot
import dev.sohan.linkpilot.ui.components.Caption
import dev.sohan.linkpilot.ui.components.Choice
import dev.sohan.linkpilot.ui.components.DiffDialog
import dev.sohan.linkpilot.ui.components.EmptyState
import dev.sohan.linkpilot.ui.components.FormField
import dev.sohan.linkpilot.ui.components.InfoRow
import dev.sohan.linkpilot.ui.components.LoadingBlock
import dev.sohan.linkpilot.ui.components.ScreenScaffold
import dev.sohan.linkpilot.ui.components.SecretField
import dev.sohan.linkpilot.ui.components.SectionCard
import dev.sohan.linkpilot.ui.components.SegmentedField
import dev.sohan.linkpilot.ui.components.SettingSwitch
import dev.sohan.linkpilot.ui.components.TextFieldDialog
import dev.sohan.linkpilot.ui.ipToLong
import dev.sohan.linkpilot.ui.isIpv4
import dev.sohan.linkpilot.ui.isMac
import dev.sohan.linkpilot.ui.sameSubnet
import dev.sohan.linkpilot.vm.RouterUiState
import dev.sohan.linkpilot.vm.RouterViewModel

@Composable
fun WanScreen(ui: RouterUiState, vm: RouterViewModel, onBack: () -> Unit) {
    val wan = ui.wan
    ScreenScaffold(
        title = "Internet (WAN)",
        onBack = onBack,
        actions = {
            IconButton(onClick = vm::refreshWan) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh WAN")
            }
        },
    ) { padding ->
        if (wan == null) {
            Column(Modifier.padding(padding).padding(16.dp)) { LoadingBlock("Reading WAN configuration") }
        } else {
            WanContent(wan, ui, vm, padding)
        }
    }
}

@Composable
private fun WanContent(wan: WanSnapshot, ui: RouterUiState, vm: RouterViewModel, padding: PaddingValues) {
    var type by remember(wan.wan.type) { mutableStateOf(wan.wan.type.ifBlank { "dhcp" }) }
    var user by remember(wan.wan.pppoeUser) { mutableStateOf(wan.wan.pppoeUser) }
    var pwd by remember(wan.wan.pppoePassword) { mutableStateOf(wan.wan.pppoePassword) }
    var ip by remember(wan.wan.ip) { mutableStateOf(wan.wan.ip) }
    var mask by remember(wan.wan.mask) { mutableStateOf(wan.wan.mask) }
    var gateway by remember(wan.wan.gateway) { mutableStateOf(wan.wan.gateway) }
    var dns1 by remember(wan.wan.dns1) { mutableStateOf(wan.wan.dns1) }
    var dns2 by remember(wan.wan.dns2) { mutableStateOf(wan.wan.dns2) }

    val adv = wan.advanced
    var mtu by remember(adv.mtu) { mutableStateOf(adv.mtu.ifBlank { "1500" }) }
    var speed by remember(adv.speed) { mutableStateOf(adv.speed.ifBlank { "Auto" }) }
    var macMode by remember(adv.macClone) { mutableStateOf(adv.macClone.ifBlank { "default" }) }
    var mac by remember(adv.macCurrentWan) { mutableStateOf(adv.macCurrentWan) }
    var serverName by remember(adv.serverName) { mutableStateOf(adv.serverName) }
    var serviceName by remember(adv.serviceName) { mutableStateOf(adv.serviceName) }

    var confirmBasic by remember { mutableStateOf(false) }
    var confirmAdvanced by remember { mutableStateOf(false) }

    val basicDirty = type != wan.wan.type || user != wan.wan.pppoeUser || pwd != wan.wan.pppoePassword ||
        ip != wan.wan.ip || mask != wan.wan.mask || gateway != wan.wan.gateway ||
        dns1 != wan.wan.dns1 || dns2 != wan.wan.dns2

    val errors = buildMap {
        if (type == "pppoe") {
            if (user.isBlank()) put("user", "PPPoE username is required")
            if (pwd.isBlank()) put("pwd", "PPPoE password is required")
        }
        if (type == "static") {
            if (!isIpv4(ip)) put("ip", "Enter a valid IPv4 address")
            if (!isIpv4(mask)) put("mask", "Enter a valid subnet mask")
            if (!isIpv4(gateway)) put("gateway", "Enter a valid gateway")
            if (dns1.isNotBlank() && !isIpv4(dns1)) put("dns1", "Invalid DNS address")
            if (dns2.isNotBlank() && !isIpv4(dns2)) put("dns2", "Invalid DNS address")
        }
        val mtuValue = mtu.toIntOrNull()
        if (mtuValue == null || mtuValue !in 576..1500) put("mtu", "MTU must be 576–1500")
        if (macMode == "manual" && !isMac(mac)) put("mac", "Use AA:BB:CC:DD:EE:FF")
    }

    val macDirty = macMode != adv.macClone || mtu != adv.mtu || speed != adv.speed ||
        serverName != adv.serverName || serviceName != adv.serviceName ||
        (macMode == "manual" && !mac.equals(adv.macCurrentWan, ignoreCase = true))

    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Link status") {
                InfoRow("State", wan.internet.message)
                InfoRow("WAN address", ui.status?.system?.wanIp ?: wan.wan.ip)
                InfoRow("Negotiated MTU", adv.mtuCurrent)
                InfoRow("Negotiated speed", adv.speedCurrent)
                InfoRow("WAN MAC", adv.macCurrentWan, copyable = true)
            }
        }

        item {
            SectionCard(title = "Connection", subtitle = "The router replaces the whole WAN module") {
                SegmentedField(
                    label = "Type",
                    options = listOf(
                        Choice("dhcp", "DHCP"),
                        Choice("pppoe", "PPPoE"),
                        Choice("static", "Static"),
                    ),
                    selected = type,
                    onSelect = { type = it },
                )
                when (type) {
                    "pppoe" -> {
                        FormField(user, { user = it }, "PPPoE username", error = errors["user"])
                        SecretField(pwd, { pwd = it }, "PPPoE password", error = errors["pwd"])
                        Caption("Returned in plaintext by the firmware; the app never logs it.")
                    }
                    "static" -> {
                        FormField(ip, { ip = it }, "IP address", error = errors["ip"], keyboard = KeyboardType.Uri)
                        FormField(mask, { mask = it }, "Subnet mask", error = errors["mask"], keyboard = KeyboardType.Uri)
                        FormField(gateway, { gateway = it }, "Gateway", error = errors["gateway"], keyboard = KeyboardType.Uri)
                        FormField(dns1, { dns1 = it }, "DNS 1 (optional)", error = errors["dns1"], keyboard = KeyboardType.Uri)
                        FormField(dns2, { dns2 = it }, "DNS 2 (optional)", error = errors["dns2"], keyboard = KeyboardType.Uri)
                    }
                    else -> {
                        Text(
                            "DHCP is selected. The router asks the upstream for an address; no credentials are needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = { confirmBasic = true },
                    enabled = basicDirty && errors.keys.none { it in setOf("user", "pwd", "ip", "mask", "gateway", "dns1", "dns2") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Preview & save connection") }
            }
        }

        item {
            SectionCard(title = "MAC clone & line", subtitle = adv.macCurrentWan) {
                SegmentedField(
                    label = "WAN MAC",
                    options = listOf(
                        Choice("default", "Factory"),
                        Choice("clone", "Clone phone"),
                        Choice("manual", "Manual"),
                    ),
                    selected = macMode,
                    onSelect = { mode ->
                        macMode = mode
                        mac = when (mode) {
                            "clone" -> adv.macHost
                            "manual" -> if (isMac(mac)) mac else adv.macRouter
                            else -> adv.macRouter
                        }
                    },
                )
                InfoRow("Factory MAC", adv.macRouter, copyable = true)
                if (adv.macWifiDevice) {
                    Caption("The router sees this phone over Wi-Fi, so cloning the phone's MAC is hidden by the firmware.")
                }
                if (macMode != "default") {
                    FormField(mac, { mac = it }, "MAC address", error = errors["mac"], keyboard = KeyboardType.Text)
                }
                FormField(mtu, { mtu = it.filter(Char::isDigit) }, "MTU", error = errors["mtu"], keyboard = KeyboardType.Number,
                    helper = "576–1500; current ${adv.mtuCurrent}")
                SegmentedField(
                    label = "WAN speed",
                    options = listOf(
                        Choice("Auto", "Auto"),
                        Choice("100", "100 Mb/s"),
                        Choice("10", "10 Mb/s"),
                    ),
                    selected = speed,
                    onSelect = { speed = it },
                )
                FormField(serverName, { serverName = it }, "PPPoE server name (optional)", helper = "Leave empty for auto")
                FormField(serviceName, { serviceName = it }, "PPPoE service name (optional)", helper = "Leave empty for auto")
                Button(
                    onClick = { confirmAdvanced = true },
                    enabled = macDirty && errors["mtu"] == null && errors["mac"] == null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Apply MAC & line settings") }
            }
        }

        item {
            SectionCard(title = "Before you save") {
                Text(
                    "Changing the WAN type, credentials or MAC drops the uplink. If the change is wrong, the router may stay offline until you correct it from a device that still has LAN access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (confirmBasic) {
        DiffDialog(
            title = "Review WAN change",
            changes = buildList {
                if (type != wan.wan.type) add("Type" to (wan.wan.type to type))
                if (type == "pppoe" && user != wan.wan.pppoeUser) add("PPPoE user" to (wan.wan.pppoeUser to user))
                if (type == "static" && ip != wan.wan.ip) add("IP" to (wan.wan.ip to ip))
                if (type == "static" && gateway != wan.wan.gateway) add("Gateway" to (wan.wan.gateway to gateway))
            },
            warning = "The router reconnects the WAN. This app may show a timeout even when the change succeeded.",
            onConfirm = {
                confirmBasic = false
                vm.saveWan(
                    WanBasic(
                        type = type,
                        pppoeUser = if (type == "pppoe") user else "",
                        pppoePassword = if (type == "pppoe") pwd else "",
                        ip = if (type == "static") ip else "",
                        mask = if (type == "static") mask else "",
                        gateway = if (type == "static") gateway else "",
                        dns1 = if (type == "static") dns1 else "",
                        dns2 = if (type == "static") dns2 else "",
                    )
                )
            },
            onDismiss = { confirmBasic = false },
        )
    }

    if (confirmAdvanced) {
        DiffDialog(
            title = "Review MAC & line",
            changes = buildList {
                if (macMode != adv.macClone) add("MAC mode" to (adv.macClone to macMode))
                if (macMode != "default" && mac != adv.macCurrentWan) add("MAC" to (adv.macCurrentWan to mac))
                if (mtu != adv.mtu) add("MTU" to (adv.mtu to mtu))
                if (speed != adv.speed) add("Speed" to (adv.speed to speed))
            },
            warning = "A MAC clone takes about 30 seconds and reconnects the WAN.",
            onConfirm = {
                confirmAdvanced = false
                vm.saveWanAdvanced(
                    serverName = serverName,
                    serviceName = serviceName,
                    mtu = mtu,
                    speed = speed,
                    macClone = macMode,
                    mac = mac,
                )
            },
            onDismiss = { confirmAdvanced = false },
        )
    }
}

@Composable
fun LanScreen(ui: RouterUiState, vm: RouterViewModel, onBack: () -> Unit) {
    val wan = ui.wan
    ScreenScaffold(
        title = "LAN & DHCP",
        onBack = onBack,
        actions = {
            IconButton(onClick = vm::refreshWan) { Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh LAN") }
        },
    ) { padding ->
        if (wan == null) {
            Column(Modifier.padding(padding).padding(16.dp)) { LoadingBlock("Reading LAN configuration") }
        } else {
            LanContent(wan.lan, vm, padding)
        }
    }
}

@Composable
private fun LanContent(lan: LanConfig, vm: RouterViewModel, padding: PaddingValues) {
    var ip by remember(lan.ip) { mutableStateOf(lan.ip) }
    var mask by remember(lan.mask) { mutableStateOf(lan.mask) }
    var dhcp by remember(lan.dhcpEnabled) { mutableStateOf(lan.dhcpEnabled) }
    var start by remember(lan.dhcpStart) { mutableStateOf(lan.dhcpStart) }
    var end by remember(lan.dhcpEnd) { mutableStateOf(lan.dhcpEnd) }
    var dns1 by remember(lan.dns1) { mutableStateOf(lan.dns1) }
    var dns2 by remember(lan.dns2) { mutableStateOf(lan.dns2) }
    var confirm by remember { mutableStateOf(false) }

    val errors = buildMap {
        if (!isIpv4(ip)) put("ip", "Invalid router address")
        if (!isIpv4(mask)) put("mask", "Invalid mask")
        if (dhcp) {
            if (!isIpv4(start)) put("start", "Invalid start address")
            if (!isIpv4(end)) put("end", "Invalid end address")
            if (isIpv4(start) && isIpv4(end) &&
                (ipToLong(start) ?: 0L) > (ipToLong(end) ?: 0L)
            ) {
                put("end", "End must be after start")
            }
            if ((isIpv4(start) && start == ip) || (isIpv4(end) && end == ip)) {
                put("end", "Pool must exclude the router address")
            }
            if (isIpv4(ip) && isIpv4(mask) && isIpv4(start) && !sameSubnet(start, ip, mask)) {
                put("start", "Pool is outside the LAN subnet")
            }
            if (isIpv4(ip) && isIpv4(mask) && isIpv4(end) && !sameSubnet(end, ip, mask)) {
                put("end", "Pool is outside the LAN subnet")
            }
        }
        if (dns1.isNotBlank() && !isIpv4(dns1)) put("dns1", "Invalid DNS address")
        if (dns2.isNotBlank() && !isIpv4(dns2)) put("dns2", "Invalid DNS address")
    }

    val dirty = ip != lan.ip || mask != lan.mask || dhcp != lan.dhcpEnabled ||
        start != lan.dhcpStart || end != lan.dhcpEnd || dns1 != lan.dns1 || dns2 != lan.dns2

    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Router address") {
                FormField(ip, { ip = it }, "LAN IP", error = errors["ip"], keyboard = KeyboardType.Uri)
                FormField(mask, { mask = it }, "Subnet mask", error = errors["mask"], keyboard = KeyboardType.Uri)
                Caption("This is the address you type in a browser. Changing it moves the management UI.")
            }
        }
        item {
            SectionCard(title = "DHCP server") {
                SettingSwitch(
                    title = "Hand out addresses",
                    checked = dhcp,
                    onCheckedChange = { dhcp = it },
                    subtitle = "Disable only if another device runs DHCP",
                )
                if (dhcp) {
                    FormField(start, { start = it }, "Pool start", error = errors["start"], keyboard = KeyboardType.Uri)
                    FormField(end, { end = it }, "Pool end", error = errors["end"], keyboard = KeyboardType.Uri)
                }
                FormField(dns1, { dns1 = it }, "DNS 1", error = errors["dns1"], keyboard = KeyboardType.Uri,
                    helper = "Leave empty to hand out the router itself")
                FormField(dns2, { dns2 = it }, "DNS 2", error = errors["dns2"], keyboard = KeyboardType.Uri)
            }
        }
        item {
            SectionCard(title = "Safety") {
                Text(
                    "If you change the LAN IP, this app keeps the old address. Update it in More → About & connection, or the router will look offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Button(
                onClick = { confirm = true },
                enabled = dirty && errors.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) { Text("Preview & save LAN") }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (confirm) {
        DiffDialog(
            title = "Review LAN change",
            changes = buildList {
                if (ip != lan.ip) add("Router IP" to (lan.ip to ip))
                if (mask != lan.mask) add("Mask" to (lan.mask to mask))
                if (dhcp != lan.dhcpEnabled) add("DHCP" to (lan.dhcpEnabled.toString() to dhcp.toString()))
                if (start != lan.dhcpStart) add("Pool start" to (lan.dhcpStart to start))
                if (end != lan.dhcpEnd) add("Pool end" to (lan.dhcpEnd to end))
                if (dns1 != lan.dns1) add("DNS 1" to (lan.dns1 to dns1))
            },
            warning = if (ip != lan.ip) {
                "The router moves to $ip. This phone loses the session until you update the connection address."
            } else null,
            onConfirm = {
                confirm = false
                vm.saveLan(LanConfig(ip, mask, dhcp, start, end, dns1, dns2))
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
fun StaticLeaseScreen(ui: RouterUiState, vm: RouterViewModel, onBack: () -> Unit) {
    val nat = ui.nat
    ScreenScaffold(
        title = "DHCP reservations",
        onBack = onBack,
        actions = {
            IconButton(onClick = vm::refreshNat) { Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh") }
        },
    ) { padding ->
        if (nat == null) {
            Column(Modifier.padding(padding).padding(16.dp)) { LoadingBlock("Reading reservations") }
        } else {
            val leases = remember(nat.leases) { mutableStateListOf<StaticLease>().apply { addAll(nat.leases) } }
            var editing by remember { mutableStateOf<StaticLease?>(null) }
            var adding by remember { mutableStateOf(false) }
            var confirmSave by remember { mutableStateOf(false) }
            val dirty = leases != nat.leases

            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${leases.size} of 20 reservations",
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(onClick = { adding = true }) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }
                if (leases.isEmpty()) {
                    item { EmptyState("No reservations", "Add a fixed address for a device that must keep its IP.") }
                }
                itemsIndexed(leases.toList(), key = { _, lease -> lease.mac + lease.ip }) { index, lease ->
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
                                Text(
                                    ui.deviceName(lease.mac, leaseRemark = lease.remark, fallback = lease.mac),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    "${lease.ip} · ${lease.mac}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { editing = lease }) {
                                Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit reservation")
                            }
                            IconButton(onClick = { leases.removeAt(index) }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete reservation")
                            }
                        }
                    }
                }
                item {
                    Button(
                        onClick = { confirmSave = true },
                        enabled = dirty,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) { Text("Save reservations") }
                }
                item {
                    Text(
                        "The firmware replaces the whole list. Reserved addresses must be inside the LAN subnet and outside the DHCP pool for predictable behaviour.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            if (adding) {
                LeaseDialog(
                    existing = leases,
                    onDismiss = { adding = false },
                    onSave = {
                        leases.add(it)
                        adding = false
                    },
                )
            }
            editing?.let { target ->
                LeaseDialog(
                    existing = leases.filter { it.mac != target.mac },
                    initial = target,
                    initialName = ui.deviceName(
                        target.mac,
                        leaseRemark = target.remark,
                        fallback = target.mac,
                    ).takeUnless { it.equals(target.mac, ignoreCase = true) }.orEmpty(),
                    onDismiss = { editing = null },
                    onSave = { updated ->
                        val index = leases.indexOfFirst { it.mac == target.mac }
                        if (index >= 0) leases[index] = updated
                        editing = null
                    },
                )
            }
            if (confirmSave) {
                DiffDialog(
                    title = "Save ${leases.size} reservations?",
                    changes = listOf("Reservations" to (nat.leases.size.toString() to leases.size.toString())),
                    warning = null,
                    onConfirm = {
                        confirmSave = false
                        vm.setStaticLeases(leases.toList())
                    },
                    onDismiss = { confirmSave = false },
                )
            }
        }
    }
}

@Composable
private fun LeaseDialog(
    existing: List<StaticLease>,
    onDismiss: () -> Unit,
    onSave: (StaticLease) -> Unit,
    initial: StaticLease? = null,
    initialName: String = initial?.remark.orEmpty(),
) {
    var ip by remember { mutableStateOf(initial?.ip ?: "") }
    var mac by remember { mutableStateOf(initial?.mac ?: "") }
    var remark by remember(initial, initialName) { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add reservation" else "Edit reservation", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(ip, { ip = it }, "IP address", keyboard = KeyboardType.Uri)
                FormField(mac, { mac = it }, "MAC address", helper = "AA:BB:CC:DD:EE:FF")
                FormField(remark, { remark = it }, "Name (optional)", helper = "Shared with Devices and MAC filter")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    !isIpv4(ip) -> "Invalid IP address"
                    !isMac(mac) -> "Invalid MAC address"
                    existing.any { it.ip == ip } -> "That IP already has a reservation"
                    existing.any { it.mac.equals(mac, true) } -> "That MAC already has a reservation"
                    else -> null
                }
                if (error == null) onSave(StaticLease(ip, mac.lowercase(), remark.trim()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PortForwardScreen(ui: RouterUiState, vm: RouterViewModel, onBack: () -> Unit) {
    val nat = ui.nat
    ScreenScaffold(
        title = "Port forwarding",
        onBack = onBack,
        actions = {
            IconButton(onClick = vm::refreshNat) { Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh") }
        },
    ) { padding ->
        if (nat == null) {
            Column(Modifier.padding(padding).padding(16.dp)) { LoadingBlock("Reading port rules") }
        } else {
            val rules = remember(nat.ports) { mutableStateListOf<PortRule>().apply { addAll(nat.ports) } }
            var editing by remember { mutableStateOf<PortRule?>(null) }
            var adding by remember { mutableStateOf(false) }
            var confirmSave by remember { mutableStateOf(false) }
            val dirty = rules != nat.ports

            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${rules.size} of 16 rules",
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(onClick = { adding = true }) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }
                if (rules.isEmpty()) {
                    item { EmptyState("No port rules", "Forward a WAN port to a LAN host only when a service needs it.") }
                }
                itemsIndexed(rules.toList(), key = { _, rule -> rule.ip + rule.externalPort + rule.protocol }) { index, rule ->
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
                                Text(
                                    "WAN ${rule.externalPort} → ${rule.ip}:${rule.internalPort}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    rule.protocol.uppercase(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { editing = rule }) {
                                Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit rule")
                            }
                            IconButton(onClick = { rules.removeAt(index) }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete rule")
                            }
                        }
                    }
                }
                item {
                    Button(
                        onClick = { confirmSave = true },
                        enabled = dirty,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) { Text("Save rules") }
                }
                item {
                    Text(
                        "Forwarding opens a port to the Internet. Keep the list minimal, and never expose the router's own management port.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            if (adding) {
                PortDialog(
                    onDismiss = { adding = false },
                    onSave = {
                        rules.add(it)
                        adding = false
                    },
                )
            }
            editing?.let { target ->
                PortDialog(
                    initial = target,
                    onDismiss = { editing = null },
                    onSave = { updated ->
                        val index = rules.indexOfFirst { it == target }
                        if (index >= 0) rules[index] = updated
                        editing = null
                    },
                )
            }
            if (confirmSave) {
                DiffDialog(
                    title = "Save ${rules.size} port rules?",
                    changes = listOf("Rules" to (nat.ports.size.toString() to rules.size.toString())),
                    warning = null,
                    onConfirm = {
                        confirmSave = false
                        vm.setPortRules(rules.toList())
                    },
                    onDismiss = { confirmSave = false },
                )
            }
        }
    }
}

@Composable
private fun PortDialog(
    onDismiss: () -> Unit,
    onSave: (PortRule) -> Unit,
    initial: PortRule? = null,
) {
    var ip by remember { mutableStateOf(initial?.ip ?: "") }
    var internalPort by remember { mutableStateOf(initial?.internalPort ?: "") }
    var externalPort by remember { mutableStateOf(initial?.externalPort ?: "") }
    var protocol by remember { mutableStateOf(initial?.protocol ?: "tcp") }
    var error by remember { mutableStateOf<String?>(null) }
    val portPattern = Regex("^\\d{1,5}(-\\d{1,5})?$")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add port rule" else "Edit port rule", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(ip, { ip = it }, "LAN host IP", keyboard = KeyboardType.Uri)
                FormField(internalPort, { internalPort = it }, "Internal port", placeholder = "8080")
                FormField(externalPort, { externalPort = it }, "External port", placeholder = "8080")
                SegmentedField(
                    label = "Protocol",
                    options = listOf(Choice("tcp", "TCP"), Choice("udp", "UDP"), Choice("both", "Both")),
                    selected = protocol,
                    onSelect = { protocol = it },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    !isIpv4(ip) -> "Invalid LAN address"
                    !portPattern.matches(internalPort) || !portPattern.matches(externalPort) -> "Ports must be 1–65535 or a range"
                    else -> null
                }
                if (error == null) onSave(PortRule(ip, internalPort, externalPort, protocol))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
