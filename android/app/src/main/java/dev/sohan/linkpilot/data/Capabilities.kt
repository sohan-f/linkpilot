package dev.sohan.linkpilot.data

/**
 * Every router operation this app knows about, with the firmware's own risk profile.
 * Screens read this registry instead of hard-coding assumptions; the capability screen
 * renders it verbatim so unsupported or dangerous actions are never silently hidden.
 */
enum class Risk { READ_ONLY, REVERSIBLE, DISRUPTIVE, DESTRUCTIVE }

data class Operation(
    val id: String,
    val title: String,
    val method: String,
    val path: String,
    val modules: List<String> = emptyList(),
    val risk: Risk = Risk.READ_ONLY,
    val verified: Boolean = true,
    val note: String? = null,
)

object Capabilities {

    const val FIRMWARE = "V12.03.01.27_multi"

    val all: List<Operation> = listOf(
        Operation("auth.token", "Login token", "GET", "/goform/getstok"),
        Operation("auth.login", "Log in", "POST", "/login/Auth", risk = Risk.READ_ONLY,
            note = "Password = md5(base64(utf8 password) + token); no username, no CSRF token."),
        Operation("auth.logout", "Log out", "POST", "/goform/loginOut"),

        Operation("home.read", "Operating mode", "GET", "/goform/getHomePageInfo",
            listOf("loginAuth", "wifiRelay")),
        Operation("status.read", "Dashboard status", "GET", "/goform/getStatus",
            listOf("internetStatus", "deviceStatistics", "systemInfo", "wanAdvCfg", "wifiRelay"),
            note = "deviceStatistics is returned under the key deviceStastics."),

        Operation("wan.read", "WAN and LAN configuration", "GET", "/goform/getWAN",
            listOf("lanCfg", "wanBasicCfg", "wanAdvCfg", "internetStatus")),
        Operation("wan.update", "Change WAN mode or credentials", "POST", "/goform/setWAN",
            listOf("wanBasicCfg"), Risk.DISRUPTIVE,
            note = "Replaces the whole module; can drop the uplink and return errCode 100."),
        Operation("wan.mac", "Clone the WAN MAC address", "POST", "/goform/setMacClone",
            listOf("wanAdvCfg"), Risk.DISRUPTIVE, note = "Takes effect after about 30 seconds."),

        Operation("wifi.read", "Wi-Fi configuration", "GET", "/goform/getWifi",
            listOf("wifiEn", "wifiBasicCfg", "wifiAdvCfg", "wifiPower", "wifiTime", "wifiWPS", "wifiVirSsid")),
        Operation("wifi.enable", "Enable or disable the radio", "POST", "/goform/setWifi",
            listOf("wifiEn"), Risk.DISRUPTIVE),
        Operation("wifi.basic", "Change SSID, password or security", "POST", "/goform/setWifi",
            listOf("wifiBasicCfg"), Risk.DISRUPTIVE, note = "Disconnects every Wi-Fi client."),
        Operation("wifi.advanced", "Channel, mode and bandwidth", "POST", "/goform/setWifi",
            listOf("wifiAdvCfg"), Risk.DISRUPTIVE),
        Operation("wifi.power", "Transmit power", "POST", "/goform/setWifi",
            listOf("wifiPower"), Risk.REVERSIBLE),
        Operation("wifi.schedule", "Wi-Fi on/off schedule", "POST", "/goform/setWifi",
            listOf("wifiTime"), Risk.REVERSIBLE),
        Operation("wifi.wps", "Enable WPS", "POST", "/goform/setWifi",
            listOf("wifiWPS"), Risk.DISRUPTIVE),
        Operation("wifi.guest", "Guest network", "POST", "/goform/setWifi",
            listOf("wifiVirSsid"), Risk.REVERSIBLE),
        Operation("wifi.wps.pbc", "WPS push-button pairing", "POST", "/goform/setWifiWps",
            risk = Risk.REVERSIBLE, note = "Not module-numbered; some builds answer with no errCode."),

        Operation("clients.read", "Clients, limits and MAC filter", "GET", "/goform/getQos",
            listOf("localhost", "onlineList", "blackList", "macFilter")),
        Operation("clients.update", "Rename, block or throttle a client", "POST", "/goform/setQos",
            listOf("qosList"), Risk.REVERSIBLE,
            note = "Full replacement of the 6-field tab row list; 38528 KB/s means unlimited."),

        Operation("relay.read", "Repeater mode and upstream", "GET", "/goform/getWifiRelay",
            listOf("wifiEn", "wifiRelay")),
        Operation("relay.scan", "Scan for upstream networks", "GET", "/goform/getWifiRelay",
            listOf("wifiScan"), note = "Blocks for roughly 5 seconds."),
        Operation("relay.update", "Change operating mode or join a network", "POST", "/goform/setWifiRelay",
            listOf("wifiRelay"), Risk.DISRUPTIVE,
            note = "Any mode change reboots the router; the management address may move."),

        Operation("parental.read", "Parental controls", "GET", "/goform/getParentControl",
            listOf("parentCtrlList", "parentAccessCtrl")),
        Operation("parental.update", "Parental rules", "POST", "/goform/setParentControl",
            listOf("parentCtrlList", "parentAccessCtrl"), Risk.REVERSIBLE,
            note = "Full replacement; at most 10 enabled devices and 32 URL keywords."),

        Operation("ipv6.read", "IPv6 configuration and status", "GET", "/goform/getIPV6",
            listOf("ipv6Enable", "wan6BasicCfg", "lan6Cfg", "ipv6Status")),
        Operation("ipv6.update", "IPv6 configuration", "POST", "/goform/setIPV6",
            listOf("ipv6Enable", "wan6BasicCfg", "lan6Cfg"), Risk.DISRUPTIVE),

        Operation("sys.read", "Administration and system time", "GET", "/goform/getSysTools",
            listOf("loginAuth", "wanAdvCfg", "lanCfg", "softWare", "wifiRelay", "sysTime", "remoteWeb",
                "isWifiClients", "systemInfo")),
        Operation("sys.password", "Change the admin password", "POST", "/goform/setSysTools",
            listOf("loginAuth"), Risk.DISRUPTIVE,
            note = "Password is Base64, not hashed; wrong old password returns errCode 2."),
        Operation("sys.time", "Time zone", "POST", "/goform/setSysTools",
            listOf("sysTime"), Risk.REVERSIBLE),
        Operation("sys.lan", "LAN address and DHCP", "POST", "/goform/setSysTools",
            listOf("lanCfg"), Risk.DISRUPTIVE, note = "Changing lanIP moves the management address."),
        Operation("sys.remote", "Remote web access", "POST", "/goform/setSysTools",
            listOf("remoteWeb"), Risk.DISRUPTIVE, note = "Requires an admin password; exposes the UI to the WAN."),
        Operation("sys.maint", "Nightly auto-maintenance", "POST", "/goform/setSysTools",
            listOf("softWare"), Risk.REVERSIBLE),
        Operation("sys.reboot", "Reboot", "POST", "/goform/sysReboot",
            listOf("sysOperate"), Risk.DISRUPTIVE),
        Operation("sys.restore", "Factory reset", "POST", "/goform/sysRestore",
            listOf("sysOperate"), Risk.DESTRUCTIVE,
            note = "Wipes the admin password and all configuration."),

        Operation("nat.read", "NAT, DHCP reservations and firewall", "GET", "/goform/getNAT",
            listOf("wifiRelay", "staticIPList", "portList", "macFilter", "localhost", "onlineList",
                "ddns", "dmz", "ping", "upnp", "lanCfg", "apIsolation")),
        Operation("nat.static", "DHCP reservations", "POST", "/goform/setNAT",
            listOf("staticIPList"), Risk.REVERSIBLE, note = "Maximum 20 leases."),
        Operation("nat.port", "Port forwarding", "POST", "/goform/setNAT",
            listOf("portList"), Risk.REVERSIBLE, note = "Maximum 16 rules; '~' and ';' encoding."),
        Operation("nat.dmz", "DMZ host", "POST", "/goform/setNAT",
            listOf("dmz"), Risk.DISRUPTIVE, note = "Exposes one host to the WAN."),
        Operation("nat.ddns", "Dynamic DNS", "POST", "/goform/setNAT",
            listOf("ddns"), Risk.REVERSIBLE),
        Operation("nat.upnp", "UPnP", "POST", "/goform/setNAT",
            listOf("upnp"), Risk.DISRUPTIVE),
        Operation("nat.ping", "Respond to WAN ping", "POST", "/goform/setNAT",
            listOf("ping"), Risk.REVERSIBLE),
        Operation("nat.isolation", "AP isolation", "POST", "/goform/setNAT",
            listOf("apIsolation"), Risk.REVERSIBLE),
        Operation("nat.macfilter", "MAC filter", "POST", "/goform/setNAT",
            listOf("macFilter"), Risk.DISRUPTIVE,
            note = "Full replacement of the active list; whitelist mode injects the router itself."),

        Operation("logs.download", "Download system log", "GET", "/cgi-bin/DownloadSyslog/RouterSystem.log",
            risk = Risk.READ_ONLY, verified = false),
        Operation("backup.download", "Download configuration backup", "GET", "/cgi-bin/DownloadCfg/RouterCfm.cfg",
            risk = Risk.READ_ONLY, verified = false),

        Operation("firmware.upgrade", "Upload firmware", "POST", "/cgi-bin/upgrade",
            risk = Risk.DESTRUCTIVE, verified = false,
            note = "Web UI only. Flashing from a phone is deliberately not offered by LinkPilot."),
        Operation("backup.restore", "Restore configuration backup", "POST", "/cgi-bin/UploadCfg",
            risk = Risk.DESTRUCTIVE, verified = false,
            note = "Web UI only. Restores the entire configuration, including the admin password."),
    )

    fun byId(id: String): Operation? = all.firstOrNull { it.id == id }
}
