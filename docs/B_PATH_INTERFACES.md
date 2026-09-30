# Focus Tag — B-path interface notes (pilot contract)

**Owner:** Software Architect  
**Status:** Canonical for Dec 30 Sep 2026 B-path hardening (Packs 2–4)  
**Repo target:** https://github.com/Amartya2005/FocusTag (land with first B-path PR after Lead seed-complete)  
**Local tree:** `/workspace/FocusTag`  
**Aligned draft:** `docs/SENIOR_B_PATH_DRAFT.md` + `supabase/migrations/20260930140000_tap_focus_rpc.sql`

Hardware for this pilot: **passive NFC tag + phone NFC reader**. No IoT door controller, Admin UI, or Device Owner on the critical path.

---

## 1. `tap_focus` RPC (server-authoritative)

**Function:** `public.tap_focus(p_tag_uid, p_install_uuid, p_idempotency_key, p_acs_health?, p_force?, p_target_student_id?) → jsonb`

| Field | Rule |
|---|---|
| Auth | `auth.uid()` required. Bind **auth user + install UUID**, not raw tag UID alone. Upsert `student_installs`. |
| Idempotency | `p_idempotency_key` UUID per tap. Replay returns cached response; **same-state retry is a no-op**. |
| Tag resolve | Active `nfc_tags.uid` → `locations` → `classes` (one class per location). Unknown/inactive → `accepted: false`, `error: unknown_or_inactive_tag`. |
| Enrollment | Student must be in `enrollments` for that class; else `not_enrolled`. |
| Session uniqueness | At most **one open** session per `(student_user_id, classroom_id)` (`FOCUS_ACTIVE` / legacy `active`). |
| Toggle | First accepted tap when CLOSED → **OPEN** (`FOCUS_ACTIVE`). Second when OPEN → **CLOSED** (`ENDED`). |
| Force release | `p_force=true` ends open session(s); teacher may target another student only if `teacher_class_access` covers that classroom. |
| Response | `{ accepted, session_id, state, class_id, policy_version, acs_hint, error?, force? }` |

**Client contract:** When online, **server accept wins before the phone arms or releases**. Do not flip local Focus/enforcement on tap alone. Simulated tag `simulated_tag_01` remains DEBUG/local-only.

Canonical state names for new writes: `FOCUS_ACTIVE` | `ENDED` (map Pack 4 OPEN/CLOSED onto these).

---

## 2. Client NFC timing

| Stage | Value | Notes |
|---|---|---|
| HID dedupe | 250–500ms (draft: **350ms**) | Drop hardware multi-fires |
| Start / multi-scan debounce | **3–5s per-UID** (draft: **4s**) | While not `FOCUS_ACTIVE` |
| Release window | **500ms** once `FOCUS_ACTIVE` | Intentional quick stop |

Flow: HID dedupe → debounce window → call `tap_focus` → on `accepted` only → `FocusStateEngine` / `EnforcementCoordinator`.

---

## 3. Policy sync

Shape: `institution_id / class_id / package → BLOCK | ALLOW | PROTECTED` + **`version`**.

- Classroom/institution policy **wins over local overrides** while session is `FOCUS_ACTIVE`.
- Prefer sync before first tap when online.
- Offline mid-session: last good snapshot; **fail-closed** (keep enforcement).
- `tap_focus` returns `policy_version` for client reconcile.

If `class_app_policies` is not yet migrated, RPC may return `policy_version: null`; client keeps last snapshot / local policy until the table exists.

---

## 4. ACS health + demo room-fail

| ACS state | Student UI | Teacher roster |
|---|---|---|
| `HEALTHY` | Normal | ACTIVE only if session `FOCUS_ACTIVE` **and** ACS HEALTHY |
| `DEGRADED` / `FAILED` | Loud, non-toast | `NOT_ACTIVE` |
| **Not enabled / restricted settings** | **Full-screen CTA; block tap path** | N/A — this is the **single unmissable demo room-fail** |

Success metric for pilot: **% of `FOCUS_ACTIVE` sessions with ACS HEALTHY mid-class** — not “unbypassable.”

Publish `acs_health` with the session (`HEALTHY` | `DEGRADED` | `FAILED` | `UNKNOWN`).

OEM demo checklist (Pixel/Samsung first; high-friction OEMs documented separately) lives with Debugger’s runbook / PaperWork.

---

## 5. B-path component map

| Layer | Owns |
|---|---|
| Student app | Auth, install bind, NFC/tap path, ACS gate + enable CTA, PolicyEngine apply, Accessibility HOME intercept, EnforcementCoordinator, history sync |
| Supabase | Auth, tag registry, enrollments, RLS, `tap_focus`, unique open session, idempotency, ledger fields (`acs_health`, `policy_version`) |
| Teacher (stretch) | Roster ACTIVE/NOT_ACTIVE from session + ACS; force-release via `tap_focus(..., force=true)` |

**Parked:** Admin control plane, IoT door device, Device Owner / MDM, QR (stretch), UsageStats/overlay (spare-time).

---

## 6. First-PR file order (Senior)

1. `supabase/migrations/20260930140000_tap_focus_rpc.sql`
2. `util/NfcController.kt` (debounce)
3. TapFocus repository + models
4. `FocusViewModel` (server-accept-before-arm/release)
5. ACS gate UI (full-screen)
6. `InstallIdStore`
7. This doc: `docs/B_PATH_INTERFACES.md`

Debugger: NFC-05/06/07, A11Y-01/02, BE-03/04 against the landed PR.

---

## 7. Ratification

Ratified by Development Lead against Research packs 1–4. Pack 4 numbers win over any earlier ~2s client-only debounce.
