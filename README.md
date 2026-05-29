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
| **Emergency SOS** | One tap broadcasts help + your ICE medical card to the mesh |
| **Multi-transport** | BLE + WiFi Direct + LAN run in parallel for best reach |
| **Multi-hop relay** | Messages hop across phones (TTL + dedup) to extend range |
| **Private DMs** | X25519 per-pair encryption; relays cannot read content |
| **Chats inbox** | Private threads in **Chats** — separate from the public **Area** feed |
| **Area hierarchy** | Nation → national region → state → region → **rolling ZIP** → local |
| **Smart default feed** | Opens on your most specific locality (ZIP when location resolves) |
| **Signed trust** | Ed25519 profiles, kudos, group admin actions |
| **Groups** | Community crews with admin verify + pin (no speech moderation) |
| **Resource brain** | Rule-based coordinator + optional on-device Gemma LLM |

## UI: Area vs Chats

| Control | What it is |
|---------|------------|
| **Area ▼** | Public feed — Everyone, geographic levels, and groups |
| **Chats** | Private direct-message inbox (most recent first) |

Public broadcasts stay in **Area**. DMs never appear in the Area dropdown.

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

Anyone with a Windows PC + USB Android phone can use this repo:

```powershell
git clone https://github.com/dawimberly/MeshHood.git
cd MeshHood

# Install app on phone (USB debugging on, MeshHood will open)
.\install.cmd

# Optional: simulate Maria + Rosa over BLE (phone running MeshHood)
cd tools
pip install -r requirements.txt
python pc_two_phone_sim.py
```

| File | Purpose |
|------|---------|
| `install.ps1` / `install.cmd` | Build + install APK; finds Android Studio Java |
| `tools/install.cmd` | Same install when your shell is in `tools\` |
| `tools/pc_two_phone_sim.py` | Guided 2-phone BLE sim (Maria + Rosa) |

### Android

**Requirements:** Android 8+ (API 26), Bluetooth, location permission (required for BLE scan and rolling ZIP on Android).

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
3. Leave the app running — the foreground service keeps the mesh alive.

Send to **Everyone** for the public Area feed; use **Chats** for private messages.

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
python pc_maria_reply.py             # stable Maria identity for DM tests
```

Covers Area feed, DMs, directory, profiles, emergency + ICE. Does **not** test WiFi Direct between two phones or 3-hop relay (needs 3 physical devices).

Persistent simulated identities live in `tools/sim_identities/` (gitignored).

## Project layout

```
MeshHood/
├── install.ps1 / install.cmd   # Windows: build + install on phone
├── app/src/main/java/com/meshhood/
│   ├── MeshService.kt      # mesh core, feed scopes, protocol
│   ├── MeshZone.kt         # geographic hierarchy
│   ├── GeoLocator.kt       # GPS → rolling ZIP
│   ├── MessageChannel.kt   # channel + geo envelope fields
│   └── MainActivity.kt     # Area ▼, Chats inbox, profile
├── tools/                  # Python BLE tests + tools/install.cmd
├── docs/ARCHITECTURE.md
├── SECURITY.md
└── LICENSE
```

## Contributing

Issues and PRs welcome. For crypto or protocol changes, include a test in `app/src/test/` or `tools/pc_*_test.py`.

## License

[MIT](LICENSE) — Copyright (c) 2026 Dan Wimberly

Third-party: [BouncyCastle](https://www.bouncycastle.org/) (Ed25519), [MediaPipe GenAI](https://ai.google.dev/edge/mediapipe/solutions/genai) (optional LLM).
