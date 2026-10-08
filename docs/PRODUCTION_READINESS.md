# Production Readiness

## Current checks
- Android build and CI configuration are tracked in the repository.
- Supabase migrations are versioned under `supabase/migrations`.
- Security-sensitive database changes are kept as explicit migrations.

## Before release
- Verify release configuration and signing credentials.
- Run the full Android test suite.
- Apply and validate Supabase migrations in the target environment.
- Confirm production environment variables are configured without committing secrets.
