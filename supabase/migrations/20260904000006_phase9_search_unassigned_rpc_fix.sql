-- supabase/migrations/20260904000006_phase9_search_unassigned_rpc_fix.sql
-- Fixes ambiguous column reference in search_unassigned_users RPC.
-- In PL/pgSQL, RETURNS TABLE(id, email, name) declares output variables that shadow
-- column names in queries if not explicitly qualified.

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
  --    Explicitly qualify "profiles.id" to avoid collision with the "id" output variable.
  SELECT profiles.role, profiles.institution_id
  INTO   v_caller_role, v_caller_inst
  FROM   public.profiles
  WHERE  profiles.id = auth.uid();

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
  v_sanitized := replace(v_sanitized, '\', '\\');
  v_sanitized := replace(v_sanitized, '%', '\%');
  v_sanitized := replace(v_sanitized, '_', '\_');

  -- 4. Return only eligible unassigned students.
  --    Table aliases 'p' and 'u' are explicitly used for all column references
  --    to prevent any shadowing by the id, email, and name output variables.
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

-- Note: execute permissions were already granted/revoked in 000005, 
-- but it's safe to restate them here to ensure the fixed function remains secure.
REVOKE EXECUTE ON FUNCTION public.search_unassigned_users(TEXT) FROM PUBLIC;
GRANT  EXECUTE ON FUNCTION public.search_unassigned_users(TEXT) TO authenticated;
