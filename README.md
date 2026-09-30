# FocusTag

Classroom focus pilot: a student taps **NFC** or scans a **QR** at the door. Both carry the same registered `nfc_tags.uid`. The server `tap_focus` RPC owns OPEN/CLOSED. The phone Accessibility Service then enforces the class app policy until the same tag is presented again.

Pilot fork of [0xyusufz/FocusTag](https://github.com/0xyusufz/FocusTag). See [docs/DIFFS_VS_UPSTREAM.md](docs/DIFFS_VS_UPSTREAM.md) for real deltas — this is not a silent clone.

## Pilot identity

- Repo: `Amartya2005/FocusTag`
- Backend: existing Supabase project (wire URL + anon/publishable key in `local.properties`)
- Pilot phone: Nothing Phone 3a (NFC)
- Registered tag: `1D:1D:70:1C:1A:10:80` (Pilot Room A)
- Timezone: Asia/Calcutta
- QR contract: `focustag://tag/{uid}`

## In scope

- QR parity with NFC (camera + deep link + debug smoke)
- Redesigned student Compose UI (home, focus-active, ACS gate, QR, sign-in)
- Live NFC proof on the 3a against Supabase
- Thin teacher roster (ACTIVE = FOCUS_ACTIVE ∧ ACS HEALTHY)
- Thin institution-scoped admin (classes, enroll, tags, policies)

## Parked

- Device Owner / MDM
- Physical IoT firmware
- Play listing and OEM production hardening
- Multi-institution admin console

## Stack

Kotlin, Jetpack Compose, Material 3, NFC reader mode, CameraX + ML Kit QR, AccessibilityService, Supabase Auth + PostgREST.

## Build

```
cp local.properties.example local.properties
# set sdk.dir, SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY
./gradlew :app:assembleDebug
```

Device steps: [docs/PILOT.md](docs/PILOT.md).
