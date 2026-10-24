package dev.sohan.linkpilot

import dev.sohan.linkpilot.ui.BackGestures
import dev.sohan.linkpilot.ui.NavState
import dev.sohan.linkpilot.ui.PageId
import dev.sohan.linkpilot.ui.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackGesturesTest {

    private fun nav() = NavState()

    /** A stack with a pushed page on top, the only shape that previews a gesture. */
    private fun navWithPage(): NavState = nav().apply {
        select(Route.More)
        push(PageId.Admin)
    }

    @Test
    fun homeHasNothingToPreview() {
        val back = BackGestures(nav())

        assertFalse(back.begin())
        assertNull(back.active)
    }

    @Test
    fun aTabBackIsNotPreviewed() {
        val state = nav().apply { select(Route.Devices) }
        val back = BackGestures(state)

        // Tabs animate a transition on commit; there is no finger-tracked overlay.
        assertFalse(back.begin())
        assertNull(back.active)

        state.back()
        assertEquals(Route.Home, state.current)
        assertNull(back.active)
    }

    @Test
    fun beginCapturesTheDestinationWithoutPopping() {
        val state = navWithPage()
        val back = BackGestures(state)

        assertTrue(back.begin())
        assertEquals(Route.Page(PageId.Admin), back.active?.outgoing)
        assertEquals(Route.More, back.active?.incoming)
        assertEquals(Route.Page(PageId.Admin), state.current)
    }

    @Test
    fun commitPopsImmediatelyAndKeepsTheOutgoingFrame() {
        val state = navWithPage()
        val back = BackGestures(state)
        back.begin()

        assertTrue(back.commit())

        // The stack already moved, but the overlay keeps drawing the outgoing screen.
        assertEquals(Route.More, state.current)
        assertTrue(back.active?.committed == true)
        assertEquals(Route.Page(PageId.Admin), back.active?.outgoing)

        assertTrue(back.finishTail())
        assertNull(back.active)
    }

    @Test
    fun aSecondBackDuringTheTailPopsTheNextDestination() {
        val state = nav().apply {
            select(Route.More)
            push(PageId.Admin)
            push(PageId.Audit)
        }
        val back = BackGestures(state)

        back.begin()
        back.commit()
        assertEquals(Route.Page(PageId.Admin), state.current)

        // The next press arrives while the first slide is still running.
        assertTrue(back.active?.committed == true)
        back.drop()
        state.back()
        assertEquals(Route.More, state.current)
        assertNull(back.active)
    }

    @Test
    fun cancelKeepsTheCurrentDestination() {
        val state = navWithPage()
        val back = BackGestures(state)
        back.begin()

        back.drop()

        assertEquals(Route.Page(PageId.Admin), state.current)
        assertEquals(Route.More, state.backTarget())
    }

    @Test
    fun aTabTapDuringTheGestureDropsTheOverlay() {
        val state = navWithPage()
        val back = BackGestures(state)
        back.begin()

        state.select(Route.Monitor)

        assertTrue(back.navChanged())
        assertNull(back.active)
        assertEquals(Route.Monitor, state.current)
    }

    @Test
    fun theCommitItselfIsNotTreatedAsACompetingNavigation() {
        val state = navWithPage()
        val back = BackGestures(state)
        back.begin()

        back.commit()

        assertFalse(back.navChanged())
        assertTrue(back.active?.committed == true)
    }

    @Test
    fun theNextGestureIsIgnoredUntilTheTailIsFinished() {
        val state = nav().apply {
            select(Route.More)
            push(PageId.Admin)
            push(PageId.Audit)
        }
        val back = BackGestures(state)
        back.begin()
        back.commit()
        assertEquals(Route.Page(PageId.Admin), state.current)

        assertFalse(back.begin())
        assertTrue(back.finishTail())
        assertTrue(back.begin())
        assertEquals(Route.Page(PageId.Admin), back.active?.outgoing)
        assertEquals(Route.More, back.active?.incoming)
    }

    @Test
    fun aStaleGestureDoesNotPopASecondTime() {
        val state = navWithPage()
        val back = BackGestures(state)
        back.begin()

        // Something else already navigated to the gesture's destination.
        state.select(Route.Home)
        assertTrue(back.navChanged())

        assertFalse(back.commit())
        assertEquals(Route.Home, state.current)
    }
}
