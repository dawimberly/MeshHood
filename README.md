# MeshHood

**Serverless hyperlocal mesh for disaster resilience.**

When cell towers and internet fail, neighbors still need to coordinate—share supplies, call for help, relay an emergency. MeshHood turns Android phones into a **phone-to-phone mesh** over Bluetooth LE, WiFi Direct, and local WiFi. No account, no cloud, no central server.

> **Status:** Experimental research prototype. Not certified for life-safety use. See [SECURITY.md](SECURITY.md).

[![CI](https://github.com/dawimberly/MeshHood/actions/workflows/ci.yml/badge.svg)](https://github.com/dawimberly/MeshHood/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## Two apps (consumer vs gateway)

One codebase builds **two installable APKs**:

| Flavor | Package | Role |
|--------|---------|------|
| **Consumer** | `com.meshhood` | Neighbor mesh UI; **verify-only** official agency alerts |
| **Gateway** | `com.meshhood.gateway` | Sign and publish agency alerts; includes neighbor UI for preview |

| Install | Command |
|---------|---------|
| Consumer (debug) | `install.cmd` or `gradlew installConsumerDebug` |
| Gateway (debug) | `install_gateway.cmd` or `gradlew installGatewayDebug` |

Gateway signing key (dev): `tools\setup_agency_gateway.cmd` before building the gateway flavor.

Publishing from the gateway app on the **same phone** as the consumer app does **not** update the consumer feed over mesh—separate processes and radios. See [docs/AGENCY-GATEWAY.md](docs/AGENCY-GATEWAY.md).

---

## What you get

| Area | Detail |
|------|--------|
| **Mesh transports** | BLE, WiFi Direct, and LAN (mDNS) run in parallel; multi-hop relay with TTL + dedup |
| **Agency alerts** | Ed25519-trusted official messages; styled **feed cards** in Area |
| **Emergency SOS** | Alert tab, lock-screen widget, shortcut—mesh broadcast + ICE; SMS to ICE when cell works |
| **Area feed** | Geographic scopes (state, rolling ZIP, locality); public Area vs private **Chats** |
| **Mutual location** | Pairwise consent for map pins; emergency always attaches live GPS—[docs/PRIVACY-SAFETY.md](docs/PRIVACY-SAFETY.md) |
| **Maps** | In-app Google Maps when keyed; **Open in Google Maps** handoff for directions |
| **Coordinator** | Rule-based resource matching + optional on-device Gemma LLM (**Resources** tab) |
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
| `connect-wifi.cmd` | Wi-Fi adb pairing (no USB) |
| `tools\setup_maps.cmd` | Maps SDK key → `local.properties` (+ optional reinstall) |
| `tools\setup_agency_gateway.cmd` | Dev `AGENCY_SIGNING_KEY` for gateway builds |
| `tools\inject_agency.cmd "text"` | Inject signed agency alert into **debug consumer** (adb) |
| `tools\emulator_install.cmd` | Consumer on a running AVD—[docs/EMULATOR.md](docs/EMULATOR.md) |

**USB:** Developer options → USB debugging → `.\install.cmd`

**Wi-Fi adb:** Same Wi-Fi → `.\connect-wifi.cmd` → `.\install.cmd`

**Maps:** Copy `local.properties.example` or run `tools\setup_maps.cmd`; enable Maps SDK for Android, restrict key to `com.meshhood`.

**Gradle (any OS):** `./gradlew installConsumerDebug` from repo root (`gradlew` is not under `tools\`).

After install: grant permissions, set state in **Area**, leave the foreground service running (status **Advertising**).

---

## Testing (phone + PC)

With MeshHood open and advertising:

```bash
cd tools
pip install -r requirements.txt
python pc_transport_matrix.py        # BLE + LAN smoke tests
python pc_transport_matrix.py --auto
python pc_two_phone_sim.py           # guided Maria/Rosa BLE sim
```

Details and limits (WiFi Direct needs two phones): [docs/TRANSPORT-TESTS.md](docs/TRANSPORT-TESTS.md).

**Agency UI without mesh:** debug consumer + `tools\inject_agency.cmd "Shelter open until 8pm"`.

---

## Docs

| Document | Purpose |
|----------|---------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Protocol and mesh design |
| [docs/AGENCY-GATEWAY.md](docs/AGENCY-GATEWAY.md) | Gateway vs consumer; relay testing |
| [docs/PRIVACY-SAFETY.md](docs/PRIVACY-SAFETY.md) | Location consent and emergency override |
| [docs/EMULATOR.md](docs/EMULATOR.md) | AVD setup on Windows |
| [docs/TRANSPORT-TESTS.md](docs/TRANSPORT-TESTS.md) | Transport matrix |
| [docs/UI-DESIGN.md](docs/UI-DESIGN.md) | UI spec |
| [docs/PITCH.md](docs/PITCH.md) | Partner pitch |

---

## Project layout (high level)

```
MeshHood/
├── app/                    # consumer + gateway product flavors
├── tools/                  # Python BLE/LAN tests, inject_agency, setup helpers
├── docs/
├── install.cmd             # consumer
└── install_gateway.cmd     # gateway
```

Core Kotlin: `MeshService.kt` (mesh + feed), `MainActivity.kt` (Area/Chats/nav), `MapActivity.kt`, agency + mutual-location helpers.

---

## Contributing

Issues and PRs welcome. For crypto or protocol changes, add tests under `app/src/test/` or `tools/pc_*`.

## License

[MIT](LICENSE) — Copyright (c) 2026 Dan Wimberly

Third-party: [BouncyCastle](https://www.bouncycastle.org/) (Ed25519), [MediaPipe GenAI](https://ai.google.dev/edge/mediapipe/solutions/genai) (optional LLM).
