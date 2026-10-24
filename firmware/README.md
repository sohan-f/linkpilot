# Tenda F3 v2 firmware — `V12.03.01.27_multi`

Running image, pulled from the router itself over the undocumented

```
GET http://192.168.0.1/cgi-bin/DownloadFlash
```

endpoint (see [`docs/TENDA_WEB_API.md` §7.1](../docs/TENDA_WEB_API.md#71-get-cgi-bindownloadflash)).
The response is the complete 2 MiB SPI flash, `Content-Type: config/conf`, HTTP/1.0, ~62 s.
Nothing was written to the device.

## What is here

| File | Size | Contents |
|---|---|---|
| `f3-v2-V12.03.01.27_multi/firmware.bin` | 3,155,688 B | Application image, decompressed |
| `f3-v2-V12.03.01.27_multi/firmware.bin.lzma` | 1,017,511 B | Exact LZMA payload as stored in flash at `0xA818` |
| `f3-v2-V12.03.01.27_multi/bootloader.bin` | 73,008 B | Bootloader, decompressed |
| `f3-v2-V12.03.01.27_multi/SHA256SUMS` | — | Hashes for the three files |

Verify with:

```sh
cd firmware/f3-v2-V12.03.01.27_multi && sha256sum -c SHA256SUMS
```

The application image is big-endian MIPS, linked at `0x80000000` (eCos, GoAhead web server), so a
file offset `X` maps to virtual address `0x80000000 + X`.

## What is deliberately *not* here

The raw 2 MiB dump (`flash.bin`) is excluded. Its last 64 KiB is the plaintext NVRAM block, which
holds the Wi-Fi SSID and PSK, the admin password (`http_passwd` is base64, one decode away) and the
PPPoE password for the specific device that was dumped. The application image and bootloader
contain none of those values — checked by searching both for the live strings — so only they are
kept.

Treat any future `DownloadFlash` output as a secret until you have zeroed that region.

## Flash layout

Verified against this dump (`0x200000` bytes total):

| Offset | Size | Contents | Stored as |
|---|---|---|---|
| `0x000000` | `0x1320` | Boot ROM + DDR init (MIPS) | raw |
| `0x001320` | 19,602 B | Bootloader | LZMA-Alone |
| `0x00A818` | 1,017,511 B | Application image | LZMA-Alone |
| `0x102EBF` | up to `0x1F0000` | Erased (`0xFF`) | — |
| `0x1F0000` | 65,536 B | NVRAM (secrets live here) | raw |

Both compressed streams are legacy LZMA-Alone (`.lzma` container: `5d` properties, 8 MiB dictionary,
then a 64-bit uncompressed size). Plain `xz -d` guesses the container from the name, so use
`xz --format=lzma -dc`, or Python's `lzma.FORMAT_ALONE`.

## Extracting from a fresh dump

```sh
# 1. application payload
dd if=flash.bin of=firmware.bin.lzma bs=1 skip=$((0xA818)) count=1017511 status=none
xz --format=lzma -dc firmware.bin.lzma > firmware.bin

# 2. bootloader
dd if=flash.bin of=bootloader.bin.lzma bs=1 skip=$((0x1320)) count=19602 status=none
xz --format=lzma -dc bootloader.bin.lzma > bootloader.bin
```

Expected hashes:

```
887a866f648f801d67f3835f347ac51b75d6a489bde4721bd2d03fc2a14aecb0  firmware.bin
178a574b2de158bc9d496e3b51e02cd4d2f420d6ac8d8ce044556bae8bdff660  firmware.bin.lzma
f24ca1ff7b11b1703057e15e32c5c5cc386fdb6faf0d5b8cdc5530aa37ac511d  bootloader.bin
```

## How this was confirmed to be the running build

The dump could plausibly have been a stale recovery partition, so it was cross-checked against the
live device:

* The version string `V12.03.01.27` sits at `firmware.bin` offset `0x1c08d8`.
* Every substantial UI file the router serves is embedded in the image verbatim, e.g. `ui/ipv6.js`
  (11,996 B) at `0x232690`, `ui/ipv6.html` at `0x1f7e40`, `ui/userManage.js` (11,859 B) at
  `0x23e2ac`, `ui/wireless.js` at `0x242768`. Fetch a file from the live device and you will find it
  at some offset in `firmware.bin`.
* Decompressing `firmware.bin.lzma` reproduces `firmware.bin` exactly, and the stream ends precisely
  where the erased region begins (`0x102EBF`), so it is neither truncated nor padded.

## Notes for analysis

* A block of form-handler names starts at file offset `0x1c1444`; the bare CGI name table is at
  `0x1bf780`. That is where `DownloadFlash` came from — the name appears nowhere in the
  JavaScript, so it cannot be found by reading the UI.
* Do not flash these files back by hand. `firmware.bin.lzma` is a payload fragment, not an upload
  image; the upgrade path expects a vendor `.bin`. A bad write leaves the unit needing the
  bootloader's TFTP recovery, which is not documented for this model.

## Copyright

The firmware is Tenda's proprietary code. It is kept here as evidence for interoperability and
reverse-engineering work on this specific model; it is not licensed for redistribution or reuse.
