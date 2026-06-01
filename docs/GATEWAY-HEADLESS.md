# Gateway headless mode

The **gateway** product flavor (`com.meshhood.gateway`) can run as a fixed mesh hub with no UI after reboot: foreground `MeshService`, gateway mode on, BLE advertising.

Headless does **not** lock you out of the app. You can always reopen **Official alerts** from the launcher, the mesh notification, or ADB.

## Enable headless (first time)

1. Install the gateway APK and open **Official alerts** (launcher).
2. Grant Bluetooth and location permissions when prompted.
3. Turn on **Run headless on boot** and confirm the dialog.
4. Reboot (or use HEADLESS_ON below). The mesh hub starts in the background; the launcher icon still opens Official alerts anytime.

Alternatively, with permissions already granted:

```bash
adb shell am broadcast -a com.meshhood.gateway.HEADLESS_ON -n com.meshhood.gateway/.gateway.GatewayUiReceiver
```

## Open Official alerts while headless

| Method | Action |
|--------|--------|
| **App icon** | Tap **MeshHood Gateway** / **Official alerts** — opens settings normally |
| **Notification** | Tap **MeshHood active** or the **Official alerts** action button |
| **Long-press icon** | Shortcut **Official alerts** (Android 7.1+) |
| **ADB** | `adb shell am broadcast -a com.meshhood.gateway.SHOW_UI -n com.meshhood.gateway/.gateway.GatewayUiReceiver` |

SHOW_UI opens the gateway console **without** disabling headless. Turn off **Run headless on boot** in settings if you want the UI on every cold start after reboot.

Direct activity start also works:

```bash
adb shell am start -a com.meshhood.gateway.SHOW_UI -n com.meshhood.gateway/com.meshhood.gateway.AgencyGatewayActivity
```

## After reboot

`GatewayBootReceiver` starts `MeshService` when headless is enabled. No activity is shown. Use any method above to open Official alerts.

## Open full neighbor app while headless

From Official alerts, tap **Open full app**, or:

```bash
adb shell am start -n com.meshhood.gateway/com.meshhood.MainActivity
```

MainActivity opens normally even when headless is enabled.

## Implementation notes

- Preference: `gateway_headless` in `meshhood_store` (`GatewayHeadlessKeys`).
- Gateway mode pref `gatewaymode` is set true when headless activates.
- `GatewayMode.ensureHeadlessMeshRunning()` starts the mesh without finishing activities.
- `GatewayMode.enterHeadlessIfNeeded()` only auto-finishes when launched with `EXTRA_AUTO_HEADLESS_START` (automatic boot path only).
- Consumer flavor (`com.meshhood`) is unchanged.
