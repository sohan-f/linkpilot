@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.sohan.linkpilot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.DrawableRes
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sohan.linkpilot.R
import dev.sohan.linkpilot.data.Capabilities
import dev.sohan.linkpilot.data.ThemeMode
import dev.sohan.linkpilot.ui.PageId
import dev.sohan.linkpilot.ui.components.Choice
import dev.sohan.linkpilot.ui.components.ConfirmDialog
import dev.sohan.linkpilot.ui.components.EmptyState
import dev.sohan.linkpilot.ui.components.ScreenScaffold
import dev.sohan.linkpilot.ui.components.SectionCard
import dev.sohan.linkpilot.ui.components.SegmentedField
import dev.sohan.linkpilot.ui.components.SettingSwitch
import dev.sohan.linkpilot.vm.RouterUiState
import dev.sohan.linkpilot.vm.RouterViewModel

internal data class MoreEntry(
    val page: PageId,
    val title: String,
    val subtitle: String,
    @DrawableRes val icon: Int,
    val keywords: String = "",
)

internal val NETWORK_ENTRIES = listOf(
    MoreEntry(PageId.Wan, "Internet (WAN)", "Connection type, credentials, MAC clone", R.drawable.ic_public, "pppoe dhcp static mtu speed"),
    MoreEntry(PageId.Lan, "LAN & DHCP", "Router address, pool, DNS", R.drawable.ic_lan, "ip mask dhcp dns subnet"),
    MoreEntry(PageId.StaticLease, "DHCP reservations", "Fixed addresses for known devices", R.drawable.ic_list_alt, "static lease bind"),
    MoreEntry(PageId.PortForward, "Port forwarding", "Expose services to the WAN", R.drawable.ic_settings_ethernet, "nat port map"),
    MoreEntry(PageId.MacFilter, "MAC filter", "Whitelist or blacklist clients", R.drawable.ic_security, "mac deny pass whitelist"),
    MoreEntry(PageId.Firewall, "Firewall & UPnP", "DMZ, DDNS, WAN ping, AP isolation", R.drawable.ic_shield, "dmz ddns upnp ping isolation"),
    MoreEntry(PageId.Parental, "Parental controls", "Time windows and URL rules", R.drawable.ic_family_restroom, "kids schedule url filter"),
    MoreEntry(PageId.Ipv6, "IPv6", "WAN mode and LAN address", R.drawable.ic_dns, "ipv6 dhcpv6 pppoe"),
    MoreEntry(PageId.Relay, "Wireless repeating", "WISP, repeater and AP modes", R.drawable.ic_wifi_find, "relay bridge scan upstream"),
)

internal val SYSTEM_ENTRIES = listOf(
    MoreEntry(PageId.Admin, "Administration", "Password, time, remote access, reboot", R.drawable.ic_admin_panel_settings, "admin password timezone backup reboot reset"),
    MoreEntry(PageId.Audit, "Audit log", "Every change this app sent", R.drawable.ic_history, "history changes"),
)

internal val APP_ENTRIES = listOf(
    MoreEntry(PageId.About, "About & connection", "Address, session, app details", R.drawable.ic_info, "address url logout version"),
    MoreEntry(PageId.Capability, "Capability matrix", "Every endpoint, risk and verification", R.drawable.ic_tune, "api endpoints parity"),
)

// The Appearance section renders no entry rows, so its searchable words are listed separately.
// "Light" is a theme option, not part of any title — searching for exactly what the
// segmented buttons say must still find it.
internal const val APPEARANCE_KEYWORDS =
    "appearance theme light dark colour color dynamic wallpaper"

internal fun filterEntries(entries: List<MoreEntry>, query: String): List<MoreEntry> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return entries
    return entries.filter {
        it.title.lowercase().contains(needle) ||
            it.subtitle.lowercase().contains(needle) ||
            it.keywords.contains(needle)
    }
}

internal fun appearanceMatches(query: String): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return true
    return APPEARANCE_KEYWORDS.contains(needle)
}

@Composable
fun MoreScreen(ui: RouterUiState, vm: RouterViewModel, onNavigate: (PageId) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val network = filterEntries(NETWORK_ENTRIES, query)
    val system = filterEntries(SYSTEM_ENTRIES, query)
    val app = filterEntries(APP_ENTRIES, query)
    val appearanceMatch = appearanceMatches(query)

    ScreenScaffold(title = "More") { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search settings") },
                    placeholder = { Text("e.g. port, password, IPv6") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(painterResource(R.drawable.ic_clear), contentDescription = "Clear search")
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

            if (network.isNotEmpty()) {
                item { SectionHeader("Network") }
                items(network, key = { it.page }) { entry ->
                    EntryRow(entry, Modifier.animateItem()) { onNavigate(entry.page) }
                }
            }

            if (system.isNotEmpty()) {
                item { SectionHeader("System") }
                items(system, key = { it.page }) { entry ->
                    EntryRow(entry, Modifier.animateItem()) { onNavigate(entry.page) }
                }
            }

            if (appearanceMatch) {
                item { SectionHeader("Appearance") }
                item {
                    SectionCard {
                        SegmentedField(
                            label = "Theme",
                            options = listOf(
                                Choice(ThemeMode.SYSTEM.name, "System"),
                                Choice(ThemeMode.LIGHT.name, "Light"),
                                Choice(ThemeMode.DARK.name, "Dark"),
                            ),
                            selected = ui.themeMode.name,
                            onSelect = { vm.setThemeMode(ThemeMode.valueOf(it)) },
                        )
                        SettingSwitch(
                            title = "Dynamic colour",
                            subtitle = "Use the wallpaper palette on Android 12+",
                            checked = ui.dynamicColor,
                            onCheckedChange = vm::setDynamicColor,
                        )
                    }
                }
            }

            if (app.isNotEmpty()) {
                item { SectionHeader("App") }
                items(app, key = { it.page }) { entry ->
                    EntryRow(entry, Modifier.animateItem()) { onNavigate(entry.page) }
                }
            }

            if (network.isEmpty() && system.isEmpty() && app.isEmpty() && !appearanceMatch) {
                item {
                    EmptyState("No matching setting", "Try another word, or browse the capability matrix.")
                }
            }

            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_router),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                ui.baseUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Firmware ${ui.sys?.firmware ?: Capabilities.FIRMWARE}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { confirmLogout = true }) { Text("Log out") }
                    }
                }
            }
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Log out?",
            body = "The session cookie is discarded. The saved password stays encrypted on this phone unless you remove it in About.",
            confirmLabel = "Log out",
            onConfirm = {
                confirmLogout = false
                vm.logout()
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun EntryRow(entry: MoreEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    painterResource(entry.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painterResource(R.drawable.ic_keyboard_arrow_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
