"""MeshHood simulated-neighborhood demo.

Plays a scripted, realistic disaster-night conversation from SEVERAL named
neighbors over the single BLE link to your phone. Each "neighbor" gets its own
X25519 identity, announces its key (so it shows up as a contact you can DM),
and then sends timed broadcast messages. Includes an EMERGENCY so you can show
the alert + notification on camera.

Great for a pitch video. Run with the MeshHood app open on the phone.

    python -m pip install bleak cryptography
    python tools\\pc_demo.py
"""

import asyncio
import base64
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


def new_identity() -> str:
    """A fresh X25519 public key (base64) for a simulated neighbor."""
    priv = X25519PrivateKey.generate()
    raw = priv.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    return base64.b64encode(raw).decode("ascii")


# (delay_seconds_before, neighbor, message)  -- "!" prefix marks an emergency.
SCRIPT = [
    (0.0, "Maria",   "Power's out on our whole block. Everyone okay?"),
    (2.5, "Dev",     "Yep, we're fine at 212 Oak. Have a propane stove if anyone needs hot water."),
    (2.5, "Sue",     "No cell service here at all. Glad this thing works without it."),
    (3.0, "Tariq",   "I've got a generator + gas. Can charge phones at 208 Oak, knock anytime."),
    (3.0, "Maria",   "Bless you Tariq. My phone's at 12%."),
    (3.5, "GrandpaJoe", "My insulin needs refrigeration. Fridge is warming up — not urgent yet but flagging it."),
    (3.0, "Dev",     "Joe I have a cooler + ice packs, bringing them over now."),
    (3.5, "Sue",     "!NEED HELP — tree fell on the Parkers' car at 220 Oak, someone may be inside!"),
    (3.0, "Tariq",   "On my way with a crowbar."),
    (2.5, "Maria",   "Calling it in if anyone gets a signal. Stay safe everyone."),
    (3.0, "Dev",     "Block meetup at the corner in 10 min to count heads."),
]


async def main() -> None:
    print("Scanning for MeshHood phone (up to 20s)...")
    device = await BleakScanner.find_device_by_filter(
        lambda d, ad: SERVICE_UUID.lower()
        in [s.lower() for s in (ad.service_uuids or [])],
        timeout=20.0,
    )
    if device is None:
        print("Phone not found. Open the MeshHood app first.")
        return

    print(f"Connected to {device.address}. Starting demo...\n")
    async with BleakClient(device) as client:
        await client.start_notify(CHAR_UUID, lambda h, d: None)

        # Introduce each neighbor by announcing their public key.
        names = sorted({n for _, n, _ in SCRIPT})
        for name in names:
            env = mesh_proto.build_key_announcement(new_identity(), sender=name)
            await client.write_gatt_char(CHAR_UUID, mesh_crypto.encrypt(env), response=True)
            await asyncio.sleep(0.2)
        print(f"Introduced {len(names)} neighbors: {', '.join(names)}\n")
        await asyncio.sleep(1.0)

        for delay, neighbor, message in SCRIPT:
            await asyncio.sleep(delay)
            emergency = message.startswith("!")
            text = message[1:] if emergency else message
            env = mesh_proto.build(text, to="*", sender=neighbor)
            await client.write_gatt_char(CHAR_UUID, mesh_crypto.encrypt(env), response=True)
            tag = "  *** EMERGENCY ***" if emergency else ""
            print(f"{neighbor}: {text}{tag}")

        print("\nDemo complete. The conversation is saved on the phone's feed.")
        await asyncio.sleep(1.0)
        await client.stop_notify(CHAR_UUID)


if __name__ == "__main__":
    asyncio.run(main())
