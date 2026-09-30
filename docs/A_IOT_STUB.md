# A — IoT stub (registry and event logging only)

This slice deliberately has no firmware, device-owner work, or hardware control. The C migration creates `iot_devices` and `iot_events`; the registry is an admin-controlled record and the event table is an append-only ingest target.

## Register a device

Use the authenticated admin Supabase client (RLS must be enabled) or the SQL editor with an appropriately scoped role:

```sql
insert into public.iot_devices (device_id, location_id)
values ('reader-lab-01', '<location uuid>');
```

Deactivate without deleting history:

```sql
update public.iot_devices
set is_active = false
where device_id = 'reader-lab-01';
```

## Log an event

A future Edge Function should authenticate the device, validate `device_id`, then insert:

```sql
insert into public.iot_events
    (device_id, tag_uid, student_hint, event, raw, ts)
values
    ('reader-lab-01', '1D:FF:7C:1C:1A:10:80', null, 'scan',
     '{"source":"reader"}'::jsonb, now());
```

`event` is one of `scan`, `heartbeat`, or `error`. Keep `raw` for diagnostics. Until device authentication exists, do not expose anonymous insert access; use a service-role Edge Function or a trusted server. Firmware and automatic focus-session changes are out of scope.
