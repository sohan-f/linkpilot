package dev.sohan.linkpilot.vm

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sohan.linkpilot.data.AppContainer
import dev.sohan.linkpilot.data.AuditEntry
import dev.sohan.linkpilot.data.Client
import dev.sohan.linkpilot.data.ConnectResult
import dev.sohan.linkpilot.data.DiagCheck
import dev.sohan.linkpilot.data.Ipv6Snapshot
import dev.sohan.linkpilot.data.LanConfig
import dev.sohan.linkpilot.data.MacFilterEntry
import dev.sohan.linkpilot.data.MutationOutcome
import dev.sohan.linkpilot.data.NatSnapshot
import dev.sohan.linkpilot.data.ParentalDevice
import dev.sohan.linkpilot.data.ParentalSnapshot
import dev.sohan.linkpilot.data.PortRule
import dev.sohan.linkpilot.data.QosSnapshot
import dev.sohan.linkpilot.data.RelayInfo
import dev.sohan.linkpilot.data.RouterRepository
import dev.sohan.linkpilot.data.ScanNetwork
import dev.sohan.linkpilot.data.SessionExpiredException
import dev.sohan.linkpilot.data.StaticLease
import dev.sohan.linkpilot.data.StatusSnapshot
import dev.sohan.linkpilot.data.SysToolsSnapshot
import dev.sohan.linkpilot.data.ThemeMode
import dev.sohan.linkpilot.data.WanBasic
import dev.sohan.linkpilot.data.WanSnapshot
import dev.sohan.linkpilot.data.WifiSnapshot
import dev.sohan.linkpilot.data.forFilterMode
import dev.sohan.linkpilot.data.normalizeBase
import dev.sohan.linkpilot.data.resolveDeviceName
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SessionPhase { CHECKING, NEEDS_LOCAL_NETWORK, LOGGED_OUT, CONNECTING, READY }

const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

data class TrafficSample(val at: Long, val downKbps: Double, val upKbps: Double)

/** Status poll cadence; device/QoS data refreshes on a slower multiple of this. */
const val STATUS_POLL_SECONDS = 1
private const val QOS_POLL_EVERY_TICKS = 12
private const val SAMPLE_LIMIT = 360
/** Consecutive failed reads before Home marks the snapshot as stale (one per poll second). */
private const val STALE_AFTER_FAILURES = 3

data class UiMessage(val id: Long, val text: String, val good: Boolean)

@Immutable
data class RouterUiState(
    val phase: SessionPhase = SessionPhase.CHECKING,
    val baseUrl: String = "http://192.168.0.1",
    val busy: Boolean = false,
    val initialError: String? = null,
    val status: StatusSnapshot? = null,
    /** Set after repeated status-read failures, so Home can mark the retained snapshot as old. */
    val statusStale: Boolean = false,
    val wan: WanSnapshot? = null,
    val wifi: WifiSnapshot? = null,
    val qos: QosSnapshot? = null,
    val nat: NatSnapshot? = null,
    val parental: ParentalSnapshot? = null,
    val ipv6: Ipv6Snapshot? = null,
    val sys: SysToolsSnapshot? = null,
    val relay: RelayInfo? = null,
    val scan: List<ScanNetwork> = emptyList(),
    val scanning: Boolean = false,
    val samples: List<TrafficSample> = emptyList(),
    val diagnostics: List<DiagCheck>? = null,
    val diagRunning: Boolean = false,
    val message: UiMessage? = null,
    val audit: List<AuditEntry> = emptyList(),
    /** Bumped when the user deliberately drops the session, so the page stack returns Home. */
    val navReset: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val hasSavedPassword: Boolean = false,
    val favorites: Set<String> = emptySet(),
    /** Latest user-assigned name per lowercase MAC, shared by every device list. */
    val aliases: Map<String, String> = emptyMap(),
    val downloadFile: File? = null,
) {
    val ready: Boolean get() = phase == SessionPhase.READY

    /** One display name per MAC across QoS, MAC filter and DHCP reservations. */
    fun deviceName(
        mac: String,
        filterRemark: String = "",
        leaseRemark: String = "",
        hostname: String = "",
        fallback: String = "",
    ): String {
        val key = mac.lowercase()
        val rows = qos?.clients.orEmpty() + qos?.blocked.orEmpty()
        val qosRow = rows.firstOrNull { it.mac.equals(mac, ignoreCase = true) }
        val filterName = filterRemark.ifBlank {
            val matches = nat?.filterEntries.orEmpty()
                .filter { it.mac.equals(mac, ignoreCase = true) && it.remark.isNotBlank() }
            val current = nat?.filterMode.orEmpty()
            (matches.firstOrNull { it.mode == current } ?: matches.firstOrNull())?.remark.orEmpty()
        }
        val leaseName = leaseRemark.ifBlank {
            nat?.leases.orEmpty().firstOrNull { it.mac.equals(mac, ignoreCase = true) }?.remark.orEmpty()
        }
        return resolveDeviceName(
            mac = mac,
            alias = aliases[key],
            qosRemark = qosRow?.remark,
            filterRemark = filterName,
            leaseRemark = leaseName,
            hostname = hostname.ifBlank { qosRow?.hostname.orEmpty() },
            fallback = fallback,
        )
    }

    /** True when allow-only filtering is active and this MAC is permitted to join. */
    fun onAllowList(mac: String): Boolean {
        val nat = nat ?: return false
        if (nat.filterMode != "pass") return false
        return nat.filterEntries.any {
            it.mode.ifBlank { nat.filterMode } == "pass" && it.mac.equals(mac, ignoreCase = true)
        }
    }
}

@Stable
class RouterViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.from(application)
    private val repo: RouterRepository = container.repository
    private val prefs = container.prefs

    private val _ui = MutableStateFlow(
        RouterUiState(
            baseUrl = prefs.baseUrl,
            audit = prefs.audit(),
            themeMode = prefs.themeMode,
            dynamicColor = prefs.dynamicColor,
            hasSavedPassword = repo.hasSavedPassword,
            favorites = prefs.favorites(),
            aliases = prefs.aliasMap(),
        )
    )
    val ui: StateFlow<RouterUiState> = _ui.asStateFlow()

    private var pollJob: Job? = null
    private var sessionJob: Job? = null
    private var foreground = true
    private var tick = 0
    private var statusFailures = 0

    init {
        if (hasLocalNetworkPermission()) {
            startSession()
        } else {
            _ui.update { it.copy(phase = SessionPhase.NEEDS_LOCAL_NETWORK) }
        }
    }

    fun hasLocalNetworkPermission(): Boolean =
        Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(
                getApplication<Application>(),
                LOCAL_NETWORK_PERMISSION,
            ) == PackageManager.PERMISSION_GRANTED

    fun onLocalNetworkPermissionResult(granted: Boolean) {
        if (!granted) {
            _ui.update { it.copy(phase = SessionPhase.NEEDS_LOCAL_NETWORK) }
            return
        }
        if (_ui.value.phase != SessionPhase.NEEDS_LOCAL_NETWORK) return
        _ui.update { it.copy(phase = SessionPhase.CHECKING, initialError = null) }
        startSession()
    }

    private fun startSession() {
        if (sessionJob?.isActive == true) return
        sessionJob = viewModelScope.launch {
            val restored = repo.silentLogin()
            if (!hasLocalNetworkPermission()) {
                _ui.update { it.copy(phase = SessionPhase.NEEDS_LOCAL_NETWORK) }
            } else if (restored) {
                _ui.update { it.copy(phase = SessionPhase.READY) }
                refreshAll()
            } else {
                _ui.update { it.copy(phase = SessionPhase.LOGGED_OUT) }
            }
        }
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (value) {
            if (!hasLocalNetworkPermission()) {
                pollJob?.cancel()
                _ui.update { it.copy(phase = SessionPhase.NEEDS_LOCAL_NETWORK) }
                return
            }
            startPolling()
            // A backgrounded session may have timed out; probe now so recovery happens before a tap.
            if (_ui.value.ready) viewModelScope.launch { reloadStatus() }
        } else {
            pollJob?.cancel()
        }
    }

    fun connect(base: String, password: String, remember: Boolean) {
        if (_ui.value.phase == SessionPhase.CONNECTING) return
        viewModelScope.launch {
            _ui.update { it.copy(phase = SessionPhase.CONNECTING, initialError = null) }
            when (val result = repo.connect(base, password, remember)) {
                ConnectResult.Success -> {
                    _ui.update {
                        it.copy(
                            phase = SessionPhase.READY,
                            baseUrl = repo.baseUrl,
                            hasSavedPassword = repo.hasSavedPassword,
                            message = UiMessage(System.nanoTime(), "Connected to ${repo.baseUrl}", true),
                        )
                    }
                    refreshAll()
                }
                is ConnectResult.WrongPassword -> _ui.update {
                    it.copy(phase = SessionPhase.LOGGED_OUT, initialError = result.message)
                }
                is ConnectResult.Failure -> _ui.update {
                    it.copy(phase = SessionPhase.LOGGED_OUT, initialError = result.message)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.disconnect()
            stopPolling()
            statusFailures = 0
            _ui.update {
                it.copy(
                    phase = SessionPhase.LOGGED_OUT,
                    status = null, statusStale = false, wan = null, wifi = null, qos = null, nat = null,
                    parental = null, ipv6 = null, sys = null, relay = null,
                    scan = emptyList(), samples = emptyList(), diagnostics = null,
                    initialError = null,
                    navReset = it.navReset + 1,
                )
            }
        }
    }

    fun forgetSavedPassword() {
        repo.forgetPassword()
        _ui.update { it.copy(hasSavedPassword = false) }
    }

    /**
     * Explicit restore from the login screen: uses the Keystore password without putting it
     * back into a text field.
     */
    fun restoreSession() {
        if (recovering || _ui.value.phase == SessionPhase.CONNECTING) return
        viewModelScope.launch {
            _ui.update { it.copy(phase = SessionPhase.CONNECTING, initialError = null) }
            if (repo.hasSavedPassword && repo.restoreSession()) {
                _ui.update {
                    it.copy(
                        phase = SessionPhase.READY,
                        baseUrl = repo.baseUrl,
                        hasSavedPassword = repo.hasSavedPassword,
                        message = UiMessage(System.nanoTime(), "Session restored", true),
                    )
                }
                refreshAll()
            } else {
                _ui.update {
                    it.copy(
                        phase = SessionPhase.LOGGED_OUT,
                        initialError = "Saved session could not be restored. Check the address and Wi-Fi, or sign in with the current password.",
                    )
                }
            }
        }
    }

    /**
     * The transport binds its origin at login, so an address change can only be honoured
     * by dropping the session and asking the user to sign in at the new one.
     */
    fun changeBaseUrl(address: String) {
        val normalized = normalizeBase(address)
        prefs.baseUrl = normalized
        viewModelScope.launch {
            runCatching { repo.disconnect() }
            stopPolling()
            statusFailures = 0
            _ui.update {
                it.copy(
                    phase = SessionPhase.LOGGED_OUT,
                    baseUrl = normalized,
                    initialError = "Address saved. Sign in at ${normalized.removePrefix("http://")}.",
                    status = null, statusStale = false, wan = null, wifi = null, qos = null, nat = null,
                    parental = null, ipv6 = null, sys = null, relay = null,
                    scan = emptyList(), samples = emptyList(), diagnostics = null,
                    navReset = it.navReset + 1,
                )
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            statusFailures = 0
            _ui.update { it.copy(busy = true) }
            reloadStatus()
            reloadWifi()
            reloadQos()
            reloadNat()
            reloadWan()
            reloadParental()
            reloadIpv6()
            reloadSys()
            reloadRelay()
            _ui.update { it.copy(busy = false) }
            startPolling()
        }
    }

    fun refreshStatus() = viewModelScope.launch { reloadStatus() }
    fun refreshQos() = viewModelScope.launch { reloadQos() }
    fun refreshWifi() = viewModelScope.launch { reloadWifi() }
    fun refreshNat() = viewModelScope.launch { reloadNat() }
    fun refreshWan() = viewModelScope.launch { reloadWan() }
    fun refreshParental() = viewModelScope.launch { reloadParental() }
    fun refreshIpv6() = viewModelScope.launch { reloadIpv6() }
    fun refreshSys() = viewModelScope.launch { reloadSys() }
    fun refreshRelay() = viewModelScope.launch { reloadRelay() }

    private suspend fun reloadStatus() {
        try {
            applyStatus(repo.loadStatus())
        } catch (e: SessionExpiredException) {
            expired()
        } catch (e: Exception) {
            recordStatusFailure()
            message(e.message ?: "Router did not answer", good = false)
        }
    }

    private fun applyStatus(status: StatusSnapshot) {
        statusFailures = 0
        _ui.update { state ->
            state.copy(
                status = status,
                samples = (state.samples + TrafficSample(
                    status.fetchedAt, status.stats.downKbps, status.stats.upKbps,
                )).takeLast(SAMPLE_LIMIT),
                statusStale = false,
            )
        }
    }

    private fun recordStatusFailure() {
        statusFailures++
        if (statusFailures >= STALE_AFTER_FAILURES) {
            _ui.update { it.copy(statusStale = true) }
        }
    }

    private suspend fun reloadWifi() = guard({ repo.loadWifi() }) { value ->
        _ui.update { it.copy(wifi = value) }
    }

    private suspend fun reloadQos() = guard({ repo.loadQos() }) { value ->
        _ui.update { it.copy(qos = value, audit = prefs.audit()) }
    }

    private suspend fun reloadNat() = guard({ repo.loadNat() }) { value ->
        _ui.update { it.copy(nat = value) }
    }

    private suspend fun reloadWan() = guard({ repo.loadWan() }) { value ->
        _ui.update { it.copy(wan = value) }
    }

    private suspend fun reloadParental() = guard({ repo.loadParental() }) { value ->
        _ui.update { it.copy(parental = value) }
    }

    private suspend fun reloadIpv6() = guard({ repo.loadIpv6() }) { value ->
        _ui.update { it.copy(ipv6 = value) }
    }

    private suspend fun reloadSys() = guard({ repo.loadSysTools() }) { value ->
        _ui.update { it.copy(sys = value) }
    }

    private suspend fun reloadRelay() = guard({ repo.loadRelay() }) { value ->
        _ui.update { it.copy(relay = value) }
    }

    private suspend fun <T> guard(block: suspend () -> T, apply: (T) -> Unit) {
        try {
            apply(block())
        } catch (e: SessionExpiredException) {
            expired()
        } catch (e: Exception) {
            message(e.message ?: "Router did not answer", good = false)
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true || !_ui.value.ready) return
        pollJob = viewModelScope.launch {
            while (true) {
                delay(STATUS_POLL_SECONDS * 1_000L)
                if (_ui.value.phase != SessionPhase.READY || !foreground) continue
                tick++
                try {
                    applyStatus(repo.loadStatus())
                } catch (e: SessionExpiredException) {
                    expired()
                    return@launch
                } catch (_: Exception) {
                    // Keep the last good snapshot; Home flags it once it is a few seconds old.
                    recordStatusFailure()
                }
                if (tick % QOS_POLL_EVERY_TICKS == 0) {
                    runCatching { repo.loadQos() }.onSuccess { value ->
                        _ui.update { it.copy(qos = value) }
                    }.onFailure { if (it is SessionExpiredException) expired() }
                }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private var recovering = false

    /**
     * Sessions expire silently after idle time. With a saved password, re-login and stay on the
     * current screen instead of dropping the user at the Home tab.
     */
    private fun expired() {
        if (recovering) return
        recovering = true
        stopPolling()
        viewModelScope.launch {
            try {
                val restored = repo.hasSavedPassword && repo.restoreSession()
                if (restored) {
                    _ui.update { it.copy(phase = SessionPhase.READY, baseUrl = repo.baseUrl, busy = false) }
                    message("Session refreshed", good = true)
                    refreshAll()
                    if (foreground) startPolling()
                } else {
                    _ui.update {
                        it.copy(
                            phase = SessionPhase.LOGGED_OUT,
                            busy = false,
                            initialError = if (repo.hasSavedPassword) {
                                "Session expired. Restore it with the saved password, or sign in again."
                            } else {
                                "Session expired — sign in again."
                            },
                        )
                    }
                }
            } finally {
                recovering = false
            }
        }
    }

    private suspend fun runMutation(block: suspend () -> MutationOutcome): MutationOutcome {
        _ui.update { it.copy(busy = true) }
        val outcome = try {
            block()
        } finally {
            val audit = withContext(Dispatchers.Default) { prefs.audit() }
            _ui.update { it.copy(busy = false, audit = audit) }
        }
        when (outcome) {
            is MutationOutcome.Applied -> message(
                if (outcome.code == "100") "Accepted — the router is restarting" else "Change applied",
                good = true,
            )
            is MutationOutcome.Rejected -> message(outcome.message, good = false)
            is MutationOutcome.Unreachable ->
                message("Outcome unknown: ${outcome.message.trimEnd('.')}. Re-check before retrying.", good = false)
            MutationOutcome.SessionExpired -> {
                expired()
                message("Session expired — change not confirmed", good = false)
            }
        }
        return outcome
    }

    fun setWifiEnabled(enabled: Boolean) = viewModelScope.launch {
        if (runMutation { repo.setWifiEnabled(enabled) } is MutationOutcome.Applied) reloadWifi()
    }

    fun saveWifiBasic(ssid: String, security: String, password: String, hidden: Boolean) = viewModelScope.launch {
        if (runMutation { repo.setWifiBasic(ssid, security, password, hidden) } is MutationOutcome.Applied) {
            prefs.lastSsid = ssid
            reloadWifi()
            reloadStatus()
        }
    }

    fun saveWifiAdvanced(mode: String, channel: String, bandwidth: String) = viewModelScope.launch {
        if (runMutation { repo.setWifiAdvanced(mode, channel, bandwidth) } is MutationOutcome.Applied) reloadWifi()
    }

    fun setWifiPower(power: String) = viewModelScope.launch {
        if (runMutation { repo.setWifiPower(power) } is MutationOutcome.Applied) reloadWifi()
    }

    fun saveWifiSchedule(enabled: Boolean, window: String, days: String) = viewModelScope.launch {
        if (runMutation { repo.setWifiSchedule(enabled, window, days) } is MutationOutcome.Applied) reloadWifi()
    }

    fun setWpsEnabled(enabled: Boolean) = viewModelScope.launch {
        if (runMutation { repo.setWpsEnabled(enabled) } is MutationOutcome.Applied) reloadWifi()
    }

    fun startWpsPbc() = viewModelScope.launch { runMutation { repo.startWpsPbc() } }

    fun saveGuest(enabled: Boolean, ssid: String, password: String) = viewModelScope.launch {
        if (runMutation { repo.setGuest(enabled, ssid, password) } is MutationOutcome.Applied) reloadWifi()
    }

    fun renameClient(client: Client, name: String) = viewModelScope.launch {
        val all = allClients()
        if (runMutation { repo.renameClient(all, client, name) } is MutationOutcome.Applied) {
            prefs.setAlias(client.mac, name)
            refreshAliases()
            reloadQos()
            reloadParental()
        }
    }

    fun setClientAllowed(client: Client, allowed: Boolean) = viewModelScope.launch {
        val state = _ui.value
        val qos = state.qos
        val nat = state.nat
        val selfIp = qos?.localIp.orEmpty()
        val selfMac = nat?.localMac.orEmpty().ifBlank { qos?.localMac.orEmpty() }
        val isSelf = (selfIp.isNotBlank() && client.ip == selfIp) ||
            (selfMac.isNotBlank() && client.mac.equals(selfMac, ignoreCase = true))
        if (!allowed && isSelf) {
            message("This is the phone running LinkPilot; blocking it would cut off the app", good = false)
            return@launch
        }
        val all = allClients()
        if (all.isEmpty()) {
            message("Client table is empty; refresh devices first", good = false)
            return@launch
        }

        // In allow-only mode the MAC list is the authority: a QoS-only block coexists with a
        // whitelist row and looks contradictory. Keep both in step.
        if (nat == null || nat.filterMode != "pass") {
            if (!allowed && client.allowed && qos != null && qos.blocked.size >= 10) {
                message("The firmware allows at most 10 blocked clients", good = false)
                return@launch
            }
            if (runMutation { repo.setClientAllowed(all, client, allowed) } is MutationOutcome.Applied) {
                reloadQos()
                reloadStatus()
            }
            return@launch
        }

        val pass = nat.filterEntries.forFilterMode("pass", "pass").map { it.copy(mode = "pass") }
        val whitelisted = pass.any { it.mac.equals(client.mac, ignoreCase = true) }

        if (!allowed) {
            if (client.allowed && qos != null && qos.blocked.size >= 10) {
                message("The firmware allows at most 10 blocked clients", good = false)
                return@launch
            }
            val next = if (whitelisted) pass.filterNot { it.mac.equals(client.mac, ignoreCase = true) } else pass
            var changed = false
            val outcome = runMutation {
                if (client.allowed) {
                    val blocked = repo.setClientAllowed(all, client, false)
                    if (blocked !is MutationOutcome.Applied) return@runMutation blocked
                    changed = true
                }
                if (next.size != pass.size) {
                    val filtered = repo.setMacFilter("pass", next, nat.localMac, "This phone")
                    if (filtered == MutationOutcome.SessionExpired) return@runMutation filtered
                    if (filtered !is MutationOutcome.Applied) {
                        return@runMutation MutationOutcome.Rejected(
                            "partial",
                            "Internet blocked, but the allow list was not updated: ${filtered.failureText()}",
                        )
                    }
                    changed = true
                }
                MutationOutcome.Applied(null)
            }
            if (changed) {
                reloadNat()
                reloadQos()
                reloadStatus()
            }
            if (outcome is MutationOutcome.Applied) {
                message(
                    if (whitelisted) "Blocked and removed from the allow list" else "Internet blocked",
                    good = true,
                )
            }
        } else {
            // Restoring in allow-only mode must put the device back on the allow list, otherwise
            // clearing the QoS row alone leaves it unable to associate.
            if (!whitelisted) {
                val phoneThere = nat.localMac.isBlank() ||
                    pass.any { it.mac.equals(nat.localMac, ignoreCase = true) } ||
                    client.mac.equals(nat.localMac, ignoreCase = true)
                val reserved = if (nat.localMac.isNotBlank() && !phoneThere) 1 else 0
                if (pass.size + 1 + reserved > 20) {
                    message("The allow list is full (20 entries, one slot reserved for this phone)", good = false)
                    return@launch
                }
            }
            val name = state.deviceName(client.mac, hostname = client.hostname, fallback = client.ip)
            val remark = name.takeIf {
                !it.equals(client.mac, true) && it != client.ip && !it.equals("Unknown", true)
            }.orEmpty()
            val next = if (whitelisted) pass
            else pass + MacFilterEntry(client.hostname, remark, client.mac, "pass")
            var changed = false
            val outcome = runMutation {
                if (!whitelisted) {
                    val filtered = repo.setMacFilter("pass", next, nat.localMac, "This phone")
                    if (filtered == MutationOutcome.SessionExpired) return@runMutation filtered
                    if (filtered !is MutationOutcome.Applied) return@runMutation filtered
                    changed = true
                }
                if (!client.allowed) {
                    val restored = repo.setClientAllowed(all, client, true)
                    if (restored == MutationOutcome.SessionExpired) return@runMutation restored
                    if (restored !is MutationOutcome.Applied) {
                        return@runMutation MutationOutcome.Rejected(
                            "partial",
                            "Device allowed, but the QoS block was not cleared: ${restored.failureText()}",
                        )
                    }
                    changed = true
                }
                MutationOutcome.Applied(null)
            }
            if (changed) {
                reloadNat()
                reloadQos()
                reloadStatus()
            }
            if (outcome is MutationOutcome.Applied) {
                message(
                    if (whitelisted) "Internet restored" else "Allowed again and added to the allow list",
                    good = true,
                )
            }
        }
    }

    fun setClientLimits(client: Client, downKbps: Int, upKbps: Int) = viewModelScope.launch {
        val all = allClients()
        if (runMutation { repo.setClientLimits(all, client, downKbps, upKbps) } is MutationOutcome.Applied) reloadQos()
    }

    private fun allClients(): List<Client> {
        val qos = _ui.value.qos ?: return emptyList()
        return qos.clients + qos.blocked.filter { blocked -> qos.clients.none { it.mac == blocked.mac } }
    }

    fun saveWan(wan: WanBasic) = viewModelScope.launch {
        if (runMutation { repo.saveWan(wan) } is MutationOutcome.Applied) {
            reloadWan()
            reloadStatus()
        }
    }

    fun saveWanAdvanced(serverName: String, serviceName: String, mtu: String, speed: String, macClone: String, mac: String) =
        viewModelScope.launch {
            if (runMutation { repo.saveWanAdvanced(serverName, serviceName, mtu, speed, macClone, mac) } is MutationOutcome.Applied) {
                reloadWan()
            }
        }

    fun saveRelay(type: String, ssid: String, mac: String, security: String, password: String, channel: String) =
        viewModelScope.launch {
            runMutation { repo.saveRelay(type, ssid, mac, security, password, channel) }
        }

    fun scan() = viewModelScope.launch {
        _ui.update { it.copy(scanning = true) }
        guard({ repo.scan() }) { networks ->
            _ui.update { it.copy(scan = networks.sortedByDescending { n -> n.signal }) }
        }
        _ui.update { it.copy(scanning = false) }
    }

    fun saveParental(devices: List<ParentalDevice>, window: String, days: String, urlMode: String, urls: List<String>) =
        viewModelScope.launch {
            if (runMutation { repo.saveParental(devices, window, days, urlMode, urls) } is MutationOutcome.Applied) {
                reloadParental()
            }
        }

    fun saveIpv6(enabled: Boolean, wan: org.json.JSONObject, lan: org.json.JSONObject) = viewModelScope.launch {
        if (runMutation { repo.saveIpv6(enabled, wan, lan) } is MutationOutcome.Applied) reloadIpv6()
    }

    fun changePassword(old: String, new: String) = viewModelScope.launch {
        runMutation { repo.changePassword(old, new) }
    }

    fun setTimezone(index: Int) = viewModelScope.launch {
        if (runMutation { repo.setTimezone(index) } is MutationOutcome.Applied) reloadSys()
    }

    fun saveLan(lan: LanConfig) = viewModelScope.launch {
        if (runMutation { repo.saveLan(lan) } is MutationOutcome.Applied) {
            reloadWan()
            reloadSys()
        }
    }

    fun saveRemoteWeb(enabled: Boolean, type: String, ip: String, port: String) = viewModelScope.launch {
        if (runMutation { repo.saveRemoteWeb(enabled, type, ip, port) } is MutationOutcome.Applied) reloadSys()
    }

    fun setAutoMaintenance(enabled: Boolean) = viewModelScope.launch {
        if (runMutation { repo.setAutoMaintenance(enabled) } is MutationOutcome.Applied) reloadSys()
    }

    fun reboot() = viewModelScope.launch { runMutation { repo.reboot() } }

    fun factoryReset() = viewModelScope.launch {
        runMutation { repo.factoryReset() }
        stopPolling()
        _ui.update { it.copy(phase = SessionPhase.LOGGED_OUT, initialError = "Factory reset sent. The router is restarting with default settings.") }
    }

    fun setStaticLeases(leases: List<StaticLease>) = viewModelScope.launch {
        val previous = _ui.value.nat?.leases.orEmpty()
        if (runMutation { repo.setStaticLeases(leases) } is MutationOutcome.Applied) {
            syncAliasesFromRenames(
                previous.map { it.mac to it.remark },
                leases.map { it.mac to it.remark },
            )
            refreshAliases()
            reloadNat()
        }
    }

    fun setPortRules(rules: List<PortRule>) = viewModelScope.launch {
        if (runMutation { repo.setPortRules(rules) } is MutationOutcome.Applied) reloadNat()
    }

    fun setDmz(enabled: Boolean, host: String) = viewModelScope.launch {
        if (runMutation { repo.setDmz(enabled, host) } is MutationOutcome.Applied) reloadNat()
    }

    fun setDdns(enabled: Boolean, service: String, server: String, user: String, password: String) = viewModelScope.launch {
        if (runMutation { repo.setDdns(enabled, service, server, user, password) } is MutationOutcome.Applied) reloadNat()
    }

    fun setUpnp(enabled: Boolean) = viewModelScope.launch {
        if (runMutation { repo.setUpnp(enabled) } is MutationOutcome.Applied) reloadNat()
    }

    fun setWanPing(enabled: Boolean) = viewModelScope.launch {
        if (runMutation { repo.setWanPing(enabled) } is MutationOutcome.Applied) reloadNat()
    }

    fun setApIsolation(enabled: Boolean) = viewModelScope.launch {
        if (runMutation { repo.setApIsolation(enabled) } is MutationOutcome.Applied) reloadNat()
    }

    fun setMacFilter(mode: String, entries: List<MacFilterEntry>) = viewModelScope.launch {
        val nat = _ui.value.nat ?: return@launch
        if (mode == "deny" && nat.localMac.isNotBlank() &&
            entries.any { it.mac.equals(nat.localMac, ignoreCase = true) }
        ) {
            message("Remove this phone's MAC before saving a block list", good = false)
            return@launch
        }
        val previous = nat.filterEntries.forFilterMode(mode, nat.filterMode)
        if (runMutation { repo.setMacFilter(mode, entries, nat.localMac, "This phone") } is MutationOutcome.Applied) {
            syncAliasesFromRenames(
                previous.map { it.mac to it.remark },
                entries.map { it.mac to it.remark },
            )
            refreshAliases()
            reloadNat()
            reloadQos()
        }
    }

    fun runDiagnostics() = viewModelScope.launch {
        _ui.update { it.copy(diagRunning = true, diagnostics = null) }
        val results = try {
            repo.diagnostics.run()
        } catch (e: Exception) {
            listOf(DiagCheck("Diagnostics", dev.sohan.linkpilot.data.DiagStatus.FAIL, e.message ?: "Failed", 0))
        }
        _ui.update { it.copy(diagRunning = false, diagnostics = results) }
    }

    fun downloadLog() = download("RouterSystem.log") { repo.downloadLog() }
    fun downloadBackup() = download("RouterCfm.cfg") { repo.downloadBackup() }

    private fun download(name: String, fetch: suspend () -> ByteArray) = viewModelScope.launch {
        try {
            val bytes = fetch()
            val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, name).apply { writeBytes(bytes) }
            _ui.update { it.copy(downloadFile = file) }
        } catch (e: SessionExpiredException) {
            expired()
        } catch (e: Exception) {
            message(e.message ?: "Download failed", good = false)
        }
    }

    fun consumeDownload() = _ui.update { it.copy(downloadFile = null) }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    fun toggleFavorite(mac: String) {
        val added = prefs.toggleFavorite(mac)
        _ui.update { it.copy(favorites = prefs.favorites()) }
        message(if (added) "Added to favourites" else "Removed from favourites", good = true)
    }

    fun clearAudit() {
        prefs.clearAudit()
        _ui.update { it.copy(audit = emptyList()) }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.themeMode = mode
        _ui.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.dynamicColor = enabled
        _ui.update { it.copy(dynamicColor = enabled) }
    }

    private fun message(text: String, good: Boolean) {
        _ui.update { it.copy(message = UiMessage(System.nanoTime(), text, good)) }
    }

    private fun refreshAliases() {
        _ui.update { it.copy(aliases = prefs.aliasMap()) }
    }

    /**
     * Publish renamed rows as shared aliases. MACs that appear more than once on either side are
     * skipped: the router allows duplicate rows (the captured list has some), so row-to-row
     * mapping is ambiguous and guessing would rename the wrong device.
     */
    private fun syncAliasesFromRenames(previous: List<Pair<String, String>>, current: List<Pair<String, String>>) {
        val before = previous.groupBy({ it.first.lowercase() }) { it.second }
        val after = current.groupBy({ it.first.lowercase() }) { it.second }
        after.forEach { (mac, names) ->
            val old = before[mac].orEmpty()
            if (old.size <= 1 && names.size <= 1) {
                val oldName = old.firstOrNull().orEmpty().trim()
                val newName = names.firstOrNull().orEmpty().trim()
                if (newName != oldName) prefs.setAlias(mac, newName)
            }
        }
    }
}

private fun MutationOutcome.failureText(): String = when (this) {
    is MutationOutcome.Rejected -> message
    is MutationOutcome.Unreachable -> message
    MutationOutcome.SessionExpired -> "session expired"
    is MutationOutcome.Applied -> "applied"
}
