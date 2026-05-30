# MeshHood

**Serverless hyperlocal mesh for disaster resilience.**

When cell towers and internet fail, neighbors still need to coordinate — share supplies, call for help, relay an emergency. MeshHood turns Android phones into a **phone-to-phone mesh** over Bluetooth LE, WiFi Direct, and local WiFi. No account, no cloud, no central server.

> **Status:** Experimental research prototype. Not certified for life-safety use. See [SECURITY.md](SECURITY.md).

[![CI](https://github.com/dawimberly/MeshHood/actions/workflows/ci.yml/badge.svg)](https://github.com/dawimberly/MeshHood/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## Why it exists

Disasters knock out centralized infrastructure first. MeshHood explores what a **911-like layer** looks like when the network *is* the people around you — encrypted, relayed across phones, and designed emergency-first.

## Highlights

| Capability | Detail |
|------------|--------|
| **Emergency SOS** | **Alert** tab (confirm dialog), lock-screen widget, or app shortcut — broadcasts help + ICE card; optional SMS to ICE contact when cell is available |
| **Multi-transport** | BLE + WiFi Direct + LAN run in parallel; cellular used for emergency SMS fallback |
| **Multi-hop relay** | Messages hop across phones (TTL + dedup) to extend range |
| **Private DMs** | X25519 per-pair encryption; relays cannot read content |
| **Chats inbox** | Private threads in **Chats** — separate from the public **Area** feed |
| **Area hierarchy** | Nation → national region → state → region → **rolling ZIP** → local |
| **Smart default feed** | Opens on your most specific locality (ZIP when location resolves) |
| **Signed trust** | Ed25519 profiles, kudos, group admin actions |
| **Groups** | Community crews with admin verify + pin (no speech moderation) |
| **Resource brain** | Rule-based coordinator + optional on-device Gemma LLM |
| **Profile avatars** | Local photo + mesh thumbnail; neighbor vouch verification |
| **Area map** | Google Maps in-app; share/hide location on the mesh; open in Google Maps for directions |
| **Bottom nav** | Home (feed), Nearby (map), Resources (coordinator), Alert (SOS) |

## Docs

| Document | Purpose |
|----------|---------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Technical design |
| [docs/PITCH.md](docs/PITCH.md) | Investor / partner pitch |
| [docs/PATENT-BRIEF.md](docs/PATENT-BRIEF.md) | Provisional patent talking points |
| [docs/UI-DESIGN.md](docs/UI-DESIGN.md) | UI blend spec (Nextdoor warmth + mesh infrastructure) |

## UI: Area vs Chats

| Control | What it is |
|---------|------------|
| **Area ▼** | Public feed — Everyone, geographic levels, and groups |
| **Chats** (header icon) | Private direct-message inbox (most recent first) |
| **Bottom nav** | **Home** feed · **Nearby** map · **Resources** coordinator · **Alert** SOS |

Public broadcasts stay in **Area**. DMs never appear in the Area dropdown.

### Main screen layout

```
┌─────────────────────────────────────┐
│  Mesh ▂▄▆█              (signal)    │  ← transport strip
│  Area ▼              💬  👤  ⋮     │  ← locality + chats + profile + menu
├─────────────────────────────────────┤
│  Area feed (scroll)                 │
├─────────────────────────────────────┤
│  Home  Nearby  Resources  Alert     │  ← bottom nav
│  Everyone              Resources    │  ← recipient + coordinator chips
│  [ message.................... ] ➤  │  ← composer
└─────────────────────────────────────┘
```

### Emergency

| Entry point | What it does |
|-------------|--------------|
| **Alert** tab (bottom nav) | Confirm dialog → mesh broadcast + ICE card |
| **Home-screen SOS widget** | Opens lock-screen emergency screen (no unlock needed) |
| **App shortcut** | Same lock-screen flow |
| **⋮ menu → Emergency card** | Edit private ICE/medical info (shared only when you send SOS) |

SOS is intentionally behind a confirm step in the main app. The widget/shortcut are for when you cannot unlock the phone.

### Geographic area

- **State** — saved on your profile (US state dropdown).
- **ZIP** — **rolling from GPS** (same location permission as BLE scan); updates as you move.
- **Area ▼** lists localities **most specific first** (ZIP/local at top, Nation and Everyone below).
- Public messages carry a **`channel`** tag on the wire (e.g. `zone:postal:87110`) plus an optional **`geo`** snapshot. Relays still flood everything; each phone filters and sorts locally.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the comms vs geo split.

## Architecture

```
Phone A ←BLE/WiFi/LAN→ Phone B ←→ Phone C
         encrypted JSON envelopes, relay by id+ttl
         channel = viewer hint; geo = sender snapshot (not routing)
```

## Quick start

### Windows PC (clone from GitHub)

Anyone with a Windows PC + Android phone can use this repo:

```powershell
git clone https://github.com/dawimberly/MeshHood.git
cd MeshHood

# Install app on phone (USB or Wi-Fi adb — see below)
.\install.cmd

# Or: build + install + open app (same as install when a device is connected)
.\run.cmd

# Optional: simulate Maria + Rosa over BLE (phone running MeshHood, Advertising)
cd tools
pip install -r requirements.txt
python pc_two_phone_sim.py
```

| File | Purpose |
|------|---------|
| `install.ps1` / `install.cmd` | Build + install APK; waits for adb, retries on failure |
| `run.ps1` / `run.cmd` | Same as install when a phone is connected |
| `connect-wifi.ps1` / `connect-wifi.cmd` | Pair/connect phone over Wi-Fi adb (no USB cable) |
| `tools/install.cmd` | Same install when your shell is in `tools\` |
| `tools/pc_two_phone_sim.py` | Guided 2-phone BLE sim (Maria + Rosa) |

#### Install over USB

1. Phone: **Developer options → USB debugging ON**
2. Plug in USB, unlock phone, tap **Allow** on the debugging prompt
3. PC: `.\install.cmd`

#### Install over Wi-Fi (no cable)

1. Phone and PC on the **same Wi-Fi**
2. Phone: **Developer options → Wireless debugging ON**
3. PC: `.\connect-wifi.cmd` — enter pairing IP:port + 6-digit code (first time), then connect IP:port
4. PC: `.\run.cmd` or `.\install.cmd`

Leave **USB debugging ON**; you do not need to toggle it off between sessions.

### Android

**Requirements:** Android 8+ (API 26), Bluetooth, location permission (required for BLE scan and rolling ZIP on Android).

**Google Maps (in-app map):** Copy `local.properties.example` → `local.properties` and set `MAPS_API_KEY` from [Google Cloud Console](https://console.cloud.google.com/) (enable **Maps SDK for Android**, restrict the key to package `com.meshhood`). Without a key the map screen still opens **Open in Google Maps**; neighbors who share location appear as pins once the key is set.

**PowerShell** — run install from the **`MeshHood`** folder (not `tools`):

```powershell
# You are here:  PS ...\AndroidStudioProjects\MeshHood>
.\install.cmd

# You are here:  PS ...\AndroidStudioProjects\MeshHood\tools>
..\install.cmd

# You are here:  PS ...\AndroidStudioProjects>
.\MeshHood\install.cmd
```

```powershell
cd MeshHood
.\install.ps1
```

**Git Bash / macOS / Linux:**

```bash
git clone https://github.com/dawimberly/MeshHood.git
cd MeshHood
./gradlew installDebug
```

> **Common mistake:** `gradlew` and `install.cmd` live in **`MeshHood`**, not in `MeshHood\tools`.

1. Open MeshHood, grant permissions, and complete profile setup.
2. **Set my area** (long-press feed or **Area ▼**) — pick your state; ZIP fills from location.
3. Leave the app running — the foreground service keeps the mesh alive; status shows **Advertising**.

Send to **Everyone** for the public Area feed; use **Chats** for private messages. Tap **Resources** in the bottom nav (or the amber chip above the composer) for coordinator matches.

### PC test harness (optional)

Test against one phone from your computer over BLE:

```bash
cd tools
pip install -r requirements.txt
python pc_test.py          # handshake + broadcast
python pc_group_test.py    # groups + admin overlay
python pc_demo.py          # scripted demo
```

The phone must be running MeshHood with Bluetooth advertising active.

### No second phone?

Simulate **two neighbors** (Maria + Rosa) from your PC on the same BLE link:

```bash
python pc_two_phone_sim.py           # guided — follow steps on screen + phone
python pc_two_phone_sim.py --auto    # hands-off demo (~45s)
python pc_transport_matrix.py        # BLE + WiFi LAN transport tests (1 phone + PC)
python pc_transport_matrix.py --auto
python pc_maria_reply.py             # stable Maria identity for DM tests
```

Covers Area feed, DMs, directory, profiles, emergency + ICE. Does **not** test WiFi Direct between two phones or 3-hop relay (needs 3 physical devices).

Persistent simulated identities live in `tools/sim_identities/` (gitignored).

## Project layout

```
MeshHood/
├── install.ps1 / install.cmd       # Windows: build + install on phone
├── run.ps1 / run.cmd               # Install when adb device is connected
├── connect-wifi.ps1 / .cmd         # Wi-Fi adb pairing (no USB)
├── app/src/main/java/com/meshhood/
│   ├── MeshService.kt              # mesh core, feed scopes, protocol
│   ├── CellularTransport.kt        # emergency SMS to ICE contact
│   ├── EmergencyActivity.kt        # lock-screen SOS screen
│   ├── EmergencyWidget.kt          # home-screen SOS widget
│   ├── FeedStyler.kt               # feed visual language
│   ├── MeshZone.kt                 # geographic hierarchy
│   ├── GeoLocator.kt               # GPS → rolling ZIP
│   ├── MessageChannel.kt           # channel + geo envelope fields
│   └── MainActivity.kt             # Area ▼, Chats, bottom nav, composer
├── tools/                          # Python BLE tests + tools/install.cmd
├── docs/ARCHITECTURE.md
├── docs/UI-DESIGN.md
├── local.properties.example        # MAPS_API_KEY template
├── SECURITY.md
└── LICENSE
```

## Contributing

Issues and PRs welcome. For crypto or protocol changes, include a test in `app/src/test/` or `tools/pc_*_test.py`.

## License

[MIT](LICENSE) — Copyright (c) 2026 Dan Wimberly

Third-party: [BouncyCastle](https://www.bouncycastle.org/) (Ed25519), [MediaPipe GenAI](https://ai.google.dev/edge/mediapipe/solutions/genai) (optional LLM).
