# CLAUDE.md — AI assistant guide for MeshHood

Concise context for Claude (and similar AI tools) working in this repo. Human onboarding: see [README.md](README.md).

---

## 1. Project summary

**MeshHood** is an experimental Android app for **serverless hyperlocal disaster mesh networking**. When cell towers and internet fail, phones form a **phone-to-phone mesh** over Bluetooth LE, WiFi Direct, and LAN (mDNS)—no account, no cloud, no central server.

**Mental model:** *Walking Dead safety model* — your block stays connected when everything else goes dark. Hyperlocal scopes (state, ZIP, neighborhood), not a global social network.

- **Status:** Research prototype. Not certified for life-safety use. See [SECURITY.md](SECURITY.md).
- **License:** MIT — Copyright (c) 2026 Dan Wimberly
- **Author context:** Dan Wimberly, Goleta CA. Dark utilitarian UI; prefer simple, readable code over abstraction.
- **CI:** GitHub Actions on `main` — `./gradlew test` ([`.github/workflows/ci.yml`](.github/workflows/ci.yml))

---

## 2. Two apps (one codebase)

Product flavor dimension: `edition`.

| Flavor | Package | Role |
|--------|---------|------|
| **consumer** | `com.meshhood` | Neighbor mesh UI; **verify-only** official agency alerts |
| **gateway** | `com.meshhood.gateway` | Sign/publish agency alerts; mesh hub on home Wi‑Fi |

Build flags (`app/build.gradle.kts`):

- `BuildConfig.AGENCY_GATEWAY` — `false` (consumer) / `true` (gateway)
- `BuildConfig.AGENCY_SIGNING_KEY` — empty on consumer; dev Ed25519 key on gateway (from `local.properties`)

**Important:** Consumer and gateway on the **same phone** are separate processes/radios. Publishing from gateway does not update consumer feed over mesh on that device. See [docs/AGENCY-GATEWAY.md](docs/AGENCY-GATEWAY.md).

---

## 3. Build and install

### Windows (primary dev path)

| Command | Purpose |
|---------|---------|
| `install.cmd` | Build + install **consumer** debug; waits for adb |
| `install_gateway.cmd` | Build + install **gateway** debug |
| `run.cmd` | Install when device already connected |
| `connect-wifi.cmd` | Wi‑Fi adb pairing |

### Gradle (any OS)

```bash
./gradlew installConsumerDebug
./gradlew installGatewayDebug
./gradlew test
```

### Flavors / SDK

- `compileSdk` / `targetSdk`: 35 · `minSdk`: 26
- Maps key via `manifestPlaceholders` + `BuildConfig.MAPS_API_KEY`
- Protobuf lite generated from `app/src/main/proto/meshhood.proto`

### First-time gateway

Run `tools\setup_agency_gateway.cmd` before building gateway flavor (writes dev signing key to `local.properties`).

---

## 4. Architecture

Full protocol detail: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

```
BLE GATT ──┐
WiFi Direct ├──► MeshService ──► MainActivity / Coordinator / persistence
LAN/mDNS ──┘         │
              State Sync, relay (TTL), dedup
              CellularTransport (gateway only)
```

### MeshService (core)

Foreground service in `MeshService.kt`. Owns:

- **BLE GATT** server + advertiser (inline in MeshService, not a separate class)
- **WifiDirectTransport** — P2P radio path
- **LanTransport** — mDNS discovery; `ROLE_GATEWAY` vs `ROLE_PEER`
- **CellularTransport** — gateway-only HTTP relay uplink
- Message decrypt/parse, TTL relay, dedup, feed persistence

### Transports

| Transport | Class | Notes |
|-----------|-------|-------|
| BLE | `MeshService` (GATT) | Always on; lowest bandwidth |
| WiFi Direct | `WifiDirectTransport.kt` | Needs two phones |
| LAN | `LanTransport.kt` | Easiest test on shared Wi‑Fi |
| Cellular uplink | `CellularTransport.kt` + `CellularUplink.kt` | Gateway + relay URL only |

### State Sync v1

`StateSync.kt` — on LAN/WiFi Direct TCP connect, peers exchange `syncreq`/`syncresp` and replay missed envelopes. See [docs/STATE-SYNC.md](docs/STATE-SYNC.md).

### Wire format

- Default: **JSON envelopes** encrypted with `Crypto.kt` (AES-GCM layer)
- Optional: **Protobuf** on LAN/WiFi Direct after capability negotiation (`MeshSerializer.kt`, `meshhood.proto`). JSON stays default for BLE and debug tools. See [docs/PROTOBUF.md](docs/PROTOBUF.md).

### Crypto and trust

| Module | Role |
|--------|------|
| `Crypto.kt` | Transport encryption |
| `DeviceKeys.kt` / `SignKeys.kt` | X25519 DMs, Ed25519 signing |
| `AgencyTrust.kt` / `AgencySigner.kt` | Verify/sign official agency alerts |
| BouncyCastle | Ed25519 (Android Conscrypt lacks raw Ed25519 import) |

### UI shell

- `MainActivity.kt` — bottom nav: **Home** (feed) · **Nearby** (map) · **Resources** (coordinator) · **Alert** (SOS)
- `NetworkReadiness.kt` — status strip: Ready / Limited / Searching / Offline
- `Coordinator.kt` — needs/offers matching; optional on-device LLM via MediaPipe GenAI (`LlmEngine.kt`), rule-based fallback

### Geo and channels

- **Geo:** `MeshZone.kt`, `GeoLocator.kt`, `ZoneContext.kt`, `ZoneRouter.kt`
- **Comms:** `MessageChannel.kt` — envelope `channel`, `origin`, `routeClass`
- **Mutual location:** `MutualLocation.kt`, `PeerLocationStore.kt` — pairwise consent; emergency always attaches live GPS

---

## 5. Key files map

| Path | Purpose |
|------|---------|
| `app/build.gradle.kts` | Flavors, Maps/signing keys, protobuf |
| `app/src/main/java/com/meshhood/MeshService.kt` | Mesh hub: transports, relay, sync, feed |
| `app/src/main/java/com/meshhood/MainActivity.kt` | Primary UI, Area/Chats, navigation |
| `app/src/main/java/com/meshhood/StateSync.kt` | LAN/WiFi Direct catch-up handshake |
| `app/src/main/java/com/meshhood/MeshSerializer.kt` | JSON ↔ protobuf envelope encoding |
| `app/src/main/java/com/meshhood/Crypto.kt` | AES-GCM transport crypto |
| `app/src/main/java/com/meshhood/LanTransport.kt` | mDNS LAN mesh |
| `app/src/main/java/com/meshhood/WifiDirectTransport.kt` | WiFi Direct mesh |
| `app/src/main/java/com/meshhood/CellularTransport.kt` | Gateway HTTP relay client |
| `app/src/main/java/com/meshhood/NetworkReadiness.kt` | Network status aggregation |
| `app/src/main/java/com/meshhood/Coordinator.kt` | Resource coordinator / needs matching |
| `app/src/main/java/com/meshhood/AgencyTrust.kt` | Consumer agency alert verification |
| `app/src/main/java/com/meshhood/AgencySigner.kt` | Gateway agency alert signing |
| `app/src/main/java/com/meshhood/GatewayHeadlessEntry.kt` | Boot → headless gateway hub |
| `app/src/main/java/com/meshhood/MapsHelper.kt` | Maps SDK + Google Maps handoff |
| `app/src/main/java/com/meshhood/EmergencyActivity.kt` | SOS flow |
| `app/src/main/proto/meshhood.proto` | Protobuf wire schema |
| `tools/mesh_lan.py` | PC ↔ phone LAN integration tests |
| `tools/cellular_relay.py` | Local HTTP relay for cellular uplink dev |
| `tools/mesh_crypto.py` | Python mirror of phone crypto/envelopes |
| `tools/inject_agency.cmd` | Inject signed agency alert (debug consumer) |
| `tools/inject_emergency.cmd` | Inject mesh-only test emergency (debug) |
| `tools/setup_maps.cmd` | Write Maps API key to `local.properties` |
| `tools/setup_agency_gateway.cmd` | Dev gateway signing key setup |
| `app/src/test/java/com/meshhood/` | Unit tests (Robolectric + JUnit) |

All Kotlin sources live in a **flat** `app/src/main/java/com/meshhood/` package (no subpackages).

---

## 6. Docs index

| Document | One-line description |
|----------|---------------------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Protocol design, message types, geo vs comms layers |
| [docs/GATEWAY-SETUP.md](docs/GATEWAY-SETUP.md) | Spare-phone gateway hub walkthrough |
| [docs/GATEWAY-HEADLESS.md](docs/GATEWAY-HEADLESS.md) | Headless hub on boot; UI always reopenable |
| [docs/STATE-SYNC.md](docs/STATE-SYNC.md) | LAN/WiFi Direct catch-up sync (v1) |
| [docs/PROTOBUF.md](docs/PROTOBUF.md) | Binary wire format + capability negotiation |
| [docs/CELLULAR-UPLINK.md](docs/CELLULAR-UPLINK.md) | Gateway HTTP relay over mobile data |
| [docs/PRIVACY-SAFETY.md](docs/PRIVACY-SAFETY.md) | Location consent and emergency override |
| [docs/AGENCY-GATEWAY.md](docs/AGENCY-GATEWAY.md) | Consumer vs gateway; relay testing |
| [docs/TRANSPORT-TESTS.md](docs/TRANSPORT-TESTS.md) | What one phone + PC can prove vs two phones |
| [docs/EMULATOR.md](docs/EMULATOR.md) | AVD setup on Windows |
| [docs/UI-DESIGN.md](docs/UI-DESIGN.md) | Dark utilitarian UI direction (Nextdoor warmth + mesh) |
| [docs/PITCH.md](docs/PITCH.md) | Investor/partner talking points |
| [docs/PATENT-BRIEF.md](docs/PATENT-BRIEF.md) | Provisional patent discussion brief |
| [docs/LORA-DONGLE-PROMPT.md](docs/LORA-DONGLE-PROMPT.md) | Copy-paste prompt for external LoRa dongle integration analysis |

---

## 7. Debug and testing

### Two Android devices (recommended)

1. `install.cmd` on both; same Wi‑Fi for LAN
2. Open app; confirm linked peers in status strip
3. Broadcast on one → verify feed on other after State Sync
4. Logcat: `adb logcat -s StateSync LanTransport MeshSerializer`

WiFi Direct needs two phones. LAN is easiest on a shared router.

### Single device + PC

```bash
cd tools
pip install -r requirements.txt
python mesh_lan.py                  # discover phones, send frames
python pc_transport_matrix.py       # BLE + LAN smoke tests
python pc_transport_matrix.py --auto
```

### Inject test data (debug builds, adb)

| Script | Purpose |
|--------|---------|
| `tools\inject_agency.cmd "Shelter open until 8pm"` | Signed agency alert into consumer feed |
| `tools\inject_emergency.cmd` | Mesh-only test emergency (default Goleta coords) |
| `tools\inject_emergency.cmd 34.43 -119.82` | Emergency at custom lat/lon |

### Cellular uplink dev

```bash
cd tools
python cellular_relay.py            # local HTTP relay
# Gateway app: Gateway mode ON + Relay base URL → http://<PC-IP>:8765
```

### Unit tests

```bash
./gradlew test
```

Key test files: `CryptoTest`, `MeshSerializerTest`, `StateSync` (via serializer), `AgencyTrustTest`, `NetworkReadinessTest`.

### Emulator

UI and agency verification only—not a substitute for BLE/WiFi Direct between real handsets. See [docs/EMULATOR.md](docs/EMULATOR.md).

---

## 8. Secrets (never commit)

`local.properties` is **gitignored**. Copy from `local.properties.example`.

| Key | Purpose | Setup |
|-----|---------|-------|
| `sdk.dir` | Android SDK path | Android Studio default |
| `MAPS_API_KEY` | Google Maps SDK for Android | `tools\setup_maps.cmd` — restrict to `com.meshhood` |
| `AGENCY_SIGNING_KEY` | Gateway dev Ed25519 key | `tools\setup_agency_gateway.cmd` |

Never paste real keys into commits, docs, or chat logs.

---

## 9. UI and conventions

- **Theme:** Dark, utilitarian, no ads, no vanity metrics, no light theme. See [docs/UI-DESIGN.md](docs/UI-DESIGN.md).
- **Code style:** Keep changes minimal and localized. Match existing flat-package Kotlin patterns. Avoid over-abstraction.
- **Comments:** Only for non-obvious business logic.
- **Tests:** Add under `app/src/test/` for crypto, protocol, or routing changes.
- **Python tools:** Mirror phone crypto in `tools/mesh_crypto.py`; keep envelope format in sync.

---

## 10. Common tasks

### Add a feature

1. Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) if touching protocol or message types
2. Implement in `com.meshhood` (service logic → `MeshService.kt`; UI → `MainActivity.kt` or dedicated Activity)
3. Add unit test if crypto/routing/serialization involved
4. `./gradlew test` then `install.cmd` on device

### Fix maps

1. Confirm `MAPS_API_KEY` in `local.properties` (`tools\setup_maps.cmd`)
2. Google Cloud Console: enable **Maps SDK for Android**; restrict key to `com.meshhood` + debug SHA-1
3. Rebuild consumer: `install.cmd`
4. In-app map in **Nearby** tab; handoff via `MapsHelper.kt` → "Open in Google Maps"

### Gateway setup

1. `tools\setup_agency_gateway.cmd` → `install_gateway.cmd`
2. Open **MeshHood Gateway** → grant permissions → **Gateway mode** ON
3. Optional: **Run headless on boot** for spare phone on charger + home Wi‑Fi
4. Consumers on same Wi‑Fi should show **WiFi LAN: gateway**
5. Full guide: [docs/GATEWAY-SETUP.md](docs/GATEWAY-SETUP.md)

### Headless gateway

- `GatewayHeadlessEntry.kt` starts `MeshService` on boot when enabled
- UI is **never locked out** — reopen from launcher, notification, or adb
- Details: [docs/GATEWAY-HEADLESS.md](docs/GATEWAY-HEADLESS.md)

### Protocol or crypto change

1. Update Kotlin + `tools/mesh_*.py` together
2. Update `docs/ARCHITECTURE.md` and/or `docs/PROTOBUF.md` if wire format changes
3. Extend `MeshSerializerTest` / `CryptoTest` / PC transport matrix

---

## Quick reference

```
Repo:     https://github.com/dawimberly/MeshHood
Consumer: com.meshhood          → install.cmd
Gateway:  com.meshhood.gateway  → install_gateway.cmd (after setup_agency_gateway)
Mesh log: adb logcat -s StateSync LanTransport MeshSerializer MeshService
```

When unsure, read README.md first, then the relevant doc from section 6 above.
