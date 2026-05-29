"""Reputation (Good Neighbor) test — proves kudos can't be forged.

  1. Handshake (exchange encryption + verify keys).
  2. Send a VALID kudos crediting "Tariq", signed by PC. Phone has PC's verify
     key, so it should accept it -> Tariq reputation +1.
  3. Send a FORGED kudos crediting "Mallory" that claims to be from PC but is
     signed with a DIFFERENT key. Phone checks against PC's real key -> reject.

Afterward, check the phone's stored reputation: Tariq should be 1, Mallory absent.
"""

import asyncio
import base64
import sys
import time

from bleak import BleakScanner, BleakClient
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

import mesh_crypto
import mesh_proto
import mesh_session
import mesh_sign

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

SERVICE_UUID = "12345678-1234-1234-1234-123456789abc"
CHAR_UUID = "12345678-1234-1234-1234-123456789abd"


def on_notify(_handle, data: bytearray) -> None:
    mesh_session.handle(data)  # store peer keys; ignore the rest


def forged_kudos(helper: str) -> bytes:
    """A kudos claiming from='PC' but signed with an attacker key."""
    ghost = Ed25519PrivateKey.generate()
    ts = int(time.time() * 1000)
    payload = mesh_sign.kudos_payload("PC", helper, ts)
    bad_sig = base64.b64encode(ghost.sign(payload.encode("utf-8"))).decode("ascii")
    return mesh_crypto.encrypt(mesh_proto.build_kudos("PC", helper, ts, bad_sig))


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
        await client.write_gatt_char(CHAR_UUID, mesh_session.build_key_msg(), response=True)
        await asyncio.sleep(2)

        print("Sending VALID kudos: PC credits Tariq (properly signed)...", flush=True)
        await client.write_gatt_char(CHAR_UUID, mesh_session.build_kudos("Tariq"), response=True)
        await asyncio.sleep(2)

        print("Sending FORGED kudos: 'PC' credits Mallory (bad signature)...", flush=True)
        await client.write_gatt_char(CHAR_UUID, forged_kudos("Mallory"), response=True)
        await asyncio.sleep(2)

        await client.stop_notify(CHAR_UUID)
    print("\nDone. Now check the phone's reputation store:", flush=True)
    print("  expect Tariq=1 (accepted), Mallory absent (forgery rejected).", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
