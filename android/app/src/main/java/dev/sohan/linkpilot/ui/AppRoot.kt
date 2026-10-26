@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.sohan.linkpilot.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.annotation.DrawableRes
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.sohan.linkpilot.R
import dev.sohan.linkpilot.ui.components.AppSnackbarHost
import dev.sohan.linkpilot.ui.components.LoadingBlock
import dev.sohan.linkpilot.ui.components.LocalSnackbarHostState
import dev.sohan.linkpilot.ui.components.showAppMessage
import dev.sohan.linkpilot.ui.screens.AboutScreen
import dev.sohan.linkpilot.ui.screens.AdminScreen
import dev.sohan.linkpilot.ui.screens.AuditScreen
import dev.sohan.linkpilot.ui.screens.CapabilityScreen
import dev.sohan.linkpilot.ui.screens.DevicesScreen
import dev.sohan.linkpilot.ui.screens.FirewallScreen
import dev.sohan.linkpilot.ui.screens.HomeScreen
import dev.sohan.linkpilot.ui.screens.Ipv6Screen
import dev.sohan.linkpilot.ui.screens.LanScreen
import dev.sohan.linkpilot.ui.screens.LocalNetworkPermissionScreen
import dev.sohan.linkpilot.ui.screens.LoginScreen
import dev.sohan.linkpilot.ui.screens.MacFilterScreen
import dev.sohan.linkpilot.ui.screens.MonitorScreen
import dev.sohan.linkpilot.ui.screens.MoreScreen
import dev.sohan.linkpilot.ui.screens.ParentalScreen
import dev.sohan.linkpilot.ui.screens.PortForwardScreen
import dev.sohan.linkpilot.ui.screens.RelayScreen
import dev.sohan.linkpilot.ui.screens.StaticLeaseScreen
import dev.sohan.linkpilot.ui.screens.WanScreen
import dev.sohan.linkpilot.ui.screens.WifiScreen
import dev.sohan.linkpilot.ui.theme.LinkPilotTheme
import dev.sohan.linkpilot.vm.RouterUiState
import dev.sohan.linkpilot.vm.RouterViewModel
import dev.sohan.linkpilot.vm.SessionPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed interface Route {
    data object Home : Route
    data object Devices : Route
    data object Wifi : Route
    data object Monitor : Route
    data object More : Route
    data class Page(val page: PageId) : Route
}

enum class PageId(val title: String) {
    Wan("Internet (WAN)"),
    Lan("LAN & DHCP"),
    StaticLease("DHCP reservations"),
    PortForward("Port forwarding"),
    Firewall("Firewall & UPnP"),
    MacFilter("MAC filter"),
    Parental("Parental controls"),
    Ipv6("IPv6"),
    Relay("Wireless repeating"),
    Admin("Administration"),
    Capability("Capability matrix"),
    About("About & connection"),
    Audit("Audit log"),
}

class NavState {
    val stack = mutableStateListOf<Route>(Route.Home)
    val current: Route get() = stack.last()

    fun select(route: Route) {
        if (stack.size == 1 && stack[0] == route) return
        stack.clear()
        stack.add(route)
    }

    fun push(page: PageId) {
        stack.add(Route.Page(page))
    }

    fun reset() {
        stack.clear()
        stack.add(Route.Home)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Where back goes, or null when the system should finish the activity. */
    fun backTarget(): Route? = when {
        stack.size > 1 -> stack[stack.lastIndex - 1]
        stack.singleOrNull() != Route.Home -> Route.Home
        else -> null
    }

    fun back() {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        } else if (stack.singleOrNull() != Route.Home) {
            stack.clear()
            stack.add(Route.Home)
        }
    }
}

private data class Tab(
    val route: Route,
    val label: String,
    @DrawableRes val icon: Int,
    @DrawableRes val iconSelected: Int,
)

@Composable
fun LinkPilotApp(vm: RouterViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Hoisted so an expired session that needs a manual sign-in returns to the same page.
    val nav = remember { NavState() }
    // Shared with copyable InfoRows so both feedback paths use the one shell snackbar.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.navReset) { nav.reset() }

    LinkPilotTheme(mode = ui.themeMode, dynamicColor = ui.dynamicColor) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            when (ui.phase) {
                SessionPhase.CHECKING -> Splash()
                SessionPhase.NEEDS_LOCAL_NETWORK -> LocalNetworkPermissionScreen(ui.baseUrl, vm)
                SessionPhase.LOGGED_OUT, SessionPhase.CONNECTING ->
                    LoginScreen(
                        ui,
                        onConnect = vm::connect,
                        onRestore = vm::restoreSession,
                        onForget = vm::forgetSavedPassword,
                    )
                SessionPhase.READY -> CompositionLocalProvider(
                    LocalSnackbarHostState provides snackbar,
                ) {
                    MainShell(ui, vm, nav, snackbar)
                }
            }
        }
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        LoadingBlock("Restoring session")
    }
}

@Composable
// The destinations own their insets; the Scaffold only positions the snackbar, which is why its
// padding is intentionally not applied to the layers.
@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
private fun MainShell(
    ui: RouterUiState,
    vm: RouterViewModel,
    nav: NavState,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tabs = remember {
        listOf(
            Tab(Route.Home, "Home", R.drawable.ic_home, R.drawable.ic_home_filled),
            Tab(Route.Devices, "Devices", R.drawable.ic_devices, R.drawable.ic_devices_filled),
            Tab(Route.Wifi, "Wi-Fi", R.drawable.ic_wifi, R.drawable.ic_wifi_filled),
            Tab(Route.Monitor, "Monitor", R.drawable.ic_insights, R.drawable.ic_insights_filled),
            Tab(Route.More, "More", R.drawable.ic_more_horiz, R.drawable.ic_more_horiz_filled),
        )
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    // Keep stacked destinations composed to preserve their scroll state.
    val states = rememberSaveableStateHolder()
    val slide = remember { Animatable(0f) }
    val motion = MaterialTheme.motionScheme
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val back = remember(nav) { BackGestures(nav) }
    var backEdge by remember { mutableIntStateOf(NavigationEvent.EDGE_LEFT) }

    // On push, slide the new page in and the underlying page a third of the width left.
    var enterFrom by remember { mutableStateOf<Route?>(null) }
    var enterTo by remember { mutableStateOf<Route?>(null) }
    var enterProgress by remember { mutableFloatStateOf(0f) }
    var enterToken by remember { mutableIntStateOf(0) }

    // Place the snackbar above the tab bar or system navigation bar.
    val density = LocalDensity.current
    val navBarBottom = with(density) { WindowInsets.navigationBars.getBottom(this) }
    val barHeight = remember { mutableIntStateOf(0) }
    val recordBarHeight = remember { { height: Int -> barHeight.intValue = height } }
    val sink = back.active?.incoming ?: nav.current
    val sinkHasBar = sink !is Route.Page

    fun settle(from: Float?, target: Float, after: suspend () -> Unit) {
        settleJob.value?.cancel()
        settleJob.value = scope.launch {
            if (from != null) slide.snapTo(from)
            slide.animateTo(target, motion.defaultSpatialSpec())
            after()
            settleJob.value = null
        }
    }

    fun resetSlide() {
        settleJob.value?.cancel()
        settleJob.value = null
        scope.launch { slide.snapTo(0f) }
    }

    fun cancelEnter() {
        if (enterFrom == null) return
        enterFrom = null
        enterTo = null
        enterProgress = 0f
        enterToken++
    }

    fun openPage(page: PageId) {
        // A tail from the previous back press may still be running; drop it before layering.
        if (back.finishTail()) resetSlide()
        enterFrom = nav.current
        enterTo = Route.Page(page)
        enterProgress = 1f
        enterToken++
        nav.push(page)
    }

    fun closePage() {
        cancelEnter()
        if (back.begin() && back.commit()) {
            settle(0f, 1f) {
                back.finishTail()
                slide.snapTo(0f)
            }
        } else {
            nav.pop()
        }
    }

    LaunchedEffect(enterToken) {
        if (enterFrom == null) return@LaunchedEffect
        Animatable(enterProgress).animateTo(
            targetValue = 0f,
            animationSpec = motion.defaultSpatialSpec(),
        ) { enterProgress = value }
        enterFrom = null
        enterTo = null
        enterProgress = 0f
    }

    LaunchedEffect(navEventState) {
        snapshotFlow { navEventState.transitionState }.collect {
            // Re-read instead of trusting the emission: a progress snapshot dispatched just before
            // the gesture finished or was cancelled must not resurrect a stale overlay.
            val transition = navEventState.transitionState
            if (transition !is NavigationEventTransitionState.InProgress) return@collect
            // The finger takes over from the entry animation.
            cancelEnter()
            // A committed gesture can still be sliding out when the next one starts; drop its
            // tail without snapping so the new gesture owns the first frame it renders.
            if (back.finishTail()) {
                settleJob.value?.cancel()
                settleJob.value = null
                slide.snapTo(0f)
            }
            back.begin()
            if (back.active == null) return@collect
            val event = transition.latestEvent
            if (event.swipeEdge != NavigationEvent.EDGE_NONE) backEdge = event.swipeEdge
            slide.snapTo(event.progress)
        }
    }

    // The callback consumes the gesture, so the platform's own animation is unavailable; the
    // outgoing page follows the finger. Without predictive frames (hardware/three-button back,
    // Android 13 and older) there is nothing to track, so the same slide replays on commit.
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = nav.backTarget() != null,
        onBackCancelled = {
            val gesture = back.active
            if (gesture != null && !gesture.committed) {
                settle(null, 0f) {
                    back.drop()
                    slide.snapTo(0f)
                }
            }
        },
        onBackCompleted = {
            cancelEnter()
            // A fast fling can commit before the progress collector has rendered a frame. The
            // dispatcher only goes idle after this callback, so its last event is still readable.
            var from: Float? = null
            var gesture = back.active
            if (gesture == null) {
                val live =
                    navEventState.transitionState as? NavigationEventTransitionState.InProgress
                val progress = live?.latestEvent?.progress ?: 0f
                if (progress > 0f && back.begin()) {
                    from = progress
                    val edge = live?.latestEvent?.swipeEdge ?: NavigationEvent.EDGE_NONE
                    if (edge != NavigationEvent.EDGE_NONE) backEdge = edge
                    gesture = back.active
                }
            }
            when {
                // A previous gesture is still sliding out; this press belongs to the new top.
                gesture != null && gesture.committed -> {
                    back.drop()
                    resetSlide()
                    nav.back()
                }
                // No predictive frames (hardware/three-button back): slide the page out; a
                // tab-level back has no page to slide and runs its content transition instead.
                gesture == null -> {
                    if (back.begin() && back.commit()) {
                        settle(0f, 1f) {
                            back.finishTail()
                            slide.snapTo(0f)
                        }
                    } else {
                        nav.back()
                    }
                }
                // Pop now so a second press during the tail pops the next destination too.
                back.commit() -> settle(from, 1f) {
                    back.finishTail()
                    slide.snapTo(0f)
                }
                // The stack moved under the gesture; drop the stale overlay.
                else -> {
                    back.drop()
                    resetSlide()
                }
            }
        },
    )

    // A tab tap, a back press or a session reset while the gesture is live wins over the stale
    // preview; the same applies to a page that is still entering.
    LaunchedEffect(nav.current) {
        if (back.navChanged()) resetSlide()
        if (enterTo != null && nav.current != enterTo) cancelEnter()
    }

    LaunchedEffect(ui.message?.id) {
        val message = ui.message ?: return@LaunchedEffect
        snackbar.showAppMessage(message.text, message.good)
        vm.consumeMessage()
    }

    LaunchedEffect(ui.downloadFile) {
        val file = ui.downloadFile ?: return@LaunchedEffect
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Save or send ${file.name}"))
        }
        vm.consumeDownload()
    }

    // Keep stacked pages composed; retain a popped page until its exit animation finishes.
    val outgoing = back.active?.outgoing
    val retiring = back.active?.takeIf { it.committed }?.outgoing
    val entering = enterFrom != null && enterTo == nav.current && back.active == null
    val layers = buildList {
        addAll(nav.stack)
        if (retiring != null && retiring != lastOrNull()) add(retiring)
    }
    val locked = back.active != null || entering

    Scaffold(
        // The root Surface already paints the window; a second full-screen fill only adds overdraw.
        containerColor = Color.Transparent,
        contentWindowInsets = remember(sinkHasBar, barHeight.intValue, navBarBottom) {
            WindowInsets(0, 0, 0, if (sinkHasBar) barHeight.intValue else navBarBottom)
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            layers.forEachIndexed { index, route ->
                val top = index == layers.lastIndex
                RouteLayer(
                    slot = index,
                    states = states,
                    route = route,
                    ui = ui,
                    vm = vm,
                    nav = nav,
                    tabs = tabs,
                    onOpenPage = ::openPage,
                    onBackPage = ::closePage,
                    onBarHeight = recordBarHeight,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = when {
                                // The page the gesture is dismissing follows the finger.
                                route == outgoing && back.active != null -> {
                                    val direction =
                                        if (backEdge == NavigationEvent.EDGE_RIGHT) -1f else 1f
                                    direction * slide.value * size.width
                                }
                                // Move both layers during a push.
                                entering && top -> enterProgress * size.width
                                entering && index == layers.lastIndex - 1 ->
                                    -(1f - enterProgress) * size.width * 0.30f
                                else -> 0f
                            }
                        }
                        .then(if (top && !locked) Modifier else Modifier.blockTouches()),
                )
                if (back.active != null && index == layers.lastIndex - 1) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                // Brighten the destination as the outgoing screen clears it.
                                alpha = (1f - slide.value).coerceIn(0f, 1f) * 0.10f
                            }
                            .background(MaterialTheme.colorScheme.scrim),
                    )
                }
            }
        }
    }
}

/**
 * One destination with the chrome that belongs to it. Layers are full-screen so a page and a tab
 * can sit behind each other during a back gesture without their insets disagreeing.
 */
@Composable
private fun RouteLayer(
    slot: Int,
    states: SaveableStateHolder,
    route: Route,
    ui: RouterUiState,
    vm: RouterViewModel,
    nav: NavState,
    tabs: List<Tab>,
    onOpenPage: (PageId) -> Unit,
    onBackPage: () -> Unit,
    onBarHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    // Both layers are opaque like a real destination window; without this the destination shows
    // through the outgoing screen's transparent gaps and its navigation-bar strip.
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            Modifier
                .weight(1f)
                .then(if (route is Route.Page) Modifier.navigationBarsPadding() else Modifier),
        ) {
            if (route is Route.Page) {
                states.SaveableStateProvider(route.saveKey(slot)) {
                    RouteContent(route, ui, vm, nav, onOpenPage, onBackPage)
                }
            } else {
                // Only the content slides; the bar below belongs to the shell and stays put.
                AnimatedContent(
                    targetState = route,
                    transitionSpec = { tabTransition(initialState, targetState, tabs, motion) },
                    label = "tab",
                ) { tab ->
                    // The tab that slides out is disposed at the end; keep its list where it was.
                    states.SaveableStateProvider(tab.saveKey(slot)) {
                        TabContent(tab, ui, vm, nav, onOpenPage)
                    }
                }
            }
        }
        if (route !is Route.Page) {
            ShortNavigationBar(modifier = Modifier.onSizeChanged { onBarHeight(it.height) }) {
                tabs.forEach { tab ->
                    val selected = route == tab.route
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = { nav.select(tab.route) },
                        icon = {
                            Icon(
                                painterResource(if (selected) tab.iconSelected else tab.icon),
                                contentDescription = null,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    }
}

/** Blocks taps on the destination until the gesture commits. */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/**
 * Peer tab switches slide like a pager, both ways. Pages never take part: forward navigation is
 * its own two-layer push, and the back direction is the gesture-tracked overlay.
 */
private fun tabTransition(
    initial: Route,
    target: Route,
    tabs: List<Tab>,
    motion: MotionScheme,
): ContentTransform {
    val from = tabs.indexOfFirst { it.route == initial }
    val to = tabs.indexOfFirst { it.route == target }
    if (from < 0 || to < 0) return EnterTransition.None togetherWith ExitTransition.None
    val direction = if (to > from) 1 else -1
    // No overshoot: the two layers tile the width exactly, and a bounce would expose the surface.
    val spec = motion.defaultSpatialSpec<IntOffset>()
    return slideInHorizontally(spec) { width -> direction * width } togetherWith
        slideOutHorizontally(spec) { width -> -direction * width }
}

/** Saveable state key for a destination. */
private fun Route.saveKey(slot: Int): String =
    if (this is Route.Page) "$slot:page:${page.name}" else "$slot:tab:$this"

@Composable
private fun RouteContent(
    route: Route,
    ui: RouterUiState,
    vm: RouterViewModel,
    nav: NavState,
    onOpenPage: (PageId) -> Unit,
    onBackPage: () -> Unit,
) {
    if (route is Route.Page) {
        when (route.page) {
            PageId.Wan -> WanScreen(ui, vm, onBackPage)
            PageId.Lan -> LanScreen(ui, vm, onBackPage)
            PageId.StaticLease -> StaticLeaseScreen(ui, vm, onBackPage)
            PageId.PortForward -> PortForwardScreen(ui, vm, onBackPage)
            PageId.Firewall -> FirewallScreen(ui, vm, onBackPage)
            PageId.MacFilter -> MacFilterScreen(ui, vm, onBackPage)
            PageId.Parental -> ParentalScreen(ui, vm, onBackPage)
            PageId.Ipv6 -> Ipv6Screen(ui, vm, onBackPage)
            PageId.Relay -> RelayScreen(ui, vm, onBackPage)
            PageId.Admin ->
                AdminScreen(ui, vm, onBackPage, onOpenAudit = { onOpenPage(PageId.Audit) })
            PageId.Capability -> CapabilityScreen(onBackPage)
            PageId.About ->
                AboutScreen(
                    ui, vm, onBackPage,
                    onOpenCapability = { onOpenPage(PageId.Capability) },
                )
            PageId.Audit -> AuditScreen(ui, vm, onBackPage)
        }
    } else {
        TabContent(route, ui, vm, nav, onOpenPage)
    }
}

@Composable
private fun TabContent(
    route: Route,
    ui: RouterUiState,
    vm: RouterViewModel,
    nav: NavState,
    onOpenPage: (PageId) -> Unit,
) {
    when (route) {
        Route.Home -> HomeScreen(
            ui, vm,
            onOpenWifi = { nav.select(Route.Wifi) },
            onOpenMonitor = { nav.select(Route.Monitor) },
            onOpenDevices = { nav.select(Route.Devices) },
        )
        Route.Devices -> DevicesScreen(ui, vm)
        Route.Wifi -> WifiScreen(ui, vm)
        Route.Monitor -> MonitorScreen(ui, vm)
        Route.More -> MoreScreen(ui, vm, onNavigate = onOpenPage)
        is Route.Page -> Unit
    }
}
