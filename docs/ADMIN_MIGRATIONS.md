# Admin phase9 migrations (000003–000016)

Imported from [0xyusufz/Focus-Tag-Admin-Dashboard](https://github.com/0xyusufz/Focus-Tag-Admin-Dashboard) into this repo so Android + admin share one migration history.

## Apply order

Apply **after** existing `phase9_schema` / `phase9_rls` / `phase9_rls_remediation` (already on live):

1. `20260904000003_phase9_admin_rpcs`
2. `20260904000004_phase9_rls_recursion_fix`
3. `20260904000005_phase9_search_unassigned_rpc`
4. `20260904000006_phase9_search_unassigned_rpc_fix`
5. `20260904000007_phase9_locations`
6. `20260904000008_phase9_location_reactivate`
7. `20260904000009_phase9_nfc_tags_rpcs`
8. `20260904000010_phase9_nfc_tag_reassign`
9. `20260904000011_phase9_session_rls_fix` — **watch carefully**
10. `20260904000012_phase9_classes_rpcs`
11. `20260904000013_phase9_classes_grants`
12. `20260904000014_phase9_audit_fixes`
13. `20260904000015_phase9_qr_credentials`
14. `20260904000016_phase9_demote_teacher`

Do **not** drop or reorder Amartya deltas that sort later:

- `20260930140000_tap_focus_rpc`
- `20260930180000_c_path_policies`
- `20260930180001_c_path_iot_stub`
- `20261001150000_tap_focus_entry_source`

## 000011 vs `tap_focus`

`000011` replaces session `SELECT` RLS policies with `is_teacher_of_session` (SECURITY DEFINER).  
`tap_focus` is also SECURITY DEFINER, so it should keep working after 000011. Before applying 000011, confirm:

```sql
SELECT proname FROM pg_proc WHERE proname = 'tap_focus';
```

After 000011, re-check that `tap_focus` still exists and a smoke tap still succeeds. If SELECT policies break student/teacher reads, stop and fix before continuing to 000012+.

## QR protocol

Student scanner expects `focustag://tag/{uid}`. Admin printed QR should use that payload as primary (opaque `qr_credentials` remain for optional audit / regenerate flows).
