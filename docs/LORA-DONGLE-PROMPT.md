# LoRa USB-C dongle integration prompt

Copy-paste this entire prompt into Claude, ChatGPT, or another external AI assistant for a codebase-aware LoRa dongle integration analysis.

---

I have built an emergency mesh network app for phones in **Kotlin / Android** (Android SDK 35, minSdk 26, product flavors **consumer** `com.meshhood` and **gateway** `com.meshhood.gateway`).

The app currently supports **Bluetooth LE GATT mesh, WiFi Direct, LAN/mDNS discovery, gateway headless hub mode, encrypted JSON/Protobuf messaging (broadcast, DMs, groups, crew), State Sync v1 catch-up on peer connect, agency signed alerts (gateway), SOS/emergency with Google Maps location links, mutual location sharing with emergency override, feed with Recent/Nearby sort, in-app Google Maps with emergency facility pins and SB County junction-box planning pins, network readiness indicator, cellular uplink relay (gateway), and offline-first local persistence** — no central server required.

I want to add support for a **LoRa USB-C dongle** (RAK811 / SX1276 over USB-serial) as a companion hardware module for much longer range and better off-grid performance in disaster scenarios.

**Key goals:**
- Plug-and-play USB-C LoRa support (primarily Android first, iOS later)
- Seamless integration with existing messaging, location, and mesh features
- Leverage or integrate with the Meshtastic protocol/stack where it makes sense
- Good power management and battery awareness when the dongle is connected
- Auto-detection of the USB LoRa device
- Fallback to software mesh (Bluetooth/WiFi/LAN) when no dongle is present

Please analyze my current codebase and suggest:

1. High-level architecture changes needed (new modules, services, communication layers)
2. Recommended libraries or existing open-source solutions (e.g. serial communication, Meshtastic client libs)
3. How to handle USB serial communication with the LoRa module (permissions, connection lifecycle, reading/writing packets)
4. Protocol design: How to abstract the transport layer so the app works with both software mesh and LoRa hardware
5. UI/UX improvements for hardware mode (connection status, signal strength, range indicators)
6. Potential challenges (Android USB OTG quirks, power draw, antenna limitations, encryption compatibility) and how to solve them
7. Step-by-step implementation plan starting with the smallest useful integration

**Here is the relevant code structure:**

```
MeshHood/
├── app/
│   ├── build.gradle.kts          # consumer + gateway flavors, protobuf
│   └── src/
│       ├── main/java/com/meshhood/   # flat package
│       │   ├── MeshService.kt        # core mesh hub, all transports
│       │   ├── MainActivity.kt       # feed, map, SOS, UI
│       │   ├── LoRaTransport.kt      # RAK811 AT, TX queue, RX (EXISTS, not wired to MeshService)
│       │   ├── LoRaFramer.kt         # 222B limit, fragmentation, LoRaReassembler
│       │   ├── LanTransport.kt
│       │   ├── WifiDirectTransport.kt
│       │   ├── CellularTransport.kt / CellularUplink.kt
│       │   ├── Crypto.kt             # AES-GCM transport encryption
│       │   ├── MeshSerializer.kt     # JSON + protobuf (MH magic prefix)
│       │   ├── StateSync.kt          # LAN/WiFi Direct catch-up
│       │   ├── NetworkReadiness.kt / NetworkStatusIndicator.kt
│       │   ├── MapsHelper.kt / MapActivity.kt
│       │   ├── JunctionBoxStore.kt   # SB County relay site pins
│       │   └── AgencySigner.kt / AgencyTrust.kt
│       ├── main/assets/
│       │   └── junction_boxes.json
│       ├── main/proto/meshhood.proto
│       ├── consumer/                 # consumer-only stubs
│       └── gateway/                  # gateway UI, headless, signing
├── tools/
│   ├── mesh_lora.py                  # PC LoRa test tool
│   ├── mesh_lan.py
│   └── inject_agency.cmd / inject_emergency.cmd
├── docs/
│   ├── LORA-TRANSPORT.md
│   ├── MeshService_LoRa_patch.kt     # integration guide (not applied)
│   ├── GATEWAY-SETUP.md
│   ├── STATE-SYNC.md / PROTOBUF.md
│   └── JUNCTION-BOXES.md
├── CLAUDE.md
└── README.md
```

**Current key files:**
- `MeshService.kt` — foreground service; owns BLE, WiFi Direct, LAN, cellular; flood/relay/TTL/dedup; **LoRa not instantiated yet**
- `LoRaTransport.kt` — RAK811 P2P AT commands, 915 MHz SF10, sync word `0x12`, USB VID/PID detect, framer/reassembler wired in send/RX
- `LoRaFramer.kt` — `encode()` + `LoRaReassembler.onFrame()` for 222-byte air limit
- `LanTransport.kt` / `WifiDirectTransport.kt` — reference pattern for transport integration
- `Crypto.kt` / `MeshSerializer.kt` — wire format (encrypt before LoRa; protobuf preferred for size)
- `NetworkReadiness.kt` / `NetworkStatusIndicator.kt` — transport strip UI (no LoRa channel yet)
- `MapActivity.kt` / `JunctionBoxStore.kt` — map + planned SB County junction pins
- `docs/MeshService_LoRa_patch.kt` — intended MeshService hooks (needs rewrite to match current APIs)
- `tools/mesh_lora.py` — PC-side LoRa integration testing

**Known gaps:** no `usb-serial-for-android` in Gradle yet; USB open is TODO in `LoRaTransport`; no USB manifest/filter; LoRa not in `notifySubscribers()` / `onTransportBytes()`; Meshtastic air protocol **not** used (private sync word `0x12`, MeshHood envelopes only).

**Repo:** https://github.com/dawimberly/MeshHood
