-- supabase/migrations/20260904000014_phase9_audit_fixes.sql
-- Forensic Audit Fixes — Minimal Remediation
--
-- This migration addresses the following audit findings:
--
-- FIX 1: Silent RPC failures
--   Five RPCs silently succeed when the target row does not exist or is
--   cross-institution. The UI then reports false success. Adding IF NOT FOUND
--   checks so the RPCs raise an exception that the frontend can surface.
--
--   Affected RPCs:
--     a) deactivate_location   (also gets cascade fix — see FIX 2)
--     b) reactivate_location
--     c) deactivate_nfc_tag
--     d) remove_student_from_class
--     e) revoke_teacher_from_class
--
-- FIX 2: Location deactivation state consistency
--   When a location is deactivated, associated classes and NFC tags must also
--   become inactive. Without this, Android may see an active class/tag pointing
--   to a deactivated location.
--
--   Reactivation is intentionally left WITHOUT cascading reactivation of
--   dependents. The admin should explicitly choose which classes and tags to
--   reactivate after reactivating a location, since some may have been
--   independently deactivated.
--
-- WHAT IS NOT CHANGED:
--   - No schema changes (no ALTER TABLE, no new indexes, no FK changes)
--   - No new tables
--   - No RLS policy changes
--   - No grant changes
--   - No authentication architecture changes
--   - All existing authorization checks are preserved verbatim


-- ============================================================
-- FIX 1a + FIX 2: deactivate_location
-- Added: IF NOT FOUND check + cascade to classes and nfc_tags
-- ============================================================
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

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Target not found or access denied';
  END IF;

  -- Cascade: deactivate associated classes
  UPDATE public.classes
  SET    is_active = false
  WHERE  location_id = p_location_id
    AND  institution_id = v_caller_inst;

  -- Cascade: deactivate associated NFC tags
  UPDATE public.nfc_tags
  SET    is_active = false
  WHERE  location_id = p_location_id;
END;
$$;

-- Permissions already granted in migration 000007; no change needed.


-- ============================================================
-- FIX 1b: reactivate_location
-- Added: IF NOT FOUND check
-- Reactivation does NOT cascade to classes/tags (intentional —
-- admin should explicitly reactivate dependents).
-- ============================================================
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

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Target not found or access denied';
  END IF;
END;
$$;

-- Permissions already granted in migration 000008; no change needed.


-- ============================================================
-- FIX 1c: deactivate_nfc_tag
-- Added: IF NOT FOUND check
-- ============================================================
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

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Target not found or access denied';
  END IF;
END;
$$;

-- Permissions already granted in migration 000009; no change needed.


-- ============================================================
-- FIX 1d: remove_student_from_class
-- Added: IF NOT FOUND check after DELETE
-- ============================================================
CREATE OR REPLACE FUNCTION public.remove_student_from_class(
    p_student_id UUID,
    p_class_id UUID
)
RETURNS VOID AS $$
DECLARE
    v_admin_institution_id UUID;
    v_class_institution_id UUID;
BEGIN
    -- 1. Auth check
    IF public.get_auth_role() != 'admin' THEN
        RAISE EXCEPTION 'Unauthorized: Only admins can manage enrollments';
    END IF;

    v_admin_institution_id := public.get_auth_institution();
    IF v_admin_institution_id IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Admin institution not found';
    END IF;

    -- 2. Verify class ownership before deleting
    SELECT institution_id
    INTO v_class_institution_id
    FROM public.classes
    WHERE id = p_class_id;

    IF NOT FOUND OR v_class_institution_id != v_admin_institution_id THEN
        RAISE EXCEPTION 'Validation Error: Class not found or access denied';
    END IF;

    -- 3. Delete enrollment
    DELETE FROM public.enrollments
    WHERE student_id = p_student_id AND class_id = p_class_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Target not found or access denied';
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Permissions already granted in migration 000012; no change needed.


-- ============================================================
-- FIX 1e: revoke_teacher_from_class
-- Added: IF NOT FOUND check after DELETE
-- ============================================================
CREATE OR REPLACE FUNCTION public.revoke_teacher_from_class(
    p_teacher_id UUID,
    p_class_id UUID
)
RETURNS VOID AS $$
DECLARE
    v_admin_institution_id UUID;
    v_class_institution_id UUID;
BEGIN
    -- 1. Auth check
    IF public.get_auth_role() != 'admin' THEN
        RAISE EXCEPTION 'Unauthorized: Only admins can manage teacher access';
    END IF;

    v_admin_institution_id := public.get_auth_institution();
    IF v_admin_institution_id IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Admin institution not found';
    END IF;

    -- 2. Verify class ownership before deleting
    SELECT institution_id
    INTO v_class_institution_id
    FROM public.classes
    WHERE id = p_class_id;

    IF NOT FOUND OR v_class_institution_id != v_admin_institution_id THEN
        RAISE EXCEPTION 'Validation Error: Class not found or access denied';
    END IF;

    -- 3. Delete assignment
    DELETE FROM public.teacher_class_access
    WHERE teacher_id = p_teacher_id AND class_id = p_class_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Target not found or access denied';
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

-- Permissions already granted in migration 000012; no change needed.
