@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.sohan.linkpilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sohan.linkpilot.ui.baseUrlHost
import dev.sohan.linkpilot.ui.components.ErrorBlock
import dev.sohan.linkpilot.ui.components.FormField
import dev.sohan.linkpilot.ui.components.LoadingBlock
import dev.sohan.linkpilot.ui.components.SecretField
import dev.sohan.linkpilot.ui.components.SettingSwitch
import dev.sohan.linkpilot.vm.RouterUiState
import dev.sohan.linkpilot.vm.SessionPhase

@Composable
fun LoginScreen(
    ui: RouterUiState,
    onConnect: (String, String, Boolean) -> Unit,
    onRestore: () -> Unit,
    onForget: () -> Unit,
) {
    var address by rememberSaveable { mutableStateOf(baseUrlHost(ui.baseUrl)) }
    // Never parcel the admin password into saved instance state.
    var password by remember { mutableStateOf("") }
    var remember by rememberSaveable { mutableStateOf(true) }
    var localError by remember { mutableStateOf<String?>(null) }

    val connecting = ui.phase == SessionPhase.CONNECTING

    fun submit() {
        localError = when {
            address.isBlank() -> "Enter the router address"
            password.isEmpty() -> "Enter the admin password"
            else -> null
        }
        if (localError == null) onConnect(address, password, remember)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Surface(
                shape = MaterialTheme.shapes.extraLargeIncreased,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Router,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "LinkPilot",
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Local control for your Tenda router",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Sign in", style = MaterialTheme.typography.titleLargeEmphasized)
                    Text(
                        "The stock firmware has no username. Use the admin password from the router label or the web UI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FormField(
                        value = address,
                        onValueChange = { address = it },
                        label = "Router address",
                        placeholder = "192.168.0.1",
                        helper = "Add :port only if the web UI runs elsewhere",
                        keyboard = KeyboardType.Uri,
                        enabled = !connecting,
                    )
                    SecretField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Admin password",
                        enabled = !connecting,
                    )
                    SettingSwitch(
                        title = "Remember on this device",
                        subtitle = "Encrypted with the Android Keystore; never leaves the phone",
                        checked = remember,
                        onCheckedChange = { remember = it },
                        enabled = !connecting,
                    )

                    val error = localError ?: ui.initialError
                    if (error != null) ErrorBlock(error)

                    Button(
                        onClick = ::submit,
                        enabled = !connecting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            if (connecting) "Connecting…" else "Connect",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    if (connecting) LoadingBlock("Waiting for the router")

                    if (ui.hasSavedPassword) {
                        FilledTonalButton(
                            onClick = onRestore,
                            enabled = !connecting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Restore saved session")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Saved for ${ui.baseUrl}. Restoring uses the Keystore password without showing it.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onForget) { Text("Forget") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "Talks only to your LAN over HTTP. No cloud account.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
