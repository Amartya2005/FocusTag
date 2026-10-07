-- Demo classroom provisioning for the teacher dashboard (CSB 1 - CSB 10)
-- Creates/reuses ten classroom locations and classes inside the signed-in admin's
-- institution, then assigns the first available teacher profiles one-to-one to
-- CSB 1, CSB 2, ... until teachers are exhausted.
--
-- This is intentionally an explicit admin RPC, not an automatic migration seed,
-- so running migrations never mutates an institution's live class layout.

CREATE OR REPLACE FUNCTION public.seed_demo_csb_classrooms()
RETURNS TABLE (
  created_classes integer,
  assigned_teachers integer
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_inst uuid := public.get_auth_institution();
  v_created integer := 0;
  v_assigned integer := 0;
  v_location_id uuid;
  v_class_id uuid;
  v_teacher_id uuid;
  v_room_name text;
  v_teacher_rank integer;
BEGIN
  IF public.get_auth_role() <> 'admin' THEN
    RAISE EXCEPTION 'Unauthorized: Only admins can seed demo classrooms'
      USING ERRCODE = '42501';
  END IF;

  IF v_inst IS NULL THEN
    RAISE EXCEPTION 'Unauthorized: Admin institution not found'
      USING ERRCODE = '42501';
  END IF;

  FOR v_teacher_rank IN 1..10 LOOP
    SELECT p.id
    INTO v_teacher_id
    FROM public.profiles p
    WHERE p.institution_id = v_inst
      AND p.role = 'teacher'
    ORDER BY p.name NULLS LAST, p.id
    OFFSET v_teacher_rank - 1
    LIMIT 1;

    v_room_name := 'CSB ' || v_teacher_rank;

    SELECT l.id
    INTO v_location_id
    FROM public.locations l
    WHERE l.institution_id = v_inst
      AND lower(trim(l.name)) = lower(v_room_name)
    ORDER BY l.created_at, l.id
    LIMIT 1;

    IF v_location_id IS NULL THEN
      INSERT INTO public.locations (institution_id, name, is_active)
      VALUES (v_inst, v_room_name, true)
      RETURNING id INTO v_location_id;
    ELSE
      UPDATE public.locations
      SET is_active = true
      WHERE id = v_location_id;
    END IF;

    SELECT c.id
    INTO v_class_id
    FROM public.classes c
    WHERE c.institution_id = v_inst
      AND c.location_id = v_location_id
    LIMIT 1;

    IF v_class_id IS NULL THEN
      INSERT INTO public.classes (institution_id, location_id, name, is_active)
      VALUES (v_inst, v_location_id, v_room_name, true)
      RETURNING id INTO v_class_id;
      v_created := v_created + 1;
    ELSE
      UPDATE public.classes
      SET name = v_room_name,
          is_active = true
      WHERE id = v_class_id;
    END IF;

    IF v_teacher_id IS NOT NULL
       AND NOT EXISTS (
         SELECT 1
         FROM public.teacher_class_access tca
         WHERE tca.teacher_id = v_teacher_id
           AND tca.class_id = v_class_id
       ) THEN
      INSERT INTO public.teacher_class_access (teacher_id, class_id)
      VALUES (v_teacher_id, v_class_id);
      v_assigned := v_assigned + 1;
    END IF;
  END LOOP;

  RETURN QUERY SELECT v_created, v_assigned;
END;
$$;

REVOKE ALL ON FUNCTION public.seed_demo_csb_classrooms() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.seed_demo_csb_classrooms() TO authenticated;
