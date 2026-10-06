-- supabase/migrations/20260904000013_phase9_classes_grants.sql
-- Grant SELECT permissions to authenticated role for Classes Dashboard read access

GRANT SELECT ON TABLE public.classes TO authenticated;
GRANT SELECT ON TABLE public.locations TO authenticated;
GRANT SELECT ON TABLE public.enrollments TO authenticated;
GRANT SELECT ON TABLE public.teacher_class_access TO authenticated;
