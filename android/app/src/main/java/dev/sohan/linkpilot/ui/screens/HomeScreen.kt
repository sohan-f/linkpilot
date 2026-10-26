@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package dev.sohan.linkpilot.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sohan.linkpilot.R
import dev.sohan.linkpilot.data.InternetStatus
import dev.sohan.linkpilot.data.StatusSnapshot
import dev.sohan.linkpilot.data.Tone
import dev.sohan.linkpilot.ui.baseUrlHost
import dev.sohan.linkpilot.ui.components.AuditRow
import dev.sohan.linkpilot.ui.components.Bullet
import dev.sohan.linkpilot.ui.components.ErrorBlock
import dev.sohan.linkpilot.ui.components.InfoRow
import dev.sohan.linkpilot.ui.components.LoadingBlock
import dev.sohan.linkpilot.ui.components.MetricTile
import dev.sohan.linkpilot.ui.components.QuickAction
import dev.sohan.linkpilot.ui.components.RebootDialog
import dev.sohan.linkpilot.ui.components.ScreenScaffold
import dev.sohan.linkpilot.ui.components.SectionCard
import dev.sohan.linkpilot.ui.components.SectionHeader
import dev.sohan.linkpilot.ui.components.WifiQrDialog
import dev.sohan.linkpilot.ui.formatSpeed
import dev.sohan.linkpilot.ui.formatTime
import dev.sohan.linkpilot.ui.formatUptime
import dev.sohan.linkpilot.ui.relayModeLabel
import dev.sohan.linkpilot.vm.RouterUiState
import dev.sohan.linkpilot.vm.RouterViewModel
import kotlinx.coroutines.launch

private data class QuickActionItem(
    val label: String,
    @DrawableRes val icon: Int,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

@Composable
fun HomeScreen(
    ui: RouterUiState,
    vm: RouterViewModel,
    onOpenWifi: () -> Unit,
    onOpenMonitor: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    var confirmReboot by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val status = ui.status
    val wifi = ui.wifi?.takeIf { it.ssid.isNotBlank() }
    val routerName = status?.stats?.routerName?.trim()?.takeIf { it.isNotEmpty() } ?: "Your router"

    val refresh: () -> Unit = {
        scope.launch {
            refreshing = true
            try {
                vm.refreshStatus().join()
            } finally {
                refreshing = false
            }
        }
    }

    val upstream = status?.internet
    val relay = ui.relay
    val warnings = buildList {
        if (ui.sys?.hasPassword == false) {
            add("No admin password is set. Anyone on this LAN can open the router's web UI.")
        }
        if (upstream?.upstreamFault == true) {
            add("WAN reports an upstream problem: ${upstream.message}.")
        }
        if (relay != null && relay.type.isNotBlank() && relay.type != "disabled") {
            add(
                "Wireless repeating is active (${relayModeLabel(relay.type)}). Changing the mode " +
                    "restarts the router and can change its address.",
            )
        }
    }

    ScreenScaffold(
        title = "Home",
        actions = {
            IconButton(
                onClick = { showQr = true },
                enabled = wifi != null,
            ) {
                Icon(
                    painterResource(R.drawable.ic_qr_code2),
                    contentDescription = if (wifi != null) "Share Wi-Fi" else "Share Wi-Fi, network name not loaded",
                )
            }
            IconButton(
                onClick = refresh,
                enabled = !refreshing && !ui.busy,
                modifier = Modifier.semantics {
                    contentDescription = if (refreshing) "Refreshing status" else "Refresh status"
                },
            ) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(painterResource(R.drawable.ic_refresh), contentDescription = null)
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
                Column {
                    Text(
                        routerName,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        baseUrlHost(ui.baseUrl),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (status != null && ui.statusStale) {
                item {
                    ErrorBlock(
                        "The router is not answering. Showing the last reading.",
                        onRetry = refresh,
                    )
                }
            }

            item {
                when {
                    status == null && ui.busy -> LoadingBlock("Reading router status")
                    status == null -> ErrorBlock(
                        "Could not read the router's status.",
                        onRetry = refresh,
                    )
                    else -> ConnectionHero(status.internet, status.fetchedAt, ui.statusStale)
                }
            }

            if (warnings.isNotEmpty()) {
                item {
                    SectionCard(
                        title = "Needs attention",
                        trailing = {
                            Icon(
                                painterResource(R.drawable.ic_warning_amber),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    ) {
                        warnings.forEach { warning ->
                            Bullet(
                                warning,
                                markColor = MaterialTheme.colorScheme.error,
                                textColor = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            if (status != null) {
                item {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetricTile(
                            "Download",
                            formatSpeed(status.stats.downKbps),
                            R.drawable.ic_download,
                            Modifier.weight(1f).fillMaxHeight(),
                        )
                        MetricTile(
                            "Upload",
                            formatSpeed(status.stats.upKbps),
                            R.drawable.ic_upload,
                            Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetricTile(
                            "Devices online",
                            status.stats.online.toString(),
                            R.drawable.ic_devices,
                            Modifier.weight(1f).fillMaxHeight(),
                        )
                        MetricTile(
                            "WAN uptime",
                            formatUptime(status.system.uptimeSeconds),
                            R.drawable.ic_schedule,
                            Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }

                item {
                    SectionCard(title = "This router") {
                        InfoRow("WAN address", status.system.wanIp, copyable = true)
                        InfoRow("WAN gateway", status.system.wanGateway)
                        InfoRow(
                            "Wi-Fi",
                            listOfNotNull(
                                ui.wifi?.ssid?.takeIf { it.isNotBlank() },
                                ui.wifi?.channelCurrent
                                    ?.takeIf { it.isNotBlank() && it != "0" }
                                    ?.let { "channel $it" },
                            ).joinToString(" · "),
                        )
                        InfoRow("Firmware", status.system.firmware)
                    }
                }
            }

            item {
                SectionCard(title = "Quick actions") {
                    val actions = listOf(
                        QuickActionItem("Wi-Fi", R.drawable.ic_wifi, onOpenWifi),
                        QuickActionItem("Devices", R.drawable.ic_devices, onOpenDevices),
                        QuickActionItem("Monitor", R.drawable.ic_insights, onOpenMonitor),
                        QuickActionItem(
                            "Reboot",
                            R.drawable.ic_power_settings_new,
                            { confirmReboot = true },
                            destructive = true,
                        ),
                    )
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val columns = if (maxWidth >= 480.dp) 4 else 2
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            actions.chunked(columns).forEach { row ->
                                Row(
                                    modifier = Modifier.height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    row.forEach { action ->
                                        QuickAction(
                                            label = action.label,
                                            icon = action.icon,
                                            onClick = action.onClick,
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            containerColor = if (action.destructive) {
                                                MaterialTheme.colorScheme.errorContainer
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            },
                                        )
                                    }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }

            if (ui.audit.isNotEmpty()) {
                item { SectionHeader("Recent changes") }
                items(ui.audit.take(3), key = { it.id }) { entry -> AuditRow(entry) }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
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

    if (showQr && wifi != null) {
        WifiQrDialog(
            ssid = wifi.ssid,
            password = wifi.password,
            security = wifi.security,
            onDismiss = { showQr = false },
        )
    }
}

@Composable
private fun ConnectionHero(internet: InternetStatus, updatedAt: Long, stale: Boolean) {
    val tone = internet.tone
    val container = when (tone) {
        Tone.GOOD -> MaterialTheme.colorScheme.primaryContainer
        Tone.WORKING -> MaterialTheme.colorScheme.tertiaryContainer
        Tone.BAD -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (tone) {
        Tone.GOOD -> MaterialTheme.colorScheme.onPrimaryContainer
        Tone.WORKING -> MaterialTheme.colorScheme.onTertiaryContainer
        Tone.BAD -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = container,
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionOrb(tone, content, animate = !stale)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        internet.message,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = content,
                    )
                    Text(
                        if (stale) "Last reading · ${formatTime(updatedAt)}"
                        else "Updated ${formatTime(updatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.75f),
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp),
                color = content.copy(alpha = 0.18f),
            )
            FlowRow(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroChip("LAN only", content)
                HeroChip("HTTP on LAN", content)
                HeroChip("No cloud", content)
            }
        }
    }
}

@Composable
private fun HeroChip(label: String, content: Color) {
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = content.copy(alpha = 0.10f),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConnectionOrb(tone: Tone, tint: Color, animate: Boolean) {
    val rotation = if (animate && tone == Tone.GOOD) {
        val transition = rememberInfiniteTransition(label = "connection-ring")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 2400, easing = LinearEasing)),
            label = "ring-rotation",
        )
    } else {
        null
    }
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(76.dp)) {
            drawCircle(color = tint.copy(alpha = 0.14f))
            if (rotation != null) {
                // Reading the animation inside draw invalidates drawing only, not Home's composition.
                rotate(rotation.value) {
                    drawArc(
                        color = tint.copy(alpha = 0.75f),
                        startAngle = -90f,
                        sweepAngle = 78f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.06f, size.height * 0.06f),
                        size = Size(size.width * 0.88f, size.height * 0.88f),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
        Icon(
            painterResource(
                when (tone) {
                    Tone.GOOD -> R.drawable.ic_wifi
                    Tone.WORKING -> R.drawable.ic_sync
                    Tone.BAD -> R.drawable.ic_cloud_off
                },
            ),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(30.dp),
        )
    }
}
