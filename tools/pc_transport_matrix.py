"""Run transport iterations possible with ONE phone + ONE PC.

Tests what MeshHood actually supports today:

  ✅ Bluetooth LE  — PC ↔ phone (Bleak GATT)
  ✅ WiFi LAN      — PC ↔ phone on same Wi‑Fi (mDNS + TCP), if phone shows "WiFi LAN: … linked"
  ⚠️ WiFi Direct   — phone ↔ phone only (needs a 2nd Android device; PC cannot join P2P)
  ❌ Cellular      — not implemented in the app yet (roadmap)

Usage (phone open, MeshHood advertising):
    cd tools
    pip install -r requirements.txt
    python pc_transport_matrix.py
    python pc_transport_matrix.py --auto
"""

from __future__ import annotations

import argparse
import asyncio
import sys
import time

from bleak import BleakClient

import mesh_ble
import mesh_crypto
import mesh_lan
import mesh_session
from pc_two_phone_sim import Neighbor, on_phone_message

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass


def header(title: str) -> None:
    print(f"\n{'=' * 64}\n  {title}\n{'=' * 64}", flush=True)


async def test_bluetooth(auto: bool) -> bool:
    header("1/3  BLUETOOTH (PC → phone via BLE GATT)")
    print("  Phone: keep MeshHood open (status should mention Advertising/Connected).", flush=True)
    if not auto:
        input("  Press Enter to start BLE test… ")

    device = await mesh_ble.find_phone(25.0)
    if device is None:
        print("  ❌ BLE: phone not found", flush=True)
        return False

    maria = Neighbor("Maria")
    ok = False
    async with BleakClient(device) as client:
        char = mesh_ble.mesh_characteristic(client)
        on_phone_message._quiet = True  # type: ignore[attr-defined]
        await client.start_notify(char, on_phone_message)
        await client.write_gatt_char(char, maria.key_bytes(), response=True)
        await asyncio.sleep(0.5)
        await client.write_gatt_char(
            char,
            maria.broadcast("BLE transport test — Maria on Bluetooth"),
            response=True,
        )
        await asyncio.sleep(2.0)
        ok = True
        print("  ✅ BLE: sent key + broadcast as Maria", flush=True)
        print("  👉 On phone: Area feed should show Maria's message.", flush=True)
    return ok


def test_lan(auto: bool) -> bool:
    header("2/3  WiFi LAN (PC → phone on same Wi‑Fi router)")
    print("  Phone + PC must be on the SAME Wi‑Fi (USB does not matter).", flush=True)
    print("  Optional isolation: Airplane mode ON, Wi‑Fi ON, Bluetooth OFF on phone.", flush=True)
    print("  Then only LAN can carry messages (best proof of Wi‑Fi path).", flush=True)
    if not auto:
        input("  Press Enter to scan LAN… ")

    peers = mesh_lan.discover_phones(15.0, retries=2)
    if not peers:
        print("  ❌ LAN: no _meshhood._tcp service found", flush=True)
        print("     Check: phone on Wi‑Fi, MeshHood running, same network as PC.", flush=True)
        return False

    host, port, name = peers[0]
    print(f"  ✓ Found {name} at {host}:{port}", flush=True)

    rosa = Neighbor("Rosa")
    received: list[str] = []

    def on_frame(data: bytes) -> None:
        m = mesh_session.handle(data)
        if not m.get("duplicate"):
            received.append(mesh_session.describe(m))

    try:
        mesh_lan.send_frame(host, port, rosa.key_bytes())
        time.sleep(0.4)
        mesh_lan.send_frame(
            host,
            port,
            rosa.broadcast("WiFi LAN transport test — Rosa on home Wi‑Fi"),
        )
        print("  ✓ Sent key + broadcast over LAN TCP", flush=True)
        print("  Listening 6s for phone replies…", flush=True)
        mesh_lan.listen_frames(host, port, on_frame, duration=6.0)
    except OSError as e:
        print(f"  ❌ LAN connect failed: {e}", flush=True)
        return False

    if received:
        print("  ✅ LAN: received from phone:", flush=True)
        for line in received[:5]:
            print(f"     ← {line}", flush=True)
    else:
        print("  ⚠️ LAN: sent OK (phone may not reply over same socket; check Area feed)", flush=True)
    print("  👉 On phone: look for Rosa's LAN message + status 'WiFi LAN: … linked'", flush=True)
    return True


def report_unsupported() -> None:
    header("3/3  WiFi DIRECT + CELLULAR (not testable with PC alone)")
    print("  WiFi Direct:", flush=True)
    print("    • Android P2P group — phone ↔ phone only", flush=True)
    print("    • Needs a 2nd Android device on MeshHood", flush=True)
    print("    • Status on phone: 'WiFi P2P: …' when linked", flush=True)
    print("", flush=True)
    print("  Cellular:", flush=True)
    print("    • Not built in MeshHood yet (roadmap uplink transport)", flush=True)
    print("    • Today: mobile data does NOT carry mesh packets", flush=True)
    print("    • Disaster story: cell fails → WiFi → BT (implemented on phone)", flush=True)
    print("", flush=True)
    print("  Combined bridge (A=WiFi only, B=BT only, C=cell):", flush=True)
    print("    • Needs multi-radio phones in proximity — test with 2+ Android devices", flush=True)


async def main() -> None:
    ap = argparse.ArgumentParser(description="MeshHood transport matrix (1 phone + 1 PC)")
    ap.add_argument("--auto", action="store_true", help="No Enter prompts")
    ap.add_argument("--ble-only", action="store_true")
    ap.add_argument("--lan-only", action="store_true")
    args = ap.parse_args()

    print("\nMeshHood transport matrix — phone + PC\n", flush=True)
    ble_ok = lan_ok = True
    if not args.lan_only:
        ble_ok = await test_bluetooth(args.auto)
    if not args.ble_only:
        lan_ok = test_lan(args.auto)
    report_unsupported()

    header("SUMMARY")
    if not args.lan_only:
        print(f"  Bluetooth LE : {'✅ pass' if ble_ok else '❌ fail'}", flush=True)
    if not args.ble_only:
        print(f"  WiFi LAN     : {'✅ pass' if lan_ok else '❌ fail / not on same Wi‑Fi'}", flush=True)
    print("  WiFi Direct  : ⏭ skip (needs 2nd phone)", flush=True)
    print("  Cellular     : ⏭ skip (not in app yet)", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
