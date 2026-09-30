# FocusTag

FocusTag is a Kotlin/Jetpack Compose Android **student pilot app**. A passive NFC tag and the phone's NFC reader start or end a server-authoritative focus session; while active, the selected distracting apps are redirected home by Android AccessibilityService.

## Pilot path

- Hardware: passive NFC tag + phone NFC reader. No IoT door controller, Device Owner, QR, or admin UI is required.
- Tap flow: authenticate as an enrolled student, tap the registered tag, then let `tap_focus` accept the transition before enforcement changes locally. A second accepted tap ends the session.
- Server states: `FOCUS_ACTIVE` and `ENDED`. ACS health is reported as `HEALTHY` or `DEGRADED`/`FAILED`; the student path is blocked until Accessibility is enabled.
- Debug-only simulated tag: `simulated_tag_01` (not a substitute for the physical pilot path).

## Student app setup (Nothing Phone (3a))

1. Install the debug APK from the pilot lead, or use Android Studio with this repo.
2. Copy `local.properties.example` to `local.properties` and set the Supabase URL and publishable key. Never commit `local.properties`.
3. On the Nothing 3a, enable NFC (Settings → Connected devices → Connection preferences → NFC; wording may vary by Nothing OS version).
4. Enable FocusTag under Settings → Accessibility → Downloaded apps/Installed services. For a sideloaded Android 13+ build, also allow restricted settings from the app's App info menu if Android requires it.
5. Open FocusTag, sign in with the enrolled student account, select the pilot apps, and confirm the ACS gate is clear. The full-screen gate blocks NFC taps until ACS is ready.
6. Tap the enrolled passive tag with the phone. The first accepted tap starts `FOCUS_ACTIVE`; tap again to end it. If ACS is degraded/failed, stop and restore Accessibility before treating the session as active.

## Accessibility disclosure

FocusTag uses Android AccessibilityService only during a focus session to observe foreground-window changes and redirect selected blocked apps to Home. It does not retrieve window content (`canRetrieveWindowContent=false`); package identifiers for blocked-app interception history may be synced to Supabase. Disable the service to stop enforcement; the app will block the tap path until it is enabled again.

## Database migrations (Lead applies; no applied-state claim is stored here)

Apply the files in timestamp order, then verify the target project before pilot use:

1. `supabase/migrations/20260904000000_phase9_schema.sql`
2. `supabase/migrations/20260904000001_phase9_rls.sql`
3. `supabase/migrations/20260904000002_phase9_rls_remediation.sql`
4. `supabase/migrations/20260930140000_tap_focus_rpc.sql` — `tap_focus`, install binding, idempotency, and open-session uniqueness.

## Layout

- `app/` — Android student app
- `supabase/migrations/` — schema, RLS, remediation, and B-path RPC migrations
- `docs/B_PATH_INTERFACES.md` — current B-path contract
- `gradle/` — wrapper and version catalog

Build with `./gradlew assembleDebug` after local configuration. Do not put credentials in source control.
