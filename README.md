# MeshHood

Serverless hyperlocal mesh communication for disaster resilience — Android-first.

Neighbors discover each other over **Bluetooth LE**, **WiFi Direct**, and **LAN** (same WiFi router). Messages relay phone-to-phone with encryption. No cell tower or internet required.

## Emergency-first

- One-tap **Emergency — Need Help** broadcasts your alert and ICE card to the mesh
- End-to-end encrypted neighborhood traffic; sealed direct messages for private chat
- Persistent feed, profiles, groups, and reputation survive restarts

## Features

- Multi-hop relay (TTL + dedup) — messages hop across phones to extend range
- Per-device X25519 encryption for true private DMs
- Signed profiles, kudos/reputation, capacity vouching, reciprocity ratings
- Groups with founder/admin overlay (verify credentials, pin announcements — no speech policing)
- Feed scope tabs: **Everyone** vs per-group channels
- Optional on-device LLM (MediaPipe Gemma) for resource matching and triage summaries
- Python BLE tools in `tools/` for PC ↔ phone testing

## Build

1. Open in Android Studio (or use Gradle)
2. SDK 35, minSdk 26
3. Connect an Android phone with Bluetooth + location permissions

```bash
./gradlew installDebug
```

## PC test tools

From `tools/` (Python 3, `bleak`, `cryptography`):

```bash
pip install bleak cryptography
python pc_test.py          # basic mesh test
python pc_group_test.py    # groups + admin overlay
```

Phone must be running MeshHood with BLE advertising active.

## License

TBD
