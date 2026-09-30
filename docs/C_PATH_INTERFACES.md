# C-path contract (Architect) — policy / Realtime / QR / IoT stub

Scope: C only. No Device Owner. D/E/A deferred.

## 1. Institution policy tables

`class_app_policies` (canonical for classroom sessions):
- `id UUID PK`
- `institution_id UUID NOT NULL → institutions`
- `class_id UUID NOT NULL → classes`
- `version TEXT NOT NULL` (monotonic string/int as text)
- `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- UNIQUE `(class_id, version)`

`class_app_policy_packages`:
- `policy_id UUID → class_app_policies ON DELETE CASCADE`
- `package_name TEXT NOT NULL`
- `action TEXT NOT NULL CHECK (action IN ('BLOCK','ALLOW','PROTECTED'))`
- PRIMARY KEY `(policy_id, package_name)`

Optional institution default: `institution_app_policies` + packages (same shape, `class_id` null). **Classroom policy wins** over institution default while session ACTIVE; local overrides lose to both.

## 2. Student fetch / sync

- `GET` latest: PostgREST `class_app_policies?class_id=eq.{id}&order=updated_at.desc&limit=1` + nested packages (or RPC `get_class_policy(p_class_id)` → `{version, packages:[{package,action}]}`).
- Apply before first tap when online; cache last-good snapshot; mid-session offline = fail-closed.
- `tap_focus` already returns `policy_version` — client reconciles if mismatch.

## 3. Realtime channels

| Channel / filter | Payload | Consumers |
|---|---|---|
| `focus_sessions` WHERE `classroom_id=…` | status, acs_health, user_id, updated_at | Teacher roster ACTIVE/NOT_ACTIVE |
| `focus_sessions` WHERE `user_id=auth.uid()` | force-release / remote END | Student reconcile |
| `class_app_policies` WHERE `class_id=…` | version bump | Student re-fetch packages |

ACS: publish `acs_health` on session row; teacher ACTIVE iff `FOCUS_ACTIVE` ∧ `HEALTHY`.

## 4. QR = first-class with NFC (non-NFC phones)

Same registered tag/location identity → **same** `tap_focus` path. Not a stretch; not a parallel session engine.
- Payload: `focustag://tag/{uid}` (or equivalent carrying registered `nfc_tags.uid`)
- Resolve against `nfc_tags` exactly as NFC; call `tap_focus(p_tag_uid=…)` identical to NFC
- Client: camera scan → extract uid → same debounce + server-accept-before-arm as NFC
- Pilot: offer QR entry wherever NFC would start/stop for devices without NFC

## 5. IoT ingest stub (firmware deferred)

`POST /functions/v1/iot_ingest` (or table `iot_events` + Edge Function):
```
{ device_id, tag_uid?, student_hint?, ts, event: "scan"|"heartbeat"|"error", raw? }
```
Server: validate device registry later; for now log + if `tag_uid` present optionally invoke same resolve path (no client arm). Firmware out of scope.

## SQL drafts

- `supabase/migrations/20260930180000_c_path_policies.sql`
- `supabase/migrations/20260930180001_c_path_iot_stub.sql` (optional table only)

## Non-goals

Admin UI (E), teacher live polish (D), door IoT hardware (A), Device Owner. QR is in C (parity with NFC), not deferred.
