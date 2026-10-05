# FocusTag

Classroom focus for school-owned and pilot Android phones.

A student presents **NFC** or **QR** at the door. Both carry the same registered `nfc_tags.uid`, so NFC and QR resolve to the same server-side focus transition. The server `tap_focus` RPC owns OPEN / CLOSED. The Accessibility Service then enforces the class app policy until the same tag is presented again.

Pilot fork of [0xyusufz/FocusTag](https://github.com/0xyusufz/FocusTag). Real deltas: [docs/DIFFS_VS_UPSTREAM.md](docs/DIFFS_VS_UPSTREAM.md) — this is not a silent clone.

## Focus state flow

`NFC/QR → tap_focus RPC → FOCUS_ACTIVE → app policy enforcement → same tag/QR → ENDED`. The client treats the server response as authoritative for session state.

## How class starts

1. Student is signed in and Accessibility is on (fail-closed otherwise).
2. Hold the tag **or** scan `focustag://tag/{uid}` (printed QR or in-app camera).
3. NFC also launches the app from the home screen (phone unlocked) via TECH / NDEF filters.
4. Server accepts → session `FOCUS_ACTIVE` → policy lock.
5. Same tag / QR again → `ENDED`.

There is no student on / off switch. Apps list is read-only. Session start is tag or QR only.

## Pilot identity

| | |
| --- | --- |
| Repo | `Amartya2005/FocusTag` |
| Backend | Existing Supabase project (`SUPABASE_URL` + publishable key in `local.properties`) |
| First pilot handset | Nothing Phone 3a (NFC). Same build runs on other Android 8+ devices. |
| Registered tag | `1D:1D:70:1C:1A:10:80` (Pilot Room A) |
| QR payload | `focustag://tag/1D:1D:70:1C:1A:10:80` |
| Timezone | Asia/Calcutta |

## In scope (on main)

- QR first-class with NFC (same `onTagEvent` → `tap_focus`, debounce, ACS gate)
- Redesigned student Compose UI (full-height door, Today, ACS / notification gates, QR, sign-in)
- Live NFC / QR against Supabase
- Notification reply guard while a session is active
- Thin teacher roster (`ACTIVE` = `FOCUS_ACTIVE` ∧ ACS `HEALTHY`)
- Thin institution-scoped admin (classes, enroll, tags, policies)
- School-owned Device Owner policy on **any** compatible Android (not 3a-only)
- Client hardening: no backup of session prefs, no cleartext, `FLAG_SECURE`, simulated tag blocked in release

## Parked

- Full MDM / Android Enterprise console and zero-touch at district scale
- Physical IoT door firmware
- Play Store listing and OEM production signing
- Multi-institution admin console
- Greying out the system Accessibility toggle (Google does not expose that API)

## Device ownership boundary

Personal devices cannot provide the same enforcement guarantees as school-owned Device Owner devices. FocusTag therefore fail-closes when required accessibility or notification capabilities are unavailable, while stronger OS controls remain limited to managed devices.

## Devices

| Phone | What the OS allows |
| --- | --- |
| Personal / BYOD | Student can disable Accessibility. FocusTag fail-closes. Settings intercept only while class is live. |
| School-owned Device Owner | Uninstall, app-control, safe boot blocked. Only FocusTag is a permitted third-party Accessibility service. Same `dpm` command on Pixel, Samsung, Motorola, Nothing, … |

Enroll a school phone (no accounts on the device):

```bash
adb shell dpm set-device-owner com.focustag.app/.data.receiver.FocusDeviceAdminReceiver
```

Details: [docs/SCHOOL_OWNED.md](docs/SCHOOL_OWNED.md).

## Stack

Kotlin, Jetpack Compose, Material 3, NFC reader mode + system NFC launch, CameraX + ML Kit QR, AccessibilityService, NotificationListenerService, DevicePolicyManager (when owner), Supabase Auth + PostgREST.

## Build

```bash
cp local.properties.example local.properties
# set sdk.dir, SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY
./gradlew :app:assembleDebug
```

Install and door proof: [docs/PILOT.md](docs/PILOT.md).  
Security notes: [docs/SECURITY.md](docs/SECURITY.md).
