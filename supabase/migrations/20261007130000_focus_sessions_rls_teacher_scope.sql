-- focus_sessions had policies defined but RLS was DISABLED, so any authenticated
-- user (incl. teachers) could read every student's sessions. Enforce scope via
-- RLS; teacher visibility flows only through teacher_class_access.
ALTER TABLE public.focus_sessions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Sessions are visible to owner and authorized teachers" ON public.focus_sessions;
CREATE POLICY "Sessions are visible to owner and authorized teachers"
  ON public.focus_sessions FOR SELECT TO authenticated
  USING (
    user_id = (SELECT auth.uid())
    OR ((SELECT public.get_auth_role()) = 'teacher' AND public.is_teacher_of_student(user_id))
  );

DROP POLICY IF EXISTS "Sessions are visible to institution admins" ON public.focus_sessions;
CREATE POLICY "Sessions are visible to institution admins"
  ON public.focus_sessions FOR SELECT TO authenticated
  USING (
    (SELECT public.get_auth_role()) = 'admin'
    AND EXISTS (SELECT 1 FROM public.profiles p
                WHERE p.id = focus_sessions.user_id
                  AND p.institution_id = (SELECT public.get_auth_institution()))
  );
