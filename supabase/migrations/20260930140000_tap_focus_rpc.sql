-- Pack 2/4: server-authoritative tap_focus + unique open session per student+classroom
-- Draft by Senior Dev — land after seed-complete / Lead clear
-- Existing focus_sessions columns (from history DTO): id, user_id, tag_id, start_at, end_at, status

ALTER TABLE public.focus_sessions
    ADD COLUMN IF NOT EXISTS classroom_id UUID REFERENCES public.classes(id),
    ADD COLUMN IF NOT EXISTS install_uuid TEXT,
    ADD COLUMN IF NOT EXISTS acs_health TEXT DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS policy_version TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now();

-- Map pilot states onto existing status column values
-- OPEN  == FOCUS_ACTIVE / 'active'
-- CLOSED == ENDED / 'ended' / 'completed'
-- We canonicalize new writes to 'FOCUS_ACTIVE' | 'ENDED'

CREATE UNIQUE INDEX IF NOT EXISTS uq_focus_sessions_open_student_classroom
    ON public.focus_sessions (user_id, classroom_id)
    WHERE status IN ('FOCUS_ACTIVE', 'active') AND classroom_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.tap_idempotency (
    idempotency_key UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    session_id UUID REFERENCES public.focus_sessions(id),
    accepted BOOLEAN NOT NULL,
    response JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tap_idempotency_user ON public.tap_idempotency (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.student_installs (
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    install_uuid TEXT NOT NULL,
    bound_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, install_uuid)
);

CREATE OR REPLACE FUNCTION public.tap_focus(
    p_tag_uid TEXT,
    p_install_uuid TEXT,
    p_idempotency_key UUID,
    p_acs_health TEXT DEFAULT 'UNKNOWN',
    p_force BOOLEAN DEFAULT FALSE,
    p_target_student_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_caller UUID := auth.uid();
    v_student UUID;
    v_tag TEXT;
    v_class_id UUID;
    v_policy_version TEXT;
    v_session public.focus_sessions%ROWTYPE;
    v_cached JSONB;
    v_accepted BOOLEAN := FALSE;
    v_new_state TEXT;
BEGIN
    IF v_caller IS NULL THEN
        RETURN jsonb_build_object('accepted', false, 'error', 'unauthenticated');
    END IF;

    SELECT response INTO v_cached
    FROM public.tap_idempotency
    WHERE idempotency_key = p_idempotency_key;
    IF FOUND THEN
        RETURN v_cached;
    END IF;

    IF p_force THEN
        v_student := COALESCE(p_target_student_id, v_caller);
        IF v_student IS DISTINCT FROM v_caller THEN
            IF NOT EXISTS (
                SELECT 1
                FROM public.focus_sessions s
                JOIN public.teacher_class_access tca ON tca.class_id = s.classroom_id
                WHERE s.user_id = v_student
                  AND s.status IN ('FOCUS_ACTIVE', 'active')
                  AND tca.teacher_id = v_caller
            ) THEN
                RETURN jsonb_build_object('accepted', false, 'error', 'forbidden_force_release');
            END IF;
        END IF;

        UPDATE public.focus_sessions
        SET status = 'ENDED',
            end_at = now(),
            updated_at = now(),
            acs_health = COALESCE(p_acs_health, acs_health)
        WHERE user_id = v_student
          AND status IN ('FOCUS_ACTIVE', 'active')
        RETURNING * INTO v_session;

        v_accepted := FOUND;
        v_cached := jsonb_build_object(
            'accepted', v_accepted,
            'session_id', v_session.id,
            'state', 'ENDED',
            'class_id', v_session.classroom_id,
            'policy_version', v_session.policy_version,
            'acs_hint', COALESCE(p_acs_health, v_session.acs_health),
            'force', true
        );
        INSERT INTO public.tap_idempotency(idempotency_key, user_id, session_id, accepted, response)
        VALUES (p_idempotency_key, v_caller, v_session.id, v_accepted, v_cached);
        RETURN v_cached;
    END IF;

    v_student := v_caller;
    v_tag := upper(trim(p_tag_uid));

    INSERT INTO public.student_installs(user_id, install_uuid)
    VALUES (v_student, p_install_uuid)
    ON CONFLICT (user_id, install_uuid)
    DO UPDATE SET last_seen_at = now();

    SELECT c.id INTO v_class_id
    FROM public.nfc_tags nt
    JOIN public.classes c ON c.location_id = nt.location_id
    WHERE nt.is_active = TRUE
      AND (upper(nt.uid) = v_tag OR upper(replace(nt.uid, ':', '')) = upper(replace(v_tag, ':', '')))
    LIMIT 1;

    IF v_class_id IS NULL THEN
        v_cached := jsonb_build_object('accepted', false, 'error', 'unknown_or_inactive_tag', 'state', 'ENDED');
        INSERT INTO public.tap_idempotency(idempotency_key, user_id, accepted, response)
        VALUES (p_idempotency_key, v_caller, false, v_cached);
        RETURN v_cached;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.enrollments e
        WHERE e.student_id = v_student AND e.class_id = v_class_id
    ) THEN
        v_cached := jsonb_build_object('accepted', false, 'error', 'not_enrolled', 'class_id', v_class_id);
        INSERT INTO public.tap_idempotency(idempotency_key, user_id, accepted, response)
        VALUES (p_idempotency_key, v_caller, false, v_cached);
        RETURN v_cached;
    END IF;

    BEGIN
        EXECUTE $q$
            SELECT version::text FROM public.class_app_policies
            WHERE class_id = $1
            ORDER BY updated_at DESC NULLS LAST
            LIMIT 1
        $q$ INTO v_policy_version USING v_class_id;
    EXCEPTION WHEN undefined_table THEN
        v_policy_version := NULL;
    END;

    SELECT * INTO v_session
    FROM public.focus_sessions
    WHERE user_id = v_student
      AND classroom_id = v_class_id
      AND status IN ('FOCUS_ACTIVE', 'active')
    FOR UPDATE;

    IF FOUND THEN
        -- Second tap: OPEN → CLOSED (idempotent same-state via tap_idempotency)
        UPDATE public.focus_sessions
        SET status = 'ENDED',
            end_at = now(),
            updated_at = now(),
            acs_health = COALESCE(p_acs_health, acs_health),
            install_uuid = COALESCE(p_install_uuid, install_uuid)
        WHERE id = v_session.id
        RETURNING * INTO v_session;
        v_new_state := 'ENDED';
        v_accepted := TRUE;
    ELSE
        INSERT INTO public.focus_sessions (
            user_id, classroom_id, tag_id, install_uuid, status, acs_health, policy_version, start_at, updated_at
        ) VALUES (
            v_student, v_class_id, v_tag, p_install_uuid, 'FOCUS_ACTIVE',
            COALESCE(p_acs_health, 'UNKNOWN'), v_policy_version, now(), now()
        )
        RETURNING * INTO v_session;
        v_new_state := 'FOCUS_ACTIVE';
        v_accepted := TRUE;
    END IF;

    v_cached := jsonb_build_object(
        'accepted', v_accepted,
        'session_id', v_session.id,
        'state', v_new_state,
        'class_id', v_class_id,
        'policy_version', v_policy_version,
        'acs_hint', COALESCE(p_acs_health, v_session.acs_health)
    );

    INSERT INTO public.tap_idempotency(idempotency_key, user_id, session_id, accepted, response)
    VALUES (p_idempotency_key, v_caller, v_session.id, v_accepted, v_cached);

    RETURN v_cached;
END;
$$;

REVOKE ALL ON FUNCTION public.tap_focus(TEXT, TEXT, UUID, TEXT, BOOLEAN, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.tap_focus(TEXT, TEXT, UUID, TEXT, BOOLEAN, UUID) TO authenticated;
