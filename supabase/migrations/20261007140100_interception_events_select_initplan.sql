-- Same semantics as before, but auth.uid() wrapped in SELECT (auth_rls_initplan).
DROP POLICY IF EXISTS "Events are visible to owner and authorized teachers" ON public.interception_events;
CREATE POLICY "Events are visible to owner and authorized teachers" ON public.interception_events
  FOR SELECT TO authenticated
  USING (
    user_id = (SELECT auth.uid())
    OR ((SELECT public.get_auth_role()) = 'teacher' AND EXISTS (
          SELECT 1 FROM public.focus_sessions s
          WHERE s.id = interception_events.session_id
            AND public.is_teacher_of_session(s.user_id, s.tag_id)))
  );
