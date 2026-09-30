# FocusTag security notes (pilot → production)

## Fixed in this slice

- `allowBackup=false` so session tokens are not pulled via ADB backup / device transfer.
- Cleartext HTTP blocked.
- `FLAG_SECURE` on the classroom activity (no screenshots / recents preview of the session).
- `simulated_tag_01` cannot start a session in release builds.
- Local registry reject before `tap_focus` when the tag list is loaded (cuts RPC spam).
- Launch/NFC tap flood limited to ~1/s.
- Session SharedPreferences no longer logged.
- Broad `TAG_DISCOVERED` filter removed; keep TECH + NDEF + reader mode.

## Still true (OS limits)

- Accessibility and notification access can be turned off by the student unless Device Owner is set.
- Uninstall across reboot is OS-true only with `dpm set-device-owner`.
- Printed QR / `focustag://tag/{uid}` is an intended entry; the server still binds the tap to the signed-in student and enrollment.
- Anon/publishable Supabase key is in the APK by design. RLS must stay on for every table the client reads.

## Operator checklist

1. Enable RLS on `focus_sessions`, `enrollments`, `nfc_tags`, `classes`.
2. Never ship a service-role key in the app.
3. Prefer release builds (`assembleRelease`) on the pilot phone after signing is configured.
