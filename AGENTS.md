# AGENTS.md

## Cursor Cloud specific instructions

### Product overview

MeshHood is an **Android-only** Kotlin app (Gradle) with two product flavors: **consumer** (`com.meshhood`) and **gateway** (`com.meshhood.gateway`). Optional **Python tools** under `tools/` support LAN mesh simulation and a dev HTTP cellular relay. There is no Node backend or Docker stack in this repo.

### Android SDK (required for Gradle)

The Android SDK is **not** vendored in git. Cloud VMs expect:

- `sdk.dir` in `local.properties` (copy from `local.properties.example`; typically `sdk.dir=/home/ubuntu/Android/Sdk`)
- Packages: `platforms;android-35`, `build-tools;35.0.0`, `platform-tools`
- Shell: `ANDROID_HOME` / `ANDROID_SDK_ROOT` pointing at that SDK (see `~/.bashrc` on configured VMs)

JDK **17+** is required (CI uses Temurin 17; JDK 21 works locally).

### Build and test commands

| Task | Command |
|------|---------|
| Unit tests (CI target) | `./gradlew test --no-daemon` |
| Gateway unit tests only | `./gradlew :app:testGatewayDebugUnitTest --no-daemon` |
| Consumer debug APK | `./gradlew assembleConsumerDebug --no-daemon` |
| Gateway debug APK | `./gradlew assembleGatewayDebug --no-daemon` |
| Install on device | `./gradlew installConsumerDebug` or `installGatewayDebug` (requires `adb` + device) |

**Known compile issue (consumer flavor):** As of this setup, `assembleConsumerDebug` / full `./gradlew test` fail because `MeshService.kt` references `R.string.gateway_notification_open_official_alerts`, which exists only under `app/src/gateway/res/`. Gateway flavor builds and tests succeed. Use gateway tasks until that resource is moved or shared.

Optional keys in `local.properties`: `MAPS_API_KEY`, `AGENCY_SIGNING_KEY` (gateway signing; see `tools/setup_agency_gateway.ps1`).

### Python tools

```bash
pip install -r tools/requirements.txt
python tools/cellular_relay.py --host 127.0.0.1 --port 8765 --token demo
```

Relay API: `POST /v1/push`, `GET /v1/pull` — see `docs/CELLULAR-UPLINK.md`. `mesh_lan.py` / `pc_transport_matrix.py` need a phone on the same LAN or PC Bluetooth (not available in a headless cloud VM without devices).

### Running the app end-to-end

Full mesh behavior needs **physical Android devices** (or an AVD with limits — no BLE/Wi‑Fi Direct between phones). In cloud VMs, validate the environment with **Gradle unit tests**, **APK assembly** (gateway), and **cellular_relay.py** HTTP smoke tests rather than installing to a device.

### Lint

No dedicated Android lint task is wired in CI. CI only runs `./gradlew test`. Use Android Studio / `./gradlew lint` locally if needed.
