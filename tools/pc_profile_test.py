"""Profile sync test — proves signed profiles are stored, and tampered ones rejected.

  1. "Maria" announces her keys, then broadcasts a signed profile
     (skills: Nurse/EMT; shares: first-aid kit; cert: RN license).
     -> Phone should store Maria's profile (verifiable in the directory).

  2. A relay-tampered profile for "Maria" (skills swapped to 'Electrician' but
     the original signature kept) is sent.
     -> Phone should REJECT it (the field-binding signature no longer matches).
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

    def tampered_profile(self, real_skills, fake_skills, shares, certs) -> bytes:
        # Sign the REAL skills, but ship FAKE skills with that signature.
        ts = int(time.time() * 1000)
        real_payload = (f"profile|{self.name}|{ts}|" + ",".join(real_skills) + "|"
                        + ",".join(shares) + "|" + ",".join(certs))
        sig = self._sign(real_payload)
        return mesh_crypto.encrypt(
            mesh_proto.build_profile(self.name, ts, fake_skills, shares, certs, sig)
        )


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

    maria = Identity("Maria")

    async with BleakClient(device) as client:
        await client.start_notify(CHAR_UUID, on_notify)

        await client.write_gatt_char(CHAR_UUID, maria.key_msg(), response=True)
        await asyncio.sleep(1.5)

        print("Maria broadcasts a signed profile (Nurse/EMT)...", flush=True)
        await client.write_gatt_char(CHAR_UUID, maria.profile(
            skills=["Nurse / EMT", "Medical / first aid"],
            shares=["first-aid kit", "spare oxygen"],
            certs=["RN license #44821"],
        ), response=True)
        await asyncio.sleep(1.5)

        print("Sending a TAMPERED profile (skills swapped to Electrician)...", flush=True)
        await client.write_gatt_char(CHAR_UUID, maria.tampered_profile(
            real_skills=["Nurse / EMT", "Medical / first aid"],
            fake_skills=["Electrician"],
            shares=["first-aid kit", "spare oxygen"],
            certs=["RN license #44821"],
        ), response=True)
        await asyncio.sleep(2)

        await client.stop_notify(CHAR_UUID)

    print("\nDone. Expected on phone:", flush=True)
    print("  Directory shows Maria with Nurse/EMT skills + RN cert (the signed profile).", flush=True)
    print("  The tampered 'Electrician' profile is rejected (signature no longer binds).", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
