-- supabase/migrations/20260904000007_phase9_locations.sql
-- Phase 2B-1: Locations Schema and Secure RPCs

-- 1. Schema Updates
-- Add location type and is_active columns.
ALTER TABLE public.locations
  ADD COLUMN IF NOT EXISTS type TEXT NOT NULL DEFAULT 'classroom'
  CHECK (type IN ('classroom', 'library'));

ALTER TABLE public.locations
  ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true;

-- 2. Secure RPC: create_location
-- Encapsulates location creation so institution_id is never trusted from the client.
CREATE OR REPLACE FUNCTION public.create_location(p_name TEXT, p_type TEXT)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_role TEXT;
  v_caller_inst UUID;
  v_sanitized_name TEXT;
  v_sanitized_type TEXT;
  v_new_location_id UUID;
BEGIN
  -- Authorize caller
  SELECT profiles.role, profiles.institution_id
  INTO   v_caller_role, v_caller_inst
  FROM   public.profiles
  WHERE  profiles.id = auth.uid();

  IF v_caller_role IS DISTINCT FROM 'admin' OR v_caller_inst IS NULL THEN
    RAISE EXCEPTION 'Unauthorized';
  END IF;

  -- Validate name
  v_sanitized_name := TRIM(p_name);
  IF LENGTH(v_sanitized_name) = 0 THEN
    RAISE EXCEPTION 'Location name cannot be empty';
  END IF;
  IF LENGTH(v_sanitized_name) > 100 THEN
    RAISE EXCEPTION 'Location name exceeds maximum length';
  END IF;

  -- Validate type
  v_sanitized_type := TRIM(p_type);
  IF v_sanitized_type NOT IN ('classroom', 'library') THEN
    RAISE EXCEPTION 'Invalid location type';
  END IF;

  -- Insert securely
  INSERT INTO public.locations (institution_id, name, type, is_active)
  VALUES (v_caller_inst, v_sanitized_name, v_sanitized_type, true)
  RETURNING id INTO v_new_location_id;

  RETURN v_new_location_id;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.create_location(TEXT, TEXT) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.create_location(TEXT, TEXT) TO authenticated;


-- 3. Secure RPC: deactivate_location
-- Encapsulates soft-deletion so cross-institution deactivation is impossible.
CREATE OR REPLACE FUNCTION public.deactivate_location(p_location_id UUID)
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

  -- Perform soft-delete scoped to caller's institution
  UPDATE public.locations
  SET    is_active = false
  WHERE  id = p_location_id
    AND  institution_id = v_caller_inst;

  -- If not found or not in same institution, no rows updated, which is fine (idempotent/secure).
  -- We could check NOT FOUND and raise an error, but silent failure prevents existence oracles.
END;
$$;

REVOKE EXECUTE ON FUNCTION public.deactivate_location(UUID) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.deactivate_location(UUID) TO authenticated;
