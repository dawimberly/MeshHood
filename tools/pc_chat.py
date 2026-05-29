"""MeshHood two-way PC <-> phone BLE chat.

Connects to the phone, listens for messages it sends (including the
EMERGENCY button), and lets you type messages back to the phone.

Usage:
    python tools\\pc_chat.py

Open the MeshHood app on the phone first (it must show "Advertising...").
Type a message and press Enter to send. Type /quit to exit.
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
    m = mesh_session.handle(data)
    if m["type"] == "key":
        # Quietly store the key; just nudge the prompt.
        print(f"\n[PHONE] {mesh_session.describe(m)}\n> ", end="", flush=True)
        return
    print(f"\n[PHONE] {mesh_session.describe(m)}\n> ", end="", flush=True)


async def read_line() -> str:
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, sys.stdin.readline)


async def main() -> None:
    print("Scanning for MeshHood phone (up to 20s)...")
    device = await BleakScanner.find_device_by_filter(
        lambda d, ad: SERVICE_UUID.lower()
        in [s.lower() for s in (ad.service_uuids or [])],
        timeout=20.0,
    )
    if device is None:
        print("Phone not found. Make sure the app is open and advertising.")
        return

    print(f"Found {device.address}. Connecting...")
    async with BleakClient(device) as client:
        await client.start_notify(CHAR_UUID, on_notify)
        # Handshake: share our public key so DMs to us can be private.
        await client.write_gatt_char(CHAR_UUID, mesh_session.build_key_msg(), response=True)
        await asyncio.sleep(1.5)
        print("Connected. Commands:")
        print("  <text>            broadcast to everyone")
        print("  @Name <text>      direct message to Name (e.g. @DeezNutz hi)")
        print("  /quit             exit")
        print("> ", end="", flush=True)
        while True:
            line = (await read_line()).strip()
            if line == "/quit":
                break
            if line:
                if line.startswith("@") and " " in line:
                    name, text = line[1:].split(" ", 1)
                    wire, private = mesh_session.build_dm(text, to=name)
                    if not private:
                        print(f"(no key for {name} yet — sending non-private)", flush=True)
                    await client.write_gatt_char(CHAR_UUID, wire, response=True)
                else:
                    await client.write_gatt_char(
                        CHAR_UUID, mesh_session.build_broadcast(line), response=True
                    )
            print("> ", end="", flush=True)
        await client.stop_notify(CHAR_UUID)


if __name__ == "__main__":
    asyncio.run(main())
