# FocusTag pilot — scope and device proof

Timezone: Asia/Calcutta.

## In scope

- Student phone: NFC **or** QR at the door → same registered `nfc_tags.uid` → server `tap_focus` → Accessibility Service policy.
- QR payload: `focustag://tag/{uid}` (Pilot Room A: `focustag://tag/1D:1D:70:1C:1A:10:80`).
- ACS hard gate. Fail-closed if ACS is not healthy. Offline mid-session stays fail-closed via existing enforcement.
- While FOCUS_ACTIVE, ACS intercepts package-installer and Settings app-info / accessibility screens so the student cannot uninstall FocusTag or turn the service off. OS-true `setUninstallBlocked` only if the device is already Device/Profile Owner (not enrolled in this pilot).
- Thin teacher roster: ACTIVE iff session `FOCUS_ACTIVE` **and** `acs_health=HEALTHY` (5s refresh; not a production dashboard).
- Thin admin: Classes, Enroll, Tags, Policies (version bump). Institution-scoped RLS.

## Parked

- Device Owner / MDM enrollment (uninstall lock is ACS intercept, not MDM)
- Physical IoT firmware / door hardware
- Play listing, OEM production hardening
- Multi-institution console
- Full teacher Realtime polish (roster polls today)

## Nothing Phone 3a live NFC

1. Put `SUPABASE_URL` + `SUPABASE_PUBLISHABLE_KEY` in `local.properties` (see `local.properties.example`).
2. `./gradlew :app:assembleDebug`
3. USB debugging on. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. Settings → Apps → FocusTag → Battery → Unrestricted.
5. Nothing OS: allow Autostart / associated startup for FocusTag.
6. Enable FocusTag Accessibility (sideload: Allow restricted settings first).
7. Sign in with the enrolled student.
8. First tap of tag `1D:1D:70:1C:1A:10:80` (Pilot Room A) → session OPEN / FOCUS_ACTIVE in Supabase `focus_sessions`.
9. With session active: App info / Uninstall should bounce to Home. After release tap, uninstall works again.
10. Second tap → ENDED. Confirm `acs_health` and `install_uuid` on the row.

QR smoke (same UID, no tag required): debug home → **Smoke: Pilot Room A QR UID**, or scan a printed `focustag://tag/1D:1D:70:1C:1A:10:80`.
