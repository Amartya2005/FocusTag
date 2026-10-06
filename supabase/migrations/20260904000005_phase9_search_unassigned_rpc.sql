-- supabase/migrations/20260904000005_phase9_search_unassigned_rpc.sql
-- Secure admin-only RPC for discovering unassigned users eligible for institutional onboarding.
-- The function owner is 'postgres' (superuser) due to Supabase migration execution context.
-- SECURITY DEFINER causes the body to run as 'postgres', granting access to auth.users.
-- The 'authenticated' role does NOT gain any direct access to auth.users through this function.
-- Only the three explicitly declared RETURNS TABLE columns are ever returned to the caller.

CREATE OR REPLACE FUNCTION public.search_unassigned_users(search_email TEXT)
RETURNS TABLE(id UUID, email TEXT, name TEXT)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_role TEXT;
  v_caller_inst UUID;
  v_sanitized   TEXT;
BEGIN
  -- 1. Authorize caller: must be admin with an assigned institution.
  --    Authorization is derived exclusively from auth.uid() -> profiles.
  --    No institution_id or role is accepted as a caller-controlled parameter.
  SELECT role, institution_id
  INTO   v_caller_role, v_caller_inst
  FROM   public.profiles
  WHERE  id = auth.uid();

  IF v_caller_role IS DISTINCT FROM 'admin' OR v_caller_inst IS NULL THEN
    RAISE EXCEPTION 'Unauthorized';
  END IF;

  -- 2. Sanitize and validate the search input.
  v_sanitized := TRIM(search_email);

  IF LENGTH(v_sanitized) < 3 THEN
    RAISE EXCEPTION 'Search term must be at least 3 characters';
  END IF;

  IF LENGTH(v_sanitized) > 254 THEN
    RAISE EXCEPTION 'Search term exceeds maximum length';
  END IF;

  -- 3. Escape ILIKE wildcard characters so the prefix is treated as a LITERAL string.
  --    Escape order: backslash first to prevent double-escaping, then % and _.
  v_sanitized := replace(v_sanitized, '\', '\\');
  v_sanitized := replace(v_sanitized, '%', '\%');
  v_sanitized := replace(v_sanitized, '_', '\_');

  -- 4. Return only eligible unassigned students (institution_id IS NULL, role = 'student').
  --    Results are capped at 20 to prevent bulk enumeration.
  --    Only id, email, and name are returned — no passwords, tokens, or auth metadata.
  RETURN QUERY
  SELECT   p.id, u.email::TEXT, p.name
  FROM     public.profiles p
  JOIN     auth.users u ON p.id = u.id
  WHERE    p.institution_id IS NULL
    AND    p.role = 'student'
    AND    u.email ILIKE v_sanitized || '%' ESCAPE '\'
  LIMIT 20;
END;
$$;

-- Restrict execution: revoke from PUBLIC, grant only to authenticated role.
REVOKE EXECUTE ON FUNCTION public.search_unassigned_users(TEXT) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.search_unassigned_users(TEXT) TO authenticated;
