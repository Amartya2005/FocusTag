-- supabase/migrations/20260904000016_phase9_demote_teacher.sql
-- Phase 9: Teacher Role Reversibility — Demote Teacher to Student
--
-- This migration adds the inverse of promote_student_to_teacher.
--
-- Behavior (Option B from the role reversibility audit):
--   1. Verify caller is an admin in the same institution as the target.
--   2. Verify target is currently a teacher (not admin, not student).
--   3. Verify target is not the caller (admin cannot demote themselves).
--   4. Delete all teacher_class_access rows for the target.
--   5. Update profiles.role from 'teacher' to 'student'.
--   6. Return the number of removed class assignments.
--
-- Steps 4 and 5 execute atomically within the same transaction.
--
-- WHAT IS NOT CHANGED:
--   - No schema changes (no ALTER TABLE, no new indexes, no FK changes)
--   - No new tables
--   - No RLS policy changes
--   - No grant changes beyond the new function
--   - No authentication architecture changes
--   - promote_student_to_teacher remains unchanged
--   - institution_id is never modified
--   - The user's auth account is never deleted
--   - The user's profile row is never deleted

CREATE OR REPLACE FUNCTION public.demote_teacher_to_student(target_user_id UUID)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_id UUID := auth.uid();
  v_caller_role TEXT;
  v_caller_inst UUID;
  v_removed_count INTEGER;
BEGIN
  -- Verify caller is authenticated
  IF v_caller_id IS NULL THEN
    RAISE EXCEPTION 'Unauthorized or invalid target state.';
  END IF;

  -- 1. Get caller profile
  SELECT role, institution_id INTO v_caller_role, v_caller_inst
  FROM public.profiles
  WHERE id = v_caller_id;

  -- 2. Basic caller authorization (must be admin with an institution)
  IF v_caller_role IS DISTINCT FROM 'admin' OR v_caller_inst IS NULL THEN
    RAISE EXCEPTION 'Unauthorized or invalid target state.';
  END IF;

  -- 3. Delete teacher_class_access rows for the target
  --    This runs BEFORE the role update so the WHERE clause can
  --    also serve as an implicit existence/role/institution check.
  DELETE FROM public.teacher_class_access
  WHERE teacher_id = target_user_id
    AND EXISTS (
      SELECT 1 FROM public.profiles p
      WHERE p.id = target_user_id
        AND p.role = 'teacher'
        AND p.institution_id = v_caller_inst
        AND p.id != v_caller_id
    );

  GET DIAGNOSTICS v_removed_count = ROW_COUNT;

  -- 4. Atomic role update (TOCTOU prevention)
  UPDATE public.profiles
  SET role = 'student'
  WHERE id = target_user_id
    AND id != v_caller_id
    AND role = 'teacher'
    AND institution_id = v_caller_inst;

  -- 5. Check if the update succeeded
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Unauthorized or invalid target state.';
  END IF;

  RETURN v_removed_count;
END;
$$;

-- Secure execution permissions
REVOKE ALL ON FUNCTION public.demote_teacher_to_student(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.demote_teacher_to_student(UUID) TO authenticated;
