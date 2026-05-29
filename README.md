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
| **Signed trust** | Ed25519 profiles, kudos, group admin actions |
| **Groups** | HOA/block crews with admin verify + pin (no speech moderation) |
| **Feed channels** | Toggle **Everyone** vs per-group views |
| **Resource brain** | Rule-based coordinator + optional on-device Gemma LLM |

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for message types, transport diagram, and protocol overview.

```
Phone A ←BLE/WiFi/LAN→ Phone B ←→ Phone C
         encrypted JSON envelopes, relay by id+ttl
```

## Quick start

### Android

**Requirements:** Android 8+ (API 26), Bluetooth, location permission (required for BLE scan on Android).

```bash
git clone https://github.com/dawimberly/MeshHood.git
cd MeshHood
./gradlew installDebug
```

Open MeshHood on your phone, grant permissions, and leave it running — the foreground service keeps the mesh alive.

### PC test harness (optional)

Test against one phone from your computer over BLE:

```bash
cd tools
pip install -r requirements.txt
python pc_test.py          # handshake + broadcast
python pc_group_test.py    # groups + admin overlay
python pc_demo.py          # scripted neighborhood demo
```

The phone must be running MeshHood with Bluetooth advertising active.

## Project layout

```
MeshHood/
├── app/src/main/java/com/meshhood/   # Android app + MeshService
├── tools/                            # Python BLE protocol tests
├── docs/ARCHITECTURE.md
├── SECURITY.md
└── LICENSE
```

## Contributing

Issues and PRs welcome. For crypto or protocol changes, include a test in `app/src/test/` or `tools/pc_*_test.py`.

## License

[MIT](LICENSE) — Copyright (c) 2026 Dan Wimberly

Third-party: [BouncyCastle](https://www.bouncycastle.org/) (Ed25519), [MediaPipe GenAI](https://ai.google.dev/edge/mediapipe/solutions/genai) (optional LLM).
