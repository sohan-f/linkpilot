@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.sohan.linkpilot.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sohan.linkpilot.R
import dev.sohan.linkpilot.data.Client
import dev.sohan.linkpilot.data.DiagCheck
import dev.sohan.linkpilot.data.DiagStatus
import dev.sohan.linkpilot.data.StatusSnapshot
import dev.sohan.linkpilot.data.Tone
import dev.sohan.linkpilot.ui.components.Caption
import dev.sohan.linkpilot.ui.components.ErrorBlock
import dev.sohan.linkpilot.ui.components.InfoRow
import dev.sohan.linkpilot.ui.components.LoadingBlock
import dev.sohan.linkpilot.ui.components.PillTone
import dev.sohan.linkpilot.ui.components.ScreenScaffold
import dev.sohan.linkpilot.ui.components.SectionCard
import dev.sohan.linkpilot.ui.components.StatusPill
import dev.sohan.linkpilot.ui.formatClock
import dev.sohan.linkpilot.ui.formatSampleWindow
import dev.sohan.linkpilot.ui.formatSpeed
import dev.sohan.linkpilot.ui.formatUptime
import dev.sohan.linkpilot.vm.RouterUiState
import dev.sohan.linkpilot.vm.RouterViewModel
import dev.sohan.linkpilot.vm.STATUS_POLL_SECONDS
import dev.sohan.linkpilot.vm.TrafficSample

@Composable
fun MonitorScreen(ui: RouterUiState, vm: RouterViewModel) {
    val status = ui.status
    val clients = ui.qos?.clients.orEmpty()
        .sortedByDescending { it.downKbps }
        .take(5)

    ScreenScaffold(
        title = "Monitor",
        actions = {
            IconButton(onClick = vm::refreshStatus) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh status")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (status == null) {
                item {
                    if (ui.busy) {
                        LoadingBlock("Reading router status")
                    } else {
                        ErrorBlock(
                            "Could not read the router's status.",
                            onRetry = vm::refreshStatus,
                        )
                    }
                }
            } else {
                if (ui.statusStale) {
                    item {
                        ErrorBlock(
                            "The router is not answering. Showing the last reading.",
                            onRetry = vm::refreshStatus,
                        )
                    }
                }
                item { ThroughputCard(ui, status.fetchedAt) }
                item { WanCard(status) }
            }

            item { DiagnosticsCard(ui, vm) }

            if (clients.isNotEmpty()) {
                item { BusiestClientsCard(clients, ui) }
            }

            item {
                SectionCard(title = "About these numbers") {
                    Caption(
                        "Speeds are the router's own KB/s counters, not a phone-side measurement. " +
                            "They cover the whole LAN, so a TV stream can dominate the chart.",
                    )
                    Caption(
                        "No historical traffic is stored on the router, so LinkPilot keeps only " +
                            "this in-memory session window.",
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ThroughputCard(ui: RouterUiState, updatedAt: Long) {
    val samples = ui.samples
    val latest = samples.lastOrNull()
    val collecting = !ui.statusStale

    SectionCard(
        title = "Throughput",
        subtitle = "Sampled every ${STATUS_POLL_SECONDS}s · updated ${formatClock(updatedAt)}",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThroughputStat(
                label = "Download",
                value = latest?.let { formatSpeed(it.downKbps) } ?: "—",
                icon = R.drawable.ic_download,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            ThroughputStat(
                label = "Upload",
                value = latest?.let { formatSpeed(it.upKbps) } ?: "—",
                icon = R.drawable.ic_upload,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
        }

        if (samples.size < 3) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (collecting) {
                    LinearWavyProgressIndicator(Modifier.width(120.dp))
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    if (collecting) "Collecting samples — the chart starts after 3 readings."
                    else "Not enough samples to draw a chart.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            ThroughputChart(
                samples = samples,
                down = MaterialTheme.colorScheme.primary,
                up = MaterialTheme.colorScheme.tertiary,
                grid = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
            )
            val peakDown = samples.maxOf { it.downKbps }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Last ${formatSampleWindow(samples.first().at, samples.last().at)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_download),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Peak down ${formatSpeed(peakDown)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThroughputStat(
    label: String,
    value: String,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.titleLargeEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WanCard(status: StatusSnapshot) {
    val tone = status.internet.tone
    val tonePill = when (tone) {
        Tone.GOOD -> PillTone.Primary
        Tone.WORKING -> PillTone.Tertiary
        Tone.BAD -> PillTone.Error
    }
    val toneIcon = when (tone) {
        Tone.GOOD -> R.drawable.ic_wifi
        Tone.WORKING -> R.drawable.ic_sync
        Tone.BAD -> R.drawable.ic_cloud_off
    }

    SectionCard(title = "WAN") {
        StatusPill(status.internet.message, tonePill, toneIcon)
        InfoRow("WAN mode", wanModeLabel(status.system.wanType, status.internet.family))
        InfoRow("WAN address", status.system.wanIp, copyable = true)
        InfoRow("Gateway", status.system.wanGateway)
        InfoRow(
            "DNS",
            listOf(status.system.dns1, status.system.dns2)
                .filter { it.isNotBlank() }
                .joinToString(", "),
        )
        InfoRow("WAN MAC", status.system.wanMac, copyable = true)
        InfoRow("Uptime", formatUptime(status.system.uptimeSeconds))
        InfoRow("Firmware", status.system.firmware)
    }
}

private fun wanModeLabel(type: String, family: Char): String {
    val normalized = type.trim().lowercase()
    return when {
        normalized == "dhcp" -> "DHCP"
        normalized == "pppoe" -> "PPPoE"
        normalized == "static" -> "Static IP"
        normalized.isNotEmpty() -> type.trim()
        family == '2' -> "DHCP"
        family == '3' -> "PPPoE"
        else -> ""
    }
}

@Composable
private fun DiagnosticsCard(ui: RouterUiState, vm: RouterViewModel) {
    SectionCard(
        title = "Diagnostics",
        subtitle = "On-device checks; the router API has no ICMP endpoint",
        trailing = {
            FilledTonalButton(
                onClick = vm::runDiagnostics,
                enabled = !ui.diagRunning,
                contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            ) {
                if (!ui.diagRunning) {
                    Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (ui.diagRunning) "Running…" else "Run")
            }
        },
    ) {
        if (ui.diagRunning) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        }

        val results = ui.diagnostics
        if (results == null && !ui.diagRunning) {
            Caption(
                "Checks the router's HTTP API, TCP port 80, DNS resolution and outbound Internet.",
            )
            Caption(
                "Only the Internet check leaves the LAN: one HTTPS request to " +
                    "connectivitycheck.gstatic.com, sent when you tap Run.",
            )
        }
        if (results != null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                results.forEach { check -> DiagRow(check) }
            }
        }
    }
}

@Composable
private fun DiagRow(check: DiagCheck) {
    val (icon, color, statusLabel) = when (check.status) {
        DiagStatus.OK -> Triple(
            R.drawable.ic_check_circle,
            MaterialTheme.colorScheme.primary,
            "Passed",
        )
        DiagStatus.WARN -> Triple(
            R.drawable.ic_warning_amber,
            MaterialTheme.colorScheme.tertiary,
            "Warning",
        )
        DiagStatus.FAIL -> Triple(
            R.drawable.ic_error_outline,
            MaterialTheme.colorScheme.error,
            "Failed",
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(check.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                check.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "${check.elapsedMs} ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BusiestClientsCard(clients: List<Client>, ui: RouterUiState) {
    val maxDown = clients.maxOfOrNull { it.downKbps }?.coerceAtLeast(1.0) ?: 1.0

    SectionCard(
        title = "Busiest clients",
        subtitle = "Top 5 by current download rate",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            clients.forEach { client ->
                ClientTrafficRow(
                    client = client,
                    name = ui.deviceName(client.mac, hostname = client.hostname, fallback = client.ip),
                    maxDownKbps = maxDown,
                )
            }
        }
    }
}

@Composable
private fun ClientTrafficRow(client: Client, name: String, maxDownKbps: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                painterResource(R.drawable.ic_download),
                contentDescription = "Download",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                formatSpeed(client.downKbps),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        ShareMeter(
            fraction = (client.downKbps / maxDownKbps).toFloat().coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clearAndSetSemantics {},
        )
    }
}

@Composable
private fun ShareMeter(fraction: Float, modifier: Modifier = Modifier) {
    val shape = CircleShape
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.secondaryContainer)) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun ThroughputChart(
    samples: List<TrafficSample>,
    down: Color,
    up: Color,
    grid: Color,
    modifier: Modifier = Modifier,
) {
    val peakDown = samples.maxOf { it.downKbps }
    val peakUp = samples.maxOf { it.upKbps }
    val latest = samples.last()
    val description = "Throughput chart over the last " +
        "${formatSampleWindow(samples.first().at, samples.last().at)}. " +
        "Download ${formatSpeed(latest.downKbps)}, upload ${formatSpeed(latest.upKbps)}. " +
        "Peak download ${formatSpeed(peakDown)}."

    Canvas(
        modifier = modifier.semantics {
            contentDescription = description
            role = Role.Image
        },
    ) {
        val maxValue = maxOf(peakDown, peakUp, 100.0)
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val left = 2.dp.toPx()
        val right = size.width - 2.dp.toPx()
        val height = (bottom - top).coerceAtLeast(1f)
        val width = (right - left).coerceAtLeast(1f)

        repeat(4) { index ->
            val y = top + height * index / 3f
            drawLine(
                grid.copy(alpha = 0.6f),
                Offset(left, y),
                Offset(right, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val times = samples.map { it.at }
        val start = times.first()
        val span = (times.last() - start).coerceAtLeast(1L).toFloat()

        fun pathFor(values: List<Double>): Path {
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = left + width *
                    (((times[index] - start).toFloat() / span).coerceIn(0f, 1f))
                val y = bottom - (value / maxValue).toFloat() * height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }

        val downPath = pathFor(samples.map { it.downKbps })
        val upPath = pathFor(samples.map { it.upKbps })

        val area = Path().apply {
            addPath(downPath)
            lineTo(right, bottom)
            lineTo(left, bottom)
            close()
        }
        drawPath(
            area,
            brush = Brush.verticalGradient(
                listOf(down.copy(alpha = 0.22f), down.copy(alpha = 0.02f)),
                startY = top,
                endY = bottom,
            ),
        )
        drawPath(
            downPath,
            down,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawPath(
            upPath,
            up,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
