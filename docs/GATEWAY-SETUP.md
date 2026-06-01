# Gateway setup

MeshHood ships as **two separate Android apps** from one codebase:

| App | Package | Role |
|-----|---------|------|
| **MeshHood** (consumer) | `com.meshhood` | Daily driver for neighbors — feed, DMs, local mesh |
| **MeshHood Gateway** | `com.meshhood.gateway` | Infrastructure hub — signs official alerts, bridges radios on home Wi‑Fi |

The gateway does **not** replace the consumer app. They are different APKs with different icons.

---

## Getting started

### What the gateway is

- A **dedicated old or spare phone** plugged in at home on Wi‑Fi — this is the intended v1 hardware.
- Or the **same phone** running the separate **MeshHood Gateway** APK (works for dev/testing only).
- The gateway is a **mesh hub** on your LAN (mDNS: “WiFi LAN: gateway”, “N linked”) plus an optional **cellular uplink** to a relay server for remote reach.

Your **consumer phone** is what you carry day to day. The **gateway phone** stays home and keeps the neighborhood mesh bridged across Wi‑Fi, Bluetooth, and Wi‑Fi Direct.

### Setup steps

1. **Install the gateway app** (separate from consumer):
   ```cmd
   install_gateway.cmd
   ```
   Look for the **MeshHood Gateway** icon (launcher opens **Official alerts**).

2. **Open MeshHood Gateway** → grant Bluetooth and location when prompted.

3. Turn **Gateway mode** on. Leave the phone on **home Wi‑Fi** and a charger. Optional: enable **Run headless on boot** so the mesh hub restarts after reboot without showing UI — you can still open **Official alerts** anytime from the app icon or notification ([GATEWAY-HEADLESS.md](GATEWAY-HEADLESS.md)).

4. On **consumer phones** on the same Wi‑Fi, open **MeshHood** (`install.cmd`). Status should show LAN discovery, e.g. **WiFi LAN: gateway** and **1 linked** when the hub is reachable.

5. **Cellular uplink (optional)** — only if you need reach beyond the local mesh:
   - Run `python tools/cellular_relay.py` on a PC or deploy a relay server ([CELLULAR-UPLINK.md](CELLULAR-UPLINK.md)).
   - In the gateway app, set **Relay base URL** (and token if used). Requires mobile data on the gateway phone.

Agency signing key (for official alerts): `tools\setup_agency_gateway.cmd` on your PC — see [AGENCY-GATEWAY.md](AGENCY-GATEWAY.md).

---

## Same phone vs spare phone

| Setup | Works? | Notes |
|-------|--------|-------|
| **Spare phone = gateway, daily phone = consumer** | **Recommended** | Production-style. Consumer discovers gateway over LAN on home Wi‑Fi. |
| **Both apps on one phone** | Dev/testing only | Separate processes and storage. Consumer will **not** auto-receive gateway broadcasts in its feed — a phone rarely meshes to itself over BLE/Wi‑Fi Direct. Use **Open full app** inside the gateway APK to preview the feed on one device, or use a second phone/PC for real relay tests. |

The consumer app will **not** “just use your phone” as the gateway. You must install and run the **gateway APK** on hub hardware (typically a spare phone).

---

## Related docs

- [AGENCY-GATEWAY.md](AGENCY-GATEWAY.md) — consumer vs gateway, same-device testing, agency alerts
- [GATEWAY-HEADLESS.md](GATEWAY-HEADLESS.md) — headless hub on boot
- [CELLULAR-UPLINK.md](CELLULAR-UPLINK.md) — relay URL, push/pull over mobile data
