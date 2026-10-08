# Testing Notes

FocusTag keeps domain and UI tests under `app/src/test`.

## Recommended validation
1. Run unit tests after changes to focus-state or enforcement logic.
2. Exercise NFC and QR entry flows on physical Android devices.
3. Verify session creation, history, and policy enforcement against the configured Supabase project.
4. Test recovery after network loss and app restarts.
