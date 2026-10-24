"""
Reference client for the Tenda router web API documented in docs/TENDA_WEB_API.md.

Tested against: firmware V12.03.01.27_multi (GoAhead web server, LAN-only HTTP).
Standard library only, so it runs anywhere Python 3.8+ does.

Auth model: the router has no username on the login screen. The browser fetches a
per-login random token, then posts md5(base64(password) + token). The server only
returns a session cookie; there is no bearer token and no CSRF token.
"""

from __future__ import annotations

import base64
import hashlib
import http.cookiejar
import json
import random
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Iterable

DEFAULT_BASE = "http://192.168.0.1"


class TendaError(RuntimeError):
    """Router returned a non-zero errCode or an unparsable body."""

    def __init__(self, message: str, err_code: str | None = None):
        super().__init__(message)
        self.err_code = err_code


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """Session loss shows up as a 302 to /login.html; surface it instead of following."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class TendaClient:
    def __init__(self, password: str, base: str = DEFAULT_BASE, timeout: float = 8.0):
        self.base = base.rstrip("/")
        self.password = password
        self.timeout = timeout
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cookies), _NoRedirect
        )

    def _hash_password(self, token: str) -> str:
        """The UI base64-encodes the UTF-8 password, then hashes with the token."""
        encoded = base64.b64encode(self.password.encode("utf-8")).decode("ascii")
        return hashlib.md5((encoded + token).encode("utf-8")).hexdigest()

    def _session_cookie(self) -> str | None:
        for c in self.cookies:
            if c.name == "ecos_pw":
                return c.value
        return None

    def login(self) -> None:
        _, _, text = self._raw("GET", "/goform/getstok")
        token = json.loads(text)["random"]
        status, headers, _ = self._raw(
            "POST", "/login/Auth", form={"password": self._hash_password(token)}
        )
        # Success is a 302 to /index.html plus the ecos_pw session cookie. Failure
        # redirects to /login.html?<code> (1 = wrong password, 2/3 = admin limit,
        # 10-14 = remaining attempt counter).
        if not self._session_cookie():
            raise TendaError(
                f"login failed (status {status}, location {headers.get('Location')})"
            )

    def logout(self) -> None:
        try:
            self._raw("POST", "/goform/loginOut", form={"action": "loginout"})
        finally:
            self.cookies.clear()
            self.cookies = http.cookiejar.CookieJar()
            self.opener = urllib.request.build_opener(
                urllib.request.HTTPCookieProcessor(self.cookies), _NoRedirect
            )

    @staticmethod
    def _encode_list(rows: Iterable[Iterable[Any]]) -> str:
        """Router list payloads are tab-separated fields, newline-separated rows."""
        return "\n".join("\t".join(str(c) for c in row) for row in rows)

    def _raw(self, method: str, path: str, form: dict | None = None) -> tuple[int, Any, str]:
        url = f"{self.base}{path}"
        body = urllib.parse.urlencode(form).encode() if form is not None else None
        req = urllib.request.Request(url, data=body, method=method)
        if body is not None:
            req.add_header("Content-Type", "application/x-www-form-urlencoded")
        try:
            with self.opener.open(req, timeout=self.timeout) as resp:
                return resp.status, resp.headers, resp.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read().decode("utf-8", "replace")

    def _request(self, method: str, form: str, *, params: dict | None = None, data: dict | None = None
                 ) -> dict:
        query = {"random": f"{random.random():.16f}"}
        if params:
            query.update(params)
        path = f"/goform/{form}?{urllib.parse.urlencode(query)}"
        status, _, text = self._raw(method, path, form=data)
        if status == 302:
            raise TendaError("session expired, log in again")
        try:
            return json.loads(text)
        except ValueError:
            raise TendaError(f"unexpected body from {form}: {text[:200]}") from None

    def get(self, form: str, modules: str) -> dict:
        return self._request("GET", form, params={"modules": modules})

    def set(self, form: str, data: dict) -> None:
        """Setters are full replacements per module: include every field of that module."""
        body = self._request("POST", form, data=data)
        code = str(body.get("errCode", "-1"))
        if code != "0":
            raise TendaError(f"{form} failed with errCode {code}", code)

    def status(self) -> dict:
        return self.get(
            "getStatus", "internetStatus,deviceStatistics,systemInfo,wanAdvCfg,wifiRelay"
        )

    def home(self) -> dict:
        return self.get("getHomePageInfo", "loginAuth,wifiRelay")

    def wan(self) -> dict:
        return self.get("getWAN", "lanCfg,wanBasicCfg,wanAdvCfg,internetStatus")

    def set_wan(self, cfg: dict) -> None:
        """cfg keys: wanType(dhcp|pppoe|static), wanPPPoEUser, wanPPPoEPwd, wanIP,
        wanMask, wanGateway, wanDns1, wanDns2. Unused fields are still sent, empty."""
        self.set("setWAN", {"module1": "wanBasicCfg", **{k: cfg.get(k, "") for k in (
            "wanType", "wanPPPoEUser", "wanPPPoEPwd", "wanIP", "wanMask",
            "wanGateway", "wanDns1", "wanDns2")}})

    def clone_mac(self, mac: str) -> None:
        self.set("setMacClone", {"module1": "wanAdvCfg", "macClone": "manual", "wanMAC": mac.upper()})

    def wifi(self) -> dict:
        return self.get(
            "getWifi",
            "wifiEn,wifiBasicCfg,wifiAdvCfg,wifiPower,wifiTime,wifiWPS,wifiVirSsid",
        )

    def set_wifi_enabled(self, enabled: bool) -> None:
        self.set("setWifi", {"module1": "wifiEn", "wifiEn": "true" if enabled else "false"})

    def set_wifi_basic(self, ssid: str, security: str, pwd: str, hide: bool = False) -> None:
        """security: none | wpa-psk | wpa2-psk | wpa&wpa2 (mixed)."""
        self.set("setWifi", {
            "module2": "wifiBasicCfg",
            "wifiSSID": ssid,
            "wifiSecurityMode": security,
            "wifiPwd": pwd,
            "wifiHideSSID": "true" if hide else "false",
        })

    def set_wifi_adv(self, mode: str, channel: str, bandwidth: str) -> None:
        """mode: bgn|bg|g|b. channel: 'auto' or '1'..'13'. bandwidth: 'auto'|'20'|'40'."""
        self.set("setWifi", {
            "module5": "wifiAdvCfg",
            "wifiMode": mode,
            "wifiChannel": channel,
            "wifiBandwidth": bandwidth,
        })

    def wps_pbc(self) -> None:
        # Unlike other setters this one returns a plain message body in some builds.
        self._raw("POST", "/goform/setWifiWps", form={"action": "pbc"})

    def qos(self) -> dict:
        return self.get("getQos", "localhost,onlineList,blackList,macFilter")

    @staticmethod
    def decode_qos_rows(blob: str) -> list[dict]:
        """Parse the qosList string: hostname, remark, mac, upLimit, downLimit, access."""
        rows = []
        for line in filter(None, blob.splitlines()):
            hostname, remark, mac, up, down, access = (line.split("\t") + [""] * 6)[:6]
            rows.append({
                "hostname": hostname,
                "remark": remark,
                "mac": mac,
                "up_limit_kbps": int(float(up)) if up else None,
                "down_limit_kbps": int(float(down)) if down else None,
                "allowed": access.lower() == "true",
            })
        return rows

    def set_qos(self, rows: list[dict]) -> None:
        """Full replacement. 38528 means 'no limit'; limit fields are KB/s."""
        lines = []
        for r in rows:
            up = r.get("up_limit_kbps")
            down = r.get("down_limit_kbps")
            lines.append("\t".join([
                r["hostname"],
                r.get("remark", ""),
                r["mac"].upper(),
                str(38528 if up is None else up),
                str(38528 if down is None else down),
                "true" if r.get("allowed", True) else "false",
            ]))
        self.set("setQos", {"module1": "qosList", "qosList": "\n".join(lines)})

    def set_mac_filter(self, mode: str, entries: list[dict]) -> None:
        """mode: deny | pass. entries: {'hostname','remark','mac'}."""
        rows = [(e.get("hostname", ""), e.get("remark", ""), e["mac"].upper()) for e in entries]
        self.set("setNAT", {
            "module8": "macFilter",
            "filterMode": mode,
            "macFilterList": self._encode_list(rows),
        })

    def relay(self) -> dict:
        return self.get("getWifiRelay", "wifiEn,wifiRelay")

    def wifi_scan(self) -> list[dict]:
        return self.get("getWifiRelay", "wifiScan").get("wifiScan", [])

    def set_relay(self, relay_type: str, ssid: str = "", pwd: str = "", mac: str = "",
                  security: str = "none", channel: str = "0") -> None:
        """relay_type: disabled | ap | wisp | client+ap. Changing it reboots the router."""
        self.set("setWifiRelay", {
            "module1": "wifiRelay",
            "wifiRelayType": relay_type,
            "wifiRelaySSID": ssid,
            "wifiRelayPwd": pwd,
            "wifiRelayMAC": mac,
            "wifiRelaySecurityMode": security,
            "wifiRelayChannel": channel,
        })

    def parental(self) -> dict:
        return self.get("getParentControl", "parentCtrlList,parentAccessCtrl")

    def set_parental_devices(self, rows: list[dict]) -> None:
        """rows: {'hostname','remark','mac','ip','enabled'}."""
        dev_rows = [(r["hostname"], r.get("remark", ""), r["mac"].upper(), r["ip"],
                     "true" if r.get("enabled") else "false") for r in rows]
        self.set("setParentControl", {
            "module1": "parentCtrlList",
            "onlineList": self._encode_list(dev_rows),
        })

    def set_parental_rules(self, window: str, days: str, url_mode: str = "disable",
                           urls: list[str] | None = None) -> None:
        """window 'HH:MM-HH:MM'; days is 8 chars, index 0 = every day, 1..7 = Mon..Sun."""
        self.set("setParentControl", {
            "module2": "parentAccessCtrl",
            "parentCtrlOnlineTime": window,
            "parentCtrlOnlineDate": days,
            "parentCtrlURLFilterMode": url_mode,
            "urlList": "\n".join(urls or []),
        })

    def ipv6(self) -> dict:
        return self.get("getIPV6", "ipv6Enable,wan6BasicCfg,lan6Cfg,ipv6Status")

    def systools(self) -> dict:
        return self.get(
            "getSysTools",
            "loginAuth,wanAdvCfg,lanCfg,softWare,wifiRelay,sysTime,remoteWeb,isWifiClients,systemInfo",
        )

    def set_lan(self, ip: str, mask: str, dhcp: bool, start: str, end: str,
                dns1: str = "", dns2: str = "") -> None:
        self.set("setSysTools", {
            "module3": "lanCfg",
            "lanIP": ip,
            "lanMask": mask,
            "dhcpEn": "true" if dhcp else "false",
            "lanDhcpStartIP": start,
            "lanDhcpEndIP": end,
            "lanDns1": dns1,
            "lanDns2": dns2,
        })

    def change_password(self, new_pwd: str, old_pwd: str | None = None) -> None:
        """Both passwords are base64-encoded, NOT hashed here (unlike login)."""
        data = {"module1": "loginAuth", "newPwd": base64.b64encode(new_pwd.encode()).decode()}
        if old_pwd is not None:
            data["oldPwd"] = base64.b64encode(old_pwd.encode()).decode()
        self.set("setSysTools", data)

    def reboot(self) -> None:
        self.set("sysReboot", {"module1": "sysOperate", "action": "reboot"})

    def factory_reset(self) -> None:
        self.set("sysRestore", {"module1": "sysOperate", "action": "restore"})

    def nat(self) -> dict:
        return self.get(
            "getNAT",
            "wifiRelay,staticIPList,portList,macFilter,localhost,onlineList,ddns,dmz,ping,upnp,lanCfg,apIsolation",
        )

    def set_port_forward(self, rules: list[dict]) -> None:
        """rules: {'ip','int_port','ext_port','proto'}; proto: tcp|udp|both."""
        blob = "~".join(
            f"{r['ip']};{r['int_port']};{r['ext_port']};{r.get('proto', 'both')}" for r in rules
        )
        self.set("setNAT", {"module2": "portList", "portList": blob})

    def set_static_dhcp(self, entries: list[dict]) -> None:
        """entries: {'ip','mac','remark'}."""
        rows = [(e["ip"], e["mac"].upper(), e.get("remark", "")) for e in entries]
        self.set("setNAT", {"module1": "staticIPList", "staticList": self._encode_list(rows)})

    def set_dmz(self, enabled: bool, host_ip: str = "") -> None:
        self.set("setNAT", {"module3": "dmz", "dmzEn": "true" if enabled else "false",
                            "dmzHostIP": host_ip})

    def set_ddns(self, enabled: bool, service: str, server: str, user: str, pwd: str) -> None:
        self.set("setNAT", {
            "module4": "ddns",
            "ddnsEn": "true" if enabled else "false",
            "ddnsServiceName": service,
            "ddnsServer": server,
            "ddnsUser": user,
            "ddnsPwd": pwd,
        })

    def set_upnp(self, enabled: bool) -> None:
        self.set("setNAT", {"module5": "upnp", "upnpEn": "true" if enabled else "false"})

    def set_wan_ping(self, enabled: bool) -> None:
        self.set("setNAT", {"module6": "ping", "pingEn": "true" if enabled else "false"})

    def set_ap_isolation(self, enabled: bool) -> None:
        self.set("setNAT", {"module7": "apIsolation", "apIsolationEn": "true" if enabled else "false"})

    def set_timezone(self, tz_index: int) -> None:
        """tz_index indexes the 75-entry list in the UI; 51 = (GMT+06:00) Astana, Dhaka."""
        self.set("setSysTools", {"module5": "sysTime", "sysTimeZone": str(tz_index)})


if __name__ == "__main__":
    import sys

    if len(sys.argv) < 2:
        raise SystemExit("usage: tenda_client.py <admin-password>")

    client = TendaClient(sys.argv[1])
    client.login()
    try:
        print(json.dumps(client.status(), indent=2))
    finally:
        client.logout()
