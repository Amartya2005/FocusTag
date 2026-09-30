-- C-path: institution/class app policies (draft — Architect)
-- Shape: institution_id / class_id / package → BLOCK|ALLOW|PROTECTED + version

CREATE TABLE IF NOT EXISTS public.class_app_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES public.institutions(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES public.classes(id) ON DELETE CASCADE,
    version TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (class_id, version)
);

CREATE INDEX IF NOT EXISTS idx_class_app_policies_class_updated
    ON public.class_app_policies (class_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS public.class_app_policy_packages (
    policy_id UUID NOT NULL REFERENCES public.class_app_policies(id) ON DELETE CASCADE,
    package_name TEXT NOT NULL,
    action TEXT NOT NULL CHECK (action IN ('BLOCK', 'ALLOW', 'PROTECTED')),
    PRIMARY KEY (policy_id, package_name)
);

CREATE TABLE IF NOT EXISTS public.institution_app_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES public.institutions(id) ON DELETE CASCADE,
    version TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (institution_id, version)
);

CREATE TABLE IF NOT EXISTS public.institution_app_policy_packages (
    policy_id UUID NOT NULL REFERENCES public.institution_app_policies(id) ON DELETE CASCADE,
    package_name TEXT NOT NULL,
    action TEXT NOT NULL CHECK (action IN ('BLOCK', 'ALLOW', 'PROTECTED')),
    PRIMARY KEY (policy_id, package_name)
);

CREATE OR REPLACE FUNCTION public.get_class_policy(p_class_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_pol public.class_app_policies%ROWTYPE;
    v_pkgs JSONB;
BEGIN
    IF auth.uid() IS NULL THEN
        RETURN jsonb_build_object('error', 'unauthenticated');
    END IF;

    SELECT * INTO v_pol
    FROM public.class_app_policies
    WHERE class_id = p_class_id
    ORDER BY updated_at DESC
    LIMIT 1;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('version', NULL, 'packages', '[]'::jsonb);
    END IF;

    SELECT COALESCE(jsonb_agg(jsonb_build_object(
        'package', package_name,
        'action', action
    ) ORDER BY package_name), '[]'::jsonb)
    INTO v_pkgs
    FROM public.class_app_policy_packages
    WHERE policy_id = v_pol.id;

    RETURN jsonb_build_object(
        'version', v_pol.version,
        'institution_id', v_pol.institution_id,
        'class_id', v_pol.class_id,
        'packages', v_pkgs,
        'updated_at', v_pol.updated_at
    );
END;
$$;

REVOKE ALL ON FUNCTION public.get_class_policy(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_class_policy(UUID) TO authenticated;

ALTER TABLE public.class_app_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.class_app_policy_packages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.institution_app_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.institution_app_policy_packages ENABLE ROW LEVEL SECURITY;

-- Minimal read: enrolled students + teachers with class access (tighten in follow-up)
CREATE POLICY class_app_policies_select ON public.class_app_policies
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.enrollments e
            WHERE e.class_id = class_app_policies.class_id AND e.student_id = auth.uid()
        )
        OR EXISTS (
            SELECT 1 FROM public.teacher_class_access t
            WHERE t.class_id = class_app_policies.class_id AND t.teacher_id = auth.uid()
        )
    );

CREATE POLICY class_app_policy_packages_select ON public.class_app_policy_packages
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.class_app_policies p
            JOIN public.enrollments e ON e.class_id = p.class_id AND e.student_id = auth.uid()
            WHERE p.id = class_app_policy_packages.policy_id
        )
        OR EXISTS (
            SELECT 1 FROM public.class_app_policies p
            JOIN public.teacher_class_access t ON t.class_id = p.class_id AND t.teacher_id = auth.uid()
            WHERE p.id = class_app_policy_packages.policy_id
        )
    );
