# Senior Dev — B-path hardening draft (local branch `senior/b-path-hardening-draft`)

Status: **draft only — not pushed**. Waiting on Lead seed-complete + clear.

## Landed locally
1. `supabase/migrations/20260930140000_tap_focus_rpc.sql` — `tap_focus` RPC, idempotency, student_installs, unique open session per (student, classroom), teacher force-release.
2. `NfcController` — HID 350ms dedupe; **4s** start debounce (3–5s window); **500ms** release when FOCUS_ACTIVE.
3. `TapFocusRepository` + models — client calls RPC; arms/releases only after `accepted`.
4. `FocusViewModel` — server-authoritative tap path; ACS-not-enabled sets `acsBlocked` and refuses taps.
5. `AcsRequiredGate` + HomeScreen — full-screen CTA (not a toast).
6. `InstallIdStore` — stable install UUID bound with auth user.

## Notes for Architect / Debugger
- Existing `focus_sessions` DTO uses `status`, `start_at`, `end_at` — migration is additive; new writes use `FOCUS_ACTIVE`/`ENDED`.
- Simulated tag `simulated_tag_01` stays local-only for DEBUG.
- Unit tests for NFC-05/06/07 not yet extended — hand to Debugger after seed-complete PR opens.

## First PR scope (when cleared)
B-path only: files above + any Architect `docs/B_PATH_INTERFACES.md`.
