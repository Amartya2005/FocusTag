# E — Minimal admin MVP (Architect)

Goal: thin control plane for pilot. Compose or simple web. No full IAM/SIS.

## Roles
- `admin` / institution staff (profiles.role). RLS: institution-scoped writes.

## Screens (4)
1. **Classes** — create/list (`classes`: institution_id, location_id, name). One class per location.
2. **Enroll** — add/remove student ↔ class (`enrollments`). Student = existing profile UUID/email lookup.
3. **Tags** — register `nfc_tags.uid` → `location_id`, `is_active`. Same UID used by NFC + QR.
4. **Policies** — edit latest `class_app_policies` + packages (`package` → BLOCK|ALLOW|PROTECTED); bump `version` on save.

## Data (existing + C)
- Use: `institutions`, `locations`, `classes`, `enrollments`, `nfc_tags`, `profiles`
- C adds: `class_app_policies`, `class_app_policy_packages` (+ optional institution defaults)
- **A (this slice):** `iot_devices` registry only (`device_id`, `location_id`, `is_active`) — list/create/deactivate. Firmware out.

## APIs
- PostgREST CRUD under RLS, or thin RPCs: `admin_create_class`, `admin_enroll`, `admin_register_tag`, `admin_upsert_class_policy`
- Policy save: insert new `version` row + packages (immutable history); clients read latest via `get_class_policy`

## Non-goals
Teacher live polish (D), Device Owner, firmware, billing, multi-tenant console chrome beyond institution filter.

## Implement order
Tags + Classes → Enroll → Policies → iot_devices registry.
