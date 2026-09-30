# FocusTag pilot — scope and device proof

Timezone: Asia/Calcutta.

## In scope

- Student phone: NFC **or** QR at the door → same registered `nfc_tags.uid` → server `tap_focus` → Accessibility Service policy.
- QR payload: `focustag://tag/{uid}` (Pilot Room A: `focustag://tag/1D:1D:70:1C:1A:10:80`).
- ACS hard gate. Fail-closed if ACS is not healthy. Offline mid-session stays fail-closed via existing enforcement.
- While FOCUS_ACTIVE, ACS intercepts package-installer, Files, and Settings app-info so the student cannot uninstall FocusTag or turn the service off.
- Reboot: session lock is stored in device-protected prefs and reapplied on `LOCKED_BOOT_COMPLETED`. OS-true uninstall block across the boot gap requires Device Owner (one ADB command below).
- Thin teacher roster: ACTIVE iff session `FOCUS_ACTIVE` **and** `acs_health=HEALTHY`.
- Thin admin: Classes, Enroll, Tags, Policies (version bump). Institution-scoped RLS.

## Parked

- Full MDM / multi-device provisioning
- Physical IoT firmware / door hardware
- Play listing, OEM production hardening
- Multi-institution console

## Nothing Phone 3a live NFC

1. Put `SUPABASE_URL` + `SUPABASE_PUBLISHABLE_KEY` in `local.properties` (see `local.properties.example`).
2. `./gradlew :app:assembleDebug`
3. USB debugging on. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. Settings → Apps → FocusTag → Battery → Unrestricted.
5. Nothing OS: allow Autostart / associated startup for FocusTag.
6. Enable FocusTag Accessibility (sideload: Allow restricted settings first).
7. Sign in with the enrolled student.
8. First tap of tag `1D:1D:70:1C:1A:10:80` (Pilot Room A) → session OPEN / FOCUS_ACTIVE in Supabase `focus_sessions`.
9. With session active: App info / Files / Uninstall should bounce to Home. After release tap, uninstall works again.
10. Second tap → ENDED. Confirm `acs_health` and `install_uuid` on the row.

## Reboot-proof uninstall (pilot 3a only)

Android will not let a normal app block uninstall until Accessibility comes back. To close that gap on the test phone:

```
adb shell dpm set-device-owner com.focustag.app/.data.receiver.FocusDeviceAdminReceiver
```

Requires no accounts on the device (or remove them first). After this, `setUninstallBlocked` is applied on session start and again at locked-boot, before the student can open Files.

Remove later with:

```
adb shell dpm remove-active-admin com.focustag.app/.data.receiver.FocusDeviceAdminReceiver
```

QR smoke: debug home → **Debug: Pilot Room A UID**, or scan `focustag://tag/1D:1D:70:1C:1A:10:80`.
