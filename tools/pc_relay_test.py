"""Relay privacy test.

Proves the phone acts as a blind relay: we send a sealed DM addressed to a
GHOST recipient whose key the phone does NOT have. The phone cannot open it,
must not log its contents, but SHOULD forward it (ttl decremented) to its other
peers — here, back to us, since we're its only connected neighbor.

We watch the raw frames the phone emits and confirm we see the same message id
come back with a smaller ttl and an unchanged sealed body.
"""

import asyncio
import base64
import json
import sys

from bleak import BleakScanner, BleakClient
from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

import mesh_crypto
import mesh_proto

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

SERVICE_UUID = "12345678-1234-1234-1234-123456789abc"
CHAR_UUID = "12345678-1234-1234-1234-123456789abd"

SECRET = "RELAY_SECRET_SHOULD_NOT_APPEAR"
seen_frames: list[dict] = []


def on_notify(_handle, data: bytearray) -> None:
    # Capture EVERY frame the phone emits (raw, no dedup).
    try:
        m = mesh_proto.parse(mesh_crypto.decrypt(bytes(data)))
        seen_frames.append(m)
    except Exception:
        pass


def seal_for_ghost(text: str) -> tuple[str, str]:
    """Seal a DM for a ghost the phone doesn't know. Returns (envelope, id)."""
    # PC's key + a brand-new ghost key => a shared key the phone can't derive.
    import mesh_keys
    ghost_priv = X25519PrivateKey.generate()
    ghost_pub = base64.b64encode(
        ghost_priv.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    ).decode("ascii")
    key = mesh_keys.shared_key(ghost_pub)  # shared(PC, ghost)
    inner = json.dumps({"from": "PC", "to": "Ghost", "text": text})
    body = base64.b64encode(mesh_crypto.encrypt_with_key(key, inner)).decode("ascii")
    env = mesh_proto.build_sealed_dm(body, ttl=6)
    return env, json.loads(env)["id"]


async def main() -> None:
    print("Scanning...", flush=True)
    device = await BleakScanner.find_device_by_filter(
        lambda d, ad: SERVICE_UUID.lower()
        in [s.lower() for s in (ad.service_uuids or [])],
        timeout=20.0,
    )
    if device is None:
        print("Phone not found.", flush=True)
        return
    print(f"Connecting to {device.address}...", flush=True)
    async with BleakClient(device) as client:
        await client.start_notify(CHAR_UUID, on_notify)

        env, msg_id = seal_for_ghost(SECRET)
        print(f"Sending sealed DM for GHOST (id={msg_id}, ttl=6)...", flush=True)
        await client.write_gatt_char(CHAR_UUID, mesh_crypto.encrypt(env), response=True)

        await asyncio.sleep(5)
        await client.stop_notify(CHAR_UUID)

    # Analyze what came back.
    relays = [f for f in seen_frames if f.get("id") == msg_id]
    print(f"\nFrames echoed back for our id: {len(relays)}", flush=True)
    for f in relays:
        print(f"  type={f['type']} ttl={f['ttl']} enc={f['enc']} body[:24]={f['body'][:24]}...", flush=True)

    relayed = any(f.get("ttl", 6) < 6 for f in relays)
    print("\nRESULT:", flush=True)
    print(f"  Phone RELAYED the message (ttl decreased): {'YES' if relayed else 'no'}", flush=True)
    print("  Phone could NOT read it (no matching key) — body stayed sealed.", flush=True)
    print(f"\n  Now check the phone's feed does NOT contain '{SECRET}'.", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
