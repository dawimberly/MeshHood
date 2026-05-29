"""Reciprocity test — proves the give/receive loop is tracked correctly.

Simulates three neighbors and a chain of verified, signed kudos:

  PC  --thanks-->  Tariq      (Tariq HELPED PC)
  Tariq --thanks-> Sam        (Sam HELPED Tariq  => Tariq received help)

Expected reciprocity afterward:
  Tariq : helped 1, received 1  -> 🔄 Pays it forward (closed the loop)
  Sam   : helped 1, received 0  -> 🌟 Generous
  PC    : helped 0, received 1  -> 🤝 Receiving help

Each kudos is Ed25519-signed by its giver, and each giver announces its verify
key first, so the phone can verify every link in the chain.
"""

import asyncio
import base64
import sys
import time

from bleak import BleakScanner, BleakClient
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

import mesh_crypto
import mesh_proto
import mesh_session

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

SERVICE_UUID = "12345678-1234-1234-1234-123456789abc"
CHAR_UUID = "12345678-1234-1234-1234-123456789abd"


def _b64(b: bytes) -> str:
    return base64.b64encode(b).decode("ascii")


class Identity:
    def __init__(self, name: str) -> None:
        self.name = name
        self._x = X25519PrivateKey.generate()
        self._ed = Ed25519PrivateKey.generate()

    def key_msg(self) -> bytes:
        xpub = _b64(self._x.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw))
        vpub = _b64(self._ed.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw))
        env = mesh_proto.build_key_announcement(xpub, sender=self.name, spub=vpub)
        return mesh_crypto.encrypt(env)

    def kudos(self, helper: str) -> bytes:
        ts = int(time.time() * 1000)
        sig = _b64(self._ed.sign(f"kudos|{self.name}|{helper}|{ts}".encode("utf-8")))
        return mesh_crypto.encrypt(mesh_proto.build_kudos(self.name, helper, ts, sig))


def on_notify(_handle, data: bytearray) -> None:
    mesh_session.handle(data)


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

    tariq = Identity("Tariq")
    sam = Identity("Sam")

    async with BleakClient(device) as client:
        await client.start_notify(CHAR_UUID, on_notify)

        print("Announcing identities (PC, Tariq, Sam)...", flush=True)
        await client.write_gatt_char(CHAR_UUID, mesh_session.build_key_msg(), response=True)
        await client.write_gatt_char(CHAR_UUID, tariq.key_msg(), response=True)
        await client.write_gatt_char(CHAR_UUID, sam.key_msg(), response=True)
        await asyncio.sleep(2)

        print("PC thanks Tariq (Tariq helped PC)...", flush=True)
        await client.write_gatt_char(CHAR_UUID, mesh_session.build_kudos("Tariq"), response=True)
        await asyncio.sleep(1.5)

        print("Tariq thanks Sam (Sam helped Tariq => Tariq pays it forward)...", flush=True)
        await client.write_gatt_char(CHAR_UUID, tariq.kudos("Sam"), response=True)
        await asyncio.sleep(2)

        await client.stop_notify(CHAR_UUID)

    print("\nDone. Expected on phone:", flush=True)
    print("  Tariq: helped 1, received 1  -> 🔄 Pays it forward", flush=True)
    print("  Sam:   helped 1, received 0  -> 🌟 Generous", flush=True)
    print("  PC:    helped 0, received 1  -> 🤝 Receiving help", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
