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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sohan.linkpilot.data.ParentalDevice
import dev.sohan.linkpilot.ui.components.Caption
import dev.sohan.linkpilot.ui.components.Choice
import dev.sohan.linkpilot.ui.components.DayChips
import dev.sohan.linkpilot.ui.components.DeviceAvatar
import dev.sohan.linkpilot.ui.components.DiffDialog
import dev.sohan.linkpilot.ui.components.EmptyState
import dev.sohan.linkpilot.ui.components.FormField
import dev.sohan.linkpilot.ui.components.LoadingBlock
import dev.sohan.linkpilot.ui.components.ScreenScaffold
import dev.sohan.linkpilot.ui.components.SectionCard
import dev.sohan.linkpilot.ui.components.SegmentedField
import dev.sohan.linkpilot.ui.daysLabel
import dev.sohan.linkpilot.ui.parseWindow
import dev.sohan.linkpilot.vm.RouterUiState
import dev.sohan.linkpilot.vm.RouterViewModel

@Composable
fun ParentalScreen(ui: RouterUiState, vm: RouterViewModel, onBack: () -> Unit) {
    val parental = ui.parental
    ScreenScaffold(
        title = "Parental controls",
        onBack = onBack,
        actions = {
            IconButton(onClick = vm::refreshParental) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
        },
    ) { padding ->
        if (parental == null) {
            Column(Modifier.padding(padding).padding(16.dp)) { LoadingBlock("Reading parental rules") }
        } else {
            ParentalContent(ui, vm, padding)
        }
    }
}

@Composable
private fun ParentalContent(ui: RouterUiState, vm: RouterViewModel, padding: PaddingValues) {
    val parental = ui.parental ?: return
    val devices = remember(parental.devices) {
        mutableStateListOf<ParentalDevice>().apply { addAll(parental.devices) }
    }
    var window by remember(parental.window) { mutableStateOf(parental.window.ifBlank { "07:00-21:00" }) }
    var days by remember(parental.days) { mutableStateOf(parental.days.ifBlank { "01111100" }) }
    var urlMode by remember(parental.urlMode) { mutableStateOf(parental.urlMode.ifBlank { "disable" }) }
    var urlsText by remember(parental.urls) { mutableStateOf(parental.urls.joinToString("\n")) }
    var confirm by remember { mutableStateOf(false) }

    val urls = urlsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val enabledCount = devices.count { it.enabled }
    val windowError = if (parseWindow(window) == null) "Use HH:MM-HH:MM" else null
    val urlError = if (urls.size > 32) "At most 32 keywords" else null
    val devicesError = if (enabledCount > 10) "At most 10 governed devices" else null
    val changed = devices.toList() != parental.devices || window != parental.window || days != parental.days ||
        urlMode != parental.urlMode || urls != parental.urls

    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Devices", subtitle = "$enabledCount of 10 slots used") {
                if (devices.isEmpty()) {
                    EmptyState("No devices", "The router reports no governed clients.")
                }
                devices.forEachIndexed { index, device ->
                    val name = ui.deviceName(
                        device.mac,
                        hostname = device.remark.ifBlank { device.hostname },
                        fallback = device.ip,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DeviceAvatar(name, wifi = true)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${device.ip} · ${device.mac}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = device.enabled,
                            onCheckedChange = { value ->
                                devices[index] = device.copy(enabled = value)
                            },
                        )
                    }
                }
                devicesError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        item {
            SectionCard(title = "Allowed time") {
                DayChips(days, onChange = { days = it })
                Caption(daysLabel(days))
                FormField(
                    value = window,
                    onValueChange = { window = it },
                    label = "Window",
                    placeholder = "07:00-21:00",
                    helper = "Internet is allowed only inside this window",
                    error = windowError,
                )
            }
        }

        item {
            SectionCard(title = "Website rules") {
                SegmentedField(
                    label = "URL filter",
                    options = listOf(
                        Choice("disable", "Off"),
                        Choice("permit", "Allow only"),
                        Choice("forbid", "Block"),
                    ),
                    selected = urlMode,
                    onSelect = { urlMode = it },
                )
                if (urlMode != "disable") {
                    FormField(
                        value = urlsText,
                        onValueChange = { urlsText = it },
                        label = "Keywords or domains",
                        helper = "One per line, up to 32. Example: youtube.com",
                        singleLine = false,
                        maxLines = 6,
                        error = urlError,
                    )
                }
            }
        }

        item {
            Button(
                onClick = { confirm = true },
                enabled = changed && windowError == null && urlError == null && devicesError == null,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) { Text("Preview & save rules") }
        }

        item {
            Text(
                "Parental controls are a full-list replacement on this firmware. Devices must be currently known to the router; otherwise enable them again after they reconnect.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (confirm) {
        DiffDialog(
            title = "Save parental rules?",
            changes = listOf(
                "Governed devices" to (parental.devices.count { it.enabled }.toString() to enabledCount.toString()),
                "Window" to (parental.window to "$window · ${daysLabel(days)}"),
                "URL filter" to (parental.urlMode to urlMode),
                "Keywords" to (parental.urls.size.toString() to urls.size.toString()),
            ),
            warning = "Devices outside the window lose Internet access immediately.",
            onConfirm = {
                confirm = false
                vm.saveParental(devices.toList(), window, days, urlMode, urls)
            },
            onDismiss = { confirm = false },
        )
    }
}
