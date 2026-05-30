# Transport test matrix (phone + PC)

What you can prove with **one Android phone** and **one PC**, vs what needs more hardware.

## Radios in MeshHood today

| Transport | Protocol | Phone ↔ PC | Phone ↔ Phone | Notes |
|-----------|----------|------------|---------------|-------|
| **Bluetooth LE** | GATT notify/write | ✅ `tools/pc_*.py` | ✅ | PC simulates Maria/Rosa |
| **WiFi LAN** | mDNS `_meshhood._tcp` + TCP | ✅ `pc_transport_matrix.py` | ✅ | Same home/community Wi‑Fi |
| **WiFi Direct** | Android P2P | ❌ | ✅ | PC cannot join P2P group |
| **Cellular** | SMS emergency + link monitor | ⏭ partial | ⏭ | ICE SMS fallback; peer relay TBD |

Every outgoing message on the phone is **fanned out** on all active transports (`MeshService.notifySubscribers`). Incoming messages are **deduped by message id** so LAN + BLE duplicates are harmless.

## Run all testable iterations

```cmd
cd C:\Users\Owner\AndroidStudioProjects\MeshHood\tools
pip install -r requirements.txt
python pc_transport_matrix.py
```

Auto (no Enter prompts):

```cmd
python pc_transport_matrix.py --auto
```

Only BLE or only LAN:

```cmd
python pc_transport_matrix.py --ble-only
python pc_transport_matrix.py --lan-only
```

### Before you run

1. Open **MeshHood** on the phone; wait for status (Advertising / Connected / transport lines).
2. **BLE test:** Bluetooth on; PC Bluetooth on.
3. **LAN test:** Phone and PC on the **same Wi‑Fi** (e.g. home router).
4. **Strict LAN-only proof (optional):** Phone → Airplane mode **ON**, Wi‑Fi **ON**, Bluetooth **OFF** → run `--lan-only`.

### What to look for on the phone

| Test | Success signals |
|------|-----------------|
| BLE | Maria/Rosa in Directory; messages in Area or Chats |
| LAN | Status includes `WiFi LAN: N linked`; Rosa LAN test message in feed |
| WiFi Direct | `WiFi P2P: …` when near a 2nd MeshHood phone |
| Cellular | N/A until uplink transport is built |

## Scenarios from the pitch (honest status)

| Scenario | Testable now? | How |
|----------|---------------|-----|
| A = WiFi only, B = BT only, bridge phone | Partial | 2+ Android phones; PC cannot play A or B |
| A = WiFi, B = BT, C = cell | Partial | Cell leg not built; WiFi+BT legs testable |
| Katrina density / multi-hop | Partial | `pc_relay_test.py` + 3 physical devices for true 3-hop |
| Infrastructure degrades cell → WiFi → BT | Manual | Turn off radios on phone in order; watch status line |

## Related tools

| Script | Purpose |
|--------|---------|
| `pc_two_phone_sim.py` | Full guided BLE sim (Maria + Rosa, DMs, emergency) |
| `pc_transport_matrix.py` | BLE + LAN transport smoke tests |
| `pc_test.py` | Handshake + DM privacy demo |

See [ARCHITECTURE.md](ARCHITECTURE.md) for protocol details.
