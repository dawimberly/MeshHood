"""Private DM test with per-device X25519 keys.

Flow:
  1. Connect + subscribe; exchange public keys with the phone (handshake).
  2. Send a broadcast (everyone can read).
  3. Send a PRIVATE direct message to the phone.
  4. Prove privacy: show what a snooping neighbor (who has the neighborhood key
     but NOT the per-pair key) would see -> just base64 ciphertext.
  5. Listen 15s for replies.
"""

import asyncio
import sys

from bleak import BleakScanner, BleakClient

import mesh_crypto
import mesh_proto
import mesh_session

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

SERVICE_UUID = "12345678-1234-1234-1234-123456789abc"
CHAR_UUID = "12345678-1234-1234-1234-123456789abd"
PHONE_NAME = "DeezNutz"


def on_notify(_handle, data: bytearray) -> None:
    m = mesh_session.handle(data)
    print(f"[FROM PHONE] {mesh_session.describe(m)}", flush=True)


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

        # 1. Handshake: send our key, give the phone a moment to send its key.
        await client.write_gatt_char(CHAR_UUID, mesh_session.build_key_msg(), response=True)
        print("[handshake] sent our public key, waiting for phone's key...", flush=True)
        await asyncio.sleep(3)
        if mesh_session.have_key_for(PHONE_NAME):
            print(f"[handshake] got {PHONE_NAME}'s key — DMs will be PRIVATE", flush=True)
        else:
            print("[handshake] no phone key yet — DM will not be private", flush=True)

        # 2. Broadcast.
        await client.write_gatt_char(CHAR_UUID, mesh_session.build_broadcast("Hi everyone on the block"), response=True)
        print("[TO PHONE] broadcast: Hi everyone on the block", flush=True)
        await asyncio.sleep(2)

        # 3. Private DM.
        secret = "psst - this is just for you"
        wire, private = mesh_session.build_dm(secret, to=PHONE_NAME)
        await client.write_gatt_char(CHAR_UUID, wire, response=True)
        tag = "PRIVATE" if private else "not private"
        print(f"[TO PHONE] direct ({tag}) to {PHONE_NAME}: {secret}", flush=True)

        # 4. What a relay/snooping neighbor sees: neighborhood key decrypts the
        #    envelope, but there is NO from/to and the body stays sealed.
        snoop = mesh_proto.parse(mesh_crypto.decrypt(wire))
        print("\n--- what a relay neighbor (neighborhood key only) sees ---", flush=True)
        print(f"    type={snoop['type']} id={snoop['id']} ttl={snoop['ttl']} enc={snoop['enc']}", flush=True)
        print(f"    from={snoop['from']!r} to={snoop['to']!r}  (no sender/recipient!)", flush=True)
        print(f"    body (sealed): {snoop['body'][:48]}...", flush=True)
        print("    -> a middle phone learns nothing but 'relay this blob'.\n", flush=True)

        print("Listening 15s for replies...", flush=True)
        await asyncio.sleep(15)
        await client.stop_notify(CHAR_UUID)
    print("Done.", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
