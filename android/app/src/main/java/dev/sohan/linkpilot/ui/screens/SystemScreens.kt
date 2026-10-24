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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sohan.linkpilot.data.AuditEntry
import dev.sohan.linkpilot.data.Capabilities
import dev.sohan.linkpilot.data.Operation
import dev.sohan.linkpilot.data.Risk
import dev.sohan.linkpilot.data.normalizeBase
import dev.sohan.linkpilot.ui.components.Bullet
import dev.sohan.linkpilot.ui.components.ConfirmDialog
import dev.sohan.linkpilot.ui.components.EmptyState
import dev.sohan.linkpilot.ui.components.InfoRow
import dev.sohan.linkpilot.ui.components.RiskChip
import dev.sohan.linkpilot.ui.components.ScreenScaffold
import dev.sohan.linkpilot.ui.components.SectionCard
import dev.sohan.linkpilot.ui.components.StatusPill
import dev.sohan.linkpilot.ui.components.TextFieldDialog
import dev.sohan.linkpilot.ui.components.auditBadge
import dev.sohan.linkpilot.ui.formatTime
import dev.sohan.linkpilot.vm.RouterUiState
import dev.sohan.linkpilot.vm.RouterViewModel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private fun category(op: Operation): String = when {
    op.id.startsWith("auth") -> "Session"
    op.id.startsWith("home") || op.id.startsWith("status") -> "Dashboard"
    op.id.startsWith("wan") -> "WAN"
    op.id.startsWith("wifi") -> "Wi-Fi"
    op.id.startsWith("clients") -> "Clients"
    op.id.startsWith("relay") -> "Repeating"
    op.id.startsWith("parental") -> "Parental controls"
    op.id.startsWith("ipv6") -> "IPv6"
    op.id.startsWith("sys") -> "Administration"
    op.id.startsWith("nat") -> "NAT & firewall"
    op.id.startsWith("logs") || op.id.startsWith("backup") || op.id.startsWith("firmware") ->
        "Files & firmware"
    else -> "Other"
}

@Composable
fun CapabilityScreen(onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(query) {
        if (query.isBlank()) Capabilities.all
        else Capabilities.all.filter { op ->
            val needle = query.trim().lowercase()
            op.title.lowercase().contains(needle) ||
                op.id.contains(needle) ||
                op.path.lowercase().contains(needle) ||
                op.modules.any { it.lowercase().contains(needle) }
        }
    }

    ScreenScaffold(title = "Capability matrix", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard(title = "What this screen is") {
                    Text(
                        "Every router operation LinkPilot knows, with its risk and verification state. Nothing is hidden: rows marked unverified are either not implemented in the app or were never exercised on the test router.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill("${filtered.size} operations", MaterialTheme.colorScheme.primary)
                        StatusPill(
                            "${filtered.count { it.verified }} verified",
                            MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Text(
                        "Evidence: stock firmware ${Capabilities.FIRMWARE}; see docs/TENDA_WEB_API.md.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search operations") },
                    placeholder = { Text("path, module, title") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
            }

            val grouped = filtered.groupBy(::category)
            grouped.forEach { (label, operations) ->
                item(key = "header-$label") {
                    Text(label, style = MaterialTheme.typography.titleMediumEmphasized)
                }
                items(operations, key = { it.id }) { op -> OperationCard(op) }
            }

            if (filtered.isEmpty()) {
                item { EmptyState("No matching operation", "Try a module name such as qosList.") }
            }

            item {
                SectionCard(title = "Deliberately missing") {
                    Text(
                        "Firmware upload and configuration restore stay in the web UI. Both write boot-critical images over a connection that can drop, and a failed flash bricks the router.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "There is also no cloud account, no remote relay and no traffic history: the firmware stores none of it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun OperationCard(op: Operation) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    op.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                RiskChip(op.risk)
            }
            SelectionContainer {
                Text(
                    "${op.method} ${op.path}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    if (op.verified) "Verified" else "Unverified",
                    if (op.verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    op.id,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (op.modules.isNotEmpty()) {
                Text(
                    op.modules.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            op.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AboutScreen(
    ui: RouterUiState,
    vm: RouterViewModel,
    onBack: () -> Unit,
    onOpenCapability: () -> Unit,
) {
    var editAddress by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    ScreenScaffold(title = "About & connection", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = MaterialTheme.shapes.largeIncreased,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Icon(
                                Icons.Rounded.Router,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(32.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("LinkPilot", style = MaterialTheme.typography.headlineSmallEmphasized)
                            Text(
                                "Version $version · built for ${Capabilities.FIRMWARE}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "A local-first Android client for the Tenda router web API. Every screen mirrors an endpoint the firmware actually exposes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionCard(title = "Connection") {
                    InfoRow("Router address", ui.baseUrl, copyable = true)
                    InfoRow("Password saved", if (ui.hasSavedPassword) "Yes, in the Android Keystore" else "No")
                    InfoRow("Session", if (ui.ready) "Signed in" else "Signed out")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { editAddress = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Address")
                        }
                        FilledTonalButton(onClick = { confirmLogout = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sign out")
                        }
                    }
                    if (ui.hasSavedPassword) {
                        OutlinedButton(
                            onClick = { confirmForget = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Forget saved password")
                        }
                    }
                }
            }

            item {
                Surface(
                    onClick = onOpenCapability,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Capability matrix", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${Capabilities.all.size} endpoints, risk and verification",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                SectionCard(title = "Privacy & security") {
                    Bullet("Credentials never leave the phone and the router. There is no cloud service, telemetry or crash upload.")
                    Bullet("The saved admin password is encrypted with an AES/GCM key held by the Android Keystore.")
                    Bullet("Traffic between the app and the router is HTTP on the LAN — the firmware has no TLS listener. Treat the LAN as trusted.")
                    Bullet("Wi-Fi keys, PPPoE and DDNS passwords are returned in plaintext by the firmware. The app shows them only when you reveal or share them.")
                    Bullet("The audit log stores operation names and results, never secret values.")
                }
            }

            item {
                SectionCard(title = "Known limits") {
                    Bullet("Firmware upgrade and configuration restore are web-UI only.")
                    Bullet("IPv6 LAN prefix settings are preserved verbatim, not edited in-app.")
                    Bullet("No historical traffic: the firmware keeps only live counters.")
                    Bullet("Changing the router's LAN address requires updating the address here.")
                }
            }

            item {
                SectionCard(title = "Legal") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Not affiliated with Tenda. LinkPilot talks to your own hardware on your own network.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Material 3 Expressive components and motion tokens; the teal/amber palette and custom spacing are LinkPilot design decisions.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "API contract and field tables: docs/TENDA_WEB_API.md in the source tree.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (editAddress) {
        TextFieldDialog(
            title = "Router address",
            label = "Base URL or IP",
            initialValue = ui.baseUrl,
            helper = "Changing it signs you out. Example: 192.168.0.1",
            validate = { value ->
                if (value.isBlank()) {
                    "Enter an address"
                } else {
                    val normalized = normalizeBase(value)
                    if (normalized.toHttpUrlOrNull()?.host.isNullOrBlank()) "Enter a valid address" else null
                }
            },
            onConfirm = { value ->
                editAddress = false
                vm.changeBaseUrl(value)
            },
            onDismiss = { editAddress = false },
        )
    }

    if (confirmForget) {
        ConfirmDialog(
            title = "Forget saved password?",
            body = "The encrypted copy is deleted from this phone. You must type the admin password next time.",
            confirmLabel = "Forget",
            destructive = true,
            onConfirm = {
                confirmForget = false
                vm.forgetSavedPassword()
            },
            onDismiss = { confirmForget = false },
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Sign out?",
            body = "The session cookie is discarded. The router keeps running with its current settings.",
            confirmLabel = "Sign out",
            onConfirm = {
                confirmLogout = false
                vm.logout()
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
fun AuditScreen(ui: RouterUiState, vm: RouterViewModel, onBack: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    val entries = remember(ui.audit) { ui.audit.sortedByDescending { it.at } }

    ScreenScaffold(
        title = "Audit log",
        onBack = onBack,
        actions = {
            if (entries.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear audit log")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard(title = "Local record") {
                    Text(
                        "Every write LinkPilot sent, its result, and when. The log lives only on this phone; clearing it does not touch the router.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${entries.size} entries",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (entries.isEmpty()) {
                item { EmptyState("Nothing yet", "Changes you apply will be listed here.", Icons.Rounded.History) }
            }

            items(entries, key = { it.id }) { entry -> AuditCard(entry) }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear the audit log?",
            body = "This removes the local history of changes. Router settings are untouched.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                confirmClear = false
                vm.clearAudit()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun AuditCard(entry: AuditEntry) {
    val badge = auditBadge(entry)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                badge.icon,
                contentDescription = null,
                tint = badge.color,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatTime(entry.at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(badge.label, badge.color)
        }
    }
}
