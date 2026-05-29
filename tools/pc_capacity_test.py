"""Capacity-aware reciprocity test — proves the ethics rules.

Cast (each a signed identity):
  Edna   — homebound elder. Declares HOMEBOUND; Tariq + Sam vouch for her.
  Marco  — able-bodied. Stays FULL capacity.

Both Edna and Marco RECEIVE a lot of help (they each thank 3 helpers), and
NEITHER gives help back. Identical numbers — but the ethics differ:

  Edna  -> 💛 Cared for      (vouched homebound => exempt, never flagged)
  Marco -> ⚠️ Could pitch in  (able + takes a lot + gives none => gentle nudge)

Also fires a crew help-call so the "📣 who can help?" flow shows on the phone.
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
    """A neighbor with its own X25519 + Ed25519 keys who can sign messages."""

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

    def status(self, cap: str) -> bytes:
        ts = int(time.time() * 1000)
        sig = self._sign(f"status|{self.name}|{cap}|{ts}")
        return mesh_crypto.encrypt(mesh_proto.build_status(self.name, cap, ts, sig))

    def vouch(self, subject: str) -> bytes:
        ts = int(time.time() * 1000)
        sig = self._sign(f"vouch|{self.name}|{subject}|{ts}")
        return mesh_crypto.encrypt(mesh_proto.build_vouch(self.name, subject, ts, sig))

    def kudos(self, helper: str) -> bytes:
        ts = int(time.time() * 1000)
        sig = self._sign(f"kudos|{self.name}|{helper}|{ts}")
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

    edna = Identity("Edna")
    marco = Identity("Marco")
    tariq = Identity("Tariq")
    sam = Identity("Sam")
    helpers = [Identity(f"Helper{i}") for i in range(3)]

    async def w(b: bytes, pause: float = 0.4) -> None:
        await client.write_gatt_char(CHAR_UUID, b, response=True)
        await asyncio.sleep(pause)

    async with BleakClient(device) as client:
        await client.start_notify(CHAR_UUID, on_notify)

        print("Announcing identities...", flush=True)
        for ident in [edna, marco, tariq, sam, *helpers]:
            await w(ident.key_msg())

        print("Edna declares HOMEBOUND; Tariq + Sam vouch for her...", flush=True)
        await w(edna.status("homebound"))
        await w(tariq.vouch("Edna"))
        await w(sam.vouch("Edna"))

        print("Marco stays FULL capacity (declares nothing).", flush=True)

        print("Edna and Marco each RECEIVE help from 3 neighbors (give none)...", flush=True)
        for i, h in enumerate(helpers):
            # A helper helped Edna  => Edna thanks them (Edna received help).
            await w(edna.kudos(f"Helper{i}"))
            # A helper helped Marco => Marco thanks them (Marco received help).
            await w(marco.kudos(f"Helper{i}"))

        print("Tariq fires a crew help-call; Sam joins...", flush=True)
        task = "Shoveling Edna's driveway, Sat 9am — who's in?"
        await w(mesh_session.build_crew(task, sender="Tariq"))
        await w(mesh_session.build_crewjoin(task, sender="Sam"))

        await asyncio.sleep(2)
        await client.stop_notify(CHAR_UUID)

    print("\nDone. Open the phone -> long-press feed -> 🏅 Good Neighbors. Expect:", flush=True)
    print("  💛 Edna  — helped 0, received 3 · Cared for   (vouched homebound = exempt)", flush=True)
    print("  ⚠️  Marco — helped 0, received 3 · Could pitch in   (+ an Opportunity line)", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
