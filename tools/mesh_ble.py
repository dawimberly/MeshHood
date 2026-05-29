"""BLE helpers shared by PC mesh tools."""

from __future__ import annotations

from bleak import BleakClient, BleakScanner

SERVICE_UUID = "12345678-1234-1234-1234-123456789abc"
CHAR_UUID = "12345678-1234-1234-1234-123456789abd"


def _norm_uuid(value: str) -> str:
    return value.lower().replace("-", "")


def mesh_characteristic(client: BleakClient):
    """Resolve the mesh GATT characteristic (handles duplicate UUID on device)."""
    matches = []
    for service in client.services:
        for char in service.characteristics:
            if _norm_uuid(str(char.uuid)) == _norm_uuid(CHAR_UUID):
                matches.append(char)
    if not matches:
        raise RuntimeError("MeshHood message characteristic not found on phone")
    # Prefer the last match when two MeshHood installs advertise the same UUID.
    return matches[-1]


async def find_phone(timeout: float = 25.0):
    print(f"Scanning for MeshHood (up to {int(timeout)}s)…", flush=True)
    device = await BleakScanner.find_device_by_filter(
        lambda d, ad: SERVICE_UUID.lower() in [s.lower() for s in (ad.service_uuids or [])],
        timeout=timeout,
    )
    if device is None:
        print("\n❌ Phone not found.")
        print("   Open MeshHood on your phone and wait for 'Advertising…' in the header.")
        return None
    print(f"✓ Found {device.address}", flush=True)
    return device
