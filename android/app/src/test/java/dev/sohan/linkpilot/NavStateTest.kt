package dev.sohan.linkpilot

import dev.sohan.linkpilot.ui.NavState
import dev.sohan.linkpilot.ui.PageId
import dev.sohan.linkpilot.ui.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavStateTest {

    @Test
    fun homeLetsTheSystemFinishTheActivity() {
        val nav = NavState()

        assertNull(nav.backTarget())
        nav.back()
        assertEquals(Route.Home, nav.current)
    }

    @Test
    fun anyOtherTabReturnsToHome() {
        val nav = NavState()
        nav.select(Route.Devices)

        assertEquals(Route.Home, nav.backTarget())
        nav.back()
        assertEquals(Route.Home, nav.current)
        assertNull(nav.backTarget())
    }

    @Test
    fun pagesPopOneAtATimeAndKeepTheirParent() {
        val nav = NavState()
        nav.select(Route.More)
        nav.push(PageId.Admin)
        nav.push(PageId.Audit)

        assertEquals(Route.Page(PageId.Admin), nav.backTarget())
        nav.back()
        assertEquals(Route.Page(PageId.Admin), nav.current)
        assertEquals(Route.More, nav.backTarget())
        nav.back()
        assertEquals(Route.More, nav.current)
        assertEquals(Route.Home, nav.backTarget())
    }

    @Test
    fun selectingATabDropsThePageStack() {
        val nav = NavState()
        nav.select(Route.Wifi)
        nav.push(PageId.Wan)

        nav.select(Route.Devices)

        assertEquals(Route.Devices, nav.current)
        assertEquals(Route.Home, nav.backTarget())
    }
}
