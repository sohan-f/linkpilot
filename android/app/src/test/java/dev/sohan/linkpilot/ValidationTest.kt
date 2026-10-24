package dev.sohan.linkpilot

import dev.sohan.linkpilot.data.normalizeBase
import dev.sohan.linkpilot.data.forFilterMode
import dev.sohan.linkpilot.data.loginErrorMessage
import dev.sohan.linkpilot.data.InternetStatus
import dev.sohan.linkpilot.data.MacFilterEntry
import dev.sohan.linkpilot.data.resolveDeviceName
import dev.sohan.linkpilot.ui.baseUrlHost
import dev.sohan.linkpilot.ui.daysFromString
import dev.sohan.linkpilot.ui.daysLabel
import dev.sohan.linkpilot.ui.formatBytes
import dev.sohan.linkpilot.ui.formatSampleWindow
import dev.sohan.linkpilot.ui.formatSpeed
import dev.sohan.linkpilot.ui.formatUptime
import dev.sohan.linkpilot.ui.ipToLong
import dev.sohan.linkpilot.ui.isIpv4
import dev.sohan.linkpilot.ui.isMac
import dev.sohan.linkpilot.ui.parseWindow
import dev.sohan.linkpilot.ui.relayModeLabel
import dev.sohan.linkpilot.ui.sameSubnet
import dev.sohan.linkpilot.ui.securityLabel
import dev.sohan.linkpilot.ui.signalBars
import dev.sohan.linkpilot.ui.signalLabel
import dev.sohan.linkpilot.ui.stringFromDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    @Test
    fun baseUrlsGetASchemeAndNoTrailingSlash() {
        assertEquals("http://192.168.0.1", normalizeBase("192.168.0.1"))
        assertEquals("http://192.168.0.1", normalizeBase("http://192.168.0.1/"))
        assertEquals("https://router.lan", normalizeBase("  https://router.lan/  "))
        assertEquals("http://192.168.0.1", normalizeBase(""))
    }

    @Test
    fun ipv4RejectsOutOfRangeAndPaddedOctets() {
        assertTrue(isIpv4("192.168.0.1"))
        assertTrue(isIpv4("0.0.0.0"))
        assertTrue(isIpv4("255.255.255.255"))
        assertFalse(isIpv4("192.168.0"))
        assertFalse(isIpv4("192.168.0.256"))
        assertFalse(isIpv4("192.168.00.1"))
        assertFalse(isIpv4("192.168.0.1 "))
        assertFalse(isIpv4(""))
    }

    @Test
    fun subnetComparisonUsesTheMask() {
        assertTrue(sameSubnet("192.168.0.10", "192.168.0.1", "255.255.255.0"))
        assertFalse(sameSubnet("192.168.1.10", "192.168.0.1", "255.255.255.0"))
        assertTrue(sameSubnet("10.0.5.4", "10.0.0.1", "255.255.0.0"))
        assertFalse(sameSubnet("not-an-ip", "10.0.0.1", "255.255.0.0"))
    }

    @Test
    fun ipToLongMatchesTheUsualEncoding() {
        assertEquals(3232235521L, ipToLong("192.168.0.1"))
        assertEquals(0L, ipToLong("0.0.0.0"))
        assertNull(ipToLong("192.168.0.256"))
    }

    @Test
    fun macNeedsSixPairs() {
        assertTrue(isMac("AA:BB:CC:DD:EE:FF"))
        assertTrue(isMac("aa:bb:cc:dd:ee:ff"))
        assertFalse(isMac("AA:BB:CC:DD:EE"))
        assertFalse(isMac("AA-BB-CC-DD-EE-FF"))
    }

    @Test
    fun scheduleWindowNeedsA24HourRange() {
        assertEquals(1380 to 420, parseWindow("23:00-07:00"))
        assertNull(parseWindow("7:00-19:00"))
        assertNull(parseWindow("24:00-01:00"))
        assertNull(parseWindow("12:60-13:00"))
        assertNull(parseWindow("23:00"))
    }

    @Test
    fun dayBitmapRoundTrips() {
        assertEquals(setOf(1, 2, 3, 4, 5), daysFromString("01111100"))
        assertEquals("01111100", stringFromDays(setOf(1, 2, 3, 4, 5)))
        assertEquals(setOf(0), daysFromString("1"))
        assertEquals("01111111", stringFromDays((1..7).toSet()))
    }

    @Test
    fun dayLabelsExplainTheBitmap() {
        assertEquals("Every day", daysLabel("10000000"))
        assertEquals("Mon, Tue, Wed, Thu, Fri", daysLabel("01111100"))
        assertEquals("No days", daysLabel("00000000"))
    }

    @Test
    fun speedFormattingSwitchesUnits() {
        assertEquals("0 KB/s", formatSpeed(0.0))
        assertEquals("5.0 KB/s", formatSpeed(5.0))
        assertEquals("10 KB/s", formatSpeed(10.0))
        assertEquals("1.0 MB/s", formatSpeed(1024.0))
    }

    @Test
    fun sampleWindowUsesWallClockTime() {
        assertEquals("0s", formatSampleWindow(1_000, 1_000))
        assertEquals("2s", formatSampleWindow(1_000, 3_000))
        assertEquals("1m 5s", formatSampleWindow(0, 65_000))
        assertEquals("2h 3m", formatSampleWindow(0, (2 * 3_600 + 3 * 60) * 1_000L))
        assertEquals("0s", formatSampleWindow(5_000, 4_000))
    }

    @Test
    fun uptimeDropsEmptyUnits() {
        assertEquals("—", formatUptime(0))
        assertEquals("0m", formatUptime(59))
        assertEquals("1h 0m", formatUptime(3600))
        assertEquals("1d 1h", formatUptime(90_061))
    }

    @Test
    fun byteFormattingStopsAtGigabytes() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2 KB", formatBytes(2048))
        assertEquals("2.0 MB", formatBytes(2 * 1024 * 1024))
    }

    @Test
    fun signalThresholdsAreInclusive() {
        assertEquals(4, signalBars(-55))
        assertEquals(3, signalBars(-56))
        assertEquals(2, signalBars(-75))
        assertEquals(1, signalBars(-76))
        assertEquals("Excellent", signalLabel(-55))
        assertEquals("Weak", signalLabel(-76))
    }

    @Test
    fun baseHostDropsSchemeAndTrailingSlash() {
        assertEquals("192.168.0.1", baseUrlHost("http://192.168.0.1"))
        assertEquals("router.lan", baseUrlHost("https://router.lan/"))
        assertEquals("192.168.0.1:8080", baseUrlHost("http://192.168.0.1:8080/"))
        assertEquals("", baseUrlHost(""))
    }

    @Test
    fun relayModesUseThePickerLabels() {
        assertEquals("Off", relayModeLabel("disabled"))
        assertEquals("AP", relayModeLabel("ap"))
        assertEquals("WISP", relayModeLabel("wisp"))
        assertEquals("Client+AP", relayModeLabel("client+ap"))
        assertEquals("—", relayModeLabel(""))
        assertEquals("unexpected", relayModeLabel("unexpected"))
    }

    @Test
    fun securityTokensUseThePickerLabels() {
        assertEquals("WPA2", securityLabel("wpa2-psk"))
        assertEquals("Mixed", securityLabel("wpa&wpa2"))
        assertEquals("WPA", securityLabel("wpa-psk"))
        assertEquals("Open", securityLabel("none"))
        assertEquals("—", securityLabel(""))
        assertEquals("future-mode", securityLabel("future-mode"))
    }

    @Test
    fun onlyUpstreamFailureMessagesCountAsFaults() {
        assertFalse(InternetStatus("13103061").upstreamFault)
        assertTrue(InternetStatus("13103071").upstreamFault)
        assertTrue(InternetStatus("13103081").upstreamFault)
        assertTrue(InternetStatus("13103091").upstreamFault)
        assertFalse(InternetStatus("13103031").upstreamFault)
        assertFalse(InternetStatus("").upstreamFault)
    }

    @Test
    fun loginFailuresCarryTheRetryBudget() {
        assertEquals("Incorrect password. Try again.", loginErrorMessage("1"))
        assertEquals(
            "Incorrect password. 4 attempts left before a 3-minute lockout.",
            loginErrorMessage("14"),
        )
        assertEquals(
            "Incorrect password. 1 attempt left before a 3-minute lockout.",
            loginErrorMessage("11"),
        )
        assertEquals(
            "Too many failed attempts. The router is locked for about 3 minutes.",
            loginErrorMessage("10"),
        )
        assertTrue(loginErrorMessage("2").contains("4 admin sessions"))
        assertTrue(loginErrorMessage("3").contains("session limit"))
        assertEquals("Router rejected the change (errCode 42)", loginErrorMessage("42"))
    }

    @Test
    fun macFilterListsAreSeparatedByMode() {
        val entries = listOf(
            MacFilterEntry("pass-device", "", "aa:aa:aa:aa:aa:aa", "pass"),
            MacFilterEntry("block-device", "", "bb:bb:bb:bb:bb:bb", "deny"),
            MacFilterEntry("legacy", "", "cc:cc:cc:cc:cc:cc", ""),
        )
        assertEquals(
            listOf("bb:bb:bb:bb:bb:bb", "cc:cc:cc:cc:cc:cc"),
            entries.forFilterMode("deny", "deny").map { it.mac },
        )
        assertEquals(
            listOf("aa:aa:aa:aa:aa:aa"),
            entries.forFilterMode("pass", "deny").map { it.mac },
        )
        assertEquals(
            listOf("aa:aa:aa:aa:aa:aa", "cc:cc:cc:cc:cc:cc"),
            entries.forFilterMode("pass", "pass").map { it.mac },
        )
    }

    @Test
    fun deviceNamesFollowAliasThenQosThenFilterThenLeaseThenHostname() {
        val mac = "aa:bb:cc:dd:ee:ff"
        assertEquals(
            "Alias",
            resolveDeviceName(mac, "Alias", "QoS", "Filter", "Lease", "host", "10.0.0.2"),
        )
        assertEquals(
            "QoS",
            resolveDeviceName(mac, "", "QoS", "Filter", "Lease", "host", "10.0.0.2"),
        )
        assertEquals(
            "Filter",
            resolveDeviceName(mac, "", "  ", "Filter", "Lease", "host", "10.0.0.2"),
        )
        assertEquals(
            "Lease",
            resolveDeviceName(mac, null, null, "", "Lease", "host", "10.0.0.2"),
        )
        assertEquals(
            "host",
            resolveDeviceName(mac, null, null, null, null, "host", "10.0.0.2"),
        )
        assertEquals(
            "10.0.0.2",
            resolveDeviceName(mac, null, null, null, null, null, "10.0.0.2"),
        )
        assertEquals(mac, resolveDeviceName(mac))
        assertEquals("Trimmed", resolveDeviceName(mac, "  Trimmed  "))
    }
}
