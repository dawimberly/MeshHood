# Android emulator on Windows

Run MeshHood consumer (and optionally gateway) on an **Android Virtual Device (AVD)** when a physical phone is unavailable. Good for UI, agency alert verification, and LAN tests to your PC. **Not** a stand-in for BLE or WiFi Direct mesh between real handsets.

**Project minSdk:** 26 (Android 8.0) — create AVDs with **API 26 or higher**.

---

## 1. Create an AVD (Android Studio)

1. Open **Android Studio** → **Device Manager** (phone icon in toolbar, or *Tools → Device Manager*).
2. **Create Device** → pick a phone profile (e.g. Pixel 6).
3. Select a system image **API 26+** (download if needed). Recommended: recent API 34/35 image with Google Play if you need Play services for Maps.
4. Finish wizard → **▶ Run** the AVD (cold boot once; leave it running).

Confirm adb sees it:

```cmd
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" devices
```

You should see a line like `emulator-5554   device`.

**Target only the emulator** when a USB phone is also plugged in:

```cmd
adb -e devices
adb -e shell getprop ro.product.model
```

`-e` = first emulator in the list. For a specific serial: `adb -s emulator-5554 ...`.

---

## 2. Install consumer (debug)

From the **MeshHood** repo root:

### Option A — helper script (recommended)

With the emulator running:

```cmd
tools\emulator_install.cmd
```

Builds `assembleConsumerDebug`, installs `installConsumerDebug`, launches `com.meshhood/.MainActivity`.

### Option B — Gradle directly

```cmd
set ANDROID_SERIAL=emulator-5554
gradlew.bat assembleConsumerDebug installConsumerDebug
adb -e shell am start -n com.meshhood/.MainActivity
```

Replace `emulator-5554` with your serial from `adb devices`.  
Or use `.\install.ps1` / `install.cmd` if **only** the emulator is connected (no USB phone).

### Option C — install.ps1

Same as phone install when emulator is the sole adb device:

```cmd
install.cmd
```

---

## 3. Install gateway (optional)

Gateway signing key must be in `local.properties` first:

```cmd
tools\setup_agency_gateway.cmd
install_gateway.cmd
```

If both phone and emulator are connected, set `ANDROID_SERIAL` to the emulator before running, or disconnect the phone.

Launch gateway UI manually:

```cmd
adb -e shell am start -n com.meshhood.gateway/com.meshhood.gateway.AgencyGatewayActivity
```

---

## 4. Test agency alerts without mesh

Debug consumer on emulator:

```cmd
tools\inject_agency.cmd "Shelter open at City Hall until 8pm"
```

If multiple adb devices exist, prefix inject’s adb calls by setting `ANDROID_SERIAL=emulator-5554` in that shell, or unplug the phone.

See [AGENCY-GATEWAY.md](AGENCY-GATEWAY.md) for why gateway publish does not appear in consumer on the same device without relay.

---

## 5. LAN mesh: emulator ↔ PC

BLE and WiFi Direct are **weak or absent** on emulators. LAN to the **host PC** works when the AVD shares the host network (default on recent images).

```cmd
cd tools
pip install -r requirements.txt
python pc_transport_matrix.py --lan-only
```

Or use `mesh_lan.py` from other `pc_*.py` tools. Phone/emulator and PC must be on the same Wi‑Fi (emulator uses host routing).

---

## 6. Limitations

| Capability | Emulator |
|------------|----------|
| Consumer / gateway UI | ✅ |
| Debug inject (`inject_agency.cmd`) | ✅ |
| LAN → PC (`mesh_lan.py`, transport matrix) | ✅ (typical) |
| BLE GATT mesh | ❌ unreliable |
| WiFi Direct P2P | ❌ not meaningful |
| Google Maps | ⚠ needs `MAPS_API_KEY` in `local.properties`; may need Play image |
| Location / ZIP | ⚠ send mock location via Android Studio *Extended controls* |
| Notifications | ✅ with permission granted in app |

---

## 7. Two emulators (phone-to-phone simulation)

You can run **two AVDs** for a crude two-node test (heavy on RAM/CPU):

1. Device Manager → start AVD #1 and AVD #2 (different serials).
2. Install consumer on both: `adb -s emulator-5554 install ...` then `adb -s emulator-5556 install ...`.
3. Prefer **LAN** between them on the same host; do not expect BLE/P2P between AVDs.

Most developers use **one emulator + one physical phone** or **one emulator + PC LAN** instead.

---

## Quick reference

```cmd
REM Start AVD in Android Studio, then:
tools\emulator_install.cmd

REM Agency alert on consumer feed (debug):
tools\inject_agency.cmd "Test alert"

REM Emulator-only adb:
adb -e logcat -s MeshHood

REM Gateway (after setup_agency_gateway.cmd):
set ANDROID_SERIAL=emulator-5554
install_gateway.cmd
```

**Do you need a phone?** See [AGENCY-GATEWAY.md](AGENCY-GATEWAY.md#phone-required-vs-emulator-ok) — emulator is fine for UI/dev; real phones are required for production-style radio mesh and the most honest disaster drills.
