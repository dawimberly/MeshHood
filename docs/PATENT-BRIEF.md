# Patent brief — MeshHood

**For provisional patent discussion.** Not legal advice. Review with patent counsel before filing or disclosing publicly.

**Suggested counsel (San Antonio):** Outlier Patent Attorneys — software / network / cybersecurity focus.

---

## Working title

**Autonomous Multi-Radio Mesh Communication System with Dynamic Transport Selection and Emergency Activation**

---

## Problem

Centralized communication fails progressively in disasters (cell → WiFi → nothing). Existing mesh apps typically bind to **one transport** and require manual setup. There is no unified layer that treats Bluetooth, WiFi, and cellular as interchangeable pipes with automatic degradation and bridge nodes across heterogeneous devices.

---

## Proposed core claims (draft)

1. **Transport abstraction** — Route messages across Bluetooth, WiFi, and cellular concurrently; select/switch channels by availability and quality without user action.

2. **Graceful degradation** — Detect infrastructure loss; progressively fall back (e.g. cell → local WiFi → Bluetooth mesh) while maintaining session continuity where possible.

3. **Emergency activation** — Enable mesh relay and discovery when degradation is detected, without login or manual configuration.

4. **Heterogeneous bridge nodes** — Use intermediate devices with multiple radios to translate between protocols (e.g. Bluetooth-only ↔ WiFi-only peers).

5. **Third-party device integration** — Enroll licensed smart-home / IoT devices as passive or active relays (Echo, HomePod, Nest, etc.) via manufacturer agreement.

6. **Local AI on mesh** — Maintain and query an on-device model populated from mesh-local data without cloud dependency during outages.

---

## One-sentence claim (provisional summary)

A method for autonomous multi-radio mesh networking that dynamically routes communications across Bluetooth, WiFi, and cellular channels based on availability, without user intervention.

---

## Prior art to discuss with attorney

| Product / system | Overlap | Differentiation |
|------------------|---------|-----------------|
| Bridgefy | Bluetooth mesh | Multi-transport + auto degradation + emergency + local AI |
| Briar | BT / WiFi, no servers | Unified transport layer + IoT bridges + disaster policy |
| Serval Mesh | WiFi mesh | Consumer phone stack + signed profiles + area geo |
| Apple Find My | Passive BT relay | Open emergency use + explicit mesh messaging |
| FirstNet | First-responder LTE | Civilian mesh + offline-first + no carrier lock-in |

---

## Implementation status (honest disclosure)

**Implemented in MeshHood prototype:**

- Parallel **BLE GATT**, **WiFi Direct**, and **LAN/mDNS** transports with shared JSON protocol
- Multi-hop flood relay (TTL + deduplication)
- Encrypted direct messages; Ed25519 signed profiles, kudos, capacity vouches
- Geographic **Area** channels + **Chats** inbox
- Profile photos with signed thumbnail broadcast and neighbor **photo vouch**
- Optional on-device LLM coordinator

**Not yet implemented (roadmap — can be in provisional as intended claims):**

- Cellular as mesh transport / uplink
- Automatic emergency-mode activation on infrastructure loss
- Consumer router emergency SSID integration
- Licensed third-party smart-speaker relay firmware

---

## Monday call checklist (Outlier / patent counsel)

- [ ] Provisional vs utility timeline and budget (~$320 USPTO provisional filing fee + attorney fees)
- [ ] Which claims to include in provisional vs follow-on continuations
- [ ] Prior art search scope
- [ ] What **not** to publish before filing (pitch decks, GitHub details, partner meetings)
- [ ] Assignment / ownership (individual vs LLC)
- [ ] International (PCT) strategy if federal partnerships are likely

**Open with:** *"Multi-radio disaster mesh for Android phones — need provisional before Amazon/government conversations."*

**Bring:** GitHub link, [ARCHITECTURE.md](ARCHITECTURE.md), transport diagram (three radios → MeshService → relay).

---

## Transport diagram (ASCII)

```
 Phone A          Bridge phone         Phone B
 [ WiFi only ] ←→ [ BT + WiFi ] ←→ [ BT only ]
       │                │                │
       └────────────────┴────────────────┘
                    MeshService
              (decrypt · dedupe · relay)
```

---

## Related docs

- [PITCH.md](PITCH.md) — investor and partner narrative
- [ARCHITECTURE.md](ARCHITECTURE.md) — current technical design
