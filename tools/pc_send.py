"""MeshHood PC -> phone encrypted BLE sender (broadcast or direct).

Usage:
    python -m pip install bleak cryptography
    python tools\\pc_send.py "your message"                 (broadcast)
    python tools\\pc_send.py --to DeezNutz "private message"  (direct)

Open the MeshHood app on your phone first so it is advertising.
The message is wrapped in an envelope and encrypted (AES-256-GCM).
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


def parse_args(argv: list[str]) -> tuple[str, str]:
    to = "*"
    if len(argv) >= 2 and argv[0] == "--to":
        to = argv[1]
        argv = argv[2:]
    message = " ".join(argv) if argv else "hello from PC"
    return message, to


def on_notify(_handle, data: bytearray) -> None:
    mesh_session.handle(data)  # stores peer keys from handshake


async def main() -> None:
    message, to = parse_args(sys.argv[1:])
    is_direct = to != "*"

    kind = "broadcast" if not is_direct else f"direct to {to}"
    print(f"Scanning for MeshHood phone (up to 20s)... [{kind}]")
    device = await BleakScanner.find_device_by_filter(
        lambda d, ad: SERVICE_UUID.lower()
        in [s.lower() for s in (ad.service_uuids or [])],
        timeout=20.0,
    )
    if device is None:
        print("Phone not found. Is the app open and advertising?")
        return

    print(f"Found {device.name or device.address}. Connecting...")
    async with BleakClient(device) as client:
        if is_direct:
            # Subscribe + handshake so the DM body can be sealed per-recipient.
            await client.start_notify(CHAR_UUID, on_notify)
            await client.write_gatt_char(CHAR_UUID, mesh_session.build_key_msg(), response=True)
            await asyncio.sleep(2.5)
            payload, private = mesh_session.build_dm(message, to=to)
            print("Private DM (per-pair key)." if private else "No key for peer — non-private DM.")
        else:
            payload = mesh_session.build_broadcast(message)

        await client.write_gatt_char(CHAR_UUID, payload, response=True)
        print(f"On the wire (encrypted hex): {payload.hex()[:64]}...")
        print("Sent.")
        if is_direct:
            await asyncio.sleep(0.5)
            await client.stop_notify(CHAR_UUID)


if __name__ == "__main__":
    asyncio.run(main())
