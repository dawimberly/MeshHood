# State Sync v1 (Tier 2)

When two MeshHood nodes open a **LAN** or **WiFi Direct** TCP link, they exchange a JSON **sync handshake** and replay recent mesh envelopes the other peer may have missed. Older builds without sync ignore unknown `syncreq` / `syncresp` types.

## Components

| Piece | Role |
|-------|------|
| `MeshMessageStore` | Persists sync-eligible envelopes in `meshhood_store` SharedPreferences (`msg_store`, `msg_seq`) |
| `StateSync` | Builds/parses `syncreq` / `syncresp` JSON |
| `MeshService` | Stores traffic on send/receive, runs handshake on transport connect (background thread) |
| `LanTransport` / `WifiDirectTransport` | `onPeerConnected` when a new TCP session is registered |

## Handshake (JSON, encrypted like all mesh traffic)

**Request** (`type: syncreq`, `ttl: 1`):

```json
{
  "v": 1,
  "type": "syncreq",
  "id": "a1b2c3d4",
  "ttl": 1,
  "ts": 1730000000000,
  "deviceId": "8f3e…",
  "lastSeenSeq": 1730000000000,
  "windowSize": 50
}
```

- **deviceId** — stable per-install id (`deviceid` in prefs), not display name.
- **lastSeenSeq** — v1 **watermark in millis**: max envelope `ts` already held locally. Peers send envelopes with `ts` strictly greater than this value.
- **windowSize** — cap on how many envelopes to return (default 50, max 200).

**Response** (`type: syncresp`):

```json
{
  "v": 1,
  "type": "syncresp",
  "id": "…",
  "ttl": 1,
  "deviceId": "…",
  "messages": ["{…full envelope…}", "…"]
}
```

Catch-up replays each envelope through `handleIncoming` (same path as live mesh). Dedup uses message **id** (`seenIds` + store).

## Stored message types

`broadcast`, `dm`, `groupmsg`, `crew`, `crewjoin`, `agency` — feed-relevant traffic only (not `key`, `profile`, etc.).

## Testing

### Single phone + PC (`mesh_lan.py`)

1. Install consumer build: `install.cmd` (device `R5CT102QT8N` or USB/Wi‑Fi adb).
2. Open MeshHood, complete profile, send a few **Everyone** messages.
3. On PC (same LAN): `pip install zeroconf` if needed.
4. Discover phone: `python tools/mesh_lan.py` or scripts that import `discover_phones` / `send_frame` from `tools/mesh_lan.py`.
5. Connect another MeshHood instance (second phone or emulator) **or** use a PC script that speaks encrypted frames (see `tools/pc_transport_matrix.py`, `tools/mesh_crypto.py`).
6. Watch logcat: `adb logcat -s StateSync LanTransport` — expect `syncreq via lan` and `catch-up applied N message(s)` after link-up.

**Offline catch-up check:** Phone A sends messages while alone → force-stop app → start Phone B (or reconnect A to mesh) → on connect, B should receive recent envelopes via `syncresp`.

### Two Android devices

1. Install on both with `install.cmd`.
2. Same Wi‑Fi: LAN transport should show `N linked`.
3. Send on device 1, then connect device 2; verify feed on 2 after handshake (logcat `StateSync`).

WiFi Direct requires two phones with P2P; LAN is easier on shared router.

## Deferred (v2+)

- Per-peer seq vectors / CRDT-style state
- BLE-triggered sync (v1: LAN + WiFi Direct connect only)
- Cellular uplink sync
- PC `mesh_lan.py` native `syncreq`/`syncresp` helper (optional)

See [PROTOBUF.md](PROTOBUF.md) for the binary wire format (implemented).
