package dev.sohan.linkpilot.ui

import dev.sohan.linkpilot.ui.components.Choice

/** Shared Wi-Fi security options for the Wi-Fi screen and relay join dialog. */
val SECURITY_CHOICES = listOf(
    Choice("wpa2-psk", "WPA2"),
    Choice("wpa&wpa2", "Mixed"),
    Choice("wpa-psk", "WPA"),
    Choice("none", "Open"),
)

fun securityLabel(type: String): String =
    SECURITY_CHOICES.firstOrNull { it.value == type }?.label ?: type.ifBlank { "—" }
