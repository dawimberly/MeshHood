# MeshHood

**Serverless hyperlocal mesh for disaster resilience.**

When cell towers and internet fail, neighbors still need to coordinate—share supplies, call for help, relay an emergency. MeshHood turns Android phones into a **phone-to-phone mesh** over Bluetooth LE, WiFi Direct, and local WiFi. No account, no cloud, no central server.

Think **Walking Dead safety model**: your block stays connected when everything else goes dark. Hyperlocal first—state, ZIP, and neighborhood scopes—not a global social network.

> **Status:** Experimental research prototype. Not certified for life-safety use. See [SECURITY.md](SECURITY.md).

[![CI](https://github.com/dawimberly/MeshHood/actions/workflows/ci.yml/badge.svg)](https://github.com/dawimberly/MeshHood/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## Two apps: Consumer vs Gateway

One codebase builds **two installable APKs**:

| Flavor | Package | Role |
|--------|---------|------|
| **Consumer** | `com.meshhood` | Neighbor mesh UI; **verify-only** official agency alerts |
| **Gateway** | `com.meshhood.gateway` | Sign and publish agency alerts; mesh hub on home Wi‑Fi |

| Install | Command |
|---------|---------|
| Consumer (debug) | `install.cmd` or `gradlew installConsumerDebug` |
| Gateway (debug) | `install_gateway.cmd` or `gradlew installGatewayDebug` |

Gateway signing key (dev): run `tools\setup_agency_gateway.cmd` before building the gateway flavor.

Publishing from the gateway app on the **same phone** as the consumer app does **not** update the consumer feed over mesh—separate processes and radios. See [docs/AGENCY-GATEWAY.md](docs/AGENCY-GATEWAY.md).

---

## Key features (current)

| Feature | Detail |
|---------|--------|
| **Network readiness** | Status strip shows Ready / Limited / Searching / Offline from mesh + transport state |
| **Feed sort** | **Recent** (time) or **Nearby** (distance) on the Home feed |
| **Google Maps handoff** | In-app map when keyed; **Open in Google Maps** for directions |
| **Mutual location** | Pairwise consent for map pins; emergency always attaches live GPS |
| **Agency alerts** | Ed25519-signed official messages via gateway; styled feed cards |
| **Gateway headless** | Spare phone runs mesh hub on boot; UI always openable from icon or notification |
| **State Sync v1** | LAN / WiFi Direct catch-up handshake replays missed envelopes |
| **Protobuf wire** | Optional binary envelopes on LAN/WiFi Direct after capability negotiation; JSON default |
| **Cellular uplink** | Gateway optionally relays encrypted frames via HTTP relay (mobile data) |
| **Mesh transports** | BLE, WiFi Direct, and LAN (mDNS) in parallel; multi-hop relay with TTL + dedup |
| **Emergency SOS** | Alert tab, lock-screen widget, shortcut—mesh broadcast + ICE; SMS when cell works |
| **Trust & DMs** | Ed25519 profiles; X25519 encrypted direct messages |

Bottom nav: **Home** (feed) · **Nearby** (map) · **Resources** (coordinator) · **Alert** (SOS).

---

## Quick start (Windows)

```powershell
git clone https://github.com/dawimberly/MeshHood.git
cd MeshHood
.\install.cmd          # consumer APK on adb device
# optional:
.\install_gateway.cmd  # gateway APK (run setup_agency_gateway first)
```

| Script | Purpose |
|--------|---------|
| `install.cmd` / `install.ps1` | Build + install **consumer**; waits for adb |
| `install_gateway.cmd` | Build + install **gateway** only |
| `run.cmd` / `run.ps1` | Install when a device is already connected |
| `connect-wifi.cmd` | Wi‑Fi adb pairing (no USB) |
| `tools\setup_maps.cmd` | Maps SDK key → `local.properties` (+ optional reinstall) |
| `tools\setup_agency_gateway.cmd` | Dev signing key for gateway builds |
| `tools\inject_agency.cmd "text"` | Inject signed agency alert into **debug consumer** (adb) |

**USB:** Developer options → USB debugging → `.\install.cmd`

**Wi‑Fi adb:** Same Wi‑Fi → `.\connect-wifi.cmd` → `.\install.cmd`

**Maps:** Copy `local.properties.example` or run `tools\setup_maps.cmd`; enable Maps SDK for Android, restrict key to `com.meshhood`.

**Gradle (any OS):** `./gradlew installConsumerDebug` from repo root.

After install: grant permissions, set state in **Area**, leave the foreground service running (status **Advertising**).

---

## Gateway setup (spare phone)

Recommended: a **dedicated old phone** on home Wi‑Fi and a charger running the **Gateway** APK as a mesh hub.

1. `install_gateway.cmd` → open **MeshHood Gateway** → grant permissions
2. Turn **Gateway mode** on; optional **Run headless on boot**
3. Consumer phones on the same Wi‑Fi should show **WiFi LAN: gateway** when linked
4. Optional: configure **Relay base URL** for cellular uplink beyond the local mesh

Full walkthrough: [docs/GATEWAY-SETUP.md](docs/GATEWAY-SETUP.md) · Headless details: [docs/GATEWAY-HEADLESS.md](docs/GATEWAY-HEADLESS.md)

---

## Testing

### Two Android devices (recommended)

1. `install.cmd` on both phones; same Wi‑Fi for LAN discovery
2. Open MeshHood on both; confirm status shows linked peers
3. Send broadcasts on one; verify feed on the other after State Sync handshake
4. Logcat: `adb logcat -s StateSync LanTransport MeshSerializer`

WiFi Direct needs two phones; LAN is easiest on a shared router.

### Phone + PC (`mesh_lan.py`)

With MeshHood open and advertising on the same LAN:

```bash
cd tools
pip install -r requirements.txt
python mesh_lan.py                    # discover phones, send frames
python pc_transport_matrix.py         # BLE + LAN smoke tests
python pc_transport_matrix.py --auto
```

State Sync and protobuf details: [docs/STATE-SYNC.md](docs/STATE-SYNC.md) · [docs/PROTOBUF.md](docs/PROTOBUF.md)

**Agency UI without mesh:** debug consumer + `tools\inject_agency.cmd "Shelter open until 8pm"`.

More transport tests: [docs/TRANSPORT-TESTS.md](docs/TRANSPORT-TESTS.md)

---

## Docs

| Document | Purpose |
|----------|---------|
| [docs/GATEWAY-SETUP.md](docs/GATEWAY-SETUP.md) | Spare-phone gateway hub setup |
| [docs/GATEWAY-HEADLESS.md](docs/GATEWAY-HEADLESS.md) | Headless hub on boot; reopen UI anytime |
| [docs/STATE-SYNC.md](docs/STATE-SYNC.md) | LAN/WiFi Direct catch-up sync (v1) |
| [docs/PROTOBUF.md](docs/PROTOBUF.md) | Binary wire format + capability negotiation |
| [docs/CELLULAR-UPLINK.md](docs/CELLULAR-UPLINK.md) | Gateway HTTP relay over mobile data |
| [docs/PRIVACY-SAFETY.md](docs/PRIVACY-SAFETY.md) | Location consent and emergency override |
| [docs/AGENCY-GATEWAY.md](docs/AGENCY-GATEWAY.md) | Consumer vs gateway; relay testing |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Protocol and mesh design |
| [docs/EMULATOR.md](docs/EMULATOR.md) | AVD setup on Windows |
| [docs/TRANSPORT-TESTS.md](docs/TRANSPORT-TESTS.md) | Transport matrix |

---

## Project layout

```
MeshHood/
├── app/                    # consumer + gateway product flavors
│   └── src/main/proto/     # meshhood.proto (protobuf wire)
├── tools/                  # mesh_lan.py, cellular_relay.py, PC tests, setup helpers
├── docs/
├── install.cmd             # consumer
└── install_gateway.cmd     # gateway
```

Core Kotlin: `MeshService.kt` (mesh + feed + sync), `MainActivity.kt` (Area/Chats/nav), `NetworkReadiness.kt`, agency + mutual-location helpers.

---

## Contributing

Issues and PRs welcome. For crypto or protocol changes, add tests under `app/src/test/` or `tools/pc_*`.

## License

[MIT](LICENSE) — Copyright (c) 2026 Dan Wimberly

Third-party: [BouncyCastle](https://www.bouncycastle.org/) (Ed25519), [MediaPipe GenAI](https://ai.google.dev/edge/mediapipe/solutions/genai) (optional LLM).
