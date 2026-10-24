package dev.sohan.linkpilot.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A back gesture whose destination has already been resolved by [NavState.backTarget]. */
internal data class BackGesture(
    val outgoing: Route,
    val incoming: Route,
    val committed: Boolean = false,
)

/**
 * Bookkeeping for the in-app predictive-back overlay, which exists only for pushed pages.
 *
 * A tab-level back (single-entry stack) is a plain transition instead: the stack moves on commit
 * and the destination slides in, so there is no finger-tracked preview to book-keep.
 *
 * The stack is popped the moment the system commits a page gesture, not when the slide finishes, so
 * a second back press during the tail still pops the next destination. Rendering keys off [active];
 * the committed flag keeps the outgoing route composed until the slide ends.
 */
internal class BackGestures(private val nav: NavState) {

    var active by mutableStateOf<BackGesture?>(null)
        private set

    /**
     * Captures the destination unless a gesture is already in flight. Only pushed pages are
     * previewed; a tab-level back (a single-entry stack) runs its slide transition on commit.
     */
    fun begin(): Boolean {
        if (active != null || nav.stack.size <= 1) return false
        val target = nav.backTarget() ?: return false
        active = BackGesture(nav.current, target)
        return true
    }

    /**
     * Pops the stack immediately and keeps the outgoing route on screen until the slide ends.
     * Returns false when the gesture is stale; the overlay is dropped either way.
     */
    fun commit(): Boolean {
        val gesture = active ?: return false
        if (gesture.committed || nav.current != gesture.outgoing) {
            active = null
            return false
        }
        nav.back()
        active = gesture.copy(committed = true)
        return true
    }

    /** Clears a committed gesture once it has slid out. Also used before tracking a new one. */
    fun finishTail(): Boolean {
        if (active?.committed != true) return false
        active = null
        return true
    }

    fun drop(): Boolean {
        val had = active != null
        active = null
        return had
    }

    /** Clears the overlay when the stack moved for a reason other than this gesture. */
    fun navChanged(): Boolean {
        val gesture = active ?: return false
        val expected = if (gesture.committed) gesture.incoming else gesture.outgoing
        if (nav.current == expected) return false
        active = null
        return true
    }
}
