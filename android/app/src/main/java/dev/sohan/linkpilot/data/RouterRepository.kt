package dev.sohan.linkpilot.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface MutationOutcome {
    data class Applied(val code: String?) : MutationOutcome
    data class Rejected(val code: String, val message: String) : MutationOutcome
    data class Unreachable(val message: String) : MutationOutcome
    data object SessionExpired : MutationOutcome
}

sealed interface ConnectResult {
    data object Success : ConnectResult
    data class WrongPassword(val message: String) : ConnectResult
    data class Failure(val message: String) : ConnectResult
}

fun errCodeMessage(code: String): String = when (code) {
    "0", "" -> "Change applied"
    "1" -> "Wrong password"
    "2" -> "Wrong old password"
    "3" -> "Too many attempts — wait a minute"
    "10", "11", "12", "13", "14" -> "Login blocked after repeated failures ($code)"
    "100" -> "Accepted — the router is restarting"
    "201" -> "Firmware image rejected"
    "202" -> "Operation failed"
    "203" -> "Payload too large"
    else -> "Router rejected the change (errCode $code)"
}

/** Converts login codes into messages, including the router's retry count and lockout. */
fun loginErrorMessage(code: String): String {
    val n = code.toIntOrNull()
    return when {
        code == "10" -> "Too many failed attempts. The router is locked for about 3 minutes."
        n != null && n in 11..14 -> {
            val left = n - 10
            "Incorrect password. $left ${if (left == 1) "attempt" else "attempts"} left before a 3-minute lockout."
        }
        code == "1" -> "Incorrect password. Try again."
        code == "2" -> "The router already has 4 admin sessions open. Sign out elsewhere, then retry."
        code == "3" -> "Admin session limit reached. Sign out on another device, then retry."
        else -> errCodeMessage(code)
    }
}

/** Treat firmware's cleared or out-of-range limits as unlimited. */
private fun Int.normalizedLimit(): Int = if (this in 1..38400) this else 38528

/** Router API access. Writes replace a full module, so callers provide its complete state. */
class RouterRepository(
    val api: TendaApi,
    private val prefs: Prefs,
    private val secureStore: SecureStore,
) {

    val diagnostics = Diagnostics(api)

    val baseUrl: String get() = api.baseUrl
    val hasSavedPassword: Boolean get() = secureStore.load() != null

    suspend fun connect(base: String, password: String, remember: Boolean): ConnectResult {
        api.setBase(base)
        return try {
            api.login(password)
            prefs.baseUrl = api.baseUrl
            prefs.rememberPassword = remember
            if (remember) secureStore.save(password) else secureStore.clear()
            ConnectResult.Success
        } catch (e: RouterRejectedException) {
            val code = e.code
            val message = loginErrorMessage(code)
            if (code == "1" || code == "10" || (code.toIntOrNull() ?: 0) in 11..14) {
                ConnectResult.WrongPassword(message)
            } else {
                ConnectResult.Failure(message)
            }
        } catch (e: RouterUnreachableException) {
            ConnectResult.Failure(
                "No response from ${api.baseUrl}. Check that this phone is on the router's Wi-Fi."
            )
        } catch (e: Exception) {
            ConnectResult.Failure(e.message ?: "Could not reach the router")
        }
    }

    suspend fun silentLogin(): Boolean = restoreSession()

    /** Reuses the Keystore password without showing the login screen. */
    suspend fun restoreSession(): Boolean {
        val saved = secureStore.load() ?: return false
        api.setBase(prefs.baseUrl)
        return try {
            api.login(saved)
            prefs.baseUrl = api.baseUrl
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun disconnect() {
        api.logout()
    }

    fun forgetPassword() = secureStore.clear()

    // Decode off the main thread; QoS/NAT payloads are large enough to hitch a poll or tab switch.
    private suspend fun <T> decode(block: suspend () -> T): T = withContext(Dispatchers.Default) { block() }

    suspend fun loadStatus(): StatusSnapshot = decode {
        Decoder.status(fetch("status.read", "getStatus", STATUS_MODULES))
    }

    suspend fun loadWan(): WanSnapshot = decode {
        Decoder.wan(fetch("wan.read", "getWAN", listOf("lanCfg", "wanBasicCfg", "wanAdvCfg", "internetStatus")))
    }

    suspend fun loadWifi(): WifiSnapshot = decode {
        Decoder.wifi(fetch("wifi.read", "getWifi", WIFI_MODULES))
    }

    suspend fun loadQos(): QosSnapshot = decode {
        val snapshot = Decoder.qos(fetch("clients.read", "getQos", listOf("localhost", "onlineList", "blackList", "macFilter")))
        prefs.recordSightings(snapshot.clients)
        snapshot
    }

    suspend fun loadRelay(): RelayInfo = decode {
        Decoder.relay(fetch("relay.read", "getWifiRelay", listOf("wifiEn", "wifiRelay")).module("wifiRelay"))
    }

    suspend fun scan(): List<ScanNetwork> = decode {
        Decoder.scan(fetch("relay.scan", "getWifiRelay", listOf("wifiScan")))
    }

    suspend fun loadParental(): ParentalSnapshot = decode {
        Decoder.parental(fetch("parental.read", "getParentControl", listOf("parentCtrlList", "parentAccessCtrl")))
    }

    suspend fun loadIpv6(): Ipv6Snapshot = decode {
        Decoder.ipv6(fetch("ipv6.read", "getIPV6", listOf("ipv6Enable", "wan6BasicCfg", "lan6Cfg", "ipv6Status")))
    }

    suspend fun loadSysTools(): SysToolsSnapshot = decode {
        Decoder.sysTools(fetch("sys.read", "getSysTools", SYS_MODULES))
    }

    suspend fun loadNat(): NatSnapshot = decode {
        Decoder.nat(fetch("nat.read", "getNAT", NAT_MODULES))
    }

    suspend fun loadHomePage(): Pair<Boolean, String> = decode {
        Decoder.home(fetch("home.read", "getHomePageInfo", listOf("loginAuth", "wifiRelay")))
    }

    private suspend fun fetch(opId: String, form: String, modules: List<String>): JSONObject {
        val op = Capabilities.byId(opId)
        return try {
            api.get(form, modules)
        } catch (e: SessionExpiredException) {
            audit(opId, op?.title ?: form, "Session expired", "EXPIRED")
            throw e
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun setWifiEnabled(enabled: Boolean): MutationOutcome =
        mutate("wifi.enable", "setWifi", listOf("module1" to "wifiEn", "wifiEn" to enabled.toString()),
            if (enabled) "Radio on" else "Radio off")

    suspend fun setWifiBasic(ssid: String, security: String, password: String, hidden: Boolean): MutationOutcome =
        mutate(
            "wifi.basic", "setWifi",
            listOf(
                "module2" to "wifiBasicCfg",
                "wifiSSID" to ssid,
                "wifiSecurityMode" to security,
                "wifiPwd" to password,
                "wifiHideSSID" to hidden.toString(),
            ),
            "SSID \"$ssid\", $security, hidden=$hidden",
        )

    suspend fun setWifiAdvanced(mode: String, channel: String, bandwidth: String): MutationOutcome =
        mutate(
            "wifi.advanced", "setWifi",
            listOf(
                "module5" to "wifiAdvCfg",
                "wifiMode" to mode,
                "wifiChannel" to channel,
                "wifiBandwidth" to bandwidth,
            ),
            "mode=$mode, channel=$channel, bandwidth=$bandwidth",
        )

    suspend fun setWifiPower(power: String): MutationOutcome =
        mutate("wifi.power", "setWifi", listOf("module6" to "wifiPower", "wifiPower" to power), "power=$power")

    suspend fun setWifiSchedule(enabled: Boolean, window: String, days: String): MutationOutcome =
        mutate(
            "wifi.schedule", "setWifi",
            listOf(
                "module3" to "wifiTime",
                "wifiTimeEn" to enabled.toString(),
                "wifiTimeClose" to window,
                "wifiTimeDate" to days,
            ),
            "enabled=$enabled, window=$window, days=$days",
        )

    suspend fun setWpsEnabled(enabled: Boolean): MutationOutcome =
        mutate("wifi.wps", "setWifi", listOf("module4" to "wifiWPS", "wpsEn" to enabled.toString()),
            "WPS enabled=$enabled")

    suspend fun startWpsPbc(): MutationOutcome =
        mutate("wifi.wps.pbc", "setWifiWps", listOf("action" to "pbc"), "WPS push-button started")

    suspend fun setGuest(enabled: Boolean, ssid: String, password: String): MutationOutcome =
        mutate(
            "wifi.guest", "setWifi",
            listOf(
                "module7" to "wifiVirSsid",
                "multiWifiEnable" to if (enabled) "1" else "0",
                "multiWifiSSID" to ssid,
                "multiWifiPwd" to password,
            ),
            "guest=$enabled, SSID \"$ssid\"",
        )

    suspend fun saveQos(clients: List<Client>): MutationOutcome {
        val rows = clients.joinToString("\n") { client ->
            val down = if (client.allowed) client.downLimit.normalizedLimit() else 0
            val up = if (client.allowed) client.upLimit.normalizedLimit() else 0
            listOf(
                client.hostname, client.remark, client.mac,
                up.toString(), down.toString(), client.allowed.toString(),
            ).joinToString("\t")
        }
        return mutate(
            "clients.update", "setQos",
            listOf("module1" to "qosList", "qosList" to rows),
            "${clients.size} client rows",
        )
    }

    suspend fun setClientAllowed(all: List<Client>, target: Client, allowed: Boolean): MutationOutcome {
        val updated = all.map { if (it.mac == target.mac) it.copy(allowed = allowed) else it }
        return saveQos(updated)
    }

    suspend fun setClientLimits(all: List<Client>, target: Client, downKbps: Int, upKbps: Int): MutationOutcome {
        val updated = all.map {
            if (it.mac == target.mac) it.copy(downLimit = downKbps, upLimit = upKbps) else it
        }
        return saveQos(updated)
    }

    suspend fun renameClient(all: List<Client>, target: Client, name: String): MutationOutcome {
        val updated = all.map { if (it.mac == target.mac) it.copy(remark = name.trim()) else it }
        return saveQos(updated)
    }

    suspend fun saveWan(wan: WanBasic): MutationOutcome =
        mutate(
            "wan.update", "setWAN",
            listOf(
                "module1" to "wanBasicCfg",
                "wanType" to wan.type,
                "wanPPPoEUser" to wan.pppoeUser,
                "wanPPPoEPwd" to wan.pppoePassword,
                "wanIP" to wan.ip,
                "wanMask" to wan.mask,
                "wanGateway" to wan.gateway,
                "wanDns1" to wan.dns1,
                "wanDns2" to wan.dns2,
            ),
            "type=${wan.type}, user=${wan.pppoeUser.ifBlank { "—" }}",
        )

    suspend fun saveWanAdvanced(
        serverName: String,
        serviceName: String,
        mtu: String,
        speed: String,
        macClone: String,
        mac: String,
    ): MutationOutcome =
        mutate(
            "wan.mac", "setSysTools",
            listOf(
                "module2" to "wanAdvCfg",
                "wanServerName" to serverName,
                "wanServiceName" to serviceName,
                "wanMTU" to mtu,
                "macClone" to macClone,
                "wanMAC" to mac,
                "wanSpeed" to speed,
            ),
            "MTU=$mtu, speed=$speed, macClone=$macClone",
        )

    suspend fun saveRelay(type: String, ssid: String, mac: String, security: String, password: String, channel: String): MutationOutcome =
        mutate(
            "relay.update", "setWifiRelay",
            listOf(
                "module1" to "wifiRelay",
                "wifiRelayType" to type,
                "wifiRelaySSID" to ssid,
                "wifiRelayMAC" to mac,
                "wifiRelaySecurityMode" to security,
                "wifiRelayPwd" to password,
                "wifiRelayChannel" to channel,
            ),
            "mode=$type, upstream=\"$ssid\"",
        )

    suspend fun saveParental(devices: List<ParentalDevice>, window: String, days: String, urlMode: String, urls: List<String>): MutationOutcome {
        val rows = devices.joinToString("\n") {
            listOf(it.hostname, it.remark, it.mac, it.ip, it.enabled.toString()).joinToString("\t")
        }
        return mutate(
            "parental.update", "setParentControl",
            listOf(
                "module1" to "parentCtrlList",
                "onlineList" to rows,
                "module2" to "parentAccessCtrl",
                "parentCtrlOnlineTime" to window,
                "parentCtrlOnlineDate" to days,
                "parentCtrlURLFilterMode" to urlMode,
                "urlList" to urls.joinToString("\n"),
            ),
            "${devices.count { it.enabled }} governed devices, window=$window, urls=${urls.size}",
        )
    }

    suspend fun saveIpv6(enabled: Boolean, wan: JSONObject, lan: JSONObject): MutationOutcome {
        val fields = mutableListOf("module1" to "ipv6Enable", "ipv6En" to if (enabled) "1" else "0")
        if (enabled) {
            fields += "module2" to "wan6BasicCfg"
            wan.keys().forEach { key -> fields += key to wan.optString(key) }
            fields += "module3" to "lan6Cfg"
            lan.keys().forEach { key -> fields += key to lan.optString(key) }
        }
        return mutate("ipv6.update", "setIPV6", fields, "enabled=$enabled")
    }

    suspend fun changePassword(old: String, new: String): MutationOutcome {
        val fields = mutableListOf("module1" to "loginAuth")
        if (old.isNotBlank()) fields += "oldPwd" to Base64.encodeToString(old.toByteArray(), Base64.NO_WRAP)
        fields += "newPwd" to Base64.encodeToString(new.toByteArray(), Base64.NO_WRAP)
        val outcome = mutate("sys.password", "setSysTools", fields, "admin password changed")
        if (outcome is MutationOutcome.Applied && prefs.rememberPassword) {
            secureStore.save(new)
        }
        return outcome
    }

    suspend fun setTimezone(index: Int): MutationOutcome =
        mutate("sys.time", "setSysTools",
            listOf("module5" to "sysTime", "sysTimeZone" to index.toString()),
            ROUTER_TIMEZONES.getOrElse(index) { "index $index" })

    suspend fun saveLan(lan: LanConfig): MutationOutcome =
        mutate(
            "sys.lan", "setSysTools",
            listOf(
                "module3" to "lanCfg",
                "lanIP" to lan.ip,
                "lanMask" to lan.mask,
                "dhcpEn" to lan.dhcpEnabled.toString(),
                "lanDhcpStartIP" to lan.dhcpStart,
                "lanDhcpEndIP" to lan.dhcpEnd,
                "lanDns1" to lan.dns1,
                "lanDns2" to lan.dns2,
            ),
            "${lan.ip}/${lan.mask}, DHCP=${lan.dhcpEnabled}",
        )

    suspend fun saveRemoteWeb(enabled: Boolean, type: String, ip: String, port: String): MutationOutcome =
        mutate(
            "sys.remote", "setSysTools",
            listOf(
                "module4" to "remoteWeb",
                "remoteWebEn" to enabled.toString(),
                "remoteWebType" to type,
                "remoteWebIP" to ip,
                "remoteWebPort" to port,
            ),
            "enabled=$enabled, type=$type, port=$port",
        )

    suspend fun setAutoMaintenance(enabled: Boolean): MutationOutcome =
        mutate("sys.maint", "setSysTools",
            listOf("module6" to "softWare", "autoMaintenanceEn" to enabled.toString()),
            "nightly auto-reboot=$enabled")

    suspend fun reboot(): MutationOutcome =
        mutate("sys.reboot", "sysReboot", listOf("module1" to "sysOperate", "action" to "reboot"), "reboot requested")

    suspend fun factoryReset(): MutationOutcome =
        mutate("sys.restore", "sysRestore", listOf("module1" to "sysOperate", "action" to "restore"), "factory reset requested")

    suspend fun setStaticLeases(leases: List<StaticLease>): MutationOutcome =
        mutate(
            "nat.static", "setNAT",
            listOf(
                "module1" to "staticIPList",
                "staticList" to leases.joinToString("\n") { "${it.ip}\t${it.mac}\t${it.remark}" },
            ),
            "${leases.size} reservations",
        )

    suspend fun setPortRules(rules: List<PortRule>): MutationOutcome =
        mutate(
            "nat.port", "setNAT",
            listOf(
                "module2" to "portList",
                "portList" to rules.joinToString("~") {
                    "${it.ip};${it.internalPort};${it.externalPort};${it.protocol}"
                },
            ),
            "${rules.size} port rules",
        )

    suspend fun setDmz(enabled: Boolean, host: String): MutationOutcome =
        mutate("nat.dmz", "setNAT",
            listOf("module3" to "dmz", "dmzEn" to enabled.toString(), "dmzHostIP" to host),
            "enabled=$enabled, host=$host")

    suspend fun setDdns(enabled: Boolean, service: String, server: String, user: String, password: String): MutationOutcome =
        mutate(
            "nat.ddns", "setNAT",
            listOf(
                "module4" to "ddns",
                "ddnsEn" to enabled.toString(),
                "ddnsServiceName" to service,
                "ddnsServer" to server,
                "ddnsUser" to user,
                "ddnsPwd" to password,
            ),
            "enabled=$enabled, service=$service, user=$user",
        )

    suspend fun setUpnp(enabled: Boolean): MutationOutcome =
        mutate("nat.upnp", "setNAT", listOf("module5" to "upnp", "upnpEn" to enabled.toString()), "UPnP=$enabled")

    suspend fun setWanPing(enabled: Boolean): MutationOutcome =
        mutate("nat.ping", "setNAT", listOf("module6" to "ping", "pingEn" to enabled.toString()),
            "respond to WAN ping=$enabled")

    suspend fun setApIsolation(enabled: Boolean): MutationOutcome =
        mutate("nat.isolation", "setNAT",
            listOf("module7" to "apIsolation", "apIsolationEn" to enabled.toString()),
            "AP isolation=$enabled")

    suspend fun setMacFilter(mode: String, entries: List<MacFilterEntry>, localMac: String, localHost: String): MutationOutcome {
        // In allow-only mode the phone running LinkPilot must stay listed or the app locks itself out.
        val effective = if (mode == "pass" && entries.none { it.mac.equals(localMac, true) } && localMac.isNotBlank()) {
            entries + MacFilterEntry(localHost.ifBlank { "This phone" }, "LinkPilot", localMac)
        } else {
            entries
        }
        val limit = if (mode == "pass") 20 else 10
        if (effective.size > limit) {
            val why = if (mode == "pass" && entries.size >= limit) {
                "The allow list is full (20 entries); remove one before saving so this phone can be added"
            } else {
                "The ${if (mode == "pass") "allow" else "block"} list allows at most $limit entries"
            }
            return MutationOutcome.Rejected("limit", why)
        }
        return mutate(
            "nat.macfilter", "setNAT",
            listOf(
                "module8" to "macFilter",
                "filterMode" to mode,
                "macFilterList" to effective.joinToString("\n") { "${it.hostname}\t${it.remark}\t${it.mac}" },
            ),
            "mode=$mode, ${effective.size} entries",
        )
    }

    suspend fun downloadLog(): ByteArray = api.download("cgi-bin/DownloadSyslog/RouterSystem.log")
    suspend fun downloadBackup(): ByteArray = api.download("cgi-bin/DownloadCfg/RouterCfm.cfg")

    private suspend fun mutate(
        opId: String,
        form: String,
        fields: List<Pair<String, String>>,
        detail: String,
    ): MutationOutcome {
        val title = Capabilities.byId(opId)?.title ?: form
        return try {
            val code = api.post(form, fields)
            val outcome = when {
                code == null || code == "0" || code == "100" -> MutationOutcome.Applied(code)
                else -> MutationOutcome.Rejected(code, errCodeMessage(code))
            }
            audit(opId, title, detail, if (outcome is MutationOutcome.Applied) "APPLIED" else "REJECTED($code)")
            outcome
        } catch (e: SessionExpiredException) {
            audit(opId, title, detail, "EXPIRED")
            MutationOutcome.SessionExpired
        } catch (e: Exception) {
            // A timeout is not a failure: the router may have applied the write.
            audit(opId, title, detail, "UNKNOWN")
            MutationOutcome.Unreachable(e.message ?: "Connection lost")
        }
    }

    private fun audit(opId: String, title: String, detail: String, outcome: String) =
        prefs.appendAudit(opId, title, detail, outcome)

    companion object {
        val STATUS_MODULES = listOf("internetStatus", "deviceStatistics", "systemInfo", "wanAdvCfg", "wifiRelay")
        val WIFI_MODULES = listOf(
            "wifiEn", "wifiBasicCfg", "wifiAdvCfg", "wifiPower", "wifiTime", "wifiWPS", "wifiVirSsid",
        )
        val SYS_MODULES = listOf(
            "loginAuth", "wanAdvCfg", "lanCfg", "softWare", "wifiRelay", "sysTime", "remoteWeb",
            "isWifiClients", "systemInfo",
        )
        val NAT_MODULES = listOf(
            "wifiRelay", "staticIPList", "portList", "macFilter", "localhost", "onlineList",
            "ddns", "dmz", "ping", "upnp", "lanCfg", "apIsolation",
        )
    }
}
