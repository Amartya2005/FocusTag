-- supabase/migrations/20260904000009_phase9_nfc_tags_rpcs.sql
-- Phase 2B-2: NFC Tags Management RPCs
--
-- Schema findings: nfc_tags already has:
--   id UUID PK, uid TEXT UNIQUE NOT NULL, location_id UUID NOT NULL, is_active BOOLEAN DEFAULT TRUE, created_at
--
-- The UNIQUE constraint on uid prevents any duplicate uid across all rows (active or historical). Good.
-- Missing: uniqueness enforcement of "at most one active tag per location".
-- We add a partial unique index for that, then create the RPCs.

-- 1. Enforce "at most one active tag per location" at DB level.
--    A partial unique index on (location_id) WHERE is_active = true.
CREATE UNIQUE INDEX IF NOT EXISTS idx_nfc_tags_one_active_per_location
  ON public.nfc_tags (location_id)
  WHERE is_active = true;


-- 2. RPC: register_nfc_tag(p_uid TEXT, p_location_id UUID)
-- Registers a new NFC tag assigned to a location belonging to the caller's institution.
-- Validates: caller is admin, location is active + belongs to institution,
--            no other active tag on same location, UID format.
CREATE OR REPLACE FUNCTION public.register_nfc_tag(p_uid TEXT, p_location_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_role      TEXT;
  v_caller_inst      UUID;
  v_normalized_uid   TEXT;
  v_loc_institution  UUID;
  v_loc_is_active    BOOLEAN;
  v_new_tag_id       UUID;
BEGIN
  -- Authorize caller
  SELECT profiles.role, profiles.institution_id
  INTO   v_caller_role, v_caller_inst
  FROM   public.profiles
  WHERE  profiles.id = auth.uid();

  IF v_caller_role IS DISTINCT FROM 'admin' OR v_caller_inst IS NULL THEN
    RAISE EXCEPTION 'Unauthorized';
  END IF;

  -- Normalize and validate UID
  -- Canonical: uppercase hex bytes separated by colons e.g. 1D:FF:7C:1C:1A:10:80
  v_normalized_uid := UPPER(TRIM(p_uid));
  IF NOT (v_normalized_uid ~ '^[0-9A-F]{2}(:[0-9A-F]{2}){3,9}$') THEN
    RAISE EXCEPTION 'Invalid UID format. Expected uppercase hex bytes separated by colons (e.g. 1D:FF:7C:1C:1A:10:80)';
  END IF;

  -- Validate location: must exist, must be active, must belong to caller's institution
  SELECT locations.institution_id, locations.is_active
  INTO   v_loc_institution, v_loc_is_active
  FROM   public.locations
  WHERE  locations.id = p_location_id;

  -- Use generic message to avoid leaking cross-institution location existence
  IF v_loc_institution IS NULL THEN
    RAISE EXCEPTION 'Location not found or not eligible';
  END IF;
  IF v_loc_institution IS DISTINCT FROM v_caller_inst THEN
    RAISE EXCEPTION 'Location not found or not eligible';
  END IF;
  IF NOT v_loc_is_active THEN
    RAISE EXCEPTION 'Cannot assign a tag to an inactive location';
  END IF;

  -- Insert new tag. The partial unique index on (location_id) WHERE is_active = true
  -- will reject this if another active tag already exists for this location.
  BEGIN
    INSERT INTO public.nfc_tags (uid, location_id, is_active)
    VALUES (v_normalized_uid, p_location_id, true)
    RETURNING nfc_tags.id INTO v_new_tag_id;
  EXCEPTION
    WHEN unique_violation THEN
      RAISE EXCEPTION 'A tag with this UID already exists, or this location already has an active tag';
  END;

  RETURN v_new_tag_id;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.register_nfc_tag(TEXT, UUID) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.register_nfc_tag(TEXT, UUID) TO authenticated;


-- 3. RPC: deactivate_nfc_tag(p_tag_id UUID)
-- Soft-deactivates a tag belonging to the caller's institution.
CREATE OR REPLACE FUNCTION public.deactivate_nfc_tag(p_tag_id UUID)
RETURNS VOID
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

  -- Deactivate only if the tag's location belongs to the caller's institution
  UPDATE public.nfc_tags
  SET    is_active = false
  WHERE  nfc_tags.id = p_tag_id
    AND  EXISTS (
      SELECT 1 FROM public.locations l
      WHERE  l.id = nfc_tags.location_id
        AND  l.institution_id = v_caller_inst
    );

  -- Silent no-op if tag not found / cross-institution (prevents existence oracle)
END;
$$;

REVOKE EXECUTE ON FUNCTION public.deactivate_nfc_tag(UUID) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.deactivate_nfc_tag(UUID) TO authenticated;


-- 4. RPC: reactivate_nfc_tag(p_tag_id UUID)
-- Reactivates an inactive tag. Will fail if another active tag already occupies the location
-- (the partial unique index enforces this at the DB level).
CREATE OR REPLACE FUNCTION public.reactivate_nfc_tag(p_tag_id UUID)
RETURNS VOID
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

  BEGIN
    UPDATE public.nfc_tags
    SET    is_active = true
    WHERE  nfc_tags.id = p_tag_id
      AND  EXISTS (
        SELECT 1 FROM public.locations l
        WHERE  l.id = nfc_tags.location_id
          AND  l.institution_id = v_caller_inst
      );
  EXCEPTION
    WHEN unique_violation THEN
      RAISE EXCEPTION 'This location already has an active tag. Deactivate it first before reactivating another.';
  END;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.reactivate_nfc_tag(UUID) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.reactivate_nfc_tag(UUID) TO authenticated;
