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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sohan.linkpilot.data.WifiSnapshot
import dev.sohan.linkpilot.ui.SECURITY_CHOICES
import dev.sohan.linkpilot.ui.components.Caption
import dev.sohan.linkpilot.ui.components.Choice
import dev.sohan.linkpilot.ui.components.ConfirmDialog
import dev.sohan.linkpilot.ui.components.DayChips
import dev.sohan.linkpilot.ui.components.DiffDialog
import dev.sohan.linkpilot.ui.components.DropdownField
import dev.sohan.linkpilot.ui.components.ErrorBlock
import dev.sohan.linkpilot.ui.components.FormField
import dev.sohan.linkpilot.ui.components.LoadingBlock
import dev.sohan.linkpilot.ui.components.ScreenScaffold
import dev.sohan.linkpilot.ui.components.SecretField
import dev.sohan.linkpilot.ui.components.SectionCard
import dev.sohan.linkpilot.ui.components.SegmentedField
import dev.sohan.linkpilot.ui.components.SettingSwitch
import dev.sohan.linkpilot.ui.daysLabel
import dev.sohan.linkpilot.ui.parseWindow
import dev.sohan.linkpilot.ui.relayModeLabel
import dev.sohan.linkpilot.ui.securityLabel
import dev.sohan.linkpilot.vm.RouterUiState
import dev.sohan.linkpilot.vm.RouterViewModel


@Composable
fun WifiScreen(ui: RouterUiState, vm: RouterViewModel) {
    val wifi = ui.wifi

    ScreenScaffold(
        title = "Wi-Fi",
        actions = {
            IconButton(
                onClick = {
                    vm.refreshWifi()
                    vm.refreshNat()
                },
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Wi-Fi and network settings")
            }
        },
    ) { padding ->
        if (wifi == null) {
            Column(Modifier.padding(padding).padding(16.dp)) {
                if (ui.busy) {
                    LoadingBlock("Reading Wi-Fi configuration")
                } else {
                    ErrorBlock("Could not read the Wi-Fi configuration.", onRetry = vm::refreshWifi)
                }
            }
        } else {
            WifiContent(wifi, ui, vm, padding)
        }
    }
}

@Composable
private fun WifiContent(wifi: WifiSnapshot, ui: RouterUiState, vm: RouterViewModel, padding: PaddingValues) {
    var confirmRadioOff by remember { mutableStateOf(false) }
    var confirmBasic by remember { mutableStateOf(false) }
    var confirmAdvanced by remember { mutableStateOf(false) }
    var confirmSchedule by remember { mutableStateOf(false) }
    var confirmPower by remember { mutableStateOf(false) }
    var confirmGuest by remember { mutableStateOf(false) }
    var confirmWps by remember { mutableStateOf(false) }

    // Basic
    var ssid by remember(wifi.ssid) { mutableStateOf(wifi.ssid) }
    var security by remember(wifi.security) { mutableStateOf(wifi.security.ifBlank { "wpa2-psk" }) }
    var password by remember(wifi.password) { mutableStateOf(wifi.password) }
    var hidden by remember(wifi.hidden) { mutableStateOf(wifi.hidden) }

    // Radio
    var mode by remember(wifi.mode) { mutableStateOf(wifi.mode) }
    var channel by remember(wifi.channel) { mutableStateOf(wifi.channel) }
    var bandwidth by remember(wifi.bandwidth) { mutableStateOf(wifi.bandwidth) }

    // Power
    var power by remember(wifi.power) { mutableStateOf(wifi.power) }

    // Schedule
    var scheduleOn by remember(wifi.scheduleEnabled) { mutableStateOf(wifi.scheduleEnabled) }
    var window by remember(wifi.scheduleWindow) { mutableStateOf(wifi.scheduleWindow.ifBlank { "23:00-07:00" }) }
    var days by remember(wifi.scheduleDays) { mutableStateOf(wifi.scheduleDays.ifBlank { "01111100" }) }

    // Guest
    var guestOn by remember(wifi.guestEnabled) { mutableStateOf(wifi.guestEnabled) }
    var guestSsid by remember(wifi.guestSsid) { mutableStateOf(wifi.guestSsid) }
    var guestPassword by remember(wifi.guestPassword) { mutableStateOf(wifi.guestPassword) }

    val basicDirty = ssid != wifi.ssid || security != wifi.security || password != wifi.password || hidden != wifi.hidden
    val radioDirty = mode != wifi.mode || channel != wifi.channel || bandwidth != wifi.bandwidth
    val scheduleDirty = scheduleOn != wifi.scheduleEnabled || window != wifi.scheduleWindow || days != wifi.scheduleDays
    val guestDirty = guestOn != wifi.guestEnabled || guestSsid != wifi.guestSsid || guestPassword != wifi.guestPassword

    val ssidError = when {
        ssid.isBlank() -> "SSID cannot be empty"
        ssid.length > 32 -> "Keep the SSID to 32 characters or fewer"
        else -> null
    }
    val passwordError = when {
        security == "none" -> null
        password.isEmpty() -> "Enter a password"
        password.length < 8 -> "WPA keys are at least 8 characters"
        password.length > 63 -> "WPA keys are at most 63 characters"
        else -> null
    }
    val windowError = if (parseWindow(window) == null) "Use HH:MM-HH:MM, e.g. 23:00-07:00" else null

    val guestPwdError = if (guestPassword.isNotBlank() && guestPassword.length !in 8..63) {
        "Guest keys are 8–63 characters"
    } else {
        null
    }
    val guestSsidError = when {
        guestSsid.isBlank() -> "Guest SSID cannot be empty"
        guestSsid.length > 32 -> "Keep the guest SSID to 32 characters or fewer"
        else -> null
    }

    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Wireless network", subtitle = "Radio, name, security and visibility") {
                SettingSwitch(
                    title = "Wi-Fi radio",
                    checked = wifi.enabled,
                    onCheckedChange = { enabled ->
                        if (enabled) vm.setWifiEnabled(true) else confirmRadioOff = true
                    },
                    subtitle = if (wifi.enabled) "Broadcasting" else "Off — clients cannot connect",
                )
                FormField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = "Network name (SSID)",
                    error = ssidError.takeIf { basicDirty },
                    enabled = wifi.enabled,
                )
                SegmentedField(
                    label = "Security",
                    options = SECURITY_CHOICES,
                    selected = security,
                    onSelect = {
                        security = it
                        if (it == "none") password = ""
                    },
                    enabled = wifi.enabled,
                )
                if (security != "none") {
                    SecretField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Wi-Fi password",
                        helper = "8–63 characters",
                        error = passwordError.takeIf { basicDirty },
                        enabled = wifi.enabled,
                    )
                } else {
                    Caption("Open network: anyone nearby can join and read unencrypted traffic.")
                }
                SettingSwitch(
                    title = "Hide network name",
                    subtitle = "Clients must type the SSID manually",
                    checked = hidden,
                    onCheckedChange = { hidden = it },
                    enabled = wifi.enabled,
                )
                Button(
                    onClick = { confirmBasic = true },
                    enabled = basicDirty && ssidError == null && passwordError == null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Preview & save") }
            }
        }

        item {
            SectionCard(title = "Radio", subtitle = "Mode, channel and width") {
                SegmentedField(
                    label = "802.11 mode",
                    options = listOf(
                        Choice("bgn", "b/g/n"),
                        Choice("bg", "b/g"),
                        Choice("g", "g"),
                        Choice("b", "b"),
                    ),
                    selected = mode.ifBlank { "bgn" },
                    onSelect = { mode = it },
                )
                DropdownField(
                    label = "Channel",
                    options = listOf(Choice("auto", "Auto")) +
                        (1..wifi.maxChannel.coerceIn(1, 13)).map { Choice("$it", "Channel $it") },
                    selected = channel.ifBlank { "auto" },
                    onSelect = { channel = it },
                )
                SegmentedField(
                    label = "Channel width",
                    options = listOf(Choice("auto", "Auto"), Choice("20", "20 MHz"), Choice("40", "40 MHz")),
                    selected = bandwidth.ifBlank { "auto" },
                    onSelect = { bandwidth = it },
                )
                Caption("Current: channel ${wifi.channelCurrent.ifBlank { "—" }} · ${wifi.bandwidthCurrent.ifBlank { "—" }} MHz")
                Button(
                    onClick = { confirmAdvanced = true },
                    enabled = radioDirty,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Preview & save radio") }
            }
        }

        item {
            SectionCard(title = "Transmit power") {
                SegmentedField(
                    label = "Power",
                    options = listOf(
                        Choice("low", "Low"),
                        Choice("normal", "Normal"),
                        Choice("high", "High"),
                    ),
                    selected = power.ifBlank { "high" },
                    onSelect = { power = it },
                )
                if (power != wifi.power) {
                    Button(
                        onClick = { confirmPower = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) { Text("Apply $power") }
                }
            }
        }

        item {
            SectionCard(title = "Schedule", subtitle = "Turn the radio off at night") {
                SettingSwitch(
                    title = "Use a schedule",
                    checked = scheduleOn,
                    onCheckedChange = { scheduleOn = it },
                    subtitle = "The router disables Wi-Fi during those hours",
                )
                if (scheduleOn) {
                    DayChips(days, onChange = { days = it })
                    Caption(daysLabel(days))
                    FormField(
                        value = window,
                        onValueChange = { window = it },
                        label = "Off hours",
                        placeholder = "23:00-07:00",
                        helper = "24-hour clock, start–end",
                        error = windowError,
                    )
                }
                Button(
                    onClick = { confirmSchedule = true },
                    enabled = scheduleDirty && (windowError == null || !scheduleOn),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Preview & save schedule") }
            }
        }

        item {
            SectionCard(title = "WPS") {
                SettingSwitch(
                    title = "WPS enabled",
                    checked = wifi.wpsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) confirmWps = true else vm.setWpsEnabled(false)
                    },
                    subtitle = if (wifi.wpsEnabled) "PIN: ${wifi.wpsPin.ifBlank { "—" }}" else "Off",
                )
                OutlinedButton(
                    onClick = vm::startWpsPbc,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Start push-button pairing") }
                Caption("WPS stays a compatibility feature; WPA2-PSK with a long password is safer.")
            }
        }

        item {
            SectionCard(title = "Guest network") {
                SettingSwitch(
                    title = "Guest Wi-Fi",
                    checked = guestOn,
                    onCheckedChange = { guestOn = it },
                    subtitle = "A second SSID isolated from the LAN by the firmware",
                )
                if (guestOn) {
                    FormField(guestSsid, { guestSsid = it }, "Guest SSID", error = guestSsidError)
                    SecretField(
                        guestPassword,
                        { guestPassword = it },
                        "Guest password",
                        helper = "8–63 characters",
                        error = guestPwdError,
                    )
                    if (guestPassword.isBlank()) {
                        Caption("Blank key = open guest network. Anyone nearby can join.")
                    }
                }
                Button(
                    onClick = { confirmGuest = true },
                    enabled = guestDirty && (!guestOn || (guestSsidError == null && guestPwdError == null)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) { Text("Preview & save guest network") }
            }
        }

        item {
            val nat = ui.nat
            SectionCard(title = "Client isolation") {
                SettingSwitch(
                    title = "AP isolation",
                    checked = nat?.apIsolation == true,
                    onCheckedChange = { vm.setApIsolation(it) },
                    subtitle = if (nat == null) {
                        "Not loaded yet — refresh to read the current value"
                    } else {
                        "Stops Wi-Fi clients from reaching each other"
                    },
                    enabled = nat != null,
                )
            }
        }

        item {
            SectionCard(title = "Client impact") {
                Text(
                    "Changing the SSID, password, security mode or channel restarts the radio. Every client drops for a few seconds and may ask for the new password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (wifi.relayType.isNotBlank() && wifi.relayType != "disabled") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.WifiTethering,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Wireless repeating is active (${relayModeLabel(wifi.relayType)}); " +
                                "Wi-Fi changes also affect the uplink.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    if (confirmRadioOff) {
        ConfirmDialog(
            title = "Turn off Wi-Fi?",
            body = "Every wireless client disconnects. Devices on Ethernet keep working, and you can turn the radio back on over this app.",
            confirmLabel = "Turn off",
            destructive = true,
            onConfirm = {
                confirmRadioOff = false
                vm.setWifiEnabled(false)
            },
            onDismiss = { confirmRadioOff = false },
        )
    }

    if (confirmWps) {
        ConfirmDialog(
            title = "Enable WPS?",
            body = "WPS accepts an 8-digit PIN and is easier to brute-force than WPA2. Enable it only while pairing a device, then turn it off again.",
            confirmLabel = "Enable",
            onConfirm = {
                confirmWps = false
                vm.setWpsEnabled(true)
            },
            onDismiss = { confirmWps = false },
        )
    }

    if (confirmBasic) {
        DiffDialog(
            title = "Review Wi-Fi changes",
            changes = buildList {
                if (ssid != wifi.ssid) add("SSID" to (wifi.ssid to ssid))
                if (security != wifi.security) {
                    add("Security" to (securityLabel(wifi.security) to securityLabel(security)))
                }
                if (password != wifi.password) {
                    val before = if (wifi.password.isBlank()) "(none)" else "••••••••"
                    val after = if (security == "none") "(none)" else "••••••••"
                    add("Password" to (before to after))
                }
                if (hidden != wifi.hidden) add("Hidden SSID" to (wifi.hidden.toString() to hidden.toString()))
            },
            warning = "All Wi-Fi clients disconnect. If you change the password, update this phone's Wi-Fi before reconnecting.",
            confirmLabel = "Apply",
            onConfirm = {
                confirmBasic = false
                vm.saveWifiBasic(ssid, security, if (security == "none") "" else password, hidden)
            },
            onDismiss = { confirmBasic = false },
        )
    }

    if (confirmAdvanced) {
        DiffDialog(
            title = "Review radio changes",
            changes = buildList {
                if (mode != wifi.mode) add("Mode" to (wifi.mode to mode))
                if (channel != wifi.channel) add("Channel" to (wifi.channel to channel))
                if (bandwidth != wifi.bandwidth) add("Width" to (wifi.bandwidth to bandwidth))
            },
            warning = "The radio restarts and clients reconnect. A fixed channel may be slower than Auto if neighbours overlap.",
            onConfirm = {
                confirmAdvanced = false
                vm.saveWifiAdvanced(mode, channel, bandwidth)
            },
            onDismiss = { confirmAdvanced = false },
        )
    }

    if (confirmSchedule) {
        DiffDialog(
            title = "Review schedule",
            changes = listOf(
                "Schedule" to (
                    (if (wifi.scheduleEnabled) wifi.scheduleWindow else "off") to
                        (if (scheduleOn) "$window · ${daysLabel(days)}" else "off")
                    ),
            ),
            warning = if (scheduleOn) "Wi-Fi switches off automatically at ${window.substringBefore('-')}." else null,
            onConfirm = {
                confirmSchedule = false
                vm.saveWifiSchedule(scheduleOn, window, days)
            },
            onDismiss = { confirmSchedule = false },
        )
    }

    if (confirmPower) {
        ConfirmDialog(
            title = "Change transmit power?",
            body = "Lower power reduces coverage and may disconnect distant clients.",
            confirmLabel = "Apply",
            onConfirm = {
                confirmPower = false
                vm.setWifiPower(power)
            },
            onDismiss = { confirmPower = false },
        )
    }

    if (confirmGuest) {
        DiffDialog(
            title = "Review guest network",
            changes = buildList {
                add("Guest Wi-Fi" to (wifi.guestEnabled.toString() to guestOn.toString()))
                add("SSID" to (wifi.guestSsid to guestSsid))
                if (guestPassword != wifi.guestPassword) {
                    val before = if (wifi.guestPassword.isBlank()) "(open)" else "••••••••"
                    val after = if (guestPassword.isBlank()) "(open)" else "••••••••"
                    add("Password" to (before to after))
                }
            },
            warning = if (guestOn && guestPassword.isBlank()) {
                "The guest network will be open — anyone nearby can join."
            } else {
                null
            },
            onConfirm = {
                confirmGuest = false
                vm.saveGuest(guestOn, guestSsid, guestPassword)
            },
            onDismiss = { confirmGuest = false },
        )
    }
}
