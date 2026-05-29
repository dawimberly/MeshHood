"""Connect as Maria with stable keys, handshake, and reply to the phone."""

from __future__ import annotations

import asyncio
import sys

from bleak import BleakClient

import mesh_ble
import mesh_keys
import mesh_session
from pc_two_phone_sim import Neighbor, find_phone

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

SIM_NAMES = {"Maria", "Rosa"}
phone_name: str | None = None
phone_key_ready = asyncio.Event()


def on_notify(_handle, data: bytearray) -> None:
    global phone_name
    m = mesh_session.handle(data)
    if m.get("duplicate"):
        return
    if m["type"] == "key" and m.get("from") and m["from"] not in SIM_NAMES:
        if phone_name is None:
            phone_name = m["from"]
            print(f"Phone identity: {phone_name}", flush=True)
        if m.get("pub"):
            phone_key_ready.set()
    elif m["type"] == "dm" and m.get("text") and m.get("from") not in SIM_NAMES:
        print(f"Your message: {m['text']}", flush=True)


async def wait_for_phone_key(timeout: float = 12.0) -> bool:
    try:
        await asyncio.wait_for(phone_key_ready.wait(), timeout=timeout)
        return True
    except asyncio.TimeoutError:
        return False


async def main() -> None:
    global phone_name
    mesh_session._seen_ids.clear()
    mesh_keys.peer_keys.clear()

    device = await find_phone()
    if device is None:
        return

    maria = Neighbor("Maria")
    async with BleakClient(device) as client:
        char = mesh_ble.mesh_characteristic(client)
        await client.start_notify(char, on_notify)

        print("Handshaking as Maria (stable identity)…", flush=True)
        await client.write_gatt_char(char, maria.key_bytes(), response=True)
        if not await wait_for_phone_key():
            print("Warning: did not receive phone public key yet — trying anyway", flush=True)
        await asyncio.sleep(1.0)

        target = phone_name or "Dan Wimberly"
        pub = mesh_keys.peer_keys.get(target)
        if not pub:
            print(f"No key for {target} — sending open (plain) DMs", flush=True)

        hello = "Hey Dan! Got your message — Maria here, still on Oak St."
        await client.write_gatt_char(char, maria.broadcast(hello), response=True)
        print(f"Maria → Everyone: {hello}", flush=True)
        await asyncio.sleep(0.8)

        replies = [
            "Power is still out but I have a first-aid kit if you need anything.",
            "Reply here anytime — I am listening on the mesh.",
        ]
        for msg in replies:
            wire, sealed = maria.dm_to(target, msg)
            tag = "private DM" if sealed else "open DM"
            await client.write_gatt_char(char, wire, response=True)
            print(f"Maria → {target} ({tag}): {msg}", flush=True)
            await asyncio.sleep(0.8)

        await asyncio.sleep(2.0)
        print("\nDone — tap the Maria tab on your phone for private chat.", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
