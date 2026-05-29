"""Groups + admin overlay test.

  1. BlockAdmin creates "Oak St Block" (signed groupcreate).
  2. Maria joins the group (signed groupjoin).
  3. Maria broadcasts a profile with RN cert.
  4. BlockAdmin verifies Maria's cert (signed groupverify).
  5. BlockAdmin pins a sandbag pickup announcement (signed grouppin).
  6. A FORGED verify (Mallory credits herself) is sent — should be rejected.

Check the phone feed / My groups menu for Oak St Block.
"""

import asyncio
import base64
import os
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
GROUP_ID = "a1b2c3d4e5f60708"
GROUP_NAME = "Oak St Block"


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

    def _sign(self, payload: str) -> str:
        return _b64(self._ed.sign(payload.encode("utf-8")))

    def profile(self, skills, shares, certs) -> bytes:
        ts = int(time.time() * 1000)
        payload = (f"profile|{self.name}|{ts}|" + ",".join(skills) + "|"
                   + ",".join(shares) + "|" + ",".join(certs))
        sig = self._sign(payload)
        return mesh_crypto.encrypt(
            mesh_proto.build_profile(self.name, ts, skills, shares, certs, sig)
        )

    def group_create(self, gid: str, name: str) -> bytes:
        ts = int(time.time() * 1000)
        payload = mesh_session.group_create_payload(gid, name, self.name, ts)
        sig = self._sign(payload)
        return mesh_crypto.encrypt(
            mesh_proto.build_groupcreate(gid, name, self.name, ts, sig)
        )

    def group_join(self, gid: str) -> bytes:
        ts = int(time.time() * 1000)
        payload = mesh_session.group_join_payload(gid, self.name, ts)
        sig = self._sign(payload)
        return mesh_crypto.encrypt(mesh_proto.build_groupjoin(gid, self.name, ts, sig))

    def group_verify(self, gid: str, subject: str, cert: str) -> bytes:
        ts = int(time.time() * 1000)
        payload = mesh_session.group_verify_payload(gid, self.name, subject, cert, ts)
        sig = self._sign(payload)
        return mesh_crypto.encrypt(
            mesh_proto.build_groupverify(gid, self.name, subject, cert, ts, sig)
        )

    def group_pin(self, gid: str, text: str) -> bytes:
        ts = int(time.time() * 1000)
        payload = mesh_session.group_pin_payload(gid, self.name, text, ts)
        sig = self._sign(payload)
        return mesh_crypto.encrypt(
            mesh_proto.build_grouppin(gid, self.name, text, ts, sig)
        )


def on_notify(_handle, data: bytearray) -> None:
    m = mesh_session.handle(data)
    if not m.get("duplicate"):
        print(f"  << {mesh_session.describe(m)}", flush=True)


async def main() -> None:
    print("Scanning...", flush=True)
    device = await BleakScanner.find_device_by_filter(
        lambda d, ad: SERVICE_UUID.lower() in (ad.service_uuids or [])
    )
    if not device:
        print("MeshHood not found — open the app on your phone.", flush=True)
        return

    admin = Identity("BlockAdmin")
    maria = Identity("Maria")
    mallory = Identity("Mallory")

    async with BleakClient(device) as client:
        await client.start_notify(CHAR_UUID, on_notify)
        print(f"Connected to {device.name}", flush=True)

        print("Handshakes...", flush=True)
        await client.write_gatt_char(CHAR_UUID, admin.key_msg(), response=True)
        await asyncio.sleep(0.5)
        await client.write_gatt_char(CHAR_UUID, maria.key_msg(), response=True)
        await asyncio.sleep(0.5)
        await client.write_gatt_char(CHAR_UUID, mallory.key_msg(), response=True)
        await asyncio.sleep(1)

        print(f'BlockAdmin creates "{GROUP_NAME}"...', flush=True)
        await client.write_gatt_char(CHAR_UUID, admin.group_create(GROUP_ID, GROUP_NAME), response=True)
        await asyncio.sleep(1)

        print("Maria joins the group...", flush=True)
        await client.write_gatt_char(CHAR_UUID, maria.group_join(GROUP_ID), response=True)
        await asyncio.sleep(1)

        print("Maria shares profile (Nurse/EMT, RN license)...", flush=True)
        await client.write_gatt_char(
            CHAR_UUID,
            maria.profile(
                skills=["Nurse / EMT", "Medical / first aid"],
                shares=["first-aid kit"],
                certs=["RN license #44821"],
            ),
            response=True,
        )
        await asyncio.sleep(1)

        print("BlockAdmin verifies Maria's RN license...", flush=True)
        await client.write_gatt_char(
            CHAR_UUID,
            admin.group_verify(GROUP_ID, "Maria", "RN license #44821"),
            response=True,
        )
        await asyncio.sleep(1)

        print("BlockAdmin pins sandbag pickup announcement...", flush=True)
        await client.write_gatt_char(
            CHAR_UUID,
            admin.group_pin(GROUP_ID, "Sandbag pickup Saturday 9am at the clubhouse"),
            response=True,
        )
        await asyncio.sleep(1)

        print("Mallory sends FORGED verify for herself (should be rejected)...", flush=True)
        await client.write_gatt_char(
            CHAR_UUID,
            mallory.group_verify(GROUP_ID, "Mallory", "Fake MD license"),
            response=True,
        )
        await asyncio.sleep(1)

    print("\nDone. On the phone:", flush=True)
    print("  ☰ Menu → 🏘️ My groups → Join \"Oak St Block\"", flush=True)
    print("  👥 Neighbor directory → Maria → cert should show ✓ Oak St Block", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
