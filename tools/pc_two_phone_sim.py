"""Simulate a 2nd (and 3rd) phone from your PC over Bluetooth.

Your real phone = Neighbor A (you).
This script = Neighbor B (Maria) and optionally Neighbor C (Rosa).

Works with ONE phone + ONE PC. Does NOT test WiFi Direct phone-to-phone or
true 3-hop relay (that needs 3 physical devices). It DOES test:

  - Discovery + feed messages from multiple neighbors
  - Signed profiles in the directory
  - Private DMs (phone <-> Maria) when keys are exchanged
  - Emergency + ICE card from Rosa
  - Two-way chat (you type on phone, script replies as Maria)

Usage:
    cd tools
    pip install -r requirements.txt
    python pc_two_phone_sim.py           # guided — follow on-screen steps
    python pc_two_phone_sim.py --auto    # runs without pauses (~45s)
    python pc_two_phone_sim.py --chat    # after setup, stay in Maria chat mode

Keep MeshHood open on the phone (Advertising...).
"""

from __future__ import annotations

import argparse
import asyncio
import base64
import sys
import time

from bleak import BleakClient
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

import mesh_ble
import mesh_crypto
import mesh_keys
import mesh_proto
import mesh_session
import sim_identity

SERVICE_UUID = mesh_ble.SERVICE_UUID
CHAR_UUID = mesh_ble.CHAR_UUID

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass


def _b64(b: bytes) -> str:
    return base64.b64encode(b).decode("ascii")


class Neighbor:
    """One simulated phone: X25519 + Ed25519 identity."""

    def __init__(self, name: str, *, persistent: bool = True) -> None:
        self.name = name
        if persistent:
            self._x, self._ed = sim_identity.load_or_create(name)
        else:
            self._x = X25519PrivateKey.generate()
            self._ed = Ed25519PrivateKey.generate()

    def key_bytes(self) -> bytes:
        xpub = _b64(self._x.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw))
        vpub = _b64(self._ed.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw))
        env = mesh_proto.build_key_announcement(xpub, sender=self.name, spub=vpub)
        return mesh_crypto.encrypt(env)

    def xpub_b64(self) -> str:
        return _b64(self._x.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw))

    def _sign(self, payload: str) -> str:
        return _b64(self._ed.sign(payload.encode("utf-8")))

    def broadcast(self, text: str) -> bytes:
        return mesh_crypto.encrypt(mesh_proto.build_broadcast(text, sender=self.name))

    def profile(
        self,
        skills: list[str],
        shares: list[str],
        certs: list[str],
    ) -> bytes:
        ts = int(time.time() * 1000)
        payload = (
            f"profile|{self.name}|{ts}|"
            + ",".join(skills)
            + "|"
            + ",".join(shares)
            + "|"
            + ",".join(certs)
        )
        sig = self._sign(payload)
        return mesh_crypto.encrypt(
            mesh_proto.build_profile(self.name, ts, skills, shares, certs, sig)
        )

    def dm_to(self, recipient: str, text: str) -> tuple[bytes, bool]:
        pub = mesh_keys.peer_keys.get(recipient)
        if not pub:
            return (
                mesh_crypto.encrypt(mesh_proto.build_plain_dm(text, recipient, self.name)),
                False,
            )
        key = mesh_keys.shared_key(pub)
        inner = __import__("json").dumps(
            {"from": self.name, "to": recipient, "text": text, "ts": int(time.time() * 1000)}
        )
        body = _b64(mesh_crypto.encrypt_with_key(key, inner))
        return mesh_crypto.encrypt(mesh_proto.build_sealed_dm(body)), True

    def emergency(self, text: str, ice: dict | None = None) -> bytes:
        return mesh_crypto.encrypt(mesh_proto.build_emergency(self.name, text, ice))


phone_name: str | None = None
SIM_NAMES = {"Maria", "Rosa"}


def on_phone_message(_handle, data: bytearray) -> None:
    global phone_name
    m = mesh_session.handle(data)
    if m.get("duplicate"):
        return
    if m["type"] == "key" and m.get("from") and m["from"] not in SIM_NAMES:
        if phone_name is None:
            phone_name = m["from"]
            print(f"\n  📱 Phone identity: {phone_name}", flush=True)
    label = mesh_session.describe(m)
    print(f"\n  ← PHONE: {label}", flush=True)
    if not getattr(on_phone_message, "_quiet", False):
        print("  (press Enter on PC to continue)\n", end="", flush=True)


async def wait_enter(prompt: str, auto: bool, delay: float = 3.0) -> None:
    print(f"\n{'=' * 60}")
    print(prompt)
    print("=" * 60)
    if auto:
        await asyncio.sleep(delay)
    else:
        await asyncio.get_event_loop().run_in_executor(None, input, "Press Enter when done… ")


async def find_phone():
    return await mesh_ble.find_phone(25.0)


async def run_sim(auto: bool, chat: bool) -> None:
    device = await find_phone()
    if device is None:
        return

    maria = Neighbor("Maria")
    rosa = Neighbor("Rosa")

    async with BleakClient(device) as client:
        char = mesh_ble.mesh_characteristic(client)
        await client.start_notify(char, on_phone_message)

        # --- Phase 0: introduce simulated neighbors ---
        print("\n▶ Introducing Maria + Rosa on the mesh…", flush=True)
        await client.write_gatt_char(char, maria.key_bytes(), response=True)
        await asyncio.sleep(0.4)
        await client.write_gatt_char(char, rosa.key_bytes(), response=True)
        await asyncio.sleep(0.4)
        await client.write_gatt_char(
            char,
            maria.profile(
                ["Nurse / EMT", "Medical / first aid"],
                ["first-aid kit", "spare oxygen"],
                ["RN license #44821"],
            ),
            response=True,
        )
        await asyncio.sleep(0.5)
        await client.write_gatt_char(
            char,
            maria.broadcast("Hey neighbors — Maria here on Oak St. Power out, I'm checking in."),
            response=True,
        )

        await wait_enter(
            "STEP 1 — On your PHONE:\n"
            "  • Check the feed for Maria's message\n"
            "  • Tap ☰ Menu → 👥 Neighbor directory → Maria (see her profile)\n"
            "  • Switch feed tab to Everyone if needed",
            auto,
        )

        # --- Phase 1: you message back ---
        await wait_enter(
            "STEP 2 — On your PHONE:\n"
            "  • To: Everyone\n"
            "  • Send: \"Hello Maria, glad you're on the mesh\"\n"
            "  • Watch this PC window — you should see your message echoed back",
            auto,
            delay=8.0 if auto else 3.0,
        )

        if auto:
            on_phone_message._quiet = True
            await asyncio.sleep(2.0)
            on_phone_message._quiet = False

        # Maria replies (DM if we learned phone name + key, else broadcast)
        target = phone_name or "Dan Wimberly"
        reply = "Got your message! I have a first-aid kit if anyone needs it."
        wire, sealed = maria.dm_to(target, reply)
        tag = "🔐 private DM" if sealed else "broadcast (no phone key yet)"
        await client.write_gatt_char(char, wire, response=True)
        print(f"\n  → Maria sent {tag} to {target}: {reply}", flush=True)

        await wait_enter(
            "STEP 3 — On your PHONE:\n"
            "  • Check feed for Maria's reply\n"
            "  • If you set up a profile name, try To: Maria → send a direct message\n"
            "  • Optional: ☰ Menu → 🙏 Thank a neighbor → Maria",
            auto,
        )

        # --- Phase 2: Rosa emergency ---
        print("\n▶ Rosa sends an EMERGENCY with ICE card…", flush=True)
        rosa_ice = {
            "blood": "A+",
            "allergies": "penicillin",
            "meds": "insulin",
            "conditions": "Type 1 diabetic",
            "contactName": "Miguel (son)",
            "contactPhone": "555-867-5309",
            "notes": "hard of hearing",
        }
        await client.write_gatt_char(
            char,
            rosa.emergency("🚨 NEED HELP — collapsed at 12 Oak St", rosa_ice),
            response=True,
        )

        await wait_enter(
            "STEP 4 — On your PHONE:\n"
            "  • Feed should show Rosa's EMERGENCY\n"
            "  • Medical line should show blood type, allergies, ICE contact\n"
            "  • Directory → Rosa → see Emergency Card section",
            auto,
        )

        print("\n✅ Simulated 2-phone test complete.")
        print("   Maria + Rosa acted as phones B & C. You were phone A.\n")

        if chat:
            print("CHAT MODE — Maria is listening. Type on phone; replies show here.")
            print("On PC: type a line + Enter to broadcast as Maria. /quit to exit.\n")
            on_phone_message._quiet = False
            while True:
                line = await asyncio.get_event_loop().run_in_executor(None, sys.stdin.readline)
                line = line.strip()
                if line == "/quit":
                    break
                if line:
                    await client.write_gatt_char(char, maria.broadcast(line), response=True)
                    print(f"  → Maria (broadcast): {line}", flush=True)

        await client.stop_notify(char)


def main() -> None:
    parser = argparse.ArgumentParser(description="Simulate 2nd phone over BLE")
    parser.add_argument("--auto", action="store_true", help="Run without pauses")
    parser.add_argument("--chat", action="store_true", help="Stay in Maria chat after sim")
    args = parser.parse_args()
    asyncio.run(run_sim(auto=args.auto, chat=args.chat or (not args.auto)))


if __name__ == "__main__":
    main()
