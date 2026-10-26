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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sohan.linkpilot.R
import dev.sohan.linkpilot.data.ROUTER_TIMEZONES
import dev.sohan.linkpilot.data.SysToolsSnapshot
import dev.sohan.linkpilot.ui.components.Caption
import dev.sohan.linkpilot.ui.components.Choice
import dev.sohan.linkpilot.ui.components.ConfirmDialog
import dev.sohan.linkpilot.ui.components.DiffDialog
import dev.sohan.linkpilot.ui.components.DropdownField
import dev.sohan.linkpilot.ui.components.FormField
import dev.sohan.linkpilot.ui.components.InfoRow
import dev.sohan.linkpilot.ui.components.LoadingBlock
import dev.sohan.linkpilot.ui.components.RebootDialog
import dev.sohan.linkpilot.ui.components.ScreenScaffold
import dev.sohan.linkpilot.ui.components.SecretField
import dev.sohan.linkpilot.ui.components.SectionCard
import dev.sohan.linkpilot.ui.components.SegmentedField
import dev.sohan.linkpilot.ui.components.SettingSwitch
import dev.sohan.linkpilot.ui.isIpv4
import dev.sohan.linkpilot.vm.RouterUiState
import dev.sohan.linkpilot.vm.RouterViewModel

@Composable
fun AdminScreen(
    ui: RouterUiState,
    vm: RouterViewModel,
    onBack: () -> Unit,
    onOpenAudit: () -> Unit,
) {
    val sys = ui.sys
    ScreenScaffold(
        title = "Administration",
        onBack = onBack,
        actions = {
            androidx.compose.material3.IconButton(onClick = onOpenAudit) {
                Icon(painterResource(R.drawable.ic_history), contentDescription = "Audit log")
            }
        },
    ) { padding ->
        if (sys == null) {
            Column(Modifier.padding(padding).padding(16.dp)) { LoadingBlock("Reading administration settings") }
        } else {
            AdminContent(sys, ui, vm, padding)
        }
    }
}

@Composable
private fun AdminContent(sys: SysToolsSnapshot, ui: RouterUiState, vm: RouterViewModel, padding: PaddingValues) {
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var pwdError by remember { mutableStateOf<String?>(null) }
    var confirmPassword by remember { mutableStateOf(false) }

    var tz by remember(sys.timezoneIndex) { mutableStateOf(sys.timezoneIndex.toString()) }

    var remoteOn by remember(sys.remoteWebEnabled) { mutableStateOf(sys.remoteWebEnabled) }
    var remoteType by remember(sys.remoteWebType) { mutableStateOf(sys.remoteWebType.ifBlank { "any" }) }
    var remoteIp by remember(sys.remoteWebIp) { mutableStateOf(sys.remoteWebIp) }
    var remotePort by remember(sys.remoteWebPort) { mutableStateOf(sys.remoteWebPort.ifBlank { "80" }) }
    var confirmRemote by remember { mutableStateOf(false) }

    var confirmReboot by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val remoteError = when {
        remoteType == "specified" && !isIpv4(remoteIp) -> "Enter a valid IPv4 address"
        remotePort.toIntOrNull() !in 1..65535 -> "Port must be 1–65535"
        remoteOn && !sys.hasPassword -> "Set an admin password before enabling remote access"
        else -> null
    }

    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "This router") {
                InfoRow("Firmware", sys.firmware)
                InfoRow("Admin password", if (sys.hasPassword) "Set" else "Not set — anyone on the LAN can sign in")
                InfoRow("Time zone", ROUTER_TIMEZONES.getOrElse(sys.timezoneIndex) { "Index ${sys.timezoneIndex}" })
                InfoRow("Router time", sys.currentTime)
            }
        }

        item {
            SectionCard(
                title = "Admin password",
                subtitle = "Base64 over the LAN; the firmware stores it as-is",
            ) {
                if (sys.hasPassword) {
                    SecretField(oldPwd, { oldPwd = it; pwdError = null }, "Current password")
                }
                SecretField(newPwd, { newPwd = it; pwdError = null }, "New password", helper = "5–32 characters")
                SecretField(confirmPwd, { confirmPwd = it; pwdError = null }, "Repeat new password")
                pwdError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = {
                        pwdError = when {
                            newPwd.length !in 5..32 -> "Password must be 5–32 characters"
                            newPwd != confirmPwd -> "Passwords do not match"
                            sys.hasPassword && oldPwd.isEmpty() -> "Enter the current password"
                            sys.hasPassword && oldPwd == newPwd -> "Choose a different password"
                            else -> null
                        }
                        if (pwdError == null) confirmPassword = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Change password") }
            }
        }

        item {
            SectionCard(title = "Time zone", subtitle = "Used by schedules and the system log") {
                DropdownField(
                    label = "Zone",
                    options = ROUTER_TIMEZONES.mapIndexed { index, name -> Choice(index.toString(), "$index · $name") },
                    selected = tz,
                    onSelect = { tz = it },
                )
                if (tz.toIntOrNull() != sys.timezoneIndex) {
                    Button(
                        onClick = { tz.toIntOrNull()?.let(vm::setTimezone) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) { Text("Apply time zone") }
                }
                Caption("The router has no NTP server field in this API.")
            }
        }

        item {
            SectionCard(title = "Remote web access") {
                SettingSwitch(
                    title = "Allow management from the WAN",
                    checked = remoteOn,
                    onCheckedChange = { remoteOn = it },
                    subtitle = "Exposes the admin UI to the Internet",
                )
                if (remoteOn) {
                    SegmentedField(
                        label = "Allowed from",
                        options = listOf(Choice("any", "Anywhere"), Choice("specified", "One address")),
                        selected = remoteType,
                        onSelect = { remoteType = it },
                    )
                    if (remoteType == "specified") {
                        FormField(remoteIp, { remoteIp = it }, "Allowed IPv4 address")
                    }
                    FormField(
                        remotePort, { remotePort = it.filter(Char::isDigit) },
                        "WAN port", helper = "1–65535",
                    )
                }
                if (remoteError != null) {
                    Text(remoteError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { confirmRemote = true },
                    enabled = remoteError == null && (
                        remoteOn != sys.remoteWebEnabled || remoteType != sys.remoteWebType ||
                            remoteIp != sys.remoteWebIp || remotePort != sys.remoteWebPort
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Apply remote access") }
            }
        }

        item {
            SectionCard(title = "Maintenance") {
                SettingSwitch(
                    title = "Nightly auto-reboot",
                    checked = sys.autoMaintenance,
                    onCheckedChange = vm::setAutoMaintenance,
                    subtitle = "The router restarts itself once a day",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = vm::downloadLog, modifier = Modifier.weight(1f)) {
                        Icon(painterResource(R.drawable.ic_cloud_download), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("System log")
                    }
                    FilledTonalButton(onClick = vm::downloadBackup, modifier = Modifier.weight(1f)) {
                        Icon(painterResource(R.drawable.ic_save), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Backup")
                    }
                }
                Caption("Downloads are shared through Android's share sheet; nothing is uploaded.")
            }
        }

        item {
            SectionCard(title = "Firmware") {
                InfoRow("Version", sys.firmware)
                Text(
                    "Firmware upload is not offered here. A failed flash from a phone can leave the router unbootable, and the stock web UI already implements it. Config restore is likewise web-UI only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "Restart") {
                OutlinedButton(
                    onClick = { confirmReboot = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(painterResource(R.drawable.ic_power_settings_new), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reboot router")
                }
                Button(
                    onClick = { confirmReset = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(painterResource(R.drawable.ic_restart_alt), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Factory reset")
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    if (confirmPassword) {
        ConfirmDialog(
            title = "Change admin password?",
            body = "The new password is saved on this phone immediately. Other sessions keep working until the router reboots.",
            confirmLabel = "Change",
            onConfirm = {
                confirmPassword = false
                vm.changePassword(oldPwd, newPwd)
                oldPwd = ""
                newPwd = ""
                confirmPwd = ""
            },
            onDismiss = { confirmPassword = false },
        )
    }

    if (confirmRemote) {
        DiffDialog(
            title = "Apply remote access?",
            changes = buildList {
                if (remoteOn != sys.remoteWebEnabled) add("Enabled" to (sys.remoteWebEnabled.toString() to remoteOn.toString()))
                if (remoteType != sys.remoteWebType) add("Scope" to (sys.remoteWebType to remoteType))
                if (remotePort != sys.remoteWebPort) add("Port" to (sys.remoteWebPort to remotePort))
            },
            warning = if (remoteOn) "The admin UI becomes reachable from the Internet on port $remotePort. Weak passwords are scanned constantly." else null,
            onConfirm = {
                confirmRemote = false
                vm.saveRemoteWeb(remoteOn, remoteType, remoteIp, remotePort)
            },
            onDismiss = { confirmRemote = false },
        )
    }

    if (confirmReboot) {
        RebootDialog(
            onConfirm = {
                confirmReboot = false
                vm.reboot()
            },
            onDismiss = { confirmReboot = false },
        )
    }

    if (confirmReset) {
        FactoryResetDialog(
            onDismiss = { confirmReset = false },
            onConfirm = {
                confirmReset = false
                vm.factoryReset()
            },
        )
    }
}

@Composable
private fun FactoryResetDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Factory reset", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This erases the admin password, Wi-Fi settings, port rules and every reservation. The router returns to 192.168.0.1 with no password.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Type RESET to confirm.", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Confirmation") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = text.trim() == "RESET",
            ) {
                Text("Erase router", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
