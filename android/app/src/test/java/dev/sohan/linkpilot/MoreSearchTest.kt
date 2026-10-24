package dev.sohan.linkpilot

import dev.sohan.linkpilot.ui.PageId
import dev.sohan.linkpilot.ui.screens.APP_ENTRIES
import dev.sohan.linkpilot.ui.screens.NETWORK_ENTRIES
import dev.sohan.linkpilot.ui.screens.SYSTEM_ENTRIES
import dev.sohan.linkpilot.ui.screens.appearanceMatches
import dev.sohan.linkpilot.ui.screens.filterEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The screen's search must reach every destination it lists, and nothing else. */
class MoreSearchTest {
    @Test
    fun `blank query keeps every entry and the appearance section`() {
        assertEquals(NETWORK_ENTRIES, filterEntries(NETWORK_ENTRIES, ""))
        assertEquals(SYSTEM_ENTRIES, filterEntries(SYSTEM_ENTRIES, "  "))
        assertEquals(APP_ENTRIES, filterEntries(APP_ENTRIES, "\t"))
        assertTrue(appearanceMatches(""))
        assertTrue(appearanceMatches(" "))
    }

    @Test
    fun `appearance section answers every word on its own controls`() {
        for (word in APPEARANCE_WORDS) {
            assertTrue("no match for '$word'", appearanceMatches(word))
        }
        assertFalse(appearanceMatches("port"))
    }

    @Test
    fun `entries match titles and keywords`() {
        assertEquals(listOf(PageId.PortForward), filterEntries(NETWORK_ENTRIES, "port forwarding").map { it.page })
        assertTrue(filterEntries(NETWORK_ENTRIES, "dhcpv6").all { it.page == PageId.Ipv6 })
        assertTrue(filterEntries(APP_ENTRIES, "logout").any { it.page == PageId.About })
        assertEquals(listOf(PageId.Admin), filterEntries(SYSTEM_ENTRIES, "reboot").map { it.page })
    }

    private companion object {
        // Listed literally so trimming the constant can't make this test pass vacuously.
        val APPEARANCE_WORDS =
            listOf("appearance", "theme", "light", "dark", "colour", "color", "dynamic", "wallpaper")
    }
}
