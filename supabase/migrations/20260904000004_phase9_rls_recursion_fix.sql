-- 20260904000004_phase9_rls_recursion_fix.sql
-- Resolves 42P17 Infinite Recursion between profiles, classes, enrollments, and teacher_class_access
-- Implements securely scoped helpers with explicit cross-institution isolation

-- 1. Create narrowly scoped SECURITY DEFINER helpers for relationship checks
-- These bypass RLS on the target tables to prevent recursive policy evaluation,
-- returning a boolean confirming the authenticated user's relationship.
-- Institution boundaries are explicitly enforced inside these helpers.

CREATE OR REPLACE FUNCTION public.is_teacher_of_class(target_class_id UUID)
RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.teacher_class_access tca
    JOIN public.classes c ON tca.class_id = c.id
    WHERE tca.class_id = target_class_id 
      AND tca.teacher_id = auth.uid()
      AND c.institution_id = public.get_auth_institution()
  );
$$ LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.is_student_in_class(target_class_id UUID)
RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.enrollments e
    JOIN public.classes c ON e.class_id = c.id
    WHERE e.class_id = target_class_id 
      AND e.student_id = auth.uid()
      AND c.institution_id = public.get_auth_institution()
  );
$$ LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.is_teacher_of_student(target_student_id UUID)
RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.teacher_class_access tca
    JOIN public.enrollments e ON tca.class_id = e.class_id
    JOIN public.classes c ON tca.class_id = c.id
    JOIN public.profiles p_student ON e.student_id = p_student.id
    WHERE tca.teacher_id = auth.uid() 
      AND e.student_id = target_student_id
      AND c.institution_id = public.get_auth_institution()
      AND p_student.institution_id = public.get_auth_institution()
  );
$$ LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public;

CREATE OR REPLACE FUNCTION public.is_class_in_auth_institution(target_class_id UUID)
RETURNS BOOLEAN AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.classes
    WHERE id = target_class_id 
      AND institution_id = public.get_auth_institution()
  );
$$ LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public;

-- Ensure Execution Privileges are strictly limited
-- RLS policies execute under the privileges of the calling user. 
-- Thus, 'authenticated' requires EXECUTE to evaluate the policy.
REVOKE EXECUTE ON FUNCTION public.is_teacher_of_class(UUID) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.is_student_in_class(UUID) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.is_teacher_of_student(UUID) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.is_class_in_auth_institution(UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.is_teacher_of_class(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_student_in_class(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_teacher_of_student(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_class_in_auth_institution(UUID) TO authenticated;


-- 2. Update existing policies to use the non-recursive helpers

-- Profiles (Sever link to teacher_class_access)
DROP POLICY IF EXISTS "Profiles are visible to self, authorized teachers, and admins" ON public.profiles;
CREATE POLICY "Profiles are visible to self, authorized teachers, and admins"
ON public.profiles FOR SELECT
TO authenticated
USING (
  id = auth.uid() OR
  (public.get_auth_role() = 'admin' AND institution_id = public.get_auth_institution()) OR
  (public.get_auth_role() = 'teacher' AND public.is_teacher_of_student(id))
);

-- Classes (Sever link to teacher_class_access and enrollments)
DROP POLICY IF EXISTS "Classes are visible to enrolled students, authorized teachers, and admins" ON public.classes;
CREATE POLICY "Classes are visible to enrolled students, authorized teachers, and admins"
ON public.classes FOR SELECT
TO authenticated
USING (
  (public.get_auth_role() = 'admin' AND institution_id = public.get_auth_institution()) OR
  (public.get_auth_role() = 'teacher' AND public.is_teacher_of_class(id)) OR
  (public.get_auth_role() = 'student' AND public.is_student_in_class(id))
);

-- Enrollments (Sever link to classes and teacher_class_access)
DROP POLICY IF EXISTS "Enrollments are visible to students and their teachers" ON public.enrollments;
DROP POLICY IF EXISTS "Enrollments are visible to participants and admins" ON public.enrollments;
CREATE POLICY "Enrollments are visible to participants and admins"
ON public.enrollments FOR SELECT
TO authenticated
USING (
  student_id = auth.uid() OR
  (public.get_auth_role() = 'admin' AND public.is_class_in_auth_institution(class_id)) OR
  (public.get_auth_role() = 'teacher' AND public.is_teacher_of_class(class_id))
);

DROP POLICY IF EXISTS "Enrollments are manageable by admins" ON public.enrollments;
CREATE POLICY "Enrollments are manageable by admins"
ON public.enrollments FOR ALL
TO authenticated
USING (
  public.get_auth_role() = 'admin' AND public.is_class_in_auth_institution(class_id)
)
WITH CHECK (
  public.get_auth_role() = 'admin' AND
  public.is_class_in_auth_institution(class_id) AND
  EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = enrollments.student_id AND p.institution_id = public.get_auth_institution())
);

-- Teacher Class Access (Sever link to classes)
DROP POLICY IF EXISTS "Teacher access is visible to self and admins" ON public.teacher_class_access;
CREATE POLICY "Teacher access is visible to self and admins"
ON public.teacher_class_access FOR SELECT
TO authenticated
USING (
  teacher_id = auth.uid() OR
  (public.get_auth_role() = 'admin' AND public.is_class_in_auth_institution(class_id))
);

DROP POLICY IF EXISTS "Teacher access is manageable by admins" ON public.teacher_class_access;
CREATE POLICY "Teacher access is manageable by admins"
ON public.teacher_class_access FOR ALL
TO authenticated
USING (
  public.get_auth_role() = 'admin' AND public.is_class_in_auth_institution(class_id)
)
WITH CHECK (
  public.get_auth_role() = 'admin' AND
  public.is_class_in_auth_institution(class_id) AND
  EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = teacher_class_access.teacher_id AND p.institution_id = public.get_auth_institution() AND p.role = 'teacher')
);
