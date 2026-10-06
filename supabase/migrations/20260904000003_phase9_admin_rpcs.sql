-- supabase/migrations/20260904000003_phase9_admin_rpcs.sql
-- Phase 9 Step 3A: Secure Admin Onboarding RPCs

-- ==================================================
-- FUNCTION 1: assign_user_to_institution
-- ==================================================
CREATE OR REPLACE FUNCTION public.assign_user_to_institution(target_user_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_id UUID := auth.uid();
  v_caller_role TEXT;
  v_caller_inst UUID;
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

  -- 3. Atomic update (TOCTOU prevention)
  UPDATE public.profiles
  SET institution_id = v_caller_inst
  WHERE id = target_user_id
    AND id != v_caller_id
    AND role = 'student'
    AND institution_id IS NULL;

  -- 4. Check if the update succeeded
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Unauthorized or invalid target state.';
  END IF;
END;
$$;

-- Secure execution permissions
REVOKE ALL ON FUNCTION public.assign_user_to_institution(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.assign_user_to_institution(UUID) TO authenticated;


-- ==================================================
-- FUNCTION 2: promote_student_to_teacher
-- ==================================================
CREATE OR REPLACE FUNCTION public.promote_student_to_teacher(target_user_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_id UUID := auth.uid();
  v_caller_role TEXT;
  v_caller_inst UUID;
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

  -- 3. Atomic update (TOCTOU prevention)
  UPDATE public.profiles
  SET role = 'teacher'
  WHERE id = target_user_id
    AND id != v_caller_id
    AND role = 'student'
    AND institution_id = v_caller_inst;

  -- 4. Check if the update succeeded
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Unauthorized or invalid target state.';
  END IF;
END;
$$;

-- Secure execution permissions
REVOKE ALL ON FUNCTION public.promote_student_to_teacher(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.promote_student_to_teacher(UUID) TO authenticated;
