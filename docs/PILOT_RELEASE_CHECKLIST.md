# FocusTag Pilot Release Checklist

Use this checklist before installing a pilot build on institution-owned devices. A checked item records a verification step; it is not a substitute for evidence from the actual handset and backend.

## 1. Configuration and secrets

- [ ] Create `local.properties` from `local.properties.example`.
- [ ] Confirm the Supabase URL and publishable key target the intended project.
- [ ] Confirm no service-role key, database password, or other server secret is bundled in the Android app.
- [ ] Confirm local configuration files containing credentials are excluded from version control.
- [ ] Verify the release build uses the intended application ID and signing configuration.

## 2. Institution and tag setup

- [ ] Create or verify the institution, class, enrollment, policy, and registered tag records.
- [ ] Confirm the NFC UID and QR payload resolve to the same registered tag.
- [ ] Confirm the tag belongs to the intended institution and class.
- [ ] Verify an unknown or disabled tag is rejected by the server.
- [ ] Verify one student's session cannot affect another institution's data.

## 3. Device readiness

- [ ] Record the handset model, Android version, build version, and ownership mode.
- [ ] Verify sign-in and required Accessibility / notification capability gates.
- [ ] For Device Owner testing, verify enrollment on a reset, institution-owned test handset.
- [ ] Confirm the app fails closed when a required capability is unavailable.
- [ ] Verify personal-device limitations are explained to pilot administrators.

## 4. Door-flow acceptance tests

- [ ] Present the registered NFC tag and confirm the server reports `FOCUS_ACTIVE`.
- [ ] Scan the equivalent QR and confirm it follows the same server-side transition.
- [ ] Present the same tag or QR again and confirm the session ends.
- [ ] Rapidly repeat scans and confirm debounce prevents duplicate transitions.
- [ ] Test an unknown tag, malformed QR payload, offline connection, and server error.
- [ ] Confirm the UI does not claim success when the server rejects the transition.
- [ ] Confirm stale or delayed responses cannot incorrectly reopen a session.

## 5. Policy and safety checks

- [ ] Verify the configured app policy on each supported ownership mode.
- [ ] Confirm the permitted-app list is institution-controlled and read-only for students.
- [ ] Verify notification-reply restrictions during an active session.
- [ ] Confirm ending a session restores the expected device behavior.
- [ ] Verify simulated tags and debug-only paths are unavailable in release builds.
- [ ] Test recovery after app process death, device restart, and network interruption.

## 6. Privacy and operational readiness

- [ ] Review requested Android permissions and document why each is required.
- [ ] Avoid collecting more student data than the pilot needs.
- [ ] Confirm institution-scoped authorization is enforced by backend policies, not just UI filtering.
- [ ] Define who can enroll students, register tags, change policies, and review roster status.
- [ ] Define a support contact and a rollback / disable procedure for the pilot.
- [ ] Keep test accounts and test data separate from real student records.

## 7. Pilot sign-off

Record the following before expanding beyond the test group:

- Build identifier and installation date
- Institution, class, and test handset identifiers
- NFC and QR test results
- Failure-mode test results
- Known issues and workarounds
- Person responsible for approval

Do not describe the pilot as production-ready until device management, release signing, backend operational controls, and the remaining Android Enterprise work have been independently reviewed.
