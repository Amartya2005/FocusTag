-- supabase/migrations/20260904000015_phase9_qr_credentials.sql
-- Phase 1: QR Credential Database Layer

-- 1. Create qr_credentials table
CREATE TABLE IF NOT EXISTS public.qr_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nfc_tag_id UUID NOT NULL REFERENCES public.nfc_tags(id) ON DELETE CASCADE,
    credential TEXT NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ NULL
);

-- 2. Create partial unique index
-- Enforces zero or one active QR credential per NFC tag
CREATE UNIQUE INDEX IF NOT EXISTS idx_qr_one_active_per_tag
ON public.qr_credentials(nfc_tag_id)
WHERE is_active = true;

-- 3. Enable RLS
ALTER TABLE public.qr_credentials ENABLE ROW LEVEL SECURITY;

-- 4. RLS Policies

-- Admins have full access to QR credentials for their institution
CREATE POLICY "QR credentials are manageable by admins"
ON public.qr_credentials FOR ALL
TO authenticated
USING (
  public.get_auth_role() = 'admin' AND
  EXISTS (
    SELECT 1 FROM public.nfc_tags nt
    JOIN public.locations l ON nt.location_id = l.id
    WHERE nt.id = qr_credentials.nfc_tag_id
    AND l.institution_id = public.get_auth_institution()
  )
);

-- 5. RPCs

-- Ensure pgcrypto is available for secure randomness
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;

-- Helper to generate a 16-character opaque random token
CREATE OR REPLACE FUNCTION public.generate_opaque_token()
RETURNS TEXT
LANGUAGE sql
AS $$
  -- Generate 15 random bytes, base64 encode, remove non-alphanumeric chars, take first 16 chars
  SELECT substring(replace(replace(replace(encode(extensions.gen_random_bytes(15), 'base64'), '/', ''), '+', ''), '=', '') from 1 for 16);
$$;

REVOKE EXECUTE ON FUNCTION public.generate_opaque_token() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.generate_opaque_token() FROM authenticated;

-- 5a. generate_qr_credential
CREATE OR REPLACE FUNCTION public.generate_qr_credential(p_nfc_tag_id UUID)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_role TEXT;
  v_caller_inst UUID;
  v_tag_inst UUID;
  v_tag_active BOOLEAN;
  v_loc_active BOOLEAN;
  v_new_credential TEXT;
BEGIN
  -- Verify caller
  SELECT profiles.role, profiles.institution_id
  INTO v_caller_role, v_caller_inst
  FROM public.profiles
  WHERE profiles.id = auth.uid();

  IF v_caller_role IS DISTINCT FROM 'admin' OR v_caller_inst IS NULL THEN
    RAISE EXCEPTION 'Unauthorized';
  END IF;

  -- Verify tag and location
  SELECT l.institution_id, nt.is_active, l.is_active
  INTO v_tag_inst, v_tag_active, v_loc_active
  FROM public.nfc_tags nt
  JOIN public.locations l ON nt.location_id = l.id
  WHERE nt.id = p_nfc_tag_id;

  IF NOT FOUND OR v_tag_inst IS DISTINCT FROM v_caller_inst THEN
    RAISE EXCEPTION 'Tag not found or not eligible';
  END IF;

  IF NOT v_tag_active THEN
    RAISE EXCEPTION 'Cannot generate QR credential for an inactive NFC tag';
  END IF;

  IF NOT v_loc_active THEN
    RAISE EXCEPTION 'Cannot generate QR credential for a tag at an inactive location';
  END IF;

  -- Generate credential
  v_new_credential := 'QR-' || public.generate_opaque_token();

  -- Insert (will fail if active QR exists due to idx_qr_one_active_per_tag)
  BEGIN
    INSERT INTO public.qr_credentials (nfc_tag_id, credential, is_active)
    VALUES (p_nfc_tag_id, v_new_credential, true);
  EXCEPTION
    WHEN unique_violation THEN
      RAISE EXCEPTION 'An active QR credential already exists for this tag';
  END;

  RETURN v_new_credential;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.generate_qr_credential(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.generate_qr_credential(UUID) TO authenticated;

-- 5b. regenerate_qr_credential
CREATE OR REPLACE FUNCTION public.regenerate_qr_credential(p_nfc_tag_id UUID)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_role TEXT;
  v_caller_inst UUID;
  v_tag_inst UUID;
  v_tag_active BOOLEAN;
  v_loc_active BOOLEAN;
  v_new_credential TEXT;
BEGIN
  -- Verify caller
  SELECT profiles.role, profiles.institution_id
  INTO v_caller_role, v_caller_inst
  FROM public.profiles
  WHERE profiles.id = auth.uid();

  IF v_caller_role IS DISTINCT FROM 'admin' OR v_caller_inst IS NULL THEN
    RAISE EXCEPTION 'Unauthorized';
  END IF;

  -- Verify tag and location
  SELECT l.institution_id, nt.is_active, l.is_active
  INTO v_tag_inst, v_tag_active, v_loc_active
  FROM public.nfc_tags nt
  JOIN public.locations l ON nt.location_id = l.id
  WHERE nt.id = p_nfc_tag_id;

  IF NOT FOUND OR v_tag_inst IS DISTINCT FROM v_caller_inst THEN
    RAISE EXCEPTION 'Tag not found or not eligible';
  END IF;

  IF NOT v_tag_active THEN
    RAISE EXCEPTION 'Cannot regenerate QR credential for an inactive NFC tag';
  END IF;

  IF NOT v_loc_active THEN
    RAISE EXCEPTION 'Cannot regenerate QR credential for a tag at an inactive location';
  END IF;

  v_new_credential := 'QR-' || public.generate_opaque_token();

  -- Atomically revoke current and insert new. 
  -- The unique index prevents a race where two concurrent calls could both insert successfully.
  UPDATE public.qr_credentials
  SET is_active = false, revoked_at = now()
  WHERE nfc_tag_id = p_nfc_tag_id AND is_active = true;

  BEGIN
    INSERT INTO public.qr_credentials (nfc_tag_id, credential, is_active)
    VALUES (p_nfc_tag_id, v_new_credential, true);
  EXCEPTION
    WHEN unique_violation THEN
      RAISE EXCEPTION 'Concurrency error: could not regenerate QR credential safely';
  END;

  RETURN v_new_credential;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.regenerate_qr_credential(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.regenerate_qr_credential(UUID) TO authenticated;

-- 5c. revoke_qr_credential
CREATE OR REPLACE FUNCTION public.revoke_qr_credential(p_qr_credential_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_role TEXT;
  v_caller_inst UUID;
  v_tag_inst UUID;
BEGIN
  -- Verify caller
  SELECT profiles.role, profiles.institution_id
  INTO v_caller_role, v_caller_inst
  FROM public.profiles
  WHERE profiles.id = auth.uid();

  IF v_caller_role IS DISTINCT FROM 'admin' OR v_caller_inst IS NULL THEN
    RAISE EXCEPTION 'Unauthorized';
  END IF;

  -- Verify credential ownership
  SELECT l.institution_id
  INTO v_tag_inst
  FROM public.qr_credentials qc
  JOIN public.nfc_tags nt ON qc.nfc_tag_id = nt.id
  JOIN public.locations l ON nt.location_id = l.id
  WHERE qc.id = p_qr_credential_id;

  IF NOT FOUND OR v_tag_inst IS DISTINCT FROM v_caller_inst THEN
    RAISE EXCEPTION 'Credential not found or not eligible';
  END IF;

  UPDATE public.qr_credentials
  SET is_active = false, revoked_at = now()
  WHERE id = p_qr_credential_id AND is_active = true;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Credential is already revoked or does not exist';
  END IF;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.revoke_qr_credential(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.revoke_qr_credential(UUID) TO authenticated;

-- 5d. resolve_qr_credential
CREATE OR REPLACE FUNCTION public.resolve_qr_credential(p_credential TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller_inst UUID;
  v_tag_uid TEXT;
BEGIN
  -- Verify caller institution
  SELECT institution_id INTO v_caller_inst
  FROM public.profiles
  WHERE id = auth.uid();

  IF v_caller_inst IS NULL THEN
    RAISE EXCEPTION 'Invalid or inactive credential';
  END IF;

  -- Resolve credential
  SELECT nt.uid
  INTO v_tag_uid
  FROM public.qr_credentials qc
  JOIN public.nfc_tags nt ON qc.nfc_tag_id = nt.id
  JOIN public.locations l ON nt.location_id = l.id
  WHERE qc.credential = p_credential
    AND qc.is_active = true
    AND nt.is_active = true
    AND l.is_active = true
    AND l.institution_id = v_caller_inst;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Invalid or inactive credential';
  END IF;

  RETURN v_tag_uid;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.resolve_qr_credential(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_qr_credential(TEXT) TO authenticated;
