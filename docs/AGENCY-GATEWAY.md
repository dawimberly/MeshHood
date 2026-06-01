# Agency gateway vs consumer app

MeshHood ships as **two Android apps** from one codebase:

| Flavor | Package | Role |
|--------|---------|------|
| **Consumer** | `com.meshhood` | Verify-only agency alerts; normal neighbor UI |
| **Gateway** | `com.meshhood.gateway` | Sign and publish official agency alerts |

Install consumer: `install.cmd` or `gradlew installConsumerDebug`  
Install gateway: `install_gateway.cmd` or `gradlew installGatewayDebug`  
Signing key setup: `tools\setup_agency_gateway.cmd`

---

## Why consumer does not show gateway alerts on one phone

This is expected when **both APKs are on the same device** and you publish from the gateway app while watching the consumer app.

### 1. Separate apps = separate MeshService and feed storage

Each flavor is its own application:

- Own process, own foreground `MeshService`, own `SharedPreferences` (`meshhood_store`)
- Own BLE advertiser/GATT server, own WiFi Direct stack, own LAN mDNS name

Publishing in the gateway app **does** update the gateway’s local feed immediately (`publishAgencyAlert` → `handleIncoming` → `handleAgency` → `appendLog`). That write never crosses into `com.meshhood`’s storage.

### 2. `publishAgencyAlert` only reaches consumer via mesh relay

After the gateway displays the alert locally, `handleAgency` calls `relay` → `flood` → `notifySubscribers`, which sends on LAN / WiFi Direct / BLE **from the gateway app’s transports only**.

The consumer app receives the alert only if one of those transports delivers the envelope to **its** `MeshService.onTransportBytes` → `handleIncoming`. There is no Android IPC between the two packages today (no shared content provider, no explicit intent to wake consumer).

### 3. Same device: radios rarely link the two apps

| Transport | Same-phone gateway → consumer |
|-----------|-------------------------------|
| **BLE** | Two apps can both advertise, but a phone seldom discovers/connects to **itself** as a peer. Status often stays “waiting to connect”. |
| **WiFi Direct** | Same limitation — P2P self-link is unreliable or impossible on one handset. |
| **LAN (mDNS)** | Both may be on the same Wi‑Fi, but the gateway node often links first to a **PC** running `tools/mesh_lan.py` or `pc_transport_matrix.py` (high bandwidth test path), not to the consumer APK. Consumer stays “searching” while gateway shows “N linked” to the PC. |

So: **gateway sent** toast + alert visible in gateway’s “Open full app” feed ≠ consumer feed updated.

### 4. Gateway “Open full app” is the same app instance

The gateway APK includes `MainActivity`. **Open full app** opens the neighbor UI inside **`com.meshhood.gateway`**, bound to the **same** `MeshService` that just published. Alerts appear there without any cross-app relay.

That is the correct way to preview the public feed on a single phone when testing the gateway edition.

---

## How to test agency alerts

| Goal | Path |
|------|------|
| **Consumer UI only (no mesh)** | Debug consumer build + `tools\inject_agency.cmd "Your alert text"` — adb broadcast to `com.meshhood.DEBUG_INJECT_ENVELOPE` |
| **Gateway publish + see feed on same device** | Gateway app → send alert → **Open full app** (gateway package MainActivity) |
| **True gateway → consumer relay** | **Second device** (phone, emulator, or PC LAN peer) with consumer (or gateway in peer mode) linked over BLE, WiFi Direct, or LAN |
| **Phone + PC matrix** | `cd tools` → `python pc_transport_matrix.py` (or `--lan-only` / `--ble-only`); see [TRANSPORT-TESTS.md](TRANSPORT-TESTS.md) |
| **Emulator consumer without phone** | Start AVD → `tools\emulator_install.cmd`; inject with `inject_agency.cmd` — see [EMULATOR.md](EMULATOR.md) |

### `inject_agency.cmd` (consumer debug)

Signs a trusted agency envelope with the dev key (`tools/sign_agency.py`, pinned in `agency_trust.json`) and injects into whichever adb device is selected:

```cmd
tools\inject_agency.cmd "Shelter open at City Hall until 8pm"
```

Requires a **debug** consumer build (`installConsumerDebug`). Does not exercise gateway signing or mesh flood — only verifies consumer verification + feed UI.

---

## Phone required vs emulator OK

| Task | Phone | Emulator |
|------|-------|----------|
| Consumer feed / agency verify UI | Optional | OK (debug + inject) |
| Gateway signing + publish UI | Optional | OK (limited mesh) |
| Gateway → consumer **mesh** relay | **Best: 2 phones** | Partial (2 AVDs or AVD + PC LAN) |
| BLE / WiFi Direct mesh | **Real phone(s)** | Poor / not supported |
| LAN mesh to PC | Phone or emulator on same Wi‑Fi as PC | Emulator OK (`mesh_lan.py`) |
| Production-style disaster drill (multi-hop, radios) | **Real devices** | Not a substitute |

**Summary:** Use **phone + phone** for full mesh behavior. Use **phone + PC** for LAN/BLE transport proofs. Use **emulator** for UI, agency verify, and LAN-to-host development. A serious field test needs real radios and multiple handsets.

---

## Optional future improvement (not implemented)

Cross-package wake on same device (explicit `Intent` from gateway to consumer, or a shared debug bridge) would not match production mesh semantics and is out of scope for now. Document and test via relay paths above instead.

Gateway local feed after publish is already correct via `handleIncoming`; no code change required for gateway-side display.
