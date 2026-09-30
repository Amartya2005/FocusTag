# Real diffs vs `0xyusufz/FocusTag`

This fork is not a silent re-seed. Main started from the upstream student core (`FocusStateEngine`, `PolicyEngine`, `EnforcementCoordinator`, Accessibility) and then landed pilot work on `Amartya2005/FocusTag`.

## Already on main before this slice

- Server-owned `tap_focus` RPC + `TapFocusRepository`
- 4s start / 500ms release debounce after server accept
- `InstallIdStore`
- ACS hard gate (`AcsRequiredGate`)
- NFC HID 350ms dedupe in `NfcController`
- `get_class_policy` + class policy / IoT stub SQL
- Thin `AdminScreen` / `AdminRepository`
- Pilot docs (`B_PATH`, `C_PATH`, `E_ADMIN`, `A_IOT_STUB`)

## This slice (`feat/qr-ui-pilot`)

| Area | Upstream | Here |
|---|---|---|
| QR | Not first-class on main | Camera QR + `focustag://tag/{uid}` + paste/smoke → **same** `onTagEvent` / `tap_focus` |
| Student UI | Default Material purple / “FT” logo / “NFC Attendance coming soon” | Teal/sand classroom UI, door copy, QR primary CTA, ACS/auth restyle |
| Theme | Dynamic Material + stock purple | Fixed classroom palette (`dynamicColor = false`) |
| Teacher ACTIVE | `status=IN_PROGRESS` | `FOCUS_ACTIVE`/`active` **and** `acs_health=HEALTHY`, 5s refresh |
| Deep links | `focustag://auth` only | Also `focustag://tag/{uid}` |

## Not rewritten

Domain engines, accessibility service, NFC reader, tap_focus SQL, and admin CRUD were left in place. No token-burning copy of upstream trees.
