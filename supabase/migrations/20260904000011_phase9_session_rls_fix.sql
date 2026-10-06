-- supabase/migrations/20260904000011_phase9_session_rls_fix.sql
--
-- PROBLEM:
--   The existing SELECT policies on focus_sessions and interception_events
--   (created in migration 000001) use inline EXISTS subqueries that directly
--   reference teacher_class_access, enrollments, classes, and nfc_tags.
--
--   When a student INSERTs a focus_session or queries their own sessions,
--   PostgreSQL evaluates the RLS policy's USING expression — including the
--   teacher branch — against the student's privileges. The student has no
--   SELECT grant on teacher_class_access, causing:
--     42501: permission denied for table teacher_class_access
--
--   The student branch (user_id = auth.uid()) is OR'd with the teacher
--   branch, but PostgreSQL may evaluate BOTH branches. Even though the
--   student branch would short-circuit logically, the planner still
--   parses and permission-checks the teacher branch's table references.
--
-- FIX:
--   Replace only the two problematic SELECT policies with versions that
--   delegate the teacher authorization check to a new SECURITY DEFINER
--   helper public.is_teacher_of_session(target_student_id UUID, target_tag_id TEXT).
--
--   Because is_teacher_of_session is SECURITY DEFINER, it executes as the
--   function owner (who has full table access), so the calling student never
--   needs direct SELECT on teacher_class_access, enrollments, classes, or
--   profiles.
--
--   This preserves the EXACT authorization semantics from migration 000001:
--   A teacher can only see a session if:
--     1. They have teacher_class_access to a class
--     2. The student is enrolled in that class
--     3. The class belongs to the teacher's institution
--     4. The student belongs to the teacher's institution
--     5. The session's tag_id matches an NFC tag assigned to that class's location
--
-- WHAT CHANGES:
--   focus_sessions SELECT policy — teacher branch simplified to:
--     get_auth_role() = 'teacher' AND is_teacher_of_session(user_id, tag_id)
--
--   interception_events SELECT policy — teacher branch simplified to:
--     get_auth_role() = 'teacher' AND EXISTS (
--       SELECT 1 FROM focus_sessions s
--       WHERE s.id = interception_events.session_id
--       AND is_teacher_of_session(s.user_id, s.tag_id)
--     )
--
-- WHAT REMAINS UNTOUCHED:
--   - "Students can insert own sessions" (INSERT on focus_sessions)
--   - "Students can update own sessions" (UPDATE on focus_sessions)
--   - "Students can insert own interception events" (INSERT on interception_events)
--   - All other tables' policies
--   - All table schemas
--   - All existing SECURITY DEFINER/search_path hardening
--
-- SECURITY MODEL:
--   Owner branch: user_id = auth.uid()
--     → Students can always read their own sessions/events. Unchanged.
--
--   Teacher branch: get_auth_role() = 'teacher' AND is_teacher_of_session(...)
--     → is_teacher_of_session internally validates all original constraints.
--     → Cross-institution access remains impossible.
--     → A teacher cannot see all sessions for a student, only those at the location of the class they teach.
--
-- CONCURRENCY / RECURSION:
--   For interception_events, evaluating the policy queries focus_sessions.
--   This triggers focus_sessions RLS, which evaluates its owner branch and
--   then calls is_teacher_of_session. The helper is SECURITY DEFINER and
--   accesses base tables (teacher_class_access, etc.) without triggering
--   further RLS on focus_sessions. The chain terminates safely without recursion.

-- ============================================================
-- 1. Create SECURITY DEFINER helper for session authorization
-- ============================================================
CREATE OR REPLACE FUNCTION public.is_teacher_of_session(target_student_id UUID, target_tag_id TEXT)
RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.teacher_class_access tca
    JOIN public.enrollments e ON tca.class_id = e.class_id
    JOIN public.classes c ON tca.class_id = c.id
    JOIN public.profiles p_student ON e.student_id = p_student.id
    JOIN public.nfc_tags nt ON c.location_id = nt.location_id
    WHERE tca.teacher_id = auth.uid() 
      AND e.student_id = target_student_id
      AND nt.uid = target_tag_id
      AND c.institution_id = public.get_auth_institution()
      AND p_student.institution_id = public.get_auth_institution()
  );
$$ LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public;

REVOKE EXECUTE ON FUNCTION public.is_teacher_of_session(UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_teacher_of_session(UUID, TEXT) TO authenticated;

-- ============================================================
-- 2. Replace focus_sessions SELECT policy
-- ============================================================
DROP POLICY IF EXISTS "Sessions are visible to owner and authorized teachers" ON public.focus_sessions;
CREATE POLICY "Sessions are visible to owner and authorized teachers"
ON public.focus_sessions FOR SELECT
TO authenticated
USING (
  focus_sessions.user_id = auth.uid()
  OR
  (
    public.get_auth_role() = 'teacher'
    AND public.is_teacher_of_session(focus_sessions.user_id, focus_sessions.tag_id)
  )
);

-- ============================================================
-- 3. Replace interception_events SELECT policy
-- ============================================================
DROP POLICY IF EXISTS "Events are visible to owner and authorized teachers" ON public.interception_events;
CREATE POLICY "Events are visible to owner and authorized teachers"
ON public.interception_events FOR SELECT
TO authenticated
USING (
  interception_events.user_id = auth.uid()
  OR
  (
    public.get_auth_role() = 'teacher'
    AND EXISTS (
      SELECT 1 FROM public.focus_sessions s
      WHERE  s.id = interception_events.session_id
        AND  public.is_teacher_of_session(s.user_id, s.tag_id)
    )
  )
);
