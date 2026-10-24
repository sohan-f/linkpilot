@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package dev.sohan.linkpilot.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sohan.linkpilot.data.AuditEntry
import dev.sohan.linkpilot.data.Risk
import kotlinx.coroutines.launch

/** The shell's snackbar, shared so copy feedback uses Material feedback instead of a platform Toast. */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }

private data class AppSnackbarVisuals(
    override val message: String,
    val good: Boolean,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = !good,
    override val duration: SnackbarDuration = if (good) SnackbarDuration.Short else SnackbarDuration.Long,
) : SnackbarVisuals

suspend fun SnackbarHostState.showAppMessage(text: String, good: Boolean) {
    currentSnackbarData?.dismiss()
    showSnackbar(AppSnackbarVisuals(text, good))
}

@Composable
fun AppSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState) { data ->
        val good = (data.visuals as? AppSnackbarVisuals)?.good ?: true
        val container = if (good) SnackbarDefaults.color else MaterialTheme.colorScheme.errorContainer
        val content = if (good) SnackbarDefaults.contentColor else MaterialTheme.colorScheme.onErrorContainer
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.large,
            containerColor = container,
            contentColor = content,
            actionContentColor = content,
            dismissActionContentColor = content,
        )
    }
}

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // MainShell owns the only Scaffold; nesting one per screen costs a subcomposition per route.
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 1,
                )
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        Box(Modifier.weight(1f)) {
            content(PaddingValues(0.dp))
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (title != null || trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (title != null) {
                            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                        }
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    copyable: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val canCopy = copyable && value.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (canCopy) {
                    Modifier.clickable(onClickLabel = "Copy $label") {
                        copyToClipboard(context, label, value)
                        if (snackbar != null) {
                            scope.launch { snackbar.showAppMessage("Copied $label", good = true) }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label is measured first so a long value wraps instead of squeezing it to nothing.
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        if (canCopy) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
fun StatusPill(text: String, color: Color, icon: ImageVector? = null) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.16f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
    }
}

@Composable
fun RiskChip(risk: Risk) {
    val (label, color) = when (risk) {
        Risk.READ_ONLY -> "Read only" to MaterialTheme.colorScheme.primary
        Risk.REVERSIBLE -> "Reversible" to MaterialTheme.colorScheme.secondary
        Risk.DISRUPTIVE -> "Disruptive" to MaterialTheme.colorScheme.tertiary
        Risk.DESTRUCTIVE -> "Destructive" to MaterialTheme.colorScheme.error
    }
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

data class AuditBadge(val label: String, val color: Color, val icon: ImageVector)

/** One outcome vocabulary for the Home preview and the full audit log. */
@Composable
fun auditBadge(entry: AuditEntry): AuditBadge {
    val label: String
    val color: Color
    val icon: ImageVector
    when {
        entry.outcome == "APPLIED" -> {
            label = "Applied"; color = MaterialTheme.colorScheme.primary; icon = Icons.Rounded.CheckCircle
        }
        entry.outcome == "EXPIRED" -> {
            label = "Session expired"; color = MaterialTheme.colorScheme.error; icon = Icons.Rounded.ErrorOutline
        }
        entry.outcome == "UNKNOWN" -> {
            label = "Unconfirmed"; color = MaterialTheme.colorScheme.tertiary; icon = Icons.AutoMirrored.Rounded.HelpOutline
        }
        entry.outcome.startsWith("REJECTED") -> {
            label = "Rejected"; color = MaterialTheme.colorScheme.error; icon = Icons.Rounded.ErrorOutline
        }
        else -> {
            label = entry.outcome; color = MaterialTheme.colorScheme.onSurfaceVariant; icon = Icons.Rounded.History
        }
    }
    return AuditBadge(label, color, icon)
}

/** A bulleted statement; the dot is decorative and kept out of the accessibility tree. */
@Composable
fun Bullet(
    text: String,
    modifier: Modifier = Modifier,
    markColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "•",
            style = MaterialTheme.typography.bodySmall,
            color = markColor,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = textColor)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                content?.invoke()
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                ),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Home and Administration must describe the same reboot the same way. */
@Composable
fun RebootDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = "Reboot the router?",
        body = "Every client loses connectivity for about a minute. The last reading stays on screen " +
            "until the router answers again.",
        confirmLabel = "Reboot",
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Before/after preview used by every write that changes connectivity. */@Composable
fun DiffDialog(
    title: String,
    changes: List<Pair<String, Pair<String, String>>>,
    warning: String?,
    confirmLabel: String = "Apply",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                changes.forEach { (label, values) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${values.first.ifBlank { "(empty)" }}  →  ${values.second.ifBlank { "(empty)" }}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (!warning.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun LoadingBlock(label: String = "Loading from router") {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LinearProgressIndicator(Modifier.width(120.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorBlock(message: String, onRetry: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text("Retry") }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Rounded.Inbox,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun QuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    val contentColor = contentColorFor(containerColor)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = contentColor)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Initial-letter avatar with a transport badge; no network image is fetched for a LAN client. */
@Composable
fun DeviceAvatar(label: String, wifi: Boolean, modifier: Modifier = Modifier, blocked: Boolean = false) {
    val contentColor = if (blocked) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onPrimaryContainer
    val initial = label.trim().take(1)

    Box(modifier = modifier.size(44.dp)) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (blocked) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center,
        ) {
            // An unnamed client is labelled by its IP address, whose first digit says nothing.
            if (initial.firstOrNull()?.isLetter() == true) {
                Text(
                    initial.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
            } else {
                Icon(
                    Icons.Rounded.Devices,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(18.dp),
        ) {
            Icon(
                if (wifi) Icons.Rounded.Wifi else Icons.Rounded.SettingsEthernet,
                contentDescription = null,
                modifier = Modifier.padding(3.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun copyToClipboard(context: Context, label: String, value: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText(label, value))
}

@Composable
fun VerticalGap(height: Int = 12) = Spacer(Modifier.height(height.dp))

@Composable
fun TextFieldDialog(
    title: String,
    label: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    helper: String? = null,
    validate: (String) -> String? = { null },
    keyboard: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                label = { Text(label) },
                isError = error != null,
                supportingText = {
                    Text(error ?: helper ?: "")
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val problem = validate(value)
                    if (problem != null) error = problem else onConfirm(value)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
