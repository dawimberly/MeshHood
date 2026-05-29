import asyncio
from bleak import BleakScanner

async def main():
    print('Scanning 12s for all BLE devices...')
    devices = await BleakScanner.discover(timeout=12.0, return_adv=True)
    for addr, (d, adv) in devices.items():
        print(f'{addr} | name={d.name!r} | rssi={adv.rssi} | uuids={adv.service_uuids}')

asyncio.run(main())
