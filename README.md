# LinkPilot: Tenda router tools

Tools and documentation for LinkPilot, an app that manages a Tenda router at `192.168.0.1`
running firmware `V12.03.01.27_multi`.

| Path | What it is |
|---|---|
| `docs/TENDA_WEB_API.md` | Router API reference, including authentication, endpoints and response formats |
| `docs/ANDROID_APP.md` | Android app features, architecture, limitations and build steps |
| `docs/samples/` | Sanitized examples of router responses |
| `clients/tenda_client.py` | Python client for the documented router API |
| `firmware/` | Firmware image, hashes and flash layout |
| `android/` | Native Kotlin and Compose app |

## Quick start

```bash
python3 clients/tenda_client.py '<admin-password>'      # prints the status payload
```

```python
from clients.tenda_client import TendaClient

c = TendaClient("<admin-password>")
c.login()
print(c.status()["systemInfo"]["statusWanIP"])
print([d["qosListRemark"] or d["qosListHostname"] for d in c.qos()["onlineList"]])
c.logout()
```

## The 60-second version

* Login: `GET /goform/getstok` → token, then `POST /login/Auth` with
  `password = md5(base64(password) + token)`. Session is the `ecos_pw` cookie.
* Reads: `GET /goform/get<Thing>?random=<float>&modules=<name>,<name>`.
* Writes: `POST /goform/set<Thing>?<random>` with `moduleN=<name>&field=value…`.
* Lists are flattened strings (`\t` fields, `\n` rows; port forwards use `~` and `;`).
* Writes replace a whole module — read, modify, write, then re-read.

Start with `docs/TENDA_WEB_API.md` §2 (conventions) and §8 (gotchas) before writing code.

## Android app (LinkPilot)

LinkPilot is a native Compose app with five main sections: Home, Devices, Wi-Fi, Monitor and More.
It works with the router over the local network and stores an optional saved password in Android
Keystore. It does not use accounts, cloud sync or telemetry. The optional diagnostics probe sends
one HTTPS request when you tap Run.

```bash
cd android
gradle :app:assembleDebug --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk   # when a device is attached
```

See [`docs/ANDROID_APP.md`](docs/ANDROID_APP.md) for supported features, build steps and known
limitations. Firmware upload and configuration restore remain available in the router's web UI.

Tagged builds are signed and published as project releases. See [`docs/RELEASING.md`](docs/RELEASING.md)
for the signing setup and release process.
