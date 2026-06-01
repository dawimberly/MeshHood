# Protobuf wire format (Tier 2)

MeshHood can carry mesh envelopes as **protobuf** inside the existing AES-GCM transport layer, while **JSON remains the default** for backward compatibility, BLE, and debug inject tools.

## Schema

File: `app/src/main/proto/meshhood.proto`

```protobuf
message MessageEnvelope {
  int32 v = 1;
  string type = 2;
  string id = 3;
  int32 ttl = 4;
  int64 ts = 5;
  map<string, string> extra = 6;  // all type-specific fields
}
```

Scalars (`from`, `text`, `deviceId`, …) are plain strings. Arrays and nested objects (`origin`, `geo`, `messages`, …) are JSON-encoded strings in `extra`.

Supported message types match JSON: `broadcast`, `dm`, `groupmsg`, `crew`, `crewjoin`, `agency`, `syncreq`, `syncresp`, plus other mesh types when relayed.

## Detection strategy

After AES-GCM decryption, plaintext is either:

| Form | Detection |
|------|-----------|
| **Protobuf** | Starts with magic bytes `0x4D 0x48 0x01` (`"MH\x01"`) |
| **JSON** | UTF-8 text starting with `{` (legacy, debug inject, BLE default) |

`MeshSerializer.decode(wireBytes)` returns canonical JSON for `handleIncoming`.

## Capability negotiation

Protobuf is **not** sent blindly to unknown peers:

1. New builds stamp `"wireFmt":"pb"` on `syncreq` / `syncresp` (JSON — old peers ignore unknown fields).
2. When a transport receives protobuf frames **or** `wireFmt=pb`, it marks that transport (LAN or WiFi Direct) as protobuf-capable.
3. Subsequent sends on that transport use protobuf; BLE always stays JSON.

First sync handshake is JSON on both sides; protobuf kicks in after capability is observed.

## Components

| Piece | Role |
|-------|------|
| `meshhood.proto` | Wire schema (protobuf Java Lite generated) |
| `MeshSerializer` | `encodeProto` / `encodeJson` / `decode` / `isProtobuf` |
| `Crypto.encryptBytes` / `decryptBytes` | Byte-oriented encrypt path for non-UTF8 protobuf |
| `MeshService.notifySubscribers` | Picks JSON vs protobuf per transport capability |
| `MeshService.onTransportBytes` | Decrypt → decode → `handleIncoming` |

## Debug inject unchanged

`tools/inject_agency.cmd`, `tools/mesh_lan.py`, and PC scripts send **JSON inside encryption** — no magic prefix. New builds decode and handle them normally.

## Testing

### Unit tests

```bat
cd C:\Users\Owner\AndroidStudioProjects\MeshHood
gradlew.bat :app:testConsumerDebugUnitTest --tests com.meshhood.MeshSerializerTest
```

Covers JSON/proto round-trip, nested fields, auto-detect, and encrypt/decrypt over protobuf wire.

### Two new-build phones (LAN)

1. `install.cmd` on both devices (same Wi‑Fi).
2. Open MeshHood on both; confirm LAN shows `N linked`.
3. Send a broadcast on device A; verify it appears on B.
4. Logcat: `adb logcat -s MeshSerializer StateSync LanTransport`
   - First link: `syncreq via lan` (JSON)
   - After capability: traffic uses protobuf (no parse errors)

### JSON peer / debug inject

1. `python tools/mesh_lan.py` or `tools/inject_agency.cmd` — messages still delivered.
2. Phone stays on JSON for that transport until it sees `wireFmt=pb` or a protobuf frame.

### Mixed old + new (simulated)

An old JSON-only build continues to work: it never advertises `wireFmt=pb` and never sends magic bytes, so a new peer keeps sending JSON on that link.

## Deferred

- Dedicated protobuf `SyncRequest` / `SyncResponse` messages (v1 uses `MessageEnvelope` + JSON `messages` array in `extra`)
- Per-link capability in transport layer (v1 tracks per transport type)
- Protobuf on BLE
- Removing JSON entirely
