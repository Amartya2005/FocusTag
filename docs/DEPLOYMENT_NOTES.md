# Deployment Notes

## Android
Build the release artifact from a clean checkout and verify the resulting package on a physical device before distribution.

## Supabase
Apply migrations from `supabase/migrations` in filename order. Validate row-level security, RPC permissions, authentication roles, and audit behavior in the target project.

## Operational rule
Never commit production secrets, local properties, signing keys, or service-role credentials to the repository.
