# Cellular uplink (gateway)

Gateway phones with mobile data can bridge the local mesh (LAN / Wi‑Fi Direct / BLE) to remote mesh nodes via a lightweight HTTP relay. Consumer builds never run uplink — only the **gateway** flavor with **Gateway mode** on and a configured relay URL.

## What it does

1. **Detects cellular** — monitors mobile data availability (and metered vs unmetered internally).
2. **Pushes encrypted frames** — every flooded envelope is POSTed to the relay as neighborhood-encrypted wire bytes (same AES-GCM layer as LAN/BLE). The relay cannot read message content.
3. **Polls for remote frames** — every ~45s (when cellular is up), GETs new frames from other gateways and feeds them into the normal decrypt → dedupe → handle → relay pipeline.
4. **Status** — mesh notification and gateway UI show:
   - `Cell: off` — uplink disabled (no relay URL or gateway mode off)
   - `Cell: no data` — no mobile data
   - `Cell: ready` — data available, relay configured
   - `Cell: uplink active` — recent successful push or pull

Headless gateway (`GatewayMode.ensureHeadlessMeshRunning` / boot receiver) uses the same prefs and `MeshService` path — no UI required after reboot.

## Configuration

On the gateway app (**Official alerts** screen):

| Field | Purpose |
|-------|---------|
| **Relay base URL** | e.g. `http://192.168.1.50:8765` or `https://your-relay.example.com` (no trailing slash). Empty = uplink off. |
| **Relay auth token** | Optional. Sent as `Authorization: Bearer <token>` when non-empty. |

Also enable **Gateway mode**.

Prefs (shared store `meshhood_store`):

- `cellular_relay_url`
- `cellular_relay_token`

## Relay HTTP API

| Method | Path | Body / query |
|--------|------|----------------|
| POST | `/v1/push` | JSON `{ "deviceId": "...", "frames": ["base64...", ...] }` |
| GET | `/v1/pull` | `?deviceId=...&since=<ms watermark>&limit=64` → `{ "frames": [{ "payload": "base64...", "ts": 123 }] }` |

Frames are opaque encrypted blobs. Gateways skip frames they already deduped by envelope `id` after decryption.

## Local testing (no public server)

### 1. Start the dev relay on your PC

```bash
python tools/cellular_relay.py --host 0.0.0.0 --port 8765 --token demo
```

### 2. Point the gateway phone at the PC

- Same Wi‑Fi: use the PC’s LAN IP, e.g. `http://192.168.1.50:8765`
- USB debugging: `adb reverse tcp:8765 tcp:8765` then use `http://127.0.0.1:8765` on the phone

Set token `demo` in gateway settings if you used `--token demo`.

### 3. Two-gateway test

1. Gateway A on Wi‑Fi + cellular, relay URL configured, gateway mode on.
2. Gateway B (or consumer on another device) on local mesh only — OR second gateway with same relay URL on cellular.
3. Publish a broadcast or agency alert on A; within one poll interval B should receive it via relay (and vice versa if both uplink).

### 4. Single gateway + PC

Use `tools/pc_relay_test.py` patterns on LAN for local mesh; use cellular uplink to verify push/pull in relay logs when the phone has no LAN path to the PC but can reach the relay over mobile data or Wi‑Fi to the PC.

## Security notes

- Payloads stay **neighborhood-encrypted** on the wire to the relay.
- Optional bearer token limits who can push/pull (v1 — use HTTPS in production).
- DMs remain sealed inside envelopes; relays forward blind.

## Architecture sketch

```
Local peer ──LAN/BLE/WFD──► Gateway phone ──cellular HTTP──► Relay ◄── cellular HTTP ── Remote gateway ──LAN/BLE──► Remote peer
                              MeshService.flood / handleIncoming (shared pipeline)
```

## Deferred (v2)

- WebSocket push instead of poll
- Per-zone or per-device routing on relay
- Automatic relay discovery
- Stricter backoff on metered networks
- Protobuf wire on uplink when all peers support it
