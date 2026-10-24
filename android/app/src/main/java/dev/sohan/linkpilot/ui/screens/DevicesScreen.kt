@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sohan.linkpilot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.sohan.linkpilot.data.AppContainer
import dev.sohan.linkpilot.data.Client
import dev.sohan.linkpilot.ui.components.Caption
import dev.sohan.linkpilot.ui.components.ConfirmDialog
import dev.sohan.linkpilot.ui.components.DeviceAvatar
import dev.sohan.linkpilot.ui.components.EmptyState
import dev.sohan.linkpilot.ui.components.ErrorBlock
import dev.sohan.linkpilot.ui.components.InfoRow
import dev.sohan.linkpilot.ui.components.LoadingBlock
import dev.sohan.linkpilot.ui.components.ScreenScaffold
import dev.sohan.linkpilot.ui.components.SectionCard
import dev.sohan.linkpilot.ui.components.StatusPill
import dev.sohan.linkpilot.ui.components.TextFieldDialog
import dev.sohan.linkpilot.ui.formatSpeed
import dev.sohan.linkpilot.ui.formatTime
import dev.sohan.linkpilot.vm.RouterUiState
import dev.sohan.linkpilot.vm.RouterViewModel

private fun Client.isBlocked(): Boolean = blocked || !allowed

private val DEVICE_SHEET_VALUES = setOf(SheetValue.Hidden, SheetValue.Expanded)

@Composable
fun DevicesScreen(ui: RouterUiState, vm: RouterViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedMac by rememberSaveable { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Client?>(null) }
    var limitsTarget by remember { mutableStateOf<Client?>(null) }
    var blockTarget by remember { mutableStateOf<Client?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val refresh: () -> Unit = {
        scope.launch {
            refreshing = true
            try {
                // Start both reads first so the network work overlaps.
                val qosJob = vm.refreshQos()
                val natJob = vm.refreshNat()
                qosJob.join()
                natJob.join()
            } finally {
                refreshing = false
            }
        }
    }

    val qos = ui.qos
    val nat = ui.nat
    val favorites = ui.favorites
    val online = qos?.clients.orEmpty()
    val blocked = qos?.blocked.orEmpty()
    val all = remember(qos) {
        online + blocked.filter { b -> online.none { it.mac.equals(b.mac, ignoreCase = true) } }
    }
    val onlineCount = all.count { !it.isBlocked() }
    val blockedCount = all.count { it.isBlocked() }

    // The router reports the requesting client in `localhost`; getNAT adds its MAC.
    val selfIp = qos?.localIp.orEmpty()
    val selfMac = nat?.localMac.orEmpty().ifBlank { qos?.localMac.orEmpty() }
    val allowOnly = nat?.filterMode == "pass"
    fun Client.isThisPhone(): Boolean =
        (selfIp.isNotBlank() && ip == selfIp) ||
            (selfMac.isNotBlank() && mac.equals(selfMac, ignoreCase = true))

    // Names are expensive (QoS + filter + lease lookups); resolve each MAC once per snapshot.
    val nameByMac = remember(qos, nat, ui.aliases) {
        all.associate { it.mac to ui.deviceName(it.mac, hostname = it.hostname, fallback = it.ip) }
    }
    fun displayName(client: Client): String = nameByMac[client.mac] ?: client.ip

    fun matches(client: Client): Boolean {
        if (query.isBlank()) return true
        val needle = query.trim().lowercase()
        return displayName(client).lowercase().contains(needle) ||
            client.hostname.lowercase().contains(needle) ||
            client.ip.contains(needle) ||
            client.mac.lowercase().contains(needle)
    }

    val filtered = remember(all, query, favorites, nameByMac) {
        all.filter(::matches)
            .sortedWith(
                compareByDescending<Client> { it.mac.lowercase() in favorites }
                    .thenBy { displayName(it).lowercase() },
            )
    }

    val selected = all.firstOrNull { it.mac.equals(selectedMac, ignoreCase = true) }

    // A device that drops out of the client table should close its sheet for good; otherwise
    // it reappears the next time the same MAC reconnects.
    LaunchedEffect(selectedMac, selected, qos) {
        if (selectedMac != null && qos != null && selected == null) selectedMac = null
    }

    ScreenScaffold(
        title = "Devices",
        actions = {
            val busy = refreshing || ui.busy
            IconButton(
                onClick = refresh,
                enabled = !busy,
                modifier = Modifier.semantics {
                    contentDescription = if (refreshing) "Refreshing devices" else "Refresh devices and network settings"
                },
            ) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search devices") },
                    placeholder = { Text("Name, IP or MAC") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                )
            }

            if (qos != null) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StatusPill("$onlineCount online", MaterialTheme.colorScheme.primary, Icons.Rounded.Wifi)
                        if (blockedCount > 0) {
                            StatusPill("$blockedCount blocked", MaterialTheme.colorScheme.error, Icons.Rounded.Block)
                        }
                        if (query.isNotBlank()) {
                            StatusPill(
                                "${filtered.size} shown",
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                Icons.Rounded.Search,
                            )
                        }
                    }
                }
            }

            if (allowOnly && qos != null) {
                item {
                    SectionCard(title = "Allow-only MAC filter is on") {
                        Caption(
                            "Only devices on the allow list can join. Blocking removes a device from that list " +
                                "and adds it to the QoS block list; restoring adds it back and clears the block."
                        )
                    }
                }
            }

            when {
                qos == null && (ui.busy || refreshing) -> item { LoadingBlock("Reading the client table") }
                qos == null -> item {
                    ErrorBlock("Could not read the client table.", onRetry = refresh)
                }
                filtered.isEmpty() -> item {
                    EmptyState(
                        if (query.isBlank()) "No clients connected" else "No matches",
                        if (query.isBlank()) "The router reports an empty client list." else "Try a shorter search term.",
                        icon = if (query.isBlank()) Icons.Rounded.Devices else Icons.Rounded.Search,
                    )
                }
                else -> {
                    val visibleOnline = filtered.filter { !it.isBlocked() }
                    val visibleBlocked = filtered.filter { it.isBlocked() }

                    if (visibleOnline.isNotEmpty()) {
                        item { SectionHeader("Online") }
                        items(visibleOnline, key = { it.mac }, contentType = { "client" }) { client ->
                            ClientRow(
                                client,
                                displayName(client),
                                client.mac.lowercase() in favorites,
                                self = client.isThisPhone(),
                                modifier = Modifier.animateItem(),
                            ) {
                                focusManager.clearFocus()
                                selectedMac = client.mac
                            }
                        }
                    }
                    if (visibleBlocked.isNotEmpty()) {
                        item { SectionHeader("Blocked") }
                        items(visibleBlocked, key = { it.mac + "-blocked" }, contentType = { "client" }) { client ->
                            ClientRow(
                                client,
                                displayName(client),
                                client.mac.lowercase() in favorites,
                                self = client.isThisPhone(),
                                modifier = Modifier.animateItem(),
                            ) {
                                focusManager.clearFocus()
                                selectedMac = client.mac
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (selected != null) {
        val selectedName = displayName(selected)
        val isFavorite = selected.mac.lowercase() in favorites
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = DEVICE_SHEET_VALUES,
        )
        val context = LocalContext.current
        val prefs = remember { AppContainer.from(context).prefs }
        val seen = remember(selected.mac, qos?.fetchedAt) { prefs.seen(selected.mac) }

        ModalBottomSheet(
            onDismissRequest = { selectedMac = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeviceAvatar(selectedName, selected.connectType == "wifi", blocked = selected.isBlocked())
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            selectedName,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            selected.mac,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconToggleButton(
                        checked = isFavorite,
                        onCheckedChange = { vm.toggleFavorite(selected.mac) },
                    ) {
                        Icon(
                            if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Favourite device",
                            tint = if (isFavorite) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusPill(
                        if (selected.connectType == "wifi") "Wi-Fi" else "Ethernet",
                        MaterialTheme.colorScheme.secondary,
                        if (selected.connectType == "wifi") Icons.Rounded.Wifi else Icons.Rounded.SettingsEthernet,
                    )
                    if (selected.isBlocked()) {
                        StatusPill("Internet blocked", MaterialTheme.colorScheme.error, Icons.Rounded.Block)
                    }
                    if (allowOnly) {
                        val onAllowList = ui.onAllowList(selected.mac)
                        StatusPill(
                            if (onAllowList) "On allow list" else "Not on allow list",
                            if (onAllowList) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            Icons.Rounded.Shield,
                        )
                    }
                    if (selected.isThisPhone()) {
                        StatusPill("This phone", MaterialTheme.colorScheme.tertiary, Icons.Rounded.Smartphone)
                    }
                    if (
                        (selected.hostname.isBlank() || selected.hostname.equals("Unknown", true)) &&
                        selectedName == selected.ip
                    ) {
                        StatusPill(
                            "Unknown device",
                            MaterialTheme.colorScheme.tertiary,
                            Icons.AutoMirrored.Rounded.HelpOutline,
                        )
                    }
                }

                DeviceSection(title = "Connection") {
                    InfoRow("IP address", selected.ip, copyable = true)
                    InfoRow("MAC address", selected.mac, copyable = true)
                    InfoRow("Hostname", selected.hostname)
                    InfoRow("Down / up", "${formatSpeed(selected.downKbps)} · ${formatSpeed(selected.upKbps)}")
                    InfoRow(
                        "Limits",
                        if (selected.unlimitedDown && selected.unlimitedUp) "Unlimited"
                        else "${if (selected.unlimitedDown) "∞" else "${selected.downLimit} KB/s"} down · " +
                            "${if (selected.unlimitedUp) "∞" else "${selected.upLimit} KB/s"} up",
                    )
                }

                if (seen != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DeviceSection(title = "History (this app)") {
                        InfoRow("First seen", formatTime(seen.firstSeen))
                        InfoRow("Last seen", formatTime(seen.lastSeen))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { renameTarget = selected },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rename")
                    }
                    FilledTonalButton(
                        onClick = { limitsTarget = selected },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Limits")
                    }
                }

                if (selected.isThisPhone()) {
                    Caption(
                        "This is the phone running LinkPilot. Blocking it would cut off the app until you sign in from another device."
                    )
                } else {
                    val isBlocked = selected.isBlocked()
                    Button(
                        onClick = { blockTarget = selected },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = if (isBlocked) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        },
                    ) {
                        Icon(
                            if (isBlocked) Icons.Rounded.LockOpen else Icons.Rounded.Block,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                allowOnly && !isBlocked -> "Block device (allow list)"
                                allowOnly -> "Allow and restore"
                                !isBlocked -> "Block Internet access"
                                else -> "Restore Internet access"
                            }
                        )
                    }
                }

                Text(
                    if (allowOnly) {
                        "Allow-only mode: blocking edits both the MAC allow list and the QoS table. The allow list has 20 slots and QoS tracks up to 10 blocks."
                    } else {
                        "Blocking and limits rewrite the router's whole QoS table. LinkPilot resends every known client, so untouched devices keep their rules."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    renameTarget?.let { client ->
        TextFieldDialog(
            title = "Rename device",
            label = "Name",
            initialValue = nameByMac[client.mac] ?: client.ip,
            helper = "Shared with the MAC filter and reservations. Leave blank to use the router's name.",
            validate = { if (it.trim().length > 32) "Use 32 characters or fewer" else null },
            onConfirm = { name ->
                vm.renameClient(client, name.trim())
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    limitsTarget?.let { client ->
        LimitDialog(
            client = client,
            name = nameByMac[client.mac] ?: client.ip,
            onDismiss = { limitsTarget = null },
            onSave = { down, up ->
                vm.setClientLimits(client, down, up)
                limitsTarget = null
            },
        )
    }

    blockTarget?.let { client ->
        val targetName = nameByMac[client.mac] ?: client.ip
        val isBlocked = client.isBlocked()
        ConfirmDialog(
            title = if (!isBlocked) "Block $targetName?"
            else if (allowOnly) "Allow $targetName again?" else "Unblock $targetName?",
            body = when {
                allowOnly && !isBlocked ->
                    "Allow-only MAC filtering is on. Blocking removes this device from the allow list and adds it " +
                        "to the QoS block list. It loses Wi-Fi/LAN access and stays blocked if the filter is switched off."
                allowOnly ->
                    "Allow-only MAC filtering is on. Restoring adds this device to the allow list and clears its " +
                        "QoS block, so it can rejoin with unlimited bandwidth."
                !isBlocked ->
                    "The client loses Internet access but stays connected to the router. The firmware allows at most 10 blocked devices."
                else ->
                    "The client regains Internet access; the app resets its limits to \"Unlimited\"."
            },
            confirmLabel = when {
                allowOnly && !isBlocked -> "Block device"
                allowOnly -> "Allow and restore"
                !isBlocked -> "Block"
                else -> "Unblock"
            },
            destructive = !isBlocked,
            onConfirm = {
                vm.setClientAllowed(client, isBlocked)
                blockTarget = null
                selectedMac = null
            },
            onDismiss = { blockTarget = null },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMediumEmphasized,
        modifier = Modifier
            .padding(start = 4.dp, top = 4.dp)
            .semantics { heading() },
    )
}

/** Groups rows inside the sheet, which is already a surface; a second card would be invisible. */
@Composable
private fun DeviceSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun ClientRow(
    client: Client,
    name: String,
    favorite: Boolean,
    self: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeviceAvatar(name, client.connectType == "wifi", blocked = client.isBlocked())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (favorite) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = "Favourite device",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(14.dp),
                        )
                    }
                }
                Text(
                    buildString {
                        append("${client.ip} · ${if (client.connectType == "wifi") "Wi-Fi" else "Ethernet"}")
                        if (self) append(" · This phone")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (self) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (client.isBlocked()) {
                    Text(
                        "Blocked",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                    )
                } else {
                    Text(
                        formatSpeed(client.downKbps),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                    Text(
                        if (client.unlimitedDown) "Unlimited"
                        else "${client.downLimit} KB/s limit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LimitDialog(client: Client, name: String, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var down by remember { mutableStateOf(if (client.unlimitedDown) "38528" else client.downLimit.toString()) }
    var up by remember { mutableStateOf(if (client.unlimitedUp) "38528" else client.upLimit.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    val presets = listOf(
        38528 to "Unlimited",
        125 to "1 Mbps",
        250 to "2 Mbps",
        625 to "5 Mbps",
        1250 to "10 Mbps",
        3125 to "25 Mbps",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Speed limits", style = MaterialTheme.typography.headlineSmall)
                Text(
                    name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Anything above 38400 KB/s means no limit; the router reports its unlimited value as 4931584.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = down,
                    onValueChange = { down = it.filter(Char::isDigit) },
                    label = { Text("Download") },
                    suffix = { Text("KB/s") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = up,
                    onValueChange = { up = it.filter(Char::isDigit) },
                    label = { Text("Upload") },
                    suffix = { Text("KB/s") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Presets set both directions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { (value, label) ->
                        val selected = down == value.toString() && up == value.toString()
                        FilterChip(
                            selected = selected,
                            onClick = {
                                down = value.toString()
                                up = value.toString()
                            },
                            label = { Text(label) },
                        )
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = down.toIntOrNull()
                val u = up.toIntOrNull()
                if (d == null || u == null || d < 1 || u < 1) {
                    error = "Enter both limits as positive integers"
                } else {
                    onSave(if (d > 38400) 38528 else d, if (u > 38400) 38528 else u)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
