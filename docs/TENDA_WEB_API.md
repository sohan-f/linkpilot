# Tenda Router — Web API Reference

Reverse-engineered from the router's own web UI (JavaScript bundles) and verified live against the device.

| Item | Value |
|---|---|
| Base URL | `http://192.168.0.1` |
| Web server | `GoAhead-Webs` (HTTP/1.0, plain HTTP only) |
| Firmware | `V12.03.01.27_multi` (reported by `software.softVersion`) |
| UI stack | sea.js + jQuery, static `.html` shells, all logic in `js/*.js` |
| Login | password only — **no username field** |
| Session | `ecos_pw` cookie (opaque token + `:language=xx` suffix) |
| CSRF | none |

Everything the web UI does is two kinds of call:

* `GET  /goform/get<Name>?random=<float>&modules=<comma,separated>`
* `POST /goform/set<Name>?<random>` with `application/x-www-form-urlencoded` body `moduleN=<moduleName>&...`

Setters take a module **number** plus a module **name**; getters take a comma list of names.
There is no REST resource model, no auth header, and no per-field update — a `moduleN` block replaces
that whole module.

---

## 1. Authentication flow

The login page does three steps. The password never leaves the browser in plaintext.

```
GET  /goform/getstok            -> {"random":"a85d14881bdf49da550b9c54f27027b6"}
POST /login/Auth  password=<md5(base64(utf8(password)) + random)>
                                -> 302 Location: /index.html
                                   Set-Cookie: ecos_pw=<token>:language=cn
```

* `base64` is standard Base64 of the UTF-8 bytes of the password (`utf16to8` in the UI).
* The UI fetches a token once per login-page load; fetch a fresh one for every attempt.
* The session lives entirely in the `ecos_pw` cookie. Copy that cookie and you are authenticated.
* Logout: `POST /goform/loginOut` with body `action=loginout`.

```bash
# minimal shell login
BASE=http://192.168.0.1
KEY=$(curl -s $BASE/goform/getstok | python3 -c 'import sys,json;print(json.load(sys.stdin)["random"])')
HASH=$(python3 -c "import hashlib,base64;print(hashlib.md5((base64.b64encode(b'$ADMIN_PASS').decode()+'$KEY').encode()).hexdigest())")
curl -s -c jar -X POST -d "password=$HASH" $BASE/login/Auth
curl -s -b jar "$BASE/goform/getStatus?random=0.1&modules=systemInfo"
```

### Failure behaviour

A failed login is a redirect to `/login.html?<code>`; the UI reads the code from the query string.

| Code | Meaning |
|---|---|
| `1` | Wrong password |
| `2` | "Up to 4 administrators have logged in." (login form still shown) |
| `3` | Admin session limit reached (dedicated "over user" page) |
| `10` | 5 failed attempts — locked for ~3 minutes |
| `11`–`14` | Wrong password, `N = code - 10` attempts left |

The router supports at most **4 concurrent admin sessions**. Session idle timeout was not measured.

> Security note: the device is HTTP-only and the API has no CSRF protection, so any page a LAN
> client visits can drive these endpoints. Do not expose port 80 to the WAN; if you need remote
> access, use the built-in `remoteWeb` feature (also plain HTTP) only behind a tunnel/VPN.

---

## 2. Request/response conventions

| Convention | Detail |
|---|---|
| Cache buster | `random=<float>` on every call. Ignored by the server. |
| `modules` | Comma-separated module names, percent-encoded (`%2C` works). |
| Response | Single JSON object keyed by module name. Requested modules are omitted if unknown. |
| No modules | An empty request returns `{}` — no default payload. |
| Unknown form | GoAhead HTML error: `Form <name> is not defined`. |
| Expired session | `302` to `/login.html` (with `curl` you get an empty body). |
| Numbers | Almost everything is a **string**, including counters and flags. |
| Booleans | Strings `"true"` / `"false"`. |
| Enums | Lowercase tokens (`"pppoe"`, `"wpa2-psk"`, `"bidi"`…), see field tables. |
| Lists | Flattened text: fields separated by `\t`, rows by `\n`. Details in §6. |
| Write result | `{"errCode":"0"}` on success; non-zero otherwise. |

`errCode` values used by the UI:

| errCode | Meaning |
|---|---|
| `0` | Success |
| any other | Generic failure ("Saving failed") |
| `2` | Old admin password incorrect (`setSysTools` / loginAuth) |
| `100` | Applied, router is rebooting — drop the connection and poll for the device |
| `101` | Session invalid — return to login |
| `201` | Firmware image rejected (upgrade) |
| `202` | Upgrade/import failed |
| `203` | Firmware file too large |

**Partial writes are accepted.** The UI sends every module of a page in one POST, but the server
applies only the `moduleN` blocks present. Verified with `setSysTools` (`module5=sysTime`) and
`setNAT` (`module6=ping`) individually. Treat each module as an independent upsert; still send all
fields of a module, because omitted fields are written as empty/default.

---

## 3. Endpoint index

| # | Method | Path | Purpose |
|---|---|---|---|
| 1 | GET | `/goform/getstok` | Login token |
| 2 | POST | `/login/Auth` | Log in |
| 3 | POST | `/goform/loginOut` | Log out |
| 4 | GET | `/goform/getHomePageInfo` | Login state + operating mode |
| 5 | GET | `/goform/getStatus` | Dashboard status |
| 6 | GET | `/goform/getWAN` | WAN + LAN configuration |
| 7 | POST | `/goform/setWAN` | WAN mode/credentials |
| 8 | POST | `/goform/setMacClone` | WAN MAC clone |
| 9 | GET | `/goform/getWifi` | All Wi-Fi settings |
| 10 | POST | `/goform/setWifi` | Wi-Fi enable/basic/schedule/WPS/advanced/power/guest |
| 11 | POST | `/goform/setWifiWps` | WPS push-button |
| 12 | GET | `/goform/getQos` | Clients, bandwidth limits, block list, MAC filter |
| 13 | POST | `/goform/setQos` | Per-client limits / internet access |
| 14 | GET | `/goform/getWifiRelay` | Repeater mode + Wi-Fi scan |
| 15 | POST | `/goform/setWifiRelay` | Operating mode (reboots) |
| 16 | GET | `/goform/getParentControl` | Parental control devices + rules |
| 17 | POST | `/goform/setParentControl` | Parental control |
| 18 | GET | `/goform/getIPV6` | IPv6 config + status |
| 19 | POST | `/goform/setIPV6` | IPv6 config |
| 20 | GET | `/goform/getSysTools` | Admin, LAN, time, remote access, firmware |
| 21 | POST | `/goform/setSysTools` | Same, writable |
| 22 | POST | `/goform/sysReboot` | Reboot |
| 23 | POST | `/goform/sysRestore` | Factory reset |
| 24 | GET | `/goform/getNAT` | Port forward, static DHCP, DMZ, DDNS, UPnP, MAC filter |
| 25 | POST | `/goform/setNAT` | Same, writable |
| 26 | GET | `/cgi-bin/DownloadSyslog/RouterSystem.log` | System log (text) |
| 27 | GET | `/cgi-bin/DownloadCfg/RouterCfm.cfg` | Config backup |
| 28 | POST | `/cgi-bin/upgrade` | Firmware upload (multipart) |
| 29 | POST | `/cgi-bin/UploadCfg` | Config restore (multipart) |
| 30 | GET | `/cgi-bin/DownloadFlash` | Full 2 MiB flash dump (firmware + NVRAM) |

---

## 4. Read endpoints

### 4.1 `GET /goform/getHomePageInfo`

Used by the UI shell on load to decide which menu items to show.

`modules=loginAuth,wifiRelay`

```json
{
  "loginAuth": { "hasLoginPwd": "true", "username ": "admin" },
  "wifiRelay": { "wifiRelayType": "disabled", "...": "..." }
}
```

| Field | Notes |
|---|---|
| `hasLoginPwd` | `"true"` once an admin password exists; drives the logout button |
| `username ` | Note the **trailing space in the key**. Always `admin`; unused by login |
| `wifiRelayType` | `disabled` \| `ap` \| `wisp` \| `client+ap` — determines the whole UI mode |

### 4.2 `GET /goform/getStatus`

The dashboard. Polled every 3 s by the UI.

`modules=internetStatus,deviceStatistics,systemInfo,wanAdvCfg,wifiRelay`

> Quirk: request `deviceStatistics`, the response key is **`deviceStastics`** (typo in firmware).
> Sending `deviceStastics` in the request returns `{}`.

`internetStatus`

| Field | Notes |
|---|---|
| `wanConnectStatus` | 8-digit string, see §5 |
| `lanWanIPConflict` | `"true"` when LAN and WAN subnets collide |
| `newLanIP` | Replacement LAN IP when a conflict is resolved, else `255.255.255.255` |

`deviceStastics`

| Field | Notes |
|---|---|
| `statusOnlineNumber` | Connected clients |
| `statusBlackNum` | Blocked clients |
| `statusUpSpeed` / `statusDownSpeed` | KB/s, decimal string |
| `wifiRate` | Link rate |
| `routerName` | Device name |
| `extendName` | Repeater/extender alias |

`systemInfo`

| Field | Notes |
|---|---|
| `wanType` | `dhcp` \| `pppoe` \| `static` |
| `wanConnectTime` | Uptime seconds of the WAN link |
| `statusWanIP` / `statusWanMask` / `statusWanGaterway` | Current WAN address (**`Gaterway`** is misspelled in firmware) |
| `statusWanDns1` / `statusWanDns2` | Negotiated DNS |
| `statusWanMAC` / `macHost` | WAN MAC, and the LAN host's MAC |
| `lanIP` | Router LAN address |
| `softVersion` | Firmware string |

`wanAdvCfg` — see 4.3. `wifiRelay` — see 4.6.

### 4.3 `GET /goform/getWAN`

`modules=lanCfg,wanBasicCfg,wanAdvCfg,internetStatus`

`lanCfg`

| Field | Notes |
|---|---|
| `lanIP`, `lanMask` | Router address and subnet mask |
| `dhcpEn` | `"true"` / `"false"` |
| `lanDhcpStartIP`, `lanDhcpEndIP` | Pool bounds, full dotted quad |
| `lanDns1`, `lanDns2` | DNS handed to clients |

`wanBasicCfg`

| Field | Notes |
|---|---|
| `wanType` | `dhcp` \| `pppoe` \| `static` |
| `wanPPPoEUser`, `wanPPPoEPwd` | **Returned in plaintext** even when `wanType` is `dhcp` |
| `wanIP`, `wanMask`, `wanGateway`, `wanDns1`, `wanDns2` | Static-mode values; empty otherwise |

`wanAdvCfg`

| Field | Notes |
|---|---|
| `wanMTU`, `wanMTUCurrent` | Configured and effective MTU |
| `wanSpeed`, `wanSpeedCurrent` | `Auto` / `10` / `100`, and the negotiated one |
| `macClone` | `default` \| `clone` \| `manual` |
| `macRouter`, `macHost`, `macCurrentWan` | Factory WAN MAC, host NIC MAC, MAC in use |
| `macWifiDevice` | `"true"` when clients arrive over Wi-Fi (hides host cloning) |
| `wanServerName`, `wanServiceName` | PPPoE service/concentrator overrides, `""` = auto |

### 4.4 `GET /goform/getWifi`

`modules=wifiEn,wifiBasicCfg,wifiAdvCfg,wifiPower,wifiTime,wifiWPS,wifiVirSsid`

| Module | Fields |
|---|---|
| `wifiEn` | `wifiEn` |
| `wifiBasicCfg` | `wifiSSID`, `wifiSecurityMode`, `wifiPwd` (plaintext), `wifiHideSSID`, `wifiNoPwd` |
| `wifiAdvCfg` | `wifiMode` (`bgn`\|`bg`\|`g`\|`b`), `wifiChannel` (`auto`\|`1`..`13`), `wifiChannelCurrent`, `wifiBandwidth` (`auto`\|`20`\|`40`), `wifiBandwidthCurrent`, `wifiMaxChannel`, `wifiRelayType` |
| `wifiPower` | `wifiPower` (`low`\|`normal`\|`high`), `wifiPowerGear` (`hide_normal`\|`hide_power`) |
| `wifiTime` | `wifiTimeEn`, `wifiTimeClose` (`HH:MM-HH:MM`), `wifiTimeDate` (8 chars), `wifiRelayType` |
| `wifiWPS` | `wpsEn`, `wpsPIN` |
| `wifiVirSsid` | `multiWifiEnable` (`"0"`/`"1"`), `multiWifiSSID`, `multiWifiPwd` |

`wifiSecurityMode`: `none`, `wpa-psk`, `wpa2-psk`, `wpa&wpa2` (mixed). The ampersand must be
URL-encoded on the wire — use a form encoder, not string concatenation.

`wifiTimeDate` is 8 characters, index `0` = "Everyday", `1..7` = Mon..Sun, `1` = on:
`"01111100"` = Mon–Fri.

### 4.5 `GET /goform/getQos`

Clients, bandwidth rules and the MAC filter. Polled every 5 s by the UI.

`modules=localhost,onlineList,blackList,macFilter`

`localhost`

| Field | Notes |
|---|---|
| `localhost` | IP of the requesting client |
| `localhostMAC` | Only on `getNAT`; absent here |
| `lanMask` | LAN mask |

`onlineList[]` — one row per connected client

| Field | Notes |
|---|---|
| `qosListHostname` | DHCP hostname or `Unknown` |
| `qosListRemark` | User-defined name (wins over hostname in the UI) |
| `qosListIP` | IPv4 |
| `qosListMac` | Lowercase, colon-separated |
| `qosListConnectType` | `wifi` \| `wires` |
| `qosListDownSpeed` / `qosListUpSpeed` | Live KB/s, `"0.00"` when idle |
| `qosListDownLimit` / `qosListUpLimit` | Current limit; anything `>= 38401` means unlimited |
| `qosListAccess` | `"true"` = internet allowed |

`blackList[]` — blocked clients; same row shape as `onlineList` (empty in the captured sample).

`macFilter`

| Field | Notes |
|---|---|
| `curFilterMode` | `deny` \| `pass` |
| `macFilterList[]` | `{hostname, remark, mac, filterMode}` |

### 4.6 `GET /goform/getWifiRelay`

`modules=wifiEn,wifiRelay` for mode/config; `modules=wifiScan` alone for an AP scan (takes ~5 s).

`wifiRelay`

| Field | Notes |
|---|---|
| `wifiRelayType` | `disabled` \| `ap` \| `wisp` \| `client+ap` |
| `wifiRelaySSID`, `wifiRelayMAC`, `wifiRelayChannel` | Upstream AP identity |
| `wifiRelaySecurityMode` | Upstream encryption (`none` for open) |
| `wifiRelayPwd` | Upstream PSK, plaintext |
| `wifiRelayConnectStatus` | Observed as integer `1` in `disabled` mode; the UI matches strings `disconnect` / `bridgeSuccess` / `pwdError` in bridge modes. **Type varies — do not assume string.** |
| `connectDuration` | Seconds bridged |

`wifiScan[]`

| Field | Notes |
|---|---|
| `wifiScanSSID` | SSID (empty for hidden networks) |
| `wifiScanMAC` | BSSID |
| `wifiScanChannel` | Channel number |
| `wifiScanSecurityMode` | `NONE`, `WPAWPA2/AES`, … |
| `wifiScanSignalStrength` | dBm, negative (`"-84"`); stronger = closer to `0` |

### 4.7 `GET /goform/getParentControl`

`modules=parentCtrlList,parentAccessCtrl`

`parentCtrlList[]`

| Field | Notes |
|---|---|
| `parentCtrlHostname` | Hostname |
| `parentCtrlRemark` | User-defined name |
| `parentCtrlMAC`, `parentCtrlIP` | Identity |
| `parentCtrlConnectTime` | Seconds online (integer) |
| `parentCtrlEn` | `"true"` = parental rules apply to this device |

`parentAccessCtrl`

| Field | Notes |
|---|---|
| `parentCtrlOnlineTime` | Allowed window `HH:MM-HH:MM` |
| `parentCtrlOnlineDate` | 8 chars, same layout as `wifiTimeDate` |
| `parentCtrlURLFilterMode` | `disable` \| `permit` (allow list) \| `forbid` (block list) |
| `parentCtrlURL` | Array of domain keywords |

### 4.8 `GET /goform/getIPV6`

`modules=ipv6Enable,wan6BasicCfg,lan6Cfg,ipv6Status`

`ipv6Enable` — `ipv6En` (`"0"`/`"1"`, string).

`wan6BasicCfg` — `ipv6WanType` (`"0"` DHCPv6, `"1"` static, `"2"` PPPoE) plus the fields for that
mode: `ipv6WanPPPoEUser`, `ipv6WanPPPoEPwd`, `ipv6PPPoeDelegation`, `ipv6PPPoeTemporary`,
`ipv6DhcpDelegation`, `ipv6DhcpTemporary`, `ipv6WanIP`, `ipv6IpPlen`, `ipv6WanGateway`,
`ipv6WanDns1`, `ipv6WanDns2`.

`lan6Cfg` — `ipv6LanType`, `ipv6LanIP`, `ipv6LanIPLen`, `ipv6LanDhcpPrefixType`, `ipv6LanDhcpPrefix`,
`ipv6LanDhcpPrefixLen`, `ipv6LanDhcpEn`, `ipv6LanDhcpType`, `ipv6LanDhcpStartID`, `ipv6LanDhcpEndID`,
`ipv6LanDnsType`, `ipv6LanDns1`, `ipv6LanDns2`. Unused fields are empty strings.

`ipv6Status` — `conType` (e.g. `PPPoEv6`), `wanAddr`, `lanAddr` (newline-separated when a prefix has
multiple addresses), `gateway`, `preDNS`, `altDNS`.

### 4.9 `GET /goform/getSysTools`

`modules=loginAuth,wanAdvCfg,lanCfg,softWare,wifiRelay,sysTime,remoteWeb,isWifiClients,systemInfo`

| Module | Fields |
|---|---|
| `loginAuth` | `hasLoginPwd`, `username ` (space in key) |
| `wanAdvCfg` | As in 4.3 |
| `lanCfg` | As in 4.3 |
| `softWare` | `softVersion`, `autoMaintenanceEn` (nightly auto-reboot) |
| `sysTime` | `sysTimeZone` (index into 75-entry list; `51` = GMT+06:00 Astana/Dhaka), `sysTimecurrentTime`, `sysTimeSntpType`, `internetState` |
| `remoteWeb` | `remoteWebEn`, `remoteWebType` (`any`\|`specified`), `remoteWebIP`, `remoteWebPort` |
| `isWifiClients` | `"true"` when the current client is on Wi-Fi |
| `wifiRelay`, `systemInfo` | As above |

### 4.10 `GET /goform/getNAT`

`modules=wifiRelay,staticIPList,portList,macFilter,localhost,onlineList,ddns,dmz,ping,upnp,lanCfg,apIsolation`

| Module | Fields |
|---|---|
| `staticIPList[]` | `staticIP`, `staticMac`, `staticRemark` |
| `portList[]` | `portListIntranetIP`, `portListIntranetPort`, `portListExtranetPort`, `portListProtocol` (`tcp`\|`udp`\|`both`) |
| `macFilter` | As in 4.5 |
| `localhost` | `localhost`, `localhostMAC`, `lanMask` |
| `onlineList[]` | As in 4.5 |
| `ddns` | `ddnsEn`, `ddnsServiceName` (e.g. `oray.com`), `ddnsUser`, `ddnsPwd`, `ddnsServer`, `ddnsStatus` (`Connected`\|`Connectting`\|`Disconnected` — spelling is firmware's) |
| `dmz` | `dmzEn`, `dmzHostIP` |
| `ping` | `pingEn` — respond to WAN ping |
| `upnp` | `upnpEn` |
| `apIsolation` | `apIsolationEn` |
| `lanCfg`, `wifiRelay` | As above |

---

## 5. `wanConnectStatus` decoding

An 8-character string: `<x><type><link><msg4>`

* index `2` — WAN link: `1` = cable up, `0` = down
* chars `3..6` — message id, from `common.js`
* index `1` — message family: `2` DHCP, `3` PPPoE; `1` is used for states the firmware does not
  attribute to a WAN mode (inferred from the message table, not confirmed on a static WAN)

Observed on PPPoE: `"13103061"` → link up, message `0306` = "You can surf the Internet".

Message ids (from the UI's translation table):

| Prefix | Family | Example ids |
|---|---|---|
| `01xx` | generic | `0101` unplugged, `0102` disconnected, `0103` connecting, `0104` connected, `0105` no internet, `0106` online |
| `02xx` | DHCP | same sequence, plus `0205` (got IP, no internet → suggests MAC clone), `0207` IP conflict, `0208` no server response |
| `03xx` | PPPoE | `0303` authenticating, `0304` dialled, `0306` online, `0307` bad credentials, `0308` no response |
| `11xx`/`12xx`/`13xx` | WISP | `xx02` no bridge, `xx03` bridging, `xx04` bridged, `xx06` online, `xx07` wrong upstream password, `xx09` IP conflict |
| `21xx`–`23xx` | Universal repeater | `xx02`..`xx04`, `xx07`/`xx09` wrong upstream password |

---

## 6. Write endpoints

Each setter is a full module replacement. `moduleN` numbers are fixed by the firmware and must match
the module name; sending the wrong number for a name silently does nothing useful.

### 6.1 `POST /goform/setWAN`

| Module | Params |
|---|---|
| `module1=wanBasicCfg` | `wanType`, `wanPPPoEUser`, `wanPPPoEPwd`, `wanIP`, `wanMask`, `wanGateway`, `wanDns1`, `wanDns2` |

Send all eight; unused ones as empty strings (the UI does exactly that). Changing WAN settings can
drop the uplink and may return `errCode 100`.

### 6.2 `POST /goform/setMacClone`

`module1=wanAdvCfg&macClone=clone|default|manual&wanMAC=<AA:BB:...>`

UI sends `macHost` when cloning. Takes effect after ~30 s.

### 6.3 `POST /goform/setWifi`

| Module | Params |
|---|---|
| `module1=wifiEn` | `wifiEn` |
| `module2=wifiBasicCfg` | `wifiSSID`, `wifiSecurityMode`, `wifiPwd`, `wifiHideSSID` |
| `module3=wifiTime` | `wifiTimeEn`, `wifiTimeClose`, `wifiTimeDate` |
| `module4=wifiWPS` | `wpsEn` |
| `module5=wifiAdvCfg` | `wifiMode`, `wifiChannel`, `wifiBandwidth` |
| `module6=wifiPower` | `wifiPower` |
| `module7=wifiVirSsid` | `multiWifiEnable` (`"0"`/`"1"`), `multiWifiSSID`, `multiWifiPwd` |

Changing SSID/password/key mode disconnects clients — the UI warns before submitting, and your app
should too.

### 6.4 `POST /goform/setWifiWps`

`action=pbc` — starts push-button pairing. Not module-numbered. Returns a message body, not
`errCode` in every build, so do not hard-fail on parse.

### 6.5 `POST /goform/setQos`

`module1=qosList&qosList=<rows>`

Row format (6 tab-separated fields, `\n` between rows):

```
<hostname>\t<remark>\t<MAC>\t<upLimitKBps>\t<downLimitKBps>\t<true|false>
```

* `hostname` is the DHCP hostname the router knows (the `data-mark` value); `remark` is your label.
* `upLimitKBps` / `downLimitKBps`: `38528` means **no limit**; `0` accompanies a blocked client.
  The UI's manual entry box caps at 38400 and turns >38400 into "No Limit".
* Last field `false` puts the client on the block list (internet denied).
* Blocked clients come back in `blackList`; re-sending them with `false` keeps them blocked.
* The UI also enforces a 10-device block limit.

### 6.6 `POST /goform/setWifiRelay`

`module1=wifiRelay&wifiRelayType=...&wifiRelaySSID=...&wifiRelayMAC=...&wifiRelaySecurityMode=...&wifiRelayPwd=...&wifiRelayChannel=...`

Changing `wifiRelayType` **reboots the router** (except `disabled`↔`disabled` and `ap`↔`ap`).
After `ap` or `client+ap` the UI moves to `tendawifi.com`; expect tens of seconds of downtime.

### 6.7 `POST /goform/setParentControl`

| Module | Params |
|---|---|
| `module1=parentCtrlList` | `onlineList` rows: `hostname\tremark\tMAC\tIP\ttrue|false` |
| `module2=parentAccessCtrl` | `parentCtrlOnlineTime`, `parentCtrlOnlineDate`, `parentCtrlURLFilterMode`, `urlList` (`\n`-separated keywords) |

The device list is a full replacement: include every device you want governed, not just the changed
one. The UI limits 10 enabled entries and 32 URL keywords.

### 6.8 `POST /goform/setIPV6`

| Module | Params |
|---|---|
| `module1=ipv6Enable` | `ipv6En` |
| `module2=wan6BasicCfg` | `ipv6WanType` + mode-specific fields (see 4.8) |
| `module3=lan6Cfg` | LAN prefix/DHCP fields (see 4.8) |

The UI omits `wan6BasicCfg`/`lan6Cfg` entirely when IPv6 is disabled, and drops
`ipv6LanDhcpType`/start/end IDs when DHCPv6 server or stateful mode is off.

### 6.9 `POST /goform/setSysTools`

| Module | Params |
|---|---|
| `module1=loginAuth` | `newPwd`, `oldPwd` — **Base64, not hashed** (unlike login). Omit `oldPwd` when no password exists yet. Wrong `oldPwd` → `errCode 2`. |
| `module2=wanAdvCfg` | `wanServerName`, `wanServiceName`, `wanMTU`, `macClone`, `wanMAC`, `wanSpeed` |
| `module3=lanCfg` | `lanIP`, `lanMask`, `dhcpEn`, `lanDhcpStartIP`, `lanDhcpEndIP`, `lanDns1`, `lanDns2` |
| `module4=remoteWeb` | `remoteWebEn`, `remoteWebType`, `remoteWebIP`, `remoteWebPort` |
| `module5=sysTime` | `sysTimeZone` |
| `module6=softWare` | `autoMaintenanceEn` |

Changing `lanIP` moves the management address — the UI sends you to the new IP after success.
`remoteWebEn=true` requires an admin password to be set. `sysTimeZone` is an index (0–74),
`51` = GMT+06:00.

### 6.10 `POST /goform/setNAT`

| Module | Params |
|---|---|
| `module1=staticIPList` | `staticList` rows: `ip\tMAC\tremark` |
| `module2=portList` | `portList` rules: `ip;intPort;extPort;proto~...` |
| `module3=dmz` | `dmzEn`, `dmzHostIP` |
| `module4=ddns` | `ddnsEn`, `ddnsServiceName`, `ddnsServer`, `ddnsUser`, `ddnsPwd` |
| `module5=upnp` | `upnpEn` |
| `module6=ping` | `pingEn` |
| `module7=apIsolation` | `apIsolationEn` |
| `module8=macFilter` | `filterMode` (`deny`\|`pass`), `macFilterList` rows: `hostname\tremark\tMAC` |

`portList` uses `~` between rules and `;` inside a rule — note it is **not** the tab/newline shape used
elsewhere. Protocol is `tcp`, `udp` or `both`. Limits: 20 static leases, 16 port-forward rules,
20 MAC whitelist / 10 blacklist entries.

`macFilterList` is a full replacement of the selected mode's list. The stock UI injects the
requesting client's host entry (the browser's `getNAT.localhostMAC`, not the router's MAC) into a
`pass` (whitelist) list automatically; keep it listed or the management session locks itself out.
The 20-entry whitelist limit counts that injected entry.

The MAC filter and the QoS block list are independent modules: a client can be present in both a
`pass` list and the QoS `blackList`. LinkPilot therefore treats allow-only mode as authoritative and
writes both lists when blocking or restoring, in an order that leaves a reversible state if the
second write fails.

### 6.11 `POST /goform/sysReboot` and `/goform/sysRestore`

`module1=sysOperate&action=reboot` and `module1=sysOperate&action=restore`.

Per the UI source, both answer `errCode 100` and the device restarts. Factory reset also wipes the admin password and
returns the LAN side to defaults (the UI redirects to `192.168.0.1` or `tendawifi.com`).

---

## 7. `cgi-bin` endpoints

| Endpoint | Method | Notes |
|---|---|---|
| `/cgi-bin/DownloadSyslog/RouterSystem.log` | GET | Plain-text system log download |
| `/cgi-bin/DownloadCfg/RouterCfm.cfg` | GET | Configuration backup |
| `/cgi-bin/upgrade` | POST multipart, field `upgradeFile` | Firmware upload; returns JSON `errCode` |
| `/cgi-bin/UploadCfg` | POST multipart, field `inportFile` | Restore backup; returns JSON `errCode` |
| `/cgi-bin/DownloadFlash` | GET | Raw 2 MiB flash image over HTTP/1.0 |

Both uploads answer `100` on success (device reboots), `201` image rejected, `202` failed,
`203` too large. Send the admin cookie with the request.

### 7.1 `GET /cgi-bin/DownloadFlash`

Not referenced by any UI file — the name exists only in the application image, among a block of
form-handler name strings at file offset `0x1c1444`, so it was found by disassembly rather than by
reading the front end. It streams the whole SPI flash as one plain HTTP body: no JSON, no `errCode`,
no progress.

```
GET /cgi-bin/DownloadFlash
Cookie: ecos_pw=<token>:language=en
```

| Property | Observed value |
|---|---|
| Status | `200` |
| `Content-Type` | `config/conf` (misleading, it is a binary image) |
| `Content-Length` | `2097152` (exactly 2 MiB) |
| Transfer time | ~62 s on a healthy link |
| Body | Raw flash, offset 0 = `0x000000` |

The transfer takes about a minute for 2 MiB and is single-shot: the handler emits the image in one
response, with no progress reporting and no resume. A short client timeout silently truncates the
body, so read the full `Content-Length` in one request and verify the byte count before parsing.

**Layout on this build** (Tenda F3 v2, `V12.03.01.27_multi`):

| Flash offset | Size | Contents |
|---|---|---|
| `0x000000` | `0x1320` | Boot ROM + DDR init code (raw MIPS, strings "DTR Done.", "DCR Done.") |
| `0x001320` | 19,602 B LZMA → 73,008 B | Bootloader |
| `0x00A818` | 1,017,511 B LZMA → 3,155,688 B | Application image (`main.bin`) |
| `0x102EBF`–`0x1EFFFF` | — | Erased (all `0xFF`) |
| `0x1F0000`–`0x1FFFFF` | 64 KiB | **NVRAM, plaintext** |

The application payload is legacy LZMA-Alone, not `xz`-container:

```sh
dd if=flash.bin of=firmware.bin.lzma bs=1 skip=$((0xA818)) count=1017511 status=none
xz --format=lzma -dc firmware.bin.lzma > firmware.bin
```

The result matches the repo's `firmware/f3-v2-V12.03.01.27_multi/firmware.bin` byte for byte and is
big-endian MIPS eCos linked at `0x80000000`, so `VA = 0x80000000 + file_offset`. Strings,
handler-name blocks and the compiled CGI code are all reachable that way.

> **The dump is a secret.** The NVRAM block holds the Wi-Fi SSID and PSK, the admin password
> (`http_passwd` is base64, not a hash — one `base64 -d` away) and the PPPoE password, all in
> plaintext. Never commit `flash.bin`, attach it to an issue, or paste it into a shared log. Keep
> the decompressed `main.bin` instead — it contains no per-device credentials, because NVRAM lives
> outside the application image.

On writes and safety: `DownloadFlash` is read-only, but it is compiled into the same CGI set as
`/cgi-bin/upgrade`, and a blind GET to `/cgi-bin/upgrade` is enough to reboot the router. Treat one
explicit request, then re-check `getstok`, as the whole procedure.

---

## 8. Gotchas worth designing around

1. **Failures look like success in HTML.** An expired session returns a 302 (or a login page body);
   the UI sniffs for `<!DOCTYPE`. Your client should check the status code *and* the `ecos_pw`
   cookie before trusting JSON.
2. **No CSRF token, no TLS.** Bind your app's control surface to the LAN and do not cache responses.
3. **Secrets come back in plaintext.** `getWAN`, `getWifi`, `getNAT` (DDNS), `getWifiRelay` and
   `getSysTools` return Wi-Fi PSK, PPPoE credentials, admin-authored labels and DDNS passwords.
   Keep them out of logs, crash reports and analytics.
4. **Setters replace, not merge.** Always read → modify → write the full module, and re-read after
   writing because the firmware normalises values (e.g. it returns `4931584.00` for an unlimited
   QoS limit you wrote as `38528`).
5. **Request/response name drift.** `deviceStatistics` → `deviceStastics`,
   `statusWanGaterway`, `username ` (trailing space), `Connectting`. Copy the names verbatim.
6. **Time formats.** Windows are `HH:MM-HH:MM`; day masks are 8 chars with index 0 = "every day".
7. **Reboot paths.** `errCode 100`, mode changes, firmware upgrade and factory reset all drop the
   device. Poll `getstok`/login until it answers, then re-authenticate; there is no progress API.
8. **One device, four admins.** If your app holds a session, the user can still open the web UI —
   but a fifth session is refused with login code `2`/`3`.
9. **Poll politely.** The UI polls status every 3 s, QoS and time every 5 s, DDNS and radio info
   every 2 s. A mobile app on 2–5 s intervals with backoff when the screen is off is fine; hammering
   the device from several screens is not.

---

## 9. Reference client

`clients/tenda_client.py` (stdlib only) implements login, every read endpoint above, and the safe
write helpers, with the list encodings from §6. Example:

```python
from clients.tenda_client import TendaClient

c = TendaClient("your-admin-password")
c.login()
try:
    print(c.status()["systemInfo"]["softVersion"])
    for dev in c.qos()["onlineList"]:
        print(dev["qosListIP"], dev["qosListRemark"] or dev["qosListHostname"])

    # throttle one client to 512 KB/s down / 128 KB/s up, everyone else unlimited
    c.set_qos([{"hostname": "dev-null", "remark": "dev-null",
                "mac": "AA:BB:CC:DD:EE:FF", "up_limit_kbps": 128,
                "down_limit_kbps": 512, "allowed": True}])
finally:
    c.logout()
```

## 10. Evidence and coverage

| Area | Status |
|---|---|
| Auth, session, logout | Verified live (login, cookie, 302 on expiry, logout) |
| `getStatus`, `getWAN`, `getWifi`, `getQos`, `getWifiRelay` (+scan), `getParentControl`, `getIPV6`, `getSysTools`, `getNAT`, `getHomePageInfo` | Verified live, samples in `docs/samples/` |
| `setSysTools` (`module5`), `setNAT` (`module6`) | Verified live — confirms partial module writes and `errCode` format |
| All other setters | Derived from the UI source; field names and encodings are exact, but **not executed** against the device (destructive). Verify one at a time. |
| `cgi-bin` downloads/uploads | `DownloadSyslog`, `DownloadCfg`, `DownloadFlash` verified live; `upgrade`/`UploadCfg` names verified in UI source, not executed |
| Session idle timeout, exact admin-session eviction rules | Not measured |

The JS sources used are the live UI files (`js/*.js`, `js/libs/common.js`, `js/libs/reasy-ui-1.0.3.js`,
`lang/b28n.js`). The running application image was also extracted from a `DownloadFlash` dump and
disassembled; see [`firmware/README.md`](../firmware/README.md) for the image, its hashes and the
extraction steps. `DownloadFlash` itself was found in a block of handler-name strings in that
image, not in the JavaScript.
