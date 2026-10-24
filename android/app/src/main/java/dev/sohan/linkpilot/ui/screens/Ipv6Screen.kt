@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.sohan.linkpilot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sohan.linkpilot.data.Ipv6Snapshot
import dev.sohan.linkpilot.ui.components.Caption
import dev.sohan.linkpilot.ui.components.Choice
import dev.sohan.linkpilot.ui.components.ConfirmDialog
import dev.sohan.linkpilot.ui.components.FormField
import dev.sohan.linkpilot.ui.components.InfoRow
import dev.sohan.linkpilot.ui.components.LoadingBlock
import dev.sohan.linkpilot.ui.components.ScreenScaffold
import dev.sohan.linkpilot.ui.components.SecretField
import dev.sohan.linkpilot.ui.components.SectionCard
import dev.sohan.linkpilot.ui.components.SegmentedField
import dev.sohan.linkpilot.ui.components.SettingSwitch
import dev.sohan.linkpilot.vm.RouterUiState
import dev.sohan.linkpilot.vm.RouterViewModel
import org.json.JSONObject

private fun isIpv6(value: String): Boolean =
    value.count { it == ':' } >= 2 && value.all { it.isDigit() || it in "abcdefABCDEF:." }

@Composable
fun Ipv6Screen(ui: RouterUiState, vm: RouterViewModel, onBack: () -> Unit) {
    val ipv6 = ui.ipv6
    ScreenScaffold(
        title = "IPv6",
        onBack = onBack,
        actions = {
            IconButton(onClick = vm::refreshIpv6) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
        },
    ) { padding ->
        if (ipv6 == null) {
            Column(Modifier.padding(padding).padding(16.dp)) { LoadingBlock("Reading IPv6 configuration") }
        } else {
            Ipv6Content(ipv6, vm, padding)
        }
    }
}

@Composable
private fun Ipv6Content(ipv6: Ipv6Snapshot, vm: RouterViewModel, padding: PaddingValues) {
    var enabled by remember(ipv6.enabled) { mutableStateOf(ipv6.enabled) }
    var type by remember(ipv6.wanType) { mutableStateOf(ipv6.wanType.ifBlank { "0" }) }

    var user by remember(ipv6.wan) { mutableStateOf(ipv6.wan.optString("ipv6WanPPPoEUser")) }
    var pwd by remember(ipv6.wan) { mutableStateOf(ipv6.wan.optString("ipv6WanPPPoEPwd")) }
    var staticIp by remember(ipv6.wan) { mutableStateOf(ipv6.wan.optString("ipv6WanIP")) }
    var prefix by remember(ipv6.wan) { mutableStateOf(ipv6.wan.optString("ipv6IpPlen")) }
    var gateway by remember(ipv6.wan) { mutableStateOf(ipv6.wan.optString("ipv6WanGateway")) }
    var dns1 by remember(ipv6.wan) { mutableStateOf(ipv6.wan.optString("ipv6WanDns1")) }
    var dns2 by remember(ipv6.wan) { mutableStateOf(ipv6.wan.optString("ipv6WanDns2")) }
    var delegation by remember(ipv6.wan) {
        mutableStateOf(ipv6.wan.optString(if (ipv6.wanType == "2") "ipv6PPPoeDelegation" else "ipv6DhcpDelegation") == "1")
    }
    var temporary by remember(ipv6.wan) {
        mutableStateOf(ipv6.wan.optString(if (ipv6.wanType == "2") "ipv6PPPoeTemporary" else "ipv6DhcpTemporary") == "1")
    }
    var confirm by remember { mutableStateOf(false) }

    val errors = buildMap {
        if (enabled && type == "2") {
            if (user.isBlank()) put("user", "PPPoE username required")
            if (pwd.isBlank()) put("pwd", "PPPoE password required")
        }
        if (enabled && type == "1") {
            if (!isIpv6(staticIp)) put("ip", "Enter an IPv6 address")
            if (prefix.toIntOrNull() !in 0..128) put("prefix", "Prefix length 0–128")
            if (!isIpv6(gateway)) put("gateway", "Enter an IPv6 gateway")
            if (dns1.isNotBlank() && !isIpv6(dns1)) put("dns1", "Invalid IPv6 DNS")
        }
    }

    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Status") {
                InfoRow("Connection", ipv6.conType.ifBlank { "—" })
                InfoRow("WAN address", ipv6.wanAddr.replace("\n", "  ").ifBlank { "—" })
                InfoRow("LAN prefix", ipv6.lanAddr.replace("\n", "  ").ifBlank { "—" })
                InfoRow("Gateway", ipv6.gateway)
                InfoRow("DNS", listOf(ipv6.preDns, ipv6.altDns).filter { it.isNotBlank() }.joinToString(", "))
            }
        }

        item {
            SectionCard(title = "IPv6") {
                SettingSwitch(
                    title = "Enable IPv6",
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    subtitle = "The stock UI hides WAN/LAN IPv6 modules while this is off",
                )
                if (enabled) {
                    SegmentedField(
                        label = "WAN mode",
                        options = listOf(
                            Choice("0", "DHCPv6"),
                            Choice("1", "Static"),
                            Choice("2", "PPPoE"),
                        ),
                        selected = type,
                        onSelect = { type = it },
                    )
                    when (type) {
                        "2" -> {
                            FormField(user, { user = it }, "PPPoE username", error = errors["user"])
                            SecretField(pwd, { pwd = it }, "PPPoE password", error = errors["pwd"])
                        }
                        "1" -> {
                            FormField(staticIp, { staticIp = it }, "IPv6 address", error = errors["ip"], keyboard = KeyboardType.Uri)
                            FormField(prefix, { prefix = it.filter(Char::isDigit) }, "Prefix length", error = errors["prefix"],
                                keyboard = KeyboardType.Number)
                            FormField(gateway, { gateway = it }, "Gateway", error = errors["gateway"], keyboard = KeyboardType.Uri)
                            FormField(dns1, { dns1 = it }, "DNS 1", error = errors["dns1"], keyboard = KeyboardType.Uri)
                            FormField(dns2, { dns2 = it }, "DNS 2", keyboard = KeyboardType.Uri)
                        }
                    }
                    SettingSwitch(
                        title = "Request a delegated prefix",
                        checked = delegation,
                        onCheckedChange = { delegation = it },
                        subtitle = "Needed by most ISPs for LAN addressing",
                    )
                    SettingSwitch(
                        title = "Temporary addresses",
                        checked = temporary,
                        onCheckedChange = { temporary = it },
                        subtitle = "Privacy extensions for outbound connections",
                    )
                    Caption(
                        "LinkPilot writes delegation and temporary-address switches as 1/0, matching the firmware's boolean encoding elsewhere."
                    )
                }
            }
        }

        item {
            SectionCard(title = "LAN IPv6") {
                Text(
                    "LinkPilot keeps the router's LAN prefix, DHCPv6 range and DNS settings exactly as read. Change those on the web UI if your ISP needs a custom prefix.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Caption("LAN address: ${ipv6.lan.optString("ipv6LanIP").ifBlank { "auto" }} / ${ipv6.lan.optString("ipv6LanIPLen").ifBlank { "—" }}")
            }
        }

        item {
            Button(
                onClick = { confirm = true },
                enabled = errors.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) { Text("Preview & save IPv6") }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Apply IPv6 settings?",
            body = "IPv6 clients renumber and existing connections may drop. The IPv4 side is unaffected.",
            confirmLabel = "Apply",
            onConfirm = {
                confirm = false
                val wan = JSONObject(ipv6.wan.toString())
                val lan = JSONObject(ipv6.lan.toString())
                wan.put("ipv6WanType", type)
                when (type) {
                    "0" -> {
                        wan.put("ipv6DhcpDelegation", if (delegation) "1" else "0")
                        wan.put("ipv6DhcpTemporary", if (temporary) "1" else "0")
                    }
                    "1" -> {
                        wan.put("ipv6WanIP", staticIp)
                        wan.put("ipv6IpPlen", prefix)
                        wan.put("ipv6WanGateway", gateway)
                        wan.put("ipv6WanDns1", dns1)
                        wan.put("ipv6WanDns2", dns2)
                    }
                    "2" -> {
                        wan.put("ipv6WanPPPoEUser", user)
                        wan.put("ipv6WanPPPoEPwd", pwd)
                        wan.put("ipv6PPPoeDelegation", if (delegation) "1" else "0")
                        wan.put("ipv6PPPoeTemporary", if (temporary) "1" else "0")
                    }
                }
                vm.saveIpv6(enabled, wan, lan)
            },
            onDismiss = { confirm = false },
        )
    }
}
