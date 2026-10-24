package dev.sohan.linkpilot.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun oneDecimal(value: Double): String {
    val scaled = (value * 10).roundToInt()
    return "${scaled / 10}.${scaled % 10}"
}

/** Formats the router's KB/s value for display. */
fun formatSpeed(kbps: Double): String = when {
    kbps >= 1024 -> "${oneDecimal(kbps / 1024)} MB/s"
    kbps >= 10 -> "${kbps.roundToInt()} KB/s"
    kbps > 0 -> "${oneDecimal(kbps)} KB/s"
    else -> "0 KB/s"
}

fun formatUptime(seconds: Int): String {
    if (seconds <= 0) return "—"
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

fun formatSampleWindow(startMs: Long, endMs: Long): String {
    val seconds = (endMs - startMs).coerceAtLeast(0L) / 1_000
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

fun formatBytes(bytes: Int): String = when {
    bytes >= 1024 * 1024 -> "${oneDecimal(bytes / 1024.0 / 1024.0)} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

private val TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
}
private val CLOCK_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
}

fun formatTime(epochMs: Long): String = TIME_FORMAT.get()!!.format(Date(epochMs))

fun formatClock(epochMs: Long): String = CLOCK_FORMAT.get()!!.format(Date(epochMs))

fun baseUrlHost(baseUrl: String): String =
    baseUrl.removePrefix("http://").removePrefix("https://").trimEnd('/')

fun relayModeLabel(type: String): String = when (type) {
    "disabled" -> "Off"
    "ap" -> "AP"
    "wisp" -> "WISP"
    "client+ap" -> "Client+AP"
    else -> type.ifBlank { "—" }
}

fun isIpv4(value: String): Boolean {
    val parts = value.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
            part.toInt() in 0..255 && (part == "0" || !part.startsWith("0"))
    }
}

fun isMac(value: String): Boolean =
    Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$").matches(value.trim())

fun ipToLong(ip: String): Long? {
    if (!isIpv4(ip)) return null
    return ip.split('.').fold(0L) { acc, octet -> (acc shl 8) or octet.toLong() }
}

fun sameSubnet(a: String, network: String, mask: String): Boolean {
    val ip = ipToLong(a) ?: return false
    val net = ipToLong(network) ?: return false
    val m = ipToLong(mask) ?: return false
    return (ip and m) == (net and m)
}

fun parseWindow(value: String): Pair<Int, Int>? {
    val match = Regex("^(\\d{2}):(\\d{2})-(\\d{2}):(\\d{2})$").find(value) ?: return null
    val (h1, m1, h2, m2) = match.destructured
    val start = h1.toInt() * 60 + m1.toInt()
    val end = h2.toInt() * 60 + m2.toInt()
    if (h1.toInt() > 23 || h2.toInt() > 23 || m1.toInt() > 59 || m2.toInt() > 59) return null
    return start to end
}

fun daysFromString(value: String): Set<Int> {
    val padded = value.padEnd(8, '0')
    return (0..7).filterTo(mutableSetOf()) { padded[it] == '1' }
}

fun stringFromDays(days: Set<Int>): String =
    (0..7).joinToString("") { if (it in days) "1" else "0" }

fun daysLabel(value: String): String {
    val days = daysFromString(value)
    if (days.isEmpty()) return "No days"
    if (0 in days) return "Every day"
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return (1..7).filter { it in days }.joinToString(", ") { names[it - 1] }
}

fun signalLabel(dbm: Int): String = when {
    dbm >= -55 -> "Excellent"
    dbm >= -67 -> "Good"
    dbm >= -75 -> "Fair"
    else -> "Weak"
}

fun signalBars(dbm: Int): Int = when {
    dbm >= -55 -> 4
    dbm >= -67 -> 3
    dbm >= -75 -> 2
    else -> 1
}
