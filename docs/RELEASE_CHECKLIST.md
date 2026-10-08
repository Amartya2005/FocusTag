# Release Checklist

- [ ] Confirm the intended Git commit is the release candidate.
- [ ] Run CI and local Android tests.
- [ ] Validate authentication and role boundaries.
- [ ] Verify database migrations are applied in order.
- [ ] Perform physical-device NFC/QR smoke tests.
- [ ] Confirm no secrets or local configuration files are tracked.
- [ ] Generate and validate the signed release build.
