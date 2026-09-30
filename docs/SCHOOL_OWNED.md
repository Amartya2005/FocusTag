# School-owned phones (any Android)

Nothing Phone 3a was only the first pilot handset. The same policy runs on Pixel, Samsung, Motorola, and other Android 8+ devices.

## Two modes

| Phone | What Android allows |
| --- | --- |
| Personal / BYOD | Student can turn Accessibility off. FocusTag fail-closes (no class) and intercepts Settings *during* a live session. |
| School-owned Device Owner | OS blocks uninstall, app-control, safe boot. Only FocusTag is a permitted third-party Accessibility service. |

There is still no public API that greys out the FocusTag Accessibility switch itself. Device Owner is the strongest path Google gives a classroom app.

## Enroll any device

1. Factory reset (or a phone with **no** accounts).
2. Install FocusTag.
3. Do **not** add a Google account yet.
4. USB debugging, then:

```bash
adb shell dpm set-device-owner com.focustag.app/.data.receiver.FocusDeviceAdminReceiver
```

Works on any OEM that implements AOSP Device Policy. Some skins (certain Xiaomi / Oppo / Vivo builds) reject the command until an account-less setup; if it fails, use Android Enterprise QR provisioning instead of ADB.

Production scale: Android Enterprise fully-managed QR / zero-touch, same `DeviceAdminReceiver`.

Remove:

```bash
adb shell dpm remove-active-admin com.focustag.app/.data.receiver.FocusDeviceAdminReceiver
```

## What gets applied when owner

- `setUninstallBlocked`
- `DISALLOW_UNINSTALL_APPS`
- `DISALLOW_APPS_CONTROL`
- `DISALLOW_SAFE_BOOT`
- `DISALLOW_DEBUGGING_FEATURES`
- `setPermittedAccessibilityServices(FocusTag)` so students cannot enable a random third-party ACS instead
