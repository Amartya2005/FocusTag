-- Security hardening (Phase 2): RLS on remaining tables, anon lockdown,
-- focus_sessions client-write guard, RPC EXECUTE grants, search_path fix.
-- Builds on 20261007130000_focus_sessions_rls_teacher_scope (does not change its SELECT policies).

-- 1. RLS on tables that had it disabled -------------------------------------
ALTER TABLE public.interception_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tap_idempotency     ENABLE ROW LEVEL SECURITY;  -- written only by tap_focus (SECURITY DEFINER)
ALTER TABLE public.student_installs    ENABLE ROW LEVEL SECURITY;  -- written only by tap_focus (SECURITY DEFINER)
REVOKE ALL ON public.tap_idempotency, public.student_installs FROM anon, authenticated;

-- 2. anon gets nothing on public tables (login uses /auth/v1 only) ----------
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT c.relname FROM pg_class c
           WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r','v','m','p')
  LOOP
    EXECUTE format('REVOKE ALL ON public.%I FROM anon', r.relname);
    EXECUTE format('REVOKE TRUNCATE, REFERENCES, TRIGGER ON public.%I FROM authenticated', r.relname);
  END LOOP;
END $$;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon;

-- 3. focus_sessions client writes ------------------------------------------
-- The Android SyncWorker upserts (id,user_id,tag_id,start_at,end_at,status).
-- Keep that working, but only for own rows, and a guard trigger stops clients from
-- forging tap_focus-style sessions or altering server-owned fields.
DROP POLICY IF EXISTS "Students can insert own sessions" ON public.focus_sessions;
DROP POLICY IF EXISTS "Students can update own sessions" ON public.focus_sessions;
CREATE POLICY "Students can insert own sessions" ON public.focus_sessions
  FOR INSERT TO authenticated WITH CHECK (user_id = (SELECT auth.uid()));
CREATE POLICY "Students can update own sessions" ON public.focus_sessions
  FOR UPDATE TO authenticated
  USING (user_id = (SELECT auth.uid()))
  WITH CHECK (user_id = (SELECT auth.uid()));

CREATE OR REPLACE FUNCTION public.focus_sessions_client_guard()
RETURNS trigger LANGUAGE plpgsql SET search_path = '' AS $$
BEGIN
  -- tap_focus and other SECURITY DEFINER paths run as the owner, not 'authenticated'.
  IF current_user <> 'authenticated' THEN
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    IF NEW.user_id IS DISTINCT FROM auth.uid() THEN
      RAISE EXCEPTION 'focus_sessions: user_id must be caller' USING ERRCODE = '42501';
    END IF;
    IF NEW.status NOT IN ('IN_PROGRESS','COMPLETED','INTERRUPTED') THEN
      RAISE EXCEPTION 'focus_sessions: invalid client status' USING ERRCODE = '22023';
    END IF;
    -- server-owned fields: only tap_focus may set them
    NEW.classroom_id   := NULL;
    NEW.install_uuid   := NULL;
    NEW.entry_source   := 'client_sync';
    NEW.acs_health     := 'UNKNOWN';
    NEW.policy_version := NULL;
    NEW.created_at     := now();
    NEW.updated_at     := now();
    RETURN NEW;
  END IF;

  -- UPDATE: identity / server-owned fields are immutable (preserved silently so
  -- offline sync of a tap_focus session does not loop on errors).
  NEW.id             := OLD.id;
  NEW.user_id        := OLD.user_id;
  NEW.tag_id         := OLD.tag_id;
  NEW.classroom_id   := OLD.classroom_id;
  NEW.install_uuid   := OLD.install_uuid;
  NEW.entry_source   := OLD.entry_source;
  NEW.acs_health     := OLD.acs_health;
  NEW.policy_version := OLD.policy_version;
  NEW.start_at       := OLD.start_at;
  NEW.created_at     := OLD.created_at;
  NEW.updated_at     := now();

  -- status: only open -> closed (release flow). Closed sessions stay closed.
  IF OLD.status IN ('COMPLETED','INTERRUPTED','ENDED') THEN
    NEW.status := OLD.status;
    NEW.end_at := OLD.end_at;
  ELSIF NEW.status IS DISTINCT FROM OLD.status
        AND NEW.status NOT IN ('COMPLETED','INTERRUPTED') THEN
    NEW.status := OLD.status;   -- e.g. IN_PROGRESS overwrite of FOCUS_ACTIVE
  END IF;
  IF NEW.end_at IS NOT NULL AND NEW.end_at < NEW.start_at THEN
    NEW.end_at := OLD.end_at;
  END IF;
  RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.focus_sessions_client_guard() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS focus_sessions_client_guard ON public.focus_sessions;
CREATE TRIGGER focus_sessions_client_guard
  BEFORE INSERT OR UPDATE ON public.focus_sessions
  FOR EACH ROW EXECUTE FUNCTION public.focus_sessions_client_guard();

-- interception_events: own rows, session must be caller's own
DROP POLICY IF EXISTS "Students can insert own interception events" ON public.interception_events;
CREATE POLICY "Students can insert own interception events" ON public.interception_events
  FOR INSERT TO authenticated
  WITH CHECK (user_id = (SELECT auth.uid())
              AND EXISTS (SELECT 1 FROM public.focus_sessions s
                          WHERE s.id = interception_events.session_id
                            AND s.user_id = (SELECT auth.uid())));

-- 4. search_path ------------------------------------------------------------
ALTER FUNCTION public.generate_opaque_token() SET search_path = '';

-- 5. get_class_policy: scope to enrolled student / class teacher / institution admin
CREATE OR REPLACE FUNCTION public.get_class_policy(p_class_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    v_pol public.class_app_policies%ROWTYPE;
    v_pkgs JSONB;
BEGIN
    IF auth.uid() IS NULL THEN
        RETURN jsonb_build_object('error', 'unauthenticated');
    END IF;
    IF NOT (public.is_student_in_class(p_class_id)
            OR public.is_teacher_of_class(p_class_id)
            OR (public.get_auth_role() = 'admin' AND public.is_class_in_auth_institution(p_class_id))) THEN
        RETURN jsonb_build_object('error', 'forbidden');
    END IF;

    SELECT * INTO v_pol FROM public.class_app_policies
    WHERE class_id = p_class_id ORDER BY updated_at DESC LIMIT 1;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('version', NULL, 'packages', '[]'::jsonb);
    END IF;

    SELECT COALESCE(jsonb_agg(jsonb_build_object('package', package_name, 'action', action)
                              ORDER BY package_name), '[]'::jsonb)
    INTO v_pkgs FROM public.class_app_policy_packages WHERE policy_id = v_pol.id;

    RETURN jsonb_build_object('version', v_pol.version, 'institution_id', v_pol.institution_id,
        'class_id', v_pol.class_id, 'packages', v_pkgs, 'updated_at', v_pol.updated_at);
END $$;

-- 6. EXECUTE grants: no PUBLIC/anon on any public function; authenticated only for
--    RPCs and RLS helpers; trigger functions callable by nobody via the API.
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT p.oid::regprocedure AS sig, p.prorettype = 'trigger'::regtype AS is_trg
           FROM pg_proc p WHERE p.pronamespace = 'public'::regnamespace AND p.prokind = 'f'
  LOOP
    EXECUTE format('REVOKE EXECUTE ON FUNCTION %s FROM PUBLIC, anon', r.sig);
    IF r.is_trg THEN
      EXECUTE format('REVOKE EXECUTE ON FUNCTION %s FROM authenticated', r.sig);
    END IF;
  END LOOP;
END $$;
REVOKE EXECUTE ON FUNCTION public.generate_opaque_token() FROM authenticated;

GRANT EXECUTE ON FUNCTION
  public.assign_teacher_to_class(uuid, uuid), public.assign_user_to_institution(uuid),
  public.create_class(text, uuid), public.create_location(text, text),
  public.deactivate_location(uuid), public.deactivate_nfc_tag(uuid),
  public.demote_teacher_to_student(uuid), public.enroll_student_in_class(uuid, uuid),
  public.generate_qr_credential(uuid), public.get_auth_institution(), public.get_auth_role(),
  public.get_class_policy(uuid), public.get_class_student_activity(uuid),
  public.get_student_activity_summary(), public.get_tag_session_status(text[]),
  public.is_caller_admin(), public.is_class_in_auth_institution(uuid),
  public.is_student_in_class(uuid), public.is_teacher_of_class(uuid),
  public.is_teacher_of_session(uuid, text), public.is_teacher_of_student(uuid),
  public.promote_student_to_teacher(uuid), public.reactivate_location(uuid),
  public.reactivate_nfc_tag(uuid), public.reassign_nfc_tag(uuid, uuid),
  public.regenerate_qr_credential(uuid), public.register_nfc_tag(text, uuid),
  public.remove_student_from_class(uuid, uuid), public.resolve_qr_credential(text),
  public.revoke_qr_credential(uuid), public.revoke_teacher_from_class(uuid, uuid),
  public.search_unassigned_users(text),
  public.tap_focus(text, text, uuid, text, boolean, uuid, text),
  public.update_class_status(uuid, boolean)
TO authenticated;

ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC, anon;
