-- supabase/migrations/20260904000008_phase9_location_reactivate.sql
-- Phase 2B-1: Reactivate Location RPC

-- Secure RPC: reactivate_location
-- Encapsulates location reactivation so cross-institution reactivation is impossible.
CREATE OR REPLACE FUNCTION public.reactivate_location(p_location_id UUID)
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

  -- Perform reactivation scoped to caller's institution
  UPDATE public.locations
  SET    is_active = true
  WHERE  id = p_location_id
    AND  institution_id = v_caller_inst;

  -- If not found or not in same institution, no rows updated, which is fine (idempotent/secure).
END;
$$;

REVOKE EXECUTE ON FUNCTION public.reactivate_location(UUID) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.reactivate_location(UUID) TO authenticated;
