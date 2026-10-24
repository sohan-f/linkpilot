package dev.sohan.linkpilot.data

import org.json.JSONObject

data class InternetStatus(val code: String) {
    val family: Char get() = code.getOrNull(1) ?: '?'
    val linkUp: Boolean get() = code.getOrNull(2) == '1'
    val messageId: String get() = if (code.length >= 7) code.substring(3, 7) else ""

    val message: String get() = STATUS_MESSAGES[messageId] ?: "State $code"

    val online: Boolean get() = messageId.endsWith("06")

    val upstreamFault: Boolean
        get() = messageId.endsWith("07") || messageId.endsWith("08") || messageId.endsWith("09")

    val tone: Tone
        get() = when {
            online -> Tone.GOOD
            messageId.endsWith("03") || messageId.endsWith("04") -> Tone.WORKING
            else -> Tone.BAD
        }

    companion object {
        fun from(json: JSONObject) = InternetStatus(json.s("wanConnectStatus"))
    }
}

enum class Tone { GOOD, WORKING, BAD }

data class SystemInfo(
    val wanType: String,
    val wanIp: String,
    val wanMask: String,
    val wanGateway: String,
    val dns1: String,
    val dns2: String,
    val wanMac: String,
    val hostMac: String,
    val lanIp: String,
    val uptimeSeconds: Int,
    val firmware: String,
)

data class DeviceStats(
    val online: Int,
    val blocked: Int,
    val downKbps: Double,
    val upKbps: Double,
    val wifiRate: String,
    val routerName: String,
)

data class WanAdvanced(
    val serverName: String,
    val serviceName: String,
    val wanType: String,
    val mtu: String,
    val mtuCurrent: String,
    val speed: String,
    val speedCurrent: String,
    val macClone: String,
    val macRouter: String,
    val macHost: String,
    val macCurrentWan: String,
    val macWifiDevice: Boolean,
)

data class RelayInfo(
    val type: String,
    val ssid: String,
    val mac: String,
    val channel: String,
    val security: String,
    val password: String,
    val connectStatus: String,
    val duration: String,
)

data class StatusSnapshot(
    val internet: InternetStatus,
    val system: SystemInfo,
    val stats: DeviceStats,
    val wanAdvanced: WanAdvanced,
    val relay: RelayInfo,
    val fetchedAt: Long,
)

data class LanConfig(
    val ip: String,
    val mask: String,
    val dhcpEnabled: Boolean,
    val dhcpStart: String,
    val dhcpEnd: String,
    val dns1: String,
    val dns2: String,
)

data class WanBasic(
    val type: String,
    val pppoeUser: String,
    val pppoePassword: String,
    val ip: String,
    val mask: String,
    val gateway: String,
    val dns1: String,
    val dns2: String,
)

data class WanSnapshot(
    val lan: LanConfig,
    val wan: WanBasic,
    val advanced: WanAdvanced,
    val internet: InternetStatus,
)

data class WifiSnapshot(
    val enabled: Boolean,
    val ssid: String,
    val security: String,
    val password: String,
    val hidden: Boolean,
    val noPassword: Boolean,
    val mode: String,
    val channel: String,
    val channelCurrent: String,
    val bandwidth: String,
    val bandwidthCurrent: String,
    val maxChannel: Int,
    val power: String,
    val powerGear: String,
    val scheduleEnabled: Boolean,
    val scheduleWindow: String,
    val scheduleDays: String,
    val wpsEnabled: Boolean,
    val wpsPin: String,
    val guestEnabled: Boolean,
    val guestSsid: String,
    val guestPassword: String,
    val relayType: String,
)

data class Client(
    val hostname: String,
    val remark: String,
    val ip: String,
    val mac: String,
    val connectType: String,
    val downKbps: Double,
    val upKbps: Double,
    val downLimit: Int,
    val upLimit: Int,
    val allowed: Boolean,
    val blocked: Boolean = false,
) {
    val label: String get() = remark.ifBlank { hostname.ifBlank { ip } }
    val unlimitedDown: Boolean get() = downLimit >= 38401
    val unlimitedUp: Boolean get() = upLimit >= 38401
}

data class MacFilterEntry(
    val hostname: String,
    val remark: String,
    val mac: String,
    val mode: String = "",
)

/** Filter mode keeps the router's combined deny/pass response from mixing the two lists. */
fun List<MacFilterEntry>.forFilterMode(mode: String, fallback: String): List<MacFilterEntry> =
    filter { it.mode.ifBlank { fallback } == mode }

/** Resolve one display name when a device has different labels across router lists. */
fun resolveDeviceName(
    mac: String,
    alias: String? = null,
    qosRemark: String? = null,
    filterRemark: String? = null,
    leaseRemark: String? = null,
    hostname: String? = null,
    fallback: String? = null,
): String = sequenceOf(alias, qosRemark, filterRemark, leaseRemark, hostname, fallback)
    .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
    .firstOrNull()
    ?: mac

data class QosSnapshot(
    val localIp: String,
    val localMac: String,
    val clients: List<Client>,
    val blocked: List<Client>,
    val filterMode: String,
    val filterEntries: List<MacFilterEntry>,
    val fetchedAt: Long,
)

data class StaticLease(val ip: String, val mac: String, val remark: String)
data class PortRule(val ip: String, val internalPort: String, val externalPort: String, val protocol: String)

data class NatSnapshot(
    val leases: List<StaticLease>,
    val ports: List<PortRule>,
    val filterMode: String,
    val filterEntries: List<MacFilterEntry>,
    val localIp: String,
    val localMac: String,
    val ddnsEnabled: Boolean,
    val ddnsService: String,
    val ddnsServer: String,
    val ddnsUser: String,
    val ddnsPassword: String,
    val ddnsStatus: String,
    val dmzEnabled: Boolean,
    val dmzHost: String,
    val wanPing: Boolean,
    val upnp: Boolean,
    val apIsolation: Boolean,
    val clients: List<Client>,
)

data class ParentalDevice(
    val hostname: String,
    val remark: String,
    val mac: String,
    val ip: String,
    val connectSeconds: Int,
    val enabled: Boolean,
) {
    val label: String get() = remark.ifBlank { hostname.ifBlank { ip } }
}

data class ParentalSnapshot(
    val devices: List<ParentalDevice>,
    val window: String,
    val days: String,
    val urlMode: String,
    val urls: List<String>,
)

data class Ipv6Snapshot(
    val enabled: Boolean,
    val wanType: String,
    val wan: JSONObject,
    val lan: JSONObject,
    val conType: String,
    val wanAddr: String,
    val lanAddr: String,
    val gateway: String,
    val preDns: String,
    val altDns: String,
)

data class SysToolsSnapshot(
    val hasPassword: Boolean,
    val firmware: String,
    val autoMaintenance: Boolean,
    val timezoneIndex: Int,
    val currentTime: String,
    val internetState: Boolean,
    val remoteWebEnabled: Boolean,
    val remoteWebType: String,
    val remoteWebIp: String,
    val remoteWebPort: String,
    val isWifiClient: Boolean,
)

data class ScanNetwork(
    val ssid: String,
    val mac: String,
    val channel: String,
    val security: String,
    val signal: Int,
)

object Decoder {

    fun status(root: JSONObject): StatusSnapshot {
        val internet = InternetStatus.from(root.module("internetStatus"))
        val sys = root.module("systemInfo")
        val stats = root.module("deviceStastics")
        val adv = root.module("wanAdvCfg")
        val relay = root.module("wifiRelay")
        return StatusSnapshot(
            internet = internet,
            system = SystemInfo(
                wanType = sys.s("wanType"),
                wanIp = sys.s("statusWanIP"),
                wanMask = sys.s("statusWanMask"),
                wanGateway = sys.s("statusWanGaterway"),
                dns1 = sys.s("statusWanDns1"),
                dns2 = sys.s("statusWanDns2"),
                wanMac = sys.s("statusWanMAC"),
                hostMac = sys.s("macHost"),
                lanIp = sys.s("lanIP"),
                uptimeSeconds = sys.s("wanConnectTime").toIntOrNull() ?: 0,
                firmware = sys.s("softVersion"),
            ),
            stats = DeviceStats(
                online = stats.s("statusOnlineNumber").toIntOrNull() ?: 0,
                blocked = stats.s("statusBlackNum").toIntOrNull() ?: 0,
                downKbps = stats.s("statusDownSpeed").toDoubleOrNull() ?: 0.0,
                upKbps = stats.s("statusUpSpeed").toDoubleOrNull() ?: 0.0,
                wifiRate = stats.s("wifiRate"),
                routerName = stats.s("routerName"),
            ),
            wanAdvanced = wanAdvanced(adv),
            relay = relay(relay),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    fun wanAdvanced(json: JSONObject) = WanAdvanced(
        serverName = json.s("wanServerName"),
        serviceName = json.s("wanServiceName"),
        wanType = json.s("wanType"),
        mtu = json.s("wanMTU"),
        mtuCurrent = json.s("wanMTUCurrent"),
        speed = json.s("wanSpeed"),
        speedCurrent = json.s("wanSpeedCurrent"),
        macClone = json.s("macClone"),
        macRouter = json.s("macRouter"),
        macHost = json.s("macHost"),
        macCurrentWan = json.s("macCurrentWan"),
        macWifiDevice = json.b("macWifiDevice"),
    )

    fun relay(json: JSONObject) = RelayInfo(
        type = json.s("wifiRelayType", "disabled"),
        ssid = json.s("wifiRelaySSID"),
        mac = json.s("wifiRelayMAC"),
        channel = json.s("wifiRelayChannel"),
        security = json.s("wifiRelaySecurityMode"),
        password = json.s("wifiRelayPwd"),
        connectStatus = json.s("wifiRelayConnectStatus"),
        duration = json.s("connectDuration"),
    )

    fun wan(root: JSONObject): WanSnapshot {
        val lan = root.module("lanCfg")
        val wan = root.module("wanBasicCfg")
        return WanSnapshot(
            lan = LanConfig(
                ip = lan.s("lanIP"),
                mask = lan.s("lanMask"),
                dhcpEnabled = lan.b("dhcpEn"),
                dhcpStart = lan.s("lanDhcpStartIP"),
                dhcpEnd = lan.s("lanDhcpEndIP"),
                dns1 = lan.s("lanDns1"),
                dns2 = lan.s("lanDns2"),
            ),
            wan = WanBasic(
                type = wan.s("wanType"),
                pppoeUser = wan.s("wanPPPoEUser"),
                pppoePassword = wan.s("wanPPPoEPwd"),
                ip = wan.s("wanIP"),
                mask = wan.s("wanMask"),
                gateway = wan.s("wanGateway"),
                dns1 = wan.s("wanDns1"),
                dns2 = wan.s("wanDns2"),
            ),
            advanced = wanAdvanced(root.module("wanAdvCfg")),
            internet = InternetStatus.from(root.module("internetStatus")),
        )
    }

    fun wifi(root: JSONObject): WifiSnapshot {
        val en = root.module("wifiEn")
        val basic = root.module("wifiBasicCfg")
        val adv = root.module("wifiAdvCfg")
        val power = root.module("wifiPower")
        val time = root.module("wifiTime")
        val wps = root.module("wifiWPS")
        val guest = root.module("wifiVirSsid")
        return WifiSnapshot(
            enabled = en.b("wifiEn"),
            ssid = basic.s("wifiSSID"),
            security = basic.s("wifiSecurityMode"),
            password = basic.s("wifiPwd"),
            hidden = basic.b("wifiHideSSID"),
            noPassword = basic.b("wifiNoPwd"),
            mode = adv.s("wifiMode"),
            channel = adv.s("wifiChannel"),
            channelCurrent = adv.s("wifiChannelCurrent"),
            bandwidth = adv.s("wifiBandwidth"),
            bandwidthCurrent = adv.s("wifiBandwidthCurrent"),
            maxChannel = adv.s("wifiMaxChannel").toIntOrNull() ?: 13,
            power = power.s("wifiPower"),
            powerGear = power.s("wifiPowerGear"),
            scheduleEnabled = time.b("wifiTimeEn"),
            scheduleWindow = time.s("wifiTimeClose"),
            scheduleDays = time.s("wifiTimeDate"),
            wpsEnabled = wps.b("wpsEn"),
            wpsPin = wps.s("wpsPIN"),
            guestEnabled = guest.s("multiWifiEnable") == "1",
            guestSsid = guest.s("multiWifiSSID"),
            guestPassword = guest.s("multiWifiPwd"),
            relayType = adv.s("wifiRelayType", "disabled"),
        )
    }

    fun qos(root: JSONObject): QosSnapshot {
        val local = root.module("localhost")
        val filter = root.module("macFilter")
        val clients = root.list("onlineList").map { client(it) }
        val blocked = root.list("blackList").map { client(it, blocked = true) }
        return QosSnapshot(
            localIp = local.s("localhost"),
            localMac = local.s("localhostMAC"),
            clients = clients,
            blocked = blocked,
            filterMode = filter.s("curFilterMode", "deny"),
            filterEntries = filter.list("macFilterList").map {
                MacFilterEntry(it.s("hostname"), it.s("remark"), it.s("mac"), it.s("filterMode"))
            },
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private fun client(json: JSONObject, blocked: Boolean = false) = Client(
        hostname = json.s("qosListHostname"),
        remark = json.s("qosListRemark"),
        ip = json.s("qosListIP"),
        mac = json.s("qosListMac"),
        connectType = json.s("qosListConnectType"),
        downKbps = json.s("qosListDownSpeed").toDoubleOrNull() ?: 0.0,
        upKbps = json.s("qosListUpSpeed").toDoubleOrNull() ?: 0.0,
        downLimit = json.s("qosListDownLimit").toDoubleOrNull()?.toInt() ?: 0,
        upLimit = json.s("qosListUpLimit").toDoubleOrNull()?.toInt() ?: 0,
        allowed = json.b("qosListAccess"),
        blocked = blocked,
    )

    fun nat(root: JSONObject): NatSnapshot {
        val filter = root.module("macFilter")
        val ddns = root.module("ddns")
        val dmz = root.module("dmz")
        val local = root.module("localhost")
        return NatSnapshot(
            leases = root.list("staticIPList").map {
                StaticLease(it.s("staticIP"), it.s("staticMac"), it.s("staticRemark"))
            },
            ports = root.list("portList").map {
                PortRule(
                    ip = it.s("portListIntranetIP"),
                    internalPort = it.s("portListIntranetPort"),
                    externalPort = it.s("portListExtranetPort"),
                    protocol = it.s("portListProtocol"),
                )
            },
            filterMode = filter.s("curFilterMode", "deny"),
            filterEntries = filter.list("macFilterList").map {
                MacFilterEntry(it.s("hostname"), it.s("remark"), it.s("mac"), it.s("filterMode"))
            },
            localIp = local.s("localhost"),
            localMac = local.s("localhostMAC"),
            ddnsEnabled = ddns.b("ddnsEn"),
            ddnsService = ddns.s("ddnsServiceName", "oray.com"),
            ddnsServer = ddns.s("ddnsServer"),
            ddnsUser = ddns.s("ddnsUser"),
            ddnsPassword = ddns.s("ddnsPwd"),
            ddnsStatus = ddns.s("ddnsStatus"),
            dmzEnabled = dmz.b("dmzEn"),
            dmzHost = dmz.s("dmzHostIP"),
            wanPing = root.module("ping").b("pingEn"),
            upnp = root.module("upnp").b("upnpEn"),
            apIsolation = root.module("apIsolation").b("apIsolationEn"),
            clients = root.list("onlineList").map { client(it) },
        )
    }

    fun parental(root: JSONObject): ParentalSnapshot {
        val rules = root.module("parentAccessCtrl")
        val urls = rules.optJSONArray("parentCtrlURL")
        return ParentalSnapshot(
            devices = root.list("parentCtrlList").map {
                ParentalDevice(
                    hostname = it.s("parentCtrlHostname"),
                    remark = it.s("parentCtrlRemark"),
                    mac = it.s("parentCtrlMAC"),
                    ip = it.s("parentCtrlIP"),
                    connectSeconds = it.s("parentCtrlConnectTime").toIntOrNull() ?: 0,
                    enabled = it.b("parentCtrlEn"),
                )
            },
            window = rules.s("parentCtrlOnlineTime", "00:00-23:59"),
            days = rules.s("parentCtrlOnlineDate", "11111111"),
            urlMode = rules.s("parentCtrlURLFilterMode", "disable"),
            urls = (0 until (urls?.length() ?: 0)).mapNotNull { urls?.optString(it) },
        )
    }

    fun ipv6(root: JSONObject): Ipv6Snapshot {
        val status = root.module("ipv6Status")
        return Ipv6Snapshot(
            enabled = root.module("ipv6Enable").s("ipv6En") == "1",
            wanType = root.module("wan6BasicCfg").s("ipv6WanType", "0"),
            wan = root.module("wan6BasicCfg"),
            lan = root.module("lan6Cfg"),
            conType = status.s("conType"),
            wanAddr = status.s("wanAddr"),
            lanAddr = status.s("lanAddr"),
            gateway = status.s("gateway"),
            preDns = status.s("preDNS"),
            altDns = status.s("altDNS"),
        )
    }

    fun sysTools(root: JSONObject): SysToolsSnapshot {
        val login = root.module("loginAuth")
        val soft = root.module("softWare")
        val time = root.module("sysTime")
        val remote = root.module("remoteWeb")
        return SysToolsSnapshot(
            hasPassword = login.b("hasLoginPwd"),
            firmware = soft.s("softVersion"),
            autoMaintenance = soft.b("autoMaintenanceEn"),
            timezoneIndex = time.s("sysTimeZone").toIntOrNull() ?: 51,
            currentTime = time.s("sysTimecurrentTime"),
            internetState = time.b("internetState"),
            remoteWebEnabled = remote.b("remoteWebEn"),
            remoteWebType = remote.s("remoteWebType", "any"),
            remoteWebIp = remote.s("remoteWebIP"),
            remoteWebPort = remote.s("remoteWebPort", "80"),
            isWifiClient = root.module("isWifiClients").b("isWifiClients"),
        )
    }

    fun scan(root: JSONObject): List<ScanNetwork> =
        root.list("wifiScan").map {
            ScanNetwork(
                ssid = it.s("wifiScanSSID"),
                mac = it.s("wifiScanMAC"),
                channel = it.s("wifiScanChannel"),
                security = it.s("wifiScanSecurityMode"),
                signal = it.s("wifiScanSignalStrength").toIntOrNull() ?: -100,
            )
        }

    fun home(root: JSONObject): Pair<Boolean, String> =
        root.module("loginAuth").b("hasLoginPwd") to root.module("wifiRelay").s("wifiRelayType", "disabled")
}

/** Message ids from the router's UI. */
private val STATUS_MESSAGES: Map<String, String> = mapOf(
    "0101" to "WAN cable unplugged",
    "0102" to "Disconnected",
    "0103" to "Connecting and detecting the Internet",
    "0104" to "Connected, accessing the Internet",
    "0105" to "Connected but no Internet access",
    "0106" to "Online",
    "0201" to "WAN cable unplugged",
    "0202" to "Disconnected",
    "0203" to "Connecting and detecting the Internet",
    "0204" to "Connected, accessing the Internet",
    "0205" to "Got an IP address but no Internet access",
    "0206" to "Online",
    "0207" to "IP conflict, change the LAN address",
    "0208" to "No response from the ISP server",
    "0302" to "Disconnected",
    "0303" to "Checking the PPPoE credentials",
    "0304" to "Dialled successfully, accessing the Internet",
    "0305" to "Dialled but no Internet access",
    "0306" to "Online",
    "0307" to "PPPoE authentication failed",
    "0308" to "No response from the PPPoE server",
    "1102" to "No bridge yet",
    "1103" to "Bridging",
    "1104" to "Bridged, checking Internet access",
    "1105" to "Connected but no Internet access",
    "1106" to "Online (WISP)",
    "1107" to "Wrong upstream Wi-Fi password",
    "1202" to "No bridge yet",
    "1203" to "Bridging",
    "1204" to "Bridged, checking Internet access",
    "1205" to "Got an IP address but no Internet access",
    "1206" to "Online (WISP)",
    "1207" to "IP conflict, change the LAN address",
    "1208" to "No response from the ISP server",
    "1209" to "Wrong upstream Wi-Fi password",
    "1302" to "No bridge yet",
    "1303" to "Checking the PPPoE credentials",
    "1304" to "Dialled successfully, accessing the Internet",
    "1306" to "Online (WISP)",
    "1307" to "PPPoE authentication failed",
    "2102" to "No bridge yet (repeater)",
    "2103" to "Bridging",
    "2104" to "Bridged successfully",
    "2107" to "Wrong upstream Wi-Fi password",
    "2202" to "No bridge yet (repeater)",
    "2203" to "Bridging",
    "2204" to "Bridged successfully",
    "2209" to "Wrong upstream Wi-Fi password",
    "2302" to "No bridge yet (repeater)",
    "2303" to "Bridging",
    "2304" to "Bridged successfully",
    "2309" to "Wrong upstream Wi-Fi password",
)
