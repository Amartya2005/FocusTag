-- supabase/migrations/20260904000010_phase9_nfc_tag_reassign.sql
-- Phase 2B-2 Hardening: Secure NFC Tag Location Reassignment
--
-- CONCURRENCY DESIGN (addresses TOCTOU race from security review):
--
--   The previous implementation used SELECT FOR UPDATE on the nfc_tags row.
--   This only locks the tag row itself. It does NOT prevent a concurrent
--   focus_sessions INSERT for the same UID from slipping in after the
--   zero-session check but before the location_id UPDATE commits.
--
--   Fix: LOCK TABLE public.focus_sessions IN SHARE MODE
--
--   A SHARE lock on focus_sessions conflicts with the ROW EXCLUSIVE lock
--   required by INSERT (and UPDATE/DELETE). While this transaction holds
--   the SHARE lock:
--     - No concurrent session INSERT for this UID can proceed.
--     - The zero-session count read is therefore a stable snapshot until we
--       commit or roll back.
--   After we commit (having verified 0 sessions and updated location_id),
--   any blocked session inserts are released — but the location is now
--   changed, and the RPC will reject future reassigns once sessions exist.
--
--   We still also keep FOR UPDATE on the nfc_tags row to prevent two
--   concurrent reassign calls on the same tag from racing each other.
--
-- UI SESSION DETECTION (addresses RLS gap from security review):
--
--   Admins do not have a SELECT grant on focus_sessions via the existing RLS.
--   The page was querying focus_sessions directly, which would silently
--   return zero rows for admins → every tag would show "Change Location"
--   even for tags with sessions.
--
--   Fix: get_tag_session_status(p_uids TEXT[]) — a narrowly scoped
--   SECURITY DEFINER function that returns only {uid TEXT, has_sessions BOOLEAN}
--   for UIDs belonging to the caller's institution. No session content is
--   ever exposed.

-- ============================================================
-- FUNCTION 1: get_tag_session_status
-- Returns has_sessions boolean per UID — admin-only, institution-scoped.
-- Exposes no session content; only confirms whether sessions exist.
-- ============================================================
CREATE OR REPLACE FUNCTION public.get_tag_session_status(p_uids TEXT[])
RETURNS TABLE (uid TEXT, has_sessions BOOLEAN)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_role TEXT;
  v_caller_inst UUID;
BEGIN
  -- Authorize caller
  SELECT profiles.role, profiles.institution_id
  INTO   v_caller_role, v_caller_inst
  FROM   public.profiles
  WHERE  profiles.id = auth.uid();

  IF v_caller_role IS DISTINCT FROM 'admin' OR v_caller_inst IS NULL THEN
    RAISE EXCEPTION 'Unauthorized';
  END IF;

  -- Return has_sessions per UID.
  -- Institution-scope: only check UIDs that actually belong to a tag
  -- registered at a location in the caller's institution. UIDs that belong
  -- to another institution are excluded — their result is simply not returned,
  -- preventing cross-institution existence oracle via session inference.
  RETURN QUERY
  SELECT
    nt.uid                                         AS uid,
    EXISTS (
      SELECT 1 FROM public.focus_sessions fs
      WHERE  fs.tag_id = nt.uid
    )                                              AS has_sessions
  FROM   public.nfc_tags nt
  JOIN   public.locations l ON l.id = nt.location_id
  WHERE  nt.uid = ANY(p_uids)
    AND  l.institution_id = v_caller_inst;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.get_tag_session_status(TEXT[]) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.get_tag_session_status(TEXT[]) TO authenticated;


-- ============================================================
-- FUNCTION 2: reassign_nfc_tag
-- Changes a tag's location_id — only if tag has ZERO focus_sessions.
-- Concurrency-safe via SHARE lock on focus_sessions table.
-- ============================================================
CREATE OR REPLACE FUNCTION public.reassign_nfc_tag(p_tag_id UUID, p_new_location_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_role     TEXT;
  v_caller_inst     UUID;
  v_tag_uid         TEXT;
  v_tag_loc_inst    UUID;
  v_new_loc_inst    UUID;
  v_new_loc_active  BOOLEAN;
  v_session_count   BIGINT;
BEGIN
  -- 1. Authorize caller: must be admin with a non-null institution
  SELECT profiles.role, profiles.institution_id
  INTO   v_caller_role, v_caller_inst
  FROM   public.profiles
  WHERE  profiles.id = auth.uid();

  IF v_caller_role IS DISTINCT FROM 'admin' OR v_caller_inst IS NULL THEN
    RAISE EXCEPTION 'Unauthorized';
  END IF;

  -- 2. Acquire SHARE lock on focus_sessions BEFORE reading the session count.
  --
  --    A SHARE lock conflicts with the ROW EXCLUSIVE lock that INSERT requires.
  --    This means no concurrent focus_sessions INSERT can proceed until we
  --    commit or roll back — making the subsequent COUNT(*) a stable, race-free
  --    read. The FOR UPDATE OF nt below additionally prevents two concurrent
  --    reassign calls on the same tag from racing each other.
  --
  --    NOTE: SHARE mode does NOT block concurrent SELECTs (reads) by the
  --    Android app; it only blocks INSERT/UPDATE/DELETE on focus_sessions.
  --    Contention window is the duration of this transaction (milliseconds).
  LOCK TABLE public.focus_sessions IN SHARE MODE;

  -- 3. Lock the tag row and read its uid + current location's institution atomically.
  SELECT nt.uid, l.institution_id
  INTO   v_tag_uid, v_tag_loc_inst
  FROM   public.nfc_tags nt
  JOIN   public.locations l ON l.id = nt.location_id
  WHERE  nt.id = p_tag_id
  FOR UPDATE OF nt;

  -- Generic error to prevent cross-institution tag existence oracle
  IF v_tag_uid IS NULL THEN
    RAISE EXCEPTION 'Tag not found or not eligible for reassignment';
  END IF;
  IF v_tag_loc_inst IS DISTINCT FROM v_caller_inst THEN
    RAISE EXCEPTION 'Tag not found or not eligible for reassignment';
  END IF;

  -- 4. Validate new location: must exist, be active, belong to caller's institution
  SELECT locations.institution_id, locations.is_active
  INTO   v_new_loc_inst, v_new_loc_active
  FROM   public.locations
  WHERE  locations.id = p_new_location_id;

  IF v_new_loc_inst IS NULL THEN
    RAISE EXCEPTION 'Location not found or not eligible';
  END IF;
  IF v_new_loc_inst IS DISTINCT FROM v_caller_inst THEN
    RAISE EXCEPTION 'Location not found or not eligible';
  END IF;
  IF NOT v_new_loc_active THEN
    RAISE EXCEPTION 'Cannot reassign a tag to an inactive location';
  END IF;

  -- 5. Count sessions for this UID.
  --    Because we hold the SHARE lock on focus_sessions (step 2), this count
  --    is guaranteed stable — no concurrent INSERT can commit before us.
  SELECT COUNT(*)
  INTO   v_session_count
  FROM   public.focus_sessions fs
  WHERE  fs.tag_id = v_tag_uid;

  IF v_session_count > 0 THEN
    RAISE EXCEPTION
      'Cannot reassign: this tag has historical focus sessions and its location '
      'is permanently locked. To correct the physical mapping, deactivate this '
      'tag and register the physical UID under the correct location.';
  END IF;

  -- 6. Update location_id.
  --    The partial unique index idx_nfc_tags_one_active_per_location (from 000009)
  --    rejects this UPDATE if another active tag already occupies p_new_location_id.
  BEGIN
    UPDATE public.nfc_tags
    SET    location_id = p_new_location_id
    WHERE  nfc_tags.id = p_tag_id;
  EXCEPTION
    WHEN unique_violation THEN
      RAISE EXCEPTION 'The target location already has an active tag. Deactivate it first.';
  END;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.reassign_nfc_tag(UUID, UUID) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.reassign_nfc_tag(UUID, UUID) TO authenticated;
