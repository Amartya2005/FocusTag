-- supabase/migrations/20260904000012_phase9_classes_rpcs.sql

-- ============================================================
-- 1. Schema Updates
-- ============================================================
ALTER TABLE public.classes ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true;

-- ============================================================
-- 2. create_class RPC
-- ============================================================
CREATE OR REPLACE FUNCTION public.create_class(
    p_name TEXT,
    p_location_id UUID
)
RETURNS UUID AS $$
DECLARE
    v_admin_institution_id UUID;
    v_location_institution_id UUID;
    v_location_is_active BOOLEAN;
    v_new_class_id UUID;
    v_trimmed_name TEXT;
BEGIN
    -- 1. Auth check
    IF public.get_auth_role() != 'admin' THEN
        RAISE EXCEPTION 'Unauthorized: Only admins can create classes';
    END IF;

    v_admin_institution_id := public.get_auth_institution();
    IF v_admin_institution_id IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Admin institution not found';
    END IF;

    -- 2. Validate input
    v_trimmed_name := trim(p_name);
    IF length(v_trimmed_name) = 0 THEN
        RAISE EXCEPTION 'Validation Error: Class name cannot be empty';
    END IF;
    IF length(v_trimmed_name) > 100 THEN
        RAISE EXCEPTION 'Validation Error: Class name too long';
    END IF;

    -- 3. Verify location
    SELECT institution_id, is_active 
    INTO v_location_institution_id, v_location_is_active
    FROM public.locations
    WHERE id = p_location_id;

    IF NOT FOUND OR v_location_institution_id != v_admin_institution_id THEN
        RAISE EXCEPTION 'Validation Error: Location not found or access denied';
    END IF;

    IF NOT v_location_is_active THEN
        RAISE EXCEPTION 'Validation Error: Cannot assign class to an inactive location';
    END IF;

    -- 4. Insert class
    BEGIN
        INSERT INTO public.classes (institution_id, location_id, name, is_active)
        VALUES (v_admin_institution_id, p_location_id, v_trimmed_name, true)
        RETURNING id INTO v_new_class_id;
    EXCEPTION WHEN unique_violation THEN
        RAISE EXCEPTION 'Validation Error: Location already has an assigned class';
    END;

    RETURN v_new_class_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

REVOKE EXECUTE ON FUNCTION public.create_class(TEXT, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.create_class(TEXT, UUID) TO authenticated;

-- ============================================================
-- 3. update_class_status RPC
-- ============================================================
CREATE OR REPLACE FUNCTION public.update_class_status(
    p_class_id UUID,
    p_is_active BOOLEAN
)
RETURNS VOID AS $$
DECLARE
    v_admin_institution_id UUID;
BEGIN
    -- 1. Auth check
    IF public.get_auth_role() != 'admin' THEN
        RAISE EXCEPTION 'Unauthorized: Only admins can update classes';
    END IF;

    v_admin_institution_id := public.get_auth_institution();
    IF v_admin_institution_id IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Admin institution not found';
    END IF;

    -- 2. Update class
    UPDATE public.classes
    SET is_active = p_is_active
    WHERE id = p_class_id 
      AND institution_id = v_admin_institution_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Validation Error: Class not found or access denied';
    END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

REVOKE EXECUTE ON FUNCTION public.update_class_status(UUID, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.update_class_status(UUID, BOOLEAN) TO authenticated;

-- ============================================================
-- 4. enroll_student_in_class RPC
-- ============================================================
CREATE OR REPLACE FUNCTION public.enroll_student_in_class(
    p_student_id UUID,
    p_class_id UUID
)
RETURNS VOID AS $$
DECLARE
    v_admin_institution_id UUID;
    v_student_institution_id UUID;
    v_student_role TEXT;
    v_class_institution_id UUID;
    v_class_is_active BOOLEAN;
BEGIN
    -- 1. Auth check
    IF public.get_auth_role() != 'admin' THEN
        RAISE EXCEPTION 'Unauthorized: Only admins can enroll students';
    END IF;

    v_admin_institution_id := public.get_auth_institution();
    IF v_admin_institution_id IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Admin institution not found';
    END IF;

    -- 2. Verify student
    SELECT institution_id, role 
    INTO v_student_institution_id, v_student_role
    FROM public.profiles
    WHERE id = p_student_id;

    IF NOT FOUND OR v_student_institution_id != v_admin_institution_id THEN
        RAISE EXCEPTION 'Validation Error: Student not found or access denied';
    END IF;

    IF v_student_role != 'student' THEN
        RAISE EXCEPTION 'Validation Error: User is not a student';
    END IF;

    -- 3. Verify class
    SELECT institution_id, is_active
    INTO v_class_institution_id, v_class_is_active
    FROM public.classes
    WHERE id = p_class_id;

    IF NOT FOUND OR v_class_institution_id != v_admin_institution_id THEN
        RAISE EXCEPTION 'Validation Error: Class not found or access denied';
    END IF;

    IF NOT v_class_is_active THEN
        RAISE EXCEPTION 'Validation Error: Cannot enroll student in an inactive class';
    END IF;

    -- 4. Insert enrollment
    BEGIN
        INSERT INTO public.enrollments (student_id, class_id)
        VALUES (p_student_id, p_class_id);
    EXCEPTION WHEN unique_violation THEN
        RAISE EXCEPTION 'Validation Error: Student is already enrolled in this class.';
    END;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

REVOKE EXECUTE ON FUNCTION public.enroll_student_in_class(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.enroll_student_in_class(UUID, UUID) TO authenticated;

-- ============================================================
-- 5. remove_student_from_class RPC
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
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

REVOKE EXECUTE ON FUNCTION public.remove_student_from_class(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.remove_student_from_class(UUID, UUID) TO authenticated;

-- ============================================================
-- 6. assign_teacher_to_class RPC
-- ============================================================
CREATE OR REPLACE FUNCTION public.assign_teacher_to_class(
    p_teacher_id UUID,
    p_class_id UUID
)
RETURNS VOID AS $$
DECLARE
    v_admin_institution_id UUID;
    v_teacher_institution_id UUID;
    v_teacher_role TEXT;
    v_class_institution_id UUID;
BEGIN
    -- 1. Auth check
    IF public.get_auth_role() != 'admin' THEN
        RAISE EXCEPTION 'Unauthorized: Only admins can assign teachers';
    END IF;

    v_admin_institution_id := public.get_auth_institution();
    IF v_admin_institution_id IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Admin institution not found';
    END IF;

    -- 2. Verify teacher
    SELECT institution_id, role 
    INTO v_teacher_institution_id, v_teacher_role
    FROM public.profiles
    WHERE id = p_teacher_id;

    IF NOT FOUND OR v_teacher_institution_id != v_admin_institution_id THEN
        RAISE EXCEPTION 'Validation Error: Teacher not found or access denied';
    END IF;

    IF v_teacher_role != 'teacher' THEN
        RAISE EXCEPTION 'Validation Error: User is not a teacher';
    END IF;

    -- 3. Verify class
    SELECT institution_id
    INTO v_class_institution_id
    FROM public.classes
    WHERE id = p_class_id;

    IF NOT FOUND OR v_class_institution_id != v_admin_institution_id THEN
        RAISE EXCEPTION 'Validation Error: Class not found or access denied';
    END IF;

    -- 4. Insert assignment
    BEGIN
        INSERT INTO public.teacher_class_access (teacher_id, class_id)
        VALUES (p_teacher_id, p_class_id);
    EXCEPTION WHEN unique_violation THEN
        RAISE EXCEPTION 'Validation Error: Teacher is already assigned to this class.';
    END;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

REVOKE EXECUTE ON FUNCTION public.assign_teacher_to_class(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.assign_teacher_to_class(UUID, UUID) TO authenticated;

-- ============================================================
-- 7. revoke_teacher_from_class RPC
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
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public;

REVOKE EXECUTE ON FUNCTION public.revoke_teacher_from_class(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.revoke_teacher_from_class(UUID, UUID) TO authenticated;
