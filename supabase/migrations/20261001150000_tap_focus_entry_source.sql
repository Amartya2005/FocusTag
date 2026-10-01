-- NFC and QR are separate doors. A session ends only on the door that started it.
ALTER TABLE public.focus_sessions ADD COLUMN IF NOT EXISTS entry_source TEXT;
-- Function body applied to the pilot project as tap_focus_entry_source.
