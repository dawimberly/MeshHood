"""Emergency Card (ICE) test — proves a neighbor's vitals ride with their 🚨.

"Rosa" sends an emergency that carries her ICE card (blood type, allergies,
medications, condition, emergency contact). The phone should:
  - store Rosa's ICE card (visible later in her directory entry), and
  - log a medical summary line in the feed.
"""

import asyncio
import sys

from bleak import BleakScanner, BleakClient

import mesh_session

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

SERVICE_UUID = "12345678-1234-1234-1234-123456789abc"
CHAR_UUID = "12345678-1234-1234-1234-123456789abd"


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

    rosa_ice = {
        "blood": "A+",
        "allergies": "penicillin",
        "meds": "insulin",
        "conditions": "Type 1 diabetic",
        "contactName": "Miguel (son)",
        "contactPhone": "555-867-5309",
        "notes": "hard of hearing",
    }

    async with BleakClient(device) as client:
        await client.start_notify(CHAR_UUID, on_notify)
        print("Rosa sends an emergency WITH her ICE card attached...", flush=True)
        await client.write_gatt_char(
            CHAR_UUID,
            mesh_session.build_emergency(
                "🚨 NEED HELP — collapsed at 12 Oak St", ice=rosa_ice, sender="Rosa"
            ),
            response=True,
        )
        await asyncio.sleep(2)
        await client.stop_notify(CHAR_UUID)

    print("\nDone. On the phone: emergency shows, and directory → Rosa shows her", flush=True)
    print("Emergency Card (A+, penicillin allergy, insulin, contact Miguel).", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
