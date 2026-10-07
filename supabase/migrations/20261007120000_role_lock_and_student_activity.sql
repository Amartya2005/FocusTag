-- Role lock + student activity (2026-10-07)
-- 1) Block non-admin changes to profiles.role / profiles.institution_id.
-- 2) Tighten the self-update RLS policy (WITH CHECK pins role + institution).
-- 3) Scoped activity RPCs for teachers/admins (active = open FOCUS_ACTIVE session from tap_focus).
-- 4) Realtime publication for dashboard refresh.

CREATE OR REPLACE FUNCTION public.is_caller_admin()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'admin'
  );
$$;

REVOKE ALL ON FUNCTION public.is_caller_admin() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_caller_admin() TO authenticated, service_role;

CREATE OR REPLACE FUNCTION public.enforce_profile_role_lock()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
  IF NEW.role IS NOT DISTINCT FROM OLD.role
     AND NEW.institution_id IS NOT DISTINCT FROM OLD.institution_id THEN
    RETURN NEW;
  END IF;

  -- service_role (backend) and direct DB sessions without an end-user JWT
  -- (migrations, SQL editor, auth triggers) are allowed.
  IF coalesce(auth.role(), '') = 'service_role' OR auth.uid() IS NULL THEN
    RETURN NEW;
  END IF;

  IF public.is_caller_admin() THEN
    RETURN NEW;
  END IF;

  RAISE EXCEPTION 'Only an admin can change role or institution_id'
    USING ERRCODE = '42501';
END;
$$;

DROP TRIGGER IF EXISTS profiles_role_lock ON public.profiles;
CREATE TRIGGER profiles_role_lock
  BEFORE UPDATE ON public.profiles
  FOR EACH ROW
  EXECUTE FUNCTION public.enforce_profile_role_lock();

-- Self-update policy: same intent as before, using SECURITY DEFINER helpers
-- (avoids recursive self-select on profiles).
DROP POLICY IF EXISTS "Users can update their own name only" ON public.profiles;
CREATE POLICY "Users can update their own name only"
  ON public.profiles
  FOR UPDATE
  TO authenticated
  USING (id = auth.uid())
  WITH CHECK (
    id = auth.uid()
    AND role IS NOT DISTINCT FROM public.get_auth_role()
    AND institution_id IS NOT DISTINCT FROM public.get_auth_institution()
  );

-- Student activity
CREATE OR REPLACE FUNCTION public.get_class_student_activity(p_class_id uuid)
RETURNS TABLE (student_id uuid, is_active boolean, active_since timestamptz, last_activity_at timestamptz)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  v_role text := public.get_auth_role();
BEGIN
  IF NOT (
    (v_role = 'admin' AND public.is_class_in_auth_institution(p_class_id))
    OR (v_role = 'teacher' AND public.is_teacher_of_class(p_class_id))
  ) THEN
    RAISE EXCEPTION 'Unauthorized' USING ERRCODE = '42501';
  END IF;

  RETURN QUERY
  SELECT e.student_id,
         bool_or(fs.status IN ('FOCUS_ACTIVE', 'active') AND fs.end_at IS NULL) IS TRUE,
         max(fs.start_at) FILTER (WHERE fs.status IN ('FOCUS_ACTIVE', 'active') AND fs.end_at IS NULL),
         max(greatest(fs.start_at, fs.end_at, fs.updated_at))
  FROM public.enrollments e
  LEFT JOIN public.focus_sessions fs ON fs.user_id = e.student_id
  WHERE e.class_id = p_class_id
  GROUP BY e.student_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.get_student_activity_summary()
RETURNS TABLE (student_id uuid, name text, is_active boolean, active_since timestamptz, last_activity_at timestamptz)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  v_role text := public.get_auth_role();
  v_inst uuid := public.get_auth_institution();
BEGIN
  IF v_role NOT IN ('admin', 'teacher') OR v_inst IS NULL THEN
    RAISE EXCEPTION 'Unauthorized' USING ERRCODE = '42501';
  END IF;

  RETURN QUERY
  WITH scope AS (
    SELECT p.id, p.name FROM public.profiles p
    WHERE v_role = 'admin' AND p.role = 'student' AND p.institution_id = v_inst
    UNION
    SELECT p.id, p.name FROM public.teacher_class_access tca
    JOIN public.classes c ON c.id = tca.class_id AND c.institution_id = v_inst
    JOIN public.enrollments e ON e.class_id = tca.class_id
    JOIN public.profiles p ON p.id = e.student_id AND p.institution_id = v_inst
    WHERE v_role = 'teacher' AND tca.teacher_id = auth.uid()
  )
  SELECT s.id, s.name,
         bool_or(fs.status IN ('FOCUS_ACTIVE', 'active') AND fs.end_at IS NULL) IS TRUE,
         max(fs.start_at) FILTER (WHERE fs.status IN ('FOCUS_ACTIVE', 'active') AND fs.end_at IS NULL),
         max(greatest(fs.start_at, fs.end_at, fs.updated_at))
  FROM scope s
  LEFT JOIN public.focus_sessions fs ON fs.user_id = s.id
  GROUP BY s.id, s.name;
END;
$$;

REVOKE ALL ON FUNCTION public.get_class_student_activity(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_student_activity_summary() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_class_student_activity(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_student_activity_summary() TO authenticated;

-- Realtime (dashboard router.refresh triggers; RLS still applies)
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['profiles', 'enrollments', 'focus_sessions'] LOOP
    IF NOT EXISTS (
      SELECT 1 FROM pg_publication_tables
      WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = t
    ) THEN
      EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', t);
    END IF;
  END LOOP;
END $$;
